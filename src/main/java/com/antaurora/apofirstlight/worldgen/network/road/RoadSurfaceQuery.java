package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The road layer of a vehicle's ground query (Road ground query V1, 2026-10-16; docs/worldgen/road_surface_query_v1.md,
 * agreed with Main in docs/vehicles/first_pickup_v0_design.md 5.1): under a wheel's contact point (x, z), the road surface
 * nearest to yHint within LAYER: its continuous height, unit upward normal, surface kind and whether it blocks (a wall the
 * wheel must not stand on: the body hits its blocks). Nothing (null / false): not a road there, or the road is covered by
 * something else; the vehicle's smooth terrain and block layers take over.
 * <ul>
 *   <li>pavement (the lanes, shoulders, a bridge's deck, a tunnel's bore): the road design's height (datum, cross slope,
 *   superelevation) and its analytic normal, where the column holds the road's blocks near it (a dug or covered road
 *   gives nothing);</li>
 *   <li>graded turf (median, roadside, slopes): the block's corners as the two triangles it is drawn with (split along
 *   the north-west to south-east diagonal);</li>
 *   <li>road_structure on top of a column (a bridge's parapet and wing walls, a tunnel's walkway, safety barrier, walls and
 *   portals): its collision top, concrete, blocking;</li>
 *   <li>road_surface not under the design's pavement (a deck's edge strip): its collision top, flat;</li>
 *   <li>a framework town's pavement (Town Framework V1, 2026-10-18; TownWorks#spot): its streets' asphalt (crowned),
 *   curbs, sidewalks and driveway aprons (concrete) at their design height, the normal by differences over the same
 *   material (a curb's face is no slope); the town's turf (utility strips, lots, yards) as graded turf above;</li>
 *   <li>the forest home's gravel driveway (County road G3, Driveway#height, 2026-10-18): its height, the normal by
 *   differences; kind GRAVEL (grip 0.65, Main 2026-10-19), its turf edges as above.</li>
 *   <li>broken asphalt (road Mesh-ification P1, RoadDamage): over a sub-cell a strike took, the aggregate base at the
 *   asphalt's underside, flat, kind GRAVEL.</li>
 * </ul>
 * Read only: no chunk is loaded (an unloaded chunk gives nothing), no state kept; the same network (RoadStructureBlock's
 * bound works) on both sides. Called per wheel point many times a tick: #sample(Level, double, double, double, Out)
 * allocates nothing on the roads (per-thread buffers; the curve and pavement lookups through their allocation-free paths,
 * which give the same numbers as RoadDesign#pavementY / RoadCurve#local; RoadSurfaceCheck asserts it); in a town the
 * footprint lookup (TownWorks#spot) allocates a few short-lived objects per point.
 */
public final class RoadSurfaceQuery {
    private RoadSurfaceQuery() {}

    /** How far from yHint a surface may lie to count (a layer farther away is another level: under a bridge), m. */
    public static final double LAYER = 1.5;
    /** An attached ramp this close to its interstate's pavement is on it (else it passes over it on a deck), m. */
    static final double SAME_LEVEL = 0.5;
    /**
     * How far the column's top block may lie under the design's pavement (a deck or turf under the asphalt's edge and
     * bevels) and how far over it (the blocks' 1/32 layers, a cell's cross slope) and still be that pavement, m. Higher:
     * something stands on the road (a tunnel portal's barrier on the shoulder), its own surface.
     */
    static final double PAVE_MATCH = 0.6, COVER = 0.1;
    /**
     * A town's COVER: its paved column's collision is the highest of the column's nine points (TownWorks#pavedRange), a
     * curb's top over the gutter where the column straddles the curb (2026-10-18), m.
     */
    static final double TOWN_COVER = 0.25;
    /** Step of the pavement's slope differences along and across the road, m. */
    static final double STEP = 0.05;
    /** How far past a road's end a point still lies on its end (RoadCurve#local's own reach), m. */
    static final double END = 0.01;
    /** #design: how far past a paved band's edge a vertex by the column's frame is still on that edge, m. */
    static final double EDGE_SNAP = 0.001;

    /**
     * The road's surface kinds (the vehicle maps them to its grip: asphalt 1.0, concrete 0.95, gravel 0.65, turf 0.70);
     * GRAVEL is the forest home's driveway (Main, 2026-10-19).
     */
    public enum Surface { ASPHALT, CONCRETE, TURF, GRAVEL }

    /** A surface found: height (world y), unit upward normal, kind, whether it blocks (no wheel stands on it). */
    public record Hit(double height, double nx, double ny, double nz, Surface surface, boolean blocking) {}

    /** A reusable result for callers that keep one per thread (#sample(Level, double, double, double, Out)). */
    public static final class Out {
        public double height, nx, ny = 1, nz;
        public Surface surface = Surface.ASPHALT;
        public boolean blocking;
        /** Set by #pavement: the pavement found is a town's (its blocks may stand higher over it: TOWN_COVER). */
        boolean town;
        /** Set by #pavement: the road whose design won (null: a pad, a town or the driveway), at station rs, offset ru. */
        RoadDesign road;
        double rs, ru;

        void set(double height, double nx, double ny, double nz, Surface surface, boolean blocking) {
            this.height = height;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.surface = surface;
            this.blocking = blocking;
        }

        public Hit hit() { return new Hit(height, nx, ny, nz, surface, blocking); }
    }

    /** One thread's buffers (server thread, client main thread, the offline check). */
    private static final class Buffers {
        final Out out = new Out();
        /** #ground's and #kind's (they never run inside a #sample) */
        final Out ground = new Out();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final RoadCurve.Scratch curve = new RoadCurve.Scratch();
    }

    private static final ThreadLocal<Buffers> BUFFERS = ThreadLocal.withInitial(Buffers::new);

    /** The road surface under (x, z) nearest to yHint within LAYER, or null (Main's interface). */
    @Nullable
    public static Hit sample(Level level, double x, double z, double yHint) {
        Out o = BUFFERS.get().out;
        return sample(level, x, z, yHint, o) ? o.hit() : null;
    }

    /** #sample into out; false: no road surface there (out unchanged in part). */
    public static boolean sample(Level level, double x, double z, double yHint, Out out) {
        RoadWorks works = RoadStructureBlock.boundWorks();
        if (works == null) return false;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), cx = bx >> 4, cz = bz >> 4;
        if (!works.touches(cx, cz) || !level.hasChunk(cx, cz)) return false;
        LevelChunk chunk = level.getChunk(cx, cz);
        Buffers buf = BUFFERS.get();
        boolean paved = pavement(works, x, z, yHint, out, buf.curve, true);
        double pave = out.height, cover = out.town ? TOWN_COVER : COVER;
        BlockPos.MutableBlockPos p = buf.pos;
        int top = (int) Math.floor(yHint + LAYER), bottom = (int) Math.floor(yHint - LAYER) - 1;
        Block surface = AflBlocks.ROAD_SURFACE.get(), structure = AflBlocks.ROAD_STRUCTURE.get(), turf = AflBlocks.GRADED_TURF.get();
        for (int y = top; y >= bottom; y--) {
            BlockState s = chunk.getBlockState(p.set(bx, y, bz));
            if (s.isAir()) continue;
            Block b = s.getBlock();
            // the tunnel's parts are the vehicle's blocks (the body hits them), never the ground
            if (b instanceof TunnelFixtureBlock) continue;
            double t;
            if (b == surface) {
                // broken asphalt (road Mesh-ification P1): over a gone sub-cell the wheel stands on the base, gravel
                var gone = com.antaurora.apofirstlight.road.RoadDamage.goneAt(level, x, z);
                if (gone != null) {
                    t = gone.top() - gone.depth();
                    if (!near(t, yHint)) return false;
                    out.set(t, 0, 1, 0, Surface.GRAVEL, false);
                    return true;
                }
                t = y + s.getValue(RoadSurfaceBlock.LAYERS) / 32.0;
                if (paved && pavement(pave, t, cover)) return true;
                if (!near(t, yHint)) return false;
                out.set(t, 0, 1, 0, Surface.ASPHALT, false);
                return true;
            }
            if (b == turf) {
                double fx = x - bx, fz = z - bz, base = y + (s.getValue(GradedTurfBlock.SHIFT) ? 0.5 : 0);
                double nw = s.getValue(GradedTurfBlock.NW) / 8.0, ne = s.getValue(GradedTurfBlock.NE) / 8.0,
                        sw = s.getValue(GradedTurfBlock.SW) / 8.0, se = s.getValue(GradedTurfBlock.SE) / 8.0;
                // the triangle (nw, ne, se) or (nw, sw, se): the north-west to south-east diagonal, as drawn
                double gx, gz;
                if (fx >= fz) { gx = ne - nw; gz = se - ne; }
                else { gx = se - sw; gz = sw - nw; }
                t = base + nw + gx * fx + gz * fz;
                // the turf just under the asphalt's edge and bevels: the wheel is on the pavement
                if (paved && pavement(pave, t, cover)) return true;
                if (!near(t, yHint)) return false;
                double inv = 1 / Math.sqrt(gx * gx + 1 + gz * gz);
                out.set(t, -gx * inv, inv, -gz * inv, Surface.TURF, false);
                return true;
            }
            VoxelShape shape = s.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;   // plants, water: not what a wheel stands on
            if (b != structure) return false; // covered by some other block: the vehicle's blocks decide
            t = y + shape.max(Direction.Axis.Y);
            if (paved && pavement(pave, t, cover)) return true;
            if (!near(t, yHint)) return false;
            out.set(t, 0, 1, 0, Surface.CONCRETE, true);
            return true;
        }
        return false;
    }

    private static boolean near(double t, double yHint) { return Math.abs(t - yHint) <= LAYER; }

    /** Whether a block top t is the design's pavement pave (#PAVE_MATCH, cover: #COVER or #TOWN_COVER). */
    private static boolean pavement(double pave, double t, double cover) { return t <= pave + cover + 1e-9 && t >= pave - PAVE_MATCH; }

    /**
     * The design's pavement under (x, z) nearest to yHint within LAYER over every road whose paved band holds the point
     * (on the route, between the inner and outer shoulder edges), into out (ASPHALT, not blocking); false: none. Pure: the
     * road designs only, no world (the offline check runs it over every road).
     */
    public static boolean pavement(RoadWorks works, double x, double z, double yHint, Out out) {
        return pavement(works, x, z, yHint, out, BUFFERS.get().curve, true);
    }

    /**
     * The asphalt under the pavement at (x, z) near yHint (road Mesh-ification P0, 2026-10-25): {pavement top, asphalt
     * thickness} (CrossSection#asphaltDepth; a junction pad's CrossSection.PAD_ASPHALT, a town street's 10 cm), or null
     * where no asphalt is there (no pavement, the driveway's gravel, a town's concrete).
     */
    public static double[] asphalt(RoadWorks works, double x, double z, double yHint) {
        Out o = new Out();
        if (!height(works, x, z, yHint, o) || o.surface != Surface.ASPHALT) return null;
        double depth = o.road != null ? o.road.section.asphaltDepth() : o.town ? CrossSection.C2.asphaltDepth() : CrossSection.PAD_ASPHALT;
        return new double[]{o.height, depth};
    }

    /** #pavement without the normal (out's normal straight up): its height and kind only, for the ground's geometry. */
    public static boolean height(RoadWorks works, double x, double z, double yHint, Out out) {
        return pavement(works, x, z, yHint, out, BUFFERS.get().curve, false);
    }

    /**
     * The drawn top of a road column for the ground's geometry (road drainage D1, docs/worldgen/road_drainage_v1_design.md
     * 3), where water runs on what is drawn while wheels and feet keep the collision: block s at (x, y, z) is a column's top
     * road_surface or graded_turf (the cell above open). rel: its 25 vertex heights 0.25 m apart (EarthGrid layout, blocks
     * above the floor y). Turf starts as its two drawn triangles (north-west to south-east), a pavement block as its
     * collision top; every vertex where the design's pavement lies over the block as #sample takes it (#PAVE_MATCH, the
     * cover) gets the design's height (#height). False: a pavement block with no design over it (a dug or covered road:
     * its boxes stand). works null: turf only.
     */
    public static boolean ground(@Nullable RoadWorks works, BlockState s, int x, int y, int z, double[] rel) {
        Block b = s.getBlock();
        boolean turf = b instanceof GradedTurfBlock;
        double top;   // the block's top at its centre (a pavement block's collision top)
        if (turf) {
            float[] c = GradedTurfBlock.corners(s);
            for (int j = 0; j <= 4; j++) for (int i = 0; i <= 4; i++) {
                double fx = i / 4.0, fz = j / 4.0;
                // the triangle (nw, ne, se) or (nw, sw, se), as drawn
                rel[j * 5 + i] = fx >= fz ? c[0] + (c[1] - c[0]) * fx + (c[3] - c[1]) * fz : c[0] + (c[3] - c[2]) * fx + (c[2] - c[0]) * fz;
            }
            top = y + (c[0] + c[1] + c[2] + c[3]) / 4;
        } else if (b instanceof RoadSurfaceBlock) {
            top = y + s.getValue(RoadSurfaceBlock.LAYERS) / 32.0;
            java.util.Arrays.fill(rel, 0, 25, top - y);
        } else return false;
        return design(works, x, y, z, top, rel) || turf;
    }

    /**
     * #ground's pavement step, pure (the offline check times it and holds it to the plain query): rel holds the block's own
     * top at each vertex; every vertex where the design's pavement near top lies over it as #sample takes it gets the
     * design's height. True: any did.
     */
    public static boolean design(@Nullable RoadWorks works, int x, int y, int z, double top, double[] rel) {
        boolean any = false;
        if (works != null && works.touches(x >> 4, z >> 4)) {
            Buffers buf = BUFFERS.get();
            Out o = buf.ground;
            // one search at the centre; where a road's design wins there and no pad, town or driveway is near, the vertices
            // are that design at their own station and offset, by the centre's frame (curvature at most 1/135 m: under
            // 0.1 mm off in height), each one off its band asked in full; an attached ramp gives way to its interstate where
            // their bands meet (#pavement), so its columns ask every vertex
            RoadDesign d = null;
            double s0 = 0, u0 = 0, tx = 0, tz = 0, along = 1;
            boolean alone = false;   // the only road near: a vertex off its band is off every pavement
            if (pavement(works, x + 0.5, z + 0.5, top, o, buf.curve, false) && o.road != null && o.road.attach == null && quiet(works, x, z)) {
                d = o.road;
                RoadDesign[] near = works.roadsNear(x + 0.5, z + 0.5);
                alone = near != null && near.length == 1;
                s0 = o.rs;
                u0 = o.ru;
                double[] f = buf.curve.f;
                d.curve.frame(Math.max(0, Math.min(d.length, s0)), f, buf.curve.span);
                tx = f[2];
                tz = f[3];
                along = Math.max(0.2, 1 - f[4] * u0);
            }
            for (int j = 0; j <= 4; j++) for (int i = 0; i <= 4; i++) {
                // the block's own top under the vertex (turf: its triangle there), as #sample matches a wheel's point
                double t = y + rel[j * 5 + i], h;
                boolean town = false;
                double dx = i / 4.0 - 0.5, dz = j / 4.0 - 0.5;
                double s = s0 + (dx * tx + dz * tz) / along, u = u0 - dx * tz + dz * tx;
                boolean on = d != null && s >= 0 && s <= d.length;
                // a vertex on a band edge (within EDGE_SNAP of it: the frame and the plain station search differ by
                // micrometres) is that edge
                double v = on ? Math.abs(u) : 0, vc = on ? Math.max(d.section.halfMedian, Math.min(d.section.edge, v)) : 0;
                if (on && Math.abs(v - vc) <= EDGE_SNAP && (!d.section.divided || (u < 0) == (u0 < 0))) {
                    h = d.pavementYAt(s, Math.copySign(vc, u));
                } else if (on && alone) {
                    continue;
                } else {
                    if (!pavement(works, x + i / 4.0, z + j / 4.0, top, o, buf.curve, false)) continue;
                    h = o.height;
                    town = o.town;
                }
                if (!pavement(h, t, town ? TOWN_COVER : COVER)) continue;
                rel[j * 5 + i] = h - y;
                any = true;
            }
        }
        return any;
    }

    /** Whether no junction pad, town street or driveway touches column (x, z): its roads' designs alone decide it. */
    private static boolean quiet(RoadWorks works, int x, int z) {
        if (works.pad(x, z) != null || works.pad(x + 1, z) != null || works.pad(x, z + 1) != null || works.pad(x + 1, z + 1) != null) return false;
        if (works.towns().touches(x >> 4, z >> 4)) return false;
        Driveway dw = works.drive();
        return dw.isEmpty() || !dw.touches(x >> 4, z >> 4);
    }

    /** The kind of the pavement over a road column at its centre (#height near yHint), null: none (road drainage D1, 4). */
    @Nullable
    public static Surface kind(@Nullable RoadWorks works, double x, double z, double yHint) {
        if (works == null || !works.touches(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16))) return null;
        Buffers buf = BUFFERS.get();
        return pavement(works, x, z, yHint, buf.ground, buf.curve, false) ? buf.ground.surface : null;
    }

    private static boolean pavement(RoadWorks works, double x, double z, double yHint, Out out, RoadCurve.Scratch k, boolean normals) {
        out.town = false;
        out.road = null;
        RoadDesign[] near = works.roadsNear(x, z);
        if (near == null) near = NONE;
        RoadDesign best = null;
        double bs = 0, bu = 0, by = 0, bd = Double.POSITIVE_INFINITY;
        for (RoadDesign d : near) {
            CrossSection sec = d.section;
            if (!d.curve.local(x, z, sec.edge + 0.5, 0, k)) continue;
            // RoadCurve#local takes a point up to 1 cm past a road's end onto it: on the end, never a gap (2026-10-18: a road
            // exactly 2808.00 m long missed its last line of samples by round-off)
            double s = Math.max(0, Math.min(d.length, k.s)), u = k.u;
            if (k.s < -END || k.s > d.length + END || !sec.paved(Math.abs(u))) continue;
            // an attached ramp (C3.3b group 2) on its interstate's paved band: the interstate's pavement (on its level: not
            // where the ramp passes over it on a deck, C3.3b group 3)
            double y = d.pavementYAt(s, u);
            RoadDesign host = d.attach == null ? null : works.attachedHost(d);
            if (host != null && host.curve.local(x, z, host.section.edge + 0.5, 0, k) && k.s >= 0 && k.s <= host.length
                    && host.section.paved(Math.abs(k.u)) && Math.abs(host.pavementYAt(k.s, k.u) - y) < SAME_LEVEL) continue;
            double dist = Math.abs(y - yHint);
            if (dist > LAYER || dist >= bd) continue;
            best = d;
            bs = s;
            bu = u;
            by = y;
            bd = dist;
        }
        // a junction pad (C3.3b): its own height and normal
        Junction pad = works.pad(x, z);
        if (pad != null) {
            double y = pad.height(x, z), dist = Math.abs(y - yHint);
            if (dist <= LAYER && dist < bd) {
                if (!normals) {
                    out.set(y, 0, 1, 0, Surface.ASPHALT, false);
                    return true;
                }
                double[] n = pad.normal(x, z);
                out.set(y, n[0], n[1], n[2], Surface.ASPHALT, false);
                return true;
            }
        }
        // a framework town's pavement (its streets; a road's formation through a town is the road's, found above)
        TownWorks towns = works.towns();
        if (!towns.isEmpty()) {
            TownWorks.Spot sp = towns.spot(x, z);
            if (sp != null && sp.part().paved()) {
                double dist = Math.abs(sp.h() - yHint);
                if (dist <= LAYER && dist < bd) {
                    if (normals) townNormal(towns, x, z, sp, out);
                    else out.set(sp.h(), 0, 1, 0, sp.part() == TownWorks.Part.ASPHALT ? Surface.ASPHALT : Surface.CONCRETE, false);
                    out.town = true;
                    return true;
                }
            }
        }
        // the forest home's gravel driveway (a road's own pavement is found above)
        Driveway dw = works.drive();
        if (!dw.isEmpty() && dw.touches(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16))) {
            double y = dw.height(x, z), dist = Math.abs(y - yHint);
            if (!Double.isNaN(y) && dist <= LAYER && dist < bd) {
                if (normals) driveNormal(dw, x, z, y, out);
                else out.set(y, 0, 1, 0, Surface.GRAVEL, false);
                // its collision is the nine points' mean (RoadWorks#paved): a town's cover
                out.town = true;
                return true;
            }
        }
        if (best == null) return false;
        if (normals) normal(best, bs, bu, by, out, k);
        else out.set(by, 0, 1, 0, Surface.ASPHALT, false);
        out.road = best;
        out.rs = bs;
        out.ru = bu;
        return true;
    }

    /** A driveway point's unit upward normal into out (height y, kind GRAVEL): differences STEP each way, one-sided at its edge. */
    static void driveNormal(Driveway dw, double x, double z, double y, Out out) {
        double gx = driveSlope(dw, x, z, y, STEP, 0), gz = driveSlope(dw, x, z, y, 0, STEP);
        double inv = 1 / Math.sqrt(gx * gx + 1 + gz * gz);
        out.set(y, -gx * inv, inv, -gz * inv, Surface.GRAVEL, false);
    }

    private static double driveSlope(Driveway dw, double x, double z, double y, double dx, double dz) {
        double a = dw.height(x + dx, z + dz), b = dw.height(x - dx, z - dz);
        if (!Double.isNaN(a) && !Double.isNaN(b)) return (a - b) / (2 * STEP);
        if (!Double.isNaN(a)) return (a - y) / STEP;
        if (!Double.isNaN(b)) return (y - b) / STEP;
        return 0;
    }

    private static final RoadDesign[] NONE = new RoadDesign[0];

    /**
     * A town pavement point's unit upward normal into out (with its height and kind: asphalt, or concrete for a curb, a
     * sidewalk or a driveway's apron): central differences of the spot heights STEP each way, one-sided where the other
     * point is another material or off the pavement (the curb's face, the pavement's edge), flat where both are.
     */
    static void townNormal(TownWorks towns, double x, double z, TownWorks.Spot sp, Out out) {
        boolean asphalt = sp.part() == TownWorks.Part.ASPHALT;
        double gx = townSlope(towns, x, z, sp, asphalt, STEP, 0), gz = townSlope(towns, x, z, sp, asphalt, 0, STEP);
        double inv = 1 / Math.sqrt(gx * gx + 1 + gz * gz);
        out.set(sp.h(), -gx * inv, inv, -gz * inv, asphalt ? Surface.ASPHALT : Surface.CONCRETE, false);
    }

    private static double townSlope(TownWorks towns, double x, double z, TownWorks.Spot sp, boolean asphalt, double dx, double dz) {
        TownWorks.Spot a = towns.spot(x + dx, z + dz), b = towns.spot(x - dx, z - dz);
        boolean ua = a != null && a.part().paved() && (a.part() == TownWorks.Part.ASPHALT) == asphalt;
        boolean ub = b != null && b.part().paved() && (b.part() == TownWorks.Part.ASPHALT) == asphalt;
        if (ua && ub) return (a.h() - b.h()) / (2 * STEP);
        if (ua) return (a.h() - sp.h()) / STEP;
        if (ub) return (sp.h() - b.h()) / STEP;
        return 0;
    }

    /**
     * The pavement's unit upward normal at (s, u) of road d (height y) into out: the slopes along the station and across
     * by differences on the design (one side of the band: the crown and the edges stay sharp, as built), turned into the
     * world by the curve's frame. A point at lateral u moves (1 - k u) per metre of station on a curve of curvature k.
     */
    static void normal(RoadDesign d, double s, double u, double y, Out out, RoadCurve.Scratch k) {
        CrossSection sec = d.section;
        double s0 = Math.max(0, s - STEP), s1 = Math.min(d.length, s + STEP);
        double ys = s1 > s0 ? (d.pavementYAt(s1, u) - d.pavementYAt(s0, u)) / (s1 - s0) : 0;
        // across, within this side's band (an undivided road's band runs through the centre)
        double a, b;
        if (sec.divided) {
            a = u < 0 ? -sec.edge : sec.halfMedian;
            b = u < 0 ? -sec.halfMedian : sec.edge;
        } else {
            a = -sec.edge;
            b = sec.edge;
        }
        double u0 = Math.max(a, u - STEP), u1 = Math.min(b, u + STEP);
        double yu = u1 > u0 ? (d.pavementYAt(s, u1) - d.pavementYAt(s, u0)) / (u1 - u0) : 0;
        double[] f = k.f;
        d.curve.frame(Math.max(0, Math.min(d.length, s)), f, k.span);
        double tx = f[2], tz = f[3], kappa = f[4];
        double along = Math.max(0.2, 1 - kappa * u);
        // lateral unit vector (-tz, tx): RoadDesign#point
        double gx = ys / along * tx - yu * tz, gz = ys / along * tz + yu * tx;
        double inv = 1 / Math.sqrt(gx * gx + 1 + gz * gz);
        out.set(y, -gx * inv, inv, -gz * inv, Surface.ASPHALT, false);
    }
}
