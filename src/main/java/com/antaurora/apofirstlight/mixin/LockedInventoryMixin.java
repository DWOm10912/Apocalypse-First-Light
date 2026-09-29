package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class LockedInventoryMixin {
    /** Limit only the two insertion searches; never change the real list size or extraction. */
    @Redirect(method = {"getFreeSlot", "getSlotWithRemainingSpace"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;size()I"))
    private int afl$unlockedInsertionRange(NonNullList<ItemStack> items) {
        Inventory inventory = (Inventory) (Object) this;
        return Math.min(items.size(), PlayerStorageCapacity.getUnlockedInventorySlots(inventory.player));
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void afl$rejectExplicitLockedDestination(int index, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (PlayerStorageCapacity.isLocked((Inventory) (Object) this, index)) cir.setReturnValue(false);
    }

    /** Survival pick-block swaps also must not send the replaced hotbar item into overflow. */
    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void afl$guardPickSwap(int index, CallbackInfo ci) {
        Inventory inventory = (Inventory) (Object) this;
        if (PlayerStorageCapacity.isLocked(inventory, index)
                && !inventory.getItem(inventory.getSuitableHotbarSlot()).isEmpty()) ci.cancel();
    }
}
