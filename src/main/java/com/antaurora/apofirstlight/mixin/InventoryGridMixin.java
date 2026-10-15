package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grid Inventory V0 pickup order (docs/ui/afl_inventory_grid_v0_plan.md 3, 10), Survival / Adventure: a weapon goes to
 * its empty weapon position first when it may be carried there (a long gun with a sling, a pistol with a holster worn, a
 * melee weapon); then vanilla's search, which only merges onto the stack in the hand (LockedInventoryMixin); what is
 * left goes into the grids, pockets → rig → backpack. Items given back (closing a menu with something on the cursor,
 * crafting leftovers) take the same way and are dropped only when nothing has room.
 */
@Mixin(Inventory.class)
public abstract class InventoryGridMixin {
    @Shadow @Final public Player player;

    @Shadow public abstract boolean add(int slot, ItemStack stack);

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void afl$weaponToItsSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty() || PlayerStorageCapacity.unrestricted(player)) return;
        // gear (rig, backpack, holster, ear muffs, ...) is worn when its slot is free: most of it is bigger than a pocket
        if (stack.getCount() == 1 && com.antaurora.apofirstlight.equipment.AflEquipmentSlots.wearIfFree(player, stack)) {
            cir.setReturnValue(true);
            return;
        }
        var role = HotbarRoles.weaponRole(stack);
        if (role == null || stack.getCount() > 1) return;
        Inventory inventory = (Inventory) (Object) this;
        int index = HotbarRoles.slotOf(role);
        if (!inventory.items.get(index).isEmpty() || PlayerStorageCapacity.rejects(inventory, index, stack)) return;
        inventory.items.set(index, stack.copyAndClear());
        inventory.items.get(index).setPopTime(5);
        cir.setReturnValue(true);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void afl$restIntoGrids(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) return;
        if (PlayerStorageCapacity.unrestricted(player)) return;
        if (PlayerGrids.insert(serverPlayer, stack)) cir.setReturnValue(true);
    }

    /** A death without keepInventory: the pockets drop with the rest (Curios drops the worn rig and backpack itself). */
    @Inject(method = "dropAll", at = @At("TAIL"))
    private void afl$dropPockets(CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) com.antaurora.apofirstlight.inventory.grid.Pockets.dropAll(serverPlayer);
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void afl$giveBackThroughGrids(ItemStack stack, boolean sendPacket, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer) || PlayerStorageCapacity.unrestricted(player)) return;
        if (!stack.isEmpty()) add(-1, stack);
        if (!stack.isEmpty()) player.drop(stack, false);
        ci.cancel();
    }
}
