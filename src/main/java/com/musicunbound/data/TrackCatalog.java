package com.musicunbound.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The track data: the bundled {@code assets/musicunbound/tracks.json}, plus dynamic tracks added at runtime
 * (new Mojang tracks and other mods' music). Lookups are safe to call from any thread.
 */
public final class TrackCatalog {
    private static final String RESOURCE = "/assets/musicunbound/tracks.json";
    /** Era of Mojang tracks that are newer than the bundled data. */
    public static final String FUTURE_ERA = "future";
    /** Era of music sound events registered by other mods. */
    public static final String MODS_ERA = "mods";
    private static volatile TrackCatalog instance;

    /** A track id with its weight (used by the vanilla-latest mapping). */
    public record WeightedId(String id, int weight) {
    }

    /** Immutable snapshot, replaced on every {@link #addDynamic}. */
    private volatile List<Track> tracks;
    private final Map<String, Track> byId = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Track> byFile = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Track> byEvent = new ConcurrentHashMap<>();
    private final List<Era> eras;
    private final Map<String, Era> erasById;
    private final Map<ResourceLocation, List<WeightedId>> vanillaLatest;

    private TrackCatalog(List<Track> tracks, List<Era> eras, Map<ResourceLocation, List<WeightedId>> vanillaLatest) {
        this.vanillaLatest = Collections.unmodifiableMap(vanillaLatest);
        this.eras = List.copyOf(withSyntheticEras(eras));
        this.erasById = new HashMap<>();
        for (Era era : this.eras) {
            erasById.put(era.id(), era);
        }
        for (Track track : tracks) {
            index(track);
        }
        this.tracks = List.copyOf(tracks);
    }

    public static TrackCatalog get() {
        TrackCatalog catalog = instance;
        if (catalog == null) {
            synchronized (TrackCatalog.class) {
                catalog = instance;
                if (catalog == null) {
                    catalog = load();
                    instance = catalog;
                }
            }
        }
        return catalog;
    }

    /** All tracks: the bundled ones in JSON order, then dynamic ones in the order they were added. A stable snapshot. */
    public List<Track> tracks() {
        return tracks;
    }

    public Optional<Track> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Lookup by the vanilla sound file, for example {@code minecraft:music/game/aerie}. */
    public Optional<Track> byFile(ResourceLocation file) {
        return Optional.ofNullable(byFile.get(file));
    }

    /** Lookup by the sound event a track plays (see {@link Track#eventId()}). */
    public Optional<Track> byEvent(ResourceLocation soundEventId) {
        return Optional.ofNullable(byEvent.get(soundEventId));
    }

    /**
     * Adds a track at runtime. A track whose id is already in the catalog is ignored.
     * Safe to call from the downloader thread.
     */
    public synchronized void addDynamic(Track track) {
        if (byId.containsKey(track.id())) {
            return;
        }
        index(track);
        List<Track> next = new ArrayList<>(tracks.size() + 1);
        next.addAll(tracks);
        next.add(track);
        tracks = List.copyOf(next);
    }

    /** Eras in JSON order (oldest first), followed by the synthetic {@value #FUTURE_ERA} and {@value #MODS_ERA} eras. */
    public List<Era> eras() {
        return eras;
    }

    @Nullable
    public Era era(String id) {
        return erasById.get(id);
    }

    /** The vanilla-latest pool for a sound event (for example {@code minecraft:music.overworld.forest}), or null. */
    @Nullable
    public List<WeightedId> vanillaLatest(ResourceLocation soundEventId) {
        return vanillaLatest.get(soundEventId);
    }

    private void index(Track track) {
        byId.put(track.id(), track);
        byFile.putIfAbsent(track.file(), track);
        byEvent.putIfAbsent(track.eventId(), track);
    }

    private static List<Era> withSyntheticEras(List<Era> jsonEras) {
        List<Era> all = new ArrayList<>(jsonEras);
        Map<String, int[]> timing = jsonEras.get(jsonEras.size() - 1).timing();
        all.add(new Era(FUTURE_ERA, "Newer updates", true, timing, all.size()));
        all.add(new Era(MODS_ERA, "Other mods", false, timing, all.size()));
        return all;
    }

    private static TrackCatalog load() {
        try (InputStream in = TrackCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new TrackCatalog(parseTracks(root.getAsJsonArray("tracks")), parseEras(root.getAsJsonArray("eras")), parseVanillaLatest(root.getAsJsonObject("vanillaLatest")));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE, e);
        }
    }

    private static List<Era> parseEras(JsonArray array) {
        List<Era> eras = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            Map<String, int[]> timing = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : o.getAsJsonObject("timing").entrySet()) {
                JsonArray range = entry.getValue().getAsJsonArray();
                timing.put(entry.getKey(), new int[]{range.get(0).getAsInt(), range.get(1).getAsInt()});
            }
            eras.add(new Era(o.get("id").getAsString(), o.get("name").getAsString(), o.get("menuMusic").getAsBoolean(), timing, eras.size()));
        }
        return eras;
    }

    private static List<Track> parseTracks(JsonArray array) {
        List<Track> tracks = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            Set<String> menuEras = new LinkedHashSet<>();
            for (JsonElement era : o.getAsJsonArray("menuEras")) {
                menuEras.add(era.getAsString());
            }
            tracks.add(new Track(
                    o.get("id").getAsString(),
                    o.get("title").getAsString(),
                    o.get("artist").getAsString(),
                    o.get("era").getAsString(),
                    o.get("category").getAsString(),
                    ResourceLocation.parse(o.get("file").getAsString()),
                    o.get("remote").getAsBoolean(),
                    o.get("gameDefault").getAsBoolean(),
                    o.get("gameAllowed").getAsBoolean(),
                    o.get("menuAll").getAsBoolean(),
                    Set.copyOf(menuEras),
                    o.has("length") ? o.get("length").getAsInt() : 0,
                    null));
        }
        return tracks;
    }

    private static Map<ResourceLocation, List<WeightedId>> parseVanillaLatest(JsonObject object) {
        Map<ResourceLocation, List<WeightedId>> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            List<WeightedId> list = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                JsonObject w = element.getAsJsonObject();
                list.add(new WeightedId(w.get("id").getAsString(), w.get("weight").getAsInt()));
            }
            map.put(ResourceLocation.parse(entry.getKey()), List.copyOf(list));
        }
        return map;
    }
}
