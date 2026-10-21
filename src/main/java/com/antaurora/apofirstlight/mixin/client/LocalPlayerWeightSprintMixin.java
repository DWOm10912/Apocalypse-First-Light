package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.weight.ClientWeightPenalties;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A heavy load, being winded (Stamina V1) or a broken leg (health/Fractures) blocks sprinting the way an empty stomach does: vanilla checks this both
 * before starting a sprint and every tick while sprinting (aiStep), so a running sprint stops too. Riding is left to the
 * mount.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerWeightSprintMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void afl$heavyLoad(CallbackInfoReturnable<Boolean> cir) {
        if (!((LocalPlayer)(Object)this).isPassenger() && (ClientWeightPenalties.sprintBlocked() || com.antaurora.apofirstlight.stamina.ClientStamina.winded()
                || com.antaurora.apofirstlight.health.ClientHealth.sprintBlocked()))
            cir.setReturnValue(false);
    }
}
