package com.antaurora.apofirstlight.inventory;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/** Placement policy only. The vanilla inventory and its persisted contents remain intact. */
public final class PlayerStorageCapacity {
    private PlayerStorageCapacity() {}

    /**
     * Capacity changes belong here, rather than in individual menus or pickup hooks: the hotbar, plus what a
     * StorageExpander worn in the back slot opens (a future backpack), at most the whole vanilla inventory.
     */
    public static int getUnlockedInventorySlots(Player player) {
        if (player.isCreative() || player.isSpectator()) return Inventory.INVENTORY_SIZE;
        var back = AflEquipmentSlots.get(player, AflEquipmentSlots.BACK);
        int extra = back.getItem() instanceof StorageExpander expander ? Math.max(0, expander.extraStorageSlots(back)) : 0;
        return Math.min(Inventory.INVENTORY_SIZE, Inventory.getSelectionSize() + extra);
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
