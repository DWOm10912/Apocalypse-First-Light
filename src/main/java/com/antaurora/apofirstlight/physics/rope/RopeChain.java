package com.antaurora.apofirstlight.physics.rope;

import org.joml.Matrix4dc;

/**
 * A rope, strap or cord as a chain of nodes, stepped by position-based dynamics (docs/native_guns/attachments/
 * sling_physics_v1.md 4): Verlet integration with gravity and damping, inextensible segments, a soft bend limit, a flat
 * strap's stiffness across its width, swept collision of nodes and segment midpoints with the colliders, no
 * self-penetration, friction on contact. Fixed substeps of
 * {@link #STEP}; both end nodes are pinned and follow their targets across the substeps of a frame. Plain maths with no
 * shared state: a chain belongs to one thread.
 */
public final class RopeChain {
    public static final double STEP = 1 / 240.0;
    public static final int MAX_STEPS = 24;
    /** An end that moves further than this in one frame (a teleport, a respawn) needs the chain placed again. */
    public static final double JUMP = 1.0;

    public final int n;
    public final double radius;
    private final double[] x, p, rest, invMass, contact;
    private final boolean[] touching;
    private final double[] head = new double[3], tail = new double[3], headFrom = new double[3], tailFrom = new double[3];
    private final double[] scratch = new double[3], start = new double[3], normal = new double[3];
    /** The width direction at each node from the last {@link #frames} (the in-plane stiffness bends against it). */
    private final double[] lateral;
    private double[] headMount, tailMount;
    private boolean lateralKnown;
    private final double[] u, rhs, diag, off, lambda;
    private double clock;
    private boolean placed, broken;

    /** Gravity (m/s^2, simulation axes): world down unless the chain is settled in a model's own frame. */
    public double gravityX = 0, gravityY = -9.81, gravityZ = 0;
    /**
     * Velocity kept per substep (0.986: about a quarter of it left after a third of a second, the drag and inner friction
     * of a heavy webbing strap, not a light cord) and the fastest a node may move relative to the holder (m/s).
     */
    public double damping = 0.986, maxSpeed = 2.5, friction = 0.6;
    /** Nodes i and i + 2 stay at least this share of their rest span apart (about 74 degrees of bend at 0.8). */
    public double bendMin = 0.8, bendStiffness = 0.5;
    /**
     * A flat strap hardly bends across its width: the turn within its own plane per node stays under this share of a
     * segment (0.1, about 6 degrees). 0 turns it off (a round rope or cord).
     */
    public double lateralMax = 0.1;
    public int iterations = 4;

    /**
     * rest[i] is the length of the segment from node i to node i + 1; mass[i] each node's mass (relative, the end nodes are
     * pinned whatever they say).
     */
    public RopeChain(double[] rest, double[] mass, double radius) {
        this.n = rest.length + 1;
        this.radius = radius;
        this.rest = rest.clone();
        x = new double[n * 3]; p = new double[n * 3]; contact = new double[n * 3];
        touching = new boolean[n];
        u = new double[(n - 1) * 3]; rhs = new double[n - 1]; diag = new double[n - 1]; off = new double[n - 1]; lambda = new double[n - 1];
        invMass = new double[n];
        lateral = new double[n * 3];
        for (int i = 1; i < n - 1; i++) invMass[i] = 1 / mass[i];
    }

    public boolean placed() { return placed && !broken; }
    public double[] positions() { return x; }
    public boolean touching(int i) { return touching[i]; }
    public double[] contacts() { return contact; }
    public double rest(int i) { return rest[i]; }

    public double length() {
        double s = 0;
        for (double r : rest) s += r;
        return s;
    }

    /**
     * Lays the chain along a guide polyline (points flattened x y z), stretched or squeezed to the guide's length, at rest.
     * Its ends become the guide's ends.
     */
    public void place(double[] guide) {
        int g = guide.length / 3;
        double[] at = new double[g];
        for (int i = 1; i < g; i++) at[i] = at[i - 1] + dist(guide, (i - 1) * 3, guide, i * 3);
        double total = length(), s = 0;
        int seg = 0;
        for (int i = 0; i < n; i++) {
            double want = at[g - 1] * (i == n - 1 ? 1 : s / total);
            while (seg < g - 2 && at[seg + 1] < want) seg++;
            double span = at[seg + 1] - at[seg], f = span > 1e-12 ? (want - at[seg]) / span : 0;
            for (int a = 0; a < 3; a++) x[i * 3 + a] = guide[seg * 3 + a] + (guide[seg * 3 + 3 + a] - guide[seg * 3 + a]) * f;
            if (i < n - 1) s += rest[i];
        }
        System.arraycopy(x, 0, p, 0, x.length);
        System.arraycopy(x, 0, head, 0, 3);
        System.arraycopy(x, (n - 1) * 3, tail, 0, 3);
        java.util.Arrays.fill(touching, false);
        lateralKnown = false;
        clock = 0;
        placed = true;
        broken = false;
    }

    /** This frame's targets for the two pinned ends. Returns false when the chain must be placed again (a jump). */
    public boolean ends(double hx, double hy, double hz, double tx, double ty, double tz) {
        if (!placed) return false;
        head[0] = hx; head[1] = hy; head[2] = hz;
        tail[0] = tx; tail[1] = ty; tail[2] = tz;
        if (!(dist(head, 0, x, 0) <= JUMP && dist(tail, 0, x, (n - 1) * 3) <= JUMP)) broken = true;
        return !broken;
    }

    /**
     * Carries the free nodes along a share of a rigid motion (sim to sim) the rope's holder made this frame: 0 leaves the
     * rope to its inertia in simulation space, 1 moves it rigidly with the holder.
     */
    public void carry(Matrix4dc motion, double share) {
        if (share <= 0) return;
        for (double[] a : new double[][] {x, p})
            for (int i = 0; i < n; i++) {
                int o = i * 3;
                double px = a[o], py = a[o + 1], pz = a[o + 2];
                double mx = motion.m00() * px + motion.m10() * py + motion.m20() * pz + motion.m30();
                double my = motion.m01() * px + motion.m11() * py + motion.m21() * pz + motion.m31();
                double mz = motion.m02() * px + motion.m12() * py + motion.m22() * pz + motion.m32();
                a[o] = px + (mx - px) * share; a[o + 1] = py + (my - py) * share; a[o + 2] = pz + (mz - pz) * share;
            }
    }

    /** Advances the chain by dt seconds of real time (capped at 0.1 s; at most {@link #MAX_STEPS} substeps). */
    public void step(double dt, RopeCollider collider) {
        if (!placed || broken) return;
        clock += Math.min(Math.max(dt, 0), 0.1);
        int steps = (int) (clock / STEP);
        if (steps >= MAX_STEPS) { steps = MAX_STEPS; clock = 0; } else clock -= steps * STEP;
        System.arraycopy(x, 0, headFrom, 0, 3);
        System.arraycopy(x, (n - 1) * 3, tailFrom, 0, 3);
        for (int s = 1; s <= steps; s++) {
            double a = (double) s / steps;
            pin(0, headFrom, head, a);
            pin(n - 1, tailFrom, tail, a);
            collider.blend(a);
            substep(collider);
        }
        pin(0, head, head, 1);
        pin(n - 1, tail, tail, 1);
        collider.blend(1);
    }

    /**
     * Runs seconds of simulated time with the ends where they are (a presettle, a static rest shape). The width frames
     * are refreshed as it goes when the mounts are known (a {@link #frames} call after {@link #place}).
     */
    public void settle(double seconds, RopeCollider collider) {
        if (!placed) return;
        // twice: the colliders stand still, from substep to substep as well
        collider.blend(1);
        collider.blend(1);
        int steps = (int) Math.ceil(seconds / STEP);
        double[] out = new double[n * 3];
        for (int s = 0; s < steps; s++) {
            if (headMount != null && s % 24 == 0) frames(headMount, tailMount, out);
            substep(collider);
        }
        for (int i = 0; i < x.length; i++) p[i] = x[i];
    }

    private void pin(int i, double[] from, double[] to, double a) {
        for (int k = 0; k < 3; k++) { x[i * 3 + k] = from[k] + (to[k] - from[k]) * a; p[i * 3 + k] = x[i * 3 + k]; }
    }

    private void substep(RopeCollider collider) {
        double h2 = STEP * STEP, cap = maxSpeed * STEP;
        for (int i = 1; i < n - 1; i++) {
            int o = i * 3;
            double vx = (x[o] - p[o]) * damping, vy = (x[o + 1] - p[o + 1]) * damping, vz = (x[o + 2] - p[o + 2]) * damping;
            double v = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (v > cap) { vx *= cap / v; vy *= cap / v; vz *= cap / v; }
            p[o] = x[o]; p[o + 1] = x[o + 1]; p[o + 2] = x[o + 2];
            x[o] += vx; x[o + 1] += vy; x[o + 2] += vz;
            x[o] += gravityX * h2; x[o + 1] += gravityY * h2; x[o + 2] += gravityZ * h2;
            touching[i] = false;
        }
        for (int it = 0; it < iterations; it++) {
            boolean down = (it & 1) == 0;
            for (int j = 0; j < n - 2; j++) bend(down ? j : n - 3 - j);
            if (lateralKnown && lateralMax > 0) for (int j = 2; j < n - 2; j++) lateral(down ? j : n - 1 - j);
            tether();
            project();
            collide(collider);
        }
        self();
        frict();
        project();
        // the projection can pull a node back into a part: the last word is the colliders'
        collide(collider);
        // overstretched, or not a number (a NaN fails every test and would never break it): placed again
        for (int i = 0; i < n - 1; i++) if (!(dist(x, i * 3, x, i * 3 + 3) <= rest[i] * 3)) broken = true;
    }

    /**
     * Every segment to its rest length at once: the segment constraints of a chain couple only neighbours, so one Newton
     * step of the projection is a tridiagonal solve (Goldenthal et al. 2007, fast projection). Two steps leave well under
     * a millimetre of error where Gauss-Seidel over thirty segments would leave centimetres.
     */
    private void project() {
        int m = n - 1;
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < m; i++) {
                int a = i * 3;
                double dx = x[a + 3] - x[a], dy = x[a + 4] - x[a + 1], dz = x[a + 5] - x[a + 2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-12) { dx = 0; dy = -1; dz = 0; d = 1e-12; }
                u[a] = dx / (d < 1e-12 ? 1 : d); u[a + 1] = dy / (d < 1e-12 ? 1 : d); u[a + 2] = dz / (d < 1e-12 ? 1 : d);
                rhs[i] = rest[i] - d;
                diag[i] = invMass[i] + invMass[i + 1];
                if (i > 0) off[i] = -invMass[i] * (u[a - 3] * u[a] + u[a - 2] * u[a + 1] + u[a - 1] * u[a + 2]);
            }
            // Thomas: off[i] couples i - 1 and i
            for (int i = 1; i < m; i++) {
                if (diag[i - 1] < 1e-12) continue;
                double f = off[i] / diag[i - 1];
                diag[i] -= f * off[i];
                rhs[i] -= f * rhs[i - 1];
            }
            for (int i = m - 1; i >= 0; i--) {
                double r = rhs[i] - (i < m - 1 ? off[i + 1] * lambda[i + 1] : 0);
                lambda[i] = diag[i] < 1e-12 ? 0 : r / diag[i];
            }
            for (int k = 1; k < n - 1; k++) {
                int o = k * 3;
                double w = invMass[k], l0 = lambda[k - 1], l1 = lambda[k];
                for (int c = 0; c < 3; c++) x[o + c] += w * (u[o - 3 + c] * l0 - u[o + c] * l1);
            }
        }
    }

    /**
     * Long-range attachments: no node gets further from either pinned end than the rope between them, so a hanging or
     * whipped chain does not stretch however few iterations converge.
     */
    private void tether() {
        double total = length(), along = 0;
        for (int i = 1; i < n - 1; i++) {
            along += rest[i - 1];
            int o = i * 3;
            for (int end = 0; end < 2; end++) {
                int e = end == 0 ? 0 : (n - 1) * 3;
                double max = end == 0 ? along : total - along;
                double dx = x[o] - x[e], dy = x[o + 1] - x[e + 1], dz = x[o + 2] - x[e + 2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d <= max || d < 1e-12) continue;
                double f = max / d;
                x[o] = x[e] + dx * f; x[o + 1] = x[e + 1] + dy * f; x[o + 2] = x[e + 2] + dz * f;
            }
        }
    }

    private void bend(int i) {
        int a = i * 3, b = a + 6;
        double wa = invMass[i], wb = invMass[i + 2], w = wa + wb;
        if (w == 0) return;
        double min = (rest[i] + rest[i + 1]) * bendMin;
        double dx = x[b] - x[a], dy = x[b + 1] - x[a + 1], dz = x[b + 2] - x[a + 2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= min || d < 1e-12) return;
        double c = (d - min) / (d * w) * bendStiffness;
        x[a] += dx * c * wa; x[a + 1] += dy * c * wa; x[a + 2] += dz * c * wa;
        x[b] -= dx * c * wb; x[b + 1] -= dy * c * wb; x[b + 2] -= dz * c * wb;
    }

    /**
     * In-plane stiffness: the curvature of node i along its width direction (x[i - 1] + x[i + 1] - 2 x[i], dotted with the
     * width) kept under lateralMax of a segment, by moving the three nodes along the width.
     */
    private void lateral(int i) {
        int a = (i - 1) * 3, b = i * 3, c = (i + 1) * 3;
        double wx = lateral[b], wy = lateral[b + 1], wz = lateral[b + 2];
        double k = (x[a] + x[c] - 2 * x[b]) * wx + (x[a + 1] + x[c + 1] - 2 * x[b + 1]) * wy + (x[a + 2] + x[c + 2] - 2 * x[b + 2]) * wz;
        double lim = lateralMax * (rest[i - 1] + rest[i]) / 2, err = k > lim ? k - lim : k < -lim ? k + lim : 0;
        if (err == 0) return;
        double ia = invMass[i - 1], ib = invMass[i], ic = invMass[i + 1], den = ia + 4 * ib + ic;
        if (den == 0) return;
        double f = err / den;
        x[a] -= wx * f * ia; x[a + 1] -= wy * f * ia; x[a + 2] -= wz * f * ia;
        x[b] += wx * 2 * f * ib; x[b + 1] += wy * 2 * f * ib; x[b + 2] += wz * 2 * f * ib;
        x[c] -= wx * f * ic; x[c + 1] -= wy * f * ic; x[c + 2] -= wz * f * ic;
    }

    /** Each node's move this substep (from p) swept against the colliders, then the segment midpoints'. */
    private void collide(RopeCollider collider) {
        for (int i = 1; i < n - 1; i++)
            if (collider.sweep(p, i * 3, x, i * 3, radius, normal)) { touching[i] = true; System.arraycopy(normal, 0, contact, i * 3, 3); }
        // midpoints of the segments between two free nodes, so a thin part cannot slip between nodes
        for (int i = 1; i < n - 2; i++) {
            int a = i * 3, b = a + 3;
            for (int k = 0; k < 3; k++) { scratch[k] = (x[a + k] + x[b + k]) / 2; start[k] = (p[a + k] + p[b + k]) / 2; }
            double mx = scratch[0], my = scratch[1], mz = scratch[2];
            if (!collider.sweep(start, 0, scratch, 0, radius, normal)) continue;
            double wa = invMass[i], wb = invMass[i + 1], fa = 2 * wa / (wa + wb), fb = 2 * wb / (wa + wb);
            double dx = scratch[0] - mx, dy = scratch[1] - my, dz = scratch[2] - mz;
            x[a] += dx * fa; x[a + 1] += dy * fa; x[a + 2] += dz * fa;
            x[b] += dx * fb; x[b + 1] += dy * fb; x[b + 2] += dz * fb;
        }
    }

    /** Nodes three or more apart keep two radii between them. */
    private void self() {
        double min = radius * 2, min2 = min * min;
        for (int i = 1; i < n - 1; i++)
            for (int j = i + 3; j < n - 1; j++) {
                int a = i * 3, b = j * 3;
                double dx = x[b] - x[a], dy = x[b + 1] - x[a + 1], dz = x[b + 2] - x[a + 2], d2 = dx * dx + dy * dy + dz * dz;
                if (d2 >= min2 || d2 < 1e-18) continue;
                double d = Math.sqrt(d2), wa = invMass[i], wb = invMass[j], c = (d - min) / (d * (wa + wb));
                x[a] += dx * c * wa; x[a + 1] += dy * c * wa; x[a + 2] += dz * c * wa;
                x[b] -= dx * c * wb; x[b + 1] -= dy * c * wb; x[b + 2] -= dz * c * wb;
            }
    }

    /** Position-based friction: a touching node loses that share of its sliding along the surface this substep. */
    private void frict() {
        for (int i = 1; i < n - 1; i++) {
            if (!touching[i]) continue;
            int o = i * 3;
            double dx = x[o] - p[o], dy = x[o + 1] - p[o + 1], dz = x[o + 2] - p[o + 2];
            double nx = contact[o], ny = contact[o + 1], nz = contact[o + 2], dn = dx * nx + dy * ny + dz * nz;
            x[o] -= (dx - dn * nx) * friction; x[o + 1] -= (dy - dn * ny) * friction; x[o + 2] -= (dz - dn * nz) * friction;
        }
    }

    /**
     * Ribbon frames for a flat strap: out[i * 3 ..] the unit width direction at node i, across the rope. Segment 0 and the
     * last segment are rigid end fittings (hooks) whose width is that of their mounts (headWidth, tailWidth: a swivel's bar
     * direction); between them the strap carries its width along and spreads the remaining twist evenly, and lies flat on
     * what it touches.
     */
    public void frames(double[] headWidth, double[] tailWidth, double[] out) {
        headMount = headWidth.clone();
        tailMount = tailWidth.clone();
        double[] t = new double[n * 3];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1) * 3, b = Math.min(n - 1, i + 1) * 3;
            if (i == 0 || i == 1) { a = 0; b = 3; }
            if (i == n - 1 || i == n - 2) { a = (n - 2) * 3; b = (n - 1) * 3; }
            setUnit(t, i * 3, x[b] - x[a], x[b + 1] - x[a + 1], x[b + 2] - x[a + 2]);
        }
        across(headWidth, t, 0, out, 0);
        across(headWidth, t, 3, out, 3);
        for (int i = 2; i < n - 1; i++) across(out, i * 3 - 3, t, i * 3, out, i * 3);
        double[] end = new double[3];
        across(tailWidth, t, (n - 2) * 3, end, 0);
        int last = (n - 2) * 3;
        double cos = dot(out, last, end, 0);
        double[] cr = cross(out, last, end, 0);
        double phi = Math.atan2(cr[0] * t[last] + cr[1] * t[last + 1] + cr[2] * t[last + 2], cos);
        if (phi > Math.PI / 2) phi -= Math.PI; else if (phi < -Math.PI / 2) phi += Math.PI;
        double total = 0;
        for (int i = 1; i < n - 2; i++) total += rest[i];
        double s = 0;
        for (int i = 2; i < n - 1; i++) {
            s += rest[i - 1];
            rotate(out, i * 3, t, i * 3, phi * s / total);
        }
        if (dot(out, last, end, 0) < 0) for (int k = 0; k < 3; k++) end[k] = -end[k];
        System.arraycopy(end, 0, out, last, 3);
        across(out, last, t, (n - 1) * 3, out, (n - 1) * 3);
        across(tailWidth, t, (n - 1) * 3, end, 0);
        if (dot(end, 0, out, (n - 1) * 3) < 0) for (int k = 0; k < 3; k++) end[k] = -end[k];
        System.arraycopy(end, 0, out, (n - 1) * 3, 3);
        // flat on what it touches
        for (int i = 2; i < n - 2; i++) {
            if (!touching[i]) continue;
            int o = i * 3;
            double[] c = cross(t, o, contact, o);
            double len = Math.sqrt(dot(c, 0, c, 0));
            if (len < 0.2) continue;
            double sign = dot(c, 0, out, o) < 0 ? -1 : 1;
            for (int k = 0; k < 3; k++) out[o + k] = out[o + k] * 0.2 + c[k] / len * sign * 0.8;
            across(out, o, t, o, out, o);
        }
        double[] tmp = new double[3];
        for (int pass = 0; pass < 2; pass++)
            for (int i = 2; i < n - 2; i++) {
                int o = i * 3;
                for (int k = 0; k < 3; k++) tmp[k] = out[o - 3 + k] + 2 * out[o + k] + out[o + 3 + k];
                across(tmp, 0, t, o, out, o);
            }
        System.arraycopy(out, 0, lateral, 0, lateral.length);
        lateralKnown = true;
    }

    private static void across(double[] w, double[] t, int to, double[] out, int oo) { across(w, 0, t, to, out, oo); }

    /** w with its part along the unit t removed, normalised; any perpendicular if w is along t. */
    private static void across(double[] w, int wo, double[] t, int to, double[] out, int oo) {
        double d = w[wo] * t[to] + w[wo + 1] * t[to + 1] + w[wo + 2] * t[to + 2];
        double ax = w[wo] - d * t[to], ay = w[wo + 1] - d * t[to + 1], az = w[wo + 2] - d * t[to + 2];
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-9) {
            boolean up = Math.abs(t[to + 1]) < 0.9;
            ax = up ? t[to + 2] : 0; ay = up ? 0 : -t[to + 2]; az = up ? -t[to] : t[to + 1];
            len = Math.sqrt(ax * ax + ay * ay + az * az);
        }
        out[oo] = ax / len; out[oo + 1] = ay / len; out[oo + 2] = az / len;
    }

    private static void rotate(double[] v, int o, double[] t, int to, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double[] k = cross(t, to, v, o);
        for (int i = 0; i < 3; i++) v[o + i] = v[o + i] * c + k[i] * s;
    }

    private static double[] cross(double[] a, int ao, double[] b, int bo) {
        return new double[] {a[ao + 1] * b[bo + 2] - a[ao + 2] * b[bo + 1], a[ao + 2] * b[bo] - a[ao] * b[bo + 2], a[ao] * b[bo + 1] - a[ao + 1] * b[bo]};
    }

    private static double dot(double[] a, int ao, double[] b, int bo) { return a[ao] * b[bo] + a[ao + 1] * b[bo + 1] + a[ao + 2] * b[bo + 2]; }

    private static void setUnit(double[] out, int o, double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-12) { out[o] = 0; out[o + 1] = 1; out[o + 2] = 0; return; }
        out[o] = x / len; out[o + 1] = y / len; out[o + 2] = z / len;
    }

    private static double dist(double[] a, int ao, double[] b, int bo) {
        double dx = a[ao] - b[bo], dy = a[ao + 1] - b[bo + 1], dz = a[ao + 2] - b[bo + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
