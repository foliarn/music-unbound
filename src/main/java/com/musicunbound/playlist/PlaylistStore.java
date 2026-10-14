package com.musicunbound.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.musicunbound.MusicUnbound;
import com.musicunbound.music.Preset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Playlists and favourites, saved in {@code config/musicunbound/playlists.json}.
 * Built-in playlists are derived from {@link Preset} once. User playlists and favourites are loaded on first use
 * and saved after every change. A missing or broken file means defaults. Save failures are logged, never thrown.
 * All methods are synchronized; they are called from the render thread, so the lock is uncontended.
 */
public final class PlaylistStore {
    private static final String DEFAULT_ACTIVE = "builtin:all";
    private static final String USER_PREFIX = "user:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Nullable
    private static List<Playlist> builtIns;
    private static final Map<String, Playlist> users = new LinkedHashMap<>();
    private static final Set<String> favourites = new LinkedHashSet<>();
    private static String active = DEFAULT_ACTIVE;
    private static boolean loaded;

    private PlaylistStore() {
    }

    /** The built-in playlists in enum order (every {@link Preset}). Computed lazily, once. */
    public static synchronized List<Playlist> builtIns() {
        if (builtIns == null) {
            List<Playlist> list = new ArrayList<>();
            for (Preset preset : Preset.values()) {
                list.add(builtIn(preset));
            }
            builtIns = Collections.unmodifiableList(list);
        }
        return builtIns;
    }

    public static synchronized List<Playlist> userPlaylists() {
        ensureLoaded();
        return List.copyOf(users.values());
    }

    /** Built-ins first, then user playlists in creation order. */
    public static synchronized List<Playlist> all() {
        ensureLoaded();
        List<Playlist> all = new ArrayList<>(builtIns());
        all.addAll(users.values());
        return Collections.unmodifiableList(all);
    }

    /** The active playlist, or {@code builtin:all} if the stored id no longer exists. */
    public static synchronized Playlist active() {
        ensureLoaded();
        Playlist playlist = lookup(active);
        return playlist != null ? playlist : defaultPlaylist();
    }

    public static synchronized Optional<Playlist> byId(String id) {
        ensureLoaded();
        return Optional.ofNullable(lookup(id));
    }

    /** Makes a playlist active. Unknown ids are ignored. */
    public static synchronized void setActive(String id) {
        ensureLoaded();
        if (lookup(id) == null || active.equals(id)) {
            return;
        }
        active = id;
        save();
    }

    /**
     * Creates a user playlist. It copies the tracks of {@code copyFromId}; a VANILLA_* playlist, an unknown id or
     * null copies {@code builtin:all}. The new playlist is not activated.
     */
    public static synchronized Playlist create(String name, @Nullable String copyFromId) {
        ensureLoaded();
        Playlist source = copyFromId == null ? null : lookup(copyFromId);
        if (source == null || source.kind() != Playlist.Kind.NORMAL) {
            source = defaultPlaylist();
        }
        Playlist playlist = new Playlist(newUserId(), name, Playlist.Kind.NORMAL, null, source.game(), source.menu());
        users.put(playlist.id(), playlist);
        save();
        return playlist;
    }

    /** Renames a user playlist. Returns false for built-ins and unknown ids. */
    public static synchronized boolean rename(String id, String name) {
        ensureLoaded();
        Playlist old = users.get(id);
        if (old == null) {
            return false;
        }
        users.put(id, new Playlist(id, name, Playlist.Kind.NORMAL, null, old.game(), old.menu()));
        save();
        return true;
    }

    /**
     * Deletes a user playlist. Returns false for built-ins and unknown ids. Deleting the active playlist makes
     * {@code builtin:all} active.
     */
    public static synchronized boolean delete(String id) {
        ensureLoaded();
        if (users.remove(id) == null) {
            return false;
        }
        if (active.equals(id)) {
            active = DEFAULT_ACTIVE;
        }
        save();
        return true;
    }

    /** Replaces the tracks of a user playlist. Returns false for built-ins and unknown ids. */
    public static synchronized boolean setTracks(String id, Set<String> game, Set<String> menu) {
        ensureLoaded();
        Playlist old = users.get(id);
        if (old == null) {
            return false;
        }
        users.put(id, new Playlist(id, old.name(), Playlist.Kind.NORMAL, null, game, menu));
        save();
        return true;
    }

    public static synchronized boolean isFavourite(String trackId) {
        ensureLoaded();
        return favourites.contains(trackId);
    }

    /** Adds or removes a track from the global favourites. Track ids are kept even if the track is unknown. */
    public static synchronized void setFavourite(String trackId, boolean favourite) {
        ensureLoaded();
        boolean changed = favourite ? favourites.add(trackId) : favourites.remove(trackId);
        if (changed) {
            save();
        }
    }

    private static Playlist builtIn(Preset preset) {
        String eraId = preset.eraId();
        String key = eraId != null ? eraId : preset.name().toLowerCase(Locale.ROOT);
        Playlist.Kind kind = switch (preset) {
            case VANILLA_1_21_1 -> Playlist.Kind.VANILLA_1_21_1;
            case VANILLA_LATEST -> Playlist.Kind.VANILLA_LATEST;
            default -> Playlist.Kind.NORMAL;
        };
        return new Playlist("builtin:" + key, preset.displayName().getString(), kind, eraId, preset.gameTracks(), preset.menuTracks());
    }

    private static Playlist defaultPlaylist() {
        Playlist playlist = lookup(DEFAULT_ACTIVE);
        if (playlist == null) {
            throw new IllegalStateException("Missing built-in playlist " + DEFAULT_ACTIVE);
        }
        return playlist;
    }

    @Nullable
    private static Playlist lookup(String id) {
        for (Playlist playlist : builtIns()) {
            if (playlist.id().equals(id)) {
                return playlist;
            }
        }
        return users.get(id);
    }

    private static String newUserId() {
        String id;
        do {
            id = USER_PREFIX + String.format(Locale.ROOT, "%08x", ThreadLocalRandom.current().nextInt());
        } while (lookup(id) != null);
        return id;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("musicunbound/playlists.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        users.clear();
        favourites.clear();
        active = DEFAULT_ACTIVE;

        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new JsonParseException("top level is not a JSON object");
            }
            read(root.getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            users.clear();
            favourites.clear();
            active = DEFAULT_ACTIVE;
            MusicUnbound.LOGGER.warn("Ignoring broken playlists file {}: {}", file, e.toString());
        }
    }

    /** Reads what it can. Malformed entries are skipped with one warning, so the rest of the file still counts. */
    private static void read(JsonObject root) {
        boolean skipped = false;

        String storedActive = string(root.get("active"));
        if (storedActive != null) {
            active = storedActive;
        }

        JsonElement storedFavourites = root.get("favourites");
        if (storedFavourites != null && storedFavourites.isJsonArray()) {
            for (JsonElement element : storedFavourites.getAsJsonArray()) {
                String trackId = string(element);
                if (trackId != null) {
                    favourites.add(trackId);
                } else {
                    skipped = true;
                }
            }
        } else if (storedFavourites != null) {
            skipped = true;
        }

        JsonElement storedPlaylists = root.get("playlists");
        if (storedPlaylists != null && storedPlaylists.isJsonArray()) {
            for (JsonElement element : storedPlaylists.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    skipped = true;
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                String id = string(o.get("id"));
                String name = string(o.get("name"));
                if (id == null || name == null || !id.startsWith(USER_PREFIX) || users.containsKey(id)) {
                    skipped = true;
                    continue;
                }
                users.put(id, new Playlist(id, name, Playlist.Kind.NORMAL, null, strings(o.get("game")), strings(o.get("menu"))));
            }
        } else if (storedPlaylists != null) {
            skipped = true;
        }

        if (skipped) {
            MusicUnbound.LOGGER.warn("Skipped malformed entries in {}", file());
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", active);

        JsonArray favouriteArray = new JsonArray();
        for (String trackId : favourites) {
            favouriteArray.add(trackId);
        }
        root.add("favourites", favouriteArray);

        JsonArray playlistArray = new JsonArray();
        for (Playlist playlist : users.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", playlist.id());
            o.addProperty("name", playlist.name());
            o.add("game", toArray(playlist.game()));
            o.add("menu", toArray(playlist.menu()));
            playlistArray.add(o);
        }
        root.add("playlists", playlistArray);

        Path file = file();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MusicUnbound.LOGGER.warn("Could not save playlists to {}: {}", file, e.toString());
        }
    }

    @Nullable
    private static String string(@Nullable JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return null;
    }

    /** The string entries of a JSON array, in order. Anything else gives an empty set. */
    private static Set<String> strings(@Nullable JsonElement element) {
        Set<String> result = new LinkedHashSet<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String value = string(item);
                if (value != null) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private static JsonArray toArray(Set<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
