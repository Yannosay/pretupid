package net.pretupid.client.social.tunnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class TunnelHost {
	private static final Pattern STREAM = Pattern.compile("[0-9a-f]{32}");
	private static final long PING_SECONDS = 30L;
	private static final long RECONNECT_SECONDS = 5L;

	private final HttpClient http;
	private final URI base;
	private final Supplier<String> token;
	private final Predicate<UUID> gate;
	private final int port;
	private final Set<TunnelPipe> pipes = ConcurrentHashMap.newKeySet();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Pretupid tunnel host");
		thread.setDaemon(true);
		return thread;
	});
	private volatile WebSocket control;
	private volatile boolean running;

	public TunnelHost(HttpClient http, URI base, Supplier<String> token, Predicate<UUID> gate, int port) {
		this.http = http;
		this.base = base;
		this.token = token;
		this.gate = gate;
		this.port = port;
	}

	public boolean running() {
		return running;
	}

	public int port() {
		return port;
	}

	public CompletableFuture<Void> start() {
		running = true;
		scheduler.scheduleAtFixedRate(this::ping, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
		return connect();
	}

	private CompletableFuture<Void> connect() {
		String bearer = token.get();
		if (bearer == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("Not signed in to Pretupid services"));
		}
		return http.newWebSocketBuilder()
			.header("Authorization", "Bearer " + bearer)
			.connectTimeout(Duration.ofSeconds(10))
			.buildAsync(base.resolve("/tunnel/host"), new ControlListener())
			.thenAccept(socket -> {
				control = socket;
				if (!running) {
					socket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped");
				}
			});
	}

	private void reconnectLater() {
		if (!running) {
			return;
		}
		scheduler.schedule(() -> {
			if (running) {
				connect().exceptionally(error -> {
					reconnectLater();
					return null;
				});
			}
		}, RECONNECT_SECONDS, TimeUnit.SECONDS);
	}

	private void ping() {
		WebSocket current = control;
		if (current != null && running) {
			current.sendText("{\"t\":\"ping\"}", true).exceptionally(error -> null);
		}
	}

	private void handle(String text) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(text);
		} catch (RuntimeException exception) {
			return;
		}
		if (!parsed.isJsonObject()) {
			return;
		}
		JsonObject frame = parsed.getAsJsonObject();
		if (!frame.has("t") || !"open".equals(frame.get("t").getAsString()) || !frame.has("stream") || !frame.has("guest")) {
			return;
		}
		String stream = frame.get("stream").getAsString();
		if (!STREAM.matcher(stream).matches()) {
			return;
		}
		UUID guest;
		try {
			guest = UUID.fromString(frame.get("guest").getAsString());
		} catch (IllegalArgumentException exception) {
			reject(stream);
			return;
		}
		if (!gate.test(guest)) {
			reject(stream);
			return;
		}
		scheduler.execute(() -> openStream(stream));
	}

	private void reject(String stream) {
		WebSocket current = control;
		if (current != null) {
			current.sendText("{\"t\":\"reject\",\"stream\":\"" + stream + "\"}", true).exceptionally(error -> null);
		}
	}

	private void openStream(String stream) {
		String bearer = token.get();
		TunnelPipe pipe;
		try {
			pipe = new TunnelPipe(new Socket(InetAddress.getLoopbackAddress(), port), text -> {
			});
		} catch (IOException exception) {
			reject(stream);
			return;
		}
		if (bearer == null) {
			pipe.close();
			reject(stream);
			return;
		}
		pipes.add(pipe);
		pipe.finished().whenComplete((result, error) -> pipes.remove(pipe));
		http.newWebSocketBuilder()
			.header("Authorization", "Bearer " + bearer)
			.connectTimeout(Duration.ofSeconds(10))
			.buildAsync(base.resolve("/tunnel/stream?stream=" + stream), pipe)
			.thenAccept(pipe::startPumping)
			.exceptionally(error -> {
				pipe.close();
				return null;
			});
	}

	public void stop() {
		running = false;
		WebSocket current = control;
		control = null;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "stopped").exceptionally(error -> null);
		}
		for (TunnelPipe pipe : pipes) {
			pipe.close();
		}
		pipes.clear();
		scheduler.shutdownNow();
	}

	private final class ControlListener implements WebSocket.Listener {
		private final StringBuilder text = new StringBuilder();

		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			text.append(data);
			if (last) {
				String frame = text.toString();
				text.setLength(0);
				handle(frame);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			if (control == webSocket) {
				control = null;
				if (statusCode != 4000) {
					reconnectLater();
				}
			}
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			if (control == webSocket) {
				control = null;
				reconnectLater();
			}
		}
	}
}
