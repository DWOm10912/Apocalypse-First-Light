package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.creature.AflCreatures;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No white death smoke for AFL bodies (players and AFL's creatures, creature/AflCreatures; user 2026-10-26). Vanilla
 * removes a dead body after 20 ticks with entity event 60 (the smoke). Ragdoll V1 first dropped it only for a player whose
 * corpse lies beside it (the corpse is the body now); since 2026-10-26 no AFL body makes it at all.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathPoofMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void afl$noDeathPoof(byte id, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (id == 60 && self.level().isClientSide && AflCreatures.isAflBody(self)) ci.cancel();
    }
}
