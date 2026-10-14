package com.musicunbound;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

// dist = CLIENT: never loaded on a dedicated server, so the mod can't affect servers.
@Mod(value = MusicUnbound.MOD_ID, dist = Dist.CLIENT)
public final class MusicUnbound {
    public static final String MOD_ID = "musicunbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MusicUnbound(IEventBus modBus, ModContainer container) {
        LOGGER.info("Music Unbound loaded");
    }
}
