package com.musicunbound.music;

import com.musicunbound.MusicUnbound;
import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Era;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which track plays and how long the wait is. Vanilla's MusicManager still does the timing and fading.
 * One shared instance ({@link #INSTANCE}) holds the pending pick, the forced track and the recent history.
 */
public final class MusicDirector {
    public static final MusicDirector INSTANCE = new MusicDirector();

    private static final int HISTORY_SIZE = 5;
    private static final int FIRST_SONG_MAX_TICKS = 12000;
    private static final String END_TRACK_ID = "the_end";
    private static final String MINECRAFT = "minecraft";

    private final RandomSource random = RandomSource.create();
    private final Deque<String> history = new ArrayDeque<>();
    @Nullable
    private Track pending;
    /** A track the player asked for (Playback.playNow). It plays until something else is playing. */
    @Nullable
    private Track forced;
    /** Era-accurate first song: ticks left before any music may start after joining. */
    private int holdTicks;

    private enum Context { MENU, CREDITS, BOSS, END, UNDERWATER, NETHER, DEEP_DARK, OVERWORLD }

    /** Weighted candidate list for one context. */
    private static final class Pool {
        private record Entry(Track track, int weight) {
        }

        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();
        private int totalWeight;

        void add(Track track, int weight) {
            if (ids.add(track.id())) {
                entries.add(new Entry(track, weight));
                totalWeight += weight;
            }
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        boolean contains(String id) {
            return ids.contains(id);
        }
    }

    /** While true (a track preview is playing), no background music plays. Null music also resets vanilla's wait. */
    private static volatile boolean suppressed;

    public static void setSuppressed(boolean value) {
        suppressed = value;
    }

    /** Makes {@code track} the playing song until something else plays, even if it is not in the pool. Null clears it. */
    public void setForced(@Nullable Track track) {
        forced = track;
    }

    /** Event handler for {@link SelectMusicEvent}. */
    public void onSelectMusic(SelectMusicEvent event) {
        if (suppressed) {
            event.setMusic(null);
            return;
        }
        // Another mod already changed the music: leave it alone.
        if (event.getMusic() != event.getOriginalMusic()) {
            return;
        }
        event.setMusic(decide(event.getOriginalMusic(), event.getPlayingMusic(), false));
    }

    /**
     * The music to play now. Returns {@code original} when the mod leaves vanilla's choice alone, or null for silence.
     * With {@code forceNewPick} the playing track is skipped and a fresh pick is made.
     */
    @Nullable
    public Music decide(Music original, @Nullable SoundInstance playing, boolean forceNewPick) {
        if (suppressed) {
            return null;
        }
        Preset preset = MusicConfig.PRESET.get();
        if (preset == Preset.VANILLA_1_21_1) {
            return original;
        }

        Track playingTrack = trackOf(playing);
        if (forced != null && (playingTrack == null || !playingTrack.id().equals(forced.id()))) {
            forced = null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation location = original.getEvent().value().getLocation();
        boolean inNether = minecraft.level != null && minecraft.level.dimension() == Level.NETHER;
        Context context = classify(location, inNether);
        if (context == null || context == Context.CREDITS || context == Context.BOSS) {
            if (forced != null && !forceNewPick) {
                return new Music(forced.sound(), original.getMinDelay(), original.getMaxDelay(), false);
            }
            return original;
        }

        LocalPlayer player = minecraft.player;
        boolean creative = player != null && player.getAbilities().instabuild;
        String timingKey = timingKey(context, creative);

        if (forced != null && !forceNewPick) {
            int[] delays = rotationDelays(context, preset, timingKey);
            return new Music(forced.sound(), delays[0], delays[1], false);
        }
        if (holdTicks > 0) {
            return null;
        }

        // When skipping, the playing track is the one to avoid. Playback.next() passes null, so fall back to Playback.
        Track avoid = forceNewPick ? (playingTrack != null ? playingTrack : Playback.current()) : playingTrack;
        if (preset == Preset.VANILLA_LATEST) {
            return selectVanillaLatest(original, location, context, timingKey, avoid, forceNewPick);
        } else if (context == Context.MENU) {
            return selectMenu(preset, avoid, forceNewPick);
        } else if (keepsVanillaMusic(context)) {
            int[] delays = MusicConfig.FREQUENCY.get().delays(baseMin(context), baseMax(context), eraOf(preset), timingKey);
            return new Music(original.getEvent(), delays[0], delays[1], original.replaceCurrentMusic());
        } else {
            return selectGame(original, preset, context, timingKey, avoid, forceNewPick);
        }
    }

    /** Era-accurate first song: Alpha/Beta waited 0 to 10 minutes after joining before the first song. */
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        boolean eraAlphaBeta = MusicConfig.PRESET.get() == Preset.ERA_ALPHA_BETA && MusicConfig.FREQUENCY.get() == Frequency.ERA;
        holdTicks = eraAlphaBeta ? random.nextInt(FIRST_SONG_MAX_TICKS + 1) : 0;
    }

    public void onClientTick(ClientTickEvent.Post event) {
        if (holdTicks > 0) {
            holdTicks--;
        }
    }

    @Nullable
    private Music selectMenu(Preset preset, @Nullable Track playing, boolean forceNewPick) {
        Pool pool = new Pool();
        for (String id : preset.menuTracks()) {
            addAvailable(pool, id, 1);
        }
        if (pool.isEmpty()) {
            // Silent title screen (for example the Alpha & Beta preset).
            return null;
        }
        return choose(pool, 20, 600, true, playing, forceNewPick);
    }

    private Music selectGame(Music original, Preset preset, Context context, String timingKey, @Nullable Track playing, boolean forceNewPick) {
        Pool pool = new Pool();
        for (String id : preset.gameTracks()) {
            TrackCatalog.get().byId(id).filter(t -> t.gameAllowed() && TrackAvailability.isAvailable(t)).ifPresent(t -> pool.add(t, 1));
        }
        boolean isEnd = context == Context.END;
        if (isEnd) {
            // The End exception is off here, so The End track joins the rotation. It replaces the current music, as vanilla does.
            TrackCatalog.get().byId(END_TRACK_ID).ifPresent(t -> pool.add(t, 1));
        }
        if (pool.isEmpty()) {
            return original;
        }
        int[] delays = MusicConfig.FREQUENCY.get().delays(baseMin(context), baseMax(context), eraOf(preset), timingKey);
        return choose(pool, delays[0], delays[1], isEnd, playing, forceNewPick);
    }

    private Music selectVanillaLatest(Music original, ResourceLocation location, Context context, String timingKey, @Nullable Track playing, boolean forceNewPick) {
        List<TrackCatalog.WeightedId> entries = TrackCatalog.get().vanillaLatest(location);
        if (entries == null) {
            return original;
        }
        Pool pool = new Pool();
        for (TrackCatalog.WeightedId entry : entries) {
            addAvailable(pool, entry.id(), entry.weight());
        }
        if (pool.isEmpty()) {
            return original;
        }
        int min = original.getMinDelay();
        int max = original.getMaxDelay();
        if (context != Context.MENU) {
            int[] delays = MusicConfig.FREQUENCY.get().delays(min, max, null, timingKey);
            min = delays[0];
            max = delays[1];
        }
        return choose(pool, min, max, original.replaceCurrentMusic(), playing, forceNewPick);
    }

    /**
     * Keeps the pending pick stable: while the playing track is in the pool, keep playing it; otherwise pick
     * one next track and keep returning it until it starts. With {@code forceNewPick}, {@code playing} is the
     * track to avoid and a new pick is always made.
     */
    private Music choose(Pool pool, int minDelay, int maxDelay, boolean replace, @Nullable Track playing, boolean forceNewPick) {
        if (!forceNewPick && playing != null && pool.contains(playing.id())) {
            if (pending != null && pending.id().equals(playing.id())) {
                remember(pending);
                pending = null;
            }
            // Same track as the one already playing: never ask for a replace, so vanilla does not cut it.
            return new Music(playing.sound(), minDelay, maxDelay, false);
        }

        if (forceNewPick || pending == null || !pool.contains(pending.id())) {
            pending = pickNext(pool, forceNewPick ? playing : null);
            MusicUnbound.LOGGER.debug("Next background track: {}", pending.id());
        }
        return new Music(pending.sound(), minDelay, maxDelay, replace);
    }

    private Track pickNext(Pool pool, @Nullable Track avoid) {
        // Avoid the most recent tracks, but never more than half the pool.
        int skip = Math.min(HISTORY_SIZE, pool.entries.size() / 2);
        Set<String> recent = new HashSet<>();
        Iterator<String> it = history.iterator();
        for (int i = 0; i < skip && it.hasNext(); i++) {
            recent.add(it.next());
        }
        if (avoid != null) {
            recent.add(avoid.id());
        }

        int total = 0;
        for (Pool.Entry entry : pool.entries) {
            if (!recent.contains(entry.track().id())) {
                total += entry.weight();
            }
        }
        if (total <= 0) {
            recent.clear();
            total = pool.totalWeight;
        }

        int roll = random.nextInt(total);
        for (Pool.Entry entry : pool.entries) {
            if (recent.contains(entry.track().id())) {
                continue;
            }
            roll -= entry.weight();
            if (roll < 0) {
                return entry.track();
            }
        }
        return pool.entries.get(pool.entries.size() - 1).track();
    }

    private void remember(Track track) {
        history.addFirst(track.id());
        while (history.size() > HISTORY_SIZE) {
            history.pollLast();
        }
    }

    @Nullable
    private static Track trackOf(@Nullable SoundInstance playing) {
        if (playing == null) {
            return null;
        }
        Sound sound = playing.getSound();
        if (sound == null) {
            return null;
        }
        // Mod music tracks are matched by their sound event, vanilla and remote tracks by their file.
        return TrackCatalog.get().byFile(sound.getLocation()).or(() -> TrackCatalog.get().byEvent(playing.getLocation())).orElse(null);
    }

    private static void addAvailable(Pool pool, String id, int weight) {
        TrackCatalog.get().byId(id).filter(TrackAvailability::isAvailable).ifPresent(t -> pool.add(t, weight));
    }

    /** Null means "another mod's music, or a context we do not handle". */
    @Nullable
    private static Context classify(ResourceLocation location, boolean inNether) {
        if (!MINECRAFT.equals(location.getNamespace())) {
            return null;
        }
        String path = location.getPath();
        switch (path) {
            case "music.menu":
                return Context.MENU;
            case "music.credits":
                return Context.CREDITS;
            case "music.dragon":
                return Context.BOSS;
            case "music.end":
                return Context.END;
            case "music.under_water":
                return Context.UNDERWATER;
            default:
                break;
        }
        boolean overworldStyle = path.equals("music.game") || path.equals("music.creative") || path.startsWith("music.overworld.");
        if (path.startsWith("music.nether.") || (inNether && overworldStyle)) {
            return Context.NETHER;
        }
        if (path.equals("music.overworld.deep_dark")) {
            return Context.DEEP_DARK;
        }
        return overworldStyle ? Context.OVERWORLD : null;
    }

    private static boolean keepsVanillaMusic(Context context) {
        return switch (context) {
            case NETHER -> MusicConfig.NETHER_KEEPS_MUSIC.get();
            case END -> MusicConfig.END_KEEPS_MUSIC.get();
            case DEEP_DARK -> MusicConfig.DEEP_DARK_KEEPS_MUSIC.get();
            case UNDERWATER -> MusicConfig.UNDERWATER_KEEPS_MUSIC.get();
            default -> false;
        };
    }

    /** Gaps a track picked by the player gets, so it follows the same timing as the rotation would. */
    private static int[] rotationDelays(Context context, Preset preset, String timingKey) {
        if (context == Context.MENU) {
            return new int[]{20, 600};
        }
        return MusicConfig.FREQUENCY.get().delays(baseMin(context), baseMax(context), eraOf(preset), timingKey);
    }

    private static String timingKey(Context context, boolean creative) {
        return switch (context) {
            case END -> "end";
            case NETHER -> "nether";
            case UNDERWATER -> "underwater";
            default -> creative ? "creative" : "game";
        };
    }

    private static int baseMin(Context context) {
        return context == Context.END ? 6000 : 12000;
    }

    private static int baseMax(Context context) {
        return 24000;
    }

    @Nullable
    private static Era eraOf(Preset preset) {
        return preset.eraId() == null ? null : TrackCatalog.get().era(preset.eraId());
    }
}
