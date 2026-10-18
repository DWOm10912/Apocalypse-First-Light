package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.light.DynamicLightShaders;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

/**
 * Dynamic lights in the vanilla programs (client/light/DynamicLightShaders, docs/rendering/dynamic_lights_v1.md): a
 * stage's source after vanilla's preprocessor (imports in), just before it goes to GL, patched when it is one of the
 * entity fragment stages or AFL's road program. The name comes from the method's start (render thread only).
 */
@Mixin(Program.class)
public abstract class DynamicLightProgramMixin {
    @Unique
    private static String afl$name = "";

    @Inject(method = "compileShaderInternal", at = @At("HEAD"))
    private static void afl$capture(Program.Type type, String name, InputStream stream, String sourceName, GlslPreprocessor preprocessor,
                                 CallbackInfoReturnable<Integer> cir) {
        afl$name = name;
    }

    @ModifyArg(method = "compileShaderInternal", index = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"))
    private static List<String> afl$lights(int shader, List<String> source) {
        String joined = String.join("", source);
        String patched = DynamicLightShaders.vanilla(GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE), afl$name, joined);
        return patched == joined ? source : List.of(patched);
    }
}
