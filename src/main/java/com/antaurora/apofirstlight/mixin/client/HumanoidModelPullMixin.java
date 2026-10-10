package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.PortableGeneratorPull;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Portable diesel generator V1: while someone pulls its recoil starter, their right arm points at the T handle
 * (PortableGeneratorPull#poseArm), set after vanilla's pose. A stand-in until the player animation rework.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelPullMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void afl$recoilPull(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                float netHeadYaw, float headPitch, CallbackInfo ci) {
        PortableGeneratorPull.poseArm((HumanoidModel<?>) (Object) this, entity, ageInTicks);
    }
}
