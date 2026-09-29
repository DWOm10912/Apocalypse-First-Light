package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class LockedInventorySlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void afl$denyLockedPlacement(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerStorageCapacity.isLocked((Slot) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void afl$denyLockedSafeInsert(ItemStack stack, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (PlayerStorageCapacity.isLocked((Slot) (Object) this)) cir.setReturnValue(stack);
    }

    /** Vanilla couples partial extraction to mayPlace. Relax that check only inside extraction. */
    @Redirect(method = "tryRemove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;allowModification(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean afl$allowPartialOverflowExtraction(Slot slot, Player player) {
        return PlayerStorageCapacity.isLocked(slot) ? slot.mayPickup(player) : slot.allowModification(player);
    }
}
