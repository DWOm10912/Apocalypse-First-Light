package com.antaurora.apofirstlight.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Immutable request boundary: a future animation may delay sending, never authorize mutation. */
public record MaintenanceActionRequest(int containerId, BlockPos bench, long revision,
        ItemStack expectedGun, NativeAttachment.Slot target, int sourceSlot, ItemStack expectedSource) { }
