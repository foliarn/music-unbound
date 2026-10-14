package com.musicunbound.mixin;

import com.musicunbound.remote.RemoteTrackProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the shared asset directory (.minecraft/assets) from the launch config, for the remote track downloader. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void musicunbound$captureAssetDirectory(GameConfig gameConfig, CallbackInfo ci) {
        RemoteTrackProvider.setAssetDirectory(gameConfig.location.assetDirectory.toPath());
    }
}
