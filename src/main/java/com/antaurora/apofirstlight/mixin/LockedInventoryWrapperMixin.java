package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PlayerMainInvWrapper / PlayerInvWrapper delegate normal Forge insertion to this wrapper. */
@Mixin(value = InvWrapper.class, remap = false)
public abstract class LockedInventoryWrapperMixin {
    @Shadow public abstract Container getInv();

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void afl$denyLockedInsertion(int slot, ItemStack stack, boolean simulate,
            CallbackInfoReturnable<ItemStack> cir) {
        if (getInv() instanceof Inventory inventory && PlayerStorageCapacity.isLocked(inventory, slot)) {
            cir.setReturnValue(stack);
        }
    }

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void afl$lockedItemValidity(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (getInv() instanceof Inventory inventory && PlayerStorageCapacity.isLocked(inventory, slot)) {
            cir.setReturnValue(false);
        }
    }
}
