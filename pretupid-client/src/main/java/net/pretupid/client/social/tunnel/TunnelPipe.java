package net.pretupid.client.social.tunnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class TunnelPipe implements WebSocket.Listener {
	private static final int BUFFER_BYTES = 32 * 1024;

	private final Socket socket;
	private final OutputStream output;
	private final Consumer<String> onText;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final CompletableFuture<Void> finished = new CompletableFuture<>();
	private volatile WebSocket webSocket;

	TunnelPipe(Socket socket, Consumer<String> onText) throws IOException {
		this.socket = socket;
		this.socket.setTcpNoDelay(true);
		this.output = socket.getOutputStream();
		this.onText = onText;
	}

	CompletableFuture<Void> finished() {
		return finished;
	}

	void startPumping() {
		startPumping(webSocket);
	}

	void startPumping(WebSocket webSocket) {
		if (webSocket == null || closed.get()) {
			return;
		}
		this.webSocket = webSocket;
		Thread pump = new Thread(this::pumpSocketToWebSocket, "Pretupid tunnel pump");
		pump.setDaemon(true);
		pump.start();
	}

	private void pumpSocketToWebSocket() {
		byte[] buffer = new byte[BUFFER_BYTES];
		try (InputStream input = socket.getInputStream()) {
			int read;
			while (!closed.get() && (read = input.read(buffer)) >= 0) {
				if (read > 0) {
					webSocket.sendBinary(ByteBuffer.wrap(Arrays.copyOf(buffer, read)), true).join();
				}
			}
		} catch (IOException | RuntimeException exception) {
			close();
			return;
		}
		close();
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		this.webSocket = webSocket;
		webSocket.request(1);
	}

	@Override
	public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		try {
			byte[] bytes = new byte[data.remaining()];
			data.get(bytes);
			output.write(bytes);
			output.flush();
		} catch (IOException exception) {
			close();
			return null;
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		onText.accept(data.toString());
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		close();
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		close();
	}

	void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		try {
			socket.close();
		} catch (IOException ignored) {
		}
		WebSocket current = webSocket;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "closed").exceptionally(error -> null);
		}
		finished.complete(null);
	}
}
