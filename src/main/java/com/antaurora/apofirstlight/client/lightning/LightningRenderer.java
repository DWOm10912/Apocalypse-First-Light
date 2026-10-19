package com.antaurora.apofirstlight.client.lightning;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md): draws a {@link LightningFlash} in place of vanilla's bolt. Each strand of
 * the channel is a ribbon turned to the camera, three deep: a white core, a blue-violet glow round it and a wide faint
 * one (the light scattered by the rain), each at least a few pixels wide however far; the cloud the channel leaves glows
 * round its top. Added onto the scene (lightning blending), no depth writes. With a shader pack the same quads go through
 * the pack's lightning program (Oculus' LightningRenderStateShard round AFL's type; its IRIS_LIGHTNING as a fallback).
 */
public final class LightningRenderer extends EntityRenderer<LightningBolt> {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Each layer's width at full strand width (blocks) and the least it is on screen (px). */
    private static final float CORE = 0.32F, CORE_PX = 1.6F, GLOW = 2.6F, GLOW_PX = 9, HAZE = 16, HAZE_PX = 40;
    /**
     * Each layer's colour and how much of the strand's brightness it carries. A contract with Aureveil (alpha.60,
     * 2026-10-18): it draws the core alone, told apart by colour (rgb all above 0.95, brightness in alpha) and drops the
     * bluish bands; keep the core (1, 1, 1) and the bands blue, and the brightness in alpha only.
     */
    private static final float[] CORE_RGB = {1.0F, 1.0F, 1.0F}, GLOW_RGB = {0.62F, 0.68F, 1.0F}, HAZE_RGB = {0.55F, 0.6F, 1.0F};
    private static final float GLOW_A = 0.42F, HAZE_A = 0.07F;
    /** The cloud lit round the channel's top: radius (share of the height), brightness. */
    private static final float CLOUD_R = 0.38F, CLOUD_A = 0.28F;
    private static final float[] CLOUD_RGB = {0.72F, 0.74F, 0.92F};
    /** The stepped leader: this bright, core only, coming down from the cloud. */
    static final float LEADER_K = 0.08F;
    /** Diagnostics: bolts drawn since the counter was last cleared (dev/LightningProbe). */
    public static int drawn;

    public LightningRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityType.LIGHTNING_BOLT, LightningRenderer::new);
        }
    }

    /** Vanilla draws bolts only within 64 x the view scale; this one as far as the client knows of it. */
    @Override
    public boolean shouldRender(LightningBolt bolt, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(LightningBolt bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(LightningBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        LightningFlash f = AflLightning.flash(bolt);
        double t = f.time(partialTick);
        if (t > f.end()) return;
        drawn++;
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        float cx = (float) (c.x - bolt.getX()), cy = (float) (c.y - bolt.getY()), cz = (float) (c.z - bolt.getZ());
        // px per block at 1 block from the eye
        float pxPerBlock = (float) (mc.getWindow().getHeight() / 2.0 / Math.tan(Math.toRadians(mc.options.fov().get()) / 2));
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(type());
        if (t < 0) {
            float reveal = (float) (f.height * (-t / LightningFlash.LEADER));
            for (LightningChannel.Strand s : f.channel.strands) strand(out, m, s, LEADER_K, reveal, cx, cy, cz, pxPerBlock, false);
            return;
        }
        f.at(t);
        for (LightningChannel.Strand s : f.channel.strands) {
            float k = (float) (s.main ? f.main : f.branch);
            if (k > 0.003F) strand(out, m, s, k, -1, cx, cy, cz, pxPerBlock, true);
        }
        if (f.main > 0.003) cloud(out, m, f, (float) f.main, cx, cy, cz);
    }

    private static final float[] SX = new float[4096], SY = new float[4096], SZ = new float[4096], DIST = new float[4096];

    /** One strand's ribbons (points above reveal only); glow layers too unless just the core. */
    private static void strand(VertexConsumer out, Matrix4f m, LightningChannel.Strand s, float k, float reveal, float cx, float cy,
                               float cz, float pxPerBlock, boolean glow) {
        int n = Math.min(s.count(), SX.length);
        if (n < 2) return;
        // each point's side (square to the strand and to the view) and distance
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float tx = s.x(b) - s.x(a), ty = s.y(b) - s.y(a), tz = s.z(b) - s.z(a);
            float vx = s.x(i) - cx, vy = s.y(i) - cy, vz = s.z(i) - cz;
            float qx = ty * vz - tz * vy, qy = tz * vx - tx * vz, qz = tx * vy - ty * vx;
            float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (l < 1e-6F) {
                qx = 1;
                qy = 0;
                qz = 0;
                l = 1;
            }
            SX[i] = qx / l;
            SY[i] = qy / l;
            SZ[i] = qz / l;
            DIST[i] = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        }
        for (int i = 0; i + 1 < n; i++) {
            if (s.y(i) < reveal || s.y(i + 1) < reveal) continue;
            float k0 = k * s.bright(i), k1 = k * s.bright(i + 1);
            float c0 = width(s.width(i) * CORE, CORE_PX, DIST[i], pxPerBlock), c1 = width(s.width(i + 1) * CORE, CORE_PX, DIST[i + 1], pxPerBlock);
            band(out, m, s, i, c0, c1, CORE_RGB, Math.min(1, k0), Math.min(1, k1), true);
            if (!glow) continue;
            float g0 = width(s.width(i) * GLOW, GLOW_PX, DIST[i], pxPerBlock), g1 = width(s.width(i + 1) * GLOW, GLOW_PX, DIST[i + 1], pxPerBlock);
            band(out, m, s, i, g0, g1, GLOW_RGB, GLOW_A * k0, GLOW_A * k1, false);
            float h0 = width(s.width(i) * HAZE, HAZE_PX, DIST[i], pxPerBlock), h1 = width(s.width(i + 1) * HAZE, HAZE_PX, DIST[i + 1], pxPerBlock);
            band(out, m, s, i, h0, h1, HAZE_RGB, HAZE_A * k0, HAZE_A * k1, false);
        }
    }

    private static float width(float blocks, float px, float dist, float pxPerBlock) {
        return Math.max(blocks, px * dist / pxPerBlock);
    }

    /**
     * The quad(s) from point i to i + 1, w0 and w1 wide: solid (one quad, the colour all across) or fading (two halves,
     * the colour along the middle, nothing at the edges).
     */
    private static void band(VertexConsumer out, Matrix4f m, LightningChannel.Strand s, int i, float w0, float w1, float[] rgb,
                             float a0, float a1, boolean solid) {
        int j = i + 1;
        float x0 = s.x(i), y0 = s.y(i), z0 = s.z(i), x1 = s.x(j), y1 = s.y(j), z1 = s.z(j);
        float h0 = w0 / 2, h1 = w1 / 2;
        float r = rgb[0], g = rgb[1], b = rgb[2];
        if (solid) {
            out.vertex(m, x0 - SX[i] * h0, y0 - SY[i] * h0, z0 - SZ[i] * h0).color(r, g, b, a0).endVertex();
            out.vertex(m, x1 - SX[j] * h1, y1 - SY[j] * h1, z1 - SZ[j] * h1).color(r, g, b, a1).endVertex();
            out.vertex(m, x1 + SX[j] * h1, y1 + SY[j] * h1, z1 + SZ[j] * h1).color(r, g, b, a1).endVertex();
            out.vertex(m, x0 + SX[i] * h0, y0 + SY[i] * h0, z0 + SZ[i] * h0).color(r, g, b, a0).endVertex();
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            out.vertex(m, x0 + side * SX[i] * h0, y0 + side * SY[i] * h0, z0 + side * SZ[i] * h0).color(r, g, b, 0).endVertex();
            out.vertex(m, x1 + side * SX[j] * h1, y1 + side * SY[j] * h1, z1 + side * SZ[j] * h1).color(r, g, b, 0).endVertex();
            out.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
            out.vertex(m, x0, y0, z0).color(r, g, b, a0).endVertex();
        }
    }

    /** The cloud lit round the channel's top: a disc turned to the camera, bright in the middle, nothing at its rim. */
    private static void cloud(VertexConsumer out, Matrix4f m, LightningFlash f, float k, float cx, float cy, float cz) {
        float x = (float) f.channel.topX, y = (float) f.channel.topY, z = (float) f.channel.topZ, rad = (float) (CLOUD_R * f.height);
        float vx = x - cx, vy = y - cy, vz = z - cz, vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1e-3F) return;
        vx /= vl;
        vy /= vl;
        vz /= vl;
        // right = view x up, up' = right x view
        float rx = -vz, ry = 0, rz = vx, rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-3F) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        float ux = ry * vz - rz * vy, uy = rz * vx - rx * vz, uz = rx * vy - ry * vx;
        float a = CLOUD_A * k, r = CLOUD_RGB[0], g = CLOUD_RGB[1], b = CLOUD_RGB[2];
        int sides = 20;
        for (int i = 0; i < sides; i++) {
            double t0 = 2 * Math.PI * i / sides, t1 = 2 * Math.PI * (i + 1) / sides;
            // the disc is wider than tall: the lit cloud base spreads sideways
            float p0 = (float) Math.cos(t0) * rad * 1.5F, q0 = (float) Math.sin(t0) * rad * 0.8F;
            float p1 = (float) Math.cos(t1) * rad * 1.5F, q1 = (float) Math.sin(t1) * rad * 0.8F;
            out.vertex(m, x, y, z).color(r, g, b, a).endVertex();
            out.vertex(m, x + rx * p0 + ux * q0, y + ry * p0 + uy * q0, z + rz * p0 + uz * q0).color(r, g, b, 0).endVertex();
            out.vertex(m, x + rx * p1 + ux * q1, y + ry * p1 + uy * q1, z + rz * p1 + uz * q1).color(r, g, b, 0).endVertex();
            out.vertex(m, x, y, z).color(r, g, b, a).endVertex();
        }
    }

    // ---- render types ----
    private static RenderType packType;
    private static boolean packTried;

    /** AFL's type without a shader pack; with one, the same inside the pack's lightning program. */
    private static RenderType type() {
        if (!AflShaderCompat.shaderPackInUse()) return Types.LIGHTNING;
        if (!packTried) {
            packTried = true;
            packType = irisWrapped();
        }
        return packType != null ? packType : Types.LIGHTNING;
    }

    private static RenderType irisWrapped() {
        try {
            Object shard = Class.forName("net.irisshaders.iris.layer.LightningRenderStateShard").getField("INSTANCE").get(null);
            return (RenderType) Class.forName("net.irisshaders.iris.layer.InnerWrappedRenderType")
                    .getConstructor(String.class, RenderType.class, RenderStateShard.class)
                    .newInstance("apocalypse_firstlight_iris_lightning", Types.LIGHTNING, shard);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            LOGGER.warn("Lightning: Oculus' lightning state not found ({}), trying IRIS_LIGHTNING", e.toString());
        }
        try {
            return (RenderType) Class.forName("net.irisshaders.iris.pathways.LightningHandler").getField("IRIS_LIGHTNING").get(null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            LOGGER.warn("Lightning: no Oculus lightning type ({}), drawing with AFL's own", e.toString());
            return null;
        }
    }

    /** A holder of the protected render state shards; never instantiated. */
    private static final class Types extends RenderType {
        /** Vanilla's lightning (its shader, added on by alpha, the weather target) without depth writes or culling. */
        static final RenderType LIGHTNING = RenderType.create("apocalypse_firstlight_lightning", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1 << 14, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setOutputState(WEATHER_TARGET)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                      Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}
