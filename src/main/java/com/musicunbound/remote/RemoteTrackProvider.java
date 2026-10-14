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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Makes the tracks that 1.21.1 doesn't ship available: downloads them once from Mojang's asset server
 * into the shared {@code assets/objects} store, verifies the SHA-1, and remembers which ones are ready.
 */
public final class RemoteTrackProvider {
    public enum Status { PENDING, DOWNLOADING, AVAILABLE, FAILED, DISABLED }

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String DOWNLOAD_BASE = "https://resources.download.minecraft.net/";
    private static final String THREAD_NAME = "MusicUnbound-Downloader";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, Path> AVAILABLE = new ConcurrentHashMap<>();
    private static final Map<String, Status> STATUS = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private static volatile Path assetDirectory;

    static {
        for (Track track : TrackCatalog.get().tracks()) {
            if (track.remote()) {
                STATUS.put(track.id(), Status.PENDING);
            }
        }
        TrackAvailability.remoteAvailable = AVAILABLE::containsKey;
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

    private static void run(Path assets) {
        List<Track> remote = new ArrayList<>();
        for (Track track : TrackCatalog.get().tracks()) {
            if (track.remote()) {
                remote.add(track);
            }
        }
        Path cacheFile = FMLPaths.CONFIGDIR.get().resolve(MusicUnbound.MOD_ID).resolve("remote_tracks.json");
        Map<String, Entry> cache = loadCache(cacheFile);
        boolean allowDownloads = MusicConfig.DOWNLOAD_NEW_TRACKS.get();

        boolean missingFromCache = remote.stream().anyMatch(t -> !cache.containsKey(t.id()));
        if (missingFromCache && allowDownloads) {
            try {
                Map<String, Entry> index = fetchIndex(remote);
                cache.putAll(index);
                saveCache(cacheFile, cache);
            } catch (Exception e) {
                MusicUnbound.LOGGER.warn("Could not load the Minecraft asset index, new tracks will not download: {}", e.toString());
            }
        }

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

    /** Looks up each remote track's hash and size in the vanilla asset index for the latest release. */
    private static Map<String, Entry> fetchIndex(List<Track> remote) throws IOException, InterruptedException {
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

        Map<String, Entry> result = new ConcurrentHashMap<>();
        for (Track track : remote) {
            String key = "minecraft/sounds/" + track.file().getPath() + ".ogg";
            JsonObject object = objects.has(key) ? objects.getAsJsonObject(key) : null;
            if (object == null) {
                MusicUnbound.LOGGER.warn("New track '{}' ({}) is missing from the asset index", track.id(), key);
                continue;
            }
            result.put(track.id(), new Entry(object.get("hash").getAsString(), object.get("size").getAsLong()));
        }
        return result;
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

    private static Map<String, Entry> loadCache(Path cacheFile) {
        Map<String, Entry> cache = new ConcurrentHashMap<>();
        if (!Files.isRegularFile(cacheFile)) {
            return cache;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(cacheFile)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                cache.put(e.getKey(), new Entry(o.get("hash").getAsString(), o.get("size").getAsLong()));
            }
        } catch (Exception e) {
            MusicUnbound.LOGGER.warn("Ignoring unreadable {}: {}", cacheFile, e.toString());
            cache.clear();
        }
        return cache;
    }

    private static void saveCache(Path cacheFile, Map<String, Entry> cache) {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Entry> e : cache.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("hash", e.getValue().hash());
                o.addProperty("size", e.getValue().size());
                root.add(e.getKey(), o);
            }
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, root.toString());
        } catch (IOException e) {
            MusicUnbound.LOGGER.warn("Could not save {}: {}", cacheFile, e.toString());
        }
    }

    /** One object of the asset index: SHA-1 hash and size in bytes. */
    private record Entry(String hash, long size) {
    }
}
