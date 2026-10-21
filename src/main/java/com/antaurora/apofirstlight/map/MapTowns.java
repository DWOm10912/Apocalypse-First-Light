package com.antaurora.apofirstlight.map;

import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCounty;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownStreets;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import java.util.ArrayList;
import java.util.List;

/**
 * The towns as the map draws them (AFL Map V1, docs/ui/afl_map_v1_plan.md 3-4), worked out once from the road network
 * the client already has: each town's limit, where its name goes (the middle of its lots), its streets as world segments
 * with their names and widths, its building sites (white boxes, landmarks, Fuel Stop A1); and the forest home's buildings
 * (user 2026-10-21: the old house and its driveway belong on the map; the driveway is drawn from RoadWorks#drive).
 */
public final class MapTowns {
    public record Street(String name, double ax, double az, double bx, double bz, double asphalt) {
        public double length() { return Math.hypot(bx - ax, bz - az); }
    }

    public record Town(TownPlan plan, double labelX, double labelZ, double x0, double z0, double x1, double z1,
                       List<Street> streets, List<TownBuildings.Site> sites) {
        /** Inside the town's limit. */
        public boolean inside(double x, double z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return false;
            double[] l = plan.local(x, z);
            return l[0] >= plan.limit[0] && l[0] <= plan.limit[1] && l[1] >= plan.limit[2] && l[1] <= plan.limit[3];
        }

        public String id() { return plan.town; }
    }

    public final List<Town> towns;
    /** Buildings outside the towns {x0, z0, x1, z1} (world, axis-aligned): the forest home's roofs. */
    public final List<double[]> buildings;

    private MapTowns(List<Town> towns, List<double[]> buildings) { this.towns = towns; this.buildings = buildings; }

    public static final MapTowns EMPTY = new MapTowns(List.of(), List.of());

    /**
     * The forest home's roofs in its paper frame {X0, Z0, X1, Z1} (cells, X1 / Z1 exclusive), from the M1 build
     * (tools/afl_minecraft_mcp/forest_home_b_m1.mjs): the main roof with the porch, the addition's shed roof, the woodshed
     * and genset bay, the carport, the outhouse. Template frame = paper + (ForestHomeLayout.PX, PZ).
     */
    static final int[][] HOME_ROOFS = {{-1, -1, 13, 13}, {13, -1, 19, 9}, {19, -5, 27, 0}, {19, 1, 26, 10}, {-6, -8, -3, -5}};

    /** The forest home's roofs in the world for its site (none without one). */
    static List<double[]> home(RoadCounty.Home home) {
        if (home == null) return List.of();
        int r = home.rotation();
        int[] o = ForestHomeLayout.origin(home.x(), home.z(), r);
        List<double[]> out = new ArrayList<>();
        for (int[] b : HOME_ROOFS) {
            double[] a = ForestHomeLayout.world(o[0], o[1], r, b[0] + ForestHomeLayout.PX, b[1] + ForestHomeLayout.PZ);
            double[] c = ForestHomeLayout.world(o[0], o[1], r, b[2] + ForestHomeLayout.PX, b[3] + ForestHomeLayout.PZ);
            out.add(new double[]{Math.min(a[0], c[0]), Math.min(a[1], c[1]), Math.max(a[0], c[0]), Math.max(a[1], c[1])});
        }
        return out;
    }

    public static MapTowns of(RoadWorks works, RoadCounty.Home home) {
        if (works == null) return EMPTY;
        TownWorks tw = works.towns();
        List<Town> out = new ArrayList<>();
        for (TownPlan p : tw.plans()) {
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
            for (double s : new double[]{p.limit[0], p.limit[1]})
                for (double v : new double[]{p.limit[2], p.limit[3]}) {
                    double[] w = p.world(s, v);
                    x0 = Math.min(x0, w[0]); x1 = Math.max(x1, w[0]); z0 = Math.min(z0, w[1]); z1 = Math.max(z1, w[1]);
                }
            double lx = 0, lz = 0;
            for (TownPlan.Lot l : p.lots) {
                double[] w = p.world((l.s0() + l.s1()) / 2, (l.v0() + l.v1()) / 2);
                lx += w[0];
                lz += w[1];
            }
            if (p.lots.isEmpty()) {
                double[] w = p.world((p.limit[0] + p.limit[1]) / 2, (p.limit[2] + p.limit[3]) / 2);
                lx = w[0];
                lz = w[1];
            } else {
                lx /= p.lots.size();
                lz /= p.lots.size();
            }
            List<Street> streets = new ArrayList<>();
            for (TownPlan.Street st : p.streets) {
                TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
                double[] wa = p.world(a.s(), a.v()), wb = p.world(b.s(), b.v());
                streets.add(new Street(st.name(), wa[0], wa[1], wb[0], wb[1], TownStreets.of(st.cls()).asphalt()));
            }
            List<TownBuildings.Site> sites;
            try {
                sites = TownBuildings.sites(p, tw.fuelStop(p));
            } catch (RuntimeException e) {
                sites = List.of();
            }
            out.add(new Town(p, lx, lz, x0, z0, x1, z1, streets, sites));
        }
        return new MapTowns(out, home(home));
    }

    /** The town whose limit holds (x, z), or null. */
    public Town at(double x, double z) {
        for (Town t : towns) if (t.inside(x, z)) return t;
        return null;
    }
}
