package com.antaurora.apofirstlight.client.rain;

/**
 * The T15's glass as the water on it sees it (docs/vehicles/t15_windshield_rain_design.md): the windshield (with the
 * wipers, {@link WindshieldGlass}), the rear window and the two door windows. Each pane has its own plane coordinates (u
 * across, s up the glass, metres) and an outline: a convex quad (left / right edges u = a + b s, bottom s0, top s1) with
 * rounded corners, the same test in the film shader (afl_windshield_film, uniforms Outline / Span). Where it lies: the
 * model frame (metres; (right, up, back) = concept (z, y, -x)), axes U x S = -N (N out of the cab), as the windshield's.
 * Shapes from tools/pickup-t15/exterior.mjs (cabShell(), doors()) by hand: keep in step. The door windows turn with their
 * doors (the renderer's door_l / door_r hinges).
 */
final class TruckGlass {
    enum Hinge {NONE, DOOR_L, DOOR_R}

    /** The door hinges (model frame; tools/pickup-t15/common.mjs PIVOTS door_l / door_r). */
    static final float HINGE_X = 0.825F, HINGE_Y = 0.80F, HINGE_Z = -0.916F;

    static final Pane WINDSHIELD, REAR, LEFT, RIGHT;
    static final Pane[] PANES;

    static {
        double uy = WindshieldGlass.UP_Y, ux = WindshieldGlass.UP_X, oy = WindshieldGlass.OUT_Y, ox = WindshieldGlass.OUT_X;
        // the windshield: the cab side leans in toward the roof (its half width 0.773 at the belt, 0.055 less 0.735 m up)
        double lean = 0.055 * uy / 0.735;
        WINDSHIELD = new Pane("windshield", Hinge.NONE, -0.773, lean, 0.773, -lean, WindshieldGlass.S0, WindshieldGlass.S1, WindshieldGlass.CORNER,
                new double[]{0, WindshieldGlass.BELT, -WindshieldGlass.WS0}, new double[]{1, 0, 0}, new double[]{0, uy, -ux}, new double[]{0, oy, -ox},
                WindshieldGlass.WIPERS, 0, 1, 0.35 / 0.75, 1, true);
        // the rear window: upright in the back panel (x = CB + 0.004), 0.585 half wide at its foot, 0.56 at its head; u
        // toward the truck's left (seen from the bed: to the right)
        double rb = 0.025 / 0.55;
        REAR = new Pane("rear", Hinge.NONE, -(0.585 + 0.075 * rb), rb, 0.585 + 0.075 * rb, -rb, 0.075, 0.625, 0.07,
                new double[]{0, WindshieldGlass.BELT, 0.296}, new double[]{-1, 0, 0}, new double[]{0, 1, 0}, new double[]{0, 0, 1},
                new WindshieldGlass.Wiper[0], 0, 0, 0, 0, false);
        // the door windows: on the cab side leaning in 0.055 over 0.735 m (z = sideZ(y) - 0.026), rear edge at x = -0.15,
        // front edge along the A pillar (x = 0.885 - 0.7308 s); u backward on the right, forward on the left
        double k = Math.hypot(1, 0.055 / 0.735), sy = 1 / k, sz = 0.055 / 0.735 / k, front = 0.732877 / k;
        RIGHT = new Pane("right", Hinge.DOOR_R, -0.885, front, 0.15, 0, 0.01 * k, 0.683 * k, 0.03,
                new double[]{0.819, WindshieldGlass.BELT, 0}, new double[]{0, 0, 1}, new double[]{-sz, sy, 0}, new double[]{sy, sz, 0},
                new WindshieldGlass.Wiper[0], 1, 0.12, 0, 0.8, false);
        LEFT = new Pane("left", Hinge.DOOR_L, -0.15, 0, 0.885, -front, 0.01 * k, 0.683 * k, 0.03,
                new double[]{-0.819, WindshieldGlass.BELT, 0}, new double[]{0, 0, -1}, new double[]{sz, sy, 0}, new double[]{-sy, sz, 0},
                new WindshieldGlass.Wiper[0], -1, 0.12, 0, 0.8, false);
        PANES = new Pane[]{WINDSHIELD, REAR, LEFT, RIGHT};
    }

    private TruckGlass() {
    }

    /** One pane: outline, place, the wipers on it, how the wind moves its drops. */
    static final class Pane {
        final String name;
        final Hinge hinge;
        final double aL, bL, aR, bR, s0, s1, corner;
        /** Its origin (u = s = 0) and axes in the model frame. */
        final double[] o, axisU, axisS, axisN;
        final WindshieldGlass.Wiper[] wipers;
        /**
         * Driving: the way the wind pushes drops (glass coordinates, per unit push), how much of the windshield's push it
         * gets, and the windshield's spread out toward the pillars (u per m of u).
         */
        final double windU, windS, spread, windK;
        /** The windshield bends back toward the A pillars (WindshieldGlass.bend, along model +z). */
        private final boolean bends;
        /** Its outline's extent and area (m, m^2), the share of gravity down it. */
        final double uMin, uMax, area, gravity;
        /** The outline shrunk by the corner radius (inside): its corners, edges and their 1 / length, 1 / length^2. */
        private final double[] qu = new double[4], qs = new double[4], eu = new double[4], es = new double[4],
                inv = new double[4], inv2 = new double[4];

        Pane(String name, Hinge hinge, double aL, double bL, double aR, double bR, double s0, double s1, double corner,
             double[] o, double[] axisU, double[] axisS, double[] axisN, WindshieldGlass.Wiper[] wipers,
             double windU, double windS, double spread, double windK, boolean bends) {
            this.name = name;
            this.hinge = hinge;
            this.aL = aL;
            this.bL = bL;
            this.aR = aR;
            this.bR = bR;
            this.s0 = s0;
            this.s1 = s1;
            this.corner = corner;
            this.o = o;
            this.axisU = axisU;
            this.axisS = axisS;
            this.axisN = axisN;
            this.wipers = wipers;
            this.windU = windU;
            this.windS = windS;
            this.spread = spread;
            this.windK = windK;
            this.bends = bends;
            uMin = Math.min(aL + bL * s0, aL + bL * s1);
            uMax = Math.max(aR + bR * s0, aR + bR * s1);
            gravity = axisS[1];
            double r = corner, lo = s0 + r, hi = s1 - r;
            double la = aL + r * Math.sqrt(1 + bL * bL), ra = aR - r * Math.sqrt(1 + bR * bR);
            double[] cu = {la + bL * lo, ra + bR * lo, ra + bR * hi, la + bL * hi}, cs = {lo, lo, hi, hi};
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                qu[i] = cu[i];
                qs[i] = cs[i];
                eu[i] = cu[j] - cu[i];
                es[i] = cs[j] - cs[i];
                double len = Math.hypot(eu[i], es[i]);
                inv[i] = 1 / len;
                inv2[i] = 1 / (len * len);
            }
            // the area by its outline (2 mm cells)
            int n = 0;
            for (double s = s0; s < s1; s += 0.002)
                for (double u = uMin; u < uMax; u += 0.002) if (inside(u + 0.001, s + 0.001) > 0) n++;
            area = n * 4e-6;
        }

        /** The middle of its outline (glass coordinates). */
        double uMid() {
            return 0.5 * (uMin + uMax);
        }

        double sMid() {
            return 0.5 * (s0 + s1);
        }

        /** The pane's outward normal against the truck's forward (model -z) and up. */
        double facingForward() {
            return -axisN[2];
        }

        double facingUp() {
            return axisN[1];
        }

        /**
         * How far inside the outline (m; negative outside): the outline shrunk by the corner radius, the distance from
         * that quad taken off the radius (so the corners round, whatever their angle).
         */
        double inside(double u, double s) {
            // the signed distance (positive outside) from the convex quad (counter-clockwise), its corners worked out once
            // (2026-10-18: it ran for every moving drop every frame; docs/rendering/rain_v1.md section 3.5)
            double out = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) out = Math.max(out, ((u - qu[i]) * es[i] - (s - qs[i]) * eu[i]) * inv[i]);
            if (out <= 0) return corner - out;
            double near = Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                double wu = u - qu[i], ws = s - qs[i], t = Math.max(0, Math.min(1, (wu * eu[i] + ws * es[i]) * inv2[i]));
                double nu = wu - eu[i] * t, ns = ws - es[i] * t;
                near = Math.min(near, nu * nu + ns * ns);
            }
            return corner - Math.sqrt(near);
        }

        /** How far the glass bends back at u (model +z; 0 but on the windshield). */
        double bendAt(double u) {
            return bends ? WindshieldGlass.bend(u) : 0;
        }

        /** A point of the water layer (model frame, off m out of the glass) into out[at..at + 2]. */
        void model(double u, double s, double off, float[] out, int at) {
            double b = bends ? WindshieldGlass.bend(u) : 0;
            for (int i = 0; i < 3; i++) out[at + i] = (float) (o[i] + u * axisU[i] + s * axisS[i] + off * axisN[i] + (i == 2 ? b : 0));
        }
    }
}
