package net.pretupid.client.music;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.pretupid.client.mixin.MusicManagerAccess;

public final class MusicController {
	private static final int MAX_QUEUE = 64;
	private static final int START_GRACE_TICKS = 10;
	private static final int RESUME_DELAY_TICKS = 40;

	private final MusicCatalog catalog = new MusicCatalog();
	private final Deque<MusicTrack> queue = new ArrayDeque<>();
	private MusicTrack loop;
	private MusicTrack playingTrack;
	private TrackSound playing;
	private int graceTicks;

	public MusicCatalog catalog() {
		return catalog;
	}

	public boolean overridesVanilla() {
		return playing != null || loop != null || !queue.isEmpty();
	}

	public MusicTrack looping() {
		return loop;
	}

	public List<MusicTrack> queue() {
		return List.copyOf(queue);
	}

	public void tick(Minecraft minecraft) {
		if (!overridesVanilla()) {
			return;
		}
		if (playing != null) {
			if (graceTicks > 0) {
				graceTicks--;
				return;
			}
			if (minecraft.getSoundManager().isActive(playing)) {
				return;
			}
			playing = null;
			playingTrack = null;
		}
		MusicTrack next = loop != null ? loop : queue.pollFirst();
		if (next != null) {
			start(minecraft, next);
		} else {
			resumeVanilla(minecraft);
		}
	}

	public void play(Minecraft minecraft, MusicTrack track) {
		loop = null;
		start(minecraft, track);
	}

	public void loop(Minecraft minecraft, MusicTrack track) {
		loop = track;
		start(minecraft, track);
	}

	public void enqueue(Minecraft minecraft, MusicTrack track) {
		if (queue.size() >= MAX_QUEUE) {
			return;
		}
		queue.addLast(track);
		if (playing == null && loop == null) {
			start(minecraft, queue.pollFirst());
		}
	}

	public void removeFromQueue(int index) {
		int position = 0;
		var iterator = queue.iterator();
		while (iterator.hasNext()) {
			iterator.next();
			if (position++ == index) {
				iterator.remove();
				return;
			}
		}
	}

	public void clearQueue() {
		queue.clear();
	}

	public void skip(Minecraft minecraft) {
		loop = null;
		stopPlaying(minecraft);
		MusicTrack next = queue.pollFirst();
		if (next != null) {
			start(minecraft, next);
			return;
		}
		minecraft.getMusicManager().stopPlaying();
		resumeVanilla(minecraft);
	}

	public void useDynamic(Minecraft minecraft) {
		boolean controlled = overridesVanilla();
		loop = null;
		queue.clear();
		stopPlaying(minecraft);
		if (controlled) {
			resumeVanilla(minecraft);
		}
	}

	public MusicTrack current(Minecraft minecraft) {
		if (playingTrack != null) {
			return playingTrack;
		}
		SoundInstance instance = ((MusicManagerAccess) minecraft.getMusicManager()).pretupid$currentMusic();
		if (instance == null || !minecraft.getSoundManager().isActive(instance)) {
			return null;
		}
		Sound sound = instance.getSound();
		return sound == null ? null : catalog.describe(minecraft, sound.getLocation());
	}

	public static boolean audible(Minecraft minecraft) {
		return minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0f && minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0f;
	}

	private void start(Minecraft minecraft, MusicTrack track) {
		stopPlaying(minecraft);
		minecraft.getMusicManager().stopPlaying();
		playing = new TrackSound(track.file());
		playingTrack = track;
		graceTicks = START_GRACE_TICKS;
		minecraft.getSoundManager().play(playing);
	}

	private void stopPlaying(Minecraft minecraft) {
		if (playing != null) {
			minecraft.getSoundManager().stop(playing);
			playing = null;
			playingTrack = null;
		}
	}

	private static void resumeVanilla(Minecraft minecraft) {
		((MusicManagerAccess) minecraft.getMusicManager()).pretupid$setNextSongDelay(RESUME_DELAY_TICKS);
	}
}
