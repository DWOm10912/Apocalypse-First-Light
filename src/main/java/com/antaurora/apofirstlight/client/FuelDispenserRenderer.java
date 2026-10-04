package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.UUID;

/**
 * Fuel Dispenser V1: the live hose of every nozzle that is out (docs/models/fuel_dispenser_v1.md). The dispenser itself
 * is baked; while a nozzle is out its model (nozzle and parked hose) is hidden by the blockstate and this draws a round
 * hose from that nozzle's outlet under the header to the holder's hand. The hose is a cubic curve hanging from both
 * ends (it leaves the outlet straight down and the held nozzle's swivel along its axis): the outlet's retractor keeps {@link #SLACK} in it, up to the hose length, so it sags a little near the dispenser
 * and runs taut at full reach; it lies on the ground rather than going through it. It samples the reserved rubber texels
 * of the dispenser's own atlas ({@link #HOSE_UV}), so it carries the same LabPBR as the parked hoses.
 */
public final class FuelDispenserRenderer implements BlockEntityRenderer<FuelDispenserBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/fuel_dispenser.png");
    /** The live hose's rubber texels in the atlas, u0 v0 u1 v1 (tools/build-fuel-dispenser-v1.mjs paints them and checks this line). */
    private static final float[] HOSE_UV = {0.994141F, 0.994141F, 0.998047F, 0.998047F};
    private static final int SIDES = 8, SEGMENTS = 28;
    /** The parked hoses' radius (0.42 px). */
    private static final double RADIUS = 0.42 / 16;
    /** Hose the retractor leaves hanging beyond the straight distance (blocks), up to the hose length. */
    private static final double SLACK = 0.7;

    public FuelDispenserRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FuelDispenserBlockEntity dispenser, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = dispenser.getLevel();
        if (level == null || !dispenser.anyOut()) return;
        BlockPos origin = dispenser.getBlockPos();
        VertexConsumer out = null;
        for (Nozzle nozzle : Nozzle.values()) {
            UUID id = dispenser.holder(nozzle);
            Player holder = id == null ? null : level.getPlayerByUUID(id);
            if (holder == null || !(holder.getMainHandItem().getItem() instanceof FuelNozzleItem)) continue;
            if (out == null) out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            double floor = Math.min(origin.getY() + 3.0 / 16, holder.getPosition(partialTick).y) + RADIUS;
            hose(pose, out, level, origin, dispenser.outlet(nozzle), hand(holder, partialTick), floor);
        }
    }

    /** The held nozzle's swivel (where its hose goes in), in the item model's block units (tools/build-fuel-dispenser-v1.mjs heldSwivel). */
    private static final float SWIVEL_X = 0.5F, SWIVEL_Y = 0.5F, SWIVEL_Z = 0.704F;

    /** Where the hose meets the held nozzle (world), and the direction it leaves the swivel. */
    private record HoseEnd(Vec3 point, Vec3 out) {}

    /**
     * The held nozzle's swivel, through the same transforms vanilla uses to draw the held item, with the item model's
     * own display transforms: in first person ItemInHandRenderer's (hand sway, arm offset; drawn at a fixed 70 degree
     * field of view, so the point is moved to the same place on screen at the world's field of view); otherwise
     * ItemInHandLayer's on the player model's arm (body yaw, walking swing, the holding-an-item pose, idle sway, crouch).
     */
    private static HoseEnd hand(Player player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        int side = left ? -1 : 1;
        ItemStack stack = player.getMainHandItem();
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, 0);
        PoseStack pose = new PoseStack();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            if (player instanceof LocalPlayer local) {
                float xBob = Mth.lerp(partialTick, local.xBobO, local.xBob), yBob = Mth.lerp(partialTick, local.yBobO, local.yBob);
                pose.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTick) - xBob) * 0.1F));
                pose.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTick) - yBob) * 0.1F));
            }
            pose.translate(side * 0.56F, -0.52F, -0.72F);
            model.applyTransform(left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, left);
            pose.translate(-0.5F, -0.5F, -0.5F);
            Vector4f p = new Vector4f(SWIVEL_X, SWIVEL_Y, SWIVEL_Z, 1).mul(pose.last().pose());
            Vector4f d = new Vector4f(0, 0, 1, 0).mul(pose.last().pose());
            double k = Math.tan(Math.toRadians(mc.options.fov().get()) / 2) / Math.tan(Math.toRadians(70) / 2);
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 right = new Vec3(camera.getLeftVector()).scale(-1), up = new Vec3(camera.getUpVector()), ahead = new Vec3(camera.getLookVector());
            Vec3 point = camera.getPosition().add(right.scale(p.x() * k)).add(up.scale(p.y() * k)).add(ahead.scale(-p.z()));
            Vec3 out = right.scale(d.x()).add(up.scale(d.y())).add(ahead.scale(-d.z())).normalize();
            return new HoseEnd(point, out);
        }
        // LivingEntityRenderer: body yaw, the model's flip and scale (PlayerRenderer 0.9375), the model origin
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);
        // HumanoidModel#setupAnim for the main arm: walking swing, the ITEM pose, crouch, idle sway (AnimationUtils.bobModelPart)
        float age = player.tickCount + partialTick, swing = player.walkAnimation.position(partialTick);
        float speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
        float xRot = Mth.cos(swing * 0.6662F + (left ? 0.0F : (float) Math.PI)) * 2.0F * speed * 0.5F;
        xRot = xRot * 0.5F - (float) Math.PI / 10.0F;
        if (player.isCrouching()) xRot += 0.4F;
        xRot += side * Mth.sin(age * 0.067F) * 0.05F;
        float zRot = side * (Mth.cos(age * 0.09F) * 0.05F + 0.05F);
        pose.translate(-side * 5.0F / 16.0F, (player.isCrouching() ? 5.2F : 2.0F) / 16.0F, 0.0F);
        pose.mulPose(new Quaternionf().rotationZYX(zRot, 0.0F, xRot));
        // ItemInHandLayer#renderArmWithItem, then the item's own third-person transform
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(side / 16.0F, 0.125F, -0.625F);
        model.applyTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, pose, left);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Vector4f p = new Vector4f(SWIVEL_X, SWIVEL_Y, SWIVEL_Z, 1).mul(pose.last().pose());
        Vector4f d = new Vector4f(0, 0, 1, 0).mul(pose.last().pose());
        Vec3 base = player.getPosition(partialTick).add(0, player.isCrouching() ? -0.125 : 0, 0);
        return new HoseEnd(base.add(p.x(), p.y(), p.z()), new Vec3(d.x(), d.y(), d.z()).normalize());
    }

    private static void hose(PoseStack pose, VertexConsumer out, Level level, BlockPos origin, Vec3 a, HoseEnd end, double floor) {
        Vec3 b = end.point();
        double distance = a.distanceTo(b);
        if (distance < 1e-3) return;
        // a parabola of the hanging length sags sqrt(3 d s / 8) below the chord; cubic handles of 4/3 that sag give it
        double length = Math.min(FuelDispenserBlockEntity.HOSE_LENGTH, distance + SLACK), slack = Math.max(0, length - distance);
        double handle = Math.sqrt(3 * distance * slack / 8) / 0.75;
        // leaving the outlet straight down; arriving along the swivel's axis, then sagging
        Vec3 p1 = a.add(0, -handle, 0), p2 = b.add(end.out().scale(Math.max(0.15, handle * 0.5))).add(0, -handle * 0.4, 0);
        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS, u = 1 - t;
            Vec3 q = a.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(b.scale(t * t * t));
            points[i] = q.y < floor ? new Vec3(q.x, floor, q.z) : q;
        }
        // rotation-minimising frames (parallel transport) for the round section
        Vec3[][] ring = new Vec3[SEGMENTS + 1][SIDES];
        Vec3 previous = points[1].subtract(points[0]).normalize();
        Vec3 normal = Math.abs(previous.y) < 0.9 ? previous.cross(new Vec3(0, 1, 0)).normalize() : previous.cross(new Vec3(1, 0, 0)).normalize();
        for (int i = 0; i <= SEGMENTS; i++) {
            Vec3 tangent = points[Math.min(SEGMENTS, i + 1)].subtract(points[Math.max(0, i - 1)]).normalize();
            Vec3 axis = previous.cross(tangent);
            double sin = axis.length();
            if (sin > 1e-9) normal = rotate(normal, axis.scale(1 / sin), Math.atan2(sin, previous.dot(tangent)));
            normal = normal.subtract(tangent.scale(normal.dot(tangent))).normalize();
            Vec3 binormal = tangent.cross(normal);
            for (int k = 0; k < SIDES; k++) {
                double angle = 2 * Math.PI * (k + 0.5) / SIDES;
                ring[i][k] = normal.scale(Math.cos(angle)).add(binormal.scale(Math.sin(angle)));
            }
            previous = tangent;
        }
        int lightA = LevelRenderer.getLightColor(level, BlockPos.containing(a)), lightB = LevelRenderer.getLightColor(level, BlockPos.containing(b));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        float u = (HOSE_UV[0] + HOSE_UV[2]) / 2, v = (HOSE_UV[1] + HOSE_UV[3]) / 2;
        for (int i = 0; i < SEGMENTS; i++) {
            int l0 = lerpLight(lightA, lightB, (double) i / SEGMENTS), l1 = lerpLight(lightA, lightB, (double) (i + 1) / SEGMENTS);
            for (int k = 0; k < SIDES; k++) {
                int k2 = (k + 1) % SIDES;
                vertex(out, matrix, normals, origin, points[i], ring[i][k], u, v, l0);
                vertex(out, matrix, normals, origin, points[i], ring[i][k2], u, v, l0);
                vertex(out, matrix, normals, origin, points[i + 1], ring[i + 1][k2], u, v, l1);
                vertex(out, matrix, normals, origin, points[i + 1], ring[i + 1][k], u, v, l1);
            }
        }
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1 - cos)));
    }

    private static int lerpLight(int a, int b, double t) {
        int block = (int) Math.round(Mth.lerp(t, a & 0xFFFF, b & 0xFFFF)), sky = (int) Math.round(Mth.lerp(t, a >>> 16, b >>> 16));
        return block | sky << 16;
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, BlockPos origin, Vec3 centre, Vec3 direction,
                               float u, float v, int light) {
        out.vertex(matrix, (float) (centre.x - origin.getX() + direction.x * RADIUS), (float) (centre.y - origin.getY() + direction.y * RADIUS),
                        (float) (centre.z - origin.getZ() + direction.z * RADIUS))
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normals, (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(FuelDispenserBlockEntity dispenser) {
        return dispenser.anyOut();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
