package com.antaurora.apofirstlight.vehicle;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * The T15 cab's key and the places it can hide (docs/vehicles/t15_hotwire_v1_design.md section 9; 2026-10-21): one key per
 * truck, either somewhere else (with a player, lost, never there) or in the truck: in the ignition, on the seat, in the
 * glovebox, in the ashtray drawer, tucked behind a sun visor. The compartments open and shut ({@link Compartment}); a cab
 * rider looks at one to open it and at the key to take it. Frames: the truck's {forward, right, up} for picking
 * (PickupTruckLayout), the model frame (x right, y up, z back) for drawing; both from tools/pickup-t15 (common.mjs KEY_SPOTS
 * and PIVOTS, cab.mjs).
 */
public final class CabStowage {
    /** A compartment that opens (bits of the truck's synced stowage byte). */
    public enum Compartment {
        VISOR_L, VISOR_R, GLOVEBOX, ASHTRAY;

        public int bit() { return 1 << ordinal(); }
    }

    /**
     * Where the truck's key is. weight: how often a truck is found with it there (in 100). The model-frame place of the
     * key's origin (its cylinder centre, tools/pickup-t15 KEY_CYL) lying flat there, its turn about up (deg), the
     * compartment it rides in.
     */
    public enum KeySpot {
        ELSEWHERE(70, null, null, 0, null),
        IGNITION(3, null, null, 0, null),
        SEAT(4, null, new double[]{0.18, 0.838, -0.05}, 25, new PickupTruckLayout.Box(-0.02, 0.10, 0.13, 0.25, 0.80, 0.88)),
        GLOVEBOX(8, Compartment.GLOVEBOX, new double[]{0.45, 0.848, -0.70}, -20, null),
        ASHTRAY(4, Compartment.ASHTRAY, new double[]{0.0, 0.808, -0.62}, 35, null),
        VISOR_L(9, Compartment.VISOR_L, new double[]{-0.40, 1.784, -0.29}, 0, null),
        VISOR_R(2, Compartment.VISOR_R, new double[]{0.40, 1.784, -0.29}, 180, null);

        public final int weight;
        @Nullable public final Compartment in;
        /** Model frame (blocks); null: not drawn there (ELSEWHERE) or in the cylinder (IGNITION). */
        @Nullable public final double[] at;
        public final float yaw;
        /** Its own click box (a key lying in the open; a compartment's key is taken through the compartment). */
        @Nullable final PickupTruckLayout.Box box;

        KeySpot(int weight, @Nullable Compartment in, @Nullable double[] at, float yaw, @Nullable PickupTruckLayout.Box box) {
            this.weight = weight;
            this.in = in;
            this.at = at;
            this.yaw = yaw;
            this.box = box;
        }

        public static KeySpot of(int ordinal) {
            KeySpot[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ELSEWHERE;
        }

        public static KeySpot byName(String name) {
            for (KeySpot s : values()) if (s.name().equalsIgnoreCase(name)) return s;
            return null;
        }

        /** A found truck's key: mostly gone, sometimes left in it (the weights). */
        public static KeySpot random(RandomSource random) {
            int total = 0;
            for (KeySpot s : values()) total += s.weight;
            int n = random.nextInt(total);
            for (KeySpot s : values()) if ((n -= s.weight) < 0) return s;
            return ELSEWHERE;
        }
    }

    /**
     * The key's pivot (model frame): the cylinder centre in the panel face right of the column, its axis toward the driver
     * (tools/pickup-t15 KEY_CYL / PIVOTS.key; 2026-10-21: on the shroud's side the horn pad hid it from the driver).
     */
    public static final float[] KEY_PIVOT = {-0.17F, 0.975F, -0.5725F};
    /** The key's turns in the cylinder (deg about model +Z, toward the driver; clockwise as the driver sees it): ON and START. */
    public static final float KEY_ON = -60.0F, KEY_START = -90.0F;
    /**
     * Lays the key flat (deg): about model Z, then Y. The key stands in the cylinder at OFF along +Z, its broad sides
     * across X; laid down it runs along +X, the cuts toward -Z, the broad sides up (each spot's yaw then turns it).
     */
    public static final float KEY_FLAT_ROLL = -90.0F, KEY_FLAT_YAW = 90.0F;
    /** Compartment pivots (model frame) and how far each opens (deg about model +X; the ashtray: blocks toward the cab). */
    public static final float[] VISOR_L_PIVOT = {-0.72F, 1.772F, -0.36F}, VISOR_R_PIVOT = {0.72F, 1.772F, -0.36F},
            GLOVEBOX_PIVOT = {0.41F, 0.835F, -0.577F}, ASHTRAY_PIVOT = {0.0F, 0.81F, -0.5775F};
    public static final float VISOR_OPEN = 80.0F, GLOVEBOX_OPEN = 75.0F, ASHTRAY_OUT = 0.07F;
    /** The turn signal stalk (left of the column; turns like the wiper stalk, + up) and the light knob on its end. */
    public static final float STALK_TURN = 12.0F, KNOB_ON = 80.0F;
    public static final float[] STALK_PIVOT = {-0.41F, 1.079601F, -0.489101F}, KNOB_PIVOT = {-0.54F, 1.055601F, -0.439101F},
            KNOB_AXIS = {-0.96F, -0.144F, 0.24F};

    /** A compartment's click box (truck frame: forward, right, up), shut or open. */
    static PickupTruckLayout.Box box(Compartment c) {
        return switch (c) {
            case VISOR_L -> new PickupTruckLayout.Box(0.20, 0.40, -0.71, -0.11, 1.60, 1.80);
            case VISOR_R -> new PickupTruckLayout.Box(0.20, 0.40, 0.11, 0.71, 1.60, 1.80);
            case GLOVEBOX -> new PickupTruckLayout.Box(0.44, 0.62, 0.20, 0.62, 0.80, 0.96);
            case ASHTRAY -> new PickupTruckLayout.Box(0.49, 0.60, -0.09, 0.09, 0.79, 0.83);
        };
    }

    /** The key cylinder and a key in it (truck frame): the keyhole. */
    static final PickupTruckLayout.Box IGNITION_BOX = new PickupTruckLayout.Box(0.49, 0.585, -0.21, -0.13, 0.935, 1.015);

    /** The horn pad's extent (m, across and up from the hub in the wheel's plane) and the spokes' half thickness. */
    private static final double PAD_SIDE = 0.105, PAD_DOWN = -0.085, PAD_UP = 0.05, SPOKE = 0.026, SPOKE_UP = -0.033;

    /**
     * The steering wheel under a look (truck frame, unit direction), at rest: the rim, the horn pad and the two spokes, not
     * the gaps between them (the keyhole and the gauges show through those). Blocks along the look, or -1.
     */
    static double wheelHit(double[] o, double[] d, double max) {
        // the wheel's plane through the hub, square to the column (truck frame: forward, right, up)
        double[] hub = {-PickupTruckLayout.WHEEL_Z, PickupTruckLayout.WHEEL_X, PickupTruckLayout.WHEEL_Y};
        double[] axis = {-PickupTruckLayout.COLUMN_Z, 0, PickupTruckLayout.COLUMN_Y}, up = {-PickupTruckLayout.COLUMN_Y, 0, -PickupTruckLayout.COLUMN_Z};
        double dn = d[0] * axis[0] + d[2] * axis[2];
        if (Math.abs(dn) < 1e-6) return -1;
        double t = ((hub[0] - o[0]) * axis[0] + (hub[2] - o[2]) * axis[2]) / dn;
        if (t < 0 || t > max) return -1;
        double[] p = {o[0] + d[0] * t - hub[0], o[1] + d[1] * t - hub[1], o[2] + d[2] * t - hub[2]};
        double across = p[1], high = p[0] * up[0] + p[2] * up[2], r = Math.hypot(across, high);
        boolean rim = Math.abs(r - PickupTruckLayout.RIM) <= 0.025;
        boolean pad = Math.abs(across) <= PAD_SIDE && high >= PAD_DOWN && high <= PAD_UP;
        boolean spoke = Math.abs(across) <= PickupTruckLayout.RIM && Math.abs(high - SPOKE_UP) <= SPOKE;
        return rim || pad || spoke ? t : -1;
    }

    private CabStowage() {
    }
}
