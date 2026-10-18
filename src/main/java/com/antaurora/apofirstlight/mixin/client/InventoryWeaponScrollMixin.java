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
 * positions, and the hand while it holds something; never into the unused hotbar indices 4-8.
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
        int next = Math.min(selected, HotbarRoles.USED - 1);
        for (int i = 0; i < HotbarRoles.USED; i++) {
            next = Math.floorMod(next - step, HotbarRoles.USED);
            if (next != HotbarRoles.HAND_SLOT || !items.get(HotbarRoles.HAND_SLOT).isEmpty()) break;
        }
        selected = next;
    }
}
