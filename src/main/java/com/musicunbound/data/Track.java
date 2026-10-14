package com.musicunbound.data;

import com.musicunbound.MusicUnbound;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * One background track. {@code file} is the vanilla sound file (for example {@code minecraft:music/game/aerie}).
 * {@code length} is the duration in seconds, or 0 when unknown.
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
        int length) {

    private static final Map<String, Holder<SoundEvent>> SOUNDS = new ConcurrentHashMap<>();

    /** The mod's own sound event for this track, {@code musicunbound:track.<id>}. Never registered in a registry. */
    public ResourceLocation eventId() {
        return ResourceLocation.fromNamespaceAndPath(MusicUnbound.MOD_ID, "track." + id);
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
}
