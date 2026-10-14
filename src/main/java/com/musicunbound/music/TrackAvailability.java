package com.musicunbound.music;

import com.musicunbound.data.Track;
import java.util.function.Predicate;

/** Whether a track can be played. Remote tracks need their file to be downloaded (M3 replaces the predicate). */
public final class TrackAvailability {
    public static volatile Predicate<String> remoteAvailable = id -> false;

    private TrackAvailability() {
    }

    public static boolean isAvailable(Track track) {
        return !track.remote() || remoteAvailable.test(track.id());
    }
}
