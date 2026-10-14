package com.musicunbound.client.ui;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Era;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import com.musicunbound.music.MusicDirector;
import com.musicunbound.music.Preset;
import com.musicunbound.music.TrackAvailability;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/** Picks which tracks play in the game and on the title screen, grouped by version. */
public class TracksScreen extends Screen {
    private static final Component TITLE = Component.translatable("musicunbound.screen.tracks");
    private static final int ITEM_HEIGHT = 24;
    private static final int BULK_BUTTON_WIDTH = 74;
    private static final int PREVIEW_WIDTH = 20;
    private static final int TEXT_LEFT = PREVIEW_WIDTH + 4;
    private static final int PREVIEW_GRACE_TICKS = 10;
    private static final String PLAY_ICON = "▶";
    private static final String STOP_ICON = "■";
    private static final FormattedText ELLIPSIS = FormattedText.of("...");

    private enum Kind { GAME, MENU }

    private final Screen lastScreen;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private TracksList list;
    @Nullable
    private Button resetButton;
    private boolean rebuildPending;

    @Nullable
    private Track previewTrack;
    @Nullable
    private SoundInstance preview;
    private int previewAge;

    public TracksScreen(Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        this.list = this.layout.addToContents(new TracksList(this));

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.resetButton = footer.addChild(Button.builder(
                        Component.translatable("musicunbound.tracks.reset"),
                        b -> resetToPreset())
                .width(150)
                .build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(150).build());

        this.layout.visitWidgets(w -> this.addRenderableWidget(w));
        this.repositionElements();
        rebuildList();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void tick() {
        if (rebuildPending) {
            rebuildPending = false;
            rebuildList();
        }
        if (preview == null) {
            return;
        }
        previewAge++;
        if (previewAge > PREVIEW_GRACE_TICKS && !this.minecraft.getSoundManager().isActive(preview)) {
            // The track finished (or could not be loaded).
            stopPreview();
        }
    }

    @Override
    public void removed() {
        stopPreview();
        MusicConfig.SPEC.save();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    /** The version presets show the ALL sets, because the vanilla ones have no mod-managed lists. */
    private static Preset shownPreset(Preset preset) {
        return preset == Preset.VANILLA_1_21_1 || preset == Preset.VANILLA_LATEST ? Preset.ALL : preset;
    }

    private void rebuildList() {
        Preset current = MusicConfig.PRESET.get();
        Preset shown = shownPreset(current);
        if (this.list != null) {
            this.list.rebuild(shown.gameTracks(), shown.menuTracks());
        }
        if (this.resetButton != null) {
            this.resetButton.active = current == Preset.CUSTOM;
        }
    }

    private void resetToPreset() {
        MusicConfig.PRESET.set(Preset.ALL);
        rebuildPending = true;
    }

    /**
     * Sets the Game or Menu flag of these tracks. From a version preset, the shown sets are copied to the custom
     * lists first, and the preset switches to Custom.
     */
    void setTracks(List<Track> tracks, Kind kind, boolean on) {
        Preset current = MusicConfig.PRESET.get();
        Preset shown = shownPreset(current);
        Set<String> game = new LinkedHashSet<>(shown.gameTracks());
        Set<String> menu = new LinkedHashSet<>(shown.menuTracks());
        Set<String> target = kind == Kind.GAME ? game : menu;
        for (Track track : tracks) {
            if (!on) {
                target.remove(track.id());
            } else if (kind == Kind.MENU || track.gameAllowed()) {
                target.add(track.id());
            }
        }
        writeCustomLists(game, menu);
        if (current != Preset.CUSTOM) {
            MusicConfig.PRESET.set(Preset.CUSTOM);
        }
        rebuildPending = true;
    }

    private static void writeCustomLists(Set<String> game, Set<String> menu) {
        List<String> gameIds = new ArrayList<>();
        List<String> menuIds = new ArrayList<>();
        for (Track track : TrackCatalog.get().tracks()) {
            if (game.contains(track.id())) {
                gameIds.add(track.id());
            }
            if (menu.contains(track.id())) {
                menuIds.add(track.id());
            }
        }
        MusicConfig.CUSTOM_GAME_TRACKS.set(gameIds);
        MusicConfig.CUSTOM_MENU_TRACKS.set(menuIds);
    }

    boolean isPreviewing(Track track) {
        return preview != null && track.equals(previewTrack);
    }

    void togglePreview(Track track) {
        boolean wasThisTrack = isPreviewing(track);
        stopPreview();
        if (!wasThisTrack) {
            startPreview(track);
        }
    }

    private void startPreview(Track track) {
        // Background music stays silent while previewing, and resumes right after.
        MusicDirector.setSuppressed(true);
        this.minecraft.getMusicManager().stopPlaying();
        preview = SimpleSoundInstance.forMusic(track.sound().value());
        previewTrack = track;
        previewAge = 0;
        this.minecraft.getSoundManager().play(preview);
        refreshPreviewButtons();
    }

    private void stopPreview() {
        if (preview == null) {
            return;
        }
        this.minecraft.getSoundManager().stop(preview);
        MusicDirector.setSuppressed(false);
        preview = null;
        previewTrack = null;
        refreshPreviewButtons();
    }

    private void refreshPreviewButtons() {
        if (this.list != null) {
            this.list.refreshPreviewButtons();
        }
    }

    private static void drawClipped(GuiGraphics graphics, Font font, FormattedText text, int x, int y, int maxWidth) {
        FormattedText shown = text;
        if (font.width(text) > maxWidth) {
            shown = FormattedText.composite(font.substrByWidth(text, maxWidth - font.width(ELLIPSIS)), ELLIPSIS);
        }
        graphics.drawString(font, Language.getInstance().getVisualOrder(shown), x, y, -1, false);
    }

    /** The scrollable list: one header row per era, then one row per track of that era. */
    private final class TracksList extends ContainerObjectSelectionList<TracksList.Row> {
        private final TracksScreen screen;

        TracksList(TracksScreen screen) {
            super(screen.minecraft, screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), ITEM_HEIGHT);
            this.screen = screen;
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return MusicSettingsScreen.ROW_WIDTH;
        }

        void rebuild(Set<String> shownGame, Set<String> shownMenu) {
            double scroll = this.getScrollAmount();
            this.clearEntries();
            for (Era era : TrackCatalog.get().eras()) {
                List<Track> tracks = TrackCatalog.get().tracks().stream().filter(t -> t.era().equals(era.id())).toList();
                if (tracks.isEmpty()) {
                    continue;
                }
                this.addEntry(new EraRow(era, tracks, shownGame, shownMenu));
                for (Track track : tracks) {
                    this.addEntry(new TrackRow(track, shownGame.contains(track.id()), shownMenu.contains(track.id())));
                }
            }
            this.setScrollAmount(scroll);
        }

        void refreshPreviewButtons() {
            for (Row row : this.children()) {
                if (row instanceof TrackRow trackRow) {
                    trackRow.refreshPreviewButton();
                }
            }
        }

        abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
            protected final List<AbstractWidget> widgets = new ArrayList<>();

            @Override
            public List<? extends GuiEventListener> children() {
                return widgets;
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return widgets;
            }
        }

        /** Header row: era name, and the bulk buttons for Game and Menu. */
        private final class EraRow extends Row {
            private final Era era;
            private final List<Track> tracks;
            private final Button gameButton;
            private final Button menuButton;

            EraRow(Era era, List<Track> tracks, Set<String> shownGame, Set<String> shownMenu) {
                this.era = era;
                this.tracks = tracks;
                List<Track> gameTracks = tracks.stream().filter(Track::gameAllowed).toList();
                boolean allGame = !gameTracks.isEmpty() && gameTracks.stream().allMatch(t -> shownGame.contains(t.id()));
                boolean allMenu = tracks.stream().allMatch(t -> shownMenu.contains(t.id()));

                this.gameButton = Button.builder(
                                Component.translatable(allGame ? "musicunbound.tracks.gameNone" : "musicunbound.tracks.gameAll"),
                                b -> screen.setTracks(gameTracks, Kind.GAME, !allGame))
                        .bounds(0, 0, BULK_BUTTON_WIDTH, 20)
                        .build();
                this.gameButton.active = !gameTracks.isEmpty();
                this.menuButton = Button.builder(
                                Component.translatable(allMenu ? "musicunbound.tracks.menuNone" : "musicunbound.tracks.menuAll"),
                                b -> screen.setTracks(tracks, Kind.MENU, !allMenu))
                        .bounds(0, 0, BULK_BUTTON_WIDTH, 20)
                        .build();
                widgets.add(gameButton);
                widgets.add(menuButton);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                int y = top + (height - 20) / 2;
                int menuX = left + width - BULK_BUTTON_WIDTH;
                int gameX = menuX - 4 - BULK_BUTTON_WIDTH;
                gameButton.setPosition(gameX, y);
                menuButton.setPosition(menuX, y);
                gameButton.render(graphics, mouseX, mouseY, partialTick);
                menuButton.render(graphics, mouseX, mouseY, partialTick);
                drawClipped(graphics, font, FormattedText.of(era.name()), left, top + (height - 9) / 2, gameX - 8 - left);
            }
        }

        /** One track: preview button, title and artist, then the Game and Menu checkboxes. */
        private final class TrackRow extends Row {
            private final Track track;
            private final Button previewButton;
            private final Checkbox gameBox;
            private final Checkbox menuBox;

            TrackRow(Track track, boolean game, boolean menu) {
                this.track = track;
                this.previewButton = Button.builder(
                                Component.literal(PLAY_ICON),
                                b -> screen.togglePreview(track))
                        .bounds(0, 0, PREVIEW_WIDTH, 20)
                        .build();
                this.gameBox = Checkbox.builder(Component.translatable("musicunbound.tracks.game"), font)
                        .selected(game)
                        .onValueChange((box, on) -> screen.setTracks(List.of(track), Kind.GAME, on))
                        .build();
                this.gameBox.active = track.gameAllowed();
                this.menuBox = Checkbox.builder(Component.translatable("musicunbound.tracks.menu"), font)
                        .selected(menu)
                        .onValueChange((box, on) -> screen.setTracks(List.of(track), Kind.MENU, on))
                        .build();
                widgets.add(previewButton);
                widgets.add(gameBox);
                widgets.add(menuBox);
                refreshPreviewButton();
            }

            void refreshPreviewButton() {
                previewButton.setMessage(Component.literal(screen.isPreviewing(track) ? STOP_ICON : PLAY_ICON));
                previewButton.active = TrackAvailability.isAvailable(track);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                previewButton.setPosition(left, top + (height - 20) / 2);
                int menuX = left + width - menuBox.getWidth();
                int gameX = menuX - 8 - gameBox.getWidth();
                gameBox.setPosition(gameX, top + (height - gameBox.getHeight()) / 2);
                menuBox.setPosition(menuX, top + (height - menuBox.getHeight()) / 2);
                previewButton.render(graphics, mouseX, mouseY, partialTick);
                gameBox.render(graphics, mouseX, mouseY, partialTick);
                menuBox.render(graphics, mouseX, mouseY, partialTick);

                MutableComponent name = Component.translatable("musicunbound.tracks.track", track.title(), track.artist());
                if (!TrackAvailability.isAvailable(track)) {
                    name.append(Component.translatable("musicunbound.tracks.notDownloaded").withStyle(ChatFormatting.GRAY));
                }
                drawClipped(graphics, font, name, left + TEXT_LEFT, top + (height - 9) / 2, gameX - 6 - (left + TEXT_LEFT));
            }
        }
    }
}
