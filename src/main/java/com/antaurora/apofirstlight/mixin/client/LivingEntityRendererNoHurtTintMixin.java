package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.creature.AflCreatures;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AFL bodies (players and AFL's creatures, creature/AflCreatures) are not tinted red while hurt or dying (user
 * 2026-10-26: wounds show as blood and lost limbs later). The white flash of a creeper about to blow stays.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererNoHurtTintMixin {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void afl$noHurtTint(LivingEntity entity, float whiteOverlay, CallbackInfoReturnable<Integer> cir) {
        if (AflCreatures.isAflBody(entity)) cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlay), OverlayTexture.v(false)));
    }
}
