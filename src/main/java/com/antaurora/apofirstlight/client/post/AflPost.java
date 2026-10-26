package com.antaurora.apofirstlight.client.post;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.GpuTimer;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * AFL picture upgrade V1a, without a shader pack (docs/rendering/vanilla_visual_upgrade_v1.md): a short chain of
 * full-screen passes over the vanilla frame.
 * <ul>
 *   <li>Ambient occlusion (afl_ssao, afl_ssao_apply): after the opaque world (RenderLevelStageEvent AFTER_BLOCK_ENTITIES),
 *   before the translucent layer, from the depth alone; multiplied into the frame. The water program's copy is made
 *   later, so the water shows the darkened ground.</li>
 *   <li>Edge smoothing and grade (afl_final): after the whole frame, the hand included, before the HUD
 *   (mixin client/PostGameRendererMixin, after GameRenderer#renderLevel), over a copy of the frame.</li>
 * </ul>
 * Off with a shader pack (the pack draws everything its own way). Each part has a switch ({@code /afl_render post}).
 */
public final class AflPost {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Switches and settings ({@code /afl_render post}). */
    public static boolean enabled = true, ao = true, smooth = true, grade = true;
    public static float aoStrength = 1.0F, aoRadius = 0.6F, contrast = 0.18F, saturation = 1.08F;

    private static volatile ShaderInstance ssao, ssaoApply, fin;
    private static final Target AO = new Target(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT, GL11.GL_NEAREST);
    private static final Target COPY = new Target(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL11.GL_LINEAR);
    private static final Matrix4f INV = new Matrix4f();
    private static final GpuTimer GPU_AO = new GpuTimer(), GPU_FINAL = new GpuTimer();
    private static boolean failed;

    private AflPost() {
    }

    private static boolean on() {
        return enabled && !failed && !AflShaderCompat.shaderPackInUse() && Minecraft.getInstance().level != null;
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Programs {
        private Programs() {
        }

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_ssao"),
                        DefaultVertexFormat.POSITION), loaded -> ssao = loaded);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_ssao_apply"),
                        DefaultVertexFormat.POSITION), loaded -> ssaoApply = loaded);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_final"),
                        DefaultVertexFormat.POSITION), loaded -> fin = loaded);
            } catch (Exception e) {
                LOGGER.warn("AFL post: programs not loaded ({}); the picture stays vanilla", e.toString());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
    public static final class Stages {
        private Stages() {
        }

        @SubscribeEvent
        public static void stage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !ao || !on()) return;
            if (AflShaderCompat.activeShadowPass()) return;
            ShaderInstance a = ssao, b = ssaoApply;
            if (a == null || b == null) return;
            try {
                occlusion(a, b, event.getProjectionMatrix());
            } catch (RuntimeException e) {
                fail(e);
            }
        }
    }

    private static void occlusion(ShaderInstance a, ShaderInstance b, Matrix4f projection) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) return;
        GPU_AO.begin();
        AO.ensure(w, h);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, AO.fbo);
        GlStateManager._viewport(0, 0, w, h);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        a.setSampler("Depth", main.getDepthTextureId());
        a.safeGetUniform("WorldProj").set(projection);
        a.safeGetUniform("InvProj").set(INV.set(projection).invert());
        a.safeGetUniform("ViewSize").set((float) w, (float) h);
        a.safeGetUniform("Radius").set(aoRadius);
        fullScreen(a);
        main.bindWrite(true);
        // multiplied into the frame (afl_ssao_apply.json's blend; set here too: the blend state is cached)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        b.setSampler("Ao", AO.texture);
        b.safeGetUniform("ViewSize").set((float) w, (float) h);
        b.safeGetUniform("Strength").set(aoStrength);
        fullScreen(b);
        restore();
        GPU_AO.end();
    }

    /** After the whole frame and the hand (PostGameRendererMixin), before the HUD. */
    public static void afterLevel() {
        if (!(smooth || grade) || !on()) return;
        ShaderInstance f = fin;
        if (f == null) return;
        try {
            finish(f);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private static void finish(ShaderInstance f) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) return;
        GPU_FINAL.begin();
        COPY.ensure(w, h);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, COPY.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        f.setSampler("Scene", COPY.texture);
        f.safeGetUniform("ViewSize").set((float) w, (float) h);
        f.safeGetUniform("Smooth").set(smooth ? 1F : 0F);
        f.safeGetUniform("Contrast").set(grade ? contrast : 0F);
        f.safeGetUniform("Saturation").set(grade ? saturation : 1F);
        fullScreen(f);
        restore();
        GPU_FINAL.end();
    }

    private static void fullScreen(ShaderInstance sh) {
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.vertex(-1, -1, 0).endVertex();
        b.vertex(1, -1, 0).endVertex();
        b.vertex(1, 1, 0).endVertex();
        b.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private static void restore() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void fail(RuntimeException e) {
        failed = true;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        restore();
        LOGGER.error("AFL post: switched off after an error", e);
    }

    /** Dev readout ({@code /afl_render post status}). */
    public static String status() {
        String why = failed ? "FAILED (see log)" : AflShaderCompat.shaderPackInUse() ? "off under a shader pack"
                : ssao == null || fin == null ? "programs missing" : enabled ? "on" : "off";
        return String.format(Locale.ROOT, "%s; ao %s (strength %.2f, radius %.2f m), smooth %s, grade %s (contrast %.2f, saturation %.2f)%s",
                why, ao ? "on" : "off", aoStrength, aoRadius, smooth ? "on" : "off", grade ? "on" : "off", contrast, saturation,
                GpuTimer.enabled ? String.format(Locale.ROOT, "; gpu ao %d us, final %d us", GPU_AO.micros(), GPU_FINAL.micros()) : "");
    }

    /** One colour texture and its framebuffer, sized to the frame. */
    private static final class Target {
        final int internal, format, type, filter;
        int fbo = -1, texture = -1, width, height;

        Target(int internal, int format, int type, int filter) {
            this.internal = internal;
            this.format = format;
            this.type = type;
            this.filter = filter;
        }

        void ensure(int w, int h) {
            if (fbo >= 0 && w == width && h == height) return;
            if (fbo >= 0) {
                GlStateManager._glDeleteFramebuffers(fbo);
                GlStateManager._deleteTexture(texture);
            }
            width = w;
            height = h;
            texture = GlStateManager._genTexture();
            GlStateManager._bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(0);
            fbo = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("AFL post target: framebuffer status " + status);
        }
    }
}
