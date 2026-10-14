package com.musicunbound.client;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks which catalog track is playing, shows the Now Playing toast, keeps music ticking while paused,
 * and adds the track to the pause menu.
 */
public final class NowPlaying {
    private static final String NOTE = "♪ ";
    private static final String DASH = " — ";
    private static final int PAUSE_LINE_COLOR = 0xFFAAAAAA;

    /** Music sound that was started this frame and is not resolved yet (its {@code getSound()} is still null). */
    @Nullable
    private static SoundInstance candidate;
    @Nullable
    private static SoundInstance currentInstance;
    @Nullable
    private static Track currentTrack;

    private NowPlaying() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NowPlaying::onPlaySound);
        NeoForge.EVENT_BUS.addListener(NowPlaying::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(NowPlaying::onPauseScreenRender);
    }

    /** The catalog track that is playing right now, or null if it is not a catalog track (or nothing plays). */
    @Nullable
    public static Track current() {
        return currentTrack;
    }

    private static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound != null && sound.getSource() == SoundSource.MUSIC) {
            candidate = sound;
        }
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (candidate != null && candidate.getSound() != null) {
            Optional<Track> track = TrackCatalog.get().byFile(candidate.getSound().getLocation());
            if (track.isPresent() && candidate != currentInstance) {
                currentInstance = candidate;
                currentTrack = track.get();
                if (MusicConfig.SHOW_TOAST.get()) {
                    NowPlayingToast.show(mc.getToasts(), track.get());
                }
            }
            candidate = null;
        }

        if (currentInstance != null && !mc.getSoundManager().isActive(currentInstance)) {
            currentInstance = null;
            currentTrack = null;
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
        Track track = current();
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
