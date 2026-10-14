package com.musicunbound.data;

import com.musicunbound.MusicUnbound;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/**
 * Makes the music sound events that other mods register available as opt-in tracks (era "Other mods").
 * Such a track plays the mod's own sound event.
 */
public final class ModdedMusic {
    private ModdedMusic() {
    }

    /** Rescans whenever the sound engine is (re)loaded, which is when sound events are replaced. */
    public static void register(IEventBus modBus) {
        modBus.addListener(ModdedMusic::onSoundEngineLoad);
    }

    private static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        scan();
    }

    /** Adds a track for each music sound event of another mod that the catalog doesn't have yet. */
    public static void scan() {
        Minecraft minecraft = Minecraft.getInstance();
        // The event also fires while the sound manager is still being constructed, when there is nothing to scan yet.
        SoundManager sounds = minecraft == null ? null : minecraft.getSoundManager();
        if (sounds == null) {
            return;
        }
        TrackCatalog catalog = TrackCatalog.get();
        int added = 0;
        for (ResourceLocation event : new ArrayList<>(sounds.getAvailableSounds())) {
            String namespace = event.getNamespace();
            String path = event.getPath();
            if ("minecraft".equals(namespace) || MusicUnbound.MOD_ID.equals(namespace) || !path.startsWith("music")) {
                continue;
            }
            String id = (namespace + "_" + path).replace('.', '_').replace('/', '_');
            if (catalog.byId(id).isPresent()) {
                continue;
            }
            String title = Track.titleFromId(lastSegment(path));
            catalog.addDynamic(new Track(
                    id,
                    title,
                    artist(namespace),
                    TrackCatalog.MODS_ERA,
                    "overworld",
                    event,
                    false,
                    false,
                    true,
                    false,
                    Set.of(),
                    0,
                    event));
            added++;
        }
        if (added > 0) {
            MusicUnbound.LOGGER.info("Found {} music tracks from other mods", added);
        }
    }

    /** The last part of a sound path such as {@code music/biome/forest} or {@code music.biome.forest}: {@code forest}. */
    private static String lastSegment(String path) {
        String[] parts = path.split("[/.]");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return parts[i];
            }
        }
        return path;
    }

    private static String artist(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> Objects.requireNonNullElse(container.getModInfo().getDisplayName(), namespace))
                .orElse(namespace);
    }
}
