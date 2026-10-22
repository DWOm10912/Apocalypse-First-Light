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

    /** Chamber kinds (21.3): a plain dome, or a breakdown room (flat bedding ceiling, rubble heaped along the walls). */
    public static final int DOME = 0, BREAKDOWN = 1;

    /**
     * A chamber: an ellipsoid with a flat floor at floorY. room: DOME or BREAKDOWN; rough: how far the wall relief
     * pulls the walls and roof in (share of the radius, never out, so bounds and roofAt stay upper limits).
     */
    public record Ellipsoid(int owner, double x, double y, double z, double rx, double ry, double rz, double floorY, int room, double rough) implements Shape {
        public Ellipsoid(int owner, double x, double y, double z, double rx, double ry, double rz, double floorY) {
            this(owner, x, y, z, rx, ry, rz, floorY, DOME, 0);
        }

        public double[] bounds() { return new double[]{x - rx, floorY, z - rz, x + rx, y + ry, z + rz}; }

        public boolean inside(double px, double py, double pz) {
            if (py < floorY) return false;
            double a = (px - x) / rx, b = (py - y) / ry, c = (pz - z) / rz, r2 = a * a + b * b + c * c;
            if (r2 >= 1) return false;
            if (room == DOME && rough <= 0) return true;
            double cap = Double.POSITIVE_INFINITY;
            if (room == BREAKDOWN) {
                double hgt = y + ry - floorY;
                cap = floorY + 0.8 * hgt;
                if (py >= cap) return false;
                // rubble heaped along the walls; the middle (where the place stands) stays clear
                double rn = Math.sqrt(a * a + c * c);
                if (rn > 0.7) {
                    double ramp = Math.min(1, (rn - 0.7) / 0.25);
                    double mound = Math.min(3, 0.25 * hgt) * ramp * ramp * (0.45 + 0.55 * valueNoise(px / 6, 0, pz / 6, owner + 7));
                    if (py < floorY + mound) return false;
                }
            }
            double r = Math.sqrt(r2);
            if (r < 1 - rough && py < cap - rough * ry) return true;
            double wn = wallNoise(px, py, pz, owner);
            double gq = 1 - rough * wn * Math.min(1, (py - floorY) / 1.2);
            return r < gq && py < cap - rough * ry * wn;
        }

        /** The roof's Y over (px, pz), or NaN outside the footprint. */
        public double roofAt(double px, double pz) {
            double a = (px - x) / rx, c = (pz - z) / rz, q = 1 - a * a - c * c;
            return q <= 0 ? Double.NaN : y + ry * Math.sqrt(q);
        }
    }

    /** Passage sections (21.3): the old ellipse, a phreatic tube, a vadose canyon, a keyhole, a bedding-plane low passage. */
    public static final int SMOOTH = 0, TUBE = 1, CANYON = 2, KEYHOLE = 3, LOW = 4;

    /**
     * A passage or shaft: a capsule a-b with an elliptic section (half width rh, half height rv); a gentle one has a flat
     * floor. profile: the section (21.3) inside the rh x rv envelope; rough: the wall relief (share of the section,
     * pulled in only); stream: depth of a water trench under the floor (0 none), kept `clear` m from both ends.
     */
    public record Capsule(int owner, double ax, double ay, double az, double bx, double by, double bz, double rh, double rv,
                          int profile, double rough, double stream, double clear) implements Shape {
        public Capsule(int owner, double ax, double ay, double az, double bx, double by, double bz, double rh, double rv) {
            this(owner, ax, ay, az, bx, by, bz, rh, rv, SMOOTH, 0, 0, 0);
        }

        public double[] bounds() {
            double below = Math.max(rv, (rv >= 1.5 ? 0.7 * rv : rv) + stream);
            return new double[]{Math.min(ax, bx) - rh, Math.min(ay, by) - below, Math.min(az, bz) - rh,
                    Math.max(ax, bx) + rh, Math.max(ay, by) + rv, Math.max(az, bz) + rh};
        }

        /** Clear height over the floor in the middle of the section, the relief taken at its worst (walk's sump test). */
        public double headroom() {
            double top = switch (profile) {
                case TUBE -> 0.75;
                case LOW -> 0.6;
                default -> 1;
            };
            return ((rv >= 1.5 ? 0.7 : 1) + top * (1 - rough)) * rv;
        }

        /** Whether the point is in the stream trench (water whatever the cave's water level). */
        public boolean inTrench(double px, double py, double pz) { return stream > 0 && where(px, py, pz) == 2; }

        public double length() { return Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)); }

        public boolean vertical() { return Math.abs(by - ay) > 1.5 * Math.hypot(bx - ax, bz - az); }

        public boolean inside(double px, double py, double pz) {
            if (profile != SMOOTH || rough > 0 || stream > 0) {
                double lh = Math.hypot(bx - ax, bz - az);
                if (lh > 1e-6 && !vertical()) return where(px, py, pz) != 0;
            }
            double k = rh / rv;
            double ux = bx - ax, uy = (by - ay) * k, uz = bz - az, l2 = ux * ux + uy * uy + uz * uz;
            double qx = px - ax, qy = (py - ay) * k, qz = pz - az;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (qx * ux + qy * uy + qz * uz) / l2));
            double dx = qx - ux * t, dy = qy - uy * t, dz = qz - uz * t;
            if (dx * dx + dy * dy + dz * dz >= rh * rh) return false;
            if (rv >= 1.5 && !vertical()) return py >= ay + (by - ay) * t - 0.7 * rv;
            return true;
        }

        /**
         * The shaped section in the passage's own frame (along s, lateral d, up v from the axis): 0 rock, 1 open,
         * 2 the stream trench. Gentle passages only (shafts keep the plain capsule).
         */
        int where(double px, double py, double pz) {
            double ux = bx - ax, uz = bz - az, lh = Math.hypot(ux, uz), hx = ux / lh, hz = uz / lh;
            double qx = px - ax, qz = pz - az;
            double sa = qx * hx + qz * hz, t = Math.max(0, Math.min(1, sa / lh));
            double e = sa < 0 ? -sa : sa > lh ? sa - lh : 0;
            double d = -qx * hz + qz * hx, v = py - (ay + (by - ay) * t);
            double fl = rv >= 1.5 ? -0.7 * rv : -rv;
            // a canyon swings from wall to wall; its trench follows the swing
            double m = 0;
            if (profile == CANYON || profile == KEYHOLE) {
                double ph = 6.2832 * frac(owner * 0.618 + ax * 0.0137 + az * 0.0071), wl = 16 + 10 * frac(owner * 0.377 + 0.5);
                m = (profile == CANYON ? 0.32 : 0.16) * Math.sin(6.2832 * sa / wl + ph) + (profile == CANYON ? 0.1 : 0.05) * Math.sin(14.5 * sa / wl + 1.3 * ph);
            }
            if (v < fl) {
                if (stream <= 0 || v < fl - stream || sa < clear || sa > lh - clear) return 0;
                return Math.abs(d / rh - m) * rh < 0.55 + 0.1 * rh ? 2 : 0;
            }
            double X = d / rh, Y = v / rv, E = e / rh;
            double r = switch (profile) {
                case TUBE -> Math.max(Math.sqrt(X * X + Y * Y + E * E), Y / 0.75);
                case CANYON -> {
                    double w = 0.55 * (1 - 0.45 * Math.max(0, Y) * Math.max(0, Y));
                    yield Math.max(Math.hypot((X - m) / w, E / w), Y);
                }
                case KEYHOLE -> {
                    double tube = Math.sqrt(X * X + (Y - 0.35) * (Y - 0.35) / (0.65 * 0.65) + E * E);
                    double slot = Y <= 0.35 ? Math.hypot((X - m) / 0.32, E / 0.32) : Double.POSITIVE_INFINITY;
                    yield Math.min(tube, slot);
                }
                case LOW -> Math.max(Math.sqrt(X * X * X * X + E * E * E * E + Y * Y), Y / 0.6);
                default -> Math.sqrt(X * X + Y * Y + E * E);
            };
            if (r >= 1) return 0;
            if (r < 1 - rough) return 1;
            double wn = wallNoise(px, py, pz, owner);
            return r < 1 - rough * wn * Math.min(1, (v - fl) / 1.2) ? 1 : 0;
        }
    }

    static double frac(double x) { return x - Math.floor(x); }

    /**
     * The wall relief (21.3), 0..1: a bump field of about 4.5 m (squashed along the bedding), a finer one of 1.7 m, and
     * bedding ledges every 2.4 m of height. Pure function of the point and the cave, so every chunk agrees.
     */
    static double wallNoise(double x, double y, double z, int owner) {
        double n = 0.6 * valueNoise(x / 4.5, y / 3.0, z / 4.5, owner) + 0.25 * valueNoise(x / 1.7, y / 1.7, z / 1.7, owner + 1);
        return Math.min(1, n + 0.15 * frac((y + 0.37 * owner) / 2.4));
    }

    /** Trilinear value noise on the unit lattice, 0..1. */
    static double valueNoise(double x, double y, double z, int salt) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double c00 = lerp(lattice(ix, iy, iz, salt), lattice(ix + 1, iy, iz, salt), fx);
        double c10 = lerp(lattice(ix, iy + 1, iz, salt), lattice(ix + 1, iy + 1, iz, salt), fx);
        double c01 = lerp(lattice(ix, iy, iz + 1, salt), lattice(ix + 1, iy, iz + 1, salt), fx);
        double c11 = lerp(lattice(ix, iy + 1, iz + 1, salt), lattice(ix + 1, iy + 1, iz + 1, salt), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double lattice(int x, int y, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
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

    /** SPRING_MOUTH, KARST_WINDOW, CLIFF_MOUTH, WAVE_CUT: the states' own ways in (design 21.10). */
    public enum EntranceKind { COLLAPSE_PIT, COVER_SINKHOLE, GREAT_SINKHOLE, FISSURE, SHAFT, HILLSIDE_MOUTH, DRY_SPRING, MINE_ADIT, MINE_SHAFT, FACILITY,
        SPRING_MOUTH, KARST_WINDOW, CLIFF_MOUTH, WAVE_CUT }

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
    /** The start dry cave (21.6): the system near the home that a light reaches from the surface, -1 none. */
    public int startCave = -1;
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
        int i = shapeAt(x, y, z);
        return i < 0 ? -1 : shapes.get(i).owner();
    }

    /** The hollow shape at a point (-1 = rock), as {@link #caveAt}. */
    public int shapeAt(double x, double y, double z) { return shapeAmong(shapesNear(x, z), x, y, z); }

    /** The shape ids whose bounds overlap the column box [x0, x1] x [z0, z1] (U2: one chunk's list, built once per chunk). */
    public int[] shapesInBox(double x0, double z0, double x1, double z1) {
        java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
        for (int cz = (int) Math.floor(z0 / INDEX_CELL); cz <= (int) Math.floor(z1 / INDEX_CELL); cz++)
            for (int cx = (int) Math.floor(x0 / INDEX_CELL); cx <= (int) Math.floor(x1 / INDEX_CELL); cx++) {
                int[] a = index.get(cellKey(cx, cz));
                if (a == null) continue;
                for (int i : a) {
                    double[] b = shapes.get(i).bounds();
                    if (b[3] >= x0 && b[0] <= x1 && b[5] >= z0 && b[2] <= z1) out.add(i);
                }
            }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** {@link #caveAt} over a given shape list (a chunk's, from {@link #shapesInBox}). */
    public int caveAmong(int[] ids, double x, double y, double z) {
        int i = shapeAmong(ids, x, y, z);
        return i < 0 ? -1 : shapes.get(i).owner();
    }

    /**
     * The hollow shape at a point among the ids (-1 = rock): the first that holds it, a stream trench only when no
     * other shape opens the point; -1 when the owner's pillar stands there.
     */
    public int shapeAmong(int[] ids, double x, double y, double z) {
        int found = -1, trench = -1;
        for (int i : ids) {
            Shape s = shapes.get(i);
            if (s instanceof Pillar) continue;
            double[] b = s.bounds();
            if (x < b[0] || y < b[1] || z < b[2] || x > b[3] || y > b[4] || z > b[5]) continue;
            if (!s.inside(x, y, z)) continue;
            if (s instanceof Capsule c && c.inTrench(x, y, z)) { if (trench < 0) trench = i; continue; }
            found = i;
            break;
        }
        if (found < 0) found = trench;
        if (found < 0) return -1;
        int owner = shapes.get(found).owner();
        for (int i : ids) if (shapes.get(i) instanceof Pillar p && p.owner() == owner && p.inside(x, y, z)) return -1;
        return found;
    }

    /** Whether a hollow point of the shape holds water: under its cave's water level, or in a stream trench (21.2). */
    public boolean wetAt(int shape, double x, double y, double z) {
        Shape s = shapes.get(shape);
        return y < caves.get(s.owner()).waterY || s instanceof Capsule c && c.inTrench(x, y, z);
    }

    public boolean airAt(double x, double y, double z) {
        int i = shapeAt(x, y, z);
        return i >= 0 && !wetAt(i, x, y, z);
    }

    public boolean waterAt(double x, double y, double z) {
        int i = shapeAt(x, y, z);
        return i >= 0 && wetAt(i, x, y, z);
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
