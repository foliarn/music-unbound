package com.musicunbound.music;

import com.mojang.blaze3d.audio.Channel;
import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import com.musicunbound.mixin.SoundEngineAccessor;
import com.musicunbound.mixin.SoundManagerAccessor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The music that is playing now: which track, how long it has run, its history, and the controls (next, previous,
 * play now, pause). Static and render-thread only.
 */
public final class Playback {
    private static final int HISTORY_SIZE = 20;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Music sound that was started this frame and is not resolved yet (its {@code getSound()} is still null). */
    @Nullable
    private static SoundInstance candidate;
    @Nullable
    private static SoundInstance currentInstance;
    @Nullable
    private static Track currentTrack;
    /** Most recent first. Includes the current track, as its first entry. */
    private static final Deque<Track> history = new ArrayDeque<>();

    /** Set when the player pauses the music. Vanilla's game unpause resumes channels, so this is re-applied each tick. */
    private static boolean paused;
    private static long startedAt;
    private static long pausedTotal;
    /** Start of the current user pause (valid while {@link #paused}). */
    private static long pausedSince;
    /** Music frozen by the game pause (not by the player). */
    private static boolean frozen;
    private static long frozenSince;

    private Playback() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Playback::onPlaySound);
        NeoForge.EVENT_BUS.addListener(Playback::onClientTickPost);
    }

    /** The catalog track that is playing right now, or null if it is not a catalog track (or nothing plays). */
    @Nullable
    public static Track current() {
        return currentTrack;
    }

    /** The sound instance that is playing right now (catalog or vanilla music), or null. */
    @Nullable
    public static SoundInstance currentInstance() {
        return currentInstance;
    }

    /** Seconds since the current track started, without the time the player spent pausing it. */
    public static int elapsedSeconds() {
        if (currentTrack == null) {
            return 0;
        }
        long end = paused ? pausedSince : frozen ? frozenSince : System.nanoTime();
        return (int) Math.max(0, (end - startedAt - pausedTotal) / NANOS_PER_SECOND);
    }

    public static boolean isPaused() {
        return paused;
    }

    /** Most recent first. */
    public static List<Track> history() {
        return List.copyOf(history);
    }

    /** Makes {@code track} the background song right away. It keeps playing even if it is not in the pool. */
    public static void playNow(Track track) {
        Minecraft minecraft = Minecraft.getInstance();
        MusicDirector.INSTANCE.setForced(track);
        MusicManager manager = minecraft.getMusicManager();
        manager.stopPlaying();
        manager.startPlaying(new Music(track.sound(), 12000, 24000, false));
    }

    /** Skips to the next pick right away. Does nothing when the director has no music to play. */
    public static void next() {
        startNextPick();
    }

    /** Replays the most recent track that is not the current one. Does nothing if there is none. */
    public static void previous() {
        for (Track track : history) {
            if (currentTrack == null || !track.id().equals(currentTrack.id())) {
                playNow(track);
                return;
            }
        }
    }

    /** Pauses or resumes the music. A pause also holds the next track that starts while paused. */
    public static void togglePause() {
        long now = System.nanoTime();
        if (paused) {
            pausedTotal += now - pausedSince;
        } else {
            pausedSince = now;
        }
        paused = !paused;
        applyPause();
    }

    /**
     * Starts the next pick if no music sound is playing (used by the events). Returns true if a new song was started.
     */
    public static boolean startNextIfSilent() {
        SoundEngine engine = engine(Minecraft.getInstance());
        for (SoundInstance instance : ((SoundEngineAccessor) engine).musicunbound$getInstanceBySource().get(SoundSource.MUSIC)) {
            if (engine.isActive(instance)) {
                return false;
            }
        }
        return startNextPick();
    }

    /** Stops the current music and starts the director's next pick. False if the director has nothing to play. */
    private static boolean startNextPick() {
        Minecraft minecraft = Minecraft.getInstance();
        Music music = MusicDirector.INSTANCE.decide(minecraft.getSituationalMusic(), null, true);
        if (music == null) {
            return false;
        }
        MusicManager manager = minecraft.getMusicManager();
        manager.stopPlaying();
        manager.startPlaying(music);
        return true;
    }

    private static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound != null && sound.getSource() == SoundSource.MUSIC) {
            candidate = sound;
        }
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            // Leaving the world ends a user pause.
            paused = false;
        }

        if (candidate != null && candidate.getSound() != null) {
            if (candidate != currentInstance) {
                SoundInstance started = candidate;
                Optional<Track> track = TrackCatalog.get().byFile(started.getSound().getLocation()).or(() -> TrackCatalog.get().byEvent(started.getLocation()));
                startTrack(candidate, track.orElse(null));
            }
            candidate = null;
        }

        if (currentInstance != null && !minecraft.getSoundManager().isActive(currentInstance)) {
            currentInstance = null;
            currentTrack = null;
        }

        if (paused && currentInstance != null) {
            applyPause();
        }

        // The game pause also pauses music unless "play while paused" is on: that time does not count as elapsed.
        boolean frozenNow = !paused && minecraft.isPaused() && !MusicConfig.PLAY_WHILE_PAUSED.get();
        if (frozenNow != frozen) {
            long now = System.nanoTime();
            if (frozenNow) {
                frozenSince = now;
            } else {
                pausedTotal += now - frozenSince;
            }
            frozen = frozenNow;
        }
    }

    private static void startTrack(SoundInstance instance, @Nullable Track track) {
        currentInstance = instance;
        currentTrack = track;
        startedAt = System.nanoTime();
        pausedTotal = 0;
        pausedSince = startedAt;
        frozenSince = startedAt;
        if (track != null) {
            remember(track);
        }
    }

    private static void remember(Track track) {
        if (!history.isEmpty() && history.peekFirst().id().equals(track.id())) {
            return;
        }
        history.addFirst(track);
        while (history.size() > HISTORY_SIZE) {
            history.pollLast();
        }
    }

    /** Pauses or resumes the current instance's channel to match {@link #paused}. */
    private static void applyPause() {
        if (currentInstance == null) {
            return;
        }
        ChannelAccess.ChannelHandle handle = channelOf(currentInstance);
        if (handle != null) {
            handle.execute(paused ? Channel::pause : Channel::unpause);
        }
    }

    @Nullable
    private static ChannelAccess.ChannelHandle channelOf(SoundInstance instance) {
        SoundEngine engine = engine(Minecraft.getInstance());
        return ((SoundEngineAccessor) engine).musicunbound$getInstanceToChannel().get(instance);
    }

    private static SoundEngine engine(Minecraft minecraft) {
        SoundManager soundManager = minecraft.getSoundManager();
        return ((SoundManagerAccessor) soundManager).musicunbound$getSoundEngine();
    }
}
