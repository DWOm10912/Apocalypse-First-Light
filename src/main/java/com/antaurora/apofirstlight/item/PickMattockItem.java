package com.antaurora.apofirstlight.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Pick mattock (Rock R1a, docs/gameplay/rock_r1_stones_boulders_design.md 4, 11.2): a 5 lb forged head, pick and adze, on a
 * 0.91 m hickory handle, 3.3 kg (item_mass). It is the one tool that strikes a boulder (boulder/BoulderStrike, started by
 * boulder/PickClient). R1 keeps no wear on it; creative only (no recipe before the recipe rework).
 */
public final class PickMattockItem extends Item {
    public PickMattockItem() {
        super(new Properties().stacksTo(1));
    }

    /** Only a new item in the hand bobs it (a strike never re-equips). */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }
}
