package com.musicunbound.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.musicunbound.config.MusicConfig;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Play while paused": vanilla pauses every channel when the game pauses. This keeps music channels playing
 * and pauses only the other sounds. {@link SoundEngine#resume()} needs no change: it only unpauses channels that are paused.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow
    private boolean loaded;

    @Shadow
    @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "pause", at = @At("HEAD"), cancellable = true)
    private void musicunbound$pauseNonMusicOnly(CallbackInfo ci) {
        boolean playWhilePaused;
        try {
            playWhilePaused = MusicConfig.PLAY_WHILE_PAUSED.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet: keep vanilla behaviour.
            return;
        }
        if (!playWhilePaused || !this.loaded) {
            return;
        }

        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : this.instanceToChannel.entrySet()) {
            if (entry.getKey().getSource() != SoundSource.MUSIC) {
                entry.getValue().execute(Channel::pause);
            }
        }
        ci.cancel();
    }
}
