package com.musicunbound.remote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musicunbound.MusicUnbound;
import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import com.musicunbound.music.TrackAvailability;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Makes the tracks that 1.21.1 doesn't ship available: downloads them once from Mojang's asset server
 * into the shared {@code assets/objects} store, verifies the SHA-1, and remembers which ones are ready.
 * Also finds Mojang music added after this mod was built and adds it to the catalog as "newer updates" tracks.
 */
public final class RemoteTrackProvider {
    public enum Status { PENDING, DOWNLOADING, AVAILABLE, FAILED, DISABLED }

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String DOWNLOAD_BASE = "https://resources.download.minecraft.net/";
    private static final String THREAD_NAME = "MusicUnbound-Downloader";
    /** The asset index is read again after this long, so that newly released Mojang tracks are found. */
    private static final long INDEX_MAX_AGE_MILLIS = Duration.ofHours(24).toMillis();
    /** Cache key that holds the time the asset index was last read. Not a track id (it has a {@code $}). */
    private static final String INDEX_CHECKED_KEY = "$indexChecked";
    private static final String MUSIC_PREFIX = "minecraft/sounds/music/";
    private static final String OGG = ".ogg";
    private static final Pattern TRACK_ID = Pattern.compile("[a-z0-9_]+");
    private static final Set<String> GAME_DEFAULT_CATEGORIES = Set.of("overworld", "menu", "creative", "underwater");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, Path> AVAILABLE = new ConcurrentHashMap<>();
    private static final Map<String, Status> STATUS = new ConcurrentHashMap<>();
    /**
     * Mojang tracks found after startup. They download now, but their sound events only exist after a restart,
     * so they are not playable until then.
     */
    private static final Set<String> FOUND_THIS_SESSION = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private static volatile Path assetDirectory;
    /** The cache as it was when the session started. Read once, before the downloader thread may write it. */
    private static Cache startupCache;

    static {
        for (Track track : TrackCatalog.get().tracks()) {
            if (track.remote()) {
                STATUS.put(track.id(), Status.PENDING);
            }
        }
        TrackAvailability.remoteAvailable = id -> AVAILABLE.containsKey(id) && !FOUND_THIS_SESSION.contains(id);
    }

    private RemoteTrackProvider() {
    }

    /** Called from the Minecraft constructor with the shared asset directory (.minecraft/assets). */
    public static void setAssetDirectory(Path path) {
        assetDirectory = path;
    }

    /** Starts the background download thread once. Safe to call repeatedly. */
    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        startupCache();
        Path assets = assetDirectory;
        if (assets == null) {
            assets = Minecraft.getInstance().gameDirectory.toPath().resolve("assets");
        }
        Path dir = assets;
        Thread thread = new Thread(() -> {
            try {
                run(dir);
            } catch (Throwable t) {
                MusicUnbound.LOGGER.warn("New track download stopped unexpectedly", t);
            } finally {
                for (Track track : TrackCatalog.get().tracks()) {
                    if (track.remote()) {
                        STATUS.computeIfPresent(track.id(), (id, s) -> s == Status.PENDING || s == Status.DOWNLOADING ? Status.FAILED : s);
                    }
                }
            }
        }, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    public static Status status(String trackId) {
        return STATUS.getOrDefault(trackId, Status.DISABLED);
    }

    public static Optional<Path> fileFor(String trackId) {
        return Optional.ofNullable(AVAILABLE.get(trackId));
    }

    /** Whether a Mojang track was found after startup, so it can't be played until the game restarts. */
    public static boolean isFoundThisSession(String trackId) {
        return FOUND_THIS_SESSION.contains(trackId);
    }

    /**
     * The Mojang tracks that were in the cache when the session started. Only these get sound events
     * (see {@link RemoteTracksPack}), so only these can be played in this session.
     */
    public static List<Track> startupDynamicTracks() {
        List<Track> tracks = new ArrayList<>();
        for (Map.Entry<String, Entry> e : startupCache().entries().entrySet()) {
            if (e.getValue().isDynamic()) {
                tracks.add(dynamicTrack(e.getKey(), e.getValue()));
            }
        }
        return tracks;
    }

    private static void run(Path assets) {
        Cache startup = startupCache();
        Map<String, Entry> cache = new LinkedHashMap<>(startup.entries());
        long indexChecked = startup.indexCheckedMillis();
        boolean allowDownloads = MusicConfig.DOWNLOAD_NEW_TRACKS.get();

        for (Track track : startupDynamicTracks()) {
            addDynamic(track, false);
        }

        List<Track> known = remoteTracks();
        boolean missingFromCache = known.stream().anyMatch(t -> !cache.containsKey(t.id()));
        boolean indexStale = System.currentTimeMillis() - indexChecked > INDEX_MAX_AGE_MILLIS;
        if ((missingFromCache || indexStale) && allowDownloads) {
            try {
                Map<String, Entry> index = fetchIndex(known);
                cache.putAll(index);
                indexChecked = System.currentTimeMillis();
                for (Map.Entry<String, Entry> e : index.entrySet()) {
                    if (e.getValue().isDynamic() && TrackCatalog.get().byId(e.getKey()).isEmpty()) {
                        Track track = dynamicTrack(e.getKey(), e.getValue());
                        addDynamic(track, true);
                        MusicUnbound.LOGGER.info("Found new track '{}'. It will download now and be playable after you restart the game.", track.id());
                    }
                }
                saveCache(cacheFile(), cache, indexChecked);
            } catch (Exception e) {
                MusicUnbound.LOGGER.warn("Could not load the Minecraft asset index, new tracks will not download: {}", e.toString());
            }
        }

        List<Track> remote = remoteTracks();
        for (Track track : remote) {
            Entry entry = cache.get(track.id());
            if (entry == null) {
                // The reason (index unavailable or entry missing) was already logged once above.
                STATUS.put(track.id(), allowDownloads ? Status.FAILED : Status.DISABLED);
                continue;
            }
            Path target = objectPath(assets, entry.hash());
            try {
                if (Files.isRegularFile(target) && Files.size(target) == entry.size()) {
                    markAvailable(track.id(), target);
                } else if (allowDownloads) {
                    download(track.id(), entry, target);
                } else {
                    STATUS.put(track.id(), Status.DISABLED);
                }
            } catch (Exception e) {
                MusicUnbound.LOGGER.warn("Could not prepare new track '{}': {}", track.id(), e.toString());
                STATUS.put(track.id(), Status.FAILED);
            }
        }

        long available = remote.stream().filter(t -> AVAILABLE.containsKey(t.id())).count();
        MusicUnbound.LOGGER.info("New tracks: {}/{} available", available, remote.size());
    }

    /** Adds a Mojang track to the catalog as pending. {@code found} means it was discovered after startup. */
    private static void addDynamic(Track track, boolean found) {
        if (TrackCatalog.get().byId(track.id()).isPresent()) {
            return;
        }
        if (found) {
            FOUND_THIS_SESSION.add(track.id());
        }
        STATUS.putIfAbsent(track.id(), Status.PENDING);
        TrackCatalog.get().addDynamic(track);
    }

    private static List<Track> remoteTracks() {
        List<Track> remote = new ArrayList<>();
        for (Track track : TrackCatalog.get().tracks()) {
            if (track.remote()) {
                remote.add(track);
            }
        }
        return remote;
    }

    private static Track dynamicTrack(String id, Entry entry) {
        String category = entry.category();
        return new Track(
                id,
                Track.titleFromId(id),
                "Minecraft",
                TrackCatalog.FUTURE_ERA,
                category,
                ResourceLocation.parse(entry.file()),
                true,
                GAME_DEFAULT_CATEGORIES.contains(category),
                !"end".equals(category),
                false,
                Set.of(),
                0,
                null);
    }

    private static void download(String id, Entry entry, Path target) throws IOException, InterruptedException, NoSuchAlgorithmException {
        STATUS.put(id, Status.DOWNLOADING);
        Path dir = target.getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, entry.hash(), ".tmp");
        try {
            String url = DOWNLOAD_BASE + entry.hash().substring(0, 2) + "/" + entry.hash();
            HttpResponse<Path> response = HTTP.send(request(url).build(), HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            String actual = sha1(temp);
            if (!entry.hash().equalsIgnoreCase(actual)) {
                throw new IOException("SHA-1 mismatch, expected " + entry.hash() + " but got " + actual);
            }
            move(temp, target);
            markAvailable(id, target);
        } catch (Exception e) {
            MusicUnbound.LOGGER.warn("Could not download new track '{}': {}", id, e.toString());
            STATUS.put(id, Status.FAILED);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // A leftover temp file in the asset store is harmless.
            }
        }
    }

    private static void markAvailable(String id, Path file) {
        AVAILABLE.put(id, file);
        STATUS.put(id, Status.AVAILABLE);
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads the latest release's asset index. Returns the entries for the {@code known} remote tracks, plus every
     * music file whose id the catalog doesn't know yet (Mojang tracks added after this mod was built).
     */
    private static Map<String, Entry> fetchIndex(List<Track> known) throws IOException, InterruptedException {
        JsonObject manifest = getJson(MANIFEST_URL);
        String latest = manifest.getAsJsonObject("latest").get("release").getAsString();
        String versionUrl = null;
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject version = element.getAsJsonObject();
            if (version.get("id").getAsString().equals(latest)) {
                versionUrl = version.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IOException("latest release " + latest + " is not in the version manifest");
        }
        String indexUrl = getJson(versionUrl).getAsJsonObject("assetIndex").get("url").getAsString();
        JsonObject objects = getJson(indexUrl).getAsJsonObject("objects");

        Map<String, Entry> result = new LinkedHashMap<>();
        for (Track track : known) {
            String key = "minecraft/sounds/" + track.file().getPath() + ".ogg";
            JsonObject object = objects.has(key) ? objects.getAsJsonObject(key) : null;
            if (object == null) {
                MusicUnbound.LOGGER.warn("New track '{}' ({}) is missing from the asset index", track.id(), key);
                continue;
            }
            String hash = object.get("hash").getAsString();
            long size = object.get("size").getAsLong();
            if (TrackCatalog.FUTURE_ERA.equals(track.era())) {
                result.put(track.id(), new Entry(hash, size, track.file().toString(), track.category()));
            } else {
                result.put(track.id(), new Entry(hash, size, null, null));
            }
        }

        for (String key : objects.keySet()) {
            if (!key.startsWith(MUSIC_PREFIX) || !key.endsWith(OGG) || key.length() <= MUSIC_PREFIX.length() + OGG.length()) {
                continue;
            }
            String rest = key.substring(MUSIC_PREFIX.length(), key.length() - OGG.length());
            String id = rest.substring(rest.lastIndexOf('/') + 1);
            ResourceLocation file = ResourceLocation.tryParse("minecraft:music/" + rest);
            if (file == null || !TRACK_ID.matcher(id).matches() || result.containsKey(id) || TrackCatalog.get().byId(id).isPresent()) {
                continue;
            }
            JsonObject object = objects.getAsJsonObject(key);
            result.put(id, new Entry(object.get("hash").getAsString(), object.get("size").getAsLong(), file.toString(), categoryOf(rest)));
        }
        return result;
    }

    /** The category of a Mojang music file from its folder, where {@code rest} is the path under {@code music/}. */
    private static String categoryOf(String rest) {
        if (rest.startsWith("game/nether/")) {
            return "nether";
        }
        if (rest.startsWith("game/end/")) {
            return "end";
        }
        if (rest.startsWith("game/water/")) {
            return "underwater";
        }
        if (rest.startsWith("game/creative/")) {
            return "creative";
        }
        if (rest.startsWith("menu/")) {
            return "menu";
        }
        return "overworld";
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(request(url).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "MusicUnbound")
                .GET();
    }

    private static Path objectPath(Path assets, String hash) {
        return assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path cacheFile() {
        return FMLPaths.CONFIGDIR.get().resolve(MusicUnbound.MOD_ID).resolve("remote_tracks.json");
    }

    private static synchronized Cache startupCache() {
        if (startupCache == null) {
            startupCache = loadCache(cacheFile());
        }
        return startupCache;
    }

    private static Cache loadCache(Path cacheFile) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        long indexChecked = 0;
        if (Files.isRegularFile(cacheFile)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(cacheFile)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (INDEX_CHECKED_KEY.equals(e.getKey())) {
                        indexChecked = e.getValue().getAsLong();
                        continue;
                    }
                    JsonObject o = e.getValue().getAsJsonObject();
                    Entry entry = new Entry(o.get("hash").getAsString(), o.get("size").getAsLong(), optionalString(o, "file"), optionalString(o, "category"));
                    if (entry.isDynamic() && ResourceLocation.tryParse(entry.file()) == null) {
                        continue;
                    }
                    entries.put(e.getKey(), entry);
                }
            } catch (Exception e) {
                MusicUnbound.LOGGER.warn("Ignoring unreadable {}: {}", cacheFile, e.toString());
                entries.clear();
                indexChecked = 0;
            }
        }
        return new Cache(Collections.unmodifiableMap(entries), indexChecked);
    }

    private static void saveCache(Path cacheFile, Map<String, Entry> cache, long indexChecked) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty(INDEX_CHECKED_KEY, indexChecked);
            for (Map.Entry<String, Entry> e : cache.entrySet()) {
                Entry entry = e.getValue();
                JsonObject o = new JsonObject();
                o.addProperty("hash", entry.hash());
                o.addProperty("size", entry.size());
                if (entry.isDynamic()) {
                    o.addProperty("file", entry.file());
                    o.addProperty("category", entry.category());
                }
                root.add(e.getKey(), o);
            }
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, root.toString());
        } catch (IOException e) {
            MusicUnbound.LOGGER.warn("Could not save {}: {}", cacheFile, e.toString());
        }
    }

    @Nullable
    private static String optionalString(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : null;
    }

    /** The remote tracks file cache: entries by track id, and when the asset index was last read (epoch millis). */
    private record Cache(Map<String, Entry> entries, long indexCheckedMillis) {
    }

    /**
     * One object of the asset index: SHA-1 hash and size in bytes. Mojang tracks found by discovery also keep their
     * sound file and category, so they can be rebuilt from the cache when the game starts offline.
     */
    private record Entry(String hash, long size, @Nullable String file, @Nullable String category) {
        boolean isDynamic() {
            return file != null && category != null;
        }
    }
}
