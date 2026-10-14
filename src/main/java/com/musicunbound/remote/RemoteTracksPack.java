package com.musicunbound.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.musicunbound.MusicUnbound;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Built-in client pack that serves the remote tracks as {@code minecraft:sounds/music/game/<name>.ogg}, and the sound
 * events of the Mojang tracks found by discovery as {@code musicunbound:sounds.json}.
 * Every remote track is always listed and returns a stream supplier, even before its file is downloaded,
 * so Minecraft's sound loading doesn't drop the tracks for the session.
 */
public final class RemoteTracksPack implements PackResources {
    public static final PackLocationInfo INFO = new PackLocationInfo(
            "musicunbound_new_tracks",
            Component.literal("Music Unbound: new tracks"),
            PackSource.BUILT_IN,
            Optional.empty());

    private static final String SOUNDS_PATH = "sounds.json";

    /** Resource path inside the {@code minecraft} namespace (for example {@code sounds/music/game/ebb.ogg}) to track id. */
    private final Map<String, String> idsByPath = new LinkedHashMap<>();
    private final byte[] soundsJson;

    public RemoteTracksPack() {
        for (Track track : TrackCatalog.get().tracks()) {
            if (track.remote() && !RemoteTrackProvider.isFoundThisSession(track.id())) {
                idsByPath.put("sounds/" + track.file().getPath() + ".ogg", track.id());
            }
        }
        List<Track> dynamic = RemoteTrackProvider.startupDynamicTracks();
        for (Track track : dynamic) {
            idsByPath.putIfAbsent("sounds/" + track.file().getPath() + ".ogg", track.id());
        }
        this.soundsJson = soundsJson(dynamic).getBytes(StandardCharsets.UTF_8);
    }

    /** {@code track.<id>} sound events for the given tracks, so that they can be played like the bundled ones. */
    private static String soundsJson(List<Track> tracks) {
        JsonObject root = new JsonObject();
        for (Track track : tracks) {
            JsonObject sound = new JsonObject();
            sound.addProperty("name", track.file().toString());
            sound.addProperty("stream", true);
            JsonArray sounds = new JsonArray();
            sounds.add(sound);
            JsonObject event = new JsonObject();
            event.add("sounds", sounds);
            root.add("track." + track.id(), event);
        }
        return root.toString();
    }

    @Override
    public PackLocationInfo location() {
        return INFO;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (packType != PackType.CLIENT_RESOURCES) {
            return null;
        }
        if (MusicUnbound.MOD_ID.equals(location.getNamespace())) {
            return SOUNDS_PATH.equals(location.getPath()) ? this::openSounds : null;
        }
        if (!"minecraft".equals(location.getNamespace())) {
            return null;
        }
        String id = idsByPath.get(location.getPath());
        return id == null ? null : open(id);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        if (packType != PackType.CLIENT_RESOURCES) {
            return;
        }
        if (MusicUnbound.MOD_ID.equals(namespace)) {
            if (path.isEmpty() || SOUNDS_PATH.equals(path)) {
                resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(MusicUnbound.MOD_ID, SOUNDS_PATH), this::openSounds);
            }
            return;
        }
        if (!"minecraft".equals(namespace)) {
            return;
        }
        for (Map.Entry<String, String> entry : idsByPath.entrySet()) {
            if (entry.getKey().startsWith(path)) {
                resourceOutput.accept(ResourceLocation.fromNamespaceAndPath("minecraft", entry.getKey()), open(entry.getValue()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of("minecraft", MusicUnbound.MOD_ID) : Set.of();
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        return null;
    }

    @Override
    public void close() {
    }

    private InputStream openSounds() {
        return new ByteArrayInputStream(soundsJson);
    }

    private static IoSupplier<InputStream> open(String id) {
        return () -> {
            Optional<Path> file = RemoteTrackProvider.fileFor(id);
            if (file.isEmpty()) {
                throw new FileNotFoundException("Music Unbound track not downloaded yet: " + id);
            }
            return Files.newInputStream(file.get());
        };
    }
}
