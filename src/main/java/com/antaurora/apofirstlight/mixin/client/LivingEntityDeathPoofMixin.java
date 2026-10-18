package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.survivor.DeathCamera;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 4): a dead player whose corpse lies beside it makes no death poof. Vanilla
 * removes the dead body after 20 ticks with entity event 60 (white smoke); the corpse is the body now, so the smoke
 * would look like the corpse going up in it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathPoofMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void afl$noPoofBesideCorpse(byte id, CallbackInfo ci) {
        if (id == 60 && (Object) this instanceof Player player && player.level().isClientSide
                && DeathCamera.corpseBeside(player)) {
            ci.cancel();
        }
    }
}
