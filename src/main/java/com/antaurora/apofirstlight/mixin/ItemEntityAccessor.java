package com.antaurora.apofirstlight.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** Ground items (inventory/grid/GroundItems): who may pick an item up, and whether it may be picked up at all. */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("pickupDelay") int afl$pickupDelay();

    @Accessor("target") UUID afl$target();
}
