package com.musicunbound.remote;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Registers the hidden built-in pack that serves the remote tracks, and starts the downloader after the game is up. */
public final class RemoteTracks {
    private static final Pack.ResourcesSupplier SUPPLIER = new Pack.ResourcesSupplier() {
        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new RemoteTracksPack();
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new RemoteTracksPack();
        }
    };

    private RemoteTracks() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RemoteTracks::onAddPackFinders);

        AtomicBoolean started = new AtomicBoolean();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (started.compareAndSet(false, true)) {
                RemoteTrackProvider.start();
            }
        });
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Pack pack = new Pack(
                RemoteTracksPack.INFO,
                SUPPLIER,
                new Pack.Metadata(Component.literal("Music Unbound: new tracks"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true),
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
