package com.musicunbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.musicunbound.music.Playback;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/** The playback keybinds. They are unbound by default, so the player picks their own keys in Controls. */
public final class MusicKeys {
    private static final String CATEGORY = "key.categories.musicunbound";

    public static final KeyMapping NEXT = new KeyMapping(
            "key.musicunbound.next", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping PREVIOUS = new KeyMapping(
            "key.musicunbound.previous", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping PAUSE = new KeyMapping(
            "key.musicunbound.pause", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);

    private MusicKeys() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MusicKeys::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(MusicKeys::onClientTickPost);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(NEXT);
        event.register(PREVIOUS);
        event.register(PAUSE);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        while (NEXT.consumeClick()) {
            Playback.next();
        }
        while (PREVIOUS.consumeClick()) {
            Playback.previous();
        }
        while (PAUSE.consumeClick()) {
            Playback.togglePause();
        }
    }
}
