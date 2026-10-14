package com.musicunbound.client.ui;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/** Vanilla-style option buttons for the Music Unbound screens. Changes are written to the config when applied. */
final class SettingOptions {
    static final int MAX_MINUTES = 60;

    private SettingOptions() {
    }

    /** An on/off option. */
    static OptionInstance<Boolean> toggle(String caption, String tooltip, ModConfigSpec.BooleanValue value) {
        return OptionInstance.createBoolean(
                caption,
                OptionInstance.<Boolean>cachedConstantTooltip(Component.translatable(tooltip)),
                value.get(),
                value::set);
    }

    /** true = the dimension keeps its own vanilla music ("Own"), false = it plays the playlist ("Shared"). */
    static OptionInstance<Boolean> ownOrShared(String caption, String tooltip, ModConfigSpec.BooleanValue value) {
        return OptionInstance.createBoolean(
                caption,
                OptionInstance.<Boolean>cachedConstantTooltip(Component.translatable(tooltip)),
                (captionText, own) -> Component.translatable(own ? "musicunbound.value.own" : "musicunbound.value.shared"),
                value.get(),
                value::set);
    }

    /** A slider from 0 to 60 minutes. The tooltip is optional. */
    static OptionInstance<Integer> minutes(String caption, @Nullable String tooltip, ModConfigSpec.IntValue value) {
        OptionInstance.TooltipSupplier<Integer> tooltipSupplier = tooltip == null
                ? OptionInstance.<Integer>noTooltip()
                : OptionInstance.<Integer>cachedConstantTooltip(Component.translatable(tooltip));
        return new OptionInstance<Integer>(
                caption,
                tooltipSupplier,
                (captionText, minutes) -> CommonComponents.optionNameValue(captionText, Component.translatable("musicunbound.value.minutes", minutes)),
                new OptionInstance.IntRange(0, MAX_MINUTES),
                value.get(),
                value::set);
    }
}
