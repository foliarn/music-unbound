package com.musicunbound.music;

import com.mojang.blaze3d.audio.Channel;
import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import com.musicunbound.mixin.SoundEngineAccessor;
import com.musicunbound.mixin.SoundManagerAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 *
 * <p>The history works like a media player's: {@link #history} is the list of played tracks, oldest first, and
 * {@link #cursor} is the entry that plays now. Previous and next move the cursor through it. Any other start
 * replaces the entries after the cursor.</p>
 */
public final class Playback {
    private static final int HISTORY_SIZE = 50;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    /** Previous restarts the current track when it has played longer than this, instead of going back. */
    private static final long RESTART_AFTER_NANOS = 3 * NANOS_PER_SECOND;

    /** Music sound that was started this frame and is not resolved yet (its {@code getSound()} is still null). */
    @Nullable
    private static SoundInstance candidate;
    @Nullable
    private static SoundInstance currentInstance;
    @Nullable
    private static Track currentTrack;

    /** Played tracks, oldest first. Only catalog tracks are listed. */
    private static final List<Track> history = new ArrayList<>();
    /** Index of the entry that plays now, or -1 when the playing track is not in the history (for example vanilla music). */
    private static int cursor = -1;
    /** The track that a history navigation (previous, next, restart) is starting. Its start does not change the history. */
    @Nullable
    private static Track navigatingTo;
    /** Music instances that the player cut. The engine keeps them registered for a while, so they are skipped as "playing". */
    private static final Set<SoundInstance> cut = new HashSet<>();

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
        return (int) (elapsedNanos() / NANOS_PER_SECOND);
    }

    public static boolean isPaused() {
        return paused;
    }

    /** Most recent first. Includes entries after the cursor, if the player has stepped back. */
    public static List<Track> history() {
        List<Track> recent = new ArrayList<>(history.size());
        for (int i = history.size() - 1; i >= 0; i--) {
            recent.add(history.get(i));
        }
        return List.copyOf(recent);
    }

    /** True when {@link #previous()} would do something other than do nothing: restart the track or go back. */
    public static boolean hasPrevious() {
        return (currentTrack != null && elapsedNanos() > RESTART_AFTER_NANOS) || hasEarlierEntry();
    }

    /** Makes {@code track} the background song right away. It keeps playing even if it is not in the pool. */
    public static void playNow(Track track) {
        navigatingTo = null;
        startNow(track);
    }

    /**
     * Moves to the next entry of the history when the player has stepped back. Otherwise skips to the next pick
     * right away. Does nothing when the director has no music to play.
     */
    public static void next() {
        if (cursor >= 0 && cursor < history.size() - 1) {
            cursor++;
            navigateTo(history.get(cursor));
            return;
        }
        startNextPick();
    }

    /**
     * Restarts the current track when it has played more than 3 seconds, or when there is no earlier entry. Otherwise
     * goes back to the previous entry of the history.
     */
    public static void previous() {
        if (currentTrack != null && elapsedNanos() > RESTART_AFTER_NANOS) {
            restartCurrent();
        } else if (hasEarlierEntry()) {
            cursor = cursor < 0 ? history.size() - 1 : cursor - 1;
            navigateTo(history.get(cursor));
        } else {
            restartCurrent();
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
        for (SoundInstance instance : new ArrayList<>(((SoundEngineAccessor) engine).musicunbound$getInstanceBySource().get(SoundSource.MUSIC))) {
            if (engine.isActive(instance) && !cut.contains(instance)) {
                return false;
            }
        }
        return startNextPick();
    }

    private static boolean hasEarlierEntry() {
        return cursor > 0 || (cursor < 0 && !history.isEmpty());
    }

    private static long elapsedNanos() {
        if (currentTrack == null) {
            return 0;
        }
        long end = paused ? pausedSince : frozen ? frozenSince : System.nanoTime();
        return Math.max(0, end - startedAt - pausedTotal);
    }

    private static void restartCurrent() {
        if (currentTrack != null) {
            navigateTo(currentTrack);
        }
    }

    /** Starts {@code track} as a history navigation: the entry is already where the cursor is, so the history stays. */
    private static void navigateTo(Track track) {
        navigatingTo = track;
        startNow(track);
    }

    /** Cuts the current music at once and starts {@code track}. It keeps playing even if it is not in the pool. */
    private static void startNow(Track track) {
        Minecraft minecraft = Minecraft.getInstance();
        MusicDirector.INSTANCE.setForced(track);
        cutAllMusic(minecraft);
        MusicManager manager = minecraft.getMusicManager();
        manager.stopPlaying();
        manager.startPlaying(new Music(track.sound(), 12000, 24000, false));
    }

    /** Stops the current music and starts the director's next pick. False if the director has nothing to play. */
    private static boolean startNextPick() {
        Minecraft minecraft = Minecraft.getInstance();
        Music music = MusicDirector.INSTANCE.decide(minecraft.getSituationalMusic(), null, true);
        if (music == null) {
            return false;
        }
        navigatingTo = null;
        cutAllMusic(minecraft);
        MusicManager manager = minecraft.getMusicManager();
        manager.stopPlaying();
        manager.startPlaying(music);
        return true;
    }

    /**
     * Stops every music sound at once, with no fade. This includes sounds that are still fading out. The fader then
     * finds them inactive and leaves them alone.
     */
    private static void cutAllMusic(Minecraft minecraft) {
        SoundManager soundManager = minecraft.getSoundManager();
        SoundEngine engine = engine(minecraft);
        for (SoundInstance instance : new ArrayList<>(((SoundEngineAccessor) engine).musicunbound$getInstanceBySource().get(SoundSource.MUSIC))) {
            soundManager.stop(instance);
            cut.add(instance);
        }
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
                startTrack(started, track.orElse(null));
            }
            candidate = null;
        }

        cut.removeIf(instance -> !minecraft.getSoundManager().isActive(instance));

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
        boolean navigated = track != null && navigatingTo != null && track.id().equals(navigatingTo.id());
        navigatingTo = null;

        currentInstance = instance;
        currentTrack = track;
        startedAt = System.nanoTime();
        pausedTotal = 0;
        pausedSince = startedAt;
        frozenSince = startedAt;
        if (track == null) {
            cursor = -1;
        } else if (!navigated) {
            remember(track);
        }
    }

    /** A fresh start: drops the entries after the cursor, appends the track and moves the cursor to it. */
    private static void remember(Track track) {
        if (cursor >= 0) {
            history.subList(cursor + 1, history.size()).clear();
        }
        // The same track started again (for example "play now" on the current track) is not listed twice in a row.
        boolean sameAsNewest = !history.isEmpty() && history.get(history.size() - 1).id().equals(track.id());
        if (!sameAsNewest) {
            history.add(track);
            while (history.size() > HISTORY_SIZE) {
                history.remove(0);
            }
        }
        cursor = history.size() - 1;
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
