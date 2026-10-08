package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link PlugCord}: the plug (its own OBJ, block/power_plug, tools/build-power-outlets-v1.mjs), on an appliance
 * the C13 connector in its inlet (block/power_connector), and the cord, a
 * round tube in the cord swatch of the strips' atlas ({@link #CORD_U} / {@link #CORD_V}, checked by the generator), so it
 * carries the same LabPBR. The plug sits in its socket (axis out of the socket, up toward the slots), in the carrier's
 * hand, or lies beside the device. The cord leaves the device's exit; an appliance whose back stands against something
 * solid runs it down behind its back to the nearer free corner first (out of sight). From there a cubic enters the
 * plug's tail along its axis, drooping onto what it passes over and kept a radius above it (as the fuel hoses).
 * Used by the power strip renderer and the plug-in appliances' renderers, called with the pose at the block entity.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlugCordRenderer {
    private static final ResourceLocation PLUG = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_plug");
    private static final ResourceLocation CONNECTOR = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_connector");
    private static final ResourceLocation ATLAS_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_strip");
    /** The cord swatch, 0..1 of the strips' atlas (tools/build-power-outlets-v1.mjs CORD_UV). */
    static final float CORD_U = 0.448242F, CORD_V = 0.731445F;
    private static final double RADIUS = 0.006;   // blocks: 6 mm, 1.5 x an 8 mm appliance cord
    /** From the socket face to where the cord leaves the plug's strain relief, blocks. */
    private static final double TAIL = 0.080;
    /** Half the plug's height (lying on the floor), blocks. */
    private static final double PLUG_HALF = 0.0263;
    private static final double SLACK = 0.3;
    private static final int SIDES = 8, SEGMENTS = 18;
    /** The local player carries a plug (for the crosshair hint): when and whose. */
    private static long carriedSeen = Long.MIN_VALUE;
    private static BlockPos carriedOwner = BlockPos.ZERO;

    private PlugCordRenderer() {}

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(PLUG);
        event.register(CONNECTOR);
    }

    /** The local player has a plug in hand (seen by a renderer within the last few ticks). */
    public static boolean localCarrying() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() - carriedSeen <= 5;
    }

    /** Whose plug the local player carries (valid while {@link #localCarrying()}). */
    public static BlockPos localCarriedOwner() {
        return carriedOwner;
    }

    public static void render(PlugCord cord, Level level, BlockPos origin, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        PlugCord.Geometry geo = cord.geometry();
        BlockPos host = cord.host();
        Entity carrier = cord.carrierId() >= 0 ? level.getEntity(cord.carrierId()) : null;
        Vec3 plugAt, axis, up = new Vec3(0, 1, 0);   // the plug's face centre, its axis (out of the socket), its up
        boolean plugged = host != null && cord.socket() < PowerPlugs.sockets(level, host);
        if (plugged) {
            plugAt = PowerPlugs.socketPoint(level, host, cord.socket());
            axis = PowerPlugs.socketAxis(level, host, cord.socket());
            up = PowerPlugs.socketUp(level, host, cord.socket());
        } else if (carrier instanceof Player player) {
            if (player == mc.player) { carriedSeen = level.getGameTime(); carriedOwner = cord.ownerPos(); }
            Vec3 look = player.getViewVector(partialTick), hand = hand(mc, player, partialTick, look);
            axis = look.scale(-1);   // the plug's face toward where the player looks
            plugAt = hand.subtract(axis.scale(TAIL));
        } else {
            Start s = start(level, geo, null);
            Vec3 at = s.point.add(s.dir.scale(0.24));
            axis = s.dir.scale(-1);   // lying on the floor, tail toward the device
            plugAt = new Vec3(at.x, floor(level, at.add(0, 0.2, 0), 2) + PLUG_HALF, at.z);
        }
        plug(mc, level, origin, pose, buffers, PLUG, plugAt, axis, up);
        // an appliance's detachable cord: its C13 connector seated in the C14 inlet
        if (geo.inlet() != null) plug(mc, level, origin, pose, buffers, CONNECTOR, geo.inlet(), geo.out(), new Vec3(0, 1, 0));
        Vec3 tail = plugAt.add(axis.scale(TAIL));
        TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ATLAS_TEXTURE);
        List<Vec3> points = new ArrayList<>();
        Start s = start(level, geo, tail);
        points.addAll(s.lead);
        curve(level, s.point, s.dir, tail, axis, points);
        tube(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), level, origin, points.toArray(Vec3[]::new),
                sprite.getU(CORD_U * 16), sprite.getV(CORD_V * 16));
    }

    /** Where the free part of the cord starts, and the straight lead before it (along an appliance's back). */
    private record Start(Vec3 point, Vec3 dir, List<Vec3> lead) {}

    private static Start start(Level level, PlugCord.Geometry geo, Vec3 target) {
        List<Vec3> lead = new ArrayList<>();
        if (geo.corners() == null) { lead.add(geo.exit()); return new Start(geo.exit(), geo.out(), lead); }
        // an appliance: straight out past its back plane first (the exit may sit in a pocket inside the device's own cell,
        // whose collision box would otherwise lift the cord; the water dispenser, 2026-10-08)
        Vec3 out = geo.backExit().add(geo.out().scale(0.03));
        if (free(level, out.add(geo.out().scale(0.2)))) { lead.add(geo.exit()); lead.add(out); return new Start(out, geo.out(), lead); }
        // blocked behind: down behind the back, along it to the nearer free corner
        int pick = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < geo.corners().length; i++) {
            Vec3 c = geo.corners()[i];
            double d = (target == null ? 0 : c.distanceTo(target)) + (free(level, c.add(geo.sides()[i].scale(0.3))) ? 0 : 100) + i * 1e-3;
            if (d < best) { best = d; pick = i; }
        }
        Vec3 corner = geo.corners()[pick];
        lead.add(geo.exit()); lead.add(geo.backExit()); lead.add(geo.backFloor());
        Vec3 along = corner.subtract(geo.backFloor());
        for (int k = 1; k <= 3; k++) lead.add(geo.backFloor().add(along.scale(k / 4.0)));
        return new Start(corner, geo.sides()[pick], lead);
    }

    private static boolean free(Level level, Vec3 p) {
        BlockPos at = BlockPos.containing(p);
        VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) + at.getY() < p.y - 0.02 || shape.min(Direction.Axis.Y) + at.getY() > p.y + 0.3;
    }

    private static void plug(Minecraft mc, Level level, BlockPos origin, PoseStack pose, MultiBufferSource buffers, ResourceLocation model, Vec3 at, Vec3 axis, Vec3 up) {
        Vec3 z = axis.normalize(), y = up.subtract(z.scale(up.dot(z)));
        if (y.lengthSqr() < 1e-6) y = Math.abs(z.y) < 0.9 ? new Vec3(0, 1, 0).subtract(z.scale(z.y)) : new Vec3(0, 0, 1).subtract(z.scale(z.z));
        y = y.normalize();
        Vec3 x = y.cross(z);
        Matrix3f basis = new Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
        pose.pushPose();
        pose.translate(at.x - origin.getX(), at.y - origin.getY(), at.z - origin.getZ());
        pose.mulPose(new Quaternionf().setFromNormalized(basis));
        pose.translate(-0.5, -0.5, -0.5);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.add(axis.scale(0.1))));
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),
                null, mc.getModelManager().getModel(model), 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
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

    /** The top of what stands under {@code p} (searching {@code depth} blocks down), else p's own block bottom minus depth. */
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

    /** The free cord: a cubic from a (leaving along dirA) to b (entering against dirB), drooping, kept above what it crosses. */
    private static void curve(Level level, Vec3 a, Vec3 dirA, Vec3 b, Vec3 dirB, List<Vec3> out) {
        double distance = a.distanceTo(b);
        if (distance < 1e-3) { out.add(b); return; }
        double handle = Math.max(0.06, Math.min(0.6, Math.sqrt(3 * distance * SLACK / 8) / 0.75));
        Vec3 p1 = a.add(dirA.scale(handle * 0.5)), p2 = b.add(dirB.scale(handle));
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= SEGMENTS; i++) {
            double s = (double) i / SEGMENTS, r = 1 - s;
            Vec3 q = a.scale(r * r * r).add(p1.scale(3 * r * r * s)).add(p2.scale(3 * r * s * s)).add(b.scale(s * s * s));
            if (i < SEGMENTS) {   // the cord droops onto what it passes over, never through it
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
            out.add(q);
        }
    }

    private static void tube(PoseStack pose, VertexConsumer out, Level level, BlockPos origin, Vec3[] points, float u, float v) {
        if (points.length < 2) return;
        Vec3[][] ring = LiquidJetRenderer.rings(points, SIDES);
        int n = points.length - 1;
        int lightA = LevelRenderer.getLightColor(level, BlockPos.containing(points[0])), lightB = LevelRenderer.getLightColor(level, BlockPos.containing(points[n]));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (int i = 0; i < n; i++) {
            int l0 = lerpLight(lightA, lightB, (double) i / n), l1 = lerpLight(lightA, lightB, (double) (i + 1) / n);
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
