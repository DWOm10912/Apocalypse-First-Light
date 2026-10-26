package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mouse wheel in Survival / Adventure (docs/ui/afl_inventory_grid_v0_plan.md 10) steps through the three weapon
 * positions, and the hand while it holds something; never into the unused hotbar indices 4-8, nor into a position that
 * cannot be used now (HotbarRoles#open: no holster, no slung gun). Leaving the hand puts what it holds back into the
 * grids (inventory.grid.Loadout); where that would not fit the wheel does nothing and says so, instead of switching and
 * being switched back by the server (user 2026-10-25: the pistol jumped on every turn of the wheel).
 */
@Mixin(Inventory.class)
public abstract class InventoryWeaponScrollMixin {
    @Shadow public int selected;
    @Shadow @Final public Player player;
    @Shadow @Final public NonNullList<ItemStack> items;

    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void afl$weaponPositions(double direction, CallbackInfo ci) {
        if (PlayerStorageCapacity.unrestricted(player)) return;
        ci.cancel();
        // driving: the wheel does not take up a weapon (Loadout puts one away on the server too)
        if (com.antaurora.apofirstlight.vehicle.PickupTruckEntity.driving(player)) return;
        int step = (int) Math.signum(direction);
        if (step == 0) return;
        int from = Math.min(selected, HotbarRoles.USED - 1), next = from;
        for (int i = 0; i < HotbarRoles.USED; i++) {
            next = Math.floorMod(next - step, HotbarRoles.USED);
            if (next == HotbarRoles.HAND_SLOT ? !items.get(HotbarRoles.HAND_SLOT).isEmpty() : HotbarRoles.open(player, next, ItemStack.EMPTY)) break;
        }
        if (next == from) return;
        if (selected == HotbarRoles.HAND_SLOT && !afl$stows(items.get(HotbarRoles.HAND_SLOT))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hint.apocalypse_firstlight.loadout.no_room"), true);
            return;
        }
        selected = next;
    }

    /**
     * Whether the hand's stack fits into the grids as the server will put it back (the hand's own reserved cells free): a
     * free place in some storage, or room on a stack of the same item. Read only: the client's storages are the drawn,
     * cached ones, so nothing is placed and each reservation is put back.
     */
    private boolean afl$stows(ItemStack hand) {
        if (hand.isEmpty()) return true;
        if (!com.antaurora.apofirstlight.inventory.grid.PlayerGrids.allowedInGrid(hand)) return false;
        var view = com.antaurora.apofirstlight.inventory.grid.PlayerGrids.view(player);
        int room = 0;
        for (var target : com.antaurora.apofirstlight.inventory.grid.PlayerGrids.Target.values()) {
            var storage = view.get(target);
            if (storage == null) continue;
            var reserved = storage.reserved();
            storage.reserve(null);
            boolean spot = storage.findSpot(hand) != null;
            storage.reserve(reserved);
            if (spot) return true;
            for (var e : storage.entries())
                if (ItemStack.isSameItemSameTags(e.stack(), hand)) room += Math.max(0, Math.min(e.stack().getMaxStackSize(), 64) - e.stack().getCount());
        }
        return room >= hand.getCount();
    }
}
