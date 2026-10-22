package com.antaurora.apofirstlight.worldgen.underground;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The U1 underground plan of one seed (docs/worldgen/underground_geology_caves_v0_design.md 7–9 and 19): sparse
 * objects (caves, giant caves, entrances, ore bodies, mines, the research facility), the shapes that hollow them, a
 * 512 m spatial index over the shapes, the route graph through them (places, steps, pitches, platforms, anchors), the
 * document points and the survivor succession candidates. Built once per seed by {@link UndergroundPlanner}; queries
 * only. Not read by any world generation yet (U2 decides that).
 */
public final class UndergroundPlan {
    /** The spatial index's cell, m (7.1). */
    public static final double INDEX_CELL = 512;

    // ------------------------------------------------------------------ shapes

    /** One hollow (or, for a pillar, solid) volume. owner = the cave object. */
    public sealed interface Shape permits Ellipsoid, Capsule, Box, Pillar {
        int owner();

        /** {minX, minY, minZ, maxX, maxY, maxZ}. */
        double[] bounds();

        boolean inside(double x, double y, double z);
    }

    /** A chamber: an ellipsoid with a flat floor at floorY. */
    public record Ellipsoid(int owner, double x, double y, double z, double rx, double ry, double rz, double floorY) implements Shape {
        public double[] bounds() { return new double[]{x - rx, floorY, z - rz, x + rx, y + ry, z + rz}; }

        public boolean inside(double px, double py, double pz) {
            if (py < floorY) return false;
            double a = (px - x) / rx, b = (py - y) / ry, c = (pz - z) / rz;
            return a * a + b * b + c * c < 1;
        }

        /** The roof's Y over (px, pz), or NaN outside the footprint. */
        public double roofAt(double px, double pz) {
            double a = (px - x) / rx, c = (pz - z) / rz, q = 1 - a * a - c * c;
            return q <= 0 ? Double.NaN : y + ry * Math.sqrt(q);
        }
    }

    /** A passage or shaft: a capsule a-b with an elliptic section (half width rh, half height rv); a gentle one has a flat floor. */
    public record Capsule(int owner, double ax, double ay, double az, double bx, double by, double bz, double rh, double rv) implements Shape {
        public double[] bounds() {
            return new double[]{Math.min(ax, bx) - rh, Math.min(ay, by) - rv, Math.min(az, bz) - rh,
                    Math.max(ax, bx) + rh, Math.max(ay, by) + rv, Math.max(az, bz) + rh};
        }

        public double length() { return Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)); }

        public boolean vertical() { return Math.abs(by - ay) > 1.5 * Math.hypot(bx - ax, bz - az); }

        public boolean inside(double px, double py, double pz) {
            double k = rh / rv;
            double ux = bx - ax, uy = (by - ay) * k, uz = bz - az, l2 = ux * ux + uy * uy + uz * uz;
            double qx = px - ax, qy = (py - ay) * k, qz = pz - az;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (qx * ux + qy * uy + qz * uz) / l2));
            double dx = qx - ux * t, dy = qy - uy * t, dz = qz - uz * t;
            if (dx * dx + dy * dy + dz * dz >= rh * rh) return false;
            if (rv >= 1.5 && !vertical()) return py >= ay + (by - ay) * t - 0.7 * rv;
            return true;
        }
    }

    /** A built room or tunnel box (the facility, a mine level, a ledge niche), turned by yaw about its centre. */
    public record Box(int owner, double x, double y, double z, double hx, double hy, double hz, double yaw) implements Shape {
        public double[] bounds() {
            double c = Math.abs(Math.cos(yaw)), s = Math.abs(Math.sin(yaw));
            double ex = c * hx + s * hz, ez = s * hx + c * hz;
            return new double[]{x - ex, y - hy, z - ez, x + ex, y + hy, z + ez};
        }

        public boolean inside(double px, double py, double pz) {
            double dx = px - x, dz = pz - z, c = Math.cos(yaw), s = Math.sin(yaw);
            double lx = dx * c + dz * s, lz = -dx * s + dz * c;
            return Math.abs(lx) < hx && Math.abs(py - y) < hy && Math.abs(lz) < hz;
        }
    }

    /** Solid rock left standing inside a giant cave (subtracts from its owner's hollow). */
    public record Pillar(int owner, double x, double z, double r, double y0, double y1) implements Shape {
        public double[] bounds() { return new double[]{x - r, y0, z - r, x + r, y1, z + r}; }

        public boolean inside(double px, double py, double pz) {
            return py >= y0 && py <= y1 && (px - x) * (px - x) + (pz - z) * (pz - z) < r * r;
        }
    }

    // ------------------------------------------------------------------ objects

    public enum CaveKind { SMALL_KARST, FISSURE, KARST_SYSTEM, FRACTURE_SYSTEM, CONNECTOR, GIANT, CANYON, SINKHOLE, BUILT }

    /**
     * A natural cave object. layer 2..4; waterY: below it the cave is flooded (13.9); host: the rock it is in;
     * shapes: indices into {@link #shapes}; places: its chambers and junctions in the route graph.
     */
    public static final class Cave {
        public final int id;
        public final CaveKind kind;
        public final int layer;
        public final double x, y, z;
        public final double waterY;
        public final RockType host;
        public final List<Integer> shapes = new ArrayList<>();
        public final List<Integer> places = new ArrayList<>();
        /** Systems: the karst unit or fault it follows; giant caves: a short label (G1..). */
        public String label = "";
        public double passageLength;

        public Cave(int id, CaveKind kind, int layer, double x, double y, double z, double waterY, RockType host) {
            this.id = id;
            this.kind = kind;
            this.layer = layer;
            this.x = x;
            this.y = y;
            this.z = z;
            this.waterY = waterY;
            this.host = host;
        }

        public boolean system() { return kind == CaveKind.KARST_SYSTEM || kind == CaveKind.FRACTURE_SYSTEM; }
    }

    /** A link between two caves in the connectivity graph (7.4): the passage that makes it, kind for the report. */
    public record CaveLink(int a, int b, String kind, int step) {}

    public enum EntranceKind { COLLAPSE_PIT, COVER_SINKHOLE, GREAT_SINKHOLE, FISSURE, SHAFT, HILLSIDE_MOUTH, DRY_SPRING, MINE_ADIT, MINE_SHAFT, FACILITY }

    /** A way in from the surface (9.1). target: the cave it reaches (-1: a dead pit); place: its rim in the route graph. */
    public record Entrance(int id, EntranceKind kind, double x, double z, double surfaceY, double radius, double depth, int target, int place) {
        public boolean connected() { return target >= 0; }
    }

    public enum OreKind { COAL_SEAM, HYDROTHERMAL_VEIN, MVT_LEAD_ZINC, BANDED_IRON, NICKEL_COPPER, PEGMATITE, GREISEN_SKARN, BAUXITE, BRECCIA_PIPE }

    /** An ore district (10.2: the real mining districts the bodies gather in; mines go next to them). */
    public record District(int id, OreKind kind, double x, double z, double radius, int province) {}

    /** One ore body: an ellipsoid-ish volume (seams and blankets are flat, veins thin and tall, pipes vertical). */
    public record OreBody(int id, OreKind kind, int district, double x, double y, double z, double rx, double ry, double rz, double yaw, RockType host) {}

    /** An abandoned mine candidate (U7 builds it): adit or shaft, its levels' Y, its places. */
    public record Mine(int id, int district, EntranceKind kind, double x, double z, double surfaceY, double[] levels, int entrance, List<Integer> places) {}

    /** A part of the research facility (role names the design's list, 19.5), its box and its place. */
    public record FacilityPart(String role, Shape shape, int place, boolean surface) {}

    /**
     * The one national research facility candidate (19.5): its parts, the service road from the nearest C road to the
     * hidden entrance, the protection boxes (parts grown by the margin), and the rock it is cut in.
     */
    public record Facility(int id, double x, double z, double yaw, List<FacilityPart> parts, double[] serviceRoad,
                           double serviceGrade, List<Box> protect, RockType host, int link) {}

    public enum DocKind { RESEARCH_RECORD, ENGINEERING_LOG, ACCIDENT_REPORT, SURVEY_MAP, RADIO_MATERIAL, NOTE }

    /** Where a readable document can lie (19.6); host names the object, the text belongs to the narrative pass. */
    public record DocumentSite(int id, DocKind kind, double x, double y, double z, String host, int place) {}

    // ------------------------------------------------------------------ route graph

    /** What a step needs (19.2): light, rope length (m), anchors, a climbing kit (V3), a crawl, a dive (never planned through). */
    public record Need(boolean light, int rope, int anchors, boolean climb, boolean crawl, boolean dive) {
        public static final Need NONE = new Need(false, 0, 0, false, false, false);
        public static final Need LIGHT = new Need(true, 0, 0, false, false, false);

        public Need plus(Need o) {
            return new Need(light || o.light, Math.max(rope, o.rope), Math.max(anchors, o.anchors), climb || o.climb, crawl || o.crawl, dive || o.dive);
        }

        public String text() {
            List<String> s = new ArrayList<>();
            if (light) s.add("light");
            if (rope > 0) s.add("rope " + rope + " m");
            if (anchors > 0) s.add(anchors + " anchors");
            if (climb) s.add("climbing kit");
            if (crawl) s.add("crawl");
            if (dive) s.add("dive");
            return s.isEmpty() ? "nothing" : String.join(" + ", s);
        }
    }

    /** What a survivor carries: the kits the checks walk with (19.3). */
    public record Kit(String name, boolean light, int rope, int anchors, boolean climb) {
        public boolean meets(Need n) {
            return n != null && !n.dive && (!n.light || light) && n.rope <= rope && n.anchors <= anchors && (!n.climb || climb);
        }
    }

    /** The checks' kit ladder: nothing, a light, V2 rope work (30 / 60 m), V3 climbing. */
    public static final List<Kit> KITS = List.of(
            new Kit("none", false, 0, 0, false),
            new Kit("light", true, 0, 0, false),
            new Kit("rope30", true, 30, 4, false),
            new Kit("rope60", true, 60, 8, false),
            new Kit("rope120+climb", true, 120, 16, true));

    public enum PlaceKind { SURFACE, CHAMBER, JUNCTION, LEDGE, PITCH_TOP, PITCH_BOTTOM, FACILITY, MINE, CAMP }

    /** A standable spot (19.2): centre on the floor, half sizes, clear height above the floor. */
    public record Platform(double x, double y, double z, double hx, double hz, double headroom) {
        public double area() { return 4 * hx * hz; }
    }

    /** A place in the route graph. cave: the cave object (-1 surface / facility / mine); platform: where one stands. */
    public record Place(int id, PlaceKind kind, double x, double y, double z, int cave, Platform platform) {}

    public enum StepKind { WALK, STEP, SLOPE, CRAWL, ROPE, FIXED_ROPE, ROPE_LADDER, FIXED_LADDER, SUMP }

    /**
     * A step between two places. ab / ba: what each direction needs (a pitch needs rope down as up, so the checks find
     * a trap if they ever differ); alt: the V3 free-climb alternative (null when the rock or the height forbids it);
     * haul: how hard it is to carry a heavy load up it (0 flat .. 3 a long hauling pitch).
     */
    public record Step(int id, int a, int b, StepKind kind, double dy, double length, Need ab, Need ba, Need alt, int haul) {}

    /** A rope or ladder anchor on the wall (19.2): where, the rock it is in, whether that rock holds a bolt. */
    public record Anchor(double x, double y, double z, RockType rock) {
        public boolean good() { return rock != null && rock.anchor; }
    }

    public enum Fixture { NONE, FIXED_ROPE, ROPE_LADDER, FIXED_LADDER }

    /**
     * A vertical stage (19.2): its step(s) top to bottom (more than one when rebelay ledges split it), its height, the
     * top and bottom places, the rigging places (top, rebelay ledges) with the anchors at each, the shaft's axis and
     * radius, the fixture the world provides, the slope step that avoids it (-1 none), whether the way out needs rope or
     * climbing gear, and the heavy-load evacuation difficulty.
     */
    public record Pitch(int id, List<Integer> steps, double height, int top, int bottom, List<Integer> rigs, List<int[]> anchorsAt,
                        double ax, double az, double rh, Fixture fixture, int slopeAlt, boolean needsGearOut, int haul, int owner, boolean dark) {}

    /** One exit from a place to the surface (19.7). */
    public record Exit(int surface, Need need, int steps, double length, double climbUp, int pitches, int haul, boolean reliable, List<Integer> path) {}

    public enum Env { CAVE_CHAMBER, DEEP_CHAMBER, GIANT_CAVE, SINKHOLE_FLOOR, MINE_LEVEL, FACILITY_ROOM, SURVEY_CAMP }

    public enum Background { CAVER, SURVEYOR, HIKER, MINER, SCAVENGER, RESEARCHER, FACILITY_STAFF }

    /**
     * A survivor succession / special start candidate (19.7): data only (no takeover logic, kit, identity or story).
     * kit: the least the survivor must carry to walk out (the reliable exit's need); hazard 0 (safe) .. 4 (deadly);
     * checkpoints: points along the best exit that must be open after generation (re-checked against the real blocks).
     */
    public record Succession(int id, Env env, double x, double y, double z, int place, String host, Platform safe,
                             List<Exit> exits, Kit kit, EnumSet<Background> backgrounds, int hazard, boolean firstStart,
                             boolean succession, List<double[]> checkpoints) {}

    // ------------------------------------------------------------------ the plan

    public final long seed;
    public final int minY;
    public final List<Shape> shapes = new ArrayList<>();
    /** Shapes that are entrance throats or built works: allowed through layer 1 and exempt from the cave layer rules. */
    public final java.util.BitSet throats = new java.util.BitSet();
    public final List<Cave> caves = new ArrayList<>();
    public final List<CaveLink> links = new ArrayList<>();
    public final List<Entrance> entrances = new ArrayList<>();
    public final List<District> districts = new ArrayList<>();
    public final List<OreBody> ores = new ArrayList<>();
    public final List<Mine> mines = new ArrayList<>();
    public Facility facility;
    public final List<DocumentSite> documents = new ArrayList<>();
    public final List<Place> places = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    public final List<Pitch> pitches = new ArrayList<>();
    public final List<Anchor> anchors = new ArrayList<>();
    public final List<Succession> successions = new ArrayList<>();
    /** Planning times, ms, by stage (the check's report). */
    public final Map<String, Long> times = new java.util.LinkedHashMap<>();

    /** Planner counters (rejections by reason and the like) for the check's report. */
    public final Map<String, Integer> notes = new java.util.TreeMap<>();

    public void note(String k) { notes.merge(k, 1, Integer::sum); }
    private Map<Long, int[]> index;
    private List<List<Integer>> adjacency;

    public UndergroundPlan(long seed, int minY) {
        this.seed = seed;
        this.minY = minY;
    }

    // ------------------------------------------------------------------ index and sampling

    static long cellKey(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    /** Builds the 512 m index (call after the last shape). */
    public void index() {
        Map<Long, List<Integer>> m = new HashMap<>();
        for (int i = 0; i < shapes.size(); i++) {
            double[] b = shapes.get(i).bounds();
            int x0 = (int) Math.floor(b[0] / INDEX_CELL), x1 = (int) Math.floor(b[3] / INDEX_CELL);
            int z0 = (int) Math.floor(b[2] / INDEX_CELL), z1 = (int) Math.floor(b[5] / INDEX_CELL);
            for (int cz = z0; cz <= z1; cz++) for (int cx = x0; cx <= x1; cx++) m.computeIfAbsent(cellKey(cx, cz), k -> new ArrayList<>()).add(i);
        }
        Map<Long, int[]> out = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : m.entrySet()) out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        index = out;
    }

    /** The shape ids whose bounds overlap the index cell of (x, z) (empty array if none). */
    public int[] shapesNear(double x, double z) {
        int[] a = index.get(cellKey((int) Math.floor(x / INDEX_CELL), (int) Math.floor(z / INDEX_CELL)));
        return a == null ? new int[0] : a;
    }

    /** The cave hollow at a point (-1 = rock): any hollow shape there, unless its owner's pillar stands there. */
    public int caveAt(double x, double y, double z) {
        int[] ids = shapesNear(x, z);
        int found = -1;
        for (int i : ids) {
            Shape s = shapes.get(i);
            if (s instanceof Pillar) continue;
            double[] b = s.bounds();
            if (x < b[0] || y < b[1] || z < b[2] || x > b[3] || y > b[4] || z > b[5]) continue;
            if (s.inside(x, y, z)) { found = s.owner(); break; }
        }
        if (found < 0) return -1;
        for (int i : ids) if (shapes.get(i) instanceof Pillar p && p.owner() == found && p.inside(x, y, z)) return -1;
        return found;
    }

    public boolean airAt(double x, double y, double z) {
        int c = caveAt(x, y, z);
        return c >= 0 && y >= caves.get(c).waterY;
    }

    public boolean waterAt(double x, double y, double z) {
        int c = caveAt(x, y, z);
        return c >= 0 && y < caves.get(c).waterY;
    }

    public boolean openAt(double x, double y, double z) { return caveAt(x, y, z) >= 0; }

    // ------------------------------------------------------------------ graph helpers

    public int addPlace(PlaceKind kind, double x, double y, double z, int cave, Platform pf) {
        places.add(new Place(places.size(), kind, x, y, z, cave, pf));
        adjacency = null;
        return places.size() - 1;
    }

    public int addStep(int a, int b, StepKind kind, Need ab, Need ba, Need alt, int haul) {
        Place p = places.get(a), q = places.get(b);
        double len = Math.sqrt((p.x() - q.x()) * (p.x() - q.x()) + (p.y() - q.y()) * (p.y() - q.y()) + (p.z() - q.z()) * (p.z() - q.z()));
        steps.add(new Step(steps.size(), a, b, kind, q.y() - p.y(), len, ab, ba, alt, haul));
        adjacency = null;
        return steps.size() - 1;
    }

    /** After steps were rewritten in place (a ledge dropped for a free hang). */
    public void resetAdjacency() { adjacency = null; }

    /** Steps touching each place. */
    public List<List<Integer>> adjacency() {
        if (adjacency == null) {
            List<List<Integer>> a = new ArrayList<>();
            for (int i = 0; i < places.size(); i++) a.add(new ArrayList<>());
            for (Step s : steps) { a.get(s.a()).add(s.id()); a.get(s.b()).add(s.id()); }
            adjacency = a;
        }
        return adjacency;
    }

    /** The need to walk a step from place `from`, or with the kit's V3 alternative; null when the kit cannot. */
    public static Need needFrom(Step s, int from, Kit kit) {
        Need n = s.a() == from ? s.ab() : s.ba();
        if (kit.meets(n)) return n;
        if (s.alt() != null && kit.meets(s.alt())) return s.alt();
        return null;
    }
}
