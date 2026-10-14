package com.musicunbound.config;

import com.musicunbound.music.Frequency;
import com.musicunbound.music.Preset;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/musicunbound-client.toml). */
public final class MusicConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<Preset> PRESET;
    public static final ModConfigSpec.EnumValue<Frequency> FREQUENCY;
    public static final ModConfigSpec.IntValue CUSTOM_MIN_MINUTES;
    public static final ModConfigSpec.IntValue CUSTOM_MAX_MINUTES;

    public static final ModConfigSpec.BooleanValue NETHER_KEEPS_MUSIC;
    public static final ModConfigSpec.BooleanValue END_KEEPS_MUSIC;
    public static final ModConfigSpec.BooleanValue DEEP_DARK_KEEPS_MUSIC;
    public static final ModConfigSpec.BooleanValue UNDERWATER_KEEPS_MUSIC;

    public static final ModConfigSpec.BooleanValue SHOW_TOAST;
    public static final ModConfigSpec.BooleanValue PLAY_WHILE_PAUSED;
    public static final ModConfigSpec.BooleanValue SHOW_IN_PAUSE_MENU;
    public static final ModConfigSpec.BooleanValue DOWNLOAD_NEW_TRACKS;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_GAME_TRACKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_MENU_TRACKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Which tracks can play. Custom uses customGameTracks and customMenuTracks.")
                .push("presets");
        PRESET = b.comment("ALL, VANILLA_1_21_1 (mod does nothing), VANILLA_LATEST, an ERA_* version preset, NEW_ONLY or CUSTOM.")
                .defineEnum("preset", Preset.ALL);
        b.pop();

        b.comment("How long to wait between songs. CUSTOM uses the minutes below. ERA uses each version's own gaps.")
                .push("frequency");
        FREQUENCY = b.comment("DEFAULT (10-20 min), FREQUENT (max 10 min), CONSTANT (5 s), ERA (version timing) or CUSTOM.")
                .defineEnum("frequency", Frequency.DEFAULT);
        CUSTOM_MIN_MINUTES = b.comment("Minimum wait in minutes (CUSTOM frequency only).")
                .defineInRange("customMinMinutes", 2, 0, 60);
        CUSTOM_MAX_MINUTES = b.comment("Maximum wait in minutes (CUSTOM frequency only). If lower than the minimum, the minimum is used.")
                .defineInRange("customMaxMinutes", 5, 0, 60);
        b.pop();

        b.comment("Keep the vanilla music for these places instead of the rotation.")
                .push("exceptions");
        NETHER_KEEPS_MUSIC = b.comment("Nether uses the vanilla Nether music.")
                .define("netherKeepsMusic", true);
        END_KEEPS_MUSIC = b.comment("The End uses the vanilla End music. If false, The End track is added to the rotation in the End.")
                .define("endKeepsMusic", true);
        DEEP_DARK_KEEPS_MUSIC = b.comment("Deep Dark uses the vanilla Deep Dark music.")
                .define("deepDarkKeepsMusic", true);
        UNDERWATER_KEEPS_MUSIC = b.comment("Underwater uses the vanilla underwater music.")
                .define("underwaterKeepsMusic", false);
        b.pop();

        b.comment("Extras.")
                .push("extras");
        SHOW_TOAST = b.comment("Show a Now Playing toast when a new track starts.")
                .define("showToast", true);
        PLAY_WHILE_PAUSED = b.comment("Keep the music playing while the game is paused.")
                .define("playWhilePaused", true);
        SHOW_IN_PAUSE_MENU = b.comment("Show the current track in the pause menu.")
                .define("showInPauseMenu", true);
        DOWNLOAD_NEW_TRACKS = b.comment("Download the tracks added after 1.21.1 from Mojang's asset server.")
                .define("downloadNewTracks", true);
        b.pop();

        b.comment("Used only when preset is CUSTOM. Track ids from tracks.json, for example \"aerie\".")
                .push("custom");
        CUSTOM_GAME_TRACKS = b.comment("Track ids for the in-game rotation.")
                .defineListAllowEmpty("customGameTracks", List.<String>of(), () -> "", o -> o instanceof String);
        CUSTOM_MENU_TRACKS = b.comment("Track ids for the title screen.")
                .defineListAllowEmpty("customMenuTracks", List.<String>of(), () -> "", o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }

    private MusicConfig() {
    }
}
