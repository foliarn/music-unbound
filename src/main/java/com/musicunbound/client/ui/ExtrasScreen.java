package com.musicunbound.client.ui;

import com.musicunbound.config.MusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** The Now Playing toast, pausing, fades and downloads. */
public class ExtrasScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("musicunbound.screen.extras");

    public ExtrasScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(
                SettingOptions.toggle("musicunbound.option.showToast", "musicunbound.tooltip.showToast", MusicConfig.SHOW_TOAST),
                SettingOptions.toggle("musicunbound.option.playWhilePaused", "musicunbound.tooltip.playWhilePaused", MusicConfig.PLAY_WHILE_PAUSED));
        this.list.addSmall(
                SettingOptions.toggle("musicunbound.option.showInPauseMenu", "musicunbound.tooltip.showInPauseMenu", MusicConfig.SHOW_IN_PAUSE_MENU),
                SettingOptions.toggle("musicunbound.option.downloadNewTracks", "musicunbound.tooltip.downloadNewTracks", MusicConfig.DOWNLOAD_NEW_TRACKS));
        this.list.addSmall(
                SettingOptions.toggle("musicunbound.option.smoothFades", "musicunbound.tooltip.smoothFades", MusicConfig.SMOOTH_FADES),
                SettingOptions.toggle("musicunbound.option.jukeboxFade", "musicunbound.tooltip.jukeboxFade", MusicConfig.JUKEBOX_FADE));
    }

    @Override
    public void removed() {
        MusicConfig.SPEC.save();
        super.removed();
    }
}
