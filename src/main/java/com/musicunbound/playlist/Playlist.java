package com.musicunbound.playlist;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * A named track selection with one set for the in-game rotation ({@code game}) and one for the title screen
 * ({@code menu}). Built-in playlists have ids starting with {@code builtin:} and are read-only; user playlists have
 * ids starting with {@code user:}. The sets are unmodifiable copies that keep their insertion order.
 */
public record Playlist(String id, String name, Kind kind, @Nullable String eraId, Set<String> game, Set<String> menu) {

    public enum Kind {
        NORMAL,
        VANILLA_1_21_1,
        VANILLA_LATEST
    }

    public Playlist {
        game = Collections.unmodifiableSet(new LinkedHashSet<>(game));
        menu = Collections.unmodifiableSet(new LinkedHashSet<>(menu));
    }

    public boolean builtIn() {
        return id.startsWith("builtin:");
    }
}
