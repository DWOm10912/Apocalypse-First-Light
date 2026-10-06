package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Burning things (2026-10-05, docs/gameplay/fuel_fire_v1.md "火焰效果 V2"): instead of vanilla's fire texture stacked in
 * rings up the body (with the realistic fire texture it read as stripes, user screenshot), a flame of their own: a fire
 * simulation of a burning person (textures/effect/body_flame, tools/build-body-flame-v1.mjs; V2.2, the pool fire's flames
 * placed round the body hid inside it and looked like ground fire on a body, user), as two quads turned to the camera
 * like vanilla's: one through the body (the body hides its middle; it shows round and above it) and a fainter one over
 * its front, each at its own place in the loop, sized to the bounding box. Sparks fly off and grey smoke rises off the top. In first person the screen's fire is two of
 * the same flames, softer and lower than vanilla's. Hooked in by EntityRenderDispatcherFireMixin (the flames) and
 * RenderBlockScreenEffectEvent (the screen).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EntityFlames {
    private static final ResourceLocation SHEET = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/effect/fuel_flame.png");
    private static final int FRAMES = 48, COLS = 8, ROWS = 6;
    /** The body flame frame against the bounding box: the burning figure filled about 0.76 x 1.76 m of a 1.2 x 3.0 m frame. */
    private static final double BODY_WIDE = 2.5, BODY_TALL = 1.7;
    private static final double FX_RANGE = 32.0;

    private EntityFlames() {
    }

    /** In place of EntityRenderDispatcher#renderFlame: the pose is at the entity's render origin. */
    public static void render(PoseStack pose, MultiBufferSource buffers, Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getFrameTime();
        Vec3 origin = entity.getPosition(partialTick), eye = minecraft.gameRenderer.getMainCamera().getPosition();
        double now = entity.level().getGameTime() + partialTick;
        double tx = eye.x - origin.x, tz = eye.z - origin.z, len = Math.sqrt(tx * tx + tz * tz);
        double fx = len > 1e-4 ? tx / len : 0, fz = len > 1e-4 ? tz / len : 1;   // towards the camera, level
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.BODY_FLAME);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        float w = entity.getBbWidth(), h = entity.getBbHeight();
        double width = w * BODY_WIDE, height = h * BODY_TALL, phase = now * 1.2 + (entity.getId() * 0x9E3779B97F4A7C15L >>> 40 & 47);
        // through the body (its middle hidden by the body, the rest round and above it), then over its front, fainter
        bodyQuad(out, matrix, normals, 0.0, 0.0, -fz, fx, width, height, phase, 235);
        bodyQuad(out, matrix, normals, fx * w * 0.55, fz * w * 0.55, -fz, fx, width * 0.9, height * 0.92, phase + 24, 150);
    }

    /** One body-flame quad, upright, across {@code (rx, rz)}, its foot at {@code (ox, 0, oz)} from the render origin. */
    private static void bodyQuad(VertexConsumer out, Matrix4f matrix, Matrix3f normals, double ox, double oz, double rx, double rz,
                                 double width, double height, double phase, int alpha) {
        int frame = (int) Math.floorMod((long) phase, FRAMES);
        float u0 = (frame % COLS) / (float) COLS, u1 = u0 + 1.0F / COLS, v0 = (frame / COLS) / (float) ROWS, v1 = v0 + 1.0F / ROWS;
        float hx = (float) (rx * width / 2), hz = (float) (rz * width / 2), x = (float) ox, z = (float) oz, y0 = -0.05F, y1 = (float) height - 0.05F;
        bodyVertex(out, matrix, normals, x - hx, y0, z - hz, u0, v1, alpha);
        bodyVertex(out, matrix, normals, x + hx, y0, z + hz, u1, v1, alpha);
        bodyVertex(out, matrix, normals, x + hx, y1, z + hz, u1, v0, alpha);
        bodyVertex(out, matrix, normals, x - hx, y1, z - hz, u0, v0, alpha);
    }

    private static void bodyVertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, float x, float y, float z, float u, float v, int alpha) {
        out.vertex(matrix, x, y, z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }

    /** First person, on fire: two flames low in the corners of the screen (vanilla's two fire sprites, our flames). */
    @SubscribeEvent
    public static void onScreenFire(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE) return;
        event.setCanceled(true);
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0 : minecraft.level.getGameTime() + minecraft.getFrameTime();
        PoseStack pose = event.getPoseStack();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.depthFunc(519);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, SHEET);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        for (int i = 0; i < 2; i++) {
            int frame = (int) Math.floorMod((long) (now * 1.2 + i * 24), FRAMES);
            float u0 = (frame % COLS) / (float) COLS, u1 = u0 + 1.0F / COLS, v0 = (frame / COLS) / (float) ROWS, v1 = v0 + 1.0F / ROWS;
            if (i == 1) {   // the right one mirrored
                float t = u0;
                u0 = u1;
                u1 = t;
            }
            pose.pushPose();
            pose.translate(-(i * 2 - 1) * 0.26F, -0.45F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees((i * 2 - 1) * 10.0F));
            Matrix4f matrix = pose.last().pose();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            builder.vertex(matrix, -0.4F, -0.55F, -0.5F).color(1.0F, 1.0F, 1.0F, 0.85F).uv(u0, v1).endVertex();
            builder.vertex(matrix, 0.4F, -0.55F, -0.5F).color(1.0F, 1.0F, 1.0F, 0.85F).uv(u1, v1).endVertex();
            builder.vertex(matrix, 0.4F, 0.55F, -0.5F).color(1.0F, 1.0F, 1.0F, 0.85F).uv(u1, v0).endVertex();
            builder.vertex(matrix, -0.4F, 0.55F, -0.5F).color(1.0F, 1.0F, 1.0F, 0.85F).uv(u0, v0).endVertex();
            BufferUploader.drawWithShader(builder.end());
            pose.popPose();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515);
    }

    /** Sparks and smoke off burning things near the camera (not off the camera's own body in first person). */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) return;
        Vec3 viewer = minecraft.gameRenderer.getMainCamera().getPosition();
        Entity self = minecraft.getCameraEntity();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        RandomSource r = level.random;
        for (Entity e : level.entitiesForRendering()) {
            if (!e.displayFireAnimation() || e.distanceToSqr(viewer) > FX_RANGE * FX_RANGE || e == self && firstPerson) continue;
            float w = e.getBbWidth(), h = e.getBbHeight();
            if (r.nextFloat() < 0.3F) {
                FireFx.smoke(level, e.getX() + (r.nextDouble() - 0.5) * w, e.getY() + h * 0.95, e.getZ() + (r.nextDouble() - 0.5) * w,
                        0.12F + w * 0.25F, FireFx.BODY_SMOKE, 0.5F, 60, 0.05);
            }
            if (r.nextFloat() < 0.4F) {
                FireFx.ember(level, e.getX() + (r.nextDouble() - 0.5) * w, e.getY() + r.nextDouble() * h, e.getZ() + (r.nextDouble() - 0.5) * w);
            }
        }
    }
}
