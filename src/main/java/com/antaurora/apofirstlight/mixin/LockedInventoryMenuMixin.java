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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class LockedInventoryMenuMixin {
    /** Also validate subclasses which override Slot.mayPlace without calling super. */
    @Redirect(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean afl$checkLockedDestination(Slot slot, ItemStack stack) {
        return !PlayerStorageCapacity.rejects(slot, stack) && slot.mayPlace(stack);
    }

    /**
     * Shift-click moves likewise, and never into the hand (hotbar index 3): the hand takes only what is put there by hand
     * (docs/ui/afl_inventory_grid_v0_plan.md 10); a shift-click toward the player goes into the grids instead.
     */
    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean afl$checkMoveDestination(Slot slot, ItemStack stack) {
        if (isSurvivalHand(slot)) return false;
        return !PlayerStorageCapacity.rejects(slot, stack) && slot.mayPlace(stack);
    }

    /**
     * Survival / Adventure, server: what a shift-click toward the player's inventory left over goes into the grids
     * (pockets → rig → backpack), so chests, the crafting result and furnaces move into them.
     */
    @Inject(method = "moveItemStackTo", at = @At("RETURN"), cancellable = true)
    private void afl$moveRestIntoGrids(ItemStack stack, int start, int end, boolean reverse, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || com.antaurora.apofirstlight.inventory.grid.GridActions.movingIntoContainer()) return;
        var self = (AbstractContainerMenu) (Object) this;
        for (int i = Math.max(0, start); i < Math.min(end, self.slots.size()); i++) {
            var slot = self.slots.get(i);
            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory inventory)) continue;
            if (!(inventory.player instanceof net.minecraft.server.level.ServerPlayer player) || PlayerStorageCapacity.unrestricted(player)) return;
            if (com.antaurora.apofirstlight.inventory.grid.PlayerGrids.insert(player, stack)) cir.setReturnValue(true);
            return;
        }
    }

    private static boolean isSurvivalHand(Slot slot) {
        return slot.container instanceof net.minecraft.world.entity.player.Inventory inventory
                && slot.getContainerSlot() == com.antaurora.apofirstlight.inventory.HotbarRoles.HAND_SLOT
                && !PlayerStorageCapacity.unrestricted(inventory.player);
    }

    /**
     * The vanilla merge phase does not consult mayPlace. Skip locked merge targets and overflow (a non-weapon left in a
     * weapon slot): nothing merges onto those. A weapon slot's own weapon still merges like vanilla.
     */
    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
    private ItemStack afl$skipLockedMergeTarget(Slot slot) {
        if (PlayerStorageCapacity.isLocked(slot)) return ItemStack.EMPTY;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory inventory
                && PlayerStorageCapacity.isOverflow(inventory, slot.getContainerSlot())) return ItemStack.EMPTY;
        return slot.getItem();
    }

    /**
     * Validate the destination index too, including non-hotbar indices in crafted swap packets, and the item a number
     * key would move into a hotbar weapon slot (HotbarRoles).
     */
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void afl$guardSwapDestination(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (type != ClickType.SWAP) return;
        var self = (AbstractContainerMenu) (Object) this;
        var moving = slotId >= 0 && slotId < self.slots.size() ? self.slots.get(slotId).getItem() : ItemStack.EMPTY;
        if (PlayerStorageCapacity.isLocked(player.getInventory(), button)
                || !moving.isEmpty() && PlayerStorageCapacity.rejects(player.getInventory(), button, moving)) ci.cancel();
    }
}
