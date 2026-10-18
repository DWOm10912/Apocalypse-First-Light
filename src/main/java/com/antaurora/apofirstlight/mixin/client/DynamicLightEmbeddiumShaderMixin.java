package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.light.DynamicLightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Dynamic lights in Embeddium's terrain program (client/light/DynamicLightShaders, docs/rendering/dynamic_lights_v1.md):
 * each stage's final source (imports and defines in) as its GlShader hands it to GL. Embeddium 5681725; without
 * Embeddium the mixin is skipped (and the lights stay off: the vanilla terrain programs are not patched).
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.GlShader", remap = false)
public abstract class DynamicLightEmbeddiumShaderMixin {
    @ModifyArg(method = "<init>", index = 1, remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderWorkarounds;safeShaderSource(ILjava/lang/CharSequence;)V", remap = false))
    // an instance handler: the call sits in the constructor (after super()), and the handler's static-ness must match it
    private CharSequence afl$lights(int handle, CharSequence source) {
        return DynamicLightShaders.embeddium(handle, source);
    }
}
