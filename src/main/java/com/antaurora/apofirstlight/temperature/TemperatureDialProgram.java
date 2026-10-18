package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflGaugeProgram;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.FloatBuffer;

/**
 * The temperature dial drawn on the GPU (apocalypse_firstlight:afl_temperature_dial, 2026-10-17): the same picture as
 * TemperatureDial#fill, per real pixel, every frame. On the CPU the dial was rastered 30 times a second whatever the HUD
 * did (it breathes and its chevrons flow), a 175 px square of distance fields at GUI scale 5 (docs/ui/hud_v2_research.md).
 * Without the program (or with AflGaugeProgram.enabled off, the probe's "as before" phase) TemperatureDial rasters it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class TemperatureDialProgram {
    /** vec4s of the look, as LOOK in afl_temperature_dial.fsh. */
    static final int VECS = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(VECS * 4);
    private static volatile ShaderInstance program;
    private static int lookAt = -1;

    private TemperatureDialProgram() {
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_temperature_dial"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> {
                program = loaded;
                lookAt = Uniform.glGetUniformLocation(loaded.getId(), "Look");
                if (lookAt < 0) {
                    LOGGER.warn("AFL temperature dial program: uniform not found; the dial is rastered on the CPU");
                    program = null;
                }
            });
        } catch (Exception e) {
            program = null;
            LOGGER.warn("AFL temperature dial program not loaded ({}); the dial is rastered on the CPU", e.toString());
        }
    }

    static boolean ready() {
        return AflGaugeProgram.enabled && program != null;
    }

    /** The dial over the square of `size` real pixels at (x0, y0) (the pose already scaled to real pixels). */
    static void draw(GuiGraphics graphics, int x0, int y0, int size, float[] look) {
        ShaderInstance sh = program;
        FloatBuffer d = DATA;
        d.clear();
        d.put(look, 0, VECS * 4).flip();
        Matrix4f m = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(m, x0, y0, 0).uv(0, 0).endVertex();
        builder.vertex(m, x0, y0 + size, 0).uv(0, size).endVertex();
        builder.vertex(m, x0 + size, y0 + size, 0).uv(size, size).endVertex();
        builder.vertex(m, x0 + size, y0, 0).uv(size, 0).endVertex();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> sh);
        // the array uniform is the program's own state: set while it is bound (apply), kept for the draw that follows
        sh.apply();
        GL20.glUniform4fv(lookAt, d);
        BufferUploader.drawWithShader(builder.end());
    }
}
