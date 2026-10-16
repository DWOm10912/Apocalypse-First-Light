package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FirstPersonBody;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person body V1 (docs/rendering/first_person_body_v1.md): the own body's arms swing forward less while walking, so
 * they do not reach into the view while the first-person hand is still there (client/FirstPersonBody#limitArms).
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelFirstPersonBodyMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void afl$firstPersonArmSwing(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        FirstPersonBody.limitArms((HumanoidModel<?>) (Object) this, entity);
    }
}
