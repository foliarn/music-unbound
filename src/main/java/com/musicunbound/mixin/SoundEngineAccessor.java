package com.musicunbound.mixin;

import com.google.common.collect.Multimap;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read access to playing channels, used to pause, resume and fade individual music tracks. Render thread only. */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> musicunbound$getInstanceToChannel();

    @Accessor("instanceBySource")
    Multimap<SoundSource, SoundInstance> musicunbound$getInstanceBySource();

    /** Vanilla's volume for this sound (sound volume × category volume). */
    @Invoker("calculateVolume")
    float musicunbound$calculateVolume(SoundInstance sound);
}
