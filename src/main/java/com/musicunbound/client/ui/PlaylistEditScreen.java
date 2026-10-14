package com.musicunbound.client.ui;

import com.musicunbound.data.Era;
import com.musicunbound.data.Track;
import com.musicunbound.data.TrackCatalog;
import com.musicunbound.music.Playback;
import com.musicunbound.music.TrackAvailability;
import com.musicunbound.playlist.Playlist;
import com.musicunbound.playlist.PlaylistStore;
import com.musicunbound.remote.RemoteTrackProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Edits one playlist: the songs for the game and for the title screen, grouped by version. Built-in playlists are
 * read-only and can be copied with "Duplicate to edit". Play now starts a song at once and it becomes the background
 * music.
 */
public class PlaylistEditScreen extends Screen {
    private static final int HEADER_HEIGHT = 40;
    private static final int SEARCH_WIDTH = 220;
    private static final int ITEM_HEIGHT = 24;
    private static final int MAX_ROW_WIDTH = 400;
    private static final int SIDE_MARGIN = 40;
    private static final int BUTTON_SIZE = 20;
    private static final int BULK_WIDTH = 80;
    private static final int STAR_LEFT = 24;
    private static final int TEXT_LEFT = 48;
    private static final int GAP = 6;
    private static final int HIGHLIGHT = 0x40FFD700;
    private static final int CURRENT_TEXT = 0xFFFFD700;
    private static final int WHITE = -1;
    private static final String ALL_ID = "builtin:all";
    private static final String PLAY_ICON = "▶";
    private static final String STOP_ICON = "■";
    private static final String FAVOURITE_ON = "★";
    private static final String FAVOURITE_OFF = "☆";

    private enum Kind { GAME, MENU }

    private final Screen lastScreen;
    private final String playlistId;
    private final boolean editable;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private TrackList list;
    private String filter = "";
    private boolean scrollToTop;
    private boolean rebuildPending;
    @Nullable
    private String shownCurrent;
    private int shownAvailability;

    public PlaylistEditScreen(Screen lastScreen, String playlistId) {
        super(titleOf(playlistId));
        this.lastScreen = lastScreen;
        this.playlistId = playlistId;
        this.editable = PlaylistStore.byId(playlistId).map(p -> !p.builtIn()).orElse(false);
    }

    private static Component titleOf(String playlistId) {
        return PlaylistStore.byId(playlistId)
                .<Component>map(p -> Component.literal(p.name()))
                .orElse(Component.translatable("musicunbound.screen.playlists"));
    }

    @Override
    protected void init() {
        this.layout.setHeaderHeight(HEADER_HEIGHT);

        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));
        EditBox search = header.addChild(new EditBox(this.font, SEARCH_WIDTH, BUTTON_SIZE, Component.translatable("musicunbound.tracks.search")));
        search.setHint(Component.translatable("musicunbound.tracks.search"));
        search.setResponder(text -> {
            this.filter = text.trim().toLowerCase(Locale.ROOT);
            this.scrollToTop = true;
            this.rebuildPending = true;
        });

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        if (!this.editable) {
            footer.addChild(Button.builder(Component.translatable("musicunbound.playlist.duplicate"), b -> duplicate())
                    .width(150)
                    .tooltip(Tooltip.create(Component.translatable("musicunbound.playlist.tip.duplicate")))
                    .build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(150).build());

        this.list = this.layout.addToContents(new TrackList(this));
        this.layout.visitWidgets(w -> this.addRenderableWidget(w));
        this.repositionElements();
        this.rebuildList();
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
        super.tick();
        // Playing song, download status and the track list change without a click: rebuild when they do.
        if (!Objects.equals(currentId(), this.shownCurrent) || availabilityKey() != this.shownAvailability) {
            this.rebuildPending = true;
        }
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.rebuildList();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    /** Copies this built-in playlist into a new user playlist, makes it active and opens its editor. */
    private void duplicate() {
        Optional<Playlist> source = PlaylistStore.byId(this.playlistId);
        if (source.isEmpty()) {
            return;
        }
        Playlist copy = PlaylistStore.create(Component.translatable("musicunbound.playlist.copyOf", source.get().name()).getString(), this.playlistId);
        PlaylistStore.setActive(copy.id());
        this.minecraft.setScreen(new PlaylistEditScreen(this.lastScreen, copy.id()));
    }

    /** Rebuilds the rows from the store. The scroll position is kept, unless the search text changed. */
    private void rebuildList() {
        this.shownCurrent = currentId();
        this.shownAvailability = availabilityKey();
        if (this.list == null) {
            return;
        }
        Set<String> game = Set.of();
        Set<String> menu = Set.of();
        Optional<Playlist> playlist = PlaylistStore.byId(this.playlistId);
        if (playlist.isPresent()) {
            // Vanilla playlists have no sets of their own: they show the All sets, as the mod plays them as All.
            Playlist shown = playlist.get().kind() == Playlist.Kind.NORMAL
                    ? playlist.get()
                    : PlaylistStore.byId(ALL_ID).orElse(playlist.get());
            game = shown.game();
            menu = shown.menu();
        }
        this.list.rebuild(game, menu);
        this.scrollToTop = false;
    }

    /** Changes the Game or Menu flag of these tracks. The rows are rebuilt on the next tick. */
    void setTracks(Collection<Track> tracks, Kind kind, boolean on) {
        Optional<Playlist> playlist = PlaylistStore.byId(this.playlistId);
        if (!this.editable || playlist.isEmpty()) {
            return;
        }
        Set<String> game = new LinkedHashSet<>(playlist.get().game());
        Set<String> menu = new LinkedHashSet<>(playlist.get().menu());
        for (Track track : tracks) {
            if (kind == Kind.GAME) {
                if (!on) {
                    game.remove(track.id());
                } else if (track.gameAllowed()) {
                    game.add(track.id());
                }
            } else if (on) {
                menu.add(track.id());
            } else {
                menu.remove(track.id());
            }
        }
        PlaylistStore.setTracks(this.playlistId, game, menu);
        this.rebuildPending = true;
    }

    private List<Track> visibleTracks(Era era) {
        return TrackCatalog.get().tracks().stream()
                .filter(t -> t.era().equals(era.id()))
                .filter(this::matchesFilter)
                .toList();
    }

    private boolean matchesFilter(Track track) {
        if (this.filter.isEmpty()) {
            return true;
        }
        return track.title().toLowerCase(Locale.ROOT).contains(this.filter)
                || track.artist().toLowerCase(Locale.ROOT).contains(this.filter);
    }

    @Nullable
    private static String currentId() {
        Track track = Playback.current();
        return track != null ? track.id() : null;
    }

    private static boolean isCurrent(Track track) {
        return track.id().equals(currentId());
    }

    /** Changes when a download finishes or a new track appears, so the rows show the new state. */
    private static int availabilityKey() {
        List<Track> tracks = TrackCatalog.get().tracks();
        int key = tracks.size();
        for (Track track : tracks) {
            int state = (TrackAvailability.isAvailable(track) ? 1 : 0) + (RemoteTrackProvider.isFoundThisSession(track.id()) ? 2 : 0);
            key = key * 31 + state;
        }
        return key;
    }

    private static String formatLength(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** The scrollable list: one header row per era, then one row per track of that era. */
    private final class TrackList extends ContainerObjectSelectionList<TrackList.Row> {
        TrackList(PlaylistEditScreen screen) {
            super(screen.minecraft, screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), ITEM_HEIGHT);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return Math.min(MAX_ROW_WIDTH, PlaylistEditScreen.this.width - SIDE_MARGIN);
        }

        void rebuild(Set<String> game, Set<String> menu) {
            double scroll = PlaylistEditScreen.this.scrollToTop ? 0.0 : this.getScrollAmount();
            this.clearEntries();
            for (Era era : TrackCatalog.get().eras()) {
                List<Track> tracks = visibleTracks(era);
                if (tracks.isEmpty()) {
                    continue;
                }
                this.addEntry(new EraRow(era, tracks, game, menu));
                for (Track track : tracks) {
                    this.addEntry(new TrackRow(track, game.contains(track.id()), menu.contains(track.id()), isCurrent(track)));
                }
            }
            this.setScrollAmount(scroll);
        }

        @Nullable
        TrackRow hoveredTrackRow() {
            Row row = this.getHovered();
            return row instanceof TrackRow trackRow ? trackRow : null;
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

        /** Era name, plus the Game and Menu bulk buttons for user playlists. */
        private final class EraRow extends Row {
            private final Era era;
            @Nullable
            private Button gameButton;
            @Nullable
            private Button menuButton;

            EraRow(Era era, List<Track> tracks, Set<String> game, Set<String> menu) {
                this.era = era;
                if (PlaylistEditScreen.this.editable) {
                    List<Track> gameTracks = tracks.stream().filter(Track::gameAllowed).toList();
                    boolean allGame = !gameTracks.isEmpty() && gameTracks.stream().allMatch(t -> game.contains(t.id()));
                    boolean allMenu = tracks.stream().allMatch(t -> menu.contains(t.id()));

                    this.gameButton = Button.builder(
                                    Component.translatable(allGame ? "musicunbound.tracks.gameNone" : "musicunbound.tracks.gameAll"),
                                    b -> PlaylistEditScreen.this.setTracks(gameTracks, Kind.GAME, !allGame))
                            .bounds(0, 0, BULK_WIDTH, BUTTON_SIZE)
                            .build();
                    this.gameButton.active = !gameTracks.isEmpty();
                    this.menuButton = Button.builder(
                                    Component.translatable(allMenu ? "musicunbound.tracks.menuNone" : "musicunbound.tracks.menuAll"),
                                    b -> PlaylistEditScreen.this.setTracks(tracks, Kind.MENU, !allMenu))
                            .bounds(0, 0, BULK_WIDTH, BUTTON_SIZE)
                            .build();
                    widgets.add(this.gameButton);
                    widgets.add(this.menuButton);
                }
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                int textRight = left + width;
                if (this.gameButton != null && this.menuButton != null) {
                    int y = top + (height - BUTTON_SIZE) / 2;
                    int menuX = left + width - BULK_WIDTH;
                    int gameX = menuX - 4 - BULK_WIDTH;
                    this.gameButton.setPosition(gameX, y);
                    this.menuButton.setPosition(menuX, y);
                    this.gameButton.render(graphics, mouseX, mouseY, partialTick);
                    this.menuButton.render(graphics, mouseX, mouseY, partialTick);
                    textRight = gameX - 8;
                }
                UiText.draw(graphics, PlaylistEditScreen.this.font, Component.literal(this.era.name()), left, top + (height - 9) / 2, textRight - left, WHITE, false);
            }
        }

        /** One track: play button, favourite star, title and artist, then the Game and Menu checkboxes. */
        private final class TrackRow extends Row {
            private final Track track;
            private final boolean current;
            private final Button playButton;
            private final Button favouriteButton;
            private final Checkbox gameBox;
            private final Checkbox menuBox;
            /** Where the title was drawn last frame, for the tooltip. */
            private int titleLeft;
            private int titleRight;
            private int rowTop;
            private int rowBottom;

            TrackRow(Track track, boolean game, boolean menu, boolean current) {
                this.track = track;
                this.current = current;
                boolean editable = PlaylistEditScreen.this.editable;

                this.playButton = Button.builder(Component.literal(current ? STOP_ICON : PLAY_ICON), b -> play())
                        .bounds(0, 0, BUTTON_SIZE, BUTTON_SIZE)
                        .tooltip(Tooltip.create(Component.translatable(current ? "musicunbound.tracks.skip" : "musicunbound.tracks.playNow")))
                        .build();
                this.playButton.active = current || TrackAvailability.isAvailable(track);

                this.favouriteButton = Button.builder(Component.literal(favouriteLabel()), b -> toggleFavourite())
                        .bounds(0, 0, BUTTON_SIZE, BUTTON_SIZE)
                        .tooltip(Tooltip.create(Component.translatable("musicunbound.tracks.favourite")))
                        .build();

                this.gameBox = Checkbox.builder(Component.translatable("musicunbound.tracks.game"), PlaylistEditScreen.this.font)
                        .selected(game)
                        .tooltip(Tooltip.create(Component.translatable(track.gameAllowed() ? "musicunbound.tracks.gameTip" : "musicunbound.tracks.gameUnavailable")))
                        .onValueChange((box, on) -> PlaylistEditScreen.this.setTracks(List.of(track), Kind.GAME, on))
                        .build();
                this.gameBox.active = editable && track.gameAllowed();

                this.menuBox = Checkbox.builder(Component.translatable("musicunbound.tracks.menu"), PlaylistEditScreen.this.font)
                        .selected(menu)
                        .tooltip(Tooltip.create(Component.translatable("musicunbound.tracks.menuTip")))
                        .onValueChange((box, on) -> PlaylistEditScreen.this.setTracks(List.of(track), Kind.MENU, on))
                        .build();
                this.menuBox.active = editable;

                widgets.add(this.playButton);
                widgets.add(this.favouriteButton);
                widgets.add(this.gameBox);
                widgets.add(this.menuBox);
            }

            private String favouriteLabel() {
                return PlaylistStore.isFavourite(this.track.id()) ? FAVOURITE_ON : FAVOURITE_OFF;
            }

            private void play() {
                if (isCurrent(this.track)) {
                    Playback.next();
                } else {
                    Playback.playNow(this.track);
                }
            }

            private void toggleFavourite() {
                PlaylistStore.setFavourite(this.track.id(), !PlaylistStore.isFavourite(this.track.id()));
                this.favouriteButton.setMessage(Component.literal(favouriteLabel()));
            }

            @Override
            public void renderBack(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
                if (this.current) {
                    graphics.fill(left - 2, top - 2, left + width + 2, top + height + 2, HIGHLIGHT);
                }
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                int y = top + (height - BUTTON_SIZE) / 2;
                this.playButton.setPosition(left, y);
                this.favouriteButton.setPosition(left + STAR_LEFT, y);
                int menuX = left + width - this.menuBox.getWidth();
                int gameX = menuX - 8 - this.gameBox.getWidth();
                this.gameBox.setPosition(gameX, top + (height - this.gameBox.getHeight()) / 2);
                this.menuBox.setPosition(menuX, top + (height - this.menuBox.getHeight()) / 2);
                this.playButton.render(graphics, mouseX, mouseY, partialTick);
                this.favouriteButton.render(graphics, mouseX, mouseY, partialTick);
                this.gameBox.render(graphics, mouseX, mouseY, partialTick);
                this.menuBox.render(graphics, mouseX, mouseY, partialTick);

                MutableComponent name = Component.translatable("musicunbound.tracks.track", this.track.title(), this.track.artist());
                if (!TrackAvailability.isAvailable(this.track)) {
                    String status = RemoteTrackProvider.isFoundThisSession(this.track.id())
                            ? "musicunbound.tracks.restartToPlay"
                            : "musicunbound.tracks.notDownloaded";
                    name.append(Component.translatable(status).withStyle(ChatFormatting.GRAY));
                }
                this.titleLeft = left + TEXT_LEFT;
                int maxWidth = gameX - GAP - this.titleLeft;
                this.titleRight = this.titleLeft + Math.min(maxWidth, PlaylistEditScreen.this.font.width(name));
                this.rowTop = top;
                this.rowBottom = top + height;
                UiText.draw(graphics, PlaylistEditScreen.this.font, name, this.titleLeft, top + (height - 9) / 2, maxWidth,
                        this.current ? CURRENT_TEXT : WHITE, false);
            }

            boolean isOverTitle(double mouseX, double mouseY) {
                return mouseX >= this.titleLeft && mouseX < this.titleRight && mouseY >= this.rowTop && mouseY < this.rowBottom;
            }

            /** Era, category and length, for the tooltip on the title. */
            List<FormattedCharSequence> titleTooltip(net.minecraft.client.Minecraft minecraft) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                Era era = TrackCatalog.get().era(this.track.era());
                lines.addAll(Tooltip.splitTooltip(minecraft, Component.translatable("musicunbound.tracks.era", era != null ? era.name() : this.track.era())));
                lines.addAll(Tooltip.splitTooltip(minecraft, Component.translatable("musicunbound.tracks.category",
                        Component.translatableWithFallback("musicunbound.category." + this.track.category(), this.track.category()))));
                if (this.track.length() > 0) {
                    lines.addAll(Tooltip.splitTooltip(minecraft, Component.translatable("musicunbound.tracks.length", formatLength(this.track.length()))));
                }
                return lines;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.list != null) {
            TrackList.TrackRow row = this.list.hoveredTrackRow();
            if (row != null && row.isOverTitle(mouseX, mouseY)) {
                this.setTooltipForNextRenderPass(row.titleTooltip(this.minecraft));
            }
        }
    }
}
