package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Wearable gear with its own grid (docs/ui/afl_inventory_grid_v0_plan.md 1): a chest rig, a backpack. The contents live
 * in the stack's "Items" list, so they stay inside when the gear is taken off, dropped or put into a corpse. Gear like
 * this never goes into a grid itself (no containers in containers in V0).
 */
public interface GridContainerItem {
    /** The Curios slot type the gear is worn in. */
    String wornSlot();

    GridLayout layout(ItemStack stack);

    static GridStorage read(ItemStack stack, boolean client) {
        if (!(stack.getItem() instanceof GridContainerItem gear)) throw new IllegalArgumentException("not grid gear: " + stack);
        var tag = stack.getTag();
        ListTag list = tag != null && tag.contains("Items", Tag.TAG_LIST) ? tag.getList("Items", Tag.TAG_COMPOUND) : null;
        return GridStorage.load(gear.layout(stack), list, client);
    }

    /** Writes the contents back into the stack (the stack object itself, so whoever holds it sees the change). */
    static void write(ItemStack stack, GridStorage storage) {
        storage.prune();
        if (storage.isEmpty()) {
            var tag = stack.getTag();
            if (tag != null) {
                tag.remove("Items");
                if (tag.isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().put("Items", storage.save());
        }
    }
}
