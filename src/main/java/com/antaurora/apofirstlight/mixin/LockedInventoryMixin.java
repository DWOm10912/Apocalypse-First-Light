package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class LockedInventoryMixin {
    @Shadow
    private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        throw new AssertionError();
    }

    /**
     * The two "find a place" searches only look at generic targets: open slots, and not the hotbar's weapon slots
     * (keys 1-5, docs/ui/afl_inventory_grid_v0_plan.md 1). Never changes the real list size or extraction.
     */
    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void afl$freeGenericSlot(CallbackInfoReturnable<Integer> cir) {
        Inventory inventory = (Inventory) (Object) this;
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).isEmpty() && PlayerStorageCapacity.isGenericTarget(inventory, i)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    /**
     * Vanilla's order (selected slot, then the list; never the off hand: inventory/NoOffHand), skipping slots that
     * would refuse the stack.
     */
    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void afl$mergeIntoAllowedSlot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Inventory inventory = (Inventory) (Object) this;
        if (mergeable(inventory, inventory.selected, stack)) {
            cir.setReturnValue(inventory.selected);
            return;
        }
        for (int i = 0; i < inventory.items.size(); i++) {
            if (mergeable(inventory, i, stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    /** In Survival / Adventure the hand (plan 10) is filled only by bindings and the hands box, never by merging. */
    private boolean mergeable(Inventory inventory, int index, ItemStack stack) {
        return index >= 0 && index < inventory.items.size() && !PlayerStorageCapacity.rejects(inventory, index, stack)
                && (index != HotbarRoles.HAND_SLOT || PlayerStorageCapacity.unrestricted(inventory.player))
                && hasRemainingSpaceForItem(inventory.items.get(index), stack);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void afl$rejectExplicitLockedDestination(int index, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (index >= 0 && PlayerStorageCapacity.rejects((Inventory) (Object) this, index, stack)) cir.setReturnValue(false);
    }

    /** Survival pick-block swaps also must not send the replaced hotbar item into overflow. */
    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void afl$guardPickSwap(int index, CallbackInfo ci) {
        Inventory inventory = (Inventory) (Object) this;
        if (PlayerStorageCapacity.isLocked(inventory, index)
                && !inventory.getItem(inventory.getSuitableHotbarSlot()).isEmpty()) ci.cancel();
    }
}
