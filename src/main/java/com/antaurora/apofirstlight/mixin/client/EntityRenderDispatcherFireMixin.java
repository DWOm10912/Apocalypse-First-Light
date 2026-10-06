package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.EntityFlames;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Burning things wear the fuel flames instead of vanilla's fire texture stacked in rings up the body (2026-10-05,
 * docs/gameplay/fuel_fire_v1.md "火焰效果 V2"): client/EntityFlames draws them in the same pose.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherFireMixin {
    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true)
    private void afl$fuelFlames(PoseStack pose, MultiBufferSource buffers, Entity entity, CallbackInfo ci) {
        EntityFlames.render(pose, buffers, entity);
        ci.cancel();
    }
}
