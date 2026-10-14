package com.musicunbound.data;

import com.musicunbound.MusicUnbound;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * One background track. {@code file} is the vanilla sound file (for example {@code minecraft:music/game/aerie}).
 * {@code length} is the duration in seconds, or 0 when unknown.
 * {@code event} is the sound event to play when it is not the mod's own {@code musicunbound:track.<id>} (for example
 * a music event registered by another mod), or null.
 */
public record Track(
        String id,
        String title,
        String artist,
        String era,
        String category,
        ResourceLocation file,
        boolean remote,
        boolean gameDefault,
        boolean gameAllowed,
        boolean menuAll,
        Set<String> menuEras,
        int length,
        @Nullable ResourceLocation event) {

    private static final Map<String, Holder<SoundEvent>> SOUNDS = new ConcurrentHashMap<>();

    /**
     * The sound event this track plays: {@link #event} when set, otherwise the mod's own {@code musicunbound:track.<id>}.
     * The mod's own events are never registered in a registry.
     */
    public ResourceLocation eventId() {
        return event != null ? event : ResourceLocation.fromNamespaceAndPath(MusicUnbound.MOD_ID, "track." + id);
    }

    /** Cached holder for {@link #eventId()}, so per-tick code allocates nothing. */
    public Holder<SoundEvent> sound() {
        Holder<SoundEvent> holder = SOUNDS.get(id);
        if (holder == null) {
            holder = Holder.direct(SoundEvent.createVariableRangeEvent(eventId()));
            SOUNDS.put(id, holder);
        }
        return holder;
    }

    /** A display title from a name such as {@code below_and_above}: {@code Below And Above}. */
    public static String titleFromId(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
