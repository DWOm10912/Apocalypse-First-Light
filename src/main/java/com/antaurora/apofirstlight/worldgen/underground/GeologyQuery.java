package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.underground.GeologyModel.Column;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Entrance;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreBody;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Succession;

import java.util.ArrayList;
import java.util.List;

/**
 * The read-only geology and underground query of a seed (docs/worldgen/underground_geology_caves_v0_design.md 19.1):
 * what rock, unit and layer a point is in, the soil and the water table of a column, the nearest fault, whether a
 * point is planned cave air or water, the openings, ore bodies, entrances and succession candidates near a point,
 * and the C works' protection (zone and roof limit). U1 only answers questions: nothing in the game calls it yet and
 * it never changes a block. E asks rock and soil here (19.12) instead of reading C's internals.
 */
public final class GeologyQuery {
    public final GeologyModel geology;
    public final GeoProtect protect;
    public final UndergroundPlan plan;

    public GeologyQuery(GeologyModel geology, GeoProtect protect, UndergroundPlan plan) {
        this.geology = geology;
        this.protect = protect;
        this.plan = plan;
    }

    /** Plans a seed's underground after its roads, towns and home (the planner runs once; queries are then read-only). */
    public static GeologyQuery plan(GeologyModel geology, GeoProtect protect) {
        return new GeologyQuery(geology, protect, UndergroundPlanner.build(geology, protect));
    }

    public RockType rockAt(double x, double y, double z) { return geology.rockAt(x, y, z); }

    /** The stratigraphic unit (index into {@link GeologyModel#UNIT_NAME}) at a point, -1 in the basement. */
    public int unitAt(double x, double y, double z) { return geology.unitAt(geology.column(x, z), y); }

    /** 0 open air above the ground, 1 near-surface solid, 2 shallow caves, 3 cave systems, 4 deep, 5 the kept floor. */
    public int layerAt(double x, double y, double z) { return geology.layer(geology.column(x, z), y); }

    /** The first rock under the loose ground. */
    public RockType bedrock(double x, double z) { return geology.bedrock(geology.column(x, z)); }

    /** The rock or soil type of the loose ground (E's soil mapping, 19.12). */
    public RockType soilType(double x, double z) { return geology.column(x, z).soilType; }

    public double soilDepth(double x, double z) { return geology.column(x, z).soil; }

    public double waterTable(double x, double z) { return geology.waterTable(geology.column(x, z)); }

    /** The karst share 0..1 of the rock at a point (limestone 1, dolomite 0.6, marl 0.2). */
    public double karstAt(double x, double y, double z) { return geology.karst(geology.column(x, z), y); }

    /** The distance to the nearest fault trace within maxD, or +inf. */
    public double faultDistance(double x, double z, double maxD) {
        double[] d = new double[1];
        return geology.nearestFault(x, z, maxD, d) == null ? Double.POSITIVE_INFINITY : d[0];
    }

    /** The planned cave at a point (cave id), or -1 in solid rock. */
    public int caveAt(double x, double y, double z) { return plan.caveAt(x, y, z); }

    public boolean airAt(double x, double y, double z) { return plan.airAt(x, y, z); }

    public boolean waterAt(double x, double y, double z) { return plan.waterAt(x, y, z); }

    /** Planned cave shapes whose bounds come within r of the column (indices into plan.shapes). */
    public List<Integer> openingsNear(double x, double z, double r) {
        List<Integer> out = new ArrayList<>();
        for (int i : plan.shapesNear(x, z)) {
            double[] b = plan.shapes.get(i).bounds();
            if (x >= b[0] - r && x <= b[3] + r && z >= b[2] - r && z <= b[5] + r) out.add(i);
        }
        return out;
    }

    public List<OreBody> oresNear(double x, double z, double r) {
        List<OreBody> out = new ArrayList<>();
        for (OreBody o : plan.ores) if (Math.hypot(o.x() - x, o.z() - z) <= r + Math.max(o.rx(), o.rz())) out.add(o);
        return out;
    }

    public List<Entrance> entrancesNear(double x, double z, double r) {
        List<Entrance> out = new ArrayList<>();
        for (Entrance e : plan.entrances) if (Math.hypot(e.x() - x, e.z() - z) <= r) out.add(e);
        return out;
    }

    public List<Succession> successionsNear(double x, double z, double r) {
        List<Succession> out = new ArrayList<>();
        for (Succession s : plan.successions) if (Math.hypot(s.x() - x, s.z() - z) <= r) out.add(s);
        return out;
    }

    /** What C work protects a column (road, bridge, tunnel, drive, town, start area, water, facility, none). */
    public GeoProtect.Zone zone(double x, double z) { return protect.zone(x, z); }

    /** The highest roof a cave of the layer may have under the column, -inf where the layer is forbidden. */
    public double roofLimit(int layer, double x, double z) { return protect.roofLimit(layer, x, z); }

    /** Whether E may dig or a later system may open the ground here without breaking a C work (engineering protection). */
    public boolean excavationAllowed(double x, double y, double z) {
        GeoProtect.Zone zn = protect.zone(x, z);
        if (zn == GeoProtect.Zone.NONE || zn == GeoProtect.Zone.WATER) return true;
        if (zn == GeoProtect.Zone.TOWN || zn == GeoProtect.Zone.START_AREA || zn == GeoProtect.Zone.FACILITY) return true;
        double ry = protect.roadY(x, z);
        return Double.isNaN(ry) || y < ry - GeoProtect.clearance(zn) || y > ry + 6;
    }

    /** The column's geology at one call (for tools and the offline check). */
    public Column column(double x, double z) { return geology.column(x, z); }
}
