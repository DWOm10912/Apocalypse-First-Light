package com.antaurora.apofirstlight.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 has no public radius getter. Read only: no explosion behavior injection. */
@Mixin(Explosion.class)
public interface ExplosionAccessor {
    @Accessor("radius")
    float afl$getRadius();
}
