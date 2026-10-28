package com.antaurora.apofirstlight.liquid;

/**
 * Water forced out of a hole by pressure (docs/gameplay/water_mesh_parcels_v1_design.md B3, M4): a burst pipe, a tank
 * shot through. Torricelli through a sharp-edged opening: the jet leaves at Cv sqrt(2 dp / rho) and the flow is
 * Cd A sqrt(2 dp / rho) (the jet narrows just past the hole: vena contracta). A 50 mm pipe at 3 bar: 24 m/s, 29 L/s.
 */
public final class Orifice {
    /** Velocity and discharge coefficients of a sharp-edged opening (textbook values). */
    public static final double CV = 0.98, CD = 0.62;
    /** Water, kg/m3. */
    public static final double RHO = 1000;
    /**
     * Air drag on a jet breaking up into drops, a = -K |v| v (1/m). Fitted to fire-stream reach: a smooth-bore stream
     * at about 3.5 bar (25 m/s) carries some 30 m at its best angle and rises some 20 m straight up; with K 0.02 a 24.5 m/s
     * jet goes 33 m at 30 degrees and 21 m up (34 m and 32 m in vacuum would be twice the real reach).
     */
    public static final double JET_DRAG = 0.02;

    private Orifice() {
    }

    /** How fast the jet leaves (m/s) at bar over the air outside. */
    public static double speed(double bar) {
        return CV * Math.sqrt(2 * Math.max(0, bar) * 1e5 / RHO);
    }

    /** The flow out of a round hole of mm across (L/s) at bar. */
    public static double litresPerSecond(double mm, double bar) {
        double r = mm / 2000;
        return CD * Math.PI * r * r * Math.sqrt(2 * Math.max(0, bar) * 1e5 / RHO) * 1000;
    }
}
