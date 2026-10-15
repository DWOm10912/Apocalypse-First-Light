package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlaceRecipe.class)
public abstract class LockedInventoryRecipeMixin {
    @Shadow protected Inventory inventory;

    /**
     * The recipe book's room for returning grid ingredients: in Survival / Adventure the free cells of the grids, where
     * returned items go (InventoryGridMixin); the vanilla slots take nothing then (HotbarRoles).
     */
    @Inject(method = "getAmountOfFreeSlotsInInventory", at = @At("HEAD"), cancellable = true)
    private void afl$countUsableStorage(CallbackInfoReturnable<Integer> cir) {
        if (PlayerStorageCapacity.unrestricted(inventory.player)) return;
        cir.setReturnValue(com.antaurora.apofirstlight.inventory.grid.PlayerGrids.view(inventory.player).freeCells());
    }
}
