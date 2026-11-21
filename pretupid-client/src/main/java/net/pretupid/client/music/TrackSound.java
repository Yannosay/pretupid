package net.pretupid.client.music;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

final class TrackSound extends AbstractSoundInstance {
	private static final Identifier EVENT = Identifier.withDefaultNamespace("music.menu");

	private final Sound track;

	TrackSound(Identifier file) {
		super(EVENT, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
		this.track = new Sound(file, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, 16);
		this.volume = 1f;
		this.pitch = 1f;
		this.looping = false;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	@Override
	public Sound getSound() {
		return track;
	}

	@Override
	public float getVolume() {
		return volume;
	}

	@Override
	public float getPitch() {
		return pitch;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
