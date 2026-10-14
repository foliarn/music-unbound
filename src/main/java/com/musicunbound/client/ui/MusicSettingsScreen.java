package com.musicunbound.client.ui;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.music.Frequency;
import com.musicunbound.music.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The Music Unbound settings: opened from Options > Music & Sounds and from the mod list. */
public class MusicSettingsScreen extends OptionsSubScreen {
    static final int ROW_WIDTH = 310;
    private static final Component TITLE = Component.translatable("musicunbound.screen.settings");
    private static final int MAX_MINUTES = 60;

    private AbstractWidget customMin;
    private AbstractWidget customMax;

    public MusicSettingsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        Component presetCaption = Component.translatable("musicunbound.option.preset");
        CycleButton<Preset> preset = CycleButton.<Preset>builder(p -> p.displayName())
                .withValues(Preset.values())
                .withInitialValue(MusicConfig.PRESET.get())
                .create(0, 0, ROW_WIDTH, 20, presetCaption, (button, value) -> MusicConfig.PRESET.set(value));
        this.list.addSmall(preset, null);

        Component frequencyCaption = Component.translatable("musicunbound.option.frequency");
        CycleButton<Frequency> frequency = CycleButton.<Frequency>builder(f -> f.displayName())
                .withValues(Frequency.values())
                .withInitialValue(MusicConfig.FREQUENCY.get())
                .create(0, 0, ROW_WIDTH, 20, frequencyCaption, (button, value) -> {
                    MusicConfig.FREQUENCY.set(value);
                    updateCustomSliders();
                });
        this.list.addSmall(frequency, null);

        OptionInstance<Integer> minOption = minutesOption("musicunbound.option.customMin", MusicConfig.CUSTOM_MIN_MINUTES);
        OptionInstance<Integer> maxOption = minutesOption("musicunbound.option.customMax", MusicConfig.CUSTOM_MAX_MINUTES);
        this.list.addBig(minOption);
        this.list.addBig(maxOption);
        this.customMin = this.list.findOption(minOption);
        this.customMax = this.list.findOption(maxOption);
        updateCustomSliders();

        this.list.addSmall(
                keepsMusicToggle("musicunbound.option.netherKeepsMusic", "musicunbound.tooltip.netherKeepsMusic", MusicConfig.NETHER_KEEPS_MUSIC),
                keepsMusicToggle("musicunbound.option.endKeepsMusic", "musicunbound.tooltip.endKeepsMusic", MusicConfig.END_KEEPS_MUSIC),
                keepsMusicToggle("musicunbound.option.deepDarkKeepsMusic", "musicunbound.tooltip.deepDarkKeepsMusic", MusicConfig.DEEP_DARK_KEEPS_MUSIC),
                keepsMusicToggle("musicunbound.option.underwaterKeepsMusic", "musicunbound.tooltip.underwaterKeepsMusic", MusicConfig.UNDERWATER_KEEPS_MUSIC));

        this.list.addSmall(
                toggle("musicunbound.option.showToast", MusicConfig.SHOW_TOAST),
                toggle("musicunbound.option.playWhilePaused", MusicConfig.PLAY_WHILE_PAUSED),
                toggle("musicunbound.option.showInPauseMenu", MusicConfig.SHOW_IN_PAUSE_MENU),
                toggle("musicunbound.option.downloadNewTracks", MusicConfig.DOWNLOAD_NEW_TRACKS));

        Button tracks = Button.builder(
                        Component.translatable("musicunbound.option.tracks"),
                        b -> this.minecraft.setScreen(new TracksScreen(this)))
                .width(ROW_WIDTH)
                .build();
        this.list.addSmall(tracks, null);
    }

    /** The custom min/max sliders only apply when the frequency is Custom. */
    private void updateCustomSliders() {
        boolean custom = MusicConfig.FREQUENCY.get() == Frequency.CUSTOM;
        this.customMin.active = custom;
        this.customMax.active = custom;
    }

    private static OptionInstance<Integer> minutesOption(String caption, ModConfigSpec.IntValue value) {
        return new OptionInstance<Integer>(
                caption,
                OptionInstance.<Integer>noTooltip(),
                (captionText, minutes) -> CommonComponents.optionNameValue(captionText, Component.translatable("musicunbound.value.minutes", minutes)),
                new OptionInstance.IntRange(0, MAX_MINUTES),
                value.get(),
                value::set);
    }

    private static OptionInstance<Boolean> toggle(String caption, ModConfigSpec.BooleanValue value) {
        return OptionInstance.createBoolean(caption, OptionInstance.<Boolean>noTooltip(), value.get(), value::set);
    }

    /** true = the dimension keeps its own vanilla music ("Own"), false = it plays the playlist ("Shared"). */
    private static OptionInstance<Boolean> keepsMusicToggle(String caption, String tooltip, ModConfigSpec.BooleanValue value) {
        return OptionInstance.createBoolean(
                caption,
                OptionInstance.cachedConstantTooltip(Component.translatable(tooltip)),
                (captionText, own) -> Component.translatable(own ? "musicunbound.value.own" : "musicunbound.value.shared"),
                value.get(),
                value::set);
    }

    /** Config is written on each change; saving to disk happens when this screen (or the tracks screen above it) closes. */
    @Override
    public void removed() {
        MusicConfig.SPEC.save();
        super.removed();
    }
}
