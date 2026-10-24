package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Bowl;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Entrance;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.EntranceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Notch;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pitch;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Place;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Platform;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Rock;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Shape;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Step;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Talus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;

/**
 * U4b (docs/worldgen/underground_geology_caves_v0_design.md 22.7): the rocks on the entrance cuts, laid once per plan after
 * the rig, boulders first, then the loose stones. Rockfall logic, test values:
 * <ul>
 * <li>a collapse pit's fallen roof lies broken on its floor (slabs and blocks, one per 30 m2 of floor);</li>
 * <li>a cover sinkhole's funnel has bared the till's erratics (rounded, one per 110 m2);</li>
 * <li>a karst window's collar has spall from its rim (one per 70 m2); a shaft's or fissure's lip none;</li>
 * <li>a great sinkhole's floor has the roof's breakdown (one per 60 m2, at most 40), its ledges and shelf the stones;</li>
 * <li>a talus sorts its fall: rubble on the flanks (loose stones), the blocks past its foot, the biggest furthest out;</li>
 * <li>a porch has the mouth's roof spall against its sides (0-2), its floor the stones.</li>
 * </ul>
 * A boulder stands on ground within 0.75 m of its own all round, never over a hollow, and keeps clear of every route
 * (places' platforms, pitches' shafts, steps' lines: {@link #ROUTE} past its edge), of the other boulders ({@link #GAP})
 * and of the protected zones; together they cover at most a quarter of a bowl's floor. Loose stones go one patch to a
 * column, in clumps, thinning up the walls (the transition ring). Data only: EntranceRocksFeature puts them in the chunks.
 */
final class EntranceRocks {
    /** Boulder forms by setting (indices into ROCK_FORMS). */
    static final int[] BREAKDOWN = {3, 4, 5, 6, 7, 10}, ERRATIC = {0, 0, 1, 9, 11, 2}, SPALL = {4, 5, 6, 9, 10},
            TOE_NEAR = {5, 6, 9, 10}, TOE_FAR = {3, 4, 6, 7, 11};
    /** Clear walk between two boulders, and round a route past a boulder's edge, m. */
    static final double GAP = 1.0, ROUTE = 1.2;
    /** How far a boulder's ground may differ from its own round its footprint, m. */
    static final double FLAT = 0.75;
    static final double CELL = 64;

    final UndergroundPlanner pl;
    final UndergroundPlan plan;
    final TerrainPlanSurface s;
    final Map<Long, List<Object>> routes = new HashMap<>();
    final Map<Long, List<double[]>> boulders = new HashMap<>();
    final Set<Long> taken = new HashSet<>();
    final Map<String, Entrance> entranceAt = new HashMap<>();

    private EntranceRocks(UndergroundPlanner pl) {
        this.pl = pl;
        this.plan = pl.plan;
        this.s = pl.s;
    }

    static void lay(UndergroundPlanner pl) { new EntranceRocks(pl).run(); }

    void run() {
        for (Entrance e : plan.entrances) entranceAt.put(at(e.x(), e.z()), e);
        indexRoutes();
        for (int i = 0; i < plan.shapes.size(); i++) {
            Shape sh = plan.shapes.get(i);
            if (sh instanceof Bowl b) bowlBoulders(i, b);
            else if (sh instanceof Talus t) talusBoulders(i, t);
            else if (sh instanceof Notch n) porchBoulders(i, n);
            else if (sh instanceof Pit pt) pitBoulders(i, pt);
        }
        for (int i = 0; i < plan.shapes.size(); i++) {
            Shape sh = plan.shapes.get(i);
            if (sh instanceof Bowl b) bowlStones(i, b);
            else if (sh instanceof Talus t) talusStones(i, t);
            else if (sh instanceof Notch n) porchStones(i, n);
            else if (sh instanceof Pit pt) pitStones(i, pt);
        }
        plan.indexRocks();
    }

    double h(long k) { return pl.h(k); }

    static String at(double x, double z) { return Math.round(x) + "," + Math.round(z); }

    static long col(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    static long cell(double v) { return (long) Math.floor(v / CELL); }

    // ------------------------------------------------------------------ the ground

    /** The ground over (x, z) where a cut (a bowl, a porch) took it down: NaN where it is not cut, talus lies on it, a hollow is under it or it is wet. */
    double cutGround(double x, double z) {
        if (s.waterAt(x, z)) return Double.NaN;
        double planned = s.heightAt(x, z), gy = plan.groundAt(x, z, planned);
        if (gy > planned - 0.3) return Double.NaN;
        if (!plan.openAt(x, gy + 0.05, z) || plan.openAt(x, gy - 0.05, z) || plan.waterAt(x, gy + 0.05, z)) return Double.NaN;
        return gy;
    }

    /**
     * The ground over (x, z) by a talus: {its surface, 0} on its flank (open over it), {its host's floor, how far that lies
     * over the cone's surface} up to 2.1 m past its foot (about 3 m out); null elsewhere, or wet.
     */
    double[] talusGround(Talus t, double x, double z) {
        if (s.waterAt(x, z)) return null;
        double ys = t.surfaceAt(x, z);
        boolean above = plan.openAt(x, ys + 0.05, z);
        if (above) {
            if (plan.openAt(x, ys - 0.05, z) || plan.waterAt(x, ys + 0.05, z)) return null;
            return new double[]{ys, 0};
        }
        for (double d = 0.25; d <= 2.1 + 1e-9; d += 0.25)
            if (plan.openAt(x, ys + d + 0.05, z)) {
                double gy = ys + d - 0.125;
                if (plan.waterAt(x, gy + 0.2, z)) return null;
                return new double[]{gy, d};
            }
        return null;
    }

    /**
     * Whether a rock can lie at (x, y, z): open over it, solid under it, and room to stand (1.5 m) or the sky over it; a
     * shallow cut's edge under its own top (an overhang a hand high) is not ground.
     */
    boolean stands(double x, double y, double z) {
        return plan.openAt(x, y + 0.3, z) && !plan.openAt(x, y - 0.3, z) && (plan.openAt(x, y + 1.5, z) || y + 1.5 > s.heightAt(x, z));
    }

    /** Whether rocks may lie in the column: not in a protected zone (the start area is open country). */
    boolean free(double x, double z) {
        GeoProtect.Zone zn = pl.p.zone(x, z);
        return zn == GeoProtect.Zone.NONE || zn == GeoProtect.Zone.START_AREA;
    }

    /** The share of patches with a flint (Rock R1 2.4): 1 in 8 where the cave is in limestone or dolomite. */
    double flint(int owner) {
        RockType host = plan.caves.get(owner).host;
        return host != null && host.karst >= 0.5 ? 1 / 8.0 : 0;
    }

    // ------------------------------------------------------------------ the rock (R3, 22.9)

    final Map<Integer, Integer> looks = new HashMap<>();

    /**
     * What the rocks of a shape are (UndergroundPlan.ROCK_LOOKS), decided once per shape: a cover sinkhole's erratics (and
     * its till's stones) are granite, carried in by the ice; the rest is the rock the cut bares or the fallen roof was:
     * the first rock under the loose ground at a bowl's or porch's middle, a great sinkhole's the chamber roof's (10 m
     * over its floor), a talus its host's.
     */
    int look(int shape) {
        Integer known = looks.get(shape);
        if (known != null) return known;
        Shape sh = plan.shapes.get(shape);
        int out;
        if (sh instanceof Talus t && t.host() != shape && t.host() >= 0 && t.host() < plan.shapes.size()
                && !(plan.shapes.get(t.host()) instanceof Talus)) out = look(t.host());
        else {
            double x, z, y;
            if (sh instanceof Bowl b) {
                Entrance e = entranceAt.get(at(b.x(), b.z()));
                if (e != null && e.kind() == EntranceKind.COVER_SINKHOLE) { looks.put(shape, 0); return 0; }
                x = b.x(); z = b.z(); y = b.floorY();
            } else if (sh instanceof Pit pt) {
                x = pt.x(); z = pt.z(); y = Math.min(pt.collarY(), pt.floorY() + 10);
            } else if (sh instanceof Notch n) {
                x = n.x(); z = n.z(); y = n.floorY();
            } else if (sh instanceof Talus t) {
                x = t.x(); z = t.z(); y = t.apexY();
            } else {
                double[] bb = sh.bounds();
                x = (bb[0] + bb[3]) / 2; z = (bb[2] + bb[5]) / 2; y = bb[4];
            }
            out = look(x, y, z, sh.owner());
        }
        looks.put(shape, out);
        return out;
    }

    /** The look of the first rock at or under y in column (x, z) (loose ground skipped, 60 m at most), else the cave's. */
    int look(double x, double y, double z, int owner) {
        GeologyModel.Column c = pl.g.column(x, z);
        for (double d = 0; d <= 60; d += 1) {
            RockType r = pl.g.rockAt(c, Math.min(y, c.h) - d);
            if (r != null && !r.soil) return look(r);
        }
        RockType host = plan.caves.get(owner).host;
        return host == null || host.soil ? 0 : look(host);
    }

    /** A rock type's look: crystalline rock granite, the carbonates limestone, sandstone, and the soft beds shale. */
    static int look(RockType r) {
        return switch (r) {
            case LIMESTONE, DOLOMITE, MARL -> 1;
            case SANDSTONE -> 2;
            case SHALE, COAL_SEAM, ROCK_SALT -> 3;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ routes

    void indexRoutes() {
        for (Place q : plan.places) {
            if (q.y() < -1e5 || q.platform() == null) continue;
            Platform pf = q.platform();
            put(q, q.x() - pf.hx(), q.z() - pf.hz(), q.x() + pf.hx(), q.z() + pf.hz());
        }
        for (Pitch pc : plan.pitches) put(pc, pc.ax() - pc.rh(), pc.az() - pc.rh(), pc.ax() + pc.rh(), pc.az() + pc.rh());
        for (Step st : plan.steps) {
            Place a = plan.places.get(st.a()), b = plan.places.get(st.b());
            if (a.y() < -1e5 || b.y() < -1e5) continue;
            put(st, Math.min(a.x(), b.x()), Math.min(a.z(), b.z()), Math.max(a.x(), b.x()), Math.max(a.z(), b.z()));
        }
    }

    void put(Object o, double x0, double z0, double x1, double z1) {
        double m = 4;
        for (long cz = cell(z0 - m); cz <= cell(z1 + m); cz++)
            for (long cx = cell(x0 - m); cx <= cell(x1 + m); cx++) routes.computeIfAbsent(cx << 32 ^ (cz & 0xFFFFFFFFL), k -> new ArrayList<>()).add(o);
    }

    /** A pitch wider than this is a great sinkhole's: its rope hangs down the wall from the rim to the floor place. */
    static final double WIDE_PITCH = 12;

    static double lineDist(double x, double z, double ax, double az, double bx, double bz) {
        double ux = bx - ax, uz = bz - az, l2 = ux * ux + uz * uz;
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * ux + (z - az) * uz) / l2));
        return Math.hypot(x - ax - ux * t, z - az - uz * t);
    }

    /** Whether a boulder of radius rad at (x, y, z) keeps clear of every place, shaft (a great sinkhole's rope line) and step. */
    boolean clearOfRoutes(double x, double y, double z, double rad) {
        List<Object> l = routes.get(cell(x) << 32 ^ (cell(z) & 0xFFFFFFFFL));
        if (l == null) return true;
        for (Object o : l) {
            if (o instanceof Place q) {
                Platform pf = q.platform();
                if (Math.abs(q.y() - y) < 4 && Math.abs(x - q.x()) < pf.hx() + rad + ROUTE && Math.abs(z - q.z()) < pf.hz() + rad + ROUTE) return false;
            } else if (o instanceof Pitch pc) {
                Place top = plan.places.get(pc.top()), bot = plan.places.get(pc.bottom());
                if (y <= bot.y() - 2 || y >= top.y() + 4) continue;
                if (pc.rh() <= WIDE_PITCH ? Math.hypot(x - pc.ax(), z - pc.az()) < pc.rh() + rad + ROUTE
                        : lineDist(x, z, top.x(), top.z(), bot.x(), bot.z()) < rad + ROUTE) return false;
            } else if (o instanceof Step st) {
                Place a = plan.places.get(st.a()), b = plan.places.get(st.b());
                double ux = b.x() - a.x(), uz = b.z() - a.z(), l2 = ux * ux + uz * uz;
                double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a.x()) * ux + (z - a.z()) * uz) / l2));
                double d = Math.hypot(x - a.x() - ux * t, z - a.z() - uz * t), yy = a.y() + (b.y() - a.y()) * t;
                if (d < rad + ROUTE && Math.abs(yy - y) < 3) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ boulders

    /**
     * Whether a boulder of radius rad fits at (x, y, z) on the ground the function gives: level all round (FLAT), no hollow
     * a metre under it or its edge, clear of the routes, the other boulders and the protected zones.
     */
    boolean fits(double x, double y, double z, double rad, DoubleBinaryOperator ground) {
        if (!free(x, z) || !stands(x, y, z) || plan.openAt(x, y - 1, z)) return false;
        for (int a = 0; a < 8; a++) {
            double px = x + Math.cos(a * Math.PI / 4) * (rad + 0.3), pz = z + Math.sin(a * Math.PI / 4) * (rad + 0.3);
            double g = ground.applyAsDouble(px, pz);
            if (Double.isNaN(g) || Math.abs(g - y) > FLAT || plan.openAt(px, g - 1, pz)) return false;
        }
        if (!clearOfRoutes(x, y, z, rad)) return false;
        for (long cz = cell(z) - 1; cz <= cell(z) + 1; cz++)
            for (long cx = cell(x) - 1; cx <= cell(x) + 1; cx++) {
                List<double[]> l = boulders.get(cx << 32 ^ (cz & 0xFFFFFFFFL));
                if (l == null) continue;
                for (double[] o : l) if (Math.hypot(x - o[0], z - o[1]) < rad + o[2] + GAP) return false;
            }
        return true;
    }

    void addBoulder(int bx, double y, int bz, int form, int shape, long kk) {
        double x = bx + 0.5, z = bz + 0.5, rad = UndergroundPlan.ROCK_LENGTH[form] / 2;
        plan.rocks.add(new Rock(bx, y, bz, form, (int) (h(kk + 7) * 4), 0, false, false, shape, look(shape)));
        boulders.computeIfAbsent(cell(x) << 32 ^ (cell(z) & 0xFFFFFFFFL), k -> new ArrayList<>()).add(new double[]{x, z, rad});
        int e = (int) Math.ceil(rad);
        for (int dz = -e; dz <= e; dz++) for (int dx = -e; dx <= e; dx++) taken.add(col(bx + dx, bz + dz));
    }

    /** The bowl's setting: {forms, m2 of floor per boulder} (null: no boulders), from its entrance. */
    Object[] setting(Bowl b) {
        Entrance e = entranceAt.get(at(b.x(), b.z()));
        if (e == null) return null;
        return switch (e.kind()) {
            case COLLAPSE_PIT -> new Object[]{BREAKDOWN, 30.0};
            case COVER_SINKHOLE -> new Object[]{ERRATIC, 110.0};
            case KARST_WINDOW, GREAT_SINKHOLE -> new Object[]{SPALL, 70.0};
            default -> null;
        };
    }

    /** The radius the throat (or a collar's pit) opens in the bowl's floor: none in a dead bowl. */
    double inner(Bowl b) {
        Entrance e = entranceAt.get(at(b.x(), b.z()));
        return e == null || plan.caves.get(b.owner()).kind == CaveKind.SINKHOLE ? 0 : e.radius() + 0.3;
    }

    void bowlBoulders(int i, Bowl b) {
        Object[] st = setting(b);
        if (st == null) return;
        int[] set = (int[]) st[0];
        double inner = inner(b), reach = b.rf() + 1.5;
        double area = Math.PI * (reach * reach - inner * inner), want = area / (double) st[1];
        int n = Math.min(12, (int) want + (h(UndergroundPlanner.k(161, i)) < want - (int) want ? 1 : 0)), got = 0;
        double cover = 0;
        for (int t = 0; got < n && t < 8 * n; t++) {
            long kk = UndergroundPlanner.k(162, (long) i << 8 | t);
            int form = set[(int) (h(kk) * set.length)];
            double rad = UndergroundPlan.ROCK_LENGTH[form] / 2, lo = inner + rad + GAP;
            if (cover + Math.PI * rad * rad > 0.25 * area || reach <= lo) continue;
            double r = Math.sqrt(lo * lo + h(kk + 1) * (reach * reach - lo * lo)), an = 2 * Math.PI * h(kk + 2);
            int bx = (int) Math.floor(b.x() + Math.cos(an) * r), bz = (int) Math.floor(b.z() + Math.sin(an) * r);
            double x = bx + 0.5, z = bz + 0.5, gy = cutGround(x, z);
            if (Double.isNaN(gy) || gy > b.floorY() + 0.8 || !fits(x, gy, z, rad, this::cutGround)) continue;
            addBoulder(bx, gy, bz, form, i, kk);
            cover += Math.PI * rad * rad;
            got++;
            plan.note("rocks boulders " + entranceAt.get(at(b.x(), b.z())).kind().name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    void talusBoulders(int i, Talus t) {
        double L = (t.apexY() - t.y0()) / t.slope();
        int n = Math.max(1, Math.min(12, (int) Math.round(L / 4))), got = 0;
        DoubleBinaryOperator ground = (px, pz) -> { double[] g = talusGround(t, px, pz); return g == null ? Double.NaN : g[0]; };
        for (int k = 0; got < n && k < 10 * n; k++) {
            long kk = UndergroundPlanner.k(163, (long) i << 8 | k);
            double r = (0.25 + 0.85 * h(kk + 1)) * L + 1, an = 2 * Math.PI * h(kk + 2);
            int bx = (int) Math.floor(t.x() + Math.cos(an) * r), bz = (int) Math.floor(t.z() + Math.sin(an) * r);
            double x = bx + 0.5, z = bz + 0.5;
            double[] g = talusGround(t, x, z);
            if (g == null || g[1] <= 0) continue;   // on the flank: the blocks lie past the foot
            // the fall sorted: the small blocks just past the foot, the big ones furthest out
            int[] set = g[1] < 1 ? TOE_NEAR : TOE_FAR;
            int form = set[(int) (h(kk) * set.length)];
            double rad = UndergroundPlan.ROCK_LENGTH[form] / 2;
            if (!fits(x, g[0], z, rad, ground)) continue;
            addBoulder(bx, g[0], bz, form, i, kk);
            got++;
            plan.note("rocks boulders talus");
        }
    }

    void porchBoulders(int i, Notch n) {
        double u = h(UndergroundPlanner.k(164, i));
        int want = u < 0.25 ? 2 : u < 0.65 ? 1 : 0, got = 0;
        for (int k = 0; got < want && k < 8; k++) {
            long kk = UndergroundPlanner.k(165, (long) i << 8 | k);
            int form = SPALL[(int) (h(kk) * SPALL.length)];
            double rad = UndergroundPlan.ROCK_LENGTH[form] / 2, l = n.w() - rad - 0.15, a = 1 + h(kk + 1) * Math.max(0, n.len() - 2);
            if (l - rad < ROUTE) continue;   // the walk up the porch's middle stays open
            double sg = h(kk + 2) < 0.5 ? 1 : -1;
            int bx = (int) Math.floor(n.x() + n.dx() * a - n.dz() * sg * l), bz = (int) Math.floor(n.z() + n.dz() * a + n.dx() * sg * l);
            double x = bx + 0.5, z = bz + 0.5, gy = cutGround(x, z);
            if (Double.isNaN(gy) || !fits(x, gy, z, rad, this::cutGround)) continue;
            addBoulder(bx, gy, bz, form, i, kk);
            got++;
            plan.note("rocks boulders porch");
        }
    }

    /** A great sinkhole's floor (22.8): the fallen roof's blocks and slabs, one per 60 m2, at most 40, on the flat floor. */
    void pitBoulders(int i, Pit pt) {
        double reach = pt.r() * (1 - Pit.LOBE), area = Math.PI * reach * reach, want = area / 60;
        int n = Math.min(40, (int) want + (h(UndergroundPlanner.k(166, i)) < want - (int) want ? 1 : 0)), got = 0;
        double cover = 0;
        for (int t = 0; got < n && t < 8 * n; t++) {
            long kk = UndergroundPlanner.k(167, (long) i << 8 | t);
            int form = BREAKDOWN[(int) (h(kk) * BREAKDOWN.length)];
            double rad = UndergroundPlan.ROCK_LENGTH[form] / 2;
            if (cover + Math.PI * rad * rad > 0.25 * area) continue;
            double r = reach * Math.sqrt(h(kk + 1)), an = 2 * Math.PI * h(kk + 2);
            int bx = (int) Math.floor(pt.x() + Math.cos(an) * r), bz = (int) Math.floor(pt.z() + Math.sin(an) * r);
            double x = bx + 0.5, z = bz + 0.5, gy = cutGround(x, z);
            if (Double.isNaN(gy) || gy > pt.floorY() + 0.8 || !fits(x, gy, z, rad, this::cutGround)) continue;
            addBoulder(bx, gy, bz, form, i, kk);
            cover += Math.PI * rad * rad;
            got++;
            plan.note("rocks boulders great_sinkhole");
        }
    }

    // ------------------------------------------------------------------ loose stones

    /**
     * A patch of loose stones in column (bx, bz) at chance p (clumped by a 4 m noise), cobble and flint at their shares, on
     * the ground the function gives; the column's hash decides, so a column two shapes cover decides once.
     */
    void stone(int shape, int bx, int bz, double p, double cob, double fl, DoubleBinaryOperator ground) {
        long c = col(bx, bz);
        if (taken.contains(c)) return;
        long kk = UndergroundPlanner.k(170, c);
        double clump = 0.35 + 1.3 * UndergroundPlan.valueNoise(bx / 4.0, 0, bz / 4.0, 0x5170);
        if (h(kk) >= p * clump) return;
        double x = bx + 0.5, z = bz + 0.5, gy = ground.applyAsDouble(x, z);
        if (Double.isNaN(gy) || !free(x, z) || !stands(x, gy, z)) return;
        boolean cobble = h(kk + 1) < cob, flint = h(kk + 2) < fl;
        int stones = (int) (h(kk + 3) * 5);
        if (stones == 0 && !cobble && !flint) stones = 1 + (int) (h(kk + 4) * 2);
        plan.rocks.add(new Rock(bx, gy, bz, -1, (int) (h(kk + 5) * 4), stones, cobble, flint, shape, look(shape)));
        taken.add(c);
    }

    void bowlStones(int i, Bowl b) {
        Entrance e = entranceAt.get(at(b.x(), b.z()));
        EntranceKind k = e == null ? null : e.kind();
        double p = k == EntranceKind.COLLAPSE_PIT ? 0.30 : k == EntranceKind.COVER_SINKHOLE ? 0.16 : k == EntranceKind.KARST_WINDOW || k == EntranceKind.GREAT_SINKHOLE ? 0.20 : 0.18;
        double cob = k == EntranceKind.COLLAPSE_PIT ? 0.45 : k == EntranceKind.KARST_WINDOW || k == EntranceKind.GREAT_SINKHOLE ? 0.40 : 0.33, fl = flint(b.owner());
        double inner = inner(b), reach = b.rf() + 3, ext = reach * (1 + UndergroundPlan.BOWL_WOB);
        for (int bz = (int) Math.floor(b.z() - ext); bz <= (int) Math.floor(b.z() + ext); bz++)
            for (int bx = (int) Math.floor(b.x() - ext); bx <= (int) Math.floor(b.x() + ext); bx++) {
                double dx = bx + 0.5 - b.x(), dz = bz + 0.5 - b.z(), r = Math.hypot(dx, dz);
                if (r < inner || r > ext) continue;
                double rho = r / b.scale(dx, dz, r);
                if (rho > reach) continue;
                // thinning up the wall's first 3 m: the transition ring
                double fade = rho <= b.rf() ? 1 : 1 - 0.75 * (rho - b.rf()) / 3;
                stone(i, bx, bz, p * fade, cob, fl, this::cutGround);
            }
    }

    void talusStones(int i, Talus t) {
        double L = (t.apexY() + 0.3 - t.y0()) / t.slope() + 3, fl = flint(t.owner());
        for (int bz = (int) Math.floor(t.z() - L); bz <= (int) Math.floor(t.z() + L); bz++)
            for (int bx = (int) Math.floor(t.x() - L); bx <= (int) Math.floor(t.x() + L); bx++) {
                if (Math.hypot(bx + 0.5 - t.x(), bz + 0.5 - t.z()) > L) continue;
                // rubble on the flanks, thinning out over the 3 m past the foot
                double keep = h(UndergroundPlanner.k(171, col(bx, bz)));
                stone(i, bx, bz, 0.40, 0.6, fl, (px, pz) -> {
                    double[] g = talusGround(t, px, pz);
                    if (g == null) return Double.NaN;
                    return g[1] <= 0 || keep < 0.75 * (1 - g[1] / 2.6) ? g[0] : Double.NaN;
                });
            }
    }

    /** A great sinkhole's stones: thick on its floor, thinner on the ledges of its cliffs and on the shelf round its rim. */
    void pitStones(int i, Pit pt) {
        double e = pt.cliffReach() + Pit.SHELF, fl = flint(pt.owner());
        for (int bz = (int) Math.floor(pt.z() - e); bz <= (int) Math.floor(pt.z() + e); bz++)
            for (int bx = (int) Math.floor(pt.x() - e); bx <= (int) Math.floor(pt.x() + e); bx++) {
                double x = bx + 0.5, z = bz + 0.5, f = pt.floorAt(x, z);
                if (f == Double.POSITIVE_INFINITY || f > pt.collarY() + 0.3) continue;
                double p = f <= pt.floorY() + 0.8 ? 0.22 : f >= pt.collarY() - 0.3 ? 0.15 : 0.12;
                stone(i, bx, bz, p, 0.45, fl, this::cutGround);
            }
    }

    void porchStones(int i, Notch n) {
        double[] bb = n.bounds();
        double fl = flint(n.owner());
        for (int bz = (int) Math.floor(bb[2]); bz <= (int) Math.floor(bb[5]); bz++)
            for (int bx = (int) Math.floor(bb[0]); bx <= (int) Math.floor(bb[3]); bx++) {
                double x = bx + 0.5, z = bz + 0.5, f = n.floorAt(x, z);
                if (f == Double.POSITIVE_INFINITY) continue;
                double a = (x - n.x()) * n.dx() + (z - n.z()) * n.dz(), up = f - (n.floorY() - 0.1 * Math.max(0, a));
                if (up > 2.5) continue;
                stone(i, bx, bz, 0.22 * (1 - 0.75 * up / 2.5), 0.4, fl, this::cutGround);
            }
    }
}
