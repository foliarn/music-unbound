package com.musicunbound.client.ui;

import com.musicunbound.playlist.Playlist;
import com.musicunbound.playlist.PlaylistStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A small screen with one text box to give a user playlist a new name. */
public class PlaylistRenameScreen extends Screen {
    private static final int MAX_NAME_LENGTH = 40;
    private static final int ENTER = 257;
    private static final int KEYPAD_ENTER = 335;

    private final Screen lastScreen;
    private final String playlistId;
    private EditBox nameBox;
    private Button doneButton;

    public PlaylistRenameScreen(Screen lastScreen, String playlistId) {
        super(Component.translatable("musicunbound.screen.renamePlaylist"));
        this.lastScreen = lastScreen;
        this.playlistId = playlistId;
    }

    @Override
    protected void init() {
        String current = PlaylistStore.byId(this.playlistId).map(Playlist::name).orElse("");
        this.nameBox = this.addRenderableWidget(new EditBox(
                this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20, Component.translatable("musicunbound.screen.renamePlaylist")));
        this.nameBox.setMaxLength(MAX_NAME_LENGTH);
        this.nameBox.setValue(current);
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.submit())
                .bounds(this.width / 2 - 155, this.height / 2 + 20, 150, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(this.width / 2 + 5, this.height / 2 + 20, 150, 20)
                .build());
        this.doneButton.active = !current.isBlank();
        this.nameBox.setResponder(text -> this.doneButton.active = !text.isBlank());
        this.setInitialFocus(this.nameBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, -1);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == ENTER || keyCode == KEYPAD_ENTER) && this.doneButton.active) {
            this.submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        String name = this.nameBox.getValue().trim();
        if (!name.isEmpty()) {
            PlaylistStore.rename(this.playlistId, name);
        }
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
