package com.antaurora.apofirstlight.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The AFL New World page (client/survivor/AflNewWorldScreen) edits the vanilla screen's WorldCreationUiState and lets
 * vanilla create the world: its private create step and the screen it returns to.
 */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Invoker("onCreate")
    void apocalypse$create();

    @Accessor("lastScreen")
    Screen apocalypse$lastScreen();
}
