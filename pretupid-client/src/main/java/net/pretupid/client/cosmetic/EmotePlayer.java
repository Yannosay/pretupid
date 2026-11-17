package net.pretupid.client.cosmetic;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.RemoteProfile;
import net.pretupid.client.util.SmoothClock;

public final class EmotePlayer {
	private static final float BLEND_SECONDS = 0.18f;
	private static final long PENDING_MS = 10_000L;

	private final Map<UUID, Active> active = new ConcurrentHashMap<>();
	private final Map<UUID, Long> lastRemoteStart = new ConcurrentHashMap<>();
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
	private CameraType restoreCamera;
	private Vec3 startPosition;

	private record Active(String id, EmoteAnimation animation, long startedAt) {
	}

	private record Pending(String id, long startedAt) {
	}

	public boolean playing(UUID uuid) {
		return uuid != null && active.containsKey(uuid);
	}

	public void playLocal(Catalog.EmoteEntry emote) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		active.put(player.getUUID(), new Active(emote.id(), emote.animation(), SmoothClock.millisLong()));
		startPosition = player.position();
		if (PretupidClient.get().settings().emoteThirdPerson.enabled() && minecraft.options.getCameraType().isFirstPerson()) {
			restoreCamera = minecraft.options.getCameraType();
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		broadcast(emote.id());
	}

	private static void broadcast(String id) {
		PretupidClient client = PretupidClient.get();
		client.realtime().sendEmote(id);
		client.presence().sendEmote(id);
	}

	public void onRealtimeEmote(UUID uuid, String id) {
		pending.remove(uuid);
		if (id == null) {
			active.remove(uuid);
			return;
		}
		if (!PretupidClient.get().settings().showCosmetics.enabled()) {
			return;
		}
		long now = SmoothClock.millisLong();
		lastRemoteStart.put(uuid, now);
		Optional<Catalog.EmoteEntry> entry = PretupidClient.get().cosmetics().catalog().emote(id);
		if (entry.isEmpty()) {
			pending.put(uuid, new Pending(id, now));
			PretupidClient.get().cosmetics().requestCatalogRefresh();
			return;
		}
		active.put(uuid, new Active(id, entry.get().animation(), now));
	}

	private void resolvePending(long now) {
		if (pending.isEmpty()) {
			return;
		}
		Catalog catalog = PretupidClient.get().cosmetics().catalog();
		pending.entrySet().removeIf(entry -> {
			Pending value = entry.getValue();
			if (now - value.startedAt() > PENDING_MS) {
				return true;
			}
			Optional<Catalog.EmoteEntry> emote = catalog.emote(value.id());
			if (emote.isEmpty()) {
				return false;
			}
			EmoteAnimation animation = emote.get().animation();
			if (animation.loop() || now - value.startedAt() < animation.durationSeconds() * 1000L) {
				active.put(entry.getKey(), new Active(value.id(), animation, value.startedAt()));
			}
			return true;
		});
	}

	public void stopLocal() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && active.remove(player.getUUID()) != null) {
			restoreCamera();
		}
	}

	public void onRemoteProfile(UUID uuid, RemoteProfile profile) {
		RemoteProfile.EmoteRef emote = profile.emote();
		if (emote == null) {
			Active current = active.get(uuid);
			if (current != null && current.animation().loop()) {
				active.remove(uuid);
			}
			return;
		}
		Long previous = lastRemoteStart.get(uuid);
		if (previous != null && Math.abs(previous - emote.startedAtMillis()) < 3000L) {
			return;
		}
		lastRemoteStart.put(uuid, emote.startedAtMillis());
		if (!PretupidClient.get().settings().showCosmetics.enabled()) {
			return;
		}
		Optional<Catalog.EmoteEntry> entry = PretupidClient.get().cosmetics().catalog().emote(emote.id());
		if (entry.isEmpty()) {
			pending.put(uuid, new Pending(emote.id(), emote.startedAtMillis()));
			PretupidClient.get().cosmetics().requestCatalogRefresh();
			return;
		}
		EmoteAnimation animation = entry.get().animation();
		long elapsed = SmoothClock.millisLong() - emote.startedAtMillis();
		if (!animation.loop() && elapsed > animation.durationSeconds() * 1000L) {
			return;
		}
		active.put(uuid, new Active(emote.id(), animation, emote.startedAtMillis()));
	}

	public void tick(Minecraft minecraft) {
		long now = SmoothClock.millisLong();
		resolvePending(now);
		active.entrySet().removeIf(entry -> {
			Active value = entry.getValue();
			return !value.animation().loop() && now - value.startedAt() > value.animation().durationSeconds() * 1000L + 50L;
		});
		LocalPlayer player = minecraft.player;
		if (player == null) {
			active.clear();
			restoreCamera = null;
			return;
		}
		Active own = active.get(player.getUUID());
		if (own == null) {
			restoreCamera();
			return;
		}
		var settings = PretupidClient.get().settings();
		boolean moved = settings.emoteStopOnMove.enabled() && startPosition != null && player.position().distanceToSqr(startPosition) > 0.04;
		boolean sneaked = settings.emoteStopOnSneak.enabled() && player.isShiftKeyDown();
		boolean attacked = settings.emoteStopOnAttack.enabled() && player.isSwinging();
		if (moved || sneaked || attacked) {
			active.remove(player.getUUID());
			broadcast(null);
			restoreCamera();
		}
	}

	private void restoreCamera() {
		if (restoreCamera != null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(restoreCamera);
			}
			restoreCamera = null;
		}
	}

	public float[][] pose(UUID uuid) {
		Active value = uuid == null ? null : active.get(uuid);
		if (value == null) {
			return null;
		}
		float duration = value.animation().durationSeconds();
		float elapsed = (float) ((SmoothClock.millis() - value.startedAt()) / 1000.0);
		if (elapsed < 0f) {
			return null;
		}
		float progress = value.animation().loop() ? (elapsed % duration) / duration : Math.min(1f, elapsed / duration);
		float weight = Math.min(1f, elapsed / BLEND_SECONDS);
		if (!value.animation().loop()) {
			weight = Math.min(weight, Math.max(0f, (duration - elapsed) / BLEND_SECONDS));
		}
		EmoteAnimation.Part[] parts = EmoteAnimation.Part.values();
		float[][] result = new float[parts.length + 1][];
		for (EmoteAnimation.Part part : parts) {
			result[part.ordinal()] = value.animation().sample(part, progress);
		}
		result[parts.length] = new float[] {Math.clamp(weight, 0f, 1f)};
		return result;
	}

	public void clear() {
		active.clear();
		pending.clear();
		lastRemoteStart.clear();
		restoreCamera();
	}
}
