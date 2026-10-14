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
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which track plays and how long the wait is. Vanilla's MusicManager still does the timing and fading.
 * Registered on the NeoForge event bus; one instance holds the pending pick and the recent history.
 */
public final class MusicDirector {
    private static final int HISTORY_SIZE = 5;
    private static final String END_TRACK_ID = "the_end";
    private static final String MINECRAFT = "minecraft";

    private final RandomSource random = RandomSource.create();
    private final Deque<String> history = new ArrayDeque<>();
    @Nullable
    private Track pending;

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

    public void onSelectMusic(SelectMusicEvent event) {
        if (suppressed) {
            event.setMusic(null);
            return;
        }
        // Another mod already changed the music: leave it alone.
        if (event.getMusic() != event.getOriginalMusic()) {
            return;
        }
        Preset preset = MusicConfig.PRESET.get();
        if (preset == Preset.VANILLA_1_21_1) {
            return;
        }

        Music original = event.getOriginalMusic();
        ResourceLocation location = original.getEvent().value().getLocation();
        Minecraft minecraft = Minecraft.getInstance();
        boolean inNether = minecraft.level != null && minecraft.level.dimension() == Level.NETHER;
        Context context = classify(location, inNether);
        if (context == null || context == Context.CREDITS || context == Context.BOSS) {
            return;
        }

        LocalPlayer player = minecraft.player;
        boolean creative = player != null && player.getAbilities().instabuild;
        String timingKey = timingKey(context, creative);

        if (preset == Preset.VANILLA_LATEST) {
            selectVanillaLatest(event, original, location, context, timingKey);
        } else if (context == Context.MENU) {
            selectMenu(event, preset);
        } else if (keepsVanillaMusic(context)) {
            int[] delays = MusicConfig.FREQUENCY.get().delays(baseMin(context), baseMax(context), eraOf(preset), timingKey);
            event.setMusic(new Music(original.getEvent(), delays[0], delays[1], original.replaceCurrentMusic()));
        } else {
            selectGame(event, preset, context, timingKey);
        }
    }

    private void selectMenu(SelectMusicEvent event, Preset preset) {
        Pool pool = new Pool();
        for (String id : preset.menuTracks()) {
            addAvailable(pool, id, 1);
        }
        if (pool.isEmpty()) {
            // Silent title screen (for example the Alpha & Beta preset).
            event.setMusic(null);
            return;
        }
        choose(event, pool, 20, 600, true);
    }

    private void selectGame(SelectMusicEvent event, Preset preset, Context context, String timingKey) {
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
            return;
        }
        int[] delays = MusicConfig.FREQUENCY.get().delays(baseMin(context), baseMax(context), eraOf(preset), timingKey);
        choose(event, pool, delays[0], delays[1], isEnd);
    }

    private void selectVanillaLatest(SelectMusicEvent event, Music original, ResourceLocation location, Context context, String timingKey) {
        List<TrackCatalog.WeightedId> entries = TrackCatalog.get().vanillaLatest(location);
        if (entries == null) {
            return;
        }
        Pool pool = new Pool();
        for (TrackCatalog.WeightedId entry : entries) {
            addAvailable(pool, entry.id(), entry.weight());
        }
        if (pool.isEmpty()) {
            return;
        }
        int min = original.getMinDelay();
        int max = original.getMaxDelay();
        if (context != Context.MENU) {
            int[] delays = MusicConfig.FREQUENCY.get().delays(min, max, null, timingKey);
            min = delays[0];
            max = delays[1];
        }
        choose(event, pool, min, max, original.replaceCurrentMusic());
    }

    /**
     * Keeps the pending pick stable: while the playing track is in the pool, keep playing it; otherwise pick
     * one next track and keep returning it until it starts.
     */
    private void choose(SelectMusicEvent event, Pool pool, int minDelay, int maxDelay, boolean replace) {
        Track playing = trackOf(event.getPlayingMusic());
        if (playing != null && pool.contains(playing.id())) {
            if (pending != null && pending.id().equals(playing.id())) {
                remember(pending);
                pending = null;
            }
            // Same track as the one already playing: never ask for a replace, so vanilla does not cut it.
            event.setMusic(new Music(playing.sound(), minDelay, maxDelay, false));
            return;
        }

        if (pending == null || !pool.contains(pending.id())) {
            pending = pickNext(pool);
            MusicUnbound.LOGGER.debug("Next background track: {}", pending.id());
        }
        event.setMusic(new Music(pending.sound(), minDelay, maxDelay, replace));
    }

    private Track pickNext(Pool pool) {
        // Avoid the most recent tracks, but never more than half the pool.
        int skip = Math.min(HISTORY_SIZE, pool.entries.size() / 2);
        Set<String> recent = new HashSet<>();
        Iterator<String> it = history.iterator();
        for (int i = 0; i < skip && it.hasNext(); i++) {
            recent.add(it.next());
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
        return TrackCatalog.get().byFile(sound.getLocation()).orElse(null);
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
