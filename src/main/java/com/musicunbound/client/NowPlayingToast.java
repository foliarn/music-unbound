package com.musicunbound.client;

import com.musicunbound.data.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vanilla-style "Now Playing" toast, laid out like the advancement toast: a music disc icon, a yellow
 * "Now Playing" header, then "Title — Artist". One toast is shared by token, so a new song updates the
 * visible toast instead of stacking another.
 */
public final class NowPlayingToast implements Toast {
    private static final Object TOKEN = new Object();
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final ItemStack ICON = new ItemStack(Items.MUSIC_DISC_CAT);
    private static final Component HEADER = Component.translatable("musicunbound.toast.nowPlaying");
    private static final String DASH = " — ";
    private static final String ELLIPSIS = "...";
    private static final long DISPLAY_TIME_MS = 5000L;
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private static final int ICON_X = 8;
    private static final int ICON_Y = 8;
    private static final int TEXT_X = 30;
    private static final int TEXT_MAX_WIDTH = 124;
    private static final int HEADER_Y = 7;
    private static final int TRACK_Y = 18;
    /** The yellow that AdvancementToast uses for a task's title. */
    private static final int HEADER_COLOR = 0xFFFFFF00;
    private static final int TRACK_COLOR = 0xFFFFFFFF;

    private String trackLine = "";
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
        this.trackLine = fit(font, track.title() + DASH + track.artist());
    }

    private static String fit(Font font, String text) {
        if (font.width(text) <= TEXT_MAX_WIDTH) {
            return text;
        }
        return font.plainSubstrByWidth(text, TEXT_MAX_WIDTH - font.width(ELLIPSIS)) + ELLIPSIS;
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
        guiGraphics.renderFakeItem(ICON, ICON_X, ICON_Y);
        Font font = toastComponent.getMinecraft().font;
        guiGraphics.drawString(font, HEADER, TEXT_X, HEADER_Y, HEADER_COLOR, false);
        guiGraphics.drawString(font, trackLine, TEXT_X, TRACK_Y, TRACK_COLOR, false);

        double displayTime = DISPLAY_TIME_MS * toastComponent.getNotificationDisplayTimeMultiplier();
        return (double) (timeSinceLastVisible - lastChanged) < displayTime ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }
}
