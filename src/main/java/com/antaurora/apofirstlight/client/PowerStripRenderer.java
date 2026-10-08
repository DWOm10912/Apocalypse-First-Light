package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerStripBlock;
import com.antaurora.apofirstlight.block.WallOutletBlock;
import com.antaurora.apofirstlight.blockentity.PowerStripBlockEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 * A power strip's cord and plug (docs/models/power_outlets_v1.md; the strip itself is baked). The plug is its own OBJ
 * (block/power_plug, tools/build-power-outlets-v1.mjs), turned so its axis points out of the socket; the cord is a round
 * tube in the cord swatch of the strips' atlas ({@link #CORD_U} / {@link #CORD_V}, checked by the generator), so it
 * carries the same LabPBR. Three places: in a wall outlet's socket (straight out, then down), in the carrier's hand, or
 * lying beside the strip. The cord is a cubic leaving the strip's grommet along the strip and entering the plug's tail
 * along its axis, kept a radius above whatever it passes over (as the fuel hoses).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PowerStripRenderer implements BlockEntityRenderer<PowerStripBlockEntity> {
    private static final ResourceLocation PLUG = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_plug");
    private static final ResourceLocation ATLAS_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_strip");
    /** The cord swatch, 0..1 of the strips' atlas (tools/build-power-outlets-v1.mjs CORD_UV). */
    static final float CORD_U = 0.776367F, CORD_V = 0.760742F;
    private static final double RADIUS = 0.00525;   // blocks: 5.25 mm, 1.5 x a 7 mm cord
    /** From the socket face to where the cord leaves the plug's strain relief, blocks. */
    private static final double TAIL = 0.0605;
    /** Half the plug's height (lying on the floor), blocks. */
    private static final double PLUG_HALF = 0.027;
    private static final double SLACK = 0.3;
    private static final int SIDES = 8, SEGMENTS = 18;
    /** The local player carries a strip's plug (for the crosshair hint): when this was last seen. */
    private static long carriedSeen = Long.MIN_VALUE;

    public PowerStripRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(PLUG);
    }

    /** The local player has a strip's plug in hand (seen by the renderer within the last few ticks). */
    public static boolean localCarrying() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() - carriedSeen <= 5;
    }

    @Override
    public boolean shouldRenderOffScreen(PowerStripBlockEntity strip) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(PowerStripBlockEntity strip, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = strip.getLevel();
        BlockState state = strip.getBlockState();
        if (level == null || !(state.getBlock() instanceof PowerStripBlock block)) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos origin = strip.getBlockPos();
        Vec3 exit = block.cordExit(origin, state), along = PowerStripBlock.cordDirection(state);
        Vec3 plugAt, axis;   // the plug's face centre and its axis (out of the socket / away from its tip)
        BlockPos outlet = strip.outlet();
        BlockState outletState = outlet == null ? null : level.getBlockState(outlet);
        Entity carrier = strip.carrierId() >= 0 ? level.getEntity(strip.carrierId()) : null;
        if (outletState != null && outletState.getBlock() instanceof WallOutletBlock) {
            axis = Vec3.atLowerCornerOf(outletState.getValue(WallOutletBlock.FACING).getNormal());
            plugAt = WallOutletBlock.socket(outlet, outletState, strip.socket());
        } else if (carrier instanceof Player player) {
            if (player == mc.player) carriedSeen = level.getGameTime();
            Vec3 look = player.getViewVector(partialTick), hand = hand(mc, player, partialTick, look);
            axis = look.scale(-1);   // the plug's face toward where the player looks
            plugAt = hand.subtract(axis.scale(TAIL));
        } else {
            axis = along.scale(-1);   // lying beside the grommet, tail toward the strip
            plugAt = new Vec3(exit.x + along.x * 0.24, floor(level, exit.add(along.scale(0.24)), 2) + PLUG_HALF, exit.z + along.z * 0.24);
        }
        // the plug: its model faces -Z with the blades; turn its +Z (out of the socket) onto the axis
        pose.pushPose();
        pose.translate(plugAt.x - origin.getX(), plugAt.y - origin.getY(), plugAt.z - origin.getZ());
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(axis.x, axis.z)));
        pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(axis.y, -1, 1))));
        pose.translate(-0.5, -0.5, -0.5);
        int plugLight = LevelRenderer.getLightColor(level, BlockPos.containing(plugAt.add(axis.scale(0.1))));
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),
                null, mc.getModelManager().getModel(PLUG), 1, 1, 1, plugLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ATLAS_TEXTURE);
        cord(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), level, origin, exit, along,
                plugAt.add(axis.scale(TAIL)), axis, sprite.getU(CORD_U * 16), sprite.getV(CORD_V * 16));
    }

    /** Roughly where the carrier's right hand holds the plug: in first person low right of the view, else at the hip. */
    private static Vec3 hand(Minecraft mc, Player player, float partialTick, Vec3 look) {
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson())
            return mc.gameRenderer.getMainCamera().getPosition().add(look.scale(0.55)).add(right.scale(0.3)).add(0, -0.3, 0);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
        return player.getPosition(partialTick).add(0, 0.85, 0).add(right.scale(0.38)).add(flat.scale(0.22));
    }

    /** The top of what stands under {@code p} (searching {@code depth} blocks down), else p's own height minus depth. */
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
        return Math.floor(p.y) - depth;
    }

    private static void cord(PoseStack pose, VertexConsumer out, Level level, BlockPos origin, Vec3 a, Vec3 dirA, Vec3 b, Vec3 dirB, float u, float v) {
        double distance = a.distanceTo(b);
        if (distance < 1e-3) return;
        double handle = Math.max(0.06, Math.min(0.6, Math.sqrt(3 * distance * SLACK / 8) / 0.75));
        Vec3 p1 = a.add(dirA.scale(handle * 0.5)), p2 = b.add(dirB.scale(handle));
        Vec3[] points = new Vec3[SEGMENTS + 1];
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= SEGMENTS; i++) {
            double s = (double) i / SEGMENTS, r = 1 - s;
            Vec3 q = a.scale(r * r * r).add(p1.scale(3 * r * r * s)).add(p2.scale(3 * r * s * s)).add(b.scale(s * s * s));
            if (i > 0 && i < SEGMENTS) {   // the cord droops onto what it passes over, never through it
                q = new Vec3(q.x, q.y - Math.sin(Math.PI * s) * Math.min(0.5, distance * 0.25), q.z);
                at.set(q.x, q.y, q.z);
                VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
                double top = shape.isEmpty() ? Double.NEGATIVE_INFINITY : at.getY() + shape.max(Direction.Axis.Y);
                if (shape.isEmpty()) {   // nothing in this cell: rest on the one below if the cord dips into it
                    at.set(q.x, q.y - 1, q.z);
                    VoxelShape below = level.getBlockState(at).getCollisionShape(level, at);
                    if (!below.isEmpty()) top = Math.max(top, at.getY() + below.max(Direction.Axis.Y));
                }
                if (q.y < top + RADIUS) q = new Vec3(q.x, top + RADIUS, q.z);
            }
            points[i] = q;
        }
        Vec3[][] ring = LiquidJetRenderer.rings(points, SIDES);
        int lightA = LevelRenderer.getLightColor(level, BlockPos.containing(a)), lightB = LevelRenderer.getLightColor(level, BlockPos.containing(b));
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
