package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.infected.InfectedEntityRules;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents daylight ignition before fire ticks or the synced fire flag can be set. */
@Mixin(Zombie.class)
public abstract class ZombieSunlightMixin {
    @Inject(method = "isSunSensitive()Z", at = @At("HEAD"), cancellable = true)
    private void apocalypse$disableDaylightBurn(CallbackInfoReturnable<Boolean> callback) {
        Zombie zombie = (Zombie) (Object) this;
        if (!zombie.level().isClientSide() && InfectedEntityRules.isSunlightImmune(zombie)) {
            callback.setReturnValue(false);
        }
    }
}
