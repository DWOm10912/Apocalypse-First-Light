package com.antaurora.apofirstlight.worldgen.home;

import com.antaurora.apofirstlight.worldgen.network.contract.RoadNode;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.contract.SpatialClaim;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;

import java.util.List;

/**
 * The forest start home's template frame (M4, docs/worldgen/forest_home_b_m4_placement.md): pure Java, shared by the
 * planner, the chunk pass, the tree exclusion and the dev command.
 * <p>
 * The template {@code apocalypse_firstlight:forest_home_b} is the M1 plot (tools/afl_minecraft_mcp/forest_home_b_m1.mjs):
 * 37 x 13 x 26 (x, y, z), front south. Its frame against the M0 / M1 paper frame (X east, Z south, k = layer above grade):
 * x = X + 10, y = k + 3, z = Z + 10. So template y 3 is the first air layer over the grade (k 0), y 2 the grass, y 0..1
 * dirt, and the root cellar reaches down to y 0.
 * <p>
 * Rotation r (0..3) turns clockwise about the template's origin corner, as vanilla places templates: cell (x, z) goes to
 * (x, z), (-z, x), (-x, -z), (z, -x); a continuous point (px, pz) of the frame goes to (px, pz), (1 - pz, px),
 * (1 - px, 1 - pz), (pz, 1 - px). Headings follow contract section 3 (0 = east, 90 = south): the front faces 90 + 90 r.
 */
public final class ForestHomeLayout {
    private ForestHomeLayout() {}

    public static final String ID = "forest_home_b", TEMPLATE = "apocalypse_firstlight:forest_home_b", OWNER = "main";
    /**
     * The start_area rules (world generation version lock, WorldgenRules): bump on any change to the template, this
     * frame, the earthwork or the driveway fixtures, since a world places its house chunk by chunk with the rules it was
     * made with. 2 (2026-10-19): the site from the county road G3, the farm gate and the mailbox.
     */
    public static final int RULES = 2;
    public static final int SIZE_X = 37, SIZE_Y = 13, SIZE_Z = 26;
    /** Template y of the first air layer over the grade (paper k 0). */
    public static final int GROUND = 3;
    /** Paper frame -> template frame. */
    public static final int PX = 10, PZ = 10;
    /** The M0 site research's pivot (paper (12, 4.5), the built footprint's centre), in the template frame. */
    public static final double PIVOT_X = 12 + PX, PIVOT_Z = 4.5 + PZ;
    /** The yard's exit to the drive (paper X 22 on the plot's south edge, the carport's line). */
    public static final double EXIT_X = 22.5 + PX, EXIT_Z = SIZE_Z;
    /** Graded ring round the plot (m): the ground blends from the yard's grade to the natural ground across it. */
    public static final int GRADE_MARGIN = 8;
    /** No natural mesh tree stands with its trunk closer than this to the plot (m): the graded ring plus crown room. */
    public static final double TREE_CLEARANCE = GRADE_MARGIN + 6;

    /** World point of a template-frame point (continuous) for origin (ox, oz) and rotation r. */
    public static double[] world(int ox, int oz, int r, double px, double pz) {
        return switch (r & 3) {
            case 0 -> new double[]{ox + px, oz + pz};
            case 1 -> new double[]{ox + 1 - pz, oz + px};
            case 2 -> new double[]{ox + 1 - px, oz + 1 - pz};
            default -> new double[]{ox + pz, oz + 1 - px};
        };
    }

    /** Template-frame point (continuous) of a world point: the inverse of {@link #world}. */
    public static double[] frame(int ox, int oz, int r, double wx, double wz) {
        double dx = wx - ox, dz = wz - oz;
        return switch (r & 3) {
            case 0 -> new double[]{dx, dz};
            case 1 -> new double[]{dz, 1 - dx};
            case 2 -> new double[]{1 - dx, 1 - dz};
            default -> new double[]{1 - dz, dx};
        };
    }

    /** The origin corner that puts the pivot at world (cx, cz) under rotation r. */
    public static int[] origin(double cx, double cz, int r) {
        double[] p = world(0, 0, r, PIVOT_X, PIVOT_Z);
        return new int[]{(int) Math.floor(cx - p[0] + 0.5), (int) Math.floor(cz - p[1] + 0.5)};
    }

    /** The front's heading (contract degrees). */
    public static double heading(int r) { return (90 + 90 * (r & 3)) % 360; }

    /** The plot as a rotated rectangle (world). */
    public static RotatedRect plot(int ox, int oz, int r) {
        double[] c = world(ox, oz, r, SIZE_X / 2.0, SIZE_Z / 2.0);
        return new RotatedRect(c[0], c[1], SIZE_X / 2.0, SIZE_Z / 2.0, 90 * (r & 3));
    }

    /** The reservation for a site: origin (ox, groundY - GROUND, oz), the plot claim, the kept ground and the node H. */
    public static StructureReservation reservation(int ox, int groundY, int oz, int r) {
        RotatedRect plot = plot(ox, oz, r);
        int y0 = groundY - GROUND;
        List<SpatialClaim> claims = List.of(
                new SpatialClaim(ID + "/house", OWNER, SpatialClaim.Kind.BUILDING, plot, y0, y0 + SIZE_Y - 1, 0, SpatialClaim.START_HOME, true),
                new SpatialClaim(ID + "/keep", OWNER, SpatialClaim.Kind.TERRAIN_KEEP, plot.grow(GRADE_MARGIN), Integer.MIN_VALUE, Integer.MAX_VALUE,
                        0, SpatialClaim.START_HOME, true));
        double[] h = world(ox, oz, r, EXIT_X, EXIT_Z);
        RoadNode node = new RoadNode(ID + "/driveway", h[0], groundY, h[1], heading(r), RoadNode.PrivateSpec.PRIVATE_GRAVEL,
                RoadNode.PublicClass.COUNTY, 3, 0, OWNER, RoadNode.State.DEAD_END, RoadNode.VERSION);
        return new StructureReservation(ID, TEMPLATE, ox, y0, oz, r & 3, 0, claims, List.of(node), List.of(),
                StructureReservation.State.PLANNED, StructureReservation.VERSION);
    }

    /** The plot claim of a reservation. */
    public static RotatedRect plot(StructureReservation r) { return plot(r.x(), r.z(), r.rotation()); }

    /** The first air layer over the grade (world Y). */
    public static int groundY(StructureReservation r) { return r.y() + GROUND; }

    /** World block of the front porch's step, where a player stands to go in (paper X 8, k 0, Z 14: before the porch). */
    public static double[] porch(StructureReservation r) { return world(r.x(), r.z(), r.rotation(), 8.5 + PX, 14.5 + PZ); }

    /**
     * The forest-home start's spots on the grass at the foot of the porch steps (paper k 0, Z 15; the three steps are
     * X 7..9 at Z 14, the railing's gap X 8 at Z 13), at most START_SPOTS: before the middle step first, then one block to
     * each side. World point {x, z} of the spot's block centre; the feet stand on the template's grade (groundY).
     */
    public static double[] startSpot(StructureReservation r, int index) {
        double dx = switch (Math.floorMod(index, START_SPOTS)) { case 1 -> -1; case 2 -> 1; default -> 0; };
        return world(r.x(), r.z(), r.rotation(), 8.5 + dx + PX, 15.5 + PZ);
    }

    /** How many survivors may start before the porch together (a family's house: SurvivorScenes' forest home, 3). */
    public static final int START_SPOTS = 3;

    /** Minecraft yaw (0 = south, 90 = west) of a player before the porch looking at the house (the front's reverse). */
    public static float facingHouse(int r) {
        double h = Math.toRadians(heading(r) + 180), dx = Math.cos(h), dz = Math.sin(h);
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
