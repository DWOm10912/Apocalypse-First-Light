package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.vehicle.WiperSystem;

/**
 * The T15 windshield's shape and its wipers as the water on it sees them (docs/vehicles/t15_windshield_rain_design.md; the
 * shape from tools/pickup-t15/exterior.mjs cabShell() and common.mjs, by hand: keep in step); its pane is
 * {@link TruckGlass#WINDSHIELD}. Glass coordinates: u across (m, the truck's right +, the driver at -u), s up the glass (m,
 * from the windshield base line at the belt, along the glass).
 */
final class WindshieldGlass {
    /** Up the glass and its outward normal (concept x forward, y up). */
    static final double UP_X = -0.591125, UP_Y = 0.806580, OUT_X = 0.806580, OUT_Y = 0.591125;
    /** The base line (concept x at the belt) and the belt height. */
    static final double WS0 = 0.955, BELT = 1.07;
    /** The glass's lower and upper edges (s) and its corner rounding (m). */
    static final double S0 = 0.025 / UP_Y, S1 = 0.695 / UP_Y, CORNER = 0.07;
    /** The water sits this far out of the glass (m): in front of it from outside, within the depth tolerance from inside. */
    static final double OFF = 0.0015;

    /** The wipers in glass coordinates: pivots (u, s; the cowl pivots projected onto the glass), the parked blade. */
    static final Wiper[] WIPERS = {
            new Wiper(-0.47, -0.02134, 0.06473, 0.49, 0.49, WiperSystem.SWEEP_L),
            new Wiper(0.03, -0.02134, 0.06473, 0.465, 0.45, WiperSystem.SWEEP_R)};

    private WindshieldGlass() {
    }

    /** The glass's sides bend back toward the A pillars (m, concept -x). */
    static double bend(double u) {
        double t = Math.max(0, Math.min(1, (Math.abs(u) - 0.42) / (0.74 - 0.42)));
        if (t == 0) return 0;   // the flat middle (most drops): no pow
        return 0.022 * Math.pow(t * t * (3 - 2 * t), 1.6);
    }

    /**
     * A wiper in glass coordinates: pivot (pu, ps); the parked blade's middle lies (rs up, arm across) from it and the blade
     * runs across (u), len long; the stroke turns it sweep degrees up the glass about the pivot.
     */
    record Wiper(double pu, double ps, double rs, double arm, double len, double sweep) {
        /** The parked blade's angle (rad, from +u toward +s) at distance rho from the pivot; NaN off the blade. */
        double restAngle(double rho, double margin) {
            double r2 = rho * rho - rs * rs;
            if (r2 <= 0) return Double.NaN;
            double along = Math.sqrt(r2) - arm;
            if (along < -len / 2 - margin || along > len / 2 + margin) return Double.NaN;
            return Math.atan2(rs, Math.sqrt(r2));
        }

        /** A point along the blade (t -0.5 .. 0.5) at turn a (rad) into out (u, s). */
        void blade(double t, double a, double[] out) {
            double z = arm + t * len, c = Math.cos(a), sn = Math.sin(a);
            out[0] = pu - rs * sn + z * c;
            out[1] = ps + rs * c + z * sn;
        }
    }
}
