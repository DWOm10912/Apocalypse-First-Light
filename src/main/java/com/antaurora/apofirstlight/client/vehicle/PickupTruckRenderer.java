package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The T15 pickup body V1 (PickupTruckEntity) from its AFL meshes (tools/build-pickup-t15.mjs; rig data in
 * src/main/blockbench/vehicles/pickup_t15.rig.json). Seven Pure Meshes, each with its geo for the bone pivots:
 * <ul><li>pickup_t15 (cab shell, doors, bed, tailgate) and pickup_t15_front (front clip, hood): always;</li>
 * <li>pickup_t15_wheel: one wheel at its hub, drawn at the four corners (the left ones turned half a turn about Y) and
 * as the spare under the bed (turned so its outer face points down);</li>
 * <li>pickup_t15_cab + pickup_t15_seat (interior) and pickup_t15_chassis: within {@link #NEAR} blocks;</li>
 * <li>pickup_t15_engine: within {@link #NEAR} while the hood is open.</li></ul>
 * Atlases: pickup_t15 (outside + wheel), pickup_t15_cab (interior), pickup_t15_under (engine + chassis). Bones turn by the
 * entity's angles: hood and tailgate about +X, doors about Y (outward), the steering wheel stays at rest.
 * <p>Passes, in this order (a plain buffer source flushes one render type before the next, so the order holds):
 * <ol><li>cutout, each mesh's atlas: painted parts (*_paint / *_paint2) multiplied by the truck's main / second colour,
 * faded by wear; everything else untinted;</li>
 * <li>cutout, the wear overlay for round(wear x 10) (pickup_t15_wear_1..10, laid out like the outside atlas) over the
 * outside's painted parts: a texel is opaque exactly where that wear tenth has worn the paint through (the vanilla
 * cutout tests texture alpha only). Same vertices and pose as pass 1, so the depths are equal and the later draw wins;</li>
 * <li>translucent: the glass and the cluster lens (skipped in a shader pack's shadow pass).</li></ol>
 * The model's front is -Z: a world yaw psi is a model turn of 180 - psi about +Y.
 */
public final class PickupTruckRenderer extends EntityRenderer<PickupTruckEntity> {
    /** Interior, chassis and engine bay are drawn within this distance (blocks). */
    private static final double NEAR = 40.0;
    private static final ResourceLocation OUTSIDE = texture("pickup_t15"), INTERIOR = texture("pickup_t15_cab"), UNDER = texture("pickup_t15_under");
    private static final ResourceLocation[] WEAR = new ResourceLocation[11];
    static { for (int i = 1; i <= 10; i++) WEAR[i] = texture("pickup_t15_wear_" + i); }
    private static final Piece EXT = new Piece("pickup_t15", OUTSIDE, true), FRONT = new Piece("pickup_t15_front", OUTSIDE, true),
            WHEEL = new Piece("pickup_t15_wheel", OUTSIDE, false), CAB = new Piece("pickup_t15_cab", INTERIOR, false),
            SEAT = new Piece("pickup_t15_seat", INTERIOR, false), ENGINE = new Piece("pickup_t15_engine", UNDER, false),
            CHASSIS = new Piece("pickup_t15_chassis", UNDER, false);
    /** Wheel hubs (blocks, model frame) and whether the wheel is turned (the left side); the spare's centre. */
    private static final float[][] HUBS = {{-0.712F, 0.36F, -1.60F, 1}, {0.712F, 0.36F, -1.60F, 0}, {-0.712F, 0.36F, 1.25F, 1}, {0.712F, 0.36F, 1.25F, 0}};
    private static final float[] SPARE = {0.0F, 0.50F, 1.86F};

    private record Piece(ResourceLocation geometry, ResourceLocation texture, boolean worn) {
        Piece(String id, ResourceLocation texture, boolean worn) {
            this(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + id + ".geo.json"), texture, worn);
        }
    }

    /** The pose of one frame: bone turns and paint. */
    private record State(float hood, float tailgate, float doorL, float doorR, float[] main, float[] second, int light) {}

    public PickupTruckRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.6F;
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/vehicle/" + name + ".png");
    }

    @Override
    public void render(PickupTruckEntity truck, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float wear = truck.wear();
        State s = new State(truck.hoodAngle(partialTick), truck.tailgateAngle(partialTick), truck.doorAngle(false, partialTick),
                truck.doorAngle(true, partialTick), tint(truck.paint(), wear), tint(truck.secondPaint(), wear), light);
        boolean near = entityRenderDispatcher.distanceToSqr(truck) < NEAR * NEAR;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        // pass 1: cutout per atlas
        draw(EXT, pose, buffers, s, MeshLayer.CUTOUT, null);
        draw(FRONT, pose, buffers, s, MeshLayer.CUTOUT, null);
        drawWheels(pose, buffers, s, near);
        if (near) {
            draw(CAB, pose, buffers, s, MeshLayer.CUTOUT, null);
            draw(SEAT, pose, buffers, s, MeshLayer.CUTOUT, null);
            draw(CHASSIS, pose, buffers, s, MeshLayer.CUTOUT, null);
            if (s.hood() > 0.5F) draw(ENGINE, pose, buffers, s, MeshLayer.CUTOUT, null);
        }
        // pass 2: the wear overlay over the outside's paint
        int level = Mth.clamp(Math.round(wear * 10.0F), 0, 10);
        if (level > 0) {
            draw(EXT, pose, buffers, s, MeshLayer.CUTOUT, WEAR[level]);
            draw(FRONT, pose, buffers, s, MeshLayer.CUTOUT, WEAR[level]);
        }
        // pass 3: glass
        if (!AflShaderCompat.activeShadowPass()) {
            draw(EXT, pose, buffers, s, MeshLayer.TRANSLUCENT, null);
            draw(FRONT, pose, buffers, s, MeshLayer.TRANSLUCENT, null);
            if (near) draw(CAB, pose, buffers, s, MeshLayer.TRANSLUCENT, null);
        }
        pose.popPose();
        super.render(truck, entityYaw, partialTick, pose, buffers, light);
    }

    /** One mesh, every root bone at its pivot. overlay != null: the wear pass (painted parts only, that texture, no tint). */
    private static void draw(Piece piece, PoseStack pose, MultiBufferSource buffers, State s, MeshLayer layer, ResourceLocation overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(piece.geometry());
        if (mesh == null || layer == MeshLayer.TRANSLUCENT && !mesh.hasTranslucent()) return;
        VertexConsumer out = buffers.getBuffer(layer == MeshLayer.TRANSLUCENT ? RenderType.entityTranslucent(piece.texture())
                : RenderType.entityCutoutNoCull(overlay != null ? overlay : piece.texture()));
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            List<MeshPart> parts = mesh.parts(bone.name(), layer);
            if (parts.isEmpty()) continue;
            pose.pushPose();
            pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
            switch (bone.name()) {
                case "hood" -> pose.mulPose(Axis.XP.rotationDegrees(s.hood()));
                case "tailgate" -> pose.mulPose(Axis.XP.rotationDegrees(s.tailgate()));
                case "door_l" -> pose.mulPose(Axis.YP.rotationDegrees(-s.doorL()));
                case "door_r" -> pose.mulPose(Axis.YP.rotationDegrees(s.doorR()));
                default -> { }
            }
            for (MeshPart part : parts) {
                boolean painted = isPainted(part);
                if (overlay != null && !painted) continue;
                float[] c = overlay != null || !painted ? null : isSecond(part) ? s.second() : s.main();
                MeshRenderer.renderParts(List.of(part), pose, out, s.light(), OverlayTexture.NO_OVERLAY,
                        c == null ? 1 : c[0], c == null ? 1 : c[1], c == null ? 1 : c[2], 1, null);
            }
            pose.popPose();
        }
    }

    /** The wheel mesh at the four hubs (the left ones turned about Y) and, near, as the spare (outer face down). */
    private static void drawWheels(PoseStack pose, MultiBufferSource buffers, State s, boolean near) {
        for (float[] hub : HUBS) {
            pose.pushPose();
            pose.translate(hub[0], hub[1], hub[2]);
            if (hub[3] != 0) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            draw(WHEEL, pose, buffers, s, MeshLayer.CUTOUT, null);
            pose.popPose();
        }
        if (!near) return;
        pose.pushPose();
        pose.translate(SPARE[0], SPARE[1], SPARE[2]);
        pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        draw(WHEEL, pose, buffers, s, MeshLayer.CUTOUT, null);
        pose.popPose();
    }

    private static boolean isPainted(MeshPart part) {
        return part.name().endsWith("_paint") || part.name().endsWith("_paint2");
    }

    private static boolean isSecond(MeshPart part) {
        return part.name().endsWith("_paint2");
    }

    /** The paint colour as vertex colour, faded by wear: less saturated and a little chalkier (design doc section 2). */
    static float[] tint(int rgb, float wear) {
        float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b, fade = 0.35F * wear, lift = 1.0F + 0.08F * wear;
        return new float[]{Mth.clamp(Mth.lerp(fade, r, lum) * lift, 0, 1), Mth.clamp(Mth.lerp(fade, g, lum) * lift, 0, 1),
                Mth.clamp(Mth.lerp(fade, b, lum) * lift, 0, 1)};
    }

    @Override
    public ResourceLocation getTextureLocation(PickupTruckEntity truck) {
        return OUTSIDE;
    }
}
