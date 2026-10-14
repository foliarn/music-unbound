package com.musicunbound.client;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.music.Playback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the Now Playing toast when a new song starts, keeps music ticking while paused,
 * and adds the current track to the pause menu. The tracking itself lives in {@link Playback}.
 */
public final class NowPlaying {
    private static final String NOTE = "♪ ";
    private static final String DASH = " — ";
    private static final int PAUSE_LINE_COLOR = 0xFFAAAAAA;

    /** The instance the toast was last shown for. A new instance (even of the same track) means a new song. */
    @Nullable
    private static SoundInstance shownInstance;

    private NowPlaying() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NowPlaying::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(NowPlaying::onPauseScreenRender);
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

    private static void onPauseScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || !MusicConfig.SHOW_IN_PAUSE_MENU.get()) {
            return;
        }
        Track track = Playback.current();
        if (track == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = NOTE + track.title() + DASH + track.artist();
        int x = (event.getScreen().width - font.width(text)) / 2;
        int y = event.getScreen().height - 12;
        GuiGraphics guiGraphics = event.getGuiGraphics();
        guiGraphics.drawString(font, text, x, y, PAUSE_LINE_COLOR, true);
    }
}
