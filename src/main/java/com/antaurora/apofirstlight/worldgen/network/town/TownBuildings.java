package com.antaurora.apofirstlight.worldgen.network.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which building stands on each lot of a framework town and where (Town buildings V1, 2026-10-19,
 * docs/worldgen/town_buildings_v1.md): one site per lot, a pure function of the town's plan (no chunk, no load order).
 * The sites are reservations: a footprint in whole blocks on the lot's pad, the side it fronts (its street), its floor's
 * Y and a seed; a kit (worldgen.building) draws the building into them chunk by chunk.
 * <ul>
 *   <li>the kind by zone (T3, docs/worldgen/federal_towns_v1.md 4): a house (the big one on a lot of BIG_HOUSE_LOT or
 *   wider), a mobile home, a shop as wide as its lot, a highway store, a civic building, a warehouse, the school, a
 *   landmark's placeholder (2026-10-20); parks and parking lots none;</li>
 *   <li>the footprint: the kind's width along the street (narrowed to what the lot leaves, never under MIN_WIDTH) and
 *   depth into the lot, its front setback from the street's lot line; along the street a house keeps its driveway
 *   (TownPlan#driveways) and DRIVE_GAP clear on that side and stands in the middle of the rest; other kinds in the
 *   middle of the lot;</li>
 *   <li>whole blocks: the grid runs on the block grid (TownGrid), so the footprint is the blocks it covers, kept inside the
 *   lot; the floor (the first air layer inside) one over the pad's block.</li>
 *   <li>Fuel Stop A1 (2026-10-20, #fuelStops, TownWorks#fuelStop): one highway lot of a town, a corner whose side street
 *   is on the right as one looks from the front street (the template is never mirrored), takes the gas_station_01
 *   template whole, 64 x 64 at that corner; of those the works pick the one whose ground round it lies nearest the
 *   template's flat surface; its driveways are the template's two entrances (TownWorks cuts the curbs). It sits in the
 *   ground (2026-10-20, user's first look): its surface level over the footprint, but the two grass strips along its
 *   streets (#bank) bank from the sidewalk down or up to it; the block's ground round its other sides meets its edge; the
 *   aprons climb to its surface at the lot line.</li>
 * </ul>
 */
public final class TownBuildings {
    private TownBuildings() {}

    /**
     * The placement rules' version (the world lock's town_buildings system, with the kits'). 7 (2026-10-21): house_small
     * 17 deep from a 6.5 m setback (was 16 from 7.5: the house stays where it was, its two-cell stoop steps in front).
     */
    public static final int VERSION = 7;

    /**
     * A kind of building: its footprint along the street (width) and into the lot (depth), storeys, front setback, the least
     * side yard, m.
     */
    public record Kind(String id, double width, double depth, int storeys, double front, double side) {}

    /** The Fuel Stop A1 kind and its template (structures/gas_station_01.nbt, 64 x 16 x 64, front north, docs/worldgen/gas_station_01_asset_v1.md). */
    public static final String FUEL_STOP = "fuel_stop_a1", FUEL_STOP_TEMPLATE = "gas_station_01";
    /** The template's size on the ground, blocks; its ground layer (the surface's block) and the first air layer over it. */
    public static final int A1 = 64, A1_GROUND = 4, A1_FLOOR = 5;
    /** The template's entrances (its DRIVEWAY sockets main and side): x 47 .. 55 on its front row z 0; z 57 .. 62 on its west column x 0. */
    static final int A1_MAIN_X0 = 47, A1_MAIN_X1 = 55, A1_SIDE_Z0 = 57, A1_SIDE_Z1 = 62;
    /**
     * The template's grass strips along its streets (#bank): its front rows z 0 .. A1_BANK - 1 and its west columns x 0 ..
     * A1_BANK - 1, but for the entrances and the curbs either side of them; their ground banks to the sidewalk, never more
     * than A1_BANK_LOW under the surface (the feed cables under the strips lie one block down), m.
     */
    public static final int A1_BANK = 3;
    public static final double A1_BANK_LOW = 0.95;

    /** Whether template cell (x, z) is in one of the grass strips along the streets, banked to the sidewalk. */
    public static boolean bank(int x, int z) {
        if (x < 0 || z < 0 || x >= A1 || z >= A1) return false;
        return z < A1_BANK && (x < A1_MAIN_X0 - 1 || x > A1_MAIN_X1 + 1) || x < A1_BANK && (z < A1_SIDE_Z0 - 1 || z > A1_SIDE_Z1 + 1);
    }

    /** The kinds by zone (T3 sizes; setbacks after north_american_roads_and_lots_spec_v1.md 5). */
    public static final Map<String, Kind> KINDS = Map.of(
            "house_small", new Kind("house_small", 14, 17, 1, 6.5, 1.5),
            "house_large", new Kind("house_large", 20, 20, 2, 7.5, 1.5),
            "mobile_home", new Kind("mobile_home", 5, 20, 1, 3, 2),
            "shop", new Kind("shop", 999, 22, 2, 0, 0),
            "highway_store", new Kind("highway_store", 24, 16, 1, 14, 6),
            "civic_hall", new Kind("civic_hall", 28, 20, 2, 10, 4),
            "warehouse", new Kind("warehouse", 40, 32, 1, 12, 6),
            "school", new Kind("school", 60, 40, 2, 15, 10),
            // a landmark's block (TownSites#landmarks), until its own building is designed (2026-10-20)
            "landmark", new Kind("landmark", 40, 30, 2, 12, 8),
            // Fuel Stop A1's whole template on its corner lot (#fuelStop, 2026-10-20)
            FUEL_STOP, new Kind(FUEL_STOP, A1, A1, 1, 0, 0));


    /** A house lot this wide or wider takes the big house, m. */
    public static final double BIG_HOUSE_LOT = 30;
    /** The narrowest building a lot may squeeze its kind to, m. */
    public static final double MIN_WIDTH = 8;
    /** A house's clearance from its driveway's far edge, m. */
    public static final double DRIVE_GAP = 1;
    /** The least yard behind a building, m. */
    public static final double REAR = 1;

    /** The kind on a lot of zone, frontage width m wide, or null (none). */
    public static Kind kind(String zone, double width) {
        return switch (zone) {
            case "res" -> KINDS.get(width >= BIG_HOUSE_LOT ? "house_large" : "house_small");
            case "mobile" -> KINDS.get("mobile_home");
            case "down" -> KINDS.get("shop");
            case "hwy" -> KINDS.get("highway_store");
            case "civic" -> KINDS.get("civic_hall");
            case "ind" -> KINDS.get("warehouse");
            case "school" -> KINDS.get("school");
            default -> zone.startsWith(TownPlan.LANDMARK) ? KINDS.get("landmark") : null;
        };
    }

    /**
     * A building's site: the town and lot (its index in TownPlan.lots), zone and kind; its footprint, world blocks x0 .. x1,
     * z0 .. z1 inclusive; floorY the first air layer inside (the floor block under it); (fx, fz) the way its front faces
     * (toward its street, a world axis); a seed for the kit's choices.
     */
    public record Site(String town, int lot, String zone, String kind, int x0, int z0, int x1, int z1, int floorY, int fx, int fz, long seed) {
        /** The footprint along the front (the street) and into the lot, blocks. */
        public int width() { return fx != 0 ? z1 - z0 + 1 : x1 - x0 + 1; }

        public int depth() { return fx != 0 ? x1 - x0 + 1 : z1 - z0 + 1; }
    }

    /**
     * Every building site of a town (none when its grid is not on the block grid: a network built before 2026-10-19); fs
     * the town's Fuel Stop A1 (TownWorks#fuelStop) or null.
     */
    public static List<Site> sites(TownPlan p, FuelStop fs) {
        List<Site> out = new ArrayList<>();
        if (!onBlockGrid(p)) return out;
        for (int i = 0; i < p.lots.size(); i++) {
            Site s = fs != null && fs.lot() == i ? fs.site() : site(p, i);
            if (s != null) out.add(s);
        }
        return out;
    }

    /**
     * A town's Fuel Stop A1: its lot, its site (the template's footprint, front toward the lot's street), the main
     * entrance's driveway along the front street (s0, s1) and the side entrance's along the side street (v0, v1) at the
     * block's end sideEdge, that street toward sideDir along s.
     */
    public record FuelStop(int lot, Site site, double[] main, double[] side, double sideEdge, int sideDir) {
        /** The same stop on a floor of its own (TownWorks#fuelStop sets it from the sidewalk at the main entrance). */
        public FuelStop withFloor(int floorY) {
            Site s = site;
            return new FuelStop(lot, new Site(s.town(), s.lot(), s.zone(), s.kind(), s.x0(), s.z0(), s.x1(), s.z1(), floorY, s.fx(), s.fz(), s.seed()),
                    main, side, sideEdge, sideDir);
        }

        /** The template cell {x, z} at world block column (bx, bz), or null off the footprint (template x = A1 - 1 - u, z = w). */
        public int[] cell(int bx, int bz) {
            int dx = -site.fx(), dz = -site.fz(), rx = -dz, rz = dx;
            int cx = rx + dx > 0 ? site.x0() : site.x1(), cz = rz + dz > 0 ? site.z0() : site.z1();
            int u = (bx - cx) * rx + (bz - cz) * rz, w = (bx - cx) * dx + (bz - cz) * dz;
            return u < 0 || w < 0 || u >= A1 || w >= A1 ? null : new int[]{A1 - 1 - u, w};
        }

        /** The world block column {x, z} of template cell (x, z). */
        public int[] world(int x, int z) {
            int dx = -site.fx(), dz = -site.fz(), rx = -dz, rz = dx;
            int cx = rx + dx > 0 ? site.x0() : site.x1(), cz = rz + dz > 0 ? site.z0() : site.z1(), u = A1 - 1 - x;
            return new int[]{cx + rx * u + dx * z, cz + rz * u + dz * z};
        }

        /** The template cell under town point (s, v), or null. */
        public int[] cell(TownPlan p, double s, double v) {
            double[] w = p.world(s, v);
            return cell((int) Math.floor(w[0]), (int) Math.floor(w[1]));
        }

        /**
         * Whether town point (s, v) is on the footprint's level part (off the banked strips) or within margin metres outside
         * it, its nearest cell not banked: the ground there is the floor (the edge meets the ground round it level).
         */
        public boolean level(TownPlan p, double s, double v, double margin) {
            double[] w = p.world(s, v);
            int dx = -site.fx(), dz = -site.fz(), rx = -dz, rz = dx;
            int cx = rx + dx > 0 ? site.x0() : site.x1(), cz = rz + dz > 0 ? site.z0() : site.z1();
            // the cell coordinates of the point (cell centres at .5)
            double u = (w[0] - cx - 0.5) * rx + (w[1] - cz - 0.5) * rz, ww = (w[0] - cx - 0.5) * dx + (w[1] - cz - 0.5) * dz;
            if (u < -margin || ww < -margin || u > A1 - 1 + margin || ww > A1 - 1 + margin) return false;
            int iu = (int) Math.round(Math.max(0, Math.min(A1 - 1, u))), iw = (int) Math.round(Math.max(0, Math.min(A1 - 1, ww)));
            return !bank(A1 - 1 - iu, iw);
        }

        /** Whether town point (s, v) is on a banked strip. */
        public boolean banked(TownPlan p, double s, double v) {
            int[] c = cell(p, s, v);
            return c != null && bank(c[0], c[1]);
        }
    }

    /**
     * The lots that could take the town's Fuel Stop A1 (2026-10-20), nearest the exit's crossing (town (0, 0)) first: its
     * highway lots (zone hwy) at a block's end on their right as one looks from their street (the template's side
     * entrance faces west from a north front: the cross street must be on that side; the template is never mirrored, its
     * PRAIRIE letters and price signs would read backwards) and at least A1 x A1 blocks. The template stands in the lot's
     * front right corner, the rest of a wider lot left graded. Its floor here: the pad's sidewalk height, rounded
     * (TownWorks#fuelStop picks one and sets its floor from the works).
     */
    public static List<FuelStop> fuelStops(TownPlan p) {
        List<FuelStop> out = new ArrayList<>();
        List<Double> ranks = new ArrayList<>();
        if (!onBlockGrid(p)) return out;
        for (int i = 0; i < p.lots.size(); i++) {
            TownPlan.Lot l = p.lots.get(i);
            if (!"hwy".equals(l.zone())) continue;
            TownPlan.Block b = block(p, l);
            if (b == null) continue;
            double sign = l.face() == TownPlan.FACE_V0 ? -1 : 1;
            int fx = (int) Math.round(sign * p.bx), fz = (int) Math.round(sign * p.bz);
            // the right of someone at the street facing the lot (the way in is -f)
            int rx = fz, rz = -fx;
            boolean high = rx * p.ax + rz * p.az > 0;   // the right end is the lot's s1
            if (high ? Math.abs(b.s1() - l.s1()) > 0.5 : Math.abs(l.s0() - b.s0()) > 0.5) continue;
            int[] in = blocks(p, l.s0(), l.s1(), l.v0(), l.v1());
            if (in[2] - in[0] + 1 < A1 || in[3] - in[1] + 1 < A1) continue;
            int x0 = fx != 0 ? (fx < 0 ? in[0] : in[2] - A1 + 1) : (rx > 0 ? in[2] - A1 + 1 : in[0]);
            int z0 = fz != 0 ? (fz < 0 ? in[1] : in[3] - A1 + 1) : (rz > 0 ? in[3] - A1 + 1 : in[1]);
            double vFront = l.face() == TownPlan.FACE_V0 ? l.v0() : l.v1(), sEnd = high ? l.s1() : l.s0();
            double rank = Math.abs(sEnd) + Math.abs(vFront);
            int floorY = (int) Math.round(l.pad() - TownPlanner.PAD - TownStreets.CROWN + TownStreets.CURB_HEIGHT);
            Site site = new Site(p.town, i, l.zone(), FUEL_STOP, x0, z0, x0 + A1 - 1, z0 + A1 - 1, floorY, fx, fz, seed(p.town, i));
            // the entrances in town coordinates: template (x, z) is the site's (u, w) = (A1 - 1 - x, z), north-facing
            double[] main = span(p, site, A1 - 1 - A1_MAIN_X1, 0, A1 - 1 - A1_MAIN_X0, 0, true);
            double[] side = span(p, site, A1 - 1, A1_SIDE_Z0, A1 - 1, A1_SIDE_Z1, false);
            int at = 0;
            while (at < ranks.size() && ranks.get(at) <= rank) at++;
            ranks.add(at, rank);
            out.add(at, new FuelStop(i, site, main, side, high ? b.s1() : b.s0(), high ? 1 : -1));
        }
        return out;
    }

    /** The span along s (alongS) or v of a site's cells (u0, w0) .. (u1, w1) in town coordinates, their outer edges. */
    private static double[] span(TownPlan p, Site site, int u0, int w0, int u1, int w1, boolean alongS) {
        // the site's frame as BuildingCanvas lays it: into the lot -f, u to the right of someone facing the building
        int dx = -site.fx(), dz = -site.fz(), rx = -dz, rz = dx;
        int cx = rx + dx > 0 ? site.x0() : site.x1(), cz = rz + dz > 0 ? site.z0() : site.z1();
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int[] c : new int[][]{{u0, w0}, {u1, w1}}) {
            double[] t = p.local(cx + rx * c[0] + dx * c[1] + 0.5, cz + rz * c[0] + dz * c[1] + 0.5);
            double a = alongS ? t[0] : t[1];
            lo = Math.min(lo, a - 0.5);
            hi = Math.max(hi, a + 0.5);
        }
        return new double[]{lo, hi};
    }

    /** Whether the plan's axes are world axes (TownGrid). */
    public static boolean onBlockGrid(TownPlan p) {
        return Math.abs(Math.abs(p.ax) + Math.abs(p.az) - 1) < 1e-9 && (p.ax == 0 || p.az == 0)
                && Math.abs(Math.abs(p.bx) + Math.abs(p.bz) - 1) < 1e-9 && (p.bx == 0 || p.bz == 0);
    }

    /** Lot i's site by its kind, or null (no building: a park, a parking lot, a lot too small for its kind). */
    public static Site site(TownPlan p, int i) {
        TownPlan.Lot l = p.lots.get(i);
        double w = l.s1() - l.s0(), d = l.v1() - l.v0();
        Kind k = kind(l.zone(), w);
        if (k == null) return null;
        // along the street: a house beside its driveway, the rest in the middle
        double lo = l.s0() + k.side(), hi = l.s1() - k.side();
        if ("house_small".equals(k.id()) || "house_large".equals(k.id()) || "mobile_home".equals(k.id())) {
            TownPlan.Block b = block(p, l);
            double[][] dw = b == null ? new double[0][] : TownPlan.driveways(l, b.s0(), b.s1());
            if (dw.length > 0) {
                if ((dw[0][0] + dw[0][1]) / 2 < (l.s0() + l.s1()) / 2) lo = Math.max(lo, dw[0][1] + DRIVE_GAP);
                else hi = Math.min(hi, dw[0][0] - DRIVE_GAP);
            }
        }
        double width = Math.min(k.width(), hi - lo);
        if (width < Math.min(MIN_WIDTH, k.width())) return null;
        double sa = (lo + hi) / 2 - width / 2, sb = sa + width;
        // into the lot: from the street's lot line, the front setback, then the depth (a yard of REAR kept behind)
        double depth = Math.min(k.depth(), d - k.front() - REAR);
        if (depth < MIN_WIDTH) return null;
        double va, vb;
        if (l.face() == TownPlan.FACE_V0) { va = l.v0() + k.front(); vb = va + depth; }
        else { vb = l.v1() - k.front(); va = vb - depth; }
        // whole blocks, inside the lot
        int[] r = blocks(p, sa, sb, va, vb), in = blocks(p, l.s0(), l.s1(), l.v0(), l.v1());
        int x0 = Math.max(r[0], in[0]), z0 = Math.max(r[1], in[1]), x1 = Math.min(r[2], in[2]), z1 = Math.min(r[3], in[3]);
        if (x1 - x0 + 1 < MIN_WIDTH && z1 - z0 + 1 < MIN_WIDTH) return null;
        // the front: toward the street (smaller v at FACE_V0)
        double sign = l.face() == TownPlan.FACE_V0 ? -1 : 1;
        int fx = (int) Math.round(sign * p.bx), fz = (int) Math.round(sign * p.bz);
        int floorY = (int) Math.floor(l.pad()) + 1;
        return new Site(p.town, i, l.zone(), k.id(), x0, z0, x1, z1, floorY, fx, fz, seed(p.town, i));
    }

    /** The block a lot lies in, or null. */
    static TownPlan.Block block(TownPlan p, TownPlan.Lot l) {
        for (TownPlan.Block b : p.blocks)
            if (l.s0() >= b.s0() - 0.01 && l.s1() <= b.s1() + 0.01 && l.v0() >= b.v0() - 0.01 && l.v1() <= b.v1() + 0.01) return b;
        return null;
    }

    /**
     * The world blocks a town rectangle covers by their middles: {x0, z0, x1, z1} inclusive (a block whose middle lies
     * inside).
     */
    static int[] blocks(TownPlan p, double s0, double s1, double v0, double v1) {
        double[] a = p.world(s0, v0), b = p.world(s1, v1);
        double xa = Math.min(a[0], b[0]), xb = Math.max(a[0], b[0]), za = Math.min(a[1], b[1]), zb = Math.max(a[1], b[1]);
        return new int[]{(int) Math.ceil(xa - 0.5), (int) Math.ceil(za - 0.5), (int) Math.floor(xb - 0.5), (int) Math.floor(zb - 0.5)};
    }

    /** A lot's seed: the town and the lot's index, mixed (SplitMix64). */
    static long seed(String town, int lot) {
        long z = town.hashCode() * 0x9E3779B97F4A7C15L + lot;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
