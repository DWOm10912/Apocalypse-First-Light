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
        if (level == null) return;
        BlockPos origin = dispenser.getBlockPos();
        for (Nozzle nozzle : Nozzle.values()) {
            UUID id = dispenser.holder(nozzle);
            Player holder = id == null ? null : level.getPlayerByUUID(id);
            HeldFrame frame = null;
            if (holder != null && holder.getMainHandItem().getItem() instanceof FuelNozzleItem) {
                double floor = Math.min(origin.getY() + 3.0 / 16, holder.getPosition(partialTick).y) + RADIUS;
                frame = held(holder, partialTick);
                hose(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), level, origin, dispenser.outlet(nozzle),
                        new HoseEnd(frame.point(SWIVEL_X, SWIVEL_Y, SWIVEL_Z), frame.direction(0, 0, 1)), floor);
            }
            FuelNozzleJets.Jet jet = FuelNozzleJets.get(origin, nozzle);
            if (jet != null) jet(pose, buffers, level, origin, jet, frame != null && jet.flowing ? frame.point(SPOUT_X, SPOUT_Y, SPOUT_Z) : null, partialTick);
        }
    }

    /** The held nozzle's spout tip in the world (FuelNozzleJets emits the jet from it). */
    static Vec3 spout(Player player, float partialTick) {
        return held(player, partialTick).point(SPOUT_X, SPOUT_Y, SPOUT_Z);
    }

    /** The held nozzle's swivel (where its hose goes in), in the item model's block units (tools/build-fuel-dispenser-v1.mjs heldSwivel). */
    private static final float SWIVEL_X = 0.5F, SWIVEL_Y = 0.5F, SWIVEL_Z = 0.704F;

    /** The held nozzle's spout tip, item model block units (the generator's straight spout's end (0, 1.0, 6.25) px in the nozzle frame). */
    private static final float SPOUT_X = 0.5F, SPOUT_Y = 0.5625F, SPOUT_Z = 0.288F;

    /** Where the hose meets the held nozzle (world), and the direction it leaves the swivel. */
    private record HoseEnd(Vec3 point, Vec3 out) {}

    /** The held nozzle's item model space in the world: points and directions in the model's block units. */
    private interface HeldFrame {
        Vec3 point(float x, float y, float z);

        Vec3 direction(float x, float y, float z);
    }

    /**
     * The held nozzle's swivel, through the same transforms vanilla uses to draw the held item, with the item model's
     * own display transforms: in first person ItemInHandRenderer's (hand sway, arm offset; drawn at a fixed 70 degree
     * field of view, so the point is moved to the same place on screen at the world's field of view); otherwise
     * ItemInHandLayer's on the player model's arm (body yaw, walking swing, the holding-an-item pose, idle sway, crouch).
     */
    private static java.lang.reflect.Field mainHandHeight, oMainHandHeight;

    /** ItemInHandRenderer's main hand equip progress (0 raised .. 1 lowered), as it draws the held nozzle; 0 if unreadable. */
    private static float equipProgress(float partialTick) {
        try {
            if (mainHandHeight == null) {
                mainHandHeight = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.client.renderer.ItemInHandRenderer.class, "f_109302_");
                oMainHandHeight = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.client.renderer.ItemInHandRenderer.class, "f_109303_");
            }
            Object renderer = Minecraft.getInstance().gameRenderer.itemInHandRenderer;
            return 1.0F - Mth.lerp(partialTick, oMainHandHeight.getFloat(renderer), mainHandHeight.getFloat(renderer));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0.0F;
        }
    }

    private static HeldFrame held(Player player, float partialTick) {
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
            pose.translate(side * 0.56F, -0.52F - 0.6F * equipProgress(partialTick), -0.72F);
            model.applyTransform(left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, left);
            pose.translate(-0.5F, -0.5F, -0.5F);
            Matrix4f m = new Matrix4f(pose.last().pose());
            // the hand is drawn at its own field of view, the world at the configured one times the dynamic factor (flying,
            // sprinting, speed): both as last computed (ViewFov). 2026-10-05: with the settings' value alone the stream left
            // the nozzle off its tip whenever the player flew or sprinted (user)
            double k = Math.tan(Math.toRadians(ViewFov.world()) / 2) / Math.tan(Math.toRadians(ViewFov.hand()) / 2);
            // the eye and the view at this partial tick (not the camera's last frame: the jet emits at tick time)
            Vec3 eye = player.getEyePosition(partialTick);
            Quaternionf view = new Quaternionf().rotationYXZ(-player.getViewYRot(partialTick) * Mth.DEG_TO_RAD, player.getViewXRot(partialTick) * Mth.DEG_TO_RAD, 0.0F);
            Vec3 right = new Vec3(new org.joml.Vector3f(-1, 0, 0).rotate(view)), up = new Vec3(new org.joml.Vector3f(0, 1, 0).rotate(view)),
                    ahead = new Vec3(new org.joml.Vector3f(0, 0, 1).rotate(view));
            return new HeldFrame() {
                @Override
                public Vec3 point(float x, float y, float z) {
                    Vector4f p = new Vector4f(x, y, z, 1).mul(m);
                    return eye.add(right.scale(p.x() * k)).add(up.scale(p.y() * k)).add(ahead.scale(-p.z()));
                }

                @Override
                public Vec3 direction(float x, float y, float z) {
                    Vector4f d = new Vector4f(x, y, z, 0).mul(m);
                    return right.scale(d.x()).add(up.scale(d.y())).add(ahead.scale(-d.z())).normalize();
                }
            };
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
        Matrix4f m = new Matrix4f(pose.last().pose());
        Vec3 base = player.getPosition(partialTick).add(0, player.isCrouching() ? -0.125 : 0, 0);
        return new HeldFrame() {
            @Override
            public Vec3 point(float x, float y, float z) {
                Vector4f p = new Vector4f(x, y, z, 1).mul(m);
                return base.add(p.x(), p.y(), p.z());
            }

            @Override
            public Vec3 direction(float x, float y, float z) {
                Vector4f d = new Vector4f(x, y, z, 0).mul(m);
                return new Vec3(d.x(), d.y(), d.z()).normalize();
            }
        };
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
        Vec3[][] ring = LiquidJetRenderer.rings(points, SIDES);
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

    /** The fuel stream's radius (blocks), sides and opacity (of 255). */
    private static final double STREAM_RADIUS = 0.009;
    /** Seconds of flight after which the stream breaks up into elongated drops (LiquidJetRenderer). */
    private static final double STREAM_BREAKUP = 0.45;
    private static final int STREAM_SIDES = 8, STREAM_ALPHA = 165;

    /**
     * A nozzle's jet (2026-10-05, FuelNozzleJets): the parcel chain as a thin translucent tube in the fuel's still texture
     * and tint (LiquidJetRenderer), attached to the held spout while it runs. The stains are drawn by ClientFuelStains.
     */
    private static void jet(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin, FuelNozzleJets.Jet jet,
                            @org.jetbrains.annotations.Nullable Vec3 spout, float partialTick) {
        net.minecraftforge.fluids.FluidStack stack = new net.minecraftforge.fluids.FluidStack(jet.fluid(), 1000);
        net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions fluidClient = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(jet.fluid());
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(fluidClient.getStillTexture(stack));
        int rgb = fluidClient.getTintColor(stack) & 0xFFFFFF;
        LiquidJetRenderer.render(pose, buffers, level, origin, jet.jet, spout, partialTick, sprite, rgb, STREAM_ALPHA, STREAM_RADIUS,
                FuelDispenserBlockEntity.NOZZLE_SPEED, STREAM_SIDES, STREAM_BREAKUP);
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
        return dispenser.anyOut() || FuelNozzleJets.any(dispenser.getBlockPos());
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
