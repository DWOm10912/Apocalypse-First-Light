package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FirstPersonBody;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person body V1 (docs/rendering/first_person_body_v1.md): the own body shows no helmet and no armour on its hidden arms. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerFirstPersonBodyMixin {
    @Inject(method = "setPartVisibility", at = @At("TAIL"))
    private void afl$firstPersonArmour(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
        FirstPersonBody.hideArmour(model);
    }
}
