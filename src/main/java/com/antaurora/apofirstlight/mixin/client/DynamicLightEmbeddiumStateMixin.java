package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.light.DynamicLights;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dynamic lights' uniforms for Embeddium's terrain program once a pass has bound it and set it up (client/light/
 * DynamicLights#bind). Embeddium 5681725; skipped without it.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface", remap = false)
public abstract class DynamicLightEmbeddiumStateMixin {
    @Inject(method = "setupState", at = @At("TAIL"), remap = false, require = 0)
    private void afl$lights(CallbackInfo ci) {
        DynamicLights.bind(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
}
