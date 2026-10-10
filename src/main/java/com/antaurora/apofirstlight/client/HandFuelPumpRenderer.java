package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FuelCanBlock;
import com.antaurora.apofirstlight.block.HandFuelPumpBlock;
import com.antaurora.apofirstlight.blockentity.HandFuelPumpBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The hand fuel pump's moving parts (docs/models/fuel_containers_v1.md; the body is baked per mount): the crank, turning
 * while it is cranked (its own model, block/hand_fuel_pump/crank, about its pivot), and the hose from the discharge's barb
 * into whatever stands under it: into a container's opening (a can's spout, a drum's 2" bung), onto the top of any other
 * fluid block, else lying on the ground in front. A cubic curve leaving the barb straight down and entering the opening
 * from above, with a little slack; round, in the rubber swatch of the containers' atlas (tools/build-fuel-containers-v1.mjs
 * paints it and checks {@link #HOSE_U} / {@link #HOSE_V}), so it carries the same LabPBR.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HandFuelPumpRenderer implements BlockEntityRenderer<HandFuelPumpBlockEntity> {
    private static final ResourceLocation CRANK = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/hand_fuel_pump/crank");
    private static final ResourceLocation ATLAS_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/fuel_containers");
    /** The hose's rubber in the containers' texture, 0..1 (checked by the generator). */
    static final float HOSE_U = 0.627441F, HOSE_V = 0.656738F;
    /** Per mount (HandFuelPumpBlock.Mount order): the crank's pivot, px about the cell's centre, facing north (generator). */
    private static final double[][] PIVOT = {{1.25, -5.2, 0.0}, {1.25, -4.07, -3.8}, {1.25, -8.02, -2.6}};
    private static final double RADIUS = 0.37 / 16;
    /** How far the hose reaches into the barb and the opening (blocks), so the joints close. */
    private static final double INSIDE = 0.25 / 16;
    /** Hose beyond the straight distance (blocks): it sags a little, as the dispenser's. */
    private static final double SLACK = 0.25;
    private static final int SIDES = 8, SEGMENTS = 14;

    public HandFuelPumpRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(CRANK);
    }

    @Override
    public void render(HandFuelPumpBlockEntity pump, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = pump.getLevel();
        if (level == null) return;
        BlockState state = pump.getBlockState();
        if (!(state.getBlock() instanceof HandFuelPumpBlock)) return;
        Direction facing = state.getValue(HandFuelPumpBlock.FACING);
        int mount = state.getValue(HandFuelPumpBlock.MOUNT).ordinal();
        Minecraft mc = Minecraft.getInstance();
        // the crank about its pivot
        double[] pivot = PIVOT[mount];
        double[] t = FuelCanBlock.turnXZ(pivot[0], pivot[2], facing);
        pose.pushPose();
        pose.translate(0.5 + t[0] / 16, 0.5 + pivot[1] / 16, 0.5 + t[1] / 16);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-pump.angle(partialTick)));
        pose.translate(-0.5, -0.5, -0.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),
                null, mc.getModelManager().getModel(CRANK), 1, 1, 1, light, overlay);
        pose.popPose();
        // the hose: into the nearest container next to the pump, else down onto the ground in front
        BlockPos origin = pump.getBlockPos();
        Vec3 a = HandFuelPumpBlock.barb(origin, state).add(0, INSIDE, 0);   // starts a little up inside the barb
        BlockPos to = HandFuelPumpBlock.target(level, origin, state);
        Vec3 b;
        boolean into = to != null;
        if (into) b = FuelCanBlock.opening(to, level.getBlockState(to)).add(0, -INSIDE, 0);   // ends a little down inside the opening
        else {
            Vec3 front = Vec3.atLowerCornerOf(facing.getNormal());
            b = new Vec3(a.x + front.x * 0.55, a.y, a.z + front.z * 0.55);
            b = new Vec3(b.x, floor(level, b, 3) + RADIUS, b.z);
        }
        TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ATLAS_TEXTURE);
        float[] uv = LiquidJetRenderer.texelCentre(sprite, HOSE_U, HOSE_V);
        hose(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), level, origin, a, b, into, uv[0], uv[1]);
    }

    /** The top of what stands under {@code p} (searching {@code depth} blocks down), or p's own height when nothing is there. */
    private static double floor(Level level, Vec3 p, int depth) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int k = 0; k <= depth; k++) {
            at.set(p.x, p.y - k, p.z);
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (!shape.isEmpty()) {
                double top = at.getY() + shape.max(Direction.Axis.Y);
                if (top <= p.y + 1e-3 || k == 0) return Math.min(top, p.y + 1.0);
            }
        }
        return p.y - depth;
    }

    /**
     * The dispenser hose's curve (client/FuelDispenserRenderer#hose; V1.2, 2026-10-05: the first one turned sideways at
     * once and stopped short over the opening, so its ends sat askew on the barb and beside the bung, user): a cubic leaving
     * the barb straight down along its axis and entering the opening straight down from above (on the ground: lying along
     * it), its handles from the sag of the hanging length ({@link #SLACK} over the straight distance). The points between
     * the ends are kept a hose's radius above what they pass over (not through the ground, a cover or a container's wall).
     */
    private static void hose(PoseStack pose, VertexConsumer out, Level level, BlockPos origin, Vec3 a, Vec3 b, boolean into, float u, float v) {
        double distance = a.distanceTo(b);
        if (distance < 1e-3) return;
        double handle = Math.max(0.1, Math.sqrt(3 * distance * SLACK / 8) / 0.75);
        Vec3 back = a.subtract(b).multiply(1, 0, 1);
        Vec3 p1 = a.add(0, -handle, 0);
        Vec3 p2 = into ? b.add(0, handle, 0) : b.add(back.lengthSqr() < 1e-6 ? Vec3.ZERO : back.normalize().scale(handle * 0.6));
        Vec3[] points = new Vec3[SEGMENTS + 1];
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= SEGMENTS; i++) {
            double s = (double) i / SEGMENTS, r = 1 - s;
            Vec3 q = a.scale(r * r * r).add(p1.scale(3 * r * r * s)).add(p2.scale(3 * r * s * s)).add(b.scale(s * s * s));
            if (i > 1 && i < SEGMENTS - 1) {   // the ends stay in the barb and the opening
                at.set(q.x, q.y, q.z);
                VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
                double top = shape.isEmpty() ? Double.NEGATIVE_INFINITY : at.getY() + shape.max(Direction.Axis.Y);
                if (q.y < top + RADIUS) q = new Vec3(q.x, top + RADIUS, q.z);
            }
            points[i] = q;
        }
        Vec3[][] ring = LiquidJetRenderer.rings(points, SIDES);
        int lightA = LevelRenderer.getLightColor(level, BlockPos.containing(a)), lightB = LevelRenderer.getLightColor(level, BlockPos.containing(b.add(0, 0.05, 0)));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
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

    private static int lerpLight(int a, int b, double t) {
        int block = (int) Math.round(Mth.lerp(t, a & 0xFFFF, b & 0xFFFF)), sky = (int) Math.round(Mth.lerp(t, a >>> 16, b >>> 16));
        return block | sky << 16;
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, BlockPos origin, Vec3 centre, Vec3 direction, float u, float v, int light) {
        out.vertex(matrix, (float) (centre.x - origin.getX() + direction.x * RADIUS), (float) (centre.y - origin.getY() + direction.y * RADIUS),
                        (float) (centre.z - origin.getZ() + direction.z * RADIUS))
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normals, (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }
}
