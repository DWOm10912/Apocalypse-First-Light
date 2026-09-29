package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class LockedInventoryMenuMixin {
    /** Also validate subclasses which override Slot.mayPlace without calling super. */
    @Redirect(method = {"doClick", "moveItemStackTo"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean afl$checkLockedDestination(Slot slot, ItemStack stack) {
        return !PlayerStorageCapacity.isLocked(slot) && slot.mayPlace(stack);
    }

    /** The vanilla merge phase does not consult mayPlace. Skip locked merge targets only. */
    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
    private ItemStack afl$skipLockedMergeTarget(Slot slot) {
        return PlayerStorageCapacity.isLocked(slot) ? ItemStack.EMPTY : slot.getItem();
    }

    /** Validate the destination index too, including non-hotbar indices in crafted swap packets. */
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void afl$guardSwapDestination(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (type == ClickType.SWAP && PlayerStorageCapacity.isLocked(player.getInventory(), button)) ci.cancel();
    }
}
