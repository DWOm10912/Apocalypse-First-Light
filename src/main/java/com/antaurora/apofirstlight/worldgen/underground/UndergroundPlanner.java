package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel.Column;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Anchor;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Background;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Box;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Capsule;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Cave;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveLink;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.District;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.DocKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.DocumentSite;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Ellipsoid;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Entrance;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.EntranceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Env;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Exit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Facility;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.FacilityPart;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Fixture;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Kit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Mine;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Need;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreBody;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pillar;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pitch;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Place;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.PlaceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Platform;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Shape;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.StepKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Succession;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the U1 underground plan of a seed (docs/worldgen/underground_geology_caves_v0_design.md 7–9, 19) after the
 * roads, towns and the home: survey every 512 m cell's geology, then place in a fixed order the research facility,
 * the cave systems (layer 3) with their layer-2 connectors and links, the giant caves and underground canyons (layer 4),
 * the small layer-2 caves, the entrances (linked and dead), the ore districts and bodies, the abandoned mine
 * candidates, the anchors of every pitch, the document points and the succession candidates. Every choice comes from
 * the seed's hash and a sort with a fixed tie-break, never from the order chunks are made in.
 */
public final class UndergroundPlanner {
    static final double CELL = 512, OCC = 64, GAP = 8, MAX_STAGE = 30, SLOPE_MAX = 0.7;

    final GeologyModel g;
    final GeoProtect p;
    final TerrainPlanSurface s;
    final UndergroundPlan plan;
    final long ns;
    final List<Survey> survey = new ArrayList<>();
    final Map<Long, List<Integer>> occ = new HashMap<>();
    /** The rock-gap boxes of each shape, aligned with plan.shapes. */
    final List<List<double[]>> pieces = new ArrayList<>();
    /** Rigging places of the pitches laid so far, by OCC cell: x, y, z (cutsRig). */
    final Map<Long, List<double[]>> rigFloors = new HashMap<>();
    final Map<Integer, Set<Integer>> linked = new HashMap<>();
    final List<Integer> systems = new ArrayList<>(), giants = new ArrayList<>(), canyons = new ArrayList<>();
    int campPlace = -1;

    record Survey(int key, double x, double z, Column c, double[] bands, double kL2, double kL3, int dom, double faultD) {}

    /** A cave node while a system is laid out (committed as an ellipsoid and a place). */
    static final class Node {
        double x, z, floor, rx, ry, rz;
        boolean chamber;
        int place = -1;

        Node(double x, double z, double floor, double rx, double ry, double rz, boolean chamber) {
            this.x = x;
            this.z = z;
            this.floor = floor;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.chamber = chamber;
        }

        Ellipsoid shape(int owner) { return new Ellipsoid(owner, x, floor + 0.6 * ry, z, rx, ry, rz, floor); }

        double r() { return Math.min(rx, rz); }

        double roof() { return floor + 1.6 * ry; }
    }

    UndergroundPlanner(GeologyModel g, GeoProtect p) {
        this.g = g;
        this.p = p;
        this.s = g.surface;
        this.plan = new UndergroundPlan(g.seed, g.minY);
        this.ns = PlanNoise.mix(g.seed ^ 0x0C47_E5L);
    }

    public static UndergroundPlan build(GeologyModel g, GeoProtect p) { return new UndergroundPlanner(g, p).run(); }

    UndergroundPlan run() {
        long t = System.nanoTime();
        survey();
        t = time("survey", t);
        facility();
        t = time("facility", t);
        // canyons first: the rare wonders take their space before the systems fill the karst (7.6)
        canyons();
        systems();
        connectors();
        systemLinks();
        t = time("systems", t);
        giants();
        t = time("giant", t);
        smallCaves();
        t = time("small", t);
        systemEntrances();
        greatSinkholes();
        bulkEntrances();
        giantRoutes();
        t = time("entrances", t);
        ores();
        mines();
        t = time("ores_mines", t);
        plan.index();
        rig();
        t = time("index_rig", t);
        documents();
        successions();
        time("docs_succession", t);
        return plan;
    }

    long time(String k, long t0) {
        long t1 = System.nanoTime();
        plan.times.put(k, (t1 - t0) / 1_000_000);
        return t1;
    }

    // ------------------------------------------------------------------ helpers

    double h(long k) { return PlanNoise.hash01(ns, k); }

    /** The deepest single shaft of a pitch series, m: the rope of one stage then stays within the 60 m kit (19.2). */
    static final double SERIES = 45;

    static long k(int a, long b) { return ((long) a << 40) ^ b; }

    boolean land(double x, double z) { return !s.waterAt(x, z) && g.map.isLand(x, z); }

    Cave cave(CaveKind kind, int layer, double x, double y, double z, double waterY, RockType host) {
        Cave c = new Cave(plan.caves.size(), kind, layer, x, y, z, waterY, host);
        plan.caves.add(c);
        return c;
    }

    int add(Shape sh) {
        int i = plan.shapes.size();
        plan.shapes.add(sh);
        plan.caves.get(sh.owner()).shapes.add(i);
        List<double[]> pc = pieces(sh);
        pieces.add(pc);
        for (double[] b : pc)
            for (int cz = (int) Math.floor(b[2] / OCC); cz <= (int) Math.floor(b[5] / OCC); cz++)
                for (int cx = (int) Math.floor(b[0] / OCC); cx <= (int) Math.floor(b[3] / OCC); cx++) {
                    List<Integer> l = occ.computeIfAbsent(UndergroundPlan.cellKey(cx, cz), q -> new ArrayList<>());
                    if (l.isEmpty() || l.get(l.size() - 1) != i) l.add(i);
                }
        return i;
    }

    /** A shape as boxes for the rock-gap test: a capsule in pieces of at most 16 m along its axis, the rest as its bounds. */
    static List<double[]> pieces(Shape sh) {
        if (!(sh instanceof Capsule c) || c.length() <= 16) return List.of(sh.bounds());
        int k = (int) Math.ceil(c.length() / 16);
        List<double[]> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            double f0 = (double) i / k, f1 = (double) (i + 1) / k;
            double x0 = c.ax() + (c.bx() - c.ax()) * f0, x1 = c.ax() + (c.bx() - c.ax()) * f1;
            double y0 = c.ay() + (c.by() - c.ay()) * f0, y1 = c.ay() + (c.by() - c.ay()) * f1;
            double z0 = c.az() + (c.bz() - c.az()) * f0, z1 = c.az() + (c.bz() - c.az()) * f1;
            out.add(new double[]{Math.min(x0, x1) - c.rh(), Math.min(y0, y1) - c.rv(), Math.min(z0, z1) - c.rh(),
                    Math.max(x0, x1) + c.rh(), Math.max(y0, y1) + c.rv(), Math.max(z0, z1) + c.rh()});
        }
        return out;
    }

    static boolean overlap(double[] a, double[] c, double gap) {
        return a[0] - gap < c[3] && a[3] + gap > c[0] && a[1] - gap < c[4] && a[4] + gap > c[1] && a[2] - gap < c[5] && a[5] + gap > c[2];
    }

    /**
     * Whether a new passage or shaft would hollow out the floor of a rigging place laid earlier (19.2), whatever cave
     * owns it: an entrance throat cut down through a pitch series would leave its platforms and anchors in the air.
     */
    boolean cutsRig(Shape sh) {
        for (double[] b : pieces(sh))
            for (int cz = (int) Math.floor(b[2] / OCC); cz <= (int) Math.floor(b[5] / OCC); cz++)
                for (int cx = (int) Math.floor(b[0] / OCC); cx <= (int) Math.floor(b[3] / OCC); cx++) {
                    List<double[]> l = rigFloors.get(UndergroundPlan.cellKey(cx, cz));
                    if (l == null) continue;
                    for (double[] q : l)
                        if (sh.inside(q[0], q[1] - 0.6, q[2]) || sh.inside(q[0], q[1] - 2, q[2])) {
                            plan.note("passage would cut a rig");
                            return true;
                        }
                }
        return false;
    }

    /** Whether a point is inside a shape laid so far (the plan's index is built only after planning; pillars ignored). */
    boolean hollow(double x, double y, double z) {
        List<Integer> l = occ.get(UndergroundPlan.cellKey((int) Math.floor(x / OCC), (int) Math.floor(z / OCC)));
        if (l == null) return false;
        for (int i : l) {
            Shape o = plan.shapes.get(i);
            if (o instanceof Pillar) continue;
            double[] b = o.bounds();
            if (x < b[0] || y < b[1] || z < b[2] || x > b[3] || y > b[4] || z > b[5]) continue;
            if (o.inside(x, y, z)) return true;
        }
        return false;
    }

    int throat(Shape sh) {
        int i = add(sh);
        plan.throats.set(i);
        return i;
    }

    void link(int a, int b, String kind, int step) {
        plan.links.add(new CaveLink(a, b, kind, step));
        linked.computeIfAbsent(a, q -> new HashSet<>()).add(b);
        linked.computeIfAbsent(b, q -> new HashSet<>()).add(a);
    }

    /** Whether a shape keeps GAP of rock from every other cave's shapes (box pieces), owners in ok excepted. */
    boolean clear(Shape sh, int owner, Set<Integer> ok) {
        for (double[] b : pieces(sh))
            for (int cz = (int) Math.floor((b[2] - GAP) / OCC); cz <= (int) Math.floor((b[5] + GAP) / OCC); cz++)
                for (int cx = (int) Math.floor((b[0] - GAP) / OCC); cx <= (int) Math.floor((b[3] + GAP) / OCC); cx++) {
                    List<Integer> l = occ.get(UndergroundPlan.cellKey(cx, cz));
                    if (l == null) continue;
                    for (int i : l) {
                        Shape o = plan.shapes.get(i);
                        if (o.owner() == owner || (ok != null && ok.contains(o.owner()))) continue;
                        for (double[] c : pieces.get(i)) if (overlap(b, c, GAP)) return false;
                    }
                }
        return true;
    }

    /** The cave layer of a shape: its own rules, the floor's kept rock, and not above the layer's band. */
    boolean fits(Shape sh, int layer, int owner, Set<Integer> ok) {
        double[] b = sh.bounds();
        if (b[1] < g.minY + GeologyModel.FLOOR_KEEP) return false;
        if (!p.allows(sh, layer)) return false;
        if (layer >= 2) {
            // the roof stays T1 + 6 m under Hs (layer 1 has no caves, 7.5): ellipsoids and boxes at their centre and
            // corners, capsules along their centreline
            List<double[]> pts = new ArrayList<>();
            if (sh instanceof Capsule c) {
                int k = Math.max(1, (int) Math.ceil(Math.hypot(c.bx() - c.ax(), c.bz() - c.az()) / 48));
                for (int i = 0; i <= k; i++) {
                    double f = (double) i / k;
                    pts.add(new double[]{c.ax() + (c.bx() - c.ax()) * f, c.az() + (c.bz() - c.az()) * f, c.ay() + (c.by() - c.ay()) * f + c.rv()});
                }
            } else {
                for (double[] q : new double[][]{{(b[0] + b[3]) / 2, (b[2] + b[5]) / 2}, {b[0], b[2]}, {b[3], b[2]}, {b[0], b[5]}, {b[3], b[5]}}) {
                    double top = b[4];
                    if (sh instanceof Ellipsoid e) { top = e.roofAt(q[0], q[1]); if (Double.isNaN(top)) continue; }
                    pts.add(new double[]{q[0], q[1], top});
                }
            }
            for (double[] q : pts) {
                Column c = g.column(q[0], q[1]);
                double roofMax = c.hs - c.t1 - 6;
                if (layer >= 3) roofMax = Math.min(roofMax, c.hs - 40);
                if (layer >= 4) roofMax = Math.min(roofMax, c.hs - 120);
                if (q[2] > roofMax) return false;
            }
        }
        return clear(sh, owner, ok);
    }

    static Platform floorPlatform(double x, double y, double z, double half, double headroom) {
        return new Platform(x, y, z, Math.max(0.8, half), Math.max(0.8, half), headroom);
    }

    int nodePlace(Node n, int owner) {
        PlaceKind k = n.chamber ? PlaceKind.CHAMBER : PlaceKind.JUNCTION;
        n.place = plan.addPlace(k, n.x, n.floor, n.z, owner, floorPlatform(n.x, n.floor, n.z, 0.3 * n.r(), 1.6 * n.ry));
        plan.caves.get(owner).places.add(n.place);
        return n.place;
    }

    /** A walking step between two cave places by their floors: walk, slope, crawl, wade or sump against the water. */
    int walk(int a, int b, double rh, double rv, double waterY, boolean dark) {
        Place pa = plan.places.get(a), pb = plan.places.get(b);
        double dy = Math.abs(pb.y() - pa.y());
        Need n = dark ? Need.LIGHT : Need.NONE;
        StepKind k = dy <= 1 ? StepKind.WALK : dy <= 4 && Math.hypot(pb.x() - pa.x(), pb.z() - pa.z()) < 4 ? StepKind.STEP : StepKind.SLOPE;
        int haul = k == StepKind.WALK ? 0 : 1;
        if (rh < 1.0 || rv < 0.95) {
            k = StepKind.CRAWL;
            n = new Need(dark, 0, 0, false, true, false);
            haul = 3;
        }
        double roof = Math.min(pa.y(), pb.y()) + 2 * rv;
        if (waterY >= roof - 0.3) {
            k = StepKind.SUMP;
            n = new Need(dark, 0, 0, false, false, true);
        } else if (waterY > Math.max(pa.y(), pb.y()) + 0.2) haul = Math.max(haul, 2);
        return plan.addStep(a, b, k, n, n, null, haul);
    }

    /**
     * A vertical stage from top to bottom (19.2): rebelay ledges every MAX_STAGE m cut as niches beside the shaft, the
     * rope or fixture each stage needs (rope for the whole pitch, two anchors per rigging point), the V3 climb where it
     * is short and the rock competent. Anchors are set later by {@link #rig()}.
     */
    int pitch(int top, int bottom, double ax, double az, double rh, int owner, boolean dark, Fixture fx, long salt, int slopeAlt) {
        Place pt = plan.places.get(top), pb = plan.places.get(bottom);
        double height = pt.y() - pb.y();
        int stages = Math.max(1, (int) Math.ceil(height / MAX_STAGE));
        double ang = 2 * Math.PI * h(salt);
        double dx = Math.cos(ang), dz = Math.sin(ang);
        List<Integer> rigs = new ArrayList<>();
        rigs.add(top);
        for (int i = 1; i < stages; i++) {
            double y = pt.y() - height * i / stages;
            double cx = ax + dx * (rh + 1.5), cz = az + dz * (rh + 1.5);
            throat(new Box(owner, ax + dx * (rh + 1.0), y + 1.3, az + dz * (rh + 1.0), 2.0, 1.3, 1.5, ang));
            int lp = plan.addPlace(PlaceKind.LEDGE, cx, y, cz, owner, new Platform(cx, y, cz, 1.0, 1.2, 2.6));
            rigs.add(lp);
        }
        rigs.add(bottom);
        for (int rp : rigs) {
            Place q = plan.places.get(rp);
            rigFloors.computeIfAbsent(UndergroundPlan.cellKey((int) Math.floor(q.x() / OCC), (int) Math.floor(q.z() / OCC)), c -> new ArrayList<>())
                    .add(new double[]{q.x(), q.y(), q.z()});
        }
        int rope = (int) Math.ceil(height * 1.1 + 3 * stages);
        Need n = switch (fx) {
            case NONE -> new Need(dark, rope, 2 * stages, false, false, false);
            default -> dark ? Need.LIGHT : Need.NONE;
        };
        RockType rock = g.rockAt(ax, (pt.y() + pb.y()) / 2, az);
        Need alt = fx == Fixture.NONE && height <= 15 && rock != null && rock.anchor ? new Need(dark, 0, 0, true, false, false) : null;
        StepKind k = switch (fx) {
            case FIXED_ROPE -> StepKind.FIXED_ROPE;
            case ROPE_LADDER -> StepKind.ROPE_LADDER;
            case FIXED_LADDER -> StepKind.FIXED_LADDER;
            default -> StepKind.ROPE;
        };
        int haul = height <= 15 ? 2 : 3;
        List<Integer> steps = new ArrayList<>();
        for (int i = 0; i + 1 < rigs.size(); i++) steps.add(plan.addStep(rigs.get(i), rigs.get(i + 1), k, n, n, alt, haul));
        Pitch pc = new Pitch(plan.pitches.size(), steps, height, top, bottom, rigs, new ArrayList<>(), ax, az, rh, fx,
                slopeAlt, fx == Fixture.NONE && slopeAlt < 0, haul, owner, dark);
        plan.pitches.add(pc);
        return pc.id();
    }

    /** The fixture a natural pitch has (V1 content): old survey or caving-club ropes on some, a rope ladder on a few. */
    Fixture naturalFixture(long salt) {
        double r = h(salt);
        return r < 0.25 ? Fixture.FIXED_ROPE : r < 0.35 ? Fixture.ROPE_LADDER : Fixture.NONE;
    }

    /**
     * Connects an upper place to a lower one (19.2): one passage when the drop is small or the slope gentle enough to
     * walk (SLOPE_MAX), else a vertical shaft at the upper end (offset into a chamber's floor, or at the end of a short
     * passage) and a passage at the lower level. Shapes are tested first (layer rules unless throat) and nothing is
     * added unless all pass. Returns the first new step, or -1.
     */
    int connect(int upper, double upperR, int lower, int owner, int layer, boolean throat, boolean dark, Set<Integer> ok, double rh, long salt, Fixture fx) {
        Place u = plan.places.get(upper), l = plan.places.get(lower);
        double hx = l.x() - u.x(), hz = l.z() - u.z(), horiz = Math.hypot(hx, hz), dy = u.y() - l.y();
        double dx = horiz < 1e-6 ? 1 : hx / horiz, dz = horiz < 1e-6 ? 0 : hz / horiz;
        double rv = rh;
        double waterY = plan.caves.get(owner).waterY;
        if (dy <= 1 || dy / Math.max(1e-6, horiz) <= SLOPE_MAX) {
            Capsule c = new Capsule(owner, u.x(), u.y() + 0.7 * rv, u.z(), l.x(), l.y() + 0.7 * rv, l.z(), rh, rv);
            if (!(throat ? clear(c, owner, ok) && p.allows(c, layer) : fits(c, layer, owner, ok))) return -1;
            if (cutsRig(c)) return -1;
            if (throat) throat(c); else add(c);
            plan.caves.get(owner).passageLength += c.length();
            return walk(upper, lower, rh, rv, waterY, dark);
        }
        double off = upperR >= 5 ? 0.65 * upperR : upperR + rh + 2.5;
        // a deep drop is a pitch series (19.2): shafts of at most SERIES m, each landing in a short hop passage that
        // swings alternately left and right of the heading to the next shaft head, the way alpine shafts step down
        int n = Math.max(1, (int) Math.ceil(dy / SERIES));
        double[] x = new double[n], z = new double[n], top = new double[n + 1];
        double[][] hop = new double[n][];
        x[0] = u.x() + dx * off;
        z[0] = u.z() + dz * off;
        top[0] = u.y();
        for (int i = 1; i <= n; i++) top[i] = u.y() - dy * i / n;
        for (int i = 1; i < n; i++) {
            double sw = (i % 2 == 1 ? 1 : -1) * (0.9 + 0.4 * h(salt + 40 + i));
            double ox = dx * Math.cos(sw) - dz * Math.sin(sw), oz = dx * Math.sin(sw) + dz * Math.cos(sw);
            double hl = 2 * rh + 9 + 8 * h(salt + 60 + i);
            x[i] = x[i - 1] + ox * hl;
            z[i] = z[i - 1] + oz * hl;
            hop[i] = new double[]{ox, oz};
        }
        // every rigging floor of the series must stand on rock the cave already has: a series dropping through its own
        // system's chamber would leave its platforms and anchors hanging in that chamber's air
        for (int i = 0; i < n; i++) {
            double ddx = i == 0 ? dx : hop[i][0], ddz = i == 0 ? dz : hop[i][1];
            if ((i > 0 || upperR < 5) && hollow(x[i] - ddx * (rh + 1.0), top[i] - 0.6, z[i] - ddz * (rh + 1.0))) {
                plan.note("series over a void");
                return -1;
            }
            if (i < n - 1 && hollow(x[i], top[i + 1] - 0.6, z[i])) {
                plan.note("series over a void");
                return -1;
            }
        }
        List<Shape> shs = new ArrayList<>();
        if (upperR < 5) shs.add(new Capsule(owner, u.x(), u.y() + 0.7 * 1.6, u.z(), x[0] + dx * rh, u.y() + 0.7 * 1.6, z[0] + dz * rh, 1.6, 1.6));
        for (int i = 0; i < n; i++) {
            double sTop = i == 0 ? u.y() + (upperR >= 5 ? 0.5 : 2) : top[i] + 2;
            shs.add(new Capsule(owner, x[i], sTop, z[i], x[i], top[i + 1] + rh, z[i], rh, rh));
            if (i + 1 < n) shs.add(new Capsule(owner, x[i], top[i + 1] + 0.7 * 1.8, z[i], x[i + 1], top[i + 1] + 0.7 * 1.8, z[i + 1], 1.8, 1.8));
        }
        double sx = x[n - 1], sz = z[n - 1];
        double rest = Math.hypot(l.x() - sx, l.z() - sz);
        boolean tail = rest > rh + 1;
        if (tail) shs.add(new Capsule(owner, sx, l.y() + 0.7 * 1.8, sz, l.x(), l.y() + 0.7 * 1.8, l.z(), 1.8, 1.8));
        for (Shape sh : shs) if (!(throat ? clear(sh, owner, ok) && p.allows(sh, layer) : fits(sh, layer, owner, ok))) return -1;
        for (Shape sh : shs) if (cutsRig(sh)) return -1;
        for (Shape sh : shs) { if (throat) throat(sh); else add(sh); if (sh instanceof Capsule c) plan.caves.get(owner).passageLength += c.length(); }
        int first = -1, prev = upper;
        for (int i = 0; i < n; i++) {
            double ddx = i == 0 ? dx : hop[i][0], ddz = i == 0 ? dz : hop[i][1];
            double tx = x[i] - ddx * (rh + 1.0), tz = z[i] - ddz * (rh + 1.0);
            int topRig = plan.addPlace(PlaceKind.PITCH_TOP, tx, top[i], tz, owner, new Platform(tx, top[i], tz, 1.0, 1.0, 2.4));
            int w = walk(prev, topRig, i == 0 ? 1.6 : 1.8, i == 0 ? 1.6 : 1.8, waterY, dark);
            if (first < 0) first = w;
            boolean last = i == n - 1;
            int bottom = last && !tail ? lower : plan.addPlace(PlaceKind.PITCH_BOTTOM, x[i], top[i + 1], z[i], owner,
                    new Platform(x[i], top[i + 1], z[i], Math.min(1.2, rh * 0.6), Math.min(1.2, rh * 0.6), 2.4));
            pitch(topRig, bottom, x[i], z[i], rh, owner, dark, fx, salt + 97L * i, -1);
            prev = bottom;
        }
        if (tail) walk(prev, lower, 1.8, 1.8, waterY, dark);
        return first;
    }

    // ------------------------------------------------------------------ survey

    void survey() {
        double o = s.gridOrigin, size = s.gridN * TerrainPlanSurface.CELL;
        int m = (int) Math.ceil(size / CELL);
        double[] fd = new double[1];
        for (int r = 0; r < m; r++) for (int c = 0; c < m; c++) {
            int key = r * m + c;
            double x = o + (c + 0.25 + 0.5 * h(k(1, key))) * CELL, z = o + (r + 0.25 + 0.5 * h(k(2, key))) * CELL;
            if (!land(x, z)) continue;
            Column col = g.column(x, z);
            double[] b = g.bands(col);
            g.nearestFault(x, z, 1e9, fd);
            survey.add(new Survey(key, x, z, col, b, g.karstShare(col, b[2], b[1]), g.karstShare(col, b[3], b[2]), g.dominantProvince(x, z), fd[0]));
        }
    }

    // ------------------------------------------------------------------ facility

    /**
     * The one research facility candidate (19.5): in competent rock under the hills or the range, at least 3 km from a
     * town and 4 km from the home, 300 m–2.5 km from a road (a service road it can have), its hidden entrance on a hillside
     * ≥ 150 m from the road works, the lab ≥ 60 m under the ground.
     */
    void facility() {
        List<Object[]> cands = new ArrayList<>();
        for (Survey v : survey) {
            double hard = v.c.w[GeologyModel.MOUNTAIN] + v.c.w[GeologyModel.HILLS];
            if (hard < 0.5) continue;
            if (p.townDistance(v.x, v.z) < 3000 || p.homeDistance(v.x, v.z) < 4000) continue;
            double rd = p.roadDistance(v.x, v.z);
            if (rd < 300 || rd > 2500) continue;
            RockType r = g.rockAt(v.c, v.c.h - 90);
            if (r == null || !r.anchor) continue;
            double rs = r == RockType.GRANITE || r == RockType.GNEISS ? 1 : r == RockType.SANDSTONE ? 0.8 : r == RockType.DOLOMITE ? 0.6 : 0.4;
            // a hillside portal within 400 m: the steepest allowed spot
            double[] bp = null;
            double bs = 0.18;
            for (int ring = 100; ring <= 400; ring += 50) for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8 + h(k(40, v.key)) ;
                double x = v.x + Math.cos(ang) * ring, z = v.z + Math.sin(ang) * ring;
                if (!land(x, z) || p.roadDistance(x, z) < GeoProtect.ENTRANCE_ROAD || !p.entranceAllowed(EntranceKind.FACILITY, x, z)) continue;
                double sl = s.slopeAt(x, z);
                if (sl > bs) { bs = sl; bp = new double[]{x, z}; }
            }
            if (bp == null) continue;
            double score = rs + 0.6 * Math.min(1, bs) + 0.3 * h(k(41, v.key)) - rd / 6000;
            cands.add(new Object[]{v, bp, score});
        }
        // the best site that fits: the cover over the lab and the C works' rules decide, the next candidate otherwise
        cands.sort(Comparator.comparingDouble((Object[] o) -> -(double) o[2]).thenComparingInt(o -> ((Survey) o[0]).key));
        plan.notes.put("facility candidates", cands.size());
        for (Object[] o : cands) if (facilityAt((Survey) o[0], (double[]) o[1])) return;
    }

    /** Lays out the facility at a site, or leaves nothing behind and returns false (no cover, or a C work in the way). */
    boolean facilityAt(Survey best, double[] portal) {
        int places0 = plan.places.size(), caves0 = plan.caves.size();
        Cave cv = cave(CaveKind.BUILT, 3, best.x, best.c.h - 90, best.z, Double.NEGATIVE_INFINITY, g.rockAt(best.c, best.c.h - 90));
        cv.label = "facility";
        double px = portal[0], pz = portal[1], py = s.heightAt(px, pz);
        // the tunnel heads into the hill: towards the higher smoothed ground
        double gx = g.hs(px + 40, pz) - g.hs(px - 40, pz), gz = g.hs(px, pz + 40) - g.hs(px, pz - 40);
        double yaw = Math.atan2(gz, gx);
        double ux = Math.cos(yaw), uz = Math.sin(yaw);
        double labY = py - 20;
        double len = 250;
        for (; len <= 700; len += 50) {
            double lx = px + ux * (len + 30), lz = pz + uz * (len + 30);
            if (s.heightAt(lx, lz) - (labY + 12) >= 60 && s.heightAt(lx + ux * 60, lz + uz * 60) - (labY + 8) >= 60) break;
        }
        if (len > 700) { plan.note("facility no cover"); plan.caves.remove(caves0); return false; }
        double ex = px + ux * len, ez = pz + uz * len;
        List<FacilityPart> parts = new ArrayList<>();
        List<Shape> under = new ArrayList<>();
        int portalPlace = plan.addPlace(PlaceKind.SURFACE, px - ux * 8, py, pz - uz * 8, cv.id, new Platform(px - ux * 8, py, pz - uz * 8, 4, 4, 50));
        Box entry = new Box(cv.id, px, py + 3, pz, 6, 3, 4, yaw);
        parts.add(new FacilityPart("hidden_entrance", entry, portalPlace, true));
        Capsule tunnel = new Capsule(cv.id, px, py + 2.1, pz, ex, labY + 2.1, ez, 3, 3);
        under.add(tunnel);
        int tunnelEnd = plan.addPlace(PlaceKind.FACILITY, ex, labY, ez, cv.id, new Platform(ex, labY, ez, 2, 2, 5));
        parts.add(new FacilityPart("haul_tunnel", tunnel, tunnelEnd, false));
        double lx = ex + ux * 30, lz = ez + uz * 30;
        Box lab = new Box(cv.id, lx, labY + 6, lz, 30, 6, 10, yaw);
        under.add(lab);
        int labPlace = plan.addPlace(PlaceKind.FACILITY, lx, labY, lz, cv.id, new Platform(lx, labY, lz, 8, 6, 11));
        parts.add(new FacilityPart("laboratory", lab, labPlace, false));
        double ix = lx + ux * 46, iz = lz + uz * 46;
        Box iso = new Box(cv.id, ix, labY + 4, iz, 15, 4, 10, yaw);
        under.add(iso);
        int isoPlace = plan.addPlace(PlaceKind.FACILITY, ix, labY, iz, cv.id, new Platform(ix, labY, iz, 4, 4, 7));
        parts.add(new FacilityPart("isolation", iso, isoPlace, false));
        double nx = -uz, nz = ux;
        double arx = lx + nx * 18, arz = lz + nz * 18;
        Box archive = new Box(cv.id, arx, labY + 2.5, arz, 6, 2.5, 5, yaw);
        under.add(archive);
        int arPlace = plan.addPlace(PlaceKind.FACILITY, arx, labY, arz, cv.id, new Platform(arx, labY, arz, 2, 2, 4.5));
        parts.add(new FacilityPart("sealed_archive", archive, arPlace, false));
        double elx = lx - nx * 16, elz = lz - nz * 16, elTop = s.heightAt(elx, elz);
        Capsule elevator = new Capsule(cv.id, elx, elTop + 1, elz, elx, labY + 3, elz, 3, 3);
        under.add(elevator);
        // the shaft stands beside the lab: a short gallery from the lab wall to the cage landing
        under.add(new Capsule(cv.id, lx - nx * 9, labY + 1.5, lz - nz * 9, elx, labY + 1.5, elz, 1.5, 1.5));
        int elBottom = plan.addPlace(PlaceKind.FACILITY, elx + nx * 4, labY, elz + nz * 4, cv.id, new Platform(elx + nx * 4, labY, elz + nz * 4, 1.5, 1.5, 4));
        int elHead = plan.addPlace(PlaceKind.SURFACE, elx + 7, elTop, elz, cv.id, new Platform(elx + 7, elTop, elz, 3, 3, 50));
        parts.add(new FacilityPart("equipment_shaft", elevator, elBottom, false));
        parts.add(new FacilityPart("shaft_headframe", new Box(cv.id, elx, elTop + 5, elz, 6, 5, 6, yaw), elHead, true));
        double qx = ix + ux * 20, qz = iz + uz * 20, qTop = s.heightAt(qx, qz);
        Capsule escape = new Capsule(cv.id, qx, qTop + 1, qz, qx, labY + 1.5, qz, 1.5, 1.5);
        under.add(escape);
        under.add(new Capsule(cv.id, ix + ux * 14, labY + 1.5, iz + uz * 14, qx, labY + 1.5, qz, 1.5, 1.5));
        int esBottom = plan.addPlace(PlaceKind.FACILITY, qx - ux * 3, labY, qz - uz * 3, cv.id, new Platform(qx - ux * 3, labY, qz - uz * 3, 1.2, 1.2, 3));
        int esTop = plan.addPlace(PlaceKind.SURFACE, qx + 4, qTop, qz, cv.id, new Platform(qx + 4, qTop, qz, 2, 2, 50));
        parts.add(new FacilityPart("emergency_exit", escape, esBottom, false));
        parts.add(new FacilityPart("emergency_hatch", new Box(cv.id, qx, qTop + 1.5, qz, 2, 1.5, 2, yaw), esTop, true));
        // the survey camp: 150-300 m from the portal across the slope, on the most level allowed spot
        double[] camp = null;
        double cs = 1e9;
        for (int a = 0; a < 12; a++) for (int ring = 150; ring <= 300; ring += 50) {
            double ang = yaw + Math.PI / 2 + a * Math.PI / 6;
            double cx = px + Math.cos(ang) * ring, cz = pz + Math.sin(ang) * ring;
            if (!land(cx, cz) || !p.entranceAllowed(EntranceKind.FISSURE, cx, cz)) continue;
            double sl = s.slopeAt(cx, cz);
            if (sl < cs) { cs = sl; camp = new double[]{cx, cz}; }
        }
        // the underground parts must keep the C works' rules (as a layer-3 object) before they protect themselves
        for (Shape sh : under) if (p.violation(sh, 3) != null) {
            plan.note("facility protect " + p.violation(sh, 3));
            while (plan.places.size() > places0) plan.places.remove(plan.places.size() - 1);
            plan.caves.remove(caves0);
            return false;
        }
        for (Shape sh : under) throat(sh);
        add(entry);
        plan.throats.set(plan.shapes.size() - 1);
        // walks and ladders (no power: the cage is dead, its shaft has a ladderway; the escape shaft is a ladderway)
        plan.addStep(portalPlace, tunnelEnd, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 1);
        plan.addStep(tunnelEnd, labPlace, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 0);
        plan.addStep(labPlace, isoPlace, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 0);
        plan.addStep(labPlace, arPlace, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 0);
        plan.addStep(labPlace, elBottom, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 0);
        plan.addStep(isoPlace, esBottom, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 0);
        int elRig = plan.addPlace(PlaceKind.PITCH_TOP, elx + 4, elTop, elz, cv.id, new Platform(elx + 4, elTop, elz, 1.5, 1.5, 50));
        plan.addStep(elHead, elRig, StepKind.WALK, Need.NONE, Need.NONE, null, 0);
        pitch(elRig, elBottom, elx, elz, 3, cv.id, true, Fixture.FIXED_LADDER, k(42, 1), -1);
        int esRig = plan.addPlace(PlaceKind.PITCH_TOP, qx + 2.2, qTop, qz, cv.id, new Platform(qx + 2.2, qTop, qz, 1.0, 1.0, 50));
        plan.addStep(esTop, esRig, StepKind.WALK, Need.NONE, Need.NONE, null, 0);
        pitch(esRig, esBottom, qx, qz, 1.5, cv.id, true, Fixture.FIXED_LADDER, k(42, 2), -1);
        int link = -1;
        if (camp != null) {
            double cy = s.heightAt(camp[0], camp[1]);
            campPlace = plan.addPlace(PlaceKind.CAMP, camp[0], cy, camp[1], cv.id, new Platform(camp[0], cy, camp[1], 12, 12, 50));
            parts.add(new FacilityPart("survey_camp", new Box(cv.id, camp[0], cy + 2, camp[1], 15, 2, 15, 0), campPlace, true));
            // the rock passage: a natural fissure the survey found from the camp down to the archive's wall (fixed rope)
            Column cc = g.column(camp[0], camp[1]);
            Cave fis = cave(CaveKind.FISSURE, 2, camp[0], labY, camp[1], g.waterTable(cc), g.rockAt(cc, labY + 10));
            fis.label = "facility rock passage";
            link = fis.id;
            int rim = plan.addPlace(PlaceKind.SURFACE, camp[0] + 3, cy, camp[1], fis.id, new Platform(camp[0] + 3, cy, camp[1], 2, 2, 50));
            plan.addStep(campPlace, rim, StepKind.WALK, Need.NONE, Need.NONE, null, 0);
            int breach = plan.addPlace(PlaceKind.CHAMBER, arx - nx * 0 + (camp[0] - arx) * 0.04, labY, arz + (camp[1] - arz) * 0.04, fis.id,
                    new Platform(arx + (camp[0] - arx) * 0.04, labY, arz + (camp[1] - arz) * 0.04, 1.2, 1.2, 3));
            Set<Integer> ok = Set.of(cv.id);
            int st = connect(rim, 0.5, breach, fis.id, 2, true, true, ok, 1.2, k(43, 1), Fixture.FIXED_ROPE);
            if (st >= 0) {
                plan.addStep(breach, arPlace, StepKind.CRAWL, new Need(true, 0, 0, false, true, false), new Need(true, 0, 0, false, true, false), null, 3);
                link(cv.id, fis.id, "facility_rock_passage", st);
            } else link = -1;
        }
        // the service road: from the nearest C road point to the portal
        double[] road = null;
        double rdBest = 1e18;
        for (double[] line : p.roadLines) for (int i = 0; i + 2 < line.length; i += 3) {
            double d = Math.hypot(line[i] - px, line[i + 1] - pz);
            if (d < rdBest) { rdBest = d; road = new double[]{line[i], line[i + 2], line[i + 1]}; }
        }
        double[] service = road == null ? new double[0] : new double[]{road[0], road[1], road[2], px, py, pz};
        double grade = road == null ? 0 : Math.abs(py - road[1]) / Math.max(1, rdBest);
        List<Box> prot = new ArrayList<>();
        for (FacilityPart fp : parts) {
            double[] b = fp.shape().bounds();
            double m = GeoProtect.FACILITY_MARGIN;
            prot.add(new Box(cv.id, (b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2, (b[3] - b[0]) / 2 + m, (b[4] - b[1]) / 2 + m, (b[5] - b[2]) / 2 + m, 0));
        }
        p.addFacility(prot);
        plan.facility = new Facility(cv.id, lx, lz, yaw, parts, service, grade, prot, cv.host, link);
        cv.label = "facility";
        cv.places.add(portalPlace);
        cv.places.add(labPlace);
        return true;
    }

    // ------------------------------------------------------------------ cave systems (layer 3)

    double bestKarstY(Column c, double lo, double hi, double pref) {
        double best = Double.NaN, bd = 1e9;
        double runStart = Double.NaN;
        for (double y = lo; y <= hi + 1e-6; y += 3) {
            boolean k = g.karst(c, y) >= 0.5;
            if (k && Double.isNaN(runStart)) runStart = y;
            if ((!k || y + 3 > hi) && !Double.isNaN(runStart)) {
                double end = k ? y : y - 3;
                if (end - runStart >= 6) {
                    double mid = (runStart + end) / 2, d = Math.abs(mid - pref);
                    if (d < bd) { bd = d; best = mid; }
                }
                runStart = Double.NaN;
            }
        }
        return best;
    }

    void systems() {
        List<double[]> accepted = new ArrayList<>();
        List<Survey> karst = new ArrayList<>();
        for (Survey v : survey) if (v.kL3 >= 0.3) karst.add(v);
        karst.sort(Comparator.comparingDouble((Survey v) -> -(v.kL3 + 0.35 * h(k(3, v.key)))).thenComparingInt(v -> v.key));
        for (Survey v : karst) {
            double spacing = (700 + 600 * h(k(4, v.key))) * (1 + Math.max(0, v.c.soil - 4) * 0.45);
            if (crowded(accepted, v.x, v.z, spacing)) continue;
            if (system(v, true)) accepted.add(new double[]{v.x, v.z, spacing});
        }
        List<Survey> frac = new ArrayList<>();
        for (Survey v : survey) {
            if (v.kL3 >= 0.3) continue;
            double hard = v.c.w[GeologyModel.MOUNTAIN] + v.c.w[GeologyModel.HILLS] + v.c.w[GeologyModel.PLATEAU];
            RockType r = g.rockAt(v.c, (v.bands[2] + v.bands[3]) / 2);
            if (!(v.faultD < 900 || (hard >= 0.3 && r != null && r.hard()))) continue;
            frac.add(v);
            plan.note("fracture candidates");
        }
        frac.sort(Comparator.comparingDouble((Survey v) -> -((v.faultD < 900 ? 1 : 0.5) + 0.4 * h(k(5, v.key)))).thenComparingInt(v -> v.key));
        int nf = 0;
        for (Survey v : frac) {
            if (nf >= 40) break;
            double spacing = 600 + 700 * h(k(6, v.key));
            if (crowded(accepted, v.x, v.z, spacing)) { plan.note("fracture crowded"); continue; }
            if (system(v, false)) { accepted.add(new double[]{v.x, v.z, spacing}); nf++; }
            else plan.note("fracture failed");
        }
    }

    static boolean crowded(List<double[]> acc, double x, double z, double spacing) {
        for (double[] a : acc) if (Math.hypot(a[0] - x, a[1] - z) < Math.max(Math.min(spacing, a[2]), 600)) return true;
        return false;
    }

    /** Lays out one system (7.3): a trunk both ways along the strike, branches, a drop to a lower level; commits if long enough. */
    boolean system(Survey v, boolean karst) {
        Column c = v.c;
        double[] b = v.bands;
        double y0 = karst ? bestKarstY(c, b[3] + 14, b[2] - 14, (b[2] + b[3]) / 2) : (b[2] + b[3]) / 2 + (h(k(7, v.key)) - 0.5) * 30;
        if (Double.isNaN(y0)) { plan.note((karst ? "karst" : "fracture") + " no level"); return false; }
        int id = plan.caves.size();
        double[] fd = new double[1];
        GeologyModel.Fault f = g.nearestFault(v.x, v.z, 1200, fd);
        double ang = karst ? g.strikeAt(v.x, v.z) + (h(k(8, v.key)) - 0.5) * 0.5 : f != null ? f.strike() : h(k(8, v.key)) * Math.PI;
        List<Node> nodes = new ArrayList<>();
        List<double[]> edges = new ArrayList<>();
        Node c0 = node(v.x, v.z, y0 - 4, karst, k(9, v.key), true);
        if (!nodeOk(c0, id)) { plan.note((karst ? "karst" : "fracture") + " centre blocked"); return false; }
        nodes.add(c0);
        double trunk = karst ? 350 + 1150 * h(k(10, v.key)) : 180 + 420 * h(k(10, v.key));
        double trh = karst ? 2.2 + 2.3 * h(k(11, v.key)) : 1.0 + 0.5 * h(k(11, v.key));
        double trv = karst ? trh * (0.8 + 0.4 * h(k(12, v.key))) : 3 + 3 * h(k(12, v.key));
        grow(nodes, edges, 0, ang, trunk / 2, trh, trv, karst, id, k(13, v.key));
        grow(nodes, edges, 0, ang + Math.PI, trunk / 2, trh, trv, karst, id, k(14, v.key));
        int nb = karst ? 2 + (int) (4 * h(k(15, v.key))) : 1 + (int) (3 * h(k(15, v.key)));
        for (int i = 0; i < nb && nodes.size() > 1; i++) {
            int from = (int) (h(k(16, v.key * 8 + i)) * nodes.size());
            double ba = ang + (h(k(17, v.key * 8 + i)) < 0.5 ? 1 : -1) * (0.7 + 0.9 * h(k(18, v.key * 8 + i)));
            double bl = 80 + 320 * h(k(19, v.key * 8 + i));
            double brh = karst ? 1.5 + 1.5 * h(k(20, v.key * 8 + i)) : 0.8 + 0.4 * h(k(20, v.key * 8 + i));
            double brv = karst ? brh : 2.5 + 2 * h(k(21, v.key * 8 + i));
            grow(nodes, edges, from, ba, bl, brh, brv, karst, id, k(22, v.key * 8 + i));
        }
        double total = 0;
        for (double[] e : edges) {
            Node a = nodes.get((int) e[0]), bn = nodes.get((int) e[1]);
            total += Math.hypot(a.x - bn.x, a.z - bn.z);
        }
        if (total < (karst ? 150 : 100)) { plan.note((karst ? "karst" : "fracture") + " too short"); return false; }
        Cave cv = cave(karst ? CaveKind.KARST_SYSTEM : CaveKind.FRACTURE_SYSTEM, 3, v.x, y0, v.z, g.waterTable(c), g.rockAt(c, y0));
        cv.label = karst ? GeologyModel.UNIT_NAME[Math.max(0, Math.min(GeologyModel.UNIT.length - 1, g.unitAt(c, y0)))] : f != null ? "fault " + f.id() : "joints";
        for (Node n : nodes) { add(n.shape(id)); nodePlace(n, id); }
        for (double[] e : edges) {
            Node a = nodes.get((int) e[0]), bn = nodes.get((int) e[1]);
            if (e[4] > 0) {
                // a drop: shaft from the upper chamber's floor (offset) into the lower node
                double dx = bn.x - a.x, dz = bn.z - a.z, l = Math.hypot(dx, dz);
                double sx = bn.x, sz = bn.z;
                Capsule shaft = new Capsule(id, sx, a.floor + 0.5, sz, sx, bn.floor + e[2], sz, e[2], e[2]);
                add(shaft);
                cv.passageLength += shaft.length();
                double tx = sx - dx / l * (e[2] + 1.0), tz = sz - dz / l * (e[2] + 1.0);
                int top = plan.addPlace(PlaceKind.PITCH_TOP, tx, a.floor, tz, id, new Platform(tx, a.floor, tz, 1.0, 1.0, 2.4));
                walk(a.place, top, 2, 2, cv.waterY, true);
                pitch(top, bn.place, sx, sz, e[2], id, true, naturalFixture(k(23, v.key * 64 + (long) e[0])), k(24, v.key * 64 + (long) e[0]), -1);
            } else {
                Capsule cp = new Capsule(id, a.x, a.floor + 0.7 * e[3], a.z, bn.x, bn.floor + 0.7 * e[3], bn.z, e[2], e[3]);
                add(cp);
                cv.passageLength += cp.length();
                walk(a.place, bn.place, e[2], e[3], cv.waterY, true);
            }
        }
        systems.add(id);
        return true;
    }

    Node node(double x, double z, double floor, boolean karst, long salt, boolean chamberForced) {
        boolean ch = chamberForced || h(salt) < 0.45;
        if (!ch) {
            double r = 2.5 + 1.5 * h(salt + 1);
            return new Node(x, z, floor, r, karst ? r * 0.8 : 3.5, r, false);
        }
        if (karst) return new Node(x, z, floor, 5 + 15 * h(salt + 2), 2 + 5 * h(salt + 3), 5 + 15 * h(salt + 4), true);
        return new Node(x, z, floor, 3 + 3 * h(salt + 2), 5 + 8 * h(salt + 3), 3 + 3 * h(salt + 4), true);
    }

    boolean nodeOk(Node n, int owner) {
        if (!land(n.x, n.z)) return false;
        Column c = g.column(n.x, n.z);
        double[] b = g.bands(c);
        if (n.floor < b[3] || n.roof() > b[2]) return false;
        return fits(n.shape(owner), 3, owner, null);
    }

    /** Grows a chain from node `from` heading `ang` for `length` m (edges: {a, b, rh, rv, drop}). */
    void grow(List<Node> nodes, List<double[]> edges, int from, double ang, double length, double rh, double rv, boolean karst, int owner, long salt) {
        double rem = length, head = ang;
        int prev = from, i = 0, offKarst = 0;
        boolean dropped = false;
        while (rem > 30) {
            Node a = nodes.get(prev);
            double seg = 60 + 90 * h(salt + 31L * i);
            head += (h(salt + 31L * i + 1) - 0.5) * 0.6;
            double nx = a.x + Math.cos(head) * seg, nz = a.z + Math.sin(head) * seg;
            Column c = g.column(nx, nz);
            double floor;
            boolean drop = karst && !dropped && a.chamber && a.r() >= 5 && h(salt + 31L * i + 2) < 0.18;
            if (drop) {
                floor = a.floor - (12 + 23 * h(salt + 31L * i + 3));
                nx = a.x + Math.cos(head) * 0.65 * a.r();
                nz = a.z + Math.sin(head) * 0.65 * a.r();
            } else if (karst) {
                double y = bestKarstY(c, a.floor - 0.35 * seg, a.floor + 0.35 * seg, a.floor + 4);
                if (Double.isNaN(y)) { y = a.floor + 4; offKarst++; } else offKarst = 0;
                if (offKarst > 1) break;
                floor = y - 4;
            } else floor = a.floor + (h(salt + 31L * i + 3) - 0.5) * 0.3 * seg;
            Node n = drop ? new Node(nx, nz, floor, 2.5 + 2 * h(salt + 31L * i + 4), 2.5, 2.5 + 2 * h(salt + 31L * i + 4), false)
                    : node(nx, nz, floor, karst, salt + 31L * i + 5, false);
            if (!nodeOk(n, owner)) break;
            double erh = drop ? 1.5 + h(salt + 31L * i + 6) : rh, erv = drop ? erh : rv;
            Shape link = drop ? new Capsule(owner, nx, a.floor + 0.5, nz, nx, n.floor + erh, nz, erh, erh)
                    : new Capsule(owner, a.x, a.floor + 0.7 * erv, a.z, n.x, n.floor + 0.7 * erv, n.z, erh, erv);
            if (!fits(link, 3, owner, null)) break;
            // keep the new node clear of this system's own nodes (no chamber inside another)
            boolean overlap = false;
            for (int j = 0; j < nodes.size(); j++) {
                if (j == prev) continue;
                Node o = nodes.get(j);
                if (Math.hypot(o.x - n.x, o.z - n.z) < o.r() + n.r() + 6 && Math.abs(o.floor - n.floor) < 20) { overlap = true; break; }
            }
            if (overlap) break;
            nodes.add(n);
            edges.add(new double[]{prev, nodes.size() - 1, erh, erv, drop ? 1 : 0});
            if (drop) dropped = true;
            prev = nodes.size() - 1;
            rem -= drop ? 0 : seg;
            i++;
            if (i > 40) break;
        }
    }

    /** Layer-2 connectors (7.4): 60 % of karst systems and 20 % of fracture systems reach up to a layer-2 chamber. */
    void connectors() {
        for (int sid : new ArrayList<>(systems)) {
            Cave sys = plan.caves.get(sid);
            double pr = sys.kind == CaveKind.KARST_SYSTEM ? 0.6 : 0.2;
            if (h(k(30, sid)) >= pr) continue;
            // the driest chamber of the system
            int best = -1;
            double bf = -1e9;
            for (int pl : sys.places) {
                Place q = plan.places.get(pl);
                if (q.kind() != PlaceKind.CHAMBER) continue;
                double f = q.y() - sys.waterY + h(k(31, pl));
                if (f > bf) { bf = f; best = pl; }
            }
            if (best < 0) continue;
            Place n = plan.places.get(best);
            for (int tries = 0; tries < 4; tries++) {
                double a = 2 * Math.PI * h(k(32, sid * 4L + tries)), d = 25 + 35 * h(k(33, sid * 4L + tries));
                double x = n.x() + Math.cos(a) * d, z = n.z() + Math.sin(a) * d;
                if (!land(x, z)) continue;
                Column c = g.column(x, z);
                double[] b = g.bands(c);
                double lo = b[2] + 8, hi = b[1] - 6 - 8;
                if (hi - lo < 4) continue;
                double floor = lo + (hi - lo) * (0.2 + 0.5 * h(k(34, sid * 4L + tries)));
                Cave con = cave(CaveKind.CONNECTOR, 2, x, floor, z, sys.waterY, g.rockAt(c, floor + 2));
                Node cn = new Node(x, z, floor, 4 + 6 * h(k(35, sid)), 2 + 2 * h(k(36, sid)), 4 + 6 * h(k(37, sid)), true);
                Ellipsoid e = cn.shape(con.id);
                if (!fits(e, 2, con.id, null)) { plan.caves.remove(plan.caves.size() - 1); continue; }
                add(e);
                nodePlace(cn, con.id);
                int st = connect(cn.place, cn.r(), best, con.id, 2, false, true, Set.of(sid), 1.5 + h(k(38, sid)), k(39, sid), naturalFixture(k(44, sid)));
                if (st < 0) {
                    // roll back the connector chamber (it is the last cave and shape)
                    unadd(con);
                    continue;
                }
                link(sid, con.id, "connector", st);
                break;
            }
        }
    }

    /** Removes the last cave's shapes and places if it never got linked (connector or small cave roll-back). */
    void unadd(Cave c) {
        for (int i = c.shapes.size() - 1; i >= 0; i--) {
            int si = c.shapes.get(i);
            for (double[] b : pieces.get(si))
                for (int cz = (int) Math.floor(b[2] / OCC); cz <= (int) Math.floor(b[5] / OCC); cz++)
                    for (int cx = (int) Math.floor(b[0] / OCC); cx <= (int) Math.floor(b[3] / OCC); cx++) {
                        List<Integer> l = occ.get(UndergroundPlan.cellKey(cx, cz));
                        if (l != null) l.remove(Integer.valueOf(si));
                    }
            if (si == plan.shapes.size() - 1) { plan.shapes.remove(si); pieces.remove(si); }
            else {
                Box gone = new Box(c.id, 0, -1e6, 0, 0.01, 0.01, 0.01, 0);
                plan.shapes.set(si, gone);
                pieces.set(si, List.of(gone.bounds()));
            }
        }
        c.shapes.clear();
        for (int pl : c.places) plan.places.set(pl, new Place(pl, PlaceKind.JUNCTION, 0, -1e6, 0, c.id, null));
        c.places.clear();
        c.label = "removed";
    }

    /** 30 % of neighbouring karst systems in the same karst connect (7.4). */
    void systemLinks() {
        for (int i = 0; i < systems.size(); i++) for (int j = i + 1; j < systems.size(); j++) {
            Cave a = plan.caves.get(systems.get(i)), b = plan.caves.get(systems.get(j));
            if (a.kind != CaveKind.KARST_SYSTEM || b.kind != CaveKind.KARST_SYSTEM) continue;
            if (Math.hypot(a.x - b.x, a.z - b.z) > 1400 || h(k(50, a.id * 1000L + b.id)) >= 0.3) continue;
            int pa = -1, pb = -1;
            double bd = 600;
            for (int x : a.places) for (int y : b.places) {
                Place q = plan.places.get(x), r = plan.places.get(y);
                if (q.kind() == PlaceKind.PITCH_TOP || r.kind() == PlaceKind.PITCH_TOP || q.kind() == PlaceKind.LEDGE || r.kind() == PlaceKind.LEDGE) continue;
                double d = Math.hypot(q.x() - r.x(), q.z() - r.z());
                if (d < bd && Math.abs(q.y() - r.y()) / Math.max(1, d) <= SLOPE_MAX) { bd = d; pa = x; pb = y; }
            }
            if (pa < 0) continue;
            Place q = plan.places.get(pa), r = plan.places.get(pb);
            int up = q.y() >= r.y() ? pa : pb, dn = up == pa ? pb : pa;
            int st = connect(up, 2, dn, plan.places.get(up).cave(), 3, false, true, Set.of(a.id, b.id), 1.8, k(51, a.id * 1000L + b.id), Fixture.NONE);
            if (st >= 0) link(a.id, b.id, "karst_link", st);
        }
    }

    // ------------------------------------------------------------------ giant caves and canyons (layer 4)

    void giants() {
        double landKm2 = survey.size() * CELL * CELL / 1e6;
        int want = (int) Math.max(3, Math.min(6, Math.round(landKm2 / 60)));
        record Cand(Survey v, double score, double roof, double floor, int cat) {}
        List<Cand> cands = new ArrayList<>();
        for (Survey v : survey) {
            double roof = Math.min(v.c.hs - 150, v.bands[3] + 30);
            double floor = g.minY + GeologyModel.FLOOR_KEEP + 8;
            if (roof - floor < 40) continue;
            double hgt = Math.min(roof - floor, 40 + 80 * h(k(60, v.key)));
            double kar = g.karstShare(v.c, roof - hgt, roof);
            int cat = v.c.w[GeologyModel.PLATEAU] > 0.4 ? 1 : v.c.w[GeologyModel.MOUNTAIN] > 0.4 && v.faultD < 600 ? 2 : 0;
            double score = kar + (v.faultD < 600 ? 0.6 : 0) + 0.5 * v.c.w[GeologyModel.PLATEAU] + 0.2 * h(k(61, v.key));
            if (cat == 0 && kar < 0.3) continue;
            cands.add(new Cand(v, score, roof, roof - hgt, cat));
        }
        cands.sort(Comparator.comparingDouble((Cand c) -> -c.score).thenComparingInt(c -> c.v.key));
        List<Cand> pick = new ArrayList<>();
        // quotas (8.2): the best tableland one, a range one on a fault (60 %), the rest in karst
        for (int cat : new int[]{1, 2}) {
            if (cat == 2 && h(k(62, 0)) >= 0.6) continue;
            for (Cand c : cands) if (c.cat == cat && tryGiant(c.v, c.roof, c.floor, pick.size())) { pick.add(c); break; }
        }
        for (Cand c : cands) {
            if (giants.size() >= want) break;
            if (c.cat != 0 && pick.contains(c)) continue;
            boolean near = false;
            for (int gi : giants) if (Math.hypot(plan.caves.get(gi).x - c.v.x, plan.caves.get(gi).z - c.v.z) < 3500) near = true;
            if (near) continue;
            if (tryGiant(c.v, c.roof, c.floor, giants.size())) pick.add(c);
        }
    }

    boolean tryGiant(Survey v, double roof, double floor, int n) {
        for (int gi : giants) if (Math.hypot(plan.caves.get(gi).x - v.x, plan.caves.get(gi).z - v.z) < 3500) return false;
        double ry = (roof - floor) / 1.6;
        double rx = 40 + 85 * h(k(63, v.key)), rz = Math.min(rx, 30 + 70 * h(k(64, v.key)));
        int id = plan.caves.size();
        Ellipsoid e = new Ellipsoid(id, v.x, floor + 0.6 * ry, v.z, rx, ry, rz, floor);
        if (!fits(e, 4, id, null)) return false;
        for (double[] q : new double[][]{{v.x - rx, v.z}, {v.x + rx, v.z}, {v.x, v.z - rz}, {v.x, v.z + rz}}) if (!land(q[0], q[1])) return false;
        Cave cv = cave(CaveKind.GIANT, 4, v.x, e.y(), v.z, g.waterTable(v.c), g.rockAt(v.c, e.y()));
        cv.label = "G" + (n + 1);
        add(e);
        int pillars = (int) (5 * h(k(65, v.key)));
        for (int i = 0; i < pillars; i++) {
            double a = 2 * Math.PI * h(k(66, v.key * 8 + i)), d = 0.5 * h(k(67, v.key * 8 + i));
            add(new Pillar(id, v.x + Math.cos(a) * d * rx, v.z + Math.sin(a) * d * rz, 4 + 6 * h(k(68, v.key * 8 + i)), floor - 1, e.y() + ry + 1));
        }
        // the floor place off the pillars, and a ledge on the wall
        double fx = v.x - 0.3 * rx, fz = v.z;
        nodePlaceAt(PlaceKind.CHAMBER, fx, floor, fz, id, new Platform(fx, floor, fz, 6, 6, 1.6 * ry));
        giants.add(id);
        // 50 % reach a layer-3 system (7.4)
        if (h(k(69, v.key)) < 0.5) linkDown(id, 1500);
        return true;
    }

    int nodePlaceAt(PlaceKind k, double x, double y, double z, int owner, Platform pf) {
        int pl = plan.addPlace(k, x, y, z, owner, pf);
        plan.caves.get(owner).places.add(pl);
        return pl;
    }

    /** Links a deep object to the nearest system chamber within reach (the system's chamber is the upper end). */
    boolean linkDown(int deep, double reach) {
        Cave d = plan.caves.get(deep);
        int dp = d.places.get(0);
        Place dq = plan.places.get(dp);
        List<int[]> cands = new ArrayList<>();
        for (int sid : systems) {
            Cave sys = plan.caves.get(sid);
            if (Math.hypot(sys.x - d.x, sys.z - d.z) > reach + 800) continue;
            for (int pl : sys.places) {
                Place q = plan.places.get(pl);
                if (q.kind() != PlaceKind.CHAMBER || q.y() < sys.waterY) continue;
                double dist = Math.hypot(q.x() - dq.x(), q.z() - dq.z());
                if (dist <= reach) cands.add(new int[]{pl, (int) dist, sid});
            }
        }
        cands.sort(Comparator.comparingInt((int[] c) -> c[1]).thenComparingInt(c -> c[0]));
        for (int[] c : cands) {
            Place q = plan.places.get(c[0]);
            double r = Math.max(2, q.platform().hx() / 0.3);
            int st = connect(c[0], r, dp, c[2], 3, false, true, Set.of(deep, c[2]), 2.2, k(70, deep * 1000L + c[0]), naturalFixture(k(71, deep * 1000L + c[0])));
            if (st >= 0) { link(c[2], deep, d.kind == CaveKind.GIANT ? "system_giant" : "system_canyon", st); return true; }
        }
        return false;
    }

    /** 1–2 underground canyons along the longest faults (8.3): a tall slot, dry or with an underground river. */
    void canyons() {
        List<GeologyModel.Fault> fs = new ArrayList<>(g.faultList());
        fs.sort(Comparator.comparingDouble((GeologyModel.Fault f) -> -f.length()).thenComparingInt(GeologyModel.Fault::id));
        int want = 1 + (h(k(80, 0)) < 0.5 ? 1 : 0);
        for (GeologyModel.Fault f : fs) {
            if (canyons.size() >= want) break;
            double len = 300 + 500 * h(k(81, f.id()));
            double t0 = 0.5 - len / f.length() / 2;
            if (t0 < 0.05) continue;
            double ax = f.ax() + (f.bx() - f.ax()) * t0, az = f.az() + (f.bz() - f.az()) * t0;
            double bx = f.ax() + (f.bx() - f.ax()) * (1 - t0), bz = f.az() + (f.bz() - f.az()) * (1 - t0);
            if (!land(ax, az) || !land(bx, bz) || !land((ax + bx) / 2, (az + bz) / 2)) continue;
            double rh = 7.5 + 12.5 * h(k(82, f.id())), rv = 30 + 30 * h(k(83, f.id()));
            double top = 1e9;
            for (int i = 0; i <= 8; i++) {
                double x = ax + (bx - ax) * i / 8, z = az + (bz - az) * i / 8;
                top = Math.min(top, g.hs(x, z) - 120);
            }
            double cy = top - rv;
            double floor = cy - 0.7 * rv;
            if (floor < g.minY + GeologyModel.FLOOR_KEEP + 8) continue;
            int id = plan.caves.size();
            Capsule c = new Capsule(id, ax, cy, az, bx, cy, bz, rh, rv);
            if (!fits(c, 4, id, null)) continue;
            Column mc = g.column((ax + bx) / 2, (az + bz) / 2);
            double wt = g.waterTable(mc);
            double waterY = wt > floor ? floor + 1.0 : Double.NEGATIVE_INFINITY;
            Cave cv = cave(CaveKind.CANYON, 4, (ax + bx) / 2, cy, (az + bz) / 2, waterY, g.rockAt(mc, cy));
            cv.label = "fault " + f.id() + (waterY > -1e9 ? " river" : " dry");
            add(c);
            cv.passageLength = c.length();
            int pa = nodePlaceAt(PlaceKind.JUNCTION, ax + (bx - ax) * 0.1, floor, az + (bz - az) * 0.1, id, new Platform(ax + (bx - ax) * 0.1, floor, az + (bz - az) * 0.1, 4, 4, 1.7 * rv));
            int pb = nodePlaceAt(PlaceKind.JUNCTION, ax + (bx - ax) * 0.9, floor, az + (bz - az) * 0.9, id, new Platform(ax + (bx - ax) * 0.9, floor, az + (bz - az) * 0.9, 4, 4, 1.7 * rv));
            plan.addStep(pa, pb, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, waterY > -1e9 ? 2 : 1);
            canyons.add(id);
            if (h(k(84, f.id())) < 0.5) linkDown(id, 1500);
        }
    }

    // ------------------------------------------------------------------ small caves (layer 2)

    void smallCaves() {
        for (Survey v : survey) {
            double lambda = 26.2 * (v.kL2 * 1.2 + (1 - v.kL2) * 0.12);
            int count = (int) Math.floor(lambda + h(k(90, v.key)));
            for (int i = 0; i < count; i++) {
                long sk = v.key * 128L + i;
                double x = v.x + (h(k(91, sk)) - 0.5) * CELL, z = v.z + (h(k(92, sk)) - 0.5) * CELL;
                if (!land(x, z)) continue;
                Column c = g.column(x, z);
                double[] b = g.bands(c);
                double lo = b[2] + 6, hi = b[1] - 6;
                if (hi - lo < 10) continue;
                double y = lo + (hi - lo) * h(k(93, sk));
                boolean kar = g.karst(c, y) >= 0.5;
                int id = plan.caves.size();
                Shape sh;
                double top;
                if (kar) {
                    double ry = (2 + 4 * h(k(94, sk))) / 1.6, rx = 2.5 + 7.5 * h(k(95, sk)), rz = 2.5 + 7.5 * h(k(96, sk));
                    double floor = y;
                    if (floor + 1.6 * ry > hi) continue;
                    sh = new Ellipsoid(id, x, floor + 0.6 * ry, z, rx, ry, rz, floor);
                    top = floor + 1.6 * ry;
                } else {
                    double a = g.strikeAt(x, z) + (h(k(97, sk)) - 0.5) * 0.8, len = 10 + 30 * h(k(98, sk));
                    double rh = 0.25 + 0.75 * h(k(99, sk)), rv = 2.5 + 7.5 * h(k(100, sk));
                    double cy = y + rv;
                    if (cy + rv > hi) continue;
                    sh = new Capsule(id, x - Math.cos(a) * len / 2, cy, z - Math.sin(a) * len / 2, x + Math.cos(a) * len / 2, cy, z + Math.sin(a) * len / 2, rh, rv);
                    top = cy + rv;
                }
                if (top > hi || !fits(sh, 2, id, null)) continue;
                Cave cv = cave(kar ? CaveKind.SMALL_KARST : CaveKind.FISSURE, 2, x, y, z, g.waterTable(c), g.rockAt(c, y + 1));
                add(sh);
                if (sh instanceof Capsule cp) cv.passageLength = cp.length();
                double fy = sh instanceof Ellipsoid e ? e.floorY() : ((Capsule) sh).ay() - 0.7 * ((Capsule) sh).rv();
                nodePlaceAt(kar ? PlaceKind.CHAMBER : PlaceKind.JUNCTION, x, fy, z, id,
                        kar ? new Platform(x, fy, z, 0.3 * Math.min(((Ellipsoid) sh).rx(), ((Ellipsoid) sh).rz()), 0.3 * Math.min(((Ellipsoid) sh).rx(), ((Ellipsoid) sh).rz()), 1.6 * ((Ellipsoid) sh).ry())
                                : new Platform(x, fy, z, Math.min(0.4, ((Capsule) sh).rh() * 0.6), 1.5, 1.7 * ((Capsule) sh).rv()));
            }
        }
    }

    // ------------------------------------------------------------------ entrances

    EntranceKind kindAt(Column c, double slope) {
        RockType r = g.bedrock(c);
        boolean kar = r != null && r.karst >= 0.6;
        if (slope >= 0.3 && c.soil <= 3 && kar) return EntranceKind.HILLSIDE_MOUTH;
        if (kar && c.soil >= 5 && (c.w[GeologyModel.COAST] + c.w[GeologyModel.PLAINS] > 0.5)) return EntranceKind.COVER_SINKHOLE;
        if (kar || c.w[GeologyModel.PLATEAU] > 0.5) return EntranceKind.SHAFT;
        return EntranceKind.FISSURE;
    }

    /** An entrance near a target place, searched in rings out to `reach` (9.2); returns the entrance id or -1. */
    int entranceTo(int targetPlace, int owner, double reach, long salt, EntranceKind force) {
        Place t = plan.places.get(targetPlace);
        for (int ring = 0; ring <= reach; ring += 40) {
            int na = ring == 0 ? 1 : 12;
            for (int a = 0; a < na; a++) {
                double ang = a * 2 * Math.PI / na + 2 * Math.PI * h(salt);
                double x = t.x() + Math.cos(ang) * ring, z = t.z() + Math.sin(ang) * ring;
                if (!land(x, z)) continue;
                Column c = g.column(x, z);
                EntranceKind kind = force != null ? force : kindAt(c, s.slopeAt(x, z));
                if (!p.entranceAllowed(kind, x, z)) continue;
                int e = openEntrance(kind, c, targetPlace, owner, salt + ring * 31L + a);
                if (e >= 0) return e;
            }
        }
        return -1;
    }

    /** Opens an entrance of the kind at a column and connects it to the target place (throat shapes). */
    int openEntrance(EntranceKind kind, Column c, int targetPlace, int owner, long salt) {
        double x = c.x, z = c.z, y = c.h;
        double rh = switch (kind) {
            case FISSURE -> 0.8 + 0.6 * h(salt);
            case SHAFT -> 1.0 + 3.0 * h(salt);
            case COVER_SINKHOLE -> 2 + 1.5 * h(salt);
            case GREAT_SINKHOLE -> 20 + 55 * h(salt);
            default -> 1.8 + 1.2 * h(salt);
        };
        int rim = plan.addPlace(PlaceKind.SURFACE, x, y, z, owner, new Platform(x, y, z, 3, 3, 50));
        int st;
        Place t = plan.places.get(targetPlace);
        if (kind == EntranceKind.HILLSIDE_MOUTH || kind == EntranceKind.DRY_SPRING) {
            int mouth = plan.addPlace(PlaceKind.JUNCTION, x, y - 1.5, z, owner, new Platform(x, y - 1.5, z, 1.2, 1.2, 2.6));
            Capsule m = new Capsule(owner, x, y - 1.5 + 0.7 * rh, z, x + (t.x() - x) * 0.02, y - 1.5 + 0.7 * rh, z + (t.z() - z) * 0.02, rh, rh);
            if (!clear(m, owner, Set.of(owner)) || cutsRig(m)) { plan.places.set(rim, new Place(rim, PlaceKind.JUNCTION, x, -1e6, z, owner, null)); plan.places.set(mouth, new Place(mouth, PlaceKind.JUNCTION, x, -1e6, z, owner, null)); return -1; }
            st = connect(mouth, 0.5, targetPlace, owner, 2, true, true, Set.of(owner), rh, salt + 1, naturalFixture(salt + 2));
            if (st >= 0) {
                throat(m);
                plan.addStep(rim, mouth, StepKind.WALK, Need.NONE, Need.NONE, null, 0);
            }
        } else if (kind == EntranceKind.GREAT_SINKHOLE) {
            double depth = Math.min(150, Math.max(40, y - (t.y() + 10)));
            double floor = y - depth;
            Capsule pit = new Capsule(owner, x, y + 6, z, x, floor + 12, z, rh, 12);
            if (!clear(pit, owner, Set.of(owner)) || !p.allows(pit, 2) || cutsRig(pit)) { kill(rim); return -1; }
            double fx = x + rh * 0.3, fz = z;
            int fl = plan.addPlace(PlaceKind.CHAMBER, fx, floor, fz, owner, new Platform(fx, floor, fz, 6, 6, depth));
            st = floor - t.y() > 1 ? connect(fl, rh, targetPlace, owner, 3, true, true, Set.of(owner), 2.5, salt + 3, naturalFixture(salt + 4)) : walk(fl, targetPlace, 3, 3, plan.caves.get(owner).waterY, true);
            if (st < 0) { kill(rim); kill(fl); return -1; }
            throat(pit);
            // the rim: rope with rebelays down the wall, or (60 %) the collapse talus as a slope route
            double tx = x + rh + 2, tz = z;
            plan.places.set(rim, new Place(rim, PlaceKind.SURFACE, tx, s.heightAt(tx, tz), tz, owner, new Platform(tx, s.heightAt(tx, tz), tz, 3, 3, 50)));
            int slope = -1;
            if (h(salt + 5) < 0.6) slope = plan.addStep(rim, fl, StepKind.SLOPE, Need.NONE, Need.NONE, null, 2);
            pitch(rim, fl, x, z, rh, owner, false, Fixture.NONE, salt + 6, slope);
            plan.caves.get(owner).places.add(fl);
        } else {
            st = connect(rim, 0.5, targetPlace, owner, plan.caves.get(owner).layer, true, true, Set.of(owner), rh, salt + 7, naturalFixture(salt + 8));
        }
        if (st < 0) { kill(rim); return -1; }
        Place tp = plan.places.get(targetPlace);
        Entrance e = new Entrance(plan.entrances.size(), kind, x, z, y, rh, y - tp.y(), owner, rim);
        plan.entrances.add(e);
        return e.id();
    }

    void kill(int place) {
        Place q = plan.places.get(place);
        plan.places.set(place, new Place(place, PlaceKind.JUNCTION, q.x(), -1e6, q.z(), q.cave(), null));
    }

    /** Entrances into systems (7.4): 40 % of karst systems and 15 % of fracture systems, through the connector if any. */
    void systemEntrances() {
        for (int sid : systems) {
            Cave sys = plan.caves.get(sid);
            double pr = sys.kind == CaveKind.KARST_SYSTEM ? 0.4 : 0.15;
            if (h(k(110, sid)) >= pr) continue;
            entranceIntoSystem(sid, k(111, sid), null);
        }
    }

    int entranceIntoSystem(int sid, long salt, EntranceKind force) {
        int con = -1;
        for (CaveLink l : plan.links) if (l.a() == sid && l.kind().equals("connector")) con = l.b();
        Cave target = con >= 0 ? plan.caves.get(con) : plan.caves.get(sid);
        for (int pl : target.places) {
            Place q = plan.places.get(pl);
            if (q.kind() != PlaceKind.CHAMBER && q.kind() != PlaceKind.JUNCTION) continue;
            if (q.y() < target.waterY) continue;
            int e = entranceTo(pl, target.id, 350, salt, force);
            if (e >= 0) return e;
        }
        return -1;
    }

    /** 4–8 great sinkholes over the largest dry chambers of karst systems (9.1). */
    void greatSinkholes() {
        List<int[]> cands = new ArrayList<>();
        for (int sid : systems) {
            Cave sys = plan.caves.get(sid);
            if (sys.kind != CaveKind.KARST_SYSTEM) continue;
            for (int pl : sys.places) {
                Place q = plan.places.get(pl);
                if (q.kind() != PlaceKind.CHAMBER || q.y() < sys.waterY) continue;
                cands.add(new int[]{pl, (int) (q.platform().hx() * 100), sid});
            }
        }
        cands.sort(Comparator.comparingInt((int[] c) -> -c[1]).thenComparingInt(c -> c[0]));
        int want = 4 + (int) (5 * h(k(120, 0)));
        int made = 0;
        Set<Integer> used = new HashSet<>();
        for (int[] c : cands) {
            if (made >= want) break;
            if (used.contains(c[2])) continue;
            Place q = plan.places.get(c[0]);
            Column col = g.column(q.x(), q.z());
            if (!land(q.x(), q.z()) || !p.entranceAllowed(EntranceKind.GREAT_SINKHOLE, q.x(), q.z())) continue;
            if (col.h - q.y() > 230) continue;
            int e = openEntrance(EntranceKind.GREAT_SINKHOLE, col, c[0], c[2], k(121, c[0]));
            if (e >= 0) { made++; used.add(c[2]); }
        }
    }

    /** The scattered pits, sinkholes, fissures, shafts, mouths and dry springs (9.2), most of them dead. */
    void bulkEntrances() {
        double o = s.gridOrigin, size = s.gridN * TerrainPlanSurface.CELL, step = 96;
        int m = (int) (size / step);
        for (int r = 0; r < m; r++) for (int cc = 0; cc < m; cc++) {
            long key = (long) r * m + cc;
            double x = o + (cc + h(k(130, key))) * step, z = o + (r + h(k(131, key))) * step;
            double roll = h(k(132, key));
            if (roll > 0.12) continue;    // the cheap cut: the largest p below is 0.11
            if (!land(x, z)) continue;
            Column c = g.column(x, z);
            RockType bed = g.bedrock(c);
            double slope = s.slopeAt(x, z);
            boolean kar = bed != null && bed.karst >= 0.6;
            EntranceKind kind = null;
            if (kar && c.soil <= 8 && roll < 0.05 * bed.karst * (0.4 + 0.6 * c.karstCover)) kind = EntranceKind.COLLAPSE_PIT;
            else if (kar && slope >= 0.35 && c.soil <= 3 && roll < 0.11) kind = EntranceKind.HILLSIDE_MOUTH;
            else if ((c.w[GeologyModel.COAST] + c.w[GeologyModel.PLAINS] > 0.5) && c.soil >= 4 && g.karst(c, c.h - c.soil - 10) >= 0.6 && roll < 0.04) kind = EntranceKind.COVER_SINKHOLE;
            else if (kar && roll < 0.012 && s.rivers != null) {
                double rd = s.rivers.nearestEdge(x, z, 160, new double[1]);
                if (rd > 40 && rd < 150) kind = EntranceKind.DRY_SPRING;
            }
            if (kind == null && (bed != null && bed.hard() || c.w[GeologyModel.MOUNTAIN] + c.w[GeologyModel.PLATEAU] > 0.5) && slope >= 0.15 && roll < 0.012) kind = EntranceKind.FISSURE;
            if (kind == null && c.w[GeologyModel.PLATEAU] > 0.5 && roll < 0.008) kind = EntranceKind.SHAFT;
            if (kind == null || !p.entranceAllowed(kind, x, z)) continue;
            bulkEntrance(kind, c, key);
        }
    }

    void bulkEntrance(EntranceKind kind, Column c, long key) {
        double u = h(k(133, key));
        double[] b = g.bands(c);
        boolean connected = switch (kind) {
            case COLLAPSE_PIT -> u < 0.15;
            case COVER_SINKHOLE -> u < 0.3;
            case FISSURE -> u < 0.5;
            default -> true;
        };
        if (connected && kind == EntranceKind.COVER_SINKHOLE) {
            // to the nearest system chamber within 300 m
            int best = -1, owner = -1;
            double bd = 300;
            for (int sid : systems) {
                Cave sys = plan.caves.get(sid);
                if (Math.hypot(sys.x - c.x, sys.z - c.z) > 2000) continue;
                for (int pl : sys.places) {
                    Place q = plan.places.get(pl);
                    if (q.kind() != PlaceKind.CHAMBER || q.y() < sys.waterY) continue;
                    double d = Math.hypot(q.x() - c.x, q.z() - c.z);
                    if (d < bd) { bd = d; best = pl; owner = sid; }
                }
            }
            if (best >= 0 && openEntrance(kind, c, best, owner, k(134, key)) >= 0) return;
            connected = false;
        }
        if (connected) {
            // a small layer-2 cave of its own under or behind the entrance
            double lo = b[2] + 6, hi = b[1] - 6;
            if (hi - lo >= 8) {
                double ang = 2 * Math.PI * h(k(135, key));
                double off = kind == EntranceKind.HILLSIDE_MOUTH || kind == EntranceKind.DRY_SPRING ? 25 + 100 * h(k(136, key)) : 5 + 10 * h(k(136, key));
                double x = c.x + Math.cos(ang) * off, z = c.z + Math.sin(ang) * off;
                if (land(x, z)) {
                    Column cc = g.column(x, z);
                    double[] bb = g.bands(cc);
                    double floor = Math.max(bb[2] + 6, Math.min(bb[1] - 12, c.h - (8 + 25 * h(k(137, key)))));
                    boolean fis = kind == EntranceKind.FISSURE;
                    int id = plan.caves.size();
                    Node n = fis ? new Node(x, z, floor, 1.2, 3, 4, true) : new Node(x, z, floor, 3 + 5 * h(k(138, key)), 1.5 + 1.5 * h(k(139, key)), 3 + 5 * h(k(140, key)), true);
                    Ellipsoid e = n.shape(id);
                    if (n.roof() <= bb[1] - 6 && fits(e, 2, id, null)) {
                        Cave cv = cave(fis ? CaveKind.FISSURE : CaveKind.SMALL_KARST, 2, x, floor, z, g.waterTable(cc), g.rockAt(cc, floor + 1));
                        add(e);
                        nodePlace(n, id);
                        if (openEntrance(kind, c, n.place, id, k(141, key)) >= 0) return;
                        unadd(cv);
                    }
                }
            }
        }
        // dead: a pit or a closed crack, no cave behind it (U4 carves the bowl)
        double depth = kind == EntranceKind.COLLAPSE_PIT ? 1 + 5 * h(k(142, key)) : kind == EntranceKind.COVER_SINKHOLE ? 5 + 20 * h(k(142, key)) : 2 + 6 * h(k(142, key));
        double radius = kind == EntranceKind.COLLAPSE_PIT ? 1.5 + 6 * h(k(143, key)) : kind == EntranceKind.COVER_SINKHOLE ? 5 + 15 * h(k(143, key)) : 0.5 + 1.5 * h(k(143, key));
        plan.entrances.add(new Entrance(plan.entrances.size(), kind, c.x, c.z, c.h, radius, depth, -1, -1));
    }

    /** 1–2 giant caves get a complete route from the surface (8.4): through a linked system that has (or gets) an entrance. */
    void giantRoutes() {
        Kit top = UndergroundPlan.KITS.get(UndergroundPlan.KITS.size() - 2);
        List<Integer> order = new ArrayList<>(giants);
        order.sort(Comparator.comparingDouble((Integer gi) -> p.homeDistance(plan.caves.get(gi).x, plan.caves.get(gi).z)).thenComparingInt(gi -> gi));
        int routed = 0;
        for (int gi : order) {
            if (routed >= 2) break;
            Cave gc = plan.caves.get(gi);
            BitSet in = UndergroundRoutes.reachIn(plan, top);
            if (in.get(gc.places.get(0))) { routed++; continue; }
            int sys = -1;
            for (CaveLink l : plan.links) if (l.b() == gi && l.kind().equals("system_giant")) sys = l.a();
            if (sys < 0 && linkDown(gi, 2500)) for (CaveLink l : plan.links) if (l.b() == gi && l.kind().equals("system_giant")) sys = l.a();
            if (sys < 0) continue;
            if (entranceIntoSystem(sys, k(150, gi), null) < 0 && entranceIntoSystem(sys, k(151, gi), EntranceKind.SHAFT) < 0) continue;
            if (UndergroundRoutes.reachIn(plan, top).get(gc.places.get(0))) routed++;
        }
    }

    // ------------------------------------------------------------------ ores and mines

    void ores() {
        record Rule(OreKind kind, int districts, double min) {}
        List<Rule> rules = List.of(new Rule(OreKind.COAL_SEAM, 4, 0.5), new Rule(OreKind.HYDROTHERMAL_VEIN, 4, 0.6),
                new Rule(OreKind.MVT_LEAD_ZINC, 3, 0.5), new Rule(OreKind.BANDED_IRON, 2, 0.5), new Rule(OreKind.NICKEL_COPPER, 2, 0.4),
                new Rule(OreKind.PEGMATITE, 3, 0.6), new Rule(OreKind.GREISEN_SKARN, 2, 0.4), new Rule(OreKind.BAUXITE, 3, 0.6),
                new Rule(OreKind.BRECCIA_PIPE, 3, 0.6));
        for (Rule r : rules) {
            List<Survey> cs = new ArrayList<>();
            for (Survey v : survey) if (oreScore(r.kind, v) >= r.min) cs.add(v);
            cs.sort(Comparator.comparingDouble((Survey v) -> -(oreScore(r.kind, v) + 0.3 * h(k(160 + r.kind.ordinal(), v.key)))).thenComparingInt(v -> v.key));
            List<double[]> acc = new ArrayList<>();
            for (Survey v : cs) {
                if (acc.size() >= r.districts) break;
                if (crowded(acc, v.x, v.z, 3000)) continue;
                District d = new District(plan.districts.size(), r.kind, v.x, v.z, 600, v.dom);
                int made = 0;
                int nb = 3 + (int) (6 * h(k(170 + r.kind.ordinal(), v.key)));
                for (int i = 0; i < nb; i++) if (oreBody(d, v, i)) made++;
                if (made == 0) continue;
                plan.districts.add(d);
                acc.add(new double[]{v.x, v.z, 3000});
            }
        }
    }

    double oreScore(OreKind kind, Survey v) {
        double[] w = v.c.w;
        return switch (kind) {
            case COAL_SEAM -> w[GeologyModel.HILLS] + w[GeologyModel.PLAINS];
            case HYDROTHERMAL_VEIN -> v.faultD < 400 ? w[GeologyModel.MOUNTAIN] + w[GeologyModel.HILLS] : 0;
            case MVT_LEAD_ZINC -> (w[GeologyModel.HILLS] + w[GeologyModel.PLAINS]) * v.kL3;
            case BANDED_IRON, NICKEL_COPPER -> v.c.hs - v.c.tops[0] < 220 ? w[GeologyModel.LAKE] : 0;
            case PEGMATITE -> w[GeologyModel.MOUNTAIN];
            case GREISEN_SKARN -> w[GeologyModel.MOUNTAIN] > 0.25 && w[GeologyModel.MOUNTAIN] < 0.75 ? 0.5 + w[GeologyModel.MOUNTAIN] * 0.2 : 0;
            case BAUXITE -> w[GeologyModel.COAST];
            case BRECCIA_PIPE -> w[GeologyModel.PLATEAU];
        };
    }

    /** One body of a district at a place its geology allows (10.2 shapes). */
    boolean oreBody(District d, Survey v, int i) {
        long sk = v.key * 64L + i + 1000L * d.kind().ordinal();
        double x = v.x + (h(k(180, sk)) - 0.5) * 2 * d.radius(), z = v.z + (h(k(181, sk)) - 0.5) * 2 * d.radius();
        if (!land(x, z)) return false;
        Column c = g.column(x, z);
        double[] b = g.bands(c);
        double y, rx, ry, rz, yaw = g.strikeAt(x, z);
        switch (d.kind()) {
            case COAL_SEAM -> {
                double[] tp = new double[GeologyModel.UNIT.length + 1];
                g.tops(c, c.hs - 60, tp);
                double bot = tp[GeologyModel.COAL_MEASURES], top = tp[GeologyModel.COAL_MEASURES + 1];
                if (top - bot < 10 || c.coalW < 0.3) return false;
                y = bot + (top - bot) * GeologyModel.SEAMS[(int) (h(k(182, sk)) * GeologyModel.SEAMS.length)];
                if (y > c.h - c.t1) return false;
                rx = 150 + 250 * h(k(183, sk)); rz = 150 + 250 * h(k(184, sk)); ry = 0.3 + 0.7 * h(k(185, sk));
            }
            case HYDROTHERMAL_VEIN -> {
                double[] fd = new double[1];
                GeologyModel.Fault f = g.nearestFault(x, z, 600, fd);
                if (f == null) return false;
                yaw = f.strike();
                y = b[2] + (b[1] - b[2]) * h(k(182, sk)) - 40;
                rx = 50 + 200 * h(k(183, sk)); rz = 0.3 + 1.2 * h(k(184, sk)); ry = 20 + 60 * h(k(185, sk));
            }
            case MVT_LEAD_ZINC -> {
                y = bestKarstY(c, b[3], b[1] - 10, (b[2] + b[3]) / 2);
                if (Double.isNaN(y)) return false;
                rx = 10 + 30 * h(k(183, sk)); rz = 10 + 30 * h(k(184, sk)); ry = 4 + 10 * h(k(185, sk));
            }
            case BANDED_IRON -> {
                double[] tp = new double[GeologyModel.UNIT.length + 1];
                g.tops(c, c.hs - 150, tp);
                y = tp[0] - 15;
                rx = 200 + 400 * h(k(183, sk)); rz = 60 + 120 * h(k(184, sk)); ry = 5 + 10 * h(k(185, sk));
            }
            case NICKEL_COPPER -> {
                double[] tp = new double[GeologyModel.UNIT.length + 1];
                g.tops(c, c.hs - 150, tp);
                y = tp[0] - 5;
                rx = 20 + 40 * h(k(183, sk)); rz = 20 + 40 * h(k(184, sk)); ry = 10 + 20 * h(k(185, sk));
            }
            case PEGMATITE -> {
                y = b[1] - 10 - (b[1] - b[3]) * h(k(182, sk));
                rx = 5 + 15 * h(k(183, sk)); rz = 1 + 3 * h(k(184, sk)); ry = 3 + 8 * h(k(185, sk));
            }
            case GREISEN_SKARN -> {
                double[] tp = new double[GeologyModel.UNIT.length + 1];
                g.tops(c, c.hs - 120, tp);
                y = tp[0] + 5;
                if (y > c.h - c.t1) y = c.h - c.t1 - 30;
                rx = 15 + 35 * h(k(183, sk)); rz = 15 + 35 * h(k(184, sk)); ry = 8 + 15 * h(k(185, sk));
            }
            case BAUXITE -> {
                y = c.h - c.t1 - 3;
                rx = 100 + 200 * h(k(183, sk)); rz = 100 + 200 * h(k(184, sk)); ry = 1 + 3 * h(k(185, sk));
            }
            default -> {
                y = (b[2] + b[3]) / 2;
                rx = 15 + 35 * h(k(183, sk)); rz = rx; ry = 80 + 70 * h(k(185, sk));
            }
        }
        if (y - ry < g.minY + GeologyModel.FLOOR_KEEP || y + ry > c.h - 1) return false;
        plan.ores.add(new OreBody(plan.ores.size(), d.kind(), d.id(), x, y, z, rx, ry, rz, yaw, g.rockAt(c, y)));
        return true;
    }

    /** One abandoned mine candidate per district whose shallowest body is within 200 m of the ground (U7 builds it). */
    void mines() {
        for (District d : plan.districts) {
            OreBody best = null;
            double bd = 200;
            for (OreBody o : plan.ores) {
                if (o.district() != d.id()) continue;
                double depth = s.heightAt(o.x(), o.z()) - (o.y() + o.ry());
                if (depth > 5 && depth < bd) { bd = depth; best = o; }
            }
            if (best == null) continue;
            OreBody ob = best;
            int id = plan.caves.size();
            Column c = g.column(ob.x(), ob.z());
            double slope = s.slopeAt(ob.x(), ob.z());
            // the collar / portal: within 250 m, allowed, the steepest spot for an adit or the body's own column for a shaft
            double[] spot = null;
            double sb = -1;
            for (int ring = 0; ring <= 250; ring += 50) for (int a = 0; a < (ring == 0 ? 1 : 8); a++) {
                double ang = a * Math.PI / 4 + h(k(190, d.id()));
                double x = ob.x() + Math.cos(ang) * ring, z = ob.z() + Math.sin(ang) * ring;
                if (!land(x, z) || !p.entranceAllowed(EntranceKind.MINE_SHAFT, x, z)) continue;
                double sl = s.slopeAt(x, z);
                if (spot == null || sl > sb) { sb = sl; spot = new double[]{x, z}; }
            }
            if (spot == null) continue;
            boolean adit = sb >= 0.25 && bd < 120;
            double sx = spot[0], sz = spot[1], sy = s.heightAt(sx, sz);
            int nl = 1 + (int) (3 * h(k(191, d.id())));
            double[] levels = new double[nl];
            for (int i = 0; i < nl; i++) levels[i] = Math.min(sy - 12, ob.y() + ob.ry()) - 30 * i;
            if (levels[nl - 1] < g.minY + GeologyModel.FLOOR_KEEP + 5) continue;
            Cave cv = cave(CaveKind.BUILT, 2, sx, levels[0], sz, g.waterTable(c), g.rockAt(c, levels[0]));
            cv.label = "mine " + d.kind().name().toLowerCase(java.util.Locale.ROOT);
            List<Shape> shs = new ArrayList<>();
            List<Integer> places = new ArrayList<>();
            int collar = plan.addPlace(PlaceKind.SURFACE, sx + 4, sy, sz, id, new Platform(sx + 4, sy, sz, 3, 3, 50));
            places.add(collar);
            boolean okAll = true;
            int prevLevel = -1;
            if (adit) {
                double dx = ob.x() - sx, dz = ob.z() - sz, l = Math.max(1, Math.hypot(dx, dz));
                shs.add(new Capsule(id, sx, sy - 0.5, sz, ob.x(), levels[0] + 1.5, ob.z(), 1.6, 1.6));
                int lv = plan.addPlace(PlaceKind.MINE, ob.x(), levels[0], ob.z(), id, new Platform(ob.x(), levels[0], ob.z(), 1.2, 3, 2.6));
                places.add(lv);
                plan.addStep(collar, lv, Math.abs(sy - levels[0]) / l > 0.12 ? StepKind.SLOPE : StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 1);
                prevLevel = lv;
            } else {
                shs.add(new Capsule(id, sx, sy + 1, sz, sx, levels[nl - 1] + 2, sz, 2, 2));
            }
            for (int i = 0; i < nl; i++) {
                if (adit && i == 0) {
                    shs.add(new Box(id, ob.x(), levels[0] + 1.3, ob.z(), Math.max(20, ob.rx() * 0.5), 1.3, 1.3, ob.yaw()));
                    continue;
                }
                double lx = adit ? ob.x() : sx, lz = adit ? ob.z() : sz;
                shs.add(new Box(id, lx + Math.cos(ob.yaw()) * 40, levels[i] + 1.3, lz + Math.sin(ob.yaw()) * 40, 40 + 60 * h(k(192, d.id() * 8L + i)), 1.3, 1.3, ob.yaw()));
            }
            for (Shape sh : shs) if (p.violation(sh, 3) != null || !clear(sh, id, null)) { okAll = false; break; }
            if (!okAll) { plan.caves.remove(plan.caves.size() - 1); kill(collar); continue; }
            for (Shape sh : shs) throat(sh);
            if (!adit) {
                double ry = s.heightAt(sx + 2.6, sz);
                int rig = plan.addPlace(PlaceKind.PITCH_TOP, sx + 2.6, ry, sz, id, new Platform(sx + 2.6, ry, sz, 1, 1, 50));
                plan.addStep(collar, rig, StepKind.WALK, Need.NONE, Need.NONE, null, 0);
                int prev = rig;
                for (int i = 0; i < nl; i++) {
                    int lv = plan.addPlace(PlaceKind.MINE, sx + Math.cos(ob.yaw()) * 6, levels[i], sz + Math.sin(ob.yaw()) * 6, id,
                            new Platform(sx + Math.cos(ob.yaw()) * 6, levels[i], sz + Math.sin(ob.yaw()) * 6, 1.2, 1.2, 2.6));
                    places.add(lv);
                    // the ladderway lands at each level (stages per level)
                    pitch(prev, lv, sx, sz, 2, id, true, Fixture.FIXED_LADDER, k(193, d.id() * 8L + i), -1);
                    prev = lv;
                }
            } else {
                for (int i = 1; i < nl; i++) {
                    int lv = plan.addPlace(PlaceKind.MINE, ob.x() + Math.cos(ob.yaw()) * 6, levels[i], ob.z() + Math.sin(ob.yaw()) * 6, id,
                            new Platform(ob.x() + Math.cos(ob.yaw()) * 6, levels[i], ob.z() + Math.sin(ob.yaw()) * 6, 1.2, 1.2, 2.6));
                    places.add(lv);
                    // an internal winze with a ladderway between levels
                    double wx = ob.x() + Math.cos(ob.yaw()) * 2.6, wz = ob.z() + Math.sin(ob.yaw()) * 2.6;
                    int rig = plan.addPlace(PlaceKind.PITCH_TOP, wx, levels[i - 1], wz, id, new Platform(wx, levels[i - 1], wz, 1, 1, 2.6));
                    plan.addStep(prevLevel, rig, StepKind.WALK, Need.LIGHT, Need.LIGHT, null, 0);
                    shs.add(new Capsule(id, ob.x(), levels[i - 1] + 0.5, ob.z(), ob.x(), levels[i] + 2, ob.z(), 2, 2));
                    throat(shs.get(shs.size() - 1));
                    pitch(rig, lv, ob.x(), ob.z(), 2, id, true, Fixture.FIXED_LADDER, k(194, d.id() * 8L + i), -1);
                    prevLevel = lv;
                }
            }
            cv.places.addAll(places);
            plan.entrances.add(new Entrance(plan.entrances.size(), adit ? EntranceKind.MINE_ADIT : EntranceKind.MINE_SHAFT, sx, sz, sy, adit ? 1.6 : 2, sy - levels[nl - 1], id, collar));
            plan.mines.add(new Mine(plan.mines.size(), d.id(), adit ? EntranceKind.MINE_ADIT : EntranceKind.MINE_SHAFT, sx, sz, sy, levels, plan.entrances.size() - 1, places));
        }
    }

    // ------------------------------------------------------------------ anchors

    /**
     * Two anchors per rigging point of every pitch (19.2): on the wall behind the rigging place (probed outward from
     * the shaft's axis until rock), 1.6 m above a cave floor, or just under the loose ground at a surface rim (the
     * bolts go in the first rock); a floor bolt where no wall is within 12 m.
     */
    void rig() {
        for (Pitch pc : plan.pitches) {
            for (int i = 0; i < pc.rigs().size(); i++) {
                int rp = pc.rigs().get(i);
                if (settle(rp) || plan.places.get(rp).kind() != PlaceKind.LEDGE) continue;
                // a rebelay ledge that hangs in a chamber's air (the shaft broke into its roof): the rope hangs free
                // from the rig above to the one below, the ledge goes
                int s1 = pc.steps().get(i - 1), s2 = pc.steps().get(i);
                UndergroundPlan.Step a = plan.steps.get(s1), b = plan.steps.get(s2);
                Place pa = plan.places.get(a.a()), pb = plan.places.get(b.b());
                double len = Math.sqrt((pa.x() - pb.x()) * (pa.x() - pb.x()) + (pa.y() - pb.y()) * (pa.y() - pb.y()) + (pa.z() - pb.z()) * (pa.z() - pb.z()));
                plan.steps.set(s1, new UndergroundPlan.Step(s1, a.a(), b.b(), a.kind(), pb.y() - pa.y(), len, a.ab(), a.ba(), a.alt(), a.haul()));
                plan.steps.set(s2, new UndergroundPlan.Step(s2, b.b(), b.b(), b.kind(), 0, 0, b.ab(), b.ba(), b.alt(), b.haul()));
                pc.steps().remove(i);
                pc.rigs().remove(i);
                kill(rp);
                plan.note("ledge free hang");
                i--;
            }
        }
        plan.resetAdjacency();
        for (Pitch pc : plan.pitches) {
            for (int rp : pc.rigs()) {
                if (rp == pc.bottom()) { pc.anchorsAt().add(new int[0]); continue; }
                Place q = plan.places.get(rp);
                double dx = q.x() - pc.ax(), dz = q.z() - pc.az(), l = Math.hypot(dx, dz);
                if (l < 1e-6) { dx = 1; dz = 0; l = 1; }
                dx /= l;
                dz /= l;
                double y;
                double sx, sz;
                boolean surf = q.kind() == PlaceKind.SURFACE || Math.abs(q.y() - s.heightAt(q.x(), q.z())) < 2;
                if (surf) {
                    Column c = g.column(pc.ax(), pc.az());
                    y = q.y() - c.soil - 0.8;
                    sx = pc.ax();
                    sz = pc.az();
                } else {
                    y = q.y() + 1.6;
                    sx = q.x();
                    sz = q.z();
                }
                double wx = Double.NaN, wz = 0;
                double reach = 12 + (sx == pc.ax() ? pc.rh() : 0);
                for (double t = 0; t <= reach; t += 0.25) {
                    double x = sx + dx * t, z = sz + dz * t;
                    if (!plan.openAt(x, y, z) && (t > 0 || !plan.openAt(sx, y, sz))) {
                        if (t == 0 && sx == pc.ax()) continue;
                        wx = x + dx * 0.3;
                        wz = z + dz * 0.3;
                        break;
                    }
                }
                double ay = y;
                if (Double.isNaN(wx)) {
                    // no wall in reach: two floor bolts beside the platform, under the loose ground at the surface
                    wx = q.x();
                    wz = q.z();
                    ay = q.kind() == PlaceKind.SURFACE ? q.y() - g.column(wx, wz).soil - 0.8 : q.y() - 0.4;
                    plan.note("anchor floor bolts");
                }
                int[] ids = new int[2];
                for (int k2 = 0; k2 < 2; k2++) {
                    double side = k2 == 0 ? 0.8 : -0.8;
                    double ax = wx - dz * side, az = wz + dx * side;
                    // each bolt sits under its own column's loose ground (at the surface, or where a wall bolt
                    // near the ground would come out above a steep slope)
                    double under = s.heightAt(ax, az) - g.column(ax, az).soil - 0.8;
                    double by = surf ? under : Math.min(ay, under);
                    // the side step can land on the curve of the wall: go on outward until the bolt sits 0.3 m in rock,
                    // and on a slope back under the ground of the column it ends in
                    for (int pass = 0; pass < 3; pass++) {
                        for (int m = 0; m < 32 && plan.openAt(ax, by, az); m++) { ax += dx * 0.25; az += dz * 0.25; }
                        if (!plan.openAt(ax, by, az)) { ax += dx * 0.3; az += dz * 0.3; }
                        double u2 = s.heightAt(ax, az) - g.column(ax, az).soil - 0.8;
                        if (by <= u2 && !plan.openAt(ax, by, az)) break;
                        by = Math.min(by, u2);
                    }
                    if (plan.openAt(ax, by, az) || by > s.heightAt(ax, az) - g.column(ax, az).soil - 0.5) {
                        // marched along a passage: the nearest rock around, under its own ground
                        found:
                        for (double rr = 1; rr <= 10; rr += 1) for (int a8 = 0; a8 < 16; a8++) {
                            double cx = ax + Math.cos(a8 * Math.PI / 8) * rr, cz = az + Math.sin(a8 * Math.PI / 8) * rr;
                            double cy = Math.min(by, s.heightAt(cx, cz) - g.column(cx, cz).soil - 0.8);
                            if (plan.openAt(cx, cy, cz)) continue;
                            ax = cx; az = cz; by = cy;
                            plan.note("anchor ring search");
                            break found;
                        }
                    }
                    plan.anchors.add(new Anchor(ax, by, az, g.rockAt(ax, by, az)));
                    ids[k2] = plan.anchors.size() - 1;
                }
                pc.anchorsAt().add(ids);
            }
        }
    }

    /**
     * Puts a rigging place on its floor (19.2): the planned height can sit inside a chamber's curved floor away from its
     * centre, or hang over a lower passage. Searched within 4 m, the nearest height with head room and rock underfoot.
     */
    boolean settle(int rp) {
        Place q = plan.places.get(rp);
        if (q.kind() == PlaceKind.SURFACE || q.platform() == null || q.y() < -1e5) return true;
        if (Math.abs(q.y() - s.heightAt(q.x(), q.z())) < 1.5 && !plan.openAt(q.x(), q.y() + 0.5, q.z())) return true;
        if (standsAt(q.x(), q.y(), q.z())) return true;
        for (int i = 1; i <= 40; i++) for (int sgn = 1; sgn >= -1; sgn -= 2) {
            double y = q.y() + sgn * i * 0.1;
            if (!standsAt(q.x(), y, q.z())) continue;
            Platform pf = q.platform();
            plan.places.set(rp, new Place(rp, q.kind(), q.x(), y, q.z(), q.cave(), new Platform(pf.x(), y, pf.z(), pf.hx(), pf.hz(), pf.headroom())));
            plan.note("rig settled");
            return true;
        }
        plan.note("rig unsettled");
        return false;
    }

    boolean standsAt(double x, double y, double z) {
        return plan.openAt(x, y + 0.5, z) && plan.openAt(x, y + 1.7, z) && !plan.openAt(x, y - 0.6, z);
    }

    // ------------------------------------------------------------------ documents

    void doc(DocKind k, int place, String host) {
        Place q = plan.places.get(place);
        plan.documents.add(new DocumentSite(plan.documents.size(), k, q.x(), q.y(), q.z(), host, place));
    }

    /** Readable things (19.6): the facility, its survey camp, the mines, and a few notes deep in caves. */
    void documents() {
        if (plan.facility != null) {
            for (FacilityPart fp : plan.facility.parts()) {
                switch (fp.role()) {
                    case "laboratory" -> { doc(DocKind.RESEARCH_RECORD, fp.place(), "facility laboratory"); doc(DocKind.RADIO_MATERIAL, fp.place(), "facility radio room"); }
                    case "sealed_archive" -> { doc(DocKind.RESEARCH_RECORD, fp.place(), "facility archive"); doc(DocKind.SURVEY_MAP, fp.place(), "facility archive"); }
                    case "isolation" -> doc(DocKind.NOTE, fp.place(), "facility isolation");
                    case "haul_tunnel" -> doc(DocKind.ENGINEERING_LOG, fp.place(), "facility tunnel office");
                    case "survey_camp" -> { doc(DocKind.SURVEY_MAP, fp.place(), "survey camp"); doc(DocKind.ENGINEERING_LOG, fp.place(), "survey camp"); doc(DocKind.NOTE, fp.place(), "survey camp"); }
                    default -> {}
                }
            }
        }
        for (Mine m : plan.mines) {
            int lv = m.places().size() > 1 ? m.places().get(1) : m.places().get(0);
            doc(h(k(200, m.id())) < 0.5 ? DocKind.ACCIDENT_REPORT : DocKind.ENGINEERING_LOG, lv, "mine " + m.id());
        }
        for (int gi : giants) doc(DocKind.NOTE, plan.caves.get(gi).places.get(0), "giant cave " + plan.caves.get(gi).label);
        for (Entrance e : plan.entrances) {
            if (!e.connected() || e.kind() == EntranceKind.MINE_ADIT || e.kind() == EntranceKind.MINE_SHAFT) continue;
            Cave c = plan.caves.get(e.target());
            if (c.layer < 3 && c.kind != CaveKind.CONNECTOR) continue;
            if (h(k(201, e.id())) < 0.3) doc(DocKind.NOTE, c.places.get(0), "cave note");
        }
    }

    // ------------------------------------------------------------------ succession candidates

    void successions() {
        List<int[]> cands = new ArrayList<>();
        for (int sid : systems) {
            Cave sys = plan.caves.get(sid);
            int best = -1;
            double bs = -1;
            for (int pl : sys.places) {
                Place q = plan.places.get(pl);
                if (q.kind() != PlaceKind.CHAMBER || q.y() < sys.waterY + 0.5) continue;
                if (q.platform().area() > bs) { bs = q.platform().area(); best = pl; }
            }
            if (best >= 0) cands.add(new int[]{best, Env.DEEP_CHAMBER.ordinal()});
        }
        for (Cave c : plan.caves) if (c.kind == CaveKind.CONNECTOR && !c.places.isEmpty()) cands.add(new int[]{c.places.get(0), Env.CAVE_CHAMBER.ordinal()});
        for (int gi : giants) cands.add(new int[]{plan.caves.get(gi).places.get(0), Env.GIANT_CAVE.ordinal()});
        for (Entrance e : plan.entrances) if (e.kind() == EntranceKind.GREAT_SINKHOLE) {
            for (Place q : plan.places) if (q.cave() == e.target() && q.kind() == PlaceKind.CHAMBER && Math.hypot(q.x() - e.x(), q.z() - e.z()) < e.radius() + 2 && q.y() > -1e5) { cands.add(new int[]{q.id(), Env.SINKHOLE_FLOOR.ordinal()}); break; }
        }
        for (Mine m : plan.mines) if (m.places().size() > 1) cands.add(new int[]{m.places().get(m.places().size() - 1), Env.MINE_LEVEL.ordinal()});
        if (plan.facility != null) for (FacilityPart fp : plan.facility.parts()) {
            if (fp.role().equals("laboratory") || fp.role().equals("sealed_archive")) cands.add(new int[]{fp.place(), Env.FACILITY_ROOM.ordinal()});
            if (fp.role().equals("survey_camp")) cands.add(new int[]{fp.place(), Env.SURVEY_CAMP.ordinal()});
        }
        for (int[] c : cands) {
            Place q = plan.places.get(c[0]);
            Env env = Env.values()[c[1]];
            List<Exit> exits = new ArrayList<>();
            Kit kit = null;
            Exit prev = null;
            for (Kit kt : UndergroundPlan.KITS) {
                Exit e = q.kind() == PlaceKind.CAMP ? new Exit(c[0], Need.NONE, 0, 0, 0, 0, 0, true, List.of()) : UndergroundRoutes.exit(plan, c[0], kt);
                if (e == null) continue;
                if (kit == null && e.reliable()) kit = kt;
                if (prev == null || !e.path().equals(prev.path())) exits.add(e);
                prev = e;
                if (exits.size() >= 3) break;
            }
            Cave cv = q.cave() >= 0 ? plan.caves.get(q.cave()) : null;
            boolean wet = cv != null && cv.waterY > q.y() - 3;
            Exit best = null;
            for (Exit e : exits) if (e.reliable() && (best == null || e.need().rope() < best.need().rope())) best = e;
            int base = switch (env) {
                case SURVEY_CAMP -> 0;
                case CAVE_CHAMBER, SINKHOLE_FLOOR -> 1;
                case DEEP_CHAMBER, MINE_LEVEL, FACILITY_ROOM -> 2;
                case GIANT_CAVE -> 3;
            };
            int hazard = Math.min(4, base + (best != null && best.pitches() > 0 ? 1 : 0) + (wet ? 1 : 0) + (best == null ? 1 : 0));
            EnumSet<Background> bg = switch (env) {
                case SURVEY_CAMP -> EnumSet.of(Background.SURVEYOR, Background.HIKER, Background.SCAVENGER);
                case MINE_LEVEL -> EnumSet.of(Background.MINER, Background.SCAVENGER);
                case FACILITY_ROOM -> EnumSet.of(Background.RESEARCHER, Background.FACILITY_STAFF);
                case SINKHOLE_FLOOR -> EnumSet.of(Background.HIKER, Background.CAVER, Background.SCAVENGER);
                default -> EnumSet.of(Background.CAVER, Background.SURVEYOR);
            };
            List<double[]> cps = new ArrayList<>();
            if (best != null) {
                int at = c[0];
                for (int si : best.path()) {
                    UndergroundPlan.Step st = plan.steps.get(si);
                    Place a = plan.places.get(at);
                    cps.add(new double[]{a.x(), a.y() + 1, a.z()});
                    at = st.a() == at ? st.b() : st.a();
                }
            }
            boolean first = hazard <= 1 && kit != null && kit.rope() == 0 && (env == Env.SURVEY_CAMP || env == Env.SINKHOLE_FLOOR || env == Env.CAVE_CHAMBER);
            boolean succ = best != null && hazard <= 3;
            plan.successions.add(new Succession(plan.successions.size(), env, q.x(), q.y(), q.z(), c[0], cv == null ? "-" : cv.kind + " " + cv.id + " " + cv.label,
                    q.platform(), exits, kit, bg, hazard, first, succ, cps));
        }
    }
}
