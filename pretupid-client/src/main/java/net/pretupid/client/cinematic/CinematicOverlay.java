package net.pretupid.client.cinematic;

import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class CinematicOverlay {
	private static final int SAMPLES_PER_SECOND = 24;
	private static final int MAX_SAMPLES = 1200;
	private static final double MAX_DISTANCE = 256.0;

	private CinematicOverlay() {
	}

	private record Projected(float x, float y, double depth) {
	}

	public static void drawPath(Gfx g, Font font, Shot shot, int selected, double playhead, int width, int height) {
		if (shot == null || shot.keyframes().isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = Platform.camera();
		if (camera == null || !camera.isInitialized()) {
			return;
		}
		Matrix4f matrix = Platform.viewProjection(camera);
		Vec3 eye = camera.position();
		List<CameraKeyframe> keyframes = shot.keyframes();
		double duration = shot.duration();
		if (keyframes.size() >= 2 && duration > 0) {
			int samples = (int) Math.min(MAX_SAMPLES, Math.max(16, duration * SAMPLES_PER_SECOND));
			Projected previous = null;
			for (int i = 0; i <= samples; i++) {
				double time = duration * i / samples;
				Shot.View view = shot.sample(time);
				Projected point = project(matrix, eye, view.position(), width, height);
				if (point != null && previous != null) {
					int color = time <= playhead ? Theme.accent() : 0xC0FFFFFF;
					line(g, previous, point, color);
				}
				previous = point;
			}
		}
		for (int i = 0; i < keyframes.size(); i++) {
			CameraKeyframe keyframe = keyframes.get(i);
			Projected point = project(matrix, eye, keyframe.position(), width, height);
			if (point == null) {
				continue;
			}
			boolean active = i == selected;
			Vec3 forward = Vec3.directionFromRotation(keyframe.pitch(), keyframe.yaw());
			Projected tip = project(matrix, eye, keyframe.position().add(forward.scale(1.2)), width, height);
			if (tip != null) {
				line(g, point, tip, active ? 0xFFFACC15 : 0xA0FFFFFF);
			}
			int size = active ? 7 : 5;
			int x = Math.round(point.x()) - size / 2;
			int y = Math.round(point.y()) - size / 2;
			Draw.rect(g, x - 1, y - 1, size + 2, size + 2, 0xC0000000);
			Draw.rect(g, x, y, size, size, active ? 0xFFFACC15 : Theme.accent());
			String label = String.valueOf(i + 1);
			Draw.text(g, font, label, Math.round(point.x()) - font.width(label) / 2, y - 10, active ? 0xFFFACC15 : 0xFFFFFFFF);
		}
		if (keyframes.size() >= 2) {
			Shot.View view = shot.sample(playhead);
			Projected head = view == null ? null : project(matrix, eye, view.position(), width, height);
			if (head != null) {
				Draw.rect(g, Math.round(head.x()) - 2, Math.round(head.y()) - 2, 5, 5, 0xFFFFFFFF);
			}
		}
	}

	private static Projected project(Matrix4f matrix, Vec3 eye, Vec3 point, int width, int height) {
		Vec3 relative = point.subtract(eye);
		if (relative.lengthSqr() > MAX_DISTANCE * MAX_DISTANCE) {
			return null;
		}
		Vector4f clip = matrix.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1f));
		if (clip.w <= 0.05f) {
			return null;
		}
		float nx = clip.x / clip.w;
		float ny = clip.y / clip.w;
		if (Math.abs(nx) > 1.6f || Math.abs(ny) > 1.6f) {
			return null;
		}
		return new Projected((nx * 0.5f + 0.5f) * width, (1f - (ny * 0.5f + 0.5f)) * height, clip.w);
	}

	private static void line(Gfx g, Projected from, Projected to, int color) {
		float dx = to.x() - from.x();
		float dy = to.y() - from.y();
		int steps = (int) Math.min(400, Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)))));
		int shadow = Colors.withAlpha(0x000000, 90);
		for (int i = 0; i <= steps; i++) {
			int x = Math.round(from.x() + dx * i / steps);
			int y = Math.round(from.y() + dy * i / steps);
			g.fill(x, y + 1, x + 1, y + 2, shadow);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	public static void drawLetterbox(Gfx g, int width, int height) {
		int target = Math.round(width / 2.39f);
		int bar = Math.max(0, (height - target) / 2);
		if (bar > 0) {
			Draw.rect(g, 0, 0, width, bar, 0xFF000000);
			Draw.rect(g, 0, height - bar, width, bar, 0xFF000000);
		}
	}
}
