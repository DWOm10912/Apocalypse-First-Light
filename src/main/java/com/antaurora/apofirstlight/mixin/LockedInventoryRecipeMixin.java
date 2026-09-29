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

    /** The recipe book must not count locked empties as room for returning grid ingredients. */
    @Inject(method = "getAmountOfFreeSlotsInInventory", at = @At("HEAD"), cancellable = true)
    private void afl$countUsableStorage(CallbackInfoReturnable<Integer> cir) {
        int capacity = PlayerStorageCapacity.getUnlockedInventorySlots(inventory.player);
        if (capacity >= inventory.items.size()) return;
        int free = 0;
        for (int slot = 0; slot < capacity; slot++) {
            if (inventory.getItem(slot).isEmpty()) free++;
        }
        cir.setReturnValue(free);
    }
}
