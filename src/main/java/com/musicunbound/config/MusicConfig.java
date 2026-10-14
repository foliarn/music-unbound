package com.musicunbound.config;

import com.musicunbound.music.Frequency;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/musicunbound-client.toml). */
public final class MusicConfig {
    public static final ModConfigSpec SPEC;

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

    public static final ModConfigSpec.BooleanValue SMOOTH_FADES;
    public static final ModConfigSpec.BooleanValue JUKEBOX_FADE;

    public static final ModConfigSpec.BooleanValue EVENT_DIMENSION;
    public static final ModConfigSpec.BooleanValue EVENT_TIME;
    public static final ModConfigSpec.BooleanValue EVENT_WEATHER;
    public static final ModConfigSpec.IntValue EVENT_COOLDOWN_MINUTES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

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

        b.comment("Playback smoothing.")
                .push("playback");
        SMOOTH_FADES = b.comment("Fade music out instead of cutting it (context changes, skipping).")
                .define("smoothFades", true);
        JUKEBOX_FADE = b.comment("Fade background music down while a jukebox can be heard.")
                .define("jukeboxFade", true);
        b.pop();

        b.comment("Start a song right away (only if nothing is playing) when something happens.")
                .push("events");
        EVENT_DIMENSION = b.comment("When you arrive in another dimension.")
                .define("dimension", true);
        EVENT_TIME = b.comment("At sunrise and nightfall.")
                .define("time", true);
        EVENT_WEATHER = b.comment("When a thunderstorm starts.")
                .define("weather", true);
        EVENT_COOLDOWN_MINUTES = b.comment("Minimum minutes between two event songs.")
                .defineInRange("cooldownMinutes", 10, 0, 60);
        b.pop();

        SPEC = b.build();
    }

    private MusicConfig() {
    }
}
