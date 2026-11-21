package net.pretupid.client.screenie;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.utility.ScreeniesModule;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.ScreenieScreen;

public final class Screenies {
	public static final int PREVIEW_WIDTH = 960;
	private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Pretupid Screenies");
		thread.setDaemon(true);
		return thread;
	});

	private boolean captureRequested;
	private boolean memeRequested;
	private MemeShot shot = MemeShot.NORMAL;
	private MemeTemplate memeTemplate = MemeTemplate.CLASSIC;
	private List<String> memeTexts = List.of();
	private net.pretupid.client.cinematic.Shot.View staged;
	private int stageTicks;
	private boolean capturing;
	private int previewGeneration;
	private Identifier previewTexture;

	public void request() {
		captureRequested = true;
		memeRequested = false;
	}

	public void requestMeme() {
		requestMeme(MemeShot.NORMAL, MemeTemplate.CLASSIC, List.of());
	}

	public void requestMeme(MemeShot nextShot, MemeTemplate template, List<String> texts) {
		captureRequested = true;
		memeRequested = true;
		shot = nextShot;
		memeTemplate = template;
		memeTexts = List.copyOf(texts);
	}

	public net.pretupid.client.cinematic.Shot.View stagedView() {
		return staged;
	}

	public boolean busy() {
		return capturing;
	}

	public void tick(Minecraft minecraft) {
		if (!captureRequested) {
			return;
		}
		if (minecraft.level == null || minecraft.player == null) {
			captureRequested = false;
			staged = null;
			return;
		}
		if (memeRequested && shot != MemeShot.NORMAL && staged == null) {
			staged = shot.view(minecraft.player);
			stageTicks = 3;
			return;
		}
		if (staged != null && --stageTicks > 0) {
			return;
		}
		captureRequested = false;
		boolean meme = memeRequested;
		memeRequested = false;
		if (capturing) {
			staged = null;
			return;
		}
		ScreeniesModule module = PretupidClient.get().modules().get(ScreeniesModule.class);
		capturing = true;
		try {
			Platform.captureScreenie(module.scale(), image -> WORKER.execute(() -> {
				BufferedImage captured = null;
				try {
					captured = toBuffered(image);
				} catch (IOException | RuntimeException exception) {
					PretupidClient.LOGGER.warn("Could not read the screenie", exception);
				} finally {
					image.close();
				}
				BufferedImage result = captured;
				minecraft.execute(() -> {
					capturing = false;
					if (result == null) {
						notify(Component.translatable("pretupid.screenie.failed"), true);
					} else {
						onCaptured(result, module, meme);
					}
				});
			}));
		} catch (RuntimeException exception) {
			capturing = false;
			staged = null;
			PretupidClient.LOGGER.warn("Could not capture a screenie", exception);
			notify(Component.translatable("pretupid.screenie.failed"), true);
		}
	}

	private void onCaptured(BufferedImage image, ScreeniesModule module, boolean meme) {
		staged = null;
		if (meme) {
			net.minecraft.client.gui.screens.Screen parent = Platform.screen() instanceof ScreenieScreen open ? open.parentScreen() : Platform.screen();
			Platform.setScreen(new ScreenieScreen(parent, image, shot, memeTemplate, memeTexts));
		} else if (module.opensEditor()) {
			Platform.setScreen(new ScreenieScreen(Platform.screen(), image));
		} else {
			save(image, ScreenieLook.NATURAL, List.of(), MemeTemplate.NONE);
		}
	}

	private static BufferedImage toBuffered(NativeImage image) throws IOException {
		Path temp = Files.createTempFile("pretupid-screenie", ".png");
		try {
			image.writeToFile(temp);
			BufferedImage read = ImageIO.read(temp.toFile());
			if (read == null) {
				throw new IOException("Unreadable screenie");
			}
			BufferedImage argb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
			var g = argb.createGraphics();
			g.drawImage(read, 0, 0, null);
			g.dispose();
			return argb;
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	public CompletableFuture<Identifier> preview(BufferedImage source, ScreenieLook look, List<TextLayer> layers, MemeTemplate template) {
		int generation = ++previewGeneration;
		return CompletableFuture.supplyAsync(() -> {
			try {
				return encode(ScreenieComposer.compose(source, look, layers, template, PREVIEW_WIDTH));
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		}, WORKER).thenApplyAsync(bytes -> {
			if (generation != previewGeneration) {
				return previewTexture;
			}
			try {
				NativeImage image = NativeImage.read(bytes);
				Identifier id = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "screenie/preview_" + generation);
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Pretupid screenie preview", image));
				releasePreview();
				previewTexture = id;
				return id;
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		}, Minecraft.getInstance());
	}

	public void releasePreview() {
		if (previewTexture != null) {
			Minecraft.getInstance().getTextureManager().release(previewTexture);
			previewTexture = null;
		}
	}

	public static byte[] encode(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out)) {
			throw new IOException("No PNG writer");
		}
		return out.toByteArray();
	}

	public CompletableFuture<byte[]> render(BufferedImage source, ScreenieLook look, List<TextLayer> layers, MemeTemplate template, int maxWidth) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return encode(ScreenieComposer.compose(source, look, layers, template, maxWidth));
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		}, WORKER);
	}

	public static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("pretupid");
	}

	public CompletableFuture<Path> save(BufferedImage source, ScreenieLook look, List<TextLayer> layers, MemeTemplate template) {
		return render(source, look, layers, template, 0).thenApplyAsync(bytes -> {
			try {
				Path folder = folder();
				Files.createDirectories(folder);
				String base = LocalDateTime.now().format(NAME);
				Path target = folder.resolve(base + ".png");
				for (int i = 2; Files.exists(target); i++) {
					target = folder.resolve(base + "_" + i + ".png");
				}
				Files.write(target, bytes);
				return target;
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		}, WORKER).whenComplete((path, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				PretupidClient.LOGGER.warn("Could not save the screenie", error);
				notify(Component.translatable("pretupid.screenie.save_failed"), true);
			} else {
				PretupidClient.get().notifications().push(Component.translatable("pretupid.screenie.saved_title"),
					Component.translatable("pretupid.screenie.saved", path.getFileName().toString()), Icon.item("painting"),
					null, Theme.SUCCESS, 5000L);
			}
		}));
	}

	private static void notify(Component text, boolean error) {
		PretupidClient.get().notifications().push(Component.translatable("pretupid.screenie.title"), text, Icon.item("painting"), null,
			error ? Theme.DANGER : Theme.accent(), 4000L);
	}
}
