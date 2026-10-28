package com.antaurora.apofirstlight.client.liquid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * The look of physical water without a shader pack (docs/gameplay/water_v1_design.md 16.10, docs/rendering/
 * water_visual_v1_design.md 5.2): the program apocalypse_firstlight:afl_water, its ripple textures and the copy of the
 * frame it draws over. The water type's numbers (absorption, scattering, ripples) are the pond's for now; the per-type
 * table water_types.json comes with the full surface of package 6.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaterLook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation[] NORMALS = {
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/water/water_normal_0.png"),
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/water/water_normal_1.png")};
    /**
     * Rain water standing on earth, per metre: absorption and scattering, the colour of what it scatters (silt and clay
     * held in it: grey-brown), how bright its own colour is in full light. Turbid: a few centimetres show the ground, a
     * pit a metre deep is a body of brown-grey water, not a tinted sheet over its bottom (falling water 20, "我看这个水
     * 还是一张纸" in game 2026-10-25). The clear pond's numbers (absorption 0.60 / 0.16 / 0.12, scattering 0.30) let the
     * bottom of a metre-deep pit show nearly as it is. Settling clear over days and the other water types: package 6.
     */
    private static final float ABS_R = 0.80F, ABS_G = 0.50F, ABS_B = 0.60F, SCATTER = 3.0F, SCATTER_LIGHT = 0.45F;
    private static final float TINT_R = 0.62F, TINT_G = 0.58F, TINT_B = 0.44F;

    private static volatile ShaderInstance program, fallProgram;
    private static final int[] normal = {-1, -1};
    private static boolean texturesFailed;
    private static final SceneCopy COPY = new SceneCopy();
    private static final Matrix4f INV = new Matrix4f();
    /** Rings on the water (ClientFalls#impacts): x, z from the texture origin, strength, start (-1: steady). */
    private static final float[] IMPACTS = new float[16 * 4];
    private static int impactsProgram = -1, impactsAt = -1, impactCountAt = -1;

    private WaterLook() {
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_water"),
                    DefaultVertexFormat.BLOCK), loaded -> program = loaded);
        } catch (Exception e) {
            program = null;
            LOGGER.warn("AFL water: no water program ({}); the plain surface stays", e.toString());
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_water_fall"),
                    DefaultVertexFormat.NEW_ENTITY), loaded -> fallProgram = loaded);
        } catch (Exception e) {
            fallProgram = null;
            LOGGER.warn("AFL water: no falling-water program ({}); falls stay faint translucent", e.toString());
        }
    }

    /** The program of water in the air (ClientFalls); null: the vanilla translucent type. */
    static ShaderInstance fallProgram() {
        return program() == null ? null : fallProgram;
    }

    /** The falling-water program's inputs for this frame (the frame copied after the standing water). */
    static void setUpFall(ShaderInstance sh, ClientLevel level, Vec3 cam, float pt, Matrix4f projection, RenderTarget main) {
        sh.setSampler("Scene", COPY.color);
        // the standing water's ripples and its whole look (afl_water_look.glsl, Mesh water M3): its reflection traces the
        // depth copy too
        sh.setSampler("SceneDepth", COPY.depth);
        sh.safeGetUniform("InvProj").set(INV.set(projection).invert());
        sh.setSampler("Normal0", normal[0]);
        sh.setSampler("Normal1", normal[1]);
        sh.safeGetUniform("ViewSize").set((float) main.width, (float) main.height);
        sh.safeGetUniform("WaterTime").set(((level.getGameTime() % 24000L) + pt) / 20F);
        sky(sh, level, cam, pt);
        sh.safeGetUniform("Absorption").set(ABS_R, ABS_G, ABS_B);
        sh.safeGetUniform("Scatter").set(SCATTER);
        sh.safeGetUniform("ScatterLight").set(SCATTER_LIGHT);
        sh.safeGetUniform("ScatterTint").set(TINT_R, TINT_G, TINT_B);
    }

    /** The program, its textures loaded; null: draw the plain surface. */
    static ShaderInstance program() {
        ShaderInstance p = program;
        if (p == null || texturesFailed) return null;
        if (normal[0] < 0) {
            try {
                for (int i = 0; i < NORMALS.length; i++) normal[i] = load(NORMALS[i]);
            } catch (IOException | RuntimeException e) {
                texturesFailed = true;
                LOGGER.warn("AFL water: ripple textures not loaded ({}); the plain surface stays", e.toString());
                return null;
            }
        }
        return p;
    }

    /** A tiling texture with mipmaps (the ripples are fine: without them they shimmer in the distance). */
    private static int load(ResourceLocation id) throws IOException {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(id); NativeImage img = NativeImage.read(in)) {
            int levels = 31 - Integer.numberOfLeadingZeros(Math.max(img.getWidth(), img.getHeight()));
            int t = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(t, levels, img.getWidth(), img.getHeight());
            img.upload(0, 0, 0, false);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GlStateManager._bindTexture(0);
            return t;
        }
    }

    /** Copies the frame behind the water (main target bound for writing again after); false: nothing to copy. */
    static boolean copyScene(RenderTarget main) {
        return COPY.copy(main);
    }

    /** Sets the program's own inputs for this frame. */
    static void setUp(ShaderInstance sh, ClientLevel level, Vec3 cam, float pt, Matrix4f projection, RenderTarget main, double ox, double oz) {
        sh.setSampler("Scene", COPY.color);
        sh.setSampler("SceneDepth", COPY.depth);
        sh.setSampler("Normal0", normal[0]);
        sh.setSampler("Normal1", normal[1]);
        sh.safeGetUniform("InvProj").set(INV.set(projection).invert());
        sh.safeGetUniform("ViewSize").set((float) main.width, (float) main.height);
        sh.safeGetUniform("WaterTime").set(((level.getGameTime() % 24000L) + pt) / 20F);
        sky(sh, level, cam, pt);
        sh.safeGetUniform("Absorption").set(ABS_R, ABS_G, ABS_B);
        sh.safeGetUniform("Scatter").set(SCATTER);
        sh.safeGetUniform("ScatterLight").set(SCATTER_LIGHT);
        sh.safeGetUniform("ScatterTint").set(TINT_R, TINT_G, TINT_B);
        rainAndRings(sh, level, cam, ox, oz);
    }

    /** The sky, the horizon and the sun (or moon) the water mirrors. */
    private static void sky(ShaderInstance sh, ClientLevel level, Vec3 cam, float pt) {
        Vec3 sky = level.getSkyColor(cam, pt);
        sh.safeGetUniform("SkyColor").set((float) sky.x, (float) sky.y, (float) sky.z);
        float[] fog = RenderSystem.getShaderFogColor();
        sh.safeGetUniform("HorizonColor").set(fog[0], fog[1], fog[2]);
        // the sun (or at night the moon) mirrored in the water, gone under cloud and rain
        double a = level.getSunAngle(pt);
        float sx = (float) -Math.sin(a), sy = (float) Math.cos(a);
        float clear = 1 - level.getRainLevel(pt);
        if (sy > 0) {
            float k = 6F * clear * smooth(sy / 0.15F);
            sh.safeGetUniform("SunDir").set(sx, sy, 0F);
            sh.safeGetUniform("SunColor").set(k, k * 0.92F, k * 0.78F);
        } else {
            float k = 0.6F * clear * smooth(-sy / 0.15F) * level.getMoonBrightness();
            sh.safeGetUniform("SunDir").set(-sx, -sy, 0F);
            sh.safeGetUniform("SunColor").set(k * 0.8F, k * 0.88F, k);
        }
    }

    private static void rainAndRings(ShaderInstance sh, ClientLevel level, Vec3 cam, double ox, double oz) {
        // rain rings as hard as it rains here (regional weather), not in snow; the shader keeps them to open sky
        float rain = com.antaurora.apofirstlight.weather.AflWeather.rainLevel(level, cam.x, cam.z);
        if (rain > 0 && com.antaurora.apofirstlight.client.rain.RainRenderer.snowNear((int) Math.floor(cam.x), (int) Math.floor(cam.z), 8)) rain = 0;
        sh.safeGetUniform("RainRipple").set(rain);
        // the falls' rings: an array uniform the JSON cannot declare, set on the program itself (it stays through apply)
        int n = ClientFalls.impacts(IMPACTS, cam, ox, oz);
        if (impactsProgram != sh.getId()) {
            impactsProgram = sh.getId();
            impactsAt = GL20.glGetUniformLocation(impactsProgram, "Impacts");
            impactCountAt = GL20.glGetUniformLocation(impactsProgram, "ImpactCount");
        }
        if (impactCountAt >= 0) {
            GL20.glUseProgram(impactsProgram);
            GL20.glUniform1i(impactCountAt, n);
            if (n > 0 && impactsAt >= 0) GL20.glUniform4fv(impactsAt, IMPACTS);
        }
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * The frame copied from the main target, colour and depth (the depth texture in the main one's own format: a blit
     * needs them alike), as the windshield water does (client/rain/WindshieldRenderer).
     */
    private static final class SceneCopy {
        int fbo = -1, color = -1, depth = -1, width, height, depthFormat;
        /** The main depth texture whose format was read (a glGet waits for the GPU: read again only for a new one). */
        private int formatOf = -1, formatW, formatH, format;

        boolean copy(RenderTarget main) {
            int w = main.width, h = main.height;
            if (w <= 0 || h <= 0) return false;
            if (main.getDepthTextureId() != formatOf || w != formatW || h != formatH) {
                formatOf = main.getDepthTextureId();
                formatW = w;
                formatH = h;
                GlStateManager._bindTexture(main.getDepthTextureId());
                format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_INTERNAL_FORMAT);
                GlStateManager._bindTexture(0);
            }
            if (fbo < 0 || w != width || h != height || format != depthFormat) create(w, h, format);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            main.bindWrite(false);
            return true;
        }

        private void create(int w, int h, int format) {
            if (fbo >= 0) {
                GlStateManager._glDeleteFramebuffers(fbo);
                GlStateManager._deleteTexture(color);
                GlStateManager._deleteTexture(depth);
            }
            width = w;
            height = h;
            depthFormat = format;
            color = GlStateManager._genTexture();
            GlStateManager._bindTexture(color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            depth = GlStateManager._genTexture();
            GlStateManager._bindTexture(depth);
            boolean stencil = format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT,
                    stencil ? (format == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : GL30.GL_UNSIGNED_INT_24_8) : GL11.GL_FLOAT,
                    (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GlStateManager._bindTexture(0);
            fbo = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, stencil ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depth, 0);
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("water scene copy: framebuffer status " + status);
        }
    }
}
