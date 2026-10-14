package com.musicunbound;

import com.mojang.logging.LogUtils;
import com.musicunbound.client.MusicKeys;
import com.musicunbound.client.NowPlaying;
import com.musicunbound.client.PausePlayer;
import com.musicunbound.client.ui.MusicUi;
import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.ModdedMusic;
import com.musicunbound.data.TrackCatalog;
import com.musicunbound.audio.MusicFader;
import com.musicunbound.music.MusicDirector;
import com.musicunbound.music.MusicEvents;
import com.musicunbound.music.Playback;
import com.musicunbound.remote.RemoteTracks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// dist = CLIENT: never loaded on a dedicated server, so the mod can't affect servers.
@Mod(value = MusicUnbound.MOD_ID, dist = Dist.CLIENT)
public final class MusicUnbound {
    public static final String MOD_ID = "musicunbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MusicUnbound(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, MusicConfig.SPEC);
        LOGGER.info("Loaded {} tracks", TrackCatalog.get().tracks().size());
        NeoForge.EVENT_BUS.addListener(MusicDirector.INSTANCE::onSelectMusic);
        Playback.register();
        MusicEvents.register();
        MusicFader.register();
        RemoteTracks.register(modBus);
        ModdedMusic.register(modBus);
        MusicUi.register(container);
        NowPlaying.register();
        PausePlayer.register();
        MusicKeys.register(modBus);
    }
}
