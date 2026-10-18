package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.light.DynamicLights;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dynamic lights' uniforms for a vanilla program as it is bound (client/light/DynamicLights#bind; programs without the
 * light code are passed over after one look).
 */
@Mixin(ShaderInstance.class)
public abstract class DynamicLightShaderApplyMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void afl$lights(CallbackInfo ci) {
        DynamicLights.bind(((ShaderInstance) (Object) this).getId());
    }
}
