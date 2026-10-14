package com.musicunbound.mixin;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the options list of a vanilla options screen, so a mod button can be added to it. */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {
    @Accessor("list")
    OptionsList musicunbound$getList();
}
