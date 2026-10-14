package com.musicunbound.client.ui;

import com.musicunbound.playlist.Playlist;
import com.musicunbound.playlist.PlaylistStore;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the active playlist, in three sections: built-in, versions and the user's own. A click selects a playlist, and
 * a double click or "Use" activates it and goes back.
 */
public class PlaylistScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("musicunbound.screen.playlists");
    private static final int ITEM_HEIGHT = 18;
    private static final int ROW_WIDTH = 310;
    private static final int FOOTER_HEIGHT = 53;
    private static final int BUTTON_WIDTH = 100;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int WHITE = -1;
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    /** Remembered across visits, so coming back from Edit or Rename keeps the same playlist selected. */
    @Nullable
    private String selectedId;
    private boolean centeredOnce;
    private boolean refreshPending;
    @Nullable
    private PlaylistList playlists;
    @Nullable
    private Button useButton;
    @Nullable
    private Button editButton;
    @Nullable
    private Button renameButton;
    @Nullable
    private Button deleteButton;

    public PlaylistScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
        this.layout.setFooterHeight(FOOTER_HEIGHT);
    }

    @Override
    protected void addContents() {
        this.playlists = this.layout.addToContents(new PlaylistList(this.minecraft));
    }

    @Override
    protected void addOptions() {
        // The list and the footer are the whole screen.
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.vertical().spacing(8));
        footer.defaultCellSetting().alignHorizontallyCenter();

        LinearLayout first = footer.addChild(LinearLayout.horizontal().spacing(8));
        this.useButton = first.addChild(button("musicunbound.playlist.use", "musicunbound.playlist.tip.use", b -> useSelected()));
        this.editButton = first.addChild(button("musicunbound.playlist.edit", "musicunbound.playlist.tip.edit", b -> editSelected()));
        first.addChild(button("musicunbound.playlist.new", "musicunbound.playlist.tip.new", b -> newPlaylist()));

        LinearLayout second = footer.addChild(LinearLayout.horizontal().spacing(8));
        this.renameButton = second.addChild(button("musicunbound.playlist.rename", null, b -> renameSelected()));
        this.deleteButton = second.addChild(button("musicunbound.playlist.delete", null, b -> deleteSelected()));
        second.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(BUTTON_WIDTH).build());
    }

    private static Button button(String label, @Nullable String tooltip, Button.OnPress onPress) {
        Button.Builder builder = Button.builder(Component.translatable(label), onPress).width(BUTTON_WIDTH);
        if (tooltip != null) {
            builder = builder.tooltip(Tooltip.create(Component.translatable(tooltip)));
        }
        return builder.build();
    }

    @Override
    protected void init() {
        super.init();
        this.refreshPending = false;
        this.refill();
    }

    @Override
    public void added() {
        super.added();
        // Shown again after Edit, Rename or Delete: the list is refilled on the next tick.
        this.refreshPending = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.refreshPending) {
            this.refreshPending = false;
            this.refill();
        }
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (this.playlists != null) {
            this.playlists.updateSize(this.width, this.layout);
        }
    }

    /** Fills the list from the store again. The scroll position and the selection are kept. */
    private void refill() {
        PlaylistList list = this.playlists;
        if (list == null) {
            return;
        }
        double scroll = list.getScrollAmount();
        list.clearRows();

        String activeId = PlaylistStore.active().id();
        List<Playlist> builtIns = PlaylistStore.builtIns();
        list.addHeader(Component.translatable("musicunbound.playlist.section.builtIn"));
        for (Playlist playlist : builtIns) {
            if (playlist.eraId() == null) {
                list.addPlaylist(playlist, playlist.id().equals(activeId));
            }
        }
        list.addHeader(Component.translatable("musicunbound.playlist.section.versions"));
        for (Playlist playlist : builtIns) {
            if (playlist.eraId() != null) {
                list.addPlaylist(playlist, playlist.id().equals(activeId));
            }
        }
        list.addHeader(Component.translatable("musicunbound.playlist.section.user"));
        List<Playlist> users = PlaylistStore.userPlaylists();
        if (users.isEmpty()) {
            list.addText(Component.translatable("musicunbound.playlist.none"));
        }
        for (Playlist playlist : users) {
            list.addPlaylist(playlist, playlist.id().equals(activeId));
        }

        String target = selectedId != null && PlaylistStore.byId(selectedId).isPresent() ? selectedId : activeId;
        this.selectedId = target;
        PlaylistList.PlaylistRow row = list.findRow(target);
        if (row != null) {
            list.setSelected(row);
        }
        if (!this.centeredOnce) {
            this.centeredOnce = true;
            if (row != null) {
                list.reveal(row);
            }
        } else {
            list.setScrollAmount(scroll);
        }
        updateButtons();
    }

    private void updateButtons() {
        Playlist selected = selectedPlaylist();
        boolean any = selected != null;
        boolean user = any && !selected.builtIn();
        if (this.useButton != null) {
            this.useButton.active = any;
        }
        if (this.editButton != null) {
            this.editButton.active = any;
        }
        if (this.renameButton != null) {
            this.renameButton.active = user;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = user;
        }
    }

    @Nullable
    private Playlist selectedPlaylist() {
        if (this.playlists == null) {
            return null;
        }
        PlaylistList.Row row = this.playlists.getSelected();
        return row instanceof PlaylistList.PlaylistRow playlistRow ? playlistRow.playlist : null;
    }

    private void useSelected() {
        Playlist selected = selectedPlaylist();
        if (selected != null) {
            PlaylistStore.setActive(selected.id());
        }
        this.onClose();
    }

    private void editSelected() {
        Playlist selected = selectedPlaylist();
        if (selected != null) {
            this.minecraft.setScreen(new PlaylistEditScreen(this, selected.id()));
        }
    }

    private void newPlaylist() {
        Playlist selected = selectedPlaylist();
        String from = selected != null ? selected.id() : PlaylistStore.active().id();
        Playlist created = PlaylistStore.create(Component.translatable("musicunbound.playlist.newName").getString(), from);
        this.selectedId = created.id();
        this.minecraft.setScreen(new PlaylistEditScreen(this, created.id()));
    }

    private void renameSelected() {
        Playlist selected = selectedPlaylist();
        if (selected != null && !selected.builtIn()) {
            this.minecraft.setScreen(new PlaylistRenameScreen(this, selected.id()));
        }
    }

    private void deleteSelected() {
        Playlist selected = selectedPlaylist();
        if (selected == null || selected.builtIn()) {
            return;
        }
        ConfirmScreen confirm = new ConfirmScreen(
                yes -> {
                    if (yes) {
                        PlaylistStore.delete(selected.id());
                    }
                    this.minecraft.setScreen(this);
                },
                Component.translatable("musicunbound.playlist.deleteTitle"),
                Component.translatable("musicunbound.playlist.deleteMessage", selected.name()),
                Component.translatable("musicunbound.playlist.delete"),
                CommonComponents.GUI_CANCEL);
        this.minecraft.setScreen(confirm);
    }

    /** The scrollable list: section headers and text rows cannot be selected, playlist rows can. */
    private final class PlaylistList extends ObjectSelectionList<PlaylistList.Row> {
        PlaylistList(Minecraft minecraft) {
            super(minecraft, PlaylistScreen.this.width, PlaylistScreen.this.layout.getContentHeight(), PlaylistScreen.this.layout.getHeaderHeight(), ITEM_HEIGHT);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        /** Arrow keys skip the rows that cannot be selected. */
        @Nullable
        @Override
        protected Row nextEntry(ScreenDirection direction, Predicate<Row> predicate, @Nullable Row selected) {
            return super.nextEntry(direction, row -> row.selectable() && predicate.test(row), selected);
        }

        void clearRows() {
            this.setFocused(null);
            this.clearEntries();
        }

        void addHeader(Component text) {
            this.addEntry(new TextRow(text));
        }

        void addText(Component text) {
            this.addEntry(new TextRow(text));
        }

        void addPlaylist(Playlist playlist, boolean active) {
            this.addEntry(new PlaylistRow(playlist, active));
        }

        @Nullable
        PlaylistRow findRow(String id) {
            for (Row row : this.children()) {
                if (row instanceof PlaylistRow playlistRow && playlistRow.playlist.id().equals(id)) {
                    return playlistRow;
                }
            }
            return null;
        }

        void reveal(Row row) {
            this.centerScrollOn(row);
        }

        abstract class Row extends ObjectSelectionList.Entry<Row> {
            abstract boolean selectable();
        }

        /** A section title or a hint. Gray, centred, not selectable. */
        private final class TextRow extends Row {
            private final Component text;

            TextRow(Component text) {
                this.text = text;
            }

            @Override
            boolean selectable() {
                return false;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                UiText.draw(graphics, PlaylistScreen.this.font, text, left, top + (height - 9) / 2, width, GRAY, true);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return false;
            }

            @Override
            public Component getNarration() {
                return text;
            }
        }

        private final class PlaylistRow extends Row {
            private final Playlist playlist;
            private final Component label;
            private long lastClick;

            PlaylistRow(Playlist playlist, boolean active) {
                this.playlist = playlist;
                this.label = active
                        ? Component.translatable("musicunbound.playlist.active", playlist.name())
                        : Component.literal(playlist.name());
            }

            @Override
            boolean selectable() {
                return true;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                UiText.draw(graphics, PlaylistScreen.this.font, label, left, top + (height - 9) / 2, width, WHITE, true);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                this.select();
                long now = Util.getMillis();
                if (now - this.lastClick < DOUBLE_CLICK_MILLIS) {
                    useSelected();
                }
                this.lastClick = now;
                return super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (CommonInputs.selected(keyCode)) {
                    this.select();
                    useSelected();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            private void select() {
                PlaylistScreen.this.selectedId = playlist.id();
                PlaylistList.this.setSelected(this);
                PlaylistScreen.this.updateButtons();
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", label);
            }
        }
    }
}
