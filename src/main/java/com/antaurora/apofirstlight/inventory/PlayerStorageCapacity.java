package com.antaurora.apofirstlight.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Placement policy only. The vanilla inventory and its persisted contents remain intact. */
public final class PlayerStorageCapacity {
    private PlayerStorageCapacity() {}

    /** Creative and Spectator: the whole vanilla inventory, no hotbar roles. */
    public static boolean unrestricted(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    /**
     * The vanilla inventory slots in use: in Survival / Adventure only the hotbar's three weapon positions and the hand
     * (HotbarRoles, docs/ui/afl_inventory_grid_v0_plan.md 10); everything else is carried in the grids. The
     * StorageExpander path of the old locked-slot system no longer opens anything (no item uses it since Grid Inventory V0).
     */
    public static int getUnlockedInventorySlots(Player player) {
        return unrestricted(player) ? Inventory.INVENTORY_SIZE : HotbarRoles.USED;
    }

    public static boolean isLocked(Inventory inventory, int storageIndex) {
        return storageIndex >= 0 && storageIndex < Inventory.INVENTORY_SIZE
                && storageIndex >= getUnlockedInventorySlots(inventory.player);
    }

    public static boolean isLocked(Slot slot) {
        return slot != null && slot.container instanceof Inventory inventory
                && isLocked(inventory, slot.getContainerSlot());
    }

    /**
     * Whether putting this stack at the storage index is refused: the off hand in every game mode (NoOffHand), a locked
     * slot, or a weapon position that does not take the item (HotbarRoles: role, sling, holster). Taking out is never
     * refused here.
     */
    public static boolean rejects(Inventory inventory, int storageIndex, ItemStack stack) {
        if (storageIndex == Inventory.SLOT_OFFHAND) return true;
        if (isLocked(inventory, storageIndex)) return true;
        return !unrestricted(inventory.player) && !HotbarRoles.accepts(inventory.player, storageIndex, stack);
    }

    public static boolean rejects(Slot slot, ItemStack stack) {
        return slot != null && slot.container instanceof Inventory inventory
                && rejects(inventory, slot.getContainerSlot(), stack);
    }

    /**
     * Overflow: an item sitting where it could not be put now (a locked slot, a weapon position whose sling or holster is
     * gone, or anything an older save left in the hotbar). It can be taken out; nothing merges onto it.
     */
    public static boolean isOverflow(Inventory inventory, int storageIndex) {
        if (storageIndex < 0 || storageIndex >= inventory.items.size()) return false;
        var stack = inventory.items.get(storageIndex);
        return !stack.isEmpty() && rejects(inventory, storageIndex, stack);
    }

    /**
     * A target for vanilla's "find a place" searches (pickup, merging, items given back): in Survival / Adventure none,
     * since everything goes into the grids (InventoryGridMixin); weapons reach their positions and items the hand only by
     * the rules that put them there.
     */
    public static boolean isGenericTarget(Inventory inventory, int storageIndex) {
        if (isLocked(inventory, storageIndex)) return false;
        return unrestricted(inventory.player);
    }
}
