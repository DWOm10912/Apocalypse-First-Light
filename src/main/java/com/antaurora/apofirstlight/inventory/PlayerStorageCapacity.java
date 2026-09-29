package com.antaurora.apofirstlight.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/** Placement policy only. The vanilla inventory and its persisted contents remain intact. */
public final class PlayerStorageCapacity {
    private PlayerStorageCapacity() {}

    /** Future capacity changes belong here, rather than in individual menus or pickup hooks. */
    public static int getUnlockedInventorySlots(Player player) {
        return player.isCreative() || player.isSpectator()
                ? Inventory.INVENTORY_SIZE : Inventory.getSelectionSize();
    }

    public static boolean isLocked(Inventory inventory, int storageIndex) {
        return storageIndex >= 0 && storageIndex < Inventory.INVENTORY_SIZE
                && storageIndex >= getUnlockedInventorySlots(inventory.player);
    }

    public static boolean isLocked(Slot slot) {
        return slot != null && slot.container instanceof Inventory inventory
                && isLocked(inventory, slot.getContainerSlot());
    }
}
