package com.antaurora.apofirstlight.inventory;

import net.minecraft.world.item.ItemStack;

/**
 * An item that, worn in the back slot (AflEquipmentSlots.BACK), opens more of the player's storage slots beyond the
 * hotbar (PlayerStorageCapacity). For the future backpacks; nothing implements it yet.
 */
public interface StorageExpander {
    /** Storage slots this stack opens on top of the 9 hotbar slots (the total is capped at 36). */
    int extraStorageSlots(ItemStack stack);
}
