package net.pretupid.client.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MusicManager.class)
public interface MusicManagerAccess {
	@Accessor("currentMusic")
	SoundInstance pretupid$currentMusic();

	@Accessor("nextSongDelay")
	void pretupid$setNextSongDelay(int delay);
}
