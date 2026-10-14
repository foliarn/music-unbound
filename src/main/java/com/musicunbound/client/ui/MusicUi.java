package com.musicunbound.client.ui;

import com.musicunbound.mixin.OptionsSubScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Wires the settings screen into the mod list (Config button) and into Options > Music & Sounds. */
public final class MusicUi {
    private MusicUi() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new MusicSettingsScreen(parent));
        NeoForge.EVENT_BUS.addListener(MusicUi::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SoundOptionsScreen screen)) {
            return;
        }
        OptionsList list = ((OptionsSubScreenAccessor) screen).musicunbound$getList();
        if (list == null) {
            return;
        }
        Button button = Button.builder(
                        Component.translatable("musicunbound.options.open"),
                        b -> Minecraft.getInstance().setScreen(new MusicSettingsScreen(screen)))
                .width(MusicSettingsScreen.ROW_WIDTH)
                .build();
        list.addSmall(button, null);
    }
}
