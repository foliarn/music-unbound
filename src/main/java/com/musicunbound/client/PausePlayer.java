package com.musicunbound.client;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Track;
import com.musicunbound.music.Playback;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The music player in the pause menu: previous, play/pause and next buttons, the current track, and a progress bar.
 * It is a 182 x 48 block. It sits centered in the free space below the vanilla buttons when there are at least
 * 48 px free, and in the top-left corner otherwise.
 */
public final class PausePlayer {
    private static final int WIDTH = 182;
    private static final int HEIGHT = 48;
    private static final int CORNER = 8;
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTONS_WIDTH = 3 * BUTTON_SIZE + 2 * BUTTON_GAP;
    private static final int TITLE_Y = 0;
    private static final int TIMES_Y = 10;
    private static final int BAR_Y = 21;
    private static final int BAR_HEIGHT = 5;
    private static final int BUTTONS_Y = 28;
    private static final int BAR_BACKGROUND = 0xFF000000;
    private static final int BAR_FILL = 0xFF80FF20;
    private static final int TRACK_COLOR = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final String NOTE = "♪ ";
    private static final String DASH = " — ";
    private static final String ELLIPSIS = "...";

    private static final Component PREVIOUS_LABEL = Component.literal("◀◀");
    private static final Component PAUSE_LABEL = Component.literal("||");
    private static final Component PLAY_LABEL = Component.literal("▶");
    private static final Component NEXT_LABEL = Component.literal("▶▶");
    private static final Component PREVIOUS_TIP = Component.translatable("key.musicunbound.previous");
    private static final Tooltip PAUSE_TIP = Tooltip.create(Component.translatable("musicunbound.player.pause"));
    private static final Tooltip RESUME_TIP = Tooltip.create(Component.translatable("musicunbound.player.resume"));
    private static final Component NEXT_TIP = Component.translatable("key.musicunbound.next");
    private static final Component NOTHING = Component.translatable("musicunbound.player.nothing");

    /** The player's position on the current pause screen, and its buttons. */
    private record Layout(int x, int y, Button previous, Button toggle, Button next) {
    }

    /** Null when the player is not shown (config off, or a pause screen without the menu). */
    @Nullable
    private static Layout layout;

    private PausePlayer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PausePlayer::onInitPost);
        NeoForge.EVENT_BUS.addListener(PausePlayer::onRenderPre);
        NeoForge.EVENT_BUS.addListener(PausePlayer::onRenderPost);
    }

    /** Runs on every pause screen init, including window resizes (Screen.resize rebuilds the widgets). */
    private static void onInitPost(ScreenEvent.Init.Post event) {
        layout = null;
        if (!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu() || !MusicConfig.SHOW_IN_PAUSE_MENU.get()) {
            return;
        }

        int bottom = 0;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget) {
                bottom = Math.max(bottom, widget.getY() + widget.getHeight());
            }
        }

        int x;
        int y;
        int free = screen.height - bottom;
        if (free >= HEIGHT) {
            x = (screen.width - WIDTH) / 2;
            y = bottom + (free - HEIGHT) / 2;
        } else {
            x = CORNER;
            y = CORNER;
        }

        int buttonsX = x + (WIDTH - BUTTONS_WIDTH) / 2;
        Button previous = Button.builder(PREVIOUS_LABEL, button -> Playback.previous())
                .bounds(buttonsX, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Tooltip.create(PREVIOUS_TIP))
                .build();
        Button toggle = Button.builder(Playback.isPaused() ? PLAY_LABEL : PAUSE_LABEL, button -> Playback.togglePause())
                .bounds(buttonsX + BUTTON_SIZE + BUTTON_GAP, y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Playback.isPaused() ? RESUME_TIP : PAUSE_TIP)
                .build();
        Button next = Button.builder(NEXT_LABEL, button -> Playback.next())
                .bounds(buttonsX + 2 * (BUTTON_SIZE + BUTTON_GAP), y + BUTTONS_Y, BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Tooltip.create(NEXT_TIP))
                .build();
        event.addListener(previous);
        event.addListener(toggle);
        event.addListener(next);

        layout = new Layout(x, y, previous, toggle, next);
    }

    /** Button states are set before the screen draws, so this frame's buttons already show them. */
    private static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (layout == null || !(event.getScreen() instanceof PauseScreen)) {
            return;
        }
        Button toggle = layout.toggle();
        layout.previous().active = canGoBack();
        toggle.active = Playback.currentInstance() != null;
        Component label = Playback.isPaused() ? PLAY_LABEL : PAUSE_LABEL;
        if (toggle.getMessage() != label) {
            toggle.setMessage(label);
            toggle.setTooltip(Playback.isPaused() ? RESUME_TIP : PAUSE_TIP);
        }
    }

    private static void onRenderPost(ScreenEvent.Render.Post event) {
        if (layout == null || !(event.getScreen() instanceof PauseScreen)) {
            return;
        }
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int x = layout.x();
        int y = layout.y();
        int centerX = x + WIDTH / 2;
        Track track = Playback.current();
        int length = track != null ? track.length() : 0;

        if (track != null) {
            guiGraphics.drawCenteredString(font, fit(font, NOTE + track.title() + DASH + track.artist()), centerX, y + TITLE_Y, TRACK_COLOR);
        } else {
            guiGraphics.drawCenteredString(font, NOTHING, centerX, y + TITLE_Y, GRAY);
        }

        int elapsed = Playback.elapsedSeconds();
        guiGraphics.drawString(font, formatTime(elapsed), x, y + TIMES_Y, GRAY, false);
        if (length > 0) {
            String total = formatTime(length);
            guiGraphics.drawString(font, total, x + WIDTH - font.width(total), y + TIMES_Y, GRAY, false);
        }

        int barY = y + BAR_Y;
        guiGraphics.fill(x, barY, x + WIDTH, barY + BAR_HEIGHT, BAR_BACKGROUND);
        double progress = length > 0 ? Math.min(1.0, Math.max(0.0, elapsed / (double) length)) : 0.0;
        int filled = (int) Math.round((WIDTH - 2) * progress);
        if (filled > 0) {
            guiGraphics.fill(x + 1, barY + 1, x + 1 + filled, barY + BAR_HEIGHT - 1, BAR_FILL);
        }
    }

    /** True when {@link Playback#previous()} would replay something: a history entry other than the current track. */
    private static boolean canGoBack() {
        Track current = Playback.current();
        for (Track track : Playback.history()) {
            if (current == null || !track.id().equals(current.id())) {
                return true;
            }
        }
        return false;
    }

    private static String fit(Font font, String text) {
        if (font.width(text) <= WIDTH) {
            return text;
        }
        return font.plainSubstrByWidth(text, WIDTH - font.width(ELLIPSIS)) + ELLIPSIS;
    }

    private static String formatTime(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
