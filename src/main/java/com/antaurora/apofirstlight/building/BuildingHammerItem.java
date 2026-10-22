package com.antaurora.apofirstlight.building;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The building hammer (building hammer B0, docs/gameplay/building_hammer_b0.md): a double-faced club hammer, the building
 * mode's own tool. In edit mode (B with it in hand) the client (client/building/BuildingHammerClient) takes the use and
 * attack clicks on blocks: right click opens the building piece catalog or installs the chosen piece, left click takes a
 * catalog piece down. Out of edit mode it is a plain tool (hangs on the tool rack and the workbench); it is no digging tool, so the mining rules treat it like a bare hand.
 * The item itself does nothing.
 */
public final class BuildingHammerItem extends Item {
    public BuildingHammerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(oldStack.getItem());
    }
}
