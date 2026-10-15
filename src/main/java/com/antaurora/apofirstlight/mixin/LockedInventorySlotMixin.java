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
    /** Locked slots, and hotbar weapon slots for items that are not their weapon (HotbarRoles). */
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void afl$denyLockedPlacement(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerStorageCapacity.rejects((Slot) (Object) this, stack)) cir.setReturnValue(false);
    }

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void afl$denyLockedSafeInsert(ItemStack stack, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (PlayerStorageCapacity.rejects((Slot) (Object) this, stack)) cir.setReturnValue(stack);
    }

    /** Vanilla couples partial extraction to mayPlace. Relax that check only inside extraction, for overflow. */
    @Redirect(method = "tryRemove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;allowModification(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean afl$allowPartialOverflowExtraction(Slot slot, Player player) {
        boolean overflow = PlayerStorageCapacity.isLocked(slot) || slot.container instanceof net.minecraft.world.entity.player.Inventory inventory
                && PlayerStorageCapacity.isOverflow(inventory, slot.getContainerSlot());
        return overflow ? slot.mayPickup(player) : slot.allowModification(player);
    }
}
