package com.musicunbound.audio;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.mixin.SoundEngineAccessor;
import com.musicunbound.mixin.SoundManagerAccessor;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Smooth music changes. Music that is stopped fades out instead of being cut, and the background music is ducked
 * while a jukebox can be heard. Both work by setting the volume of the music channels every client tick.
 * Render thread only.
 */
public final class MusicFader {
    /** Fade-out length in ticks (2 s). */
    private static final int FADE_TICKS = 40;
    private static final double JUKEBOX_RANGE_SQR = 64.0 * 64.0;
    /** Duck gain change per tick (1 s from full to silent). */
    private static final float DUCK_STEP = 0.05F;

    /** Music that is fading out, with the ticks left until it is really stopped. */
    private static final Map<SoundInstance, Integer> FADING = new HashMap<>();

    /** 1 = normal volume, 0 = silenced because a jukebox is playing nearby. */
    private static float duckGain = 1.0F;

    /** What the last volume pass used. The pass is skipped while nothing has changed. */
    private static float appliedDuck = 1.0F;
    private static float appliedCategoryVolume = 1.0F;
    private static final Set<SoundInstance> APPLIED_MUSIC = new HashSet<>();

    private MusicFader() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MusicFader::onClientTickPost);
    }

    /**
     * Lowers this music instance to silence over {@value #FADE_TICKS} ticks, then stops it. Calling it again for an
     * instance that is already fading does nothing.
     */
    public static void fadeOutAndStop(SoundInstance instance) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (FADING.containsKey(instance)) {
            return;
        }
        if (!soundManager.isActive(instance)) {
            soundManager.stop(instance);
            return;
        }
        FADING.put(instance, FADE_TICKS);
    }

    public static boolean isFading(SoundInstance instance) {
        return FADING.containsKey(instance);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SoundEngineAccessor engine = (SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).musicunbound$getSoundEngine();

        advanceFades(mc.getSoundManager());
        duckGain = moveTowards(duckGain, duckTarget(engine, mc));
        applyVolumes(engine, mc);
    }

    private static void advanceFades(SoundManager soundManager) {
        Iterator<Map.Entry<SoundInstance, Integer>> iterator = FADING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SoundInstance, Integer> entry = iterator.next();
            SoundInstance sound = entry.getKey();
            if (!soundManager.isActive(sound)) {
                // Finished on its own, or the engine was cleared (world unloaded).
                iterator.remove();
                continue;
            }
            int left = entry.getValue() - 1;
            if (left <= 0) {
                iterator.remove();
                soundManager.stop(sound);
            } else {
                entry.setValue(left);
            }
        }
    }

    private static float duckTarget(SoundEngineAccessor engine, Minecraft mc) {
        if (!enabled(MusicConfig.JUKEBOX_FADE)) {
            return 1.0F;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        for (SoundInstance sound : engine.musicunbound$getInstanceBySource().get(SoundSource.RECORDS)) {
            // Relative sounds have no position to measure, so they count as audible.
            if (sound.isRelative() || camPos.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) <= JUKEBOX_RANGE_SQR) {
                return 0.0F;
            }
        }
        return 1.0F;
    }

    private static float moveTowards(float current, float target) {
        if (current < target) {
            return Math.min(target, current + DUCK_STEP);
        }
        if (current > target) {
            return Math.max(target, current - DUCK_STEP);
        }
        return current;
    }

    private static void applyVolumes(SoundEngineAccessor engine, Minecraft mc) {
        Collection<SoundInstance> music = engine.musicunbound$getInstanceBySource().get(SoundSource.MUSIC);
        float categoryVolume = mc.options.getSoundSourceVolume(SoundSource.MUSIC);
        float duck = duckGain;

        // A new song only needs correcting while ducked. At full gain, vanilla already set its volume.
        boolean unchanged = FADING.isEmpty()
                && duck == appliedDuck
                && categoryVolume == appliedCategoryVolume
                && (duck == 1.0F || sameAsApplied(music));
        if (unchanged) {
            return;
        }

        appliedDuck = duck;
        appliedCategoryVolume = categoryVolume;
        APPLIED_MUSIC.clear();
        APPLIED_MUSIC.addAll(music);

        for (SoundInstance sound : music) {
            ChannelAccess.ChannelHandle handle = engine.musicunbound$getInstanceToChannel().get(sound);
            if (handle == null) {
                continue;
            }
            float volume = engine.musicunbound$calculateVolume(sound) * duck * fadeFactor(sound);
            handle.execute(channel -> channel.setVolume(volume));
        }
    }

    private static boolean sameAsApplied(Collection<SoundInstance> music) {
        return music.size() == APPLIED_MUSIC.size() && APPLIED_MUSIC.containsAll(music);
    }

    private static float fadeFactor(SoundInstance sound) {
        Integer left = FADING.get(sound);
        return left == null ? 1.0F : left / (float) FADE_TICKS;
    }

    /** Reads a config option. Before the config is loaded, the vanilla behaviour applies (option off). */
    private static boolean enabled(ModConfigSpec.BooleanValue option) {
        try {
            return option.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
