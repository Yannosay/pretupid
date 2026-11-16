package net.pretupid.client.social.tunnel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TunnelGuest implements AutoCloseable {
	private static final String READY = "{\"t\":\"ready\"}";

	private final HttpClient http;
	private final URI base;
	private final Supplier<String> token;
	private final UUID host;
	private final ServerSocket server;
	private final Set<TunnelPipe> pipes = ConcurrentHashMap.newKeySet();
	private volatile boolean used;

	private TunnelGuest(HttpClient http, URI base, Supplier<String> token, UUID host) throws IOException {
		this.http = http;
		this.base = base;
		this.token = token;
		this.host = host;
		this.server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
	}

	public static TunnelGuest open(HttpClient http, URI base, Supplier<String> token, UUID host) throws IOException {
		TunnelGuest guest = new TunnelGuest(http, base, token, host);
		Thread acceptor = new Thread(guest::acceptLoop, "Pretupid tunnel guest");
		acceptor.setDaemon(true);
		acceptor.start();
		return guest;
	}

	public UUID host() {
		return host;
	}

	public int port() {
		return server.getLocalPort();
	}

	public String address() {
		return InetAddress.getLoopbackAddress().getHostAddress() + ":" + port();
	}

	private void acceptLoop() {
		while (!server.isClosed()) {
			Socket socket;
			try {
				socket = server.accept();
			} catch (IOException exception) {
				return;
			}
			used = true;
			connect(socket);
		}
	}

	private void connect(Socket socket) {
		String bearer = token.get();
		TunnelPipe pipe;
		try {
			if (bearer == null) {
				socket.close();
				return;
			}
			TunnelPipe[] holder = new TunnelPipe[1];
			pipe = new TunnelPipe(socket, text -> {
				if (READY.equals(text) && holder[0] != null) {
					holder[0].startPumping();
				}
			});
			holder[0] = pipe;
		} catch (IOException exception) {
			return;
		}
		pipes.add(pipe);
		pipe.finished().whenComplete((result, error) -> {
			pipes.remove(pipe);
			if (used && pipes.isEmpty()) {
				close();
			}
		});
		http.newWebSocketBuilder()
			.header("Authorization", "Bearer " + bearer)
			.connectTimeout(Duration.ofSeconds(10))
			.buildAsync(base.resolve("/tunnel/join?host=" + host), pipe)
			.exceptionally(error -> {
				pipe.close();
				return null;
			});
	}

	@Override
	public void close() {
		try {
			server.close();
		} catch (IOException ignored) {
		}
		for (TunnelPipe pipe : pipes) {
			pipe.close();
		}
		pipes.clear();
	}
}
