package com.antaurora.apofirstlight.client.rain;

import java.util.Arrays;
import java.util.Random;

/**
 * The water on one pane of a T15's glass ({@link TruckGlass}), on the client (docs/vehicles/t15_windshield_rain_design.md):
 * drops, the fine beads' build-up and, on the windshield, a film map and the water the wiper blades push, in the pane's
 * glass coordinates.
 * <ul>
 * <li>Drops (at most {@link #max}, by the pane's area): rain lands on the glass, as much as the pane faces into it (up,
 * and forward into the rain when driving; upright glass still catches some blown rain). A drop landing on another merges
 * with it (volumes add). Drops over the pane's slide size run down, picking up what they touch and leaving small beads
 * and a wet trail. Driving, the wind pushes them along the glass (the windshield: up and out toward the pillars; the
 * door windows: back; the rear window sits in the wake), the big ones first.</li>
 * <li>Beads: how far the fine beads have built up (0..1: the shader shows the simulated bead tile up to it; they build up
 * in the rain and dry slowly). On the windshield inside the blades' sweeps a map ({@link #FW} x {@link #FH} cells: wet,
 * and streak = the thin film a blade just left, which smears lights along the stroke and fades in about a second);
 * {@link #outside} everywhere else.</li>
 * <li>Blades (the windshield): each blade's swept sector (between last frame's turn and this one's) wipes the drops and
 * the film; the water goes into {@link #BINS} bins along the blade and rides on its leading edge; where the stroke turns
 * back the bins are left on the glass as a line of drops.</li>
 * </ul>
 */
final class GlassWater {
    static final int MAX = 3000, FW = 192, FH = 112, BINS = 18, TRAILS = 256;
    /** Shape cells in textures/effect/windshield_drops: resting ones, then running ones (tools/blender/windshield_water). */
    static final int RESTING_SHAPES = 24, RUNNING_SHAPES = 8;
    /** The windshield's film map extent (glass coordinates, m). */
    static final double FU0 = -0.80, FU1 = 0.80, FS0 = 0.0, FS1 = 0.88;
    /**
     * Drop radii (m; the user 2026-10-17: smaller than the concept sheets, about 0.6 of them): most 0.6-1.5 mm, some up to
     * 2.6, a few to 3.6. Over R_SLIDE a drop runs down the windshield (its tilt holds about 2.5 mm; steeper glass less).
     */
    static final double R_SLIDE = 0.0024, R_MAX = 0.0045;
    /**
     * Drops a second landing on the whole windshield in full rain, standing (the fine beads are the shader's tile; these
     * are the ones big enough to see one by one); other panes by their area and how they face the rain.
     */
    private static final double LAND_RATE = 70;
    /** Upright glass catches at least this share of what the windshield does per area (rain blown by the wind, spray). */
    private static final double BLOWN = 0.2;
    /** Wind (forward speed, m/s) from which drops start to creep along the glass. */
    private static final double WIND_START = 7.0;
    /** The spatial hash for merges: cell size (m). */
    private static final double CELL = 0.008;

    final TruckGlass.Pane pane;
    final int max;
    /** The slide size and the slide speed's scale on this pane (steeper glass: smaller drops run, faster). */
    private final double rSlide, downK;
    final float[] u, s, r, vu, vs;
    /** Distance a moving drop has run since its last bead (m); where its trail's last point is (glass coordinates). */
    private final float[] run, tu, ts;
    /**
     * Each drop's look: its resting and running shape cells, the resting shape's small tilt off straight down the glass
     * (rad), how far it has gone over to the running shape (0..1), and its last way of travel (unit, glass coordinates).
     */
    final byte[] rest, running;
    final float[] tilt, runK, wu, ws;
    int count;
    /** The windshield's film map (empty on the other panes). */
    final float[] wet, streak;
    /** The beads' build-up where no blade reaches (as a film cell's, never wiped). */
    float outside;
    /** Each blade's pushed water (volume as r^3, per bin) and its direction of travel (+1 up, -1 down, 0 still). */
    final float[][] load;
    final int[] travel;
    final double[] turn;
    /** Slide trails: ring of segments (u0, s0, u1, s1, width, age). */
    final float[] trail = new float[TRAILS * 6];
    private int trailNext;
    /** The film map changed since it was last uploaded. */
    boolean filmDirty = true;
    /** Water on the glass at all (drops or beads): when none and no rain, nothing is drawn. */
    boolean any;

    private final double gu0, gs0;
    private final int gu, gs;
    private final int[] head, next;
    /** Each film cell's turn from the parked blade about each pivot (rad; NaN where the blade never reaches). */
    private static final float[][] FILM_TURN = new float[WindshieldGlass.WIPERS.length][FW * FH];

    static {
        for (int w = 0; w < WindshieldGlass.WIPERS.length; w++) {
            WindshieldGlass.Wiper wp = WindshieldGlass.WIPERS[w];
            for (int fs = 0; fs < FH; fs++)
                for (int fu = 0; fu < FW; fu++) {
                    double uu = FU0 + (fu + 0.5) * (FU1 - FU0) / FW, ss = FS0 + (fs + 0.5) * (FS1 - FS0) / FH;
                    double du = uu - wp.pu(), ds = ss - wp.ps(), rest = wp.restAngle(Math.hypot(du, ds), 0);
                    FILM_TURN[w][fs * FW + fu] = Double.isNaN(rest) ? Float.NaN : (float) (Math.atan2(ds, du) - rest);
                }
        }
    }
    private final Random rnd;
    private double landCarry;

    GlassWater(TruckGlass.Pane pane, long seed) {
        this.pane = pane;
        rnd = new Random(seed);
        max = Math.max(600, (int) Math.round(MAX * pane.area / TruckGlass.WINDSHIELD.area));
        u = new float[max];
        s = new float[max];
        r = new float[max];
        vu = new float[max];
        vs = new float[max];
        run = new float[max];
        tu = new float[max];
        ts = new float[max];
        rest = new byte[max];
        running = new byte[max];
        tilt = new float[max];
        runK = new float[max];
        wu = new float[max];
        ws = new float[max];
        next = new int[max];
        int blades = pane.wipers.length;
        wet = new float[blades > 0 ? FW * FH : 0];
        streak = new float[wet.length];
        load = new float[blades][BINS];
        travel = new int[blades];
        turn = new double[blades];
        Arrays.fill(turn, Double.NaN);
        double g = TruckGlass.WINDSHIELD.gravity;
        rSlide = R_SLIDE * Math.sqrt(g / pane.gravity);
        downK = pane.gravity / g;
        gu0 = pane.uMin - 0.05;
        gs0 = pane.s0 - 0.05;
        gu = (int) Math.ceil((pane.uMax + 0.05 - gu0) / CELL);
        gs = (int) Math.ceil((pane.s1 + 0.05 - gs0) / CELL);
        head = new int[gu * gs];
    }

    /**
     * How much rain the pane catches against the standing windshield, per area: rain falling about 6 m/s, met at the
     * truck's forward speed (m/s).
     */
    private double exposure(double forward) {
        double standing = 6 * TruckGlass.WINDSHIELD.facingUp();
        return Math.max(6 * pane.facingUp() + pane.facingForward() * forward, BLOWN * standing) / standing;
    }

    /**
     * One step (s). rain: rain on the glass now (0..1; 0 under a roof or when it does not rain there); forward: the truck's
     * speed forward (m/s); blades: the wipers' turns (deg; the windshield only).
     */
    void update(double dt, double rain, double forward, double... blades) {
        if (dt <= 0) return;
        dt = Math.min(dt, 0.1);
        rebuildHash();
        double wind = Math.max(0, forward - WIND_START), ex = exposure(forward);
        if (rain > 0) {
            double rate = LAND_RATE * pane.area / TruckGlass.WINDSHIELD.area * rain * ex;
            landCarry += rate * dt;
            int n = (int) landCarry;
            landCarry -= n;
            for (int i = 0; i < n; i++) land();
        }
        move(dt, wind);
        if (rain <= 0) evaporate(dt);
        for (int w = 0; w < pane.wipers.length; w++) wipe(w, blades[w], dt);
        film(dt, rain, forward, ex);
        for (int i = 0; i < TRAILS; i++) trail[i * 6 + 5] += (float) dt;
        any = count > 0 || filmAny || loaded();
    }

    private boolean filmAny;

    private boolean loaded() {
        for (float[] l : load) for (float v : l) if (v > 0) return true;
        return false;
    }

    // ---- drops ----

    /** Without rain the drops slowly dry up (a 1 mm one in about four minutes). */
    private void evaporate(double dt) {
        float shrink = (float) (dt * 4e-6);
        for (int i = 0; i < count; i++) {
            r[i] -= shrink;
            if (r[i] < 0.0001F) {
                remove(i);
                i--;
            }
        }
    }

    private double radius() {
        double p = rnd.nextDouble();
        return p < 0.80 ? 0.0006 + rnd.nextDouble() * 0.0009 : p < 0.97 ? 0.0015 + rnd.nextDouble() * 0.0011 : 0.0026 + rnd.nextDouble() * 0.0010;
    }

    /** A drop lands somewhere on the pane (a few tries within the outline's box). */
    private void land() {
        for (int k = 0; k < 4; k++) {
            double uu = pane.uMin + rnd.nextDouble() * (pane.uMax - pane.uMin), ss = pane.s0 + rnd.nextDouble() * (pane.s1 - pane.s0);
            if (pane.inside(uu, ss) < 0.001) continue;
            add(uu, ss, radius());
            return;
        }
    }

    /** A drop at (u, s): merged into one it touches, or a new one (the smallest replaced when full). */
    private void add(double uu, double ss, double rr) {
        if (pane.inside(uu, ss) < 0) return;   // a bead shed past the edge: off the glass already
        int hit = touching(uu, ss, rr, -1);
        if (hit >= 0) {
            merge(hit, uu, ss, rr);
            return;
        }
        int i;
        if (count < max) i = count++;
        else {
            i = rnd.nextInt(max);
            if (r[i] > 0.0012) return;   // full: a fresh small drop only takes the place of another small one
            unhash(i);
        }
        u[i] = (float) uu;
        s[i] = (float) ss;
        r[i] = (float) rr;
        vu[i] = vs[i] = run[i] = runK[i] = 0;
        tu[i] = u[i];
        ts[i] = s[i];
        rest[i] = (byte) rnd.nextInt(RESTING_SHAPES);
        running[i] = (byte) (RESTING_SHAPES + rnd.nextInt(RUNNING_SHAPES));
        tilt[i] = (float) ((rnd.nextDouble() * 2 - 1) * 0.35);
        wu[i] = 0;
        ws[i] = -1;
        hash(i);
    }

    /** Drop i takes in a drop of radius rr at (uu, ss): volumes add, the centre moves to their balance. */
    private void merge(int i, double uu, double ss, double rr) {
        double a = (double) r[i] * r[i] * r[i], b = rr * rr * rr, v = a + b;
        unhash(i);
        u[i] = (float) ((u[i] * a + uu * b) / v);
        s[i] = (float) ((s[i] * a + ss * b) / v);
        r[i] = (float) Math.min(R_MAX, Math.cbrt(v));
        hash(i);
    }

    /** Drop i gone: the last drop takes its place (in the hash as before: a drop being moved stays out of it). */
    private void remove(int i) {
        unhash(i);
        int last = --count;
        if (i != last) {
            boolean hashed = next[last] != -2;
            unhash(last);
            u[i] = u[last];
            s[i] = s[last];
            r[i] = r[last];
            vu[i] = vu[last];
            vs[i] = vs[last];
            run[i] = run[last];
            tu[i] = tu[last];
            ts[i] = ts[last];
            rest[i] = rest[last];
            running[i] = running[last];
            tilt[i] = tilt[last];
            runK[i] = runK[last];
            wu[i] = wu[last];
            ws[i] = ws[last];
            if (hashed) hash(i);
            else next[i] = -2;
        }
    }

    private void move(double dt, double wind) {
        double k = Math.min(1, dt * 6);
        for (int i = 0; i < count; i++) {
            double rr = r[i];
            // gravity: a big drop runs down (faster the bigger, and the steeper the glass); wind: along the pane's way,
            // the pinned small ones stay
            double down = rr > rSlide ? Math.min(0.22, 0.05 + 40 * (rr - rSlide)) * downK : 0;
            double push = pane.windK * Math.max(0, wind * 15 * rr - 0.05 - (rr > rSlide ? 0 : 0.02));
            double wantU = push * (pane.windU + pane.spread * u[i]), wantS = push * pane.windS - (push > 0 ? 0.6 * down : down);
            vu[i] += (float) ((wantU - vu[i]) * k);
            vs[i] += (float) ((wantS - vs[i]) * k);
            if (Math.abs(vu[i]) + Math.abs(vs[i]) < 0.002) {
                vu[i] = vs[i] = 0;
                runK[i] = (float) Math.max(0, runK[i] - dt * 2);   // settles back into a resting shape
                continue;
            }
            double du = vu[i] * dt, ds = vs[i] * dt, step = Math.hypot(du, ds);
            double speed = Math.hypot(vu[i], vs[i]);
            wu[i] = (float) (vu[i] / speed);
            ws[i] = (float) (vs[i] / speed);
            runK[i] = (float) Math.min(1, runK[i] + dt * 4 * Math.min(1, speed / 0.03));
            unhash(i);
            u[i] += (float) du;
            s[i] += (float) ds;
            // gathers the drops it runs into
            int hit;
            while ((hit = touching(u[i], s[i], r[i], i)) >= 0) {
                double a = (double) r[i] * r[i] * r[i], b = (double) r[hit] * r[hit] * r[hit];
                r[i] = (float) Math.min(R_MAX, Math.cbrt(a + b));
                int last = count - 1;
                remove(hit);
                if (i == last) i = hit;   // this drop was the last one and moved into the gap
            }
            // beads and a trail behind a running drop
            run[i] += (float) step;
            if (run[i] > 0.006 + rnd.nextDouble() * 0.008) {
                run[i] = 0;
                double br = 0.00015 + rnd.nextDouble() * 0.0002, a = (double) r[i] * r[i] * r[i] - br * br * br;
                if (a > 0 && rnd.nextFloat() < 0.35F) {
                    r[i] = (float) Math.cbrt(a);
                    double back = r[i] + br + 0.0005, len = Math.max(1e-6, step);
                    hash(i);
                    add(u[i] - du / len * back, s[i] - ds / len * back, br);
                    unhash(i);
                }
            }
            // a running big drop's wet trail, a segment every 6 mm
            if (rr > rSlide * 0.9 && Math.hypot(u[i] - tu[i], s[i] - ts[i]) > 0.006) {
                trail(tu[i], ts[i], u[i], s[i], r[i] * 1.3);
                tu[i] = u[i];
                ts[i] = s[i];
            } else if (rr <= rSlide * 0.9) {
                tu[i] = u[i];
                ts[i] = s[i];
            }
            if (pane.inside(u[i], s[i]) < -r[i]) {
                // off the glass (down into the sill, up over the roof, round the pillars)
                hash(i);
                remove(i);
                i--;
                continue;
            }
            hash(i);
        }
    }

    private void trail(double u0, double s0, double u1, double s1, double w) {
        int t = trailNext * 6;
        trail[t] = (float) u0;
        trail[t + 1] = (float) s0;
        trail[t + 2] = (float) u1;
        trail[t + 3] = (float) s1;
        trail[t + 4] = (float) w;
        trail[t + 5] = 0;
        trailNext = (trailNext + 1) % TRAILS;
    }

    // ---- wipers (the windshield) ----

    private final double[] at = new double[2];

    /** Blade w from last frame's turn to deg now: what it passed over is wiped, its water rides on, left where it turns. */
    private void wipe(int w, double deg, double dt) {
        WindshieldGlass.Wiper wp = pane.wipers[w];
        double a1 = Math.toRadians(deg), a0 = Double.isNaN(turn[w]) ? a1 : turn[w];
        turn[w] = a1;
        int dir = a1 > a0 + 1e-6 ? 1 : a1 < a0 - 1e-6 ? -1 : 0;
        if (dir != 0 && travel[w] != 0 && dir != travel[w]) leave(w, a0, travel[w]);   // turned back: the water line
        if (dir == 0) {
            if (travel[w] != 0 && a1 < 1e-4) leave(w, a1, travel[w]);   // parked: what it carried drains off the base
            travel[w] = 0;
            return;
        }
        travel[w] = dir;
        double lo = Math.min(a0, a1), hi = Math.max(a0, a1);
        // drops in the sector: each drop's place turned back by the parked blade's angle at its distance (the blade runs
        // across, not out from the pivot), then tested against the sector's edges (no trigonometry per drop)
        double m = 0.006, rs = wp.rs(), in = wp.arm() - wp.len() / 2, out = wp.arm() + wp.len() / 2;
        double lx = Math.cos(lo - m), ly = Math.sin(lo - m), hx = Math.cos(hi + m), hy = Math.sin(hi + m), mx = Math.cos((lo + hi) / 2), my = Math.sin((lo + hi) / 2);
        for (int i = 0; i < count; i++) {
            double du = u[i] - wp.pu(), ds = s[i] - wp.ps(), q = du * du + ds * ds - rs * rs, ri = r[i];
            if (q <= (in - ri) * (in - ri) || q >= (out + ri) * (out + ri)) continue;
            double along = Math.sqrt(q), rho = Math.sqrt(q + rs * rs);
            double cx = (du * along + ds * rs) / rho, cy = (ds * along - du * rs) / rho;
            if (lx * cy - ly * cx < 0 || cx * hy - cy * hx < 0 || cx * mx + cy * my <= 0) continue;
            int bin = Math.max(0, Math.min(BINS - 1, (int) (((along - wp.arm()) / wp.len() + 0.5) * BINS)));
            load[w][bin] += ri * ri * ri;
            remove(i);
            i--;
        }
        // the film under it: clear, a thin streak left (each cell's angle about this pivot worked out once, FILM_TURN)
        float[] cells = FILM_TURN[w];
        float flo = (float) (lo - 0.01), fhi = (float) (hi + 0.01);
        for (int c = 0; c < FW * FH; c++) {
            float a = cells[c];
            if (!(a >= flo && a <= fhi)) continue;   // NaN (off the blade) fails too
            streak[c] = Math.max(streak[c], 0.4F + 1.2F * wet[c]);
            wet[c] = 0;
            filmDirty = true;
        }
        // a little of what it carries slips under the rubber
        for (int b = 0; b < BINS; b++) {
            float v = load[w][b];
            if (v <= 0) continue;
            if (rnd.nextFloat() < dt * 3) {
                double br = Math.min(Math.cbrt(v) * 0.5, 0.0005 + rnd.nextDouble() * 0.0004);
                wp.blade((b + 0.5) / BINS - 0.5, a0, at);
                load[w][b] = (float) Math.max(0, v - br * br * br);
                add(at[0], at[1], br);
            }
        }
    }

    /** Blade w's water left on the glass at turn a (rad), ahead of it in its direction of travel: the water line. */
    private void leave(int w, double a, int dir) {
        WindshieldGlass.Wiper wp = pane.wipers[w];
        for (int b = 0; b < BINS; b++) {
            double v = load[w][b];
            load[w][b] = 0;
            while (v > 1e-11) {
                double rr = Math.min(Math.cbrt(v), 0.0011 + rnd.nextDouble() * 0.0012);
                v -= rr * rr * rr;
                double t = (b + rnd.nextDouble()) / BINS - 0.5, ahead = dir * (0.004 + rr + rnd.nextDouble() * 0.003);
                wp.blade(t, a, at);
                double rho = Math.hypot(at[0] - wp.pu(), at[1] - wp.ps());
                wp.blade(t, a + ahead / Math.max(0.05, rho), at);
                if (pane.inside(at[0], at[1]) > rr) add(at[0], at[1], rr);
            }
        }
    }

    // ---- beads ----

    private void film(double dt, double rain, double forward, double ex) {
        // fine droplets build up in the rain (less at speed: the wind blows them off; less and slower on glass that
        // catches less rain) and dry slowly without it
        double target = rain > 0 ? Math.min(1, 0.35 + 0.65 * rain) * Math.max(0.3, 1 - 0.025 * Math.max(0, forward)) * Math.min(1, 0.5 + 0.5 * ex) : 0;
        double build = rain > 0 ? dt * 0.06 * (0.4 + rain) * (1 + 0.15 * Math.max(0, forward)) * Math.max(0.3, Math.min(1, ex)) : 0;
        double dry = dt * (0.012 + 0.02 * Math.max(0, forward - WIND_START));
        double fade = Math.exp(-dt / 0.9);
        outside = outside < target ? (float) Math.min(target, outside + build * (1.15 - outside)) : (float) Math.max(target, outside - dry);
        boolean anyNow = outside > 0.004F;
        for (int c = 0; c < wet.length; c++) {
            float w0 = wet[c], st0 = streak[c];
            float w = w0 < target ? (float) Math.min(target, w0 + build * (1.15 - w0)) : (float) Math.max(target, w0 - dry);
            float st = st0 * (float) fade;
            if (st < 0.01F) st = 0;
            wet[c] = w;
            streak[c] = st;
            if (Math.abs(w - w0) > 0.002F || st != st0) filmDirty = true;
            anyNow |= w > 0.004F || st > 0;
        }
        filmAny = anyNow;
    }

    // ---- spatial hash ----

    private int cell(double uu, double ss) {
        int cu = (int) Math.floor((uu - gu0) / CELL), cs = (int) Math.floor((ss - gs0) / CELL);
        if (cu < 0 || cs < 0 || cu >= gu || cs >= gs) return -1;
        return cs * gu + cu;
    }

    private void rebuildHash() {
        Arrays.fill(head, -1);
        for (int i = 0; i < count; i++) hash(i);
    }

    private void hash(int i) {
        int c = cell(u[i], s[i]);
        if (c < 0) {
            next[i] = -2;
            return;
        }
        next[i] = head[c];
        head[c] = i;
    }

    private void unhash(int i) {
        if (next[i] == -2) return;
        int c = cell(u[i], s[i]);
        if (c < 0) return;
        int p = -1;
        for (int j = head[c]; j >= 0; p = j, j = next[j]) {
            if (j != i) continue;
            if (p < 0) head[c] = next[j];
            else next[p] = next[j];
            break;
        }
        next[i] = -2;
    }

    /** A drop (not skip) overlapping a circle of radius rr at (uu, ss), or -1. */
    private int touching(double uu, double ss, double rr, int skip) {
        int cu = (int) Math.floor((uu - gu0) / CELL), cs = (int) Math.floor((ss - gs0) / CELL);
        for (int y = cs - 1; y <= cs + 1; y++) {
            if (y < 0 || y >= gs) continue;
            for (int x = cu - 1; x <= cu + 1; x++) {
                if (x < 0 || x >= gu) continue;
                for (int j = head[y * gu + x]; j >= 0; j = next[j]) {
                    if (j == skip) continue;
                    double d = rr + r[j], du = u[j] - uu, ds = s[j] - ss;
                    if (du * du + ds * ds < d * d) return j;
                }
            }
        }
        return -1;
    }
}
