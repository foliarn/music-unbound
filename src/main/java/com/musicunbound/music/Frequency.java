package com.musicunbound.music;

import com.musicunbound.config.MusicConfig;
import com.musicunbound.data.Era;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public enum Frequency {
    DEFAULT("Default"),
    FREQUENT("Frequent"),
    CONSTANT("Constant"),
    ERA("Era-accurate"),
    CUSTOM("Custom");

    private final String label;

    Frequency(String label) {
        this.label = label;
    }

    public Component displayName() {
        return Component.literal(label);
    }

    /**
     * Returns {min, max} delay in ticks.
     *
     * @param era       the era for ERA frequency, or null (then the base delays are used)
     * @param timingKey one of game, creative, nether, end, underwater
     */
    public int[] delays(int baseMin, int baseMax, @Nullable Era era, String timingKey) {
        return switch (this) {
            case DEFAULT -> new int[]{baseMin, baseMax};
            case FREQUENT -> new int[]{Math.min(baseMin, 12000), Math.min(baseMax, 12000)};
            case CONSTANT -> new int[]{100, 100};
            case CUSTOM -> {
                int min = MusicConfig.CUSTOM_MIN_MINUTES.get() * 1200;
                int max = Math.max(min, MusicConfig.CUSTOM_MAX_MINUTES.get() * 1200);
                yield new int[]{min, max};
            }
            case ERA -> {
                int[] timing = era != null ? era.timing().get(timingKey) : null;
                yield timing != null ? timing : new int[]{baseMin, baseMax};
            }
        };
    }
}
