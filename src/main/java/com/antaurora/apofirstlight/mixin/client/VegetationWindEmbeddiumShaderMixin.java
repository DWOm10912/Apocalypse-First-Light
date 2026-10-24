package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.vegetation.client.VegetationWind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The mesh plants' wind in Embeddium's terrain vertex stage (vegetation/client/VegetationWind, docs/gameplay/
 * vegetation_v1_design.md 4): each stage's final source as its GlShader hands it to GL, the same call the dynamic lights
 * patch (their lines and these do not overlap, either order works). Embeddium 5681725; skipped without it.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.GlShader", remap = false)
public abstract class VegetationWindEmbeddiumShaderMixin {
    @ModifyArg(method = "<init>", index = 1, remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderWorkarounds;safeShaderSource(ILjava/lang/CharSequence;)V", remap = false))
    private CharSequence afl$vegetationWind(int handle, CharSequence source) {
        return VegetationWind.embeddium(handle, source);
    }
}
