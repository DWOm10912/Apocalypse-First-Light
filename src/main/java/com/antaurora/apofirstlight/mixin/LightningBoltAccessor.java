package com.antaurora.apofirstlight.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lightning injury V1 (health/LightningInjury): a visual-only bolt (a lightning rod's spark, a trident's show) hurts nobody. */
@Mixin(LightningBolt.class)
public interface LightningBoltAccessor {
    @Accessor("visualOnly")
    boolean afl$visualOnly();
}
