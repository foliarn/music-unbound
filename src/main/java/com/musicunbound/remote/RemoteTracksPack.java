package com.musicunbound.remote;

import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
 * Built-in client pack that serves the remote tracks as {@code minecraft:sounds/music/game/<name>.ogg}.
 * Every remote track is always listed and returns a stream supplier, even before its file is downloaded,
 * so Minecraft's sound loading doesn't drop the tracks for the session.
 */
public final class RemoteTracksPack implements PackResources {
    public static final PackLocationInfo INFO = new PackLocationInfo(
            "musicunbound_new_tracks",
            Component.literal("Music Unbound: new tracks"),
            PackSource.BUILT_IN,
            Optional.empty());

    /** Resource path inside the {@code minecraft} namespace (for example {@code sounds/music/game/ebb.ogg}) to track id. */
    private static final Map<String, String> IDS_BY_PATH = new LinkedHashMap<>();

    static {
        for (Track track : TrackCatalog.get().tracks()) {
            if (track.remote()) {
                IDS_BY_PATH.put("sounds/" + track.file().getPath() + ".ogg", track.id());
            }
        }
    }

    @Override
    public PackLocationInfo location() {
        return INFO;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (packType != PackType.CLIENT_RESOURCES || !"minecraft".equals(location.getNamespace())) {
            return null;
        }
        String id = IDS_BY_PATH.get(location.getPath());
        return id == null ? null : open(id);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        if (packType != PackType.CLIENT_RESOURCES || !"minecraft".equals(namespace)) {
            return;
        }
        for (Map.Entry<String, String> entry : IDS_BY_PATH.entrySet()) {
            if (entry.getKey().startsWith(path)) {
                resourceOutput.accept(ResourceLocation.fromNamespaceAndPath("minecraft", entry.getKey()), open(entry.getValue()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of("minecraft") : Set.of();
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
