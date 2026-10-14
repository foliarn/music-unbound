package com.musicunbound.music;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Era;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public enum Preset {
    ALL(null),
    VANILLA_1_21_1(null),
    VANILLA_LATEST(null),
    ERA_ALPHA_BETA("alpha_beta"),
    ERA_MUSIC_UPDATE("music_update"),
    ERA_AQUATIC("aquatic"),
    ERA_NETHER("nether"),
    ERA_CAVES_CLIFFS("caves_cliffs"),
    ERA_WILD("wild"),
    ERA_TRAILS_TALES("trails_tales"),
    ERA_TRICKY_TRIALS("tricky_trials"),
    ERA_CHASE_THE_SKIES("chase_the_skies"),
    ERA_CHAOS_CUBED("chaos_cubed"),
    NEW_ONLY(null),
    CUSTOM(null);

    @Nullable
    private final String eraId;

    Preset(@Nullable String eraId) {
        this.eraId = eraId;
    }

    @Nullable
    public String eraId() {
        return eraId;
    }

    public Component displayName() {
        String text = switch (this) {
            case ALL -> "All";
            case VANILLA_1_21_1 -> "Vanilla 1.21.1";
            case VANILLA_LATEST -> "Vanilla (latest)";
            case NEW_ONLY -> "New only";
            case CUSTOM -> "Custom";
            default -> {
                Era era = eraObject();
                yield era != null ? era.name() : name();
            }
        };
        return Component.literal(text);
    }

    /** Track ids for the in-game rotation (not yet filtered by availability). */
    public Set<String> gameTracks() {
        TrackCatalog catalog = TrackCatalog.get();
        Set<String> ids = new LinkedHashSet<>();
        if (this == CUSTOM) {
            for (String id : MusicConfig.CUSTOM_GAME_TRACKS.get()) {
                catalog.byId(id).filter(Track::gameAllowed).ifPresent(t -> ids.add(t.id()));
            }
        } else if (this == NEW_ONLY) {
            for (Track t : catalog.tracks()) {
                if (t.remote()) {
                    ids.add(t.id());
                }
            }
        } else if (this == ALL) {
            for (Track t : catalog.tracks()) {
                if (t.gameDefault()) {
                    ids.add(t.id());
                }
            }
        } else if (eraId != null) {
            Era era = eraObject();
            if (era != null) {
                for (Track t : catalog.tracks()) {
                    Era trackEra = catalog.era(t.era());
                    if (t.gameDefault() && trackEra != null && trackEra.order() <= era.order()) {
                        ids.add(t.id());
                    }
                }
            }
        }
        return ids;
    }

    /** Track ids for the title screen (not yet filtered by availability). */
    public Set<String> menuTracks() {
        TrackCatalog catalog = TrackCatalog.get();
        Set<String> ids = new LinkedHashSet<>();
        if (this == CUSTOM) {
            for (String id : MusicConfig.CUSTOM_MENU_TRACKS.get()) {
                catalog.byId(id).ifPresent(t -> ids.add(t.id()));
            }
        } else if (this == NEW_ONLY) {
            for (Track t : catalog.tracks()) {
                if (t.remote()) {
                    ids.add(t.id());
                }
            }
        } else if (this == ALL) {
            for (Track t : catalog.tracks()) {
                if (t.menuAll()) {
                    ids.add(t.id());
                }
            }
        } else if (eraId != null) {
            for (Track t : catalog.tracks()) {
                if (t.menuEras().contains(eraId)) {
                    ids.add(t.id());
                }
            }
        }
        return ids;
    }

    @Nullable
    private Era eraObject() {
        return eraId == null ? null : TrackCatalog.get().era(eraId);
    }
}
