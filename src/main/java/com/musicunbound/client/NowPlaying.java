package com.musicunbound.client;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.music.Playback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the Now Playing toast when a new song starts and keeps music ticking while paused.
 * The tracking itself lives in {@link Playback}, and the pause menu player is {@link PausePlayer}.
 */
public final class NowPlaying {
    /** The instance the toast was last shown for. A new instance (even of the same track) means a new song. */
    @Nullable
    private static SoundInstance shownInstance;

    private NowPlaying() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NowPlaying::onClientTickPost);
    }

    /** The catalog track that is playing right now, or null. Same as {@link Playback#current()}. */
    @Nullable
    public static Track current() {
        return Playback.current();
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        SoundInstance playing = Playback.currentInstance();
        if (playing != null && playing != shownInstance) {
            shownInstance = playing;
            Track track = Playback.current();
            if (track != null && MusicConfig.SHOW_TOAST.get()) {
                NowPlayingToast.show(mc.getToasts(), track);
            }
        }

        // Vanilla skips MusicManager.tick() while paused, so the next song would never start.
        if (MusicConfig.PLAY_WHILE_PAUSED.get() && mc.isPaused() && mc.level != null) {
            mc.getMusicManager().tick();
        }
    }
}
