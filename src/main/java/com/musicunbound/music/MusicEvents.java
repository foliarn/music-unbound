package com.musicunbound.music;

import com.musicunbound.config.MusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Game events that start a song right away when nothing is playing: a dimension change, sunrise or nightfall,
 * and the start of a thunderstorm. Also drives the era-accurate first-song hold.
 */
public final class MusicEvents {
    private static final int POLL_INTERVAL_TICKS = 20;
    private static final long DAY_LENGTH = 24000L;
    private static final long SUNRISE = 23000L;
    private static final long NIGHTFALL = 13000L;
    private static final long NANOS_PER_MINUTE = 60L * 1_000_000_000L;

    @Nullable
    private static ResourceKey<Level> lastDimension;
    private static long lastDayTime = -1;
    private static boolean lastThundering;
    private static int tickCounter;
    private static boolean hasLastEvent;
    private static long lastEventNanos;

    private MusicEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MusicEvents::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(MusicDirector.INSTANCE::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(MusicDirector.INSTANCE::onClientTick);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            // Not in a world: the next arrival is a first join, not a change.
            lastDimension = null;
            lastDayTime = -1;
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        boolean dimensionChanged = lastDimension != null && !lastDimension.equals(dimension);
        lastDimension = dimension;
        if (dimensionChanged && MusicConfig.EVENT_DIMENSION.get()) {
            trigger();
        }

        if (tickCounter++ % POLL_INTERVAL_TICKS != 0) {
            return;
        }
        long dayTime = level.getDayTime() % DAY_LENGTH;
        boolean thundering = level.isThundering();
        if (lastDayTime >= 0) {
            if (MusicConfig.EVENT_TIME.get() && (crossed(lastDayTime, dayTime, SUNRISE) || crossed(lastDayTime, dayTime, NIGHTFALL))) {
                trigger();
            }
            if (MusicConfig.EVENT_WEATHER.get() && !lastThundering && thundering) {
                trigger();
            }
        }
        lastDayTime = dayTime;
        lastThundering = thundering;
    }

    /** Starts a song if nothing plays and the shared cooldown has passed. The cooldown only starts when a song did start. */
    private static void trigger() {
        long now = System.nanoTime();
        long cooldown = MusicConfig.EVENT_COOLDOWN_MINUTES.get() * NANOS_PER_MINUTE;
        if (hasLastEvent && now - lastEventNanos < cooldown) {
            return;
        }
        if (Playback.startNextIfSilent()) {
            hasLastEvent = true;
            lastEventNanos = now;
        }
    }

    /** True when the day time passed {@code threshold} going forward between two samples (handles the wrap at midnight). */
    private static boolean crossed(long before, long after, long threshold) {
        if (before <= after) {
            return before < threshold && threshold <= after;
        }
        return before < threshold || threshold <= after;
    }
}
