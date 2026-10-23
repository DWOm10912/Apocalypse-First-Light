package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.LightningAim;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightning V2 (world/LightningAim): a strike on a lightning rod (its current led to ground), on a truck or on a tree lights
 * no vanilla fire where the bolt ends; a tree's crown catches fire by its own chance (world/LightningTrees).
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltFireMixin {
    @Inject(method = "spawnFire", at = @At("HEAD"), cancellable = true)
    private void afl$noFire(int extra, CallbackInfo ci) {
        if (LightningAim.noFire((LightningBolt) (Object) this)) ci.cancel();
    }
}
