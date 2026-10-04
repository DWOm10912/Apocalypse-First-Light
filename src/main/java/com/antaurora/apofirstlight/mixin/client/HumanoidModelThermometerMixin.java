package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.temperature.ClientThermometerHands;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temperature V1: while someone holds a clinical thermometer under the arm, that arm goes across the chest to the other
 * armpit (ClientThermometerHands#poseArm), set after vanilla's pose so nothing else changes. No custom ArmPose enum
 * value: one created at run time falls outside the arm-pose switch tables vanilla built at start-up and breaks rendering
 * (the native pistol checks assert "no custom enum" for the same reason).
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelThermometerMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void afl$thermometerArmpit(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        ClientThermometerHands.poseArm((HumanoidModel<?>)(Object)this, entity);
    }
}
