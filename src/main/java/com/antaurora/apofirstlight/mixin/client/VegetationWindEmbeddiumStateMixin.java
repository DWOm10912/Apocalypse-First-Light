package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.vegetation.client.VegetationWind;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mesh plants' wind uniforms for Embeddium's terrain program once a pass has bound it and set it up
 * (vegetation/client/VegetationWind#bind). Embeddium 5681725; skipped without it.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface", remap = false)
public abstract class VegetationWindEmbeddiumStateMixin {
    @Inject(method = "setupState", at = @At("TAIL"), remap = false, require = 0)
    private void afl$vegetationWind(CallbackInfo ci) {
        VegetationWind.bind(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
}
