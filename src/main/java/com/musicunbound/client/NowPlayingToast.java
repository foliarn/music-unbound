package com.musicunbound.client;

import com.musicunbound.data.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Vanilla-style "Now Playing" toast: title on the first line, artist on the second.
 * One toast is shared by token, so a new song updates the visible toast instead of stacking another.
 */
public final class NowPlayingToast implements Toast {
    private static final Object TOKEN = new Object();
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/system");
    private static final long DISPLAY_TIME_MS = 5000L;
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private static final int TEXT_X = 18;
    private static final int TEXT_MAX_WIDTH = 130;
    private static final int TITLE_Y = 7;
    private static final int ARTIST_Y = 18;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int ARTIST_COLOR = 0xFFAAAAAA;

    private String titleLine = "";
    private String artistLine = "";
    private boolean changed;
    private long lastChanged;

    private NowPlayingToast(Track track) {
        setTrack(track);
    }

    /** Shows the toast for a new track, or updates and re-times the one already on screen. */
    public static void show(ToastComponent toasts, Track track) {
        NowPlayingToast existing = toasts.getToast(NowPlayingToast.class, TOKEN);
        if (existing == null) {
            toasts.addToast(new NowPlayingToast(track));
        } else {
            existing.update(track);
        }
    }

    private void update(Track track) {
        setTrack(track);
        changed = true;
    }

    private void setTrack(Track track) {
        Font font = Minecraft.getInstance().font;
        this.titleLine = fit(font, "♪ " + track.title());
        this.artistLine = fit(font, track.artist());
    }

    private static String fit(Font font, String text) {
        if (font.width(text) <= TEXT_MAX_WIDTH) {
            return text;
        }
        return font.plainSubstrByWidth(text, TEXT_MAX_WIDTH - font.width("...")) + "...";
    }

    @Override
    public Object getToken() {
        return TOKEN;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Toast.Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        // Restart the display timer when the track changed (same approach as SystemToast).
        if (changed) {
            lastChanged = timeSinceLastVisible;
            changed = false;
        }

        guiGraphics.blitSprite(BACKGROUND, 0, 0, WIDTH, HEIGHT);
        Font font = toastComponent.getMinecraft().font;
        guiGraphics.drawString(font, titleLine, TEXT_X, TITLE_Y, TITLE_COLOR, false);
        guiGraphics.drawString(font, artistLine, TEXT_X, ARTIST_Y, ARTIST_COLOR, false);

        double displayTime = DISPLAY_TIME_MS * toastComponent.getNotificationDisplayTimeMultiplier();
        return (double) (timeSinceLastVisible - lastChanged) < displayTime ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }
}
