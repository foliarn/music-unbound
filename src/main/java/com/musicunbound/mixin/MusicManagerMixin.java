package com.musicunbound.mixin;

import com.musicunbound.audio.MusicFader;
import com.musicunbound.config.MusicConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "Smooth fades": vanilla cuts a song off instantly when the music changes or stops. Those stop calls fade the song
 * out instead (see {@link MusicFader}).
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    private static final String STOP_SOUND = "Lnet/minecraft/client/sounds/SoundManager;stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V";

    @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = STOP_SOUND))
    private void musicunbound$stopOnTick(SoundManager soundManager, SoundInstance instance) {
        musicunbound$stopMusic(soundManager, instance);
    }

    @Redirect(method = "stopPlaying()V", at = @At(value = "INVOKE", target = STOP_SOUND))
    private void musicunbound$stopOnStopPlaying(SoundManager soundManager, SoundInstance instance) {
        musicunbound$stopMusic(soundManager, instance);
    }

    private static void musicunbound$stopMusic(SoundManager soundManager, SoundInstance instance) {
        if (musicunbound$smoothFades()) {
            MusicFader.fadeOutAndStop(instance);
        } else {
            soundManager.stop(instance);
        }
    }

    private static boolean musicunbound$smoothFades() {
        try {
            return MusicConfig.SMOOTH_FADES.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet: keep vanilla behaviour.
            return false;
        }
    }
}
