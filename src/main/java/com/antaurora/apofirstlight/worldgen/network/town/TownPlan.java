package com.antaurora.apofirstlight.worldgen.network.town;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A framework town's plan (Town Framework V1, docs/worldgen/federal_towns_v1.md): its street grid, blocks, lots and
 * zones, no buildings. Pure Java, plain data; TownPlanner lays it out at network build, the built network's JSON carries
 * it (section "towns", appended after the routes), the server's works and every client read it back (#read).
 * <p>
 * Town coordinates: s along the axis (the interstate's tangent at the exit crossing), v across it, positive on the town's
 * side; (0, 0) is the crossing. Every street, block and lot is axis-aligned in them: parallel streets at constant v,
 * cross streets at constant s. World x = ox + s ax + v bx, z = oz + s az + v bz.
 */
public final class TownPlan {
    /** An intersection: town coordinates, the street datum there (crown height). */
    public record Node(double s, double v, double h) {}

    /** A straight street from node a to node b: its name and section (TownStreets: R12, C14, I12). */
    public record Street(String name, String cls, int a, int b) {}

    /**
     * A block (between two parallel and two cross streets' rights of way): its zones per long side, near (toward the
     * interstate, the smaller |v|) and far, either null (no lots on that side); whole: one lot of zone near (a park, a
     * school).
     */
    public record Block(double s0, double s1, double v0, double v1, String near, String far, boolean whole) {}

    /**
     * A lot: rectangle, zone, the side its street is on (FACE_V0: at v0, FACE_V1: at v1) and its pad height (the graded
     * level its building stands on).
     */
    public record Lot(double s0, double s1, double v0, double v1, String zone, int face, double pad) {}

    public static final int FACE_V0 = 0, FACE_V1 = 1;

    /**
     * Driveway widths at the curb, m (2026-10-18): a house's (12 ft), a mobile home's, a two-way commercial or civic
     * entrance (24 ft), a highway lot's (trucks), an industry lot's (semi-trailers).
     */
    public static final double DRIVE_HOUSE = 3.6, DRIVE_MOBILE = 3.0, DRIVE_TWO_WAY = 7.2, DRIVE_HWY = 9, DRIVE_IND = 12;
    /** A driveway's least distance from its lot's side lines and from its block's ends (the intersections), m. */
    public static final double DRIVE_SIDE = 1.5, DRIVE_CORNER = 9;

    /**
     * The driveways of a lot on its frontage street (the lot's face), {s0, s1} each along s; blockS0 .. blockS1 the
     * block's frontage. Pure: the works cut the curbs from it, the planner levels the pad at the first one, and the
     * buildings placed later take their entrances from it.
     * <ul>
     *   <li>one, at a side line (the lot's s0 or s1 by its place, never the one at the block's end): a house, a mobile home,
     *   civic, industry;</li>
     *   <li>one at a quarter of the frontage: a lot as wide as its block (a school: its middle may be a cross street);</li>
     *   <li>two, at a quarter and three quarters of the frontage: highway commerce, a parking lot;</li>
     *   <li>none: downtown (its lots park on the street and take the alley), a park.</li>
     * </ul>
     * Never nearer than DRIVE_CORNER to the block's ends, never nearer than DRIVE_SIDE to the lot's side lines; a
     * frontage too narrow for that has none.
     */
    public static double[][] driveways(Lot l, double blockS0, double blockS1) {
        double w;
        int count = 1;
        switch (l.zone()) {
            case "res" -> w = DRIVE_HOUSE;
            case "mobile" -> w = DRIVE_MOBILE;
            case "civic", "school" -> w = DRIVE_TWO_WAY;
            case "ind" -> w = DRIVE_IND;
            case "hwy" -> { w = DRIVE_HWY; count = 2; }
            case "park_lot" -> { w = DRIVE_TWO_WAY; count = 2; }
            default -> { return new double[0][]; }
        }
        double lo = Math.max(l.s0() + DRIVE_SIDE, blockS0 + DRIVE_CORNER), hi = Math.min(l.s1() - DRIVE_SIDE, blockS1 - DRIVE_CORNER);
        if (hi - lo < w * count) return new double[0][];
        boolean atLow = l.s0() - blockS0 < 0.5, atHigh = blockS1 - l.s1() < 0.5;
        double[] centres;
        if (count == 2) {
            double len = l.s1() - l.s0();
            centres = new double[]{l.s0() + len / 4, l.s0() + 3 * len / 4};
        } else if (atLow == atHigh) {
            // a lot on neither end: the side by its place (neighbours alternate); on both ends: the middle
            boolean low = Math.floorMod((int) Math.round(l.s0()), 2) == 0;
            centres = new double[]{atLow ? l.s0() + (l.s1() - l.s0()) / 4 : low ? lo + w / 2 : hi - w / 2};
        } else centres = new double[]{atLow ? hi - w / 2 : lo + w / 2};
        double[][] out = new double[count][];
        for (int i = 0; i < count; i++) {
            double c = Math.max(lo + w / 2, Math.min(hi - w / 2, centres[i]));
            out[i] = new double[]{c - w / 2, c + w / 2};
        }
        if (count == 2 && out[1][0] < out[0][1]) return new double[][]{out[0]};
        return out;
    }

    public final String town, name, state;
    public final double ox, oz, ax, az, bx, bz;
    /** The exit's crossroad (a built route id) or null (no exit: the grid hangs on the town point). */
    public final String exit;
    /** The town limit: a rectangle in town coordinates (s0, s1, v0, v1). */
    public final double[] limit;
    /** The exit crossroad's paved half width (its section's edge), m; 0 without an exit. */
    public final double exitHalf;
    /**
     * Where the trimmed crossroad ends at its Front St corners: {node index, v of the crossroad's end}; the corner's box
     * reaches out to it (TownWorks: the crossroad's mouth).
     */
    public final List<double[]> exitEnds;
    public final List<Node> nodes;
    public final List<Street> streets;
    public final List<Block> blocks;
    public final List<Lot> lots;

    public TownPlan(String town, String name, String state, double ox, double oz, double ax, double az, double bx, double bz, String exit,
                    double[] limit, double exitHalf, List<double[]> exitEnds, List<Node> nodes, List<Street> streets, List<Block> blocks, List<Lot> lots) {
        this.town = town;
        this.name = name;
        this.state = state;
        this.ox = ox;
        this.oz = oz;
        this.ax = ax;
        this.az = az;
        this.bx = bx;
        this.bz = bz;
        this.exit = exit;
        this.limit = limit.clone();
        this.exitHalf = exitHalf;
        List<double[]> ends = new ArrayList<>();
        for (double[] e : exitEnds) ends.add(e.clone());
        this.exitEnds = List.copyOf(ends);
        this.nodes = List.copyOf(nodes);
        this.streets = List.copyOf(streets);
        this.blocks = List.copyOf(blocks);
        this.lots = List.copyOf(lots);
    }

    /** World (x, z) of town coordinates. */
    public double[] world(double s, double v) { return new double[]{ox + s * ax + v * bx, oz + s * az + v * bz}; }

    /** Town coordinates (s, v) of a world point. */
    public double[] local(double x, double z) {
        double dx = x - ox, dz = z - oz;
        return new double[]{dx * ax + dz * az, dx * bx + dz * bz};
    }

    /** Whether town coordinates lie inside the limit grown by margin, m. */
    public boolean inside(double s, double v, double margin) {
        return s >= limit[0] - margin && s <= limit[1] + margin && v >= limit[2] - margin && v <= limit[3] + margin;
    }

    // ------------------------------------------------------------------ JSON

    /** The towns as the network JSON's "towns" array (numbers to 2 decimals, the network's format). */
    public static String write(List<TownPlan> towns) {
        StringBuilder b = new StringBuilder(1 << 16);
        b.append('[');
        for (int t = 0; t < towns.size(); t++) {
            TownPlan p = towns.get(t);
            if (t > 0) b.append(',');
            b.append("{\"town\":").append(str(p.town)).append(",\"name\":").append(str(p.name)).append(",\"state\":").append(str(p.state))
                    .append(",\"origin\":[").append(f(p.ox)).append(',').append(f(p.oz)).append("],\"axis\":[").append(f6(p.ax)).append(',').append(f6(p.az))
                    .append("],\"across\":[").append(f6(p.bx)).append(',').append(f6(p.bz)).append("],\"exit\":").append(p.exit == null ? "null" : str(p.exit))
                    .append(",\"limit\":[").append(f(p.limit[0])).append(',').append(f(p.limit[1])).append(',').append(f(p.limit[2])).append(',').append(f(p.limit[3]))
                    .append("],\"exit_half\":").append(f(p.exitHalf)).append(",\"exit_ends\":[");
            for (int i = 0; i < p.exitEnds.size(); i++) {
                double[] e = p.exitEnds.get(i);
                if (i > 0) b.append(',');
                b.append('[').append((int) e[0]).append(',').append(f(e[1])).append(']');
            }
            b.append("],\"nodes\":[");
            for (int i = 0; i < p.nodes.size(); i++) {
                Node n = p.nodes.get(i);
                if (i > 0) b.append(',');
                b.append('[').append(f(n.s())).append(',').append(f(n.v())).append(',').append(f(n.h())).append(']');
            }
            b.append("],\"streets\":[");
            for (int i = 0; i < p.streets.size(); i++) {
                Street s = p.streets.get(i);
                if (i > 0) b.append(',');
                b.append('[').append(str(s.name())).append(',').append(str(s.cls())).append(',').append(s.a()).append(',').append(s.b()).append(']');
            }
            b.append("],\"blocks\":[");
            for (int i = 0; i < p.blocks.size(); i++) {
                Block k = p.blocks.get(i);
                if (i > 0) b.append(',');
                b.append('[').append(f(k.s0())).append(',').append(f(k.s1())).append(',').append(f(k.v0())).append(',').append(f(k.v1())).append(',')
                        .append(k.near() == null ? "null" : str(k.near())).append(',').append(k.far() == null ? "null" : str(k.far())).append(',').append(k.whole()).append(']');
            }
            b.append("],\"lots\":[");
            for (int i = 0; i < p.lots.size(); i++) {
                Lot l = p.lots.get(i);
                if (i > 0) b.append(',');
                b.append('[').append(f(l.s0())).append(',').append(f(l.s1())).append(',').append(f(l.v0())).append(',').append(f(l.v1())).append(',')
                        .append(str(l.zone())).append(',').append(l.face()).append(',').append(f(l.pad())).append(']');
            }
            b.append("]}");
        }
        return b.append(']').toString();
    }

    /** The network JSON with the towns appended (a "towns" member after the routes); the JSON unchanged when there are none. */
    public static String append(String networkJson, List<TownPlan> towns) {
        if (towns.isEmpty()) return networkJson;
        int end = networkJson.lastIndexOf('}');
        return networkJson.substring(0, end) + ",\"towns\":" + write(towns) + networkJson.substring(end);
    }

    /** The towns of a network JSON (none when it has no "towns" member). */
    @SuppressWarnings("unchecked")
    public static List<TownPlan> read(String networkJson) {
        List<TownPlan> out = new ArrayList<>();
        if (!networkJson.contains("\"towns\":")) return out;
        Map<String, Object> root = (Map<String, Object>) RoadNetworkJson.parse(networkJson);
        if (!(root.get("towns") instanceof List<?> ts)) return out;
        for (Object o : ts) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<Object> og = (List<Object>) m.get("origin"), ax = (List<Object>) m.get("axis"), ac = (List<Object>) m.get("across"), li = (List<Object>) m.get("limit");
            List<double[]> ends = new ArrayList<>();
            if (m.get("exit_ends") instanceof List<?> es)
                for (Object n : es) {
                    List<Object> a = (List<Object>) n;
                    ends.add(new double[]{d(a.get(0)), d(a.get(1))});
                }
            double exitHalf = m.get("exit_half") instanceof Double h ? h : 0;
            List<Node> nodes = new ArrayList<>();
            for (Object n : (List<Object>) m.get("nodes")) {
                List<Object> a = (List<Object>) n;
                nodes.add(new Node(d(a.get(0)), d(a.get(1)), d(a.get(2))));
            }
            List<Street> streets = new ArrayList<>();
            for (Object n : (List<Object>) m.get("streets")) {
                List<Object> a = (List<Object>) n;
                streets.add(new Street((String) a.get(0), (String) a.get(1), (int) d(a.get(2)), (int) d(a.get(3))));
            }
            List<Block> blocks = new ArrayList<>();
            for (Object n : (List<Object>) m.get("blocks")) {
                List<Object> a = (List<Object>) n;
                blocks.add(new Block(d(a.get(0)), d(a.get(1)), d(a.get(2)), d(a.get(3)), (String) a.get(4), (String) a.get(5), (Boolean) a.get(6)));
            }
            List<Lot> lots = new ArrayList<>();
            for (Object n : (List<Object>) m.get("lots")) {
                List<Object> a = (List<Object>) n;
                lots.add(new Lot(d(a.get(0)), d(a.get(1)), d(a.get(2)), d(a.get(3)), (String) a.get(4), (int) d(a.get(5)), d(a.get(6))));
            }
            out.add(new TownPlan((String) m.get("town"), (String) m.get("name"), (String) m.get("state"), d(og.get(0)), d(og.get(1)), d(ax.get(0)), d(ax.get(1)),
                    d(ac.get(0)), d(ac.get(1)), (String) m.get("exit"), new double[]{d(li.get(0)), d(li.get(1)), d(li.get(2)), d(li.get(3))}, exitHalf, ends, nodes, streets, blocks, lots));
        }
        return out;
    }

    private static double d(Object o) { return (Double) o; }

    private static String f(double v) { return RoadNetworkJson.fmt(v); }

    private static String f6(double v) { return String.format(Locale.ROOT, "%.6f", v); }

    private static String str(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
