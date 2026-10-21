package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.map.MapStyle;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

/**
 * The map tiles' program (apocalypse_firstlight:afl_map, AFL Map V1): a tile texture through a rounded-rectangle mask in
 * framebuffer pixels, so the minimap can turn under a fixed frame; unknown ground as fog or clear; an optional live
 * circle. Masks are given in GUI coordinates and turned into framebuffer pixels here.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MapProgram {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ShaderInstance program;
    private static int maskAt, radiusAt, circleAt, featherAt, fogMixAt, fogAt, alphaAt;

    private MapProgram() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_map"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> {
                int id = loaded.getId();
                maskAt = Uniform.glGetUniformLocation(id, "Mask");
                radiusAt = Uniform.glGetUniformLocation(id, "MaskRadius");
                circleAt = Uniform.glGetUniformLocation(id, "Circle");
                featherAt = Uniform.glGetUniformLocation(id, "Feather");
                fogMixAt = Uniform.glGetUniformLocation(id, "FogMix");
                fogAt = Uniform.glGetUniformLocation(id, "Fog");
                alphaAt = Uniform.glGetUniformLocation(id, "Alpha");
                program = maskAt < 0 || alphaAt < 0 ? null : loaded;
                if (program == null) LOGGER.warn("AFL map program: uniforms not found; no map is drawn");
            });
        } catch (Exception e) {
            program = null;
            LOGGER.warn("AFL map program not loaded ({}); no map is drawn", e.toString());
        }
    }

    public static boolean ready() { return program != null; }

    /**
     * How tiles are drawn: the mask (GUI coordinates) and its corner radius, unknown as fog or clear, the live circle
     * (GUI centre and radius; radius &lt;= 0 off) and its feather (GUI), the overall alpha.
     */
    public record Look(float x0, float y0, float x1, float y1, float radius, boolean fog, float cx, float cy, float cr, float feather, float alpha) {}

    /** A tile's quad (pose coordinates) with its texture. */
    public static void tile(Matrix4f m, float x0, float y0, float x1, float y1, int texture, Look look) {
        ShaderInstance sh = program;
        if (sh == null || texture < 0) return;
        Minecraft mc = Minecraft.getInstance();
        double s = mc.getWindow().getGuiScale();
        int fh = mc.getWindow().getHeight();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, x0, y0, 0).uv(0, 0).endVertex();
        b.vertex(m, x0, y1, 0).uv(0, 1).endVertex();
        b.vertex(m, x1, y1, 0).uv(1, 1).endVertex();
        b.vertex(m, x1, y0, 0).uv(1, 0).endVertex();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(() -> sh);
        sh.apply();
        GL20.glUniform4f(maskAt, (float) (look.x0() * s), (float) (fh - look.y1() * s), (float) (look.x1() * s), (float) (fh - look.y0() * s));
        GL20.glUniform1f(radiusAt, (float) (look.radius() * s));
        GL20.glUniform3f(circleAt, (float) (look.cx() * s), (float) (fh - look.cy() * s), (float) (look.cr() * s));
        GL20.glUniform1f(featherAt, (float) (look.feather() * s));
        GL20.glUniform1f(fogMixAt, look.fog() ? 1 : 0);
        GL20.glUniform3f(fogAt, (MapStyle.FOG >> 16 & 255) / 255F, (MapStyle.FOG >> 8 & 255) / 255F, (MapStyle.FOG & 255) / 255F);
        GL20.glUniform1f(alphaAt, look.alpha());
        BufferUploader.drawWithShader(b.end());
    }
}
