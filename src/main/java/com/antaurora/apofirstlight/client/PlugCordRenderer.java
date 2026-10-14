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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;

/**
 * Draws a {@link PlugCord}: the plug (its own OBJ, block/power_plug, tools/build-power-outlets-v1.mjs), on an appliance
 * the C13 connector in its inlet (block/power_connector), and the cord, a
 * round tube in the cord swatch of the strips' atlas ({@link #CORD_U} / {@link #CORD_V}, checked by the generator), so it
 * carries the same LabPBR. The plug sits in its socket (axis out of the socket, up toward the slots), in the carrier's
 * hand, or lies beside the device. The cord leaves the device's exit; an appliance whose back stands against something
 * solid runs it down behind its back to the nearer free corner first (out of sight). From there a cubic enters the
 * plug's tail along its axis, drooping onto what it passes over and kept a radius above it (as the fuel hoses).
 * Used by the power strip renderer and the plug-in appliances' renderers, called with the pose at the block entity.
 * <p>
 * Per plug kind ({@link Style}, 2026-10-13): the NEMA 5-15 cords as above; the generator inlet's 15 m L14-30 cord with
 * its own plug (block/l14_30_plug) and yellow swatch (tools/build-generator-inlet-v1.mjs), a 21 mm cord routed along the
 * ground ({@link #groundCord}): a heavy cord does not span the air between the wall and the generator. The first try, the
 * free cubic with a 3 block sag lifted out of the ground, hung in a stiff arc and stepped up and down over every block
 * edge (user, 2026-10-13: "接线有点诡异"). Light at every point. Nothing is drawn while a stowing cord hangs coiled on its
 * owner (its mesh draws the coil).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlugCordRenderer {
    private static final ResourceLocation PLUG = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_plug");
    private static final ResourceLocation L14_PLUG = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/l14_30_plug");
    private static final ResourceLocation CONNECTOR = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_connector");
    private static final ResourceLocation ATLAS_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_strip");
    private static final ResourceLocation INLET_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/generator_inlet_box");
    /** The cord swatch, 0..1 of the strips' atlas (tools/build-power-outlets-v1.mjs CORD_UV). */
    static final float CORD_U = 0.448242F, CORD_V = 0.731445F;
    /** The yellow cord swatch, 0..1 of the inlet's atlas (tools/build-generator-inlet-v1.mjs CORD_UV). */
    static final float L14_CORD_U = 0.299805F, L14_CORD_V = 0.762695F;
    private static final double SLACK = 0.3;
    private static final int SIDES = 8, SEGMENTS = 18;

    /**
     * A cord's look: its plug model, the atlas and swatch of its jacket, its radius, the plug's length from the socket face
     * to where the cord leaves it (tail) and half its height lying down, how far the cord may sag (blocks), whether the
     * light is taken at every point (long cords) or only at the ends, and whether it lies along the ground
     * ({@link #groundCord}; else the free cubic, also the ground cord's fallback).
     */
    private record Style(ResourceLocation plug, ResourceLocation atlas, float u, float v, double radius, double tail, double plugHalf, double sag, boolean lightEach, boolean ground) {}
    // NEMA: 6 mm (1.5 x an 8 mm appliance cord); L14-30: the 10/4 SOOW's 21 mm at real size
    private static final Style NEMA = new Style(PLUG, ATLAS_TEXTURE, CORD_U, CORD_V, 0.006, 0.080, 0.0263, 0.5, false, false);
    private static final Style L14 = new Style(L14_PLUG, INLET_TEXTURE, L14_CORD_U, L14_CORD_V, 0.0105, 0.128, 0.0292, 3.0, true, true);

    private static Style style(PlugCord cord) {
        return cord.kind() == PlugCord.Kind.L14_30 ? L14 : NEMA;
    }
    /** The local player carries a plug (for the crosshair hint): when and whose. */
    private static long carriedSeen = Long.MIN_VALUE;
    private static BlockPos carriedOwner = BlockPos.ZERO;

    private PlugCordRenderer() {}

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(PLUG);
        event.register(L14_PLUG);
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
        if (cord.stowed()) return;   // coiled on its owner: the owner's mesh draws it
        long timing = AflRenderProfiler.begin();
        try {
            renderCord(cord, level, origin, partialTick, pose, buffers);
        } finally {
            AflRenderProfiler.end("plug_cord.all", timing);
        }
    }

    private static void renderCord(PlugCord cord, Level level, BlockPos origin, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        Style st = style(cord);
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
            plugAt = hand.subtract(axis.scale(st.tail()));
        } else if (st.ground() && geo.corners() == null) {
            Vec3 away = flat(geo.out(), new Vec3(0, 0, 1));   // lying flat on the ground out from the wall, tail toward it
            Vec3 at = geo.exit().add(away.scale(0.75));
            double ground = groundAt(level, at.x, at.y, at.z, DROP);
            axis = away.scale(-1);
            plugAt = new Vec3(at.x, (Double.isNaN(ground) ? at.y - 1 : ground) + st.plugHalf(), at.z);
        } else {
            Start s = start(level, geo, null);
            Vec3 at = s.point.add(s.dir.scale(0.24));
            axis = s.dir.scale(-1);   // lying on the floor, tail toward the device
            plugAt = new Vec3(at.x, floor(level, at.add(0, 0.2, 0), 2) + st.plugHalf(), at.z);
        }
        plug(mc, level, origin, pose, buffers, st.plug(), plugAt, axis, up);
        // an appliance's detachable cord: its C13 connector seated in the C14 inlet
        if (geo.inlet() != null) plug(mc, level, origin, pose, buffers, CONNECTOR, geo.inlet(), geo.out(), new Vec3(0, 1, 0));
        Vec3 tail = plugAt.add(axis.scale(st.tail()));
        TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(st.atlas());
        Start s = start(level, geo, tail);
        // the ground cord's lead off the plug's tail: out of a socket level, a little way out of a hand, none lying
        Vec3 lead = plugged ? flat(axis, axis).scale(PLUG_LEAD) : carrier instanceof Player ? axis.scale(HAND_LEAD) : Vec3.ZERO;
        List<Vec3> points = st.ground() && geo.corners() == null ? routed(cord, level, geo, tail, lead, plugged ? host : null) : null;
        if (points == null) {
            points = new ArrayList<>(s.lead);
            curve(level, s.point, s.dir, tail, axis, points, st);
        }
        Vec3[] path = points.toArray(Vec3[]::new);
        int[] light = new int[path.length];
        int from = Math.max(0, Math.min(s.lead.size() - 1, path.length - 2));
        if (st.lightEach()) {   // a long cord: the light where each point lies (it crosses shade and open ground)
            for (int i = 0; i < path.length; i++) {
                // on a smoothed ground top (TerrainSmooth) the cord lies inside the ground block's cell, which is dark
                BlockPos at = BlockPos.containing(path[Math.max(i, from)].add(0, 0.05, 0));
                if (level.getBlockState(at).isSolidRender(level, at)) at = at.above();
                light[i] = LevelRenderer.getLightColor(level, at);
            }
        } else {
            // light from where the cord comes into view (the lead may start inside the wall behind the appliance, at light 0)
            int lightStart = LevelRenderer.getLightColor(level, BlockPos.containing(s.point.add(0, 0.05, 0))), lightEnd = LevelRenderer.getLightColor(level, BlockPos.containing(tail));
            int n = path.length - 1;
            for (int i = 0; i < path.length; i++) light[i] = lerpLight(lightStart, lightEnd, Math.max(0, (double) (i - from) / Math.max(1, n - from)));
        }
        float[] uv = LiquidJetRenderer.texelCentre(sprite, st.u(), st.v());
        tube(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), path, origin, light, uv[0], uv[1], st.radius());
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
        // blocked behind (against a wall): down to the floor just inside the back edge, then along under it to the side
        // where the cell beside is free; in a row of appliances on past the neighbours (up to ROW_REACH blocks), hidden
        // under their back edges too. Nothing free within reach: the nearer corner, as before.
        int pick = 0, steps = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < geo.corners().length; i++) {
            for (int k = 0; k <= ROW_REACH; k++) {
                Vec3 c = geo.corners()[i].add(geo.sides()[i].scale(k));
                boolean open = free(level, c.add(geo.sides()[i].scale(0.3)));
                if (!open && k < ROW_REACH) continue;
                double d = (target == null ? 0 : c.distanceTo(target)) + k * 0.5 + (open ? 0 : 100) + i * 1e-3;
                if (d < best) { best = d; pick = i; steps = open ? k : 0; }
                break;
            }
        }
        Vec3 corner = geo.corners()[pick].add(geo.sides()[pick].scale(steps));
        // a socket above the appliance (a chest freezer under its outlet): up behind the back, out over the top back edge
        // right below the plug, when that is shorter than any way round a side
        if (target != null && geo.top() != null) {
            Vec3 a = geo.top()[0], b = geo.top()[1], ab = b.subtract(a);
            double t = Math.max(0, Math.min(1, target.subtract(a).dot(ab) / Math.max(1e-9, ab.lengthSqr())));
            Vec3 up = a.add(ab.scale(t));
            if (target.y > up.y + 0.05 && free(level, up.add(0, 0.15, 0)) && target.distanceTo(up) < best) {
                lead.add(geo.exit());
                lead.add(new Vec3(up.x, up.y - 0.05, up.z).subtract(geo.out().scale(0.02)));   // inside the cabinet, under the edge
                return new Start(up, new Vec3(0, 1, 0), lead);
            }
        }
        lead.add(geo.exit()); lead.add(geo.backFloor());
        Vec3 along = corner.subtract(geo.backFloor());
        int pieces = Math.max(4, (int) Math.ceil(along.length() * 2));   // a point every half block for the light
        for (int k = 1; k < pieces; k++) lead.add(geo.backFloor().add(along.scale((double) k / pieces)));
        return new Start(corner, geo.sides()[pick], lead);
    }

    /** Blocks a cord behind an appliance row may run past its neighbours to find a free side. */
    private static final int ROW_REACH = 3;

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

    /**
     * The free cord: a cubic from a (leaving along dirA) to b (entering against dirB), drooping (at most the style's sag),
     * kept above what it crosses: a point that sagged into a block is lifted onto it, again while it is still inside one
     * (a long cord's sag reaches into the ground).
     */
    private static void curve(Level level, Vec3 a, Vec3 dirA, Vec3 b, Vec3 dirB, List<Vec3> out, Style st) {
        double distance = a.distanceTo(b), radius = st.radius();
        if (distance < 1e-3) { out.add(b); return; }
        double handle = Math.max(0.06, Math.min(0.6, Math.sqrt(3 * distance * SLACK / 8) / 0.75));
        Vec3 p1 = a.add(dirA.scale(handle * 0.5)), p2 = b.add(dirB.scale(handle));
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int segments = Math.max(SEGMENTS, (int) Math.ceil(distance * 3));
        for (int i = 1; i <= segments; i++) {
            double s = (double) i / segments, r = 1 - s;
            Vec3 q = a.scale(r * r * r).add(p1.scale(3 * r * r * s)).add(p2.scale(3 * r * s * s)).add(b.scale(s * s * s));
            if (i < segments) {   // the cord droops onto what it passes over, never through it
                q = new Vec3(q.x, q.y - Math.sin(Math.PI * s) * Math.min(st.sag(), distance * 0.25), q.z);
                for (int k = 0; k < 4; k++) {
                    double top = Math.max(surface(level, at, q, 0, radius), surface(level, at, q, -1, radius));
                    if (q.y >= top + radius) break;
                    q = new Vec3(q.x, top + radius, q.z);
                }
            }
            out.add(q);
        }
    }

    /**
     * The top of the collision boxes of the cell {@code dy} below q's that lie under q (q's x / z inside the box, the box not
     * starting above it). Per box, not the cell's highest box: beside a narrow appliance (a water dispenser leaves 2.4 px of
     * its cell free on each side) a cord used to be lifted onto the appliance's top, a loop up and down (2026-10-08).
     */
    private static double surface(Level level, BlockPos.MutableBlockPos at, Vec3 q, int dy, double radius) {
        at.set(q.x, q.y + dy, q.z);
        VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
        if (shape.isEmpty()) return Double.NEGATIVE_INFINITY;
        double lx = q.x - at.getX(), ly = q.y - at.getY(), lz = q.z - at.getZ();
        double top = Double.NEGATIVE_INFINITY;
        for (var box : shape.toAabbs())
            if (lx > box.minX - radius && lx < box.maxX + radius && lz > box.minZ - radius && lz < box.maxZ + radius && box.minY <= ly + 0.3)
                top = Math.max(top, at.getY() + box.maxY);
        return top;
    }

    // ---- the ground cord (the generator's L14-30, 2026-10-13) ----

    /** Blocks searched down for the ground under either end, and the most the cord drops off an edge from one sample to the next. */
    private static final int DROP = 6;
    /** The lead off the plug's tail before the cord goes down: out of a socket (a short droop), out of a hand (then straight down). */
    private static final double PLUG_LEAD = 0.3, HAND_LEAD = 0.08;
    /** Along the gland out of the exit before hanging straight down; the bend radius of every corner; round a bend of the run. */
    private static final double GLAND = 0.14, BEND = 0.12, RUN_BEND = 0.3;
    /**
     * Ground samples apart; the most the cord climbs from one sample to the next (a step, a slab, a log); the room it needs
     * over what it lies on; the steepest it falls off an edge (blocks per block); the clearance kept round the host.
     */
    private static final double STEP = 0.2, CLIMB = 1.05, ROOM = 0.06, SLOPE = 2.5, MARGIN = 0.15;
    /** The way round: cells searched beyond the ends' box, the search's limit, and what a block of climbing costs (in blocks walked). */
    private static final int REACH = 4, MAX_POPS = 2500;
    private static final double CLIMB_COST = 1.5;
    /** The bow of a long straight run: at most BOW to the side, BOW_SHARE of the run; none under BOW_MIN. */
    private static final double BOW = 0.5, BOW_SHARE = 0.08, BOW_MIN = 1.5;
    /**
     * Ticks a route is kept while its ends stay put; ticks a way round is kept while its ends stay in their cells (the
     * search costs a few ms; a block placed in the way is seen within 5 s).
     */
    private static final int REFRESH = 20, WAY_REFRESH = 100;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1}, DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private record Route(Vec3 exit, Vec3 tail, Vec3 lead, long time, @Nullable List<Vec3> points) {}
    private static final Map<PlugCord, Route> ROUTES = new WeakHashMap<>();
    /** The bends of a way round, by the cells of its ends (a carried plug moves every frame; the search runs per cell). */
    private record Way(BlockPos from, BlockPos to, long time, List<Vec3> bends) {}
    private static final Map<PlugCord, Way> WAYS = new WeakHashMap<>();

    /** The ground cord's points, kept while its ends do not move (a plugged cord; one in a hand moves every frame). */
    @Nullable
    private static List<Vec3> routed(PlugCord cord, Level level, PlugCord.Geometry geo, Vec3 tail, Vec3 lead, @Nullable BlockPos host) {
        long now = level.getGameTime();
        Route kept = ROUTES.get(cord);
        if (kept != null && now >= kept.time() && now - kept.time() < REFRESH && kept.exit().equals(geo.exit()) && kept.tail().equals(tail) && kept.lead().equals(lead))
            return kept.points();
        long seed = cord.ownerPos().asLong() * 31 + (host == null ? 0 : host.asLong());
        List<Vec3> points = groundCord(cord, level, geo.exit(), geo.out(), tail, lead, host, seed);
        ROUTES.put(cord, new Route(geo.exit(), tail, lead, now, points));
        return points;
    }

    /**
     * A cord that lies on the ground: out of the exit along its gland and straight down; along the ground to under the
     * plug's lead; up to the lead and into the plug's tail. On the ground it goes over what it can (a step, a slab, a
     * slope: up at most CLIMB per sample, with room on top), under what is wholly above it (a table top), and round what
     * it cannot cross (a post, a wall, a fence, the generator it is plugged into): the straight run when that is clear,
     * else the shortest way round found over the cells between the ends ({@link #around}). It falls off an edge no
     * steeper than SLOPE, a long straight run bows gently to a free side, every corner is rounded and every point kept a
     * radius above the boxes under it. Null when either end finds no ground within DROP blocks: the free cubic then.
     * <p>
     * The first version only stepped up onto whatever stood in the way, at most a block per 0.2 sample, so it would have
     * climbed a porch post or a wall (2026-10-13, before the user saw it).
     */
    @Nullable
    private static List<Vec3> groundCord(PlugCord cord, Level level, Vec3 exit, Vec3 out, Vec3 tail, Vec3 lead, @Nullable BlockPos host, long seed) {
        double r = L14.radius();
        Vec3 gland = exit.add(out.scale(GLAND)), corner = tail.add(lead);
        double gIn = groundAt(level, gland.x, gland.y, gland.z, DROP), gPlug = groundAt(level, corner.x, Math.max(corner.y, tail.y) + 0.05, corner.z, DROP);
        if (Double.isNaN(gIn) || Double.isNaN(gPlug) || gIn + r > gland.y - 0.02 || gPlug + r > Math.max(corner.y, tail.y) + 0.05) return null;
        // the run in plan view (y 0): from under the lead to under the gland, round what is in the way
        Vec3 a = new Vec3(corner.x, 0, corner.z), b = new Vec3(gland.x, 0, gland.z);
        AABB keepOut = host == null ? null : footprint(level, host);
        List<Vec3> bends = bends(cord, level, a, b, gPlug, keepOut);
        List<Vec3> plan = new ArrayList<>();
        plan.add(a);
        plan.addAll(bends);
        plan.add(b);
        List<Vec3> run = resample(fillet(plan, RUN_BEND), STEP);
        if (bends.isEmpty()) bow(level, run, keepOut, seed, gPlug);
        // heights: walked from the plug's end (where a rounded bend cuts into something, straight on), the gland's foot
        // last; then no steeper than SLOPE off any edge
        int n = run.size();
        double[] h = walk(level, run, gPlug, keepOut, false), d = new double[n], y = new double[n];
        h[n - 1] = gIn;
        for (int i = 1; i < n; i++) d[i] = d[i - 1] + run.get(i).distanceTo(run.get(i - 1));
        for (int i = 0; i < n; i++) {
            double top = h[i];
            for (int j = 0; j < n; j++) top = Math.max(top, h[j] - SLOPE * Math.abs(d[i] - d[j]));
            y[i] = top + r;
        }
        List<Vec3> path = new ArrayList<>();
        path.add(exit);
        path.add(gland);
        for (int i = n - 1; i >= 0; i--) { Vec3 p = run.get(i); path.add(new Vec3(p.x, y[i], p.z)); }
        if (lead.lengthSqr() > 1e-6) path.add(corner);
        path.add(tail);
        List<Vec3> smooth = fillet(path, BEND);
        // a rounded corner cuts a little into an edge it bends over: lift each point a radius above the boxes under it
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 1; i < smooth.size() - 1; i++) {
            Vec3 q = smooth.get(i);
            double top = Math.max(surface(level, at, q, 0, r), surface(level, at, q, -1, r));
            if (q.y < top + r) smooth.set(i, new Vec3(q.x, top + r, q.z));
        }
        return smooth;
    }

    /** The highest collision box top under x / z (only boxes over that point) at or below fromY, searching depth blocks down; NaN if none. */
    private static double groundAt(Level level, double x, double fromY, double z, int depth) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int top = Mth.floor(fromY);
        for (int k = 0; k <= depth; k++) {
            at.set(Mth.floor(x), top - k, Mth.floor(z));
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (shape.isEmpty()) continue;
            double lx = x - at.getX(), lz = z - at.getZ(), best = Double.NaN;
            for (AABB box : shape.toAabbs()) {
                double boxTop = at.getY() + box.maxY;
                if (lx >= box.minX && lx <= box.maxX && lz >= box.minZ && lz <= box.maxZ && boxTop <= fromY + 1e-3 && (Double.isNaN(best) || boxTop > best)) best = boxTop;
            }
            if (!Double.isNaN(best)) return best;
        }
        return Double.NaN;
    }

    /** The host's collision box in the world, inflated by MARGIN: the cord keeps out of it. Null without one. */
    @Nullable
    private static AABB footprint(Level level, BlockPos host) {
        VoxelShape shape = level.getBlockState(host).getCollisionShape(level, host);
        return shape.isEmpty() ? null : shape.bounds().move(host).inflate(MARGIN, 0, MARGIN);
    }

    /**
     * Where a cord lying at height prev comes to lie at x / z: on top of what rises through it there (a step, a slab, a
     * slope; at most CLIMB up), else on the highest surface at or below it (level, or down an edge, at most DROP down).
     * What lies wholly above the cord (a table top, an overhang) it passes under. NaN where it cannot go: too high, no
     * room on top (a post, a wall: the next box starts where the climb ends), no ground, or inside keepOut.
     */
    private static double step(Level level, double x, double z, double prev, @Nullable AABB keepOut) {
        if (keepOut != null && x > keepOut.minX && x < keepOut.maxX && z > keepOut.minZ && z < keepOut.maxZ) return Double.NaN;
        double cord = prev + 0.03, climb = Double.NaN, ground = Double.NaN;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double lx = x - ix, lz = z - iz;
        for (int cy = Mth.floor(cord); cy >= Mth.floor(prev) - DROP; cy--) {
            if (!Double.isNaN(ground) && cy < Mth.floor(ground) - 1) break;   // nothing lower reaches up past it
            at.set(ix, cy, iz);
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                if (lx < box.minX || lx > box.maxX || lz < box.minZ || lz > box.maxZ) continue;
                double lo = cy + box.minY, hi = cy + box.maxY;
                if (lo <= cord && hi > cord) climb = Double.isNaN(climb) ? hi : Math.max(climb, hi);
                else if (hi <= cord) ground = Double.isNaN(ground) ? hi : Math.max(ground, hi);
            }
        }
        double h = Double.isNaN(climb) ? ground : climb > prev + CLIMB ? Double.NaN : climb;
        return Double.isNaN(h) || !room(level, x, h, z) ? Double.NaN : h;
    }

    /** Nothing at x / z between h and ROOM above it (the cord fits there on what it lies on). */
    private static boolean room(Level level, double x, double h, double z) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double lx = x - ix, lz = z - iz;
        for (int cy = Mth.floor(h) - 1; cy <= Mth.floor(h + ROOM); cy++) {
            at.set(ix, cy, iz);
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs())
                if (lx >= box.minX && lx <= box.maxX && lz >= box.minZ && lz <= box.maxZ && cy + box.minY < h + ROOM && cy + box.maxY > h + 0.03) return false;
        }
        return true;
    }

    /**
     * The heights of a cord lying along the points (plan view), from h0 at the first, by {@link #step}. Strict: null as soon
     * as one point cannot be reached. Lenient: such a point keeps the height before it.
     */
    @Nullable
    private static double[] walk(Level level, List<Vec3> run, double h0, @Nullable AABB keepOut, boolean strict) {
        double[] h = new double[run.size()];
        h[0] = h0;
        for (int i = 1; i < run.size(); i++) {
            Vec3 p = run.get(i);
            double g = step(level, p.x, p.z, h[i - 1], keepOut);
            if (Double.isNaN(g) && strict) return null;
            h[i] = Double.isNaN(g) ? h[i - 1] : g;
        }
        return h;
    }

    /** Whether a cord lying at h0 at a can lie straight on to b (plan view), every STEP; the height at b, else NaN. */
    private static double straight(Level level, Vec3 a, Vec3 b, double h0, @Nullable AABB keepOut) {
        double[] h = walk(level, resample(List.of(a, b), STEP), h0, keepOut, true);
        return h == null ? Double.NaN : h[h.length - 1];
    }

    /** The bends of the run from a to b (plan view): none when the straight run is clear, else the way round (kept per end cells). */
    private static List<Vec3> bends(PlugCord cord, Level level, Vec3 a, Vec3 b, double h0, @Nullable AABB keepOut) {
        if (!Double.isNaN(straight(level, a, b, h0, keepOut))) return List.of();
        long now = level.getGameTime();
        BlockPos from = BlockPos.containing(a), to = BlockPos.containing(b);
        Way kept = WAYS.get(cord);
        if (kept != null && now >= kept.time() && now - kept.time() < WAY_REFRESH && kept.from().equals(from) && kept.to().equals(to)) return kept.bends();
        List<Vec3> bends = around(level, a, b, h0, keepOut);
        WAYS.put(cord, new Way(from, to, now, bends));
        return bends;
    }

    /**
     * The shortest way round (plan view) from a to b: an A* over the cells between them (REACH more on every side, 8
     * neighbours, a diagonal only past two passable sides), each cell entered at its centre by {@link #step}, climbing
     * CLIMB_COST per block; then pulled straight (each bend kept only where the straight cut past it is not clear). No
     * way within reach: no bends (the run then goes straight, over or through what is in the way).
     */
    private static List<Vec3> around(Level level, Vec3 a, Vec3 b, double h0, @Nullable AABB keepOut) {
        int ax = Mth.floor(a.x), az = Mth.floor(a.z), bx = Mth.floor(b.x), bz = Mth.floor(b.z);
        int x0 = Math.min(ax, bx) - REACH, z0 = Math.min(az, bz) - REACH;
        int w = Math.abs(ax - bx) + 2 * REACH + 1, d = Math.abs(az - bz) + 2 * REACH + 1;
        int start = (az - z0) * w + ax - x0, goal = (bz - z0) * w + bx - x0;
        double[] cost = new double[w * d], height = new double[w * d];
        int[] from = new int[w * d];
        boolean[] done = new boolean[w * d];
        Arrays.fill(cost, Double.MAX_VALUE);
        cost[start] = 0;
        height[start] = h0;
        from[start] = -1;
        PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble((double[] e) -> e[0]));
        open.add(new double[]{Math.hypot(ax - bx, az - bz), start});
        for (int pops = 0; !open.isEmpty() && pops < MAX_POPS && !done[goal]; pops++) {
            int c = (int) open.poll()[1];
            if (done[c]) continue;
            done[c] = true;
            int cx = c % w, cz = c / w;
            for (int k = 0; k < 8; k++) {
                int nx = cx + DX[k], nz = cz + DZ[k], m = nz * w + nx;
                if (nx < 0 || nz < 0 || nx >= w || nz >= d || done[m]) continue;
                if (k >= 4 && (Double.isNaN(step(level, x0 + nx + 0.5, z0 + cz + 0.5, height[c], keepOut))
                        || Double.isNaN(step(level, x0 + cx + 0.5, z0 + nz + 0.5, height[c], keepOut)))) continue;
                double hn = m == goal ? step(level, b.x, b.z, height[c], keepOut) : step(level, x0 + nx + 0.5, z0 + nz + 0.5, height[c], keepOut);
                if (Double.isNaN(hn)) continue;
                double g = cost[c] + (k >= 4 ? Math.sqrt(2) : 1) + CLIMB_COST * Math.max(0, hn - height[c]);
                if (g >= cost[m]) continue;
                cost[m] = g;
                height[m] = hn;
                from[m] = c;
                open.add(new double[]{g + Math.hypot(nx + x0 - bx, nz + z0 - bz), m});
            }
        }
        if (!done[goal]) return List.of();
        List<Vec3> pts = new ArrayList<>();
        List<Double> hs = new ArrayList<>();
        for (int c = from[goal]; c >= 0 && c != start; c = from[c]) { pts.add(0, new Vec3(x0 + c % w + 0.5, 0, z0 + c / w + 0.5)); hs.add(0, height[c]); }
        pts.add(0, a);
        hs.add(0, h0);
        pts.add(b);
        hs.add(height[goal]);
        // pull it straight: from each kept point on along the path while the cord still reaches the next point in a straight line
        List<Vec3> bends = new ArrayList<>();
        int i = 0;
        double h = h0;
        while (i < pts.size() - 1) {
            int j = i + 1;
            double reached = hs.get(j);
            while (j + 1 < pts.size()) {
                double next = straight(level, pts.get(i), pts.get(j + 1), h, keepOut);
                if (Double.isNaN(next)) break;
                reached = next;
                j++;
            }
            if (j < pts.size() - 1) bends.add(pts.get(j));
            h = reached;
            i = j;
        }
        return bends;
    }

    /**
     * Bows a long straight run (plan view) to one side, the side picked by the seed, the other when the first cannot be
     * walked or climbs more than 0.3 over the straight run, none when both fail.
     */
    private static void bow(Level level, List<Vec3> run, @Nullable AABB keepOut, long seed, double h0) {
        Vec3 a = run.get(0), b = run.get(run.size() - 1), ab = b.subtract(a);
        double length = ab.length();
        if (length < BOW_MIN) return;
        double[] flat = walk(level, run, h0, keepOut, false);
        double amount = Math.min(BOW, BOW_SHARE * length);
        Vec3 side = new Vec3(-ab.z / length, 0, ab.x / length);
        int first = (Long.hashCode(seed * 0x9E3779B97F4A7C15L) & 1) == 0 ? 1 : -1;
        for (int sign : new int[]{first, -first}) {
            Vec3 off = side.scale(sign * amount);
            List<Vec3> bowed = new ArrayList<>(run.size());
            for (Vec3 p : run) bowed.add(p.add(off.scale(Math.sin(Math.PI * Mth.clamp(p.subtract(a).dot(ab) / (length * length), 0, 1)))));
            double[] h = walk(level, bowed, h0, keepOut, true);
            if (h == null || climbed(h) > climbed(flat) + 0.3) continue;
            for (int i = 0; i < run.size(); i++) run.set(i, bowed.get(i));
            return;
        }
    }

    private static double climbed(double[] h) {
        double up = 0;
        for (int i = 1; i < h.length; i++) up += Math.max(0, h[i] - h[i - 1]);
        return up;
    }

    /** The polyline with every inner corner rounded: a quadratic from {@code bend} before the corner to bend after it (at most half of either leg). */
    private static List<Vec3> fillet(List<Vec3> in, double bend) {
        List<Vec3> pts = dedupe(in);
        if (pts.size() < 3) return pts;
        List<Vec3> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 1; i < pts.size() - 1; i++) {
            Vec3 c = pts.get(i), u = pts.get(i - 1).subtract(c), w = pts.get(i + 1).subtract(c);
            double lu = u.length(), lw = w.length(), t = Math.min(bend, Math.min(lu, lw) / 2);
            u = u.scale(1 / lu);
            w = w.scale(1 / lw);
            Vec3 p = c.add(u.scale(t)), q = c.add(w.scale(t));
            int m = Math.max(1, (int) Math.ceil(Math.acos(Mth.clamp(-u.dot(w), -1, 1)) / 0.3));   // a point every ~17 degrees of turn
            out.add(p);
            for (int k = 1; k < m; k++) {
                double s = (double) k / m, o = 1 - s;
                out.add(p.scale(o * o).add(c.scale(2 * o * s)).add(q.scale(s * s)));
            }
            out.add(q);
        }
        out.add(pts.get(pts.size() - 1));
        return dedupe(out);
    }

    private static List<Vec3> dedupe(List<Vec3> in) {
        List<Vec3> out = new ArrayList<>(in.size());
        for (Vec3 p : in) if (out.isEmpty() || out.get(out.size() - 1).distanceToSqr(p) > 1e-8) out.add(p);
        return out;
    }

    /** Points evenly along the polyline, about {@code step} apart, both ends included. */
    private static List<Vec3> resample(List<Vec3> in, double step) {
        double total = 0;
        for (int i = 1; i < in.size(); i++) total += in.get(i).distanceTo(in.get(i - 1));
        List<Vec3> out = new ArrayList<>();
        out.add(in.get(0));
        if (total < 1e-4) return out;
        int pieces = Math.max(1, (int) Math.ceil(total / step));
        int seg = 1;
        double before = 0;   // the length up to in[seg - 1]
        for (int k = 1; k < pieces; k++) {
            double want = total * k / pieces;
            while (seg < in.size() - 1 && before + in.get(seg).distanceTo(in.get(seg - 1)) < want) { before += in.get(seg).distanceTo(in.get(seg - 1)); seg++; }
            Vec3 s0 = in.get(seg - 1), s1 = in.get(seg);
            double len = s1.distanceTo(s0);
            out.add(len < 1e-9 ? s0 : s0.add(s1.subtract(s0).scale((want - before) / len)));
        }
        out.add(in.get(in.size() - 1));
        return out;
    }

    private static Vec3 flat(Vec3 v, Vec3 fallback) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? fallback : f.normalize();
    }

    /** The tube along the points, each ring at its light. */
    private static void tube(PoseStack pose, VertexConsumer out, Vec3[] points, BlockPos origin, int[] light, float u, float v, double radius) {
        if (points.length < 2) return;
        Vec3[][] ring = LiquidJetRenderer.rings(points, SIDES);
        int n = points.length - 1;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (int i = 0; i < n; i++) {
            int l0 = light[i], l1 = light[i + 1];
            for (int k = 0; k < SIDES; k++) {
                int k2 = (k + 1) % SIDES;
                vertex(out, matrix, normals, origin, points[i], ring[i][k], u, v, l0, radius);
                vertex(out, matrix, normals, origin, points[i], ring[i][k2], u, v, l0, radius);
                vertex(out, matrix, normals, origin, points[i + 1], ring[i + 1][k2], u, v, l1, radius);
                vertex(out, matrix, normals, origin, points[i + 1], ring[i + 1][k], u, v, l1, radius);
            }
        }
    }

    private static int lerpLight(int a, int b, double t) {
        int block = (int) Math.round(Mth.lerp(t, a & 0xFFFF, b & 0xFFFF)), sky = (int) Math.round(Mth.lerp(t, a >>> 16, b >>> 16));
        return block | sky << 16;
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, BlockPos origin, Vec3 centre, Vec3 direction, float u, float v, int light, double radius) {
        out.vertex(matrix, (float) (centre.x - origin.getX() + direction.x * radius), (float) (centre.y - origin.getY() + direction.y * radius),
                        (float) (centre.z - origin.getZ() + direction.z * radius))
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normals, (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }
}
