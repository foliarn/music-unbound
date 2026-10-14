package com.musicunbound.client.ui;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.music.Frequency;
import com.musicunbound.playlist.PlaylistStore;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * The Music Unbound main screen: opened from Options > Music & Sounds and from the mod list. The playlist, places and
 * extras have their own screens.
 */
public class MusicSettingsScreen extends OptionsSubScreen {
    static final int ROW_WIDTH = 310;
    private static final int SMALL_WIDTH = 150;
    private static final Component TITLE = Component.translatable("musicunbound.screen.settings");

    private SettingsList settings;
    /** Set when the screen is shown again (the playlist may have changed) or the frequency changed. Applied on the next tick. */
    private boolean refreshPending;

    public MusicSettingsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addContents() {
        this.settings = this.layout.addToContents(new SettingsList(this.minecraft, this.width, this));
        this.list = this.settings;
        this.addOptions();
    }

    @Override
    protected void addOptions() {
        Button playlist = Button.builder(
                        Component.translatable("musicunbound.option.playlist", PlaylistStore.active().name()),
                        b -> this.minecraft.setScreen(new PlaylistScreen(this)))
                .width(ROW_WIDTH)
                .tooltip(Tooltip.create(Component.translatable("musicunbound.tooltip.playlist")))
                .build();
        this.list.addSmall(playlist, null);

        CycleButton<Frequency> frequency = CycleButton.<Frequency>builder(f -> f.displayName())
                .withValues(Frequency.values())
                .withInitialValue(MusicConfig.FREQUENCY.get())
                .withTooltip(f -> Tooltip.create(Component.translatable("musicunbound.tooltip.frequency." + f.name().toLowerCase(Locale.ROOT))))
                .create(0, 0, ROW_WIDTH, 20, Component.translatable("musicunbound.option.frequency"), (button, value) -> {
                    MusicConfig.FREQUENCY.set(value);
                    this.refreshPending = true;
                });
        this.list.addSmall(frequency, null);

        // The custom sliders only exist while the frequency is Custom.
        if (MusicConfig.FREQUENCY.get() == Frequency.CUSTOM) {
            this.list.addBig(SettingOptions.minutes("musicunbound.option.customMin", null, MusicConfig.CUSTOM_MIN_MINUTES));
            this.list.addBig(SettingOptions.minutes("musicunbound.option.customMax", null, MusicConfig.CUSTOM_MAX_MINUTES));
        }

        Button places = Button.builder(
                        Component.translatable("musicunbound.option.places"),
                        b -> this.minecraft.setScreen(new PlacesScreen(this)))
                .width(SMALL_WIDTH)
                .tooltip(Tooltip.create(Component.translatable("musicunbound.tooltip.places")))
                .build();
        Button extras = Button.builder(
                        Component.translatable("musicunbound.option.extras"),
                        b -> this.minecraft.setScreen(new ExtrasScreen(this)))
                .width(SMALL_WIDTH)
                .tooltip(Tooltip.create(Component.translatable("musicunbound.tooltip.extras")))
                .build();
        this.list.addSmall(places, extras);
    }

    @Override
    protected void init() {
        super.init();
        this.refreshPending = false;
    }

    @Override
    public void added() {
        super.added();
        // Vanilla does not run init() again when a screen is shown a second time, so the rows are refreshed instead.
        this.refreshPending = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.refreshPending) {
            this.refreshPending = false;
            this.refresh();
        }
    }

    /** Rebuilds the rows in place. The layout keeps its widgets, so the screen itself is not initialised again. */
    private void refresh() {
        double scroll = this.settings.getScrollAmount();
        this.settings.clearRows();
        this.addOptions();
        this.settings.setScrollAmount(scroll);
    }

    /** Config is written on each change; saving to disk happens when this screen closes. */
    @Override
    public void removed() {
        MusicConfig.SPEC.save();
        super.removed();
    }

    /** An options list whose rows can be cleared and added again. */
    private static final class SettingsList extends OptionsList {
        SettingsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
            super(minecraft, width, screen);
        }

        void clearRows() {
            this.setFocused(null);
            this.clearEntries();
        }
    }
}
