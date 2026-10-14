package com.musicunbound.client.ui;

import com.musicunbound.config.MusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Where the vanilla music is kept or shared, and the events that start a song by themselves. */
public class PlacesScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("musicunbound.screen.places");

    public PlacesScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(
                SettingOptions.ownOrShared("musicunbound.option.netherKeepsMusic", "musicunbound.tooltip.netherKeepsMusic", MusicConfig.NETHER_KEEPS_MUSIC),
                SettingOptions.ownOrShared("musicunbound.option.endKeepsMusic", "musicunbound.tooltip.endKeepsMusic", MusicConfig.END_KEEPS_MUSIC));
        this.list.addSmall(
                SettingOptions.ownOrShared("musicunbound.option.deepDarkKeepsMusic", "musicunbound.tooltip.deepDarkKeepsMusic", MusicConfig.DEEP_DARK_KEEPS_MUSIC),
                SettingOptions.ownOrShared("musicunbound.option.underwaterKeepsMusic", "musicunbound.tooltip.underwaterKeepsMusic", MusicConfig.UNDERWATER_KEEPS_MUSIC));

        this.list.addBig(SettingOptions.toggle("musicunbound.option.eventDimension", "musicunbound.tooltip.eventDimension", MusicConfig.EVENT_DIMENSION));
        this.list.addBig(SettingOptions.toggle("musicunbound.option.eventTime", "musicunbound.tooltip.eventTime", MusicConfig.EVENT_TIME));
        this.list.addBig(SettingOptions.toggle("musicunbound.option.eventWeather", "musicunbound.tooltip.eventWeather", MusicConfig.EVENT_WEATHER));
        this.list.addBig(SettingOptions.minutes("musicunbound.option.eventCooldown", "musicunbound.tooltip.eventCooldown", MusicConfig.EVENT_COOLDOWN_MINUTES));
    }

    @Override
    public void removed() {
        MusicConfig.SPEC.save();
        super.removed();
    }
}
