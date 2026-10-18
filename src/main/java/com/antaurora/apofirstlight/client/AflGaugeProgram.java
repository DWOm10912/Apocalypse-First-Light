package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
import java.util.List;

/**
 * The bands of an {@link AflGauge} drawn on the GPU (apocalypse_firstlight:afl_gauge, 2026-10-17): the same coverage as
 * AflGauge#raster, per real pixel, so nothing is rastered on the CPU or uploaded when a gauge moves. On the CPU a moving
 * gauge cost its whole texture every frame: health 7.5 ms, stamina 5.7 ms at GUI scale 5 (2560 x 1440), so a filling
 * stamina arc took the user from about 125 to 77 fps (RainPerfProbe hud mode, docs/ui/hud_v2_research.md). Without the
 * program, or with more than {@link #MAX_BANDS} bands, AflGauge rasters them as before.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflGaugeProgram {
    /** As MAX_BANDS in afl_gauge.fsh. */
    static final int MAX_BANDS = 48;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(MAX_BANDS * 16);
    private static volatile ShaderInstance program;
    private static int bandsAt = -1, countAt = -1, centerAt = -1;
    /** Dev switch (RainPerfProbe): false rasters every gauge on the CPU as before. */
    public static volatile boolean enabled = true;

    private AflGaugeProgram() {
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_gauge"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> {
                program = loaded;
                int id = loaded.getId();
                bandsAt = Uniform.glGetUniformLocation(id, "Bands");
                countAt = Uniform.glGetUniformLocation(id, "BandCount");
                centerAt = Uniform.glGetUniformLocation(id, "Center");
                if (bandsAt < 0 || countAt < 0 || centerAt < 0) {
                    LOGGER.warn("AFL gauge program: uniforms not found; the gauges are rastered on the CPU");
                    program = null;
                }
            });
        } catch (Exception e) {
            program = null;
            LOGGER.warn("AFL gauge program not loaded ({}); the gauges are rastered on the CPU", e.toString());
        }
    }

    /** Whether these bands can be drawn here. */
    static boolean ready(List<AflGauge.Band> bands) {
        return enabled && program != null && bands.size() <= MAX_BANDS;
    }

    /**
     * The bands over the square of `size` real pixels at (x0, y0) (real pixels; the pose is already scaled to them), the
     * gauge's centre at (cx, cy) in it, s real pixels per GUI pixel.
     */
    static void draw(GuiGraphics graphics, int x0, int y0, int size, double cx, double cy, int s, List<AflGauge.Band> bands) {
        ShaderInstance sh = program;
        FloatBuffer d = DATA;
        d.clear();
        int n = 0;
        for (AflGauge.Band b : bands) {
            if (b.alpha() <= 0) continue;
            d.put((float) (b.outer() * s)).put((float) (b.width() * s)).put((float) b.start()).put((float) b.span());
            d.put(b.segments()).put((float) (b.gapGui() * s)).put((float) b.from()).put((float) b.to());
            d.put((b.rgb() >> 16 & 255) / 255F).put((b.rgb() >> 8 & 255) / 255F).put((b.rgb() & 255) / 255F).put((float) b.alpha());
            d.put((b.backRgb() >> 16 & 255) / 255F).put((b.backRgb() >> 8 & 255) / 255F).put((b.backRgb() & 255) / 255F).put((float) b.backAlpha());
            n++;
        }
        if (n == 0) return;
        d.flip();
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
        // the array uniforms are the program's own state: set while it is bound (apply), kept for the draw that follows
        sh.apply();
        GL20.glUniform4fv(bandsAt, d);
        GL20.glUniform1i(countAt, n);
        GL20.glUniform2f(centerAt, (float) cx, (float) cy);
        BufferUploader.drawWithShader(builder.end());
    }
}
