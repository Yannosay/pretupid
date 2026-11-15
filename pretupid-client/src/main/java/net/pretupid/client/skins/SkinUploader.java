package net.pretupid.client.skins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidInfo;

public final class SkinUploader {
	public enum Outcome {
		APPLIED,
		NOT_SIGNED_IN,
		RATE_LIMITED,
		REJECTED,
		NETWORK
	}

	private static final URI ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	public CompletableFuture<Outcome> upload(SkinLibrary.Skin skin) {
		String token = Minecraft.getInstance().getUser().getAccessToken();
		if (token == null || token.length() < 16) {
			return CompletableFuture.completedFuture(Outcome.NOT_SIGNED_IN);
		}
		byte[] body;
		String boundary = "pretupid-" + UUID.randomUUID();
		try {
			body = multipart(boundary, skin);
		} catch (IOException exception) {
			return CompletableFuture.completedFuture(Outcome.REJECTED);
		}
		HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
			.timeout(Duration.ofSeconds(20))
			.header("Authorization", "Bearer " + token)
			.header("User-Agent", PretupidInfo.userAgent())
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body))
			.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
			.thenApply(response -> switch (response.statusCode()) {
				case 200, 204 -> Outcome.APPLIED;
				case 401, 403 -> Outcome.NOT_SIGNED_IN;
				case 429 -> Outcome.RATE_LIMITED;
				default -> Outcome.REJECTED;
			})
			.exceptionally(error -> Outcome.NETWORK);
	}

	private static byte[] multipart(String boundary, SkinLibrary.Skin skin) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		String variant = skin.variant().name().toLowerCase(Locale.ROOT);
		write(output, "--" + boundary + "\r\n");
		write(output, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
		write(output, variant + "\r\n");
		write(output, "--" + boundary + "\r\n");
		write(output, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
		write(output, "Content-Type: image/png\r\n\r\n");
		output.write(Files.readAllBytes(skin.file()));
		write(output, "\r\n--" + boundary + "--\r\n");
		return output.toByteArray();
	}

	private static void write(ByteArrayOutputStream output, String text) {
		output.writeBytes(text.getBytes(StandardCharsets.UTF_8));
	}
}
