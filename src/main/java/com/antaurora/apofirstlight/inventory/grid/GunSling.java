package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.world.item.ItemStack;

/**
 * A sling fitted to a long gun (docs/ui/afl_inventory_grid_v0_plan.md 10): only a gun with one can be carried on the back
 * at key 1. Kept as a flag in the gun's NBT ({@code AflSling}); the gun_sling item is used up when fitted and comes back
 * when taken off. Not part of the gun-bench attachment system (no hotspot, no model).
 */
public final class GunSling {
    public static final String TAG = "AflSling";

    private GunSling() {}

    public static boolean has(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(TAG);
    }

    /** A primary weapon (long gun) without a sling yet. */
    public static boolean canFit(ItemStack gun) {
        return HotbarRoles.weaponRole(gun) == HotbarRoles.Role.PRIMARY && !has(gun) && gun.getCount() == 1;
    }

    public static boolean isSling(ItemStack stack) {
        return stack.is(AflItems.GUN_SLING.get());
    }

    public static void fit(ItemStack gun) {
        gun.getOrCreateTag().putBoolean(TAG, true);
    }

    /** Takes the sling off; returns the sling item, or empty when there was none. */
    public static ItemStack remove(ItemStack gun) {
        if (!has(gun)) return ItemStack.EMPTY;
        gun.getTag().remove(TAG);
        if (gun.getTag().isEmpty()) gun.setTag(null);
        return new ItemStack(AflItems.GUN_SLING.get());
    }
}
