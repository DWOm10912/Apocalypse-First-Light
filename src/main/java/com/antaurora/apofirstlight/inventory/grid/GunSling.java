package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.weapon.NativeAttachment;
import com.antaurora.apofirstlight.weapon.NativeAttachments;
import net.minecraft.world.item.ItemStack;

/**
 * A sling fitted to a long gun (docs/ui/afl_inventory_grid_v0_plan.md 10): only a gun with one can be carried on the back
 * at key 1. Since 2026-10-22 (user) it is an attachment ({@link NativeAttachment.Slot#SLING}), fitted and taken off at the
 * rear swivel hotspot on the Z page or the gun maintenance bench, the gun JSON's {@code sling_slot}; the old
 * {@code AflSling} flag still reads as a fitted sling (NativeAttachments). No model on the gun.
 */
public final class GunSling {
    private GunSling() {}

    public static boolean has(ItemStack stack) {
        return !stack.isEmpty() && !NativeAttachments.stored(stack, NativeAttachment.Slot.SLING).isEmpty();
    }

    /** Setup only (GameTests, dev): see NativeAttachments.fitSling. */
    public static void fit(ItemStack gun) {
        NativeAttachments.fitSling(gun);
    }
}
