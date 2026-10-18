package com.antaurora.apofirstlight.vehicle.physics;

import com.antaurora.apofirstlight.vehicle.TransferLever;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline smoke check of the vehicle physics V0-b packages 1 and 2 (docs/vehicles/first_pickup_v0_design.md 8.3, 8.5):
 * the T15 on made-up ground (a plane, tilted or not, and block-like boxes for a kerb and a wall), no game classes. Run
 * with ./gradlew vehicleCheck. Each case prints its numbers and PASS / FAIL against a loose bound (the bounds only catch
 * a broken model; the feel is checked in game).
 */
public final class VehiclePhysicsCheck {
    private static int failures;

    /** A plane y = y0 + sx * x + sz * z with block-like boxes {x0, y0, z0, x1, y1, z1} on it; grip for all. */
    static final class TestWorld implements VehicleWorld {
        final double y0, sx, sz, grip;
        final List<double[]> boxes = new ArrayList<>();

        TestWorld(double y0, double sx, double sz, double grip) {
            this.y0 = y0;
            this.sx = sx;
            this.sz = sz;
            this.grip = grip;
        }

        TestWorld box(double x0, double by0, double z0, double x1, double by1, double z1) {
            boxes.add(new double[]{x0, by0, z0, x1, by1, z1});
            return this;
        }

        double plane(double x, double z) { return y0 + sx * x + sz * z; }

        @Override
        public boolean ground(double x, double z, double yHint, double yTop, double yBottom, Ground out) {
            double best = plane(x, z);
            boolean box = false;
            for (double[] b : boxes) {
                if (x < b[0] || x >= b[3] || z < b[2] || z >= b[5] || b[1] > yTop) continue;
                if (b[4] > best) {
                    best = b[4];
                    box = true;
                }
            }
            if (best < yBottom) {
                out.none();
                return true;
            }
            if (box) out.set(best, 0, 1, 0, grip, Source.BLOCK);
            else {
                double len = Math.sqrt(sx * sx + 1 + sz * sz);
                out.set(best, -sx / len, 1 / len, -sz / len, grip, Source.SMOOTH);
            }
            return true;
        }

        @Override
        public boolean solid(double x, double y, double z, Push out) {
            for (double[] b : boxes) {
                if (x < b[0] || x > b[3] || y < b[1] || y > b[4] || z < b[2] || z > b[5]) continue;
                double best = b[4] - y;
                out.set(0, 1, 0, best);
                if (x - b[0] < best) out.set(-1, 0, 0, best = x - b[0]);
                if (b[3] - x < best) out.set(1, 0, 0, best = b[3] - x);
                if (z - b[2] < best) out.set(0, 0, -1, best = z - b[2]);
                if (b[5] - z < best) out.set(0, 0, 1, b[5] - z);
                return true;
            }
            double h = plane(x, z);
            if (y >= h) return false;
            double len = Math.sqrt(sx * sx + 1 + sz * sz);
            out.set(-sx / len, 1 / len, -sz / len, (h - y) / len);
            return true;
        }
    }

    /**
     * A test driver for package 2: holds the key until the engine runs, puts it in 1st, shifts up near 3300 rpm and down
     * below 1300 rpm (with the clutch in), never into reverse unless asked.
     */
    static final class Driver {
        boolean fuel = true;
        int key;

        void tick(VehiclePhysics p, VehicleWorld world, float throttle, float brake, float steer, boolean handbrake) {
            Drivetrain d = p.drivetrain;
            boolean ignition = !d.running && fuel && key < 200;
            if (ignition) key++;
            int shift = 0;
            if (d.running && !d.shifting()) {
                if (d.gear == 0) shift = 1;
                else if (d.gear > 0 && d.locked && d.rpm() > 3300 && d.gear < 5) shift = 1;
                else if (d.gear > 1 && d.locked && d.rpm() < 1300) shift = -1;
            }
            p.tick(new VehicleInput(throttle, brake, steer, handbrake, shift, false, ignition, fuel, true), world);
        }
    }

    public static void main(String[] args) {
        VehicleSpec spec = VehicleSpec.T15;
        Vector3d o = new Vector3d();
        Driver dr = new Driver();

        // 1. settle on flat ground from 5 cm up, parked: ride height, rest, sleep
        TestWorld flat = new TestWorld(64, 0, 0, 1.0);
        VehiclePhysics p = new VehiclePhysics(spec);
        p.reset(0, 64.05, 0, 0);
        int slept = -1;
        for (int t = 0; t < 200 && slept < 0; t++) {
            p.tick(VehicleInput.PARKED, flat);
            if (p.asleep) slept = t;
        }
        p.origin(o);
        check("settle", String.format(Locale.ROOT, "origin y %.4f, travel %s, asleep after %d ticks", o.y, fmt(p.travel), slept),
                Math.abs(o.y - 64) < 0.02 && maxAbs(p.travel) < 0.02 && slept > 0 && slept < 120);

        // 2. the key held: preheat, crank, it fires and idles
        p.wake();
        int fired = -1;
        for (int t = 0; t < 120; t++) {
            dr.tick(p, flat, 0, 0, 0, true);
            if (fired < 0 && p.drivetrain.running) fired = t;
        }
        check("start", String.format(Locale.ROOT, "fired after %.2f s, idle %.0f rpm, gear %d, moved %.3f m", fired * 0.05, p.drivetrain.rpm(),
                p.drivetrain.gear, p.origin(new Vector3d()).distance(0, 64, 0)), fired > 20 && fired < 60 && Math.abs(p.drivetrain.rpm() - 750) < 40);

        // 3. full throttle on flat asphalt, shifting up: 0-100, top speed (limiter)
        double[] at = new double[3];
        int to100 = -1, shifts = 0, lastGear = p.drivetrain.gear;
        double peakRpm = 0;
        for (int t = 1; t <= 900; t++) {
            dr.tick(p, flat, 1, 0, 0, false);
            if (p.drivetrain.gear != lastGear) { shifts++; lastGear = p.drivetrain.gear; }
            peakRpm = Math.max(peakRpm, p.drivetrain.rpm());
            if (t == 100) at[0] = p.forwardSpeed();
            if (t == 200) at[1] = p.forwardSpeed();
            if (to100 < 0 && p.forwardSpeed() * 3.6 >= 99) to100 = t;
        }
        at[2] = p.forwardSpeed();
        p.origin(o);
        check("accelerate", String.format(Locale.ROOT, "%.0f / %.0f km/h after 5 / 10 s, 99 km/h after %.1f s, %.0f km/h after 45 s in gear %d (%d shifts), peak %.0f rpm, drifted x %.3f",
                at[0] * 3.6, at[1] * 3.6, to100 * 0.05, at[2] * 3.6, p.drivetrain.gear, shifts, peakRpm, o.x),
                at[1] * 3.6 > 45 && at[1] * 3.6 < 90 && to100 > 0 && at[2] * 3.6 < 102 && peakRpm < 4400 && Math.abs(o.x) < 0.5);

        // 4. coasting in 4th from 60 km/h: engine braking, the engine keeps running
        VehiclePhysics c = new VehiclePhysics(spec);
        c.reset(0, 64, 0, 0);
        Driver cd = new Driver();
        for (int t = 0; t < 2000 && c.forwardSpeed() < 60 / 3.6; t++) cd.tick(c, flat, 1, 0, 0, false);
        for (int t = 0; t < 100 && (c.drivetrain.gear != 4 || c.drivetrain.shifting()); t++) {
            int s = c.drivetrain.shifting() ? 0 : Integer.signum(4 - c.drivetrain.gear);
            c.tick(new VehicleInput(1, 0, 0, false, s, false, false, true, true), flat);
        }
        for (int t = 0; t < 20; t++) c.tick(new VehicleInput(1, 0, 0, false), flat);   // the clutch back in
        double c0 = c.forwardSpeed();
        for (int t = 0; t < 100; t++) c.tick(new VehicleInput(0, 0, 0, false), flat);
        check("engine braking", String.format(Locale.ROOT, "4th: %.0f to %.0f km/h in 5 s off the throttle (%.2f m/s2), %.0f rpm, running %s", c0 * 3.6,
                c.forwardSpeed() * 3.6, (c0 - c.forwardSpeed()) / 5, c.drivetrain.rpm(), c.drivetrain.running),
                (c0 - c.forwardSpeed()) / 5 > 0.25 && (c0 - c.forwardSpeed()) / 5 < 1.5 && c.drivetrain.running && c.drivetrain.gear == 4);

        // 5. brake from top speed (brake pedal only): the clutch lets out, the engine idles on
        double z0 = o.z, v0 = p.forwardSpeed();
        int ticks = 0;
        while (p.forwardSpeed() > 0.5 && ticks < 400) {
            dr.tick(p, flat, 0, 1, 0, false);
            ticks++;
        }
        for (int t = 0; t < 40; t++) dr.tick(p, flat, 0, 1, 0, false);
        p.origin(o);
        check("brake", String.format(Locale.ROOT, "%.0f km/h to 2 km/h in %.2f s over %.1f m, then %.0f rpm, gear %d, clutch %.2f, running %s", v0 * 3.6, ticks * 0.05,
                o.z - z0, p.drivetrain.rpm(), p.drivetrain.gear, p.drivetrain.clutch, p.drivetrain.running),
                o.z - z0 < 70 && o.z - z0 > 25 && p.drivetrain.running && p.drivetrain.clutch < 0.05);

        // 6. reverse: down through 1st and neutral into R (only once stopped), then throttle
        for (int t = 0; t < 60 && p.drivetrain.gear != -1; t++)
            p.tick(new VehicleInput(0, 1, 0, false, p.drivetrain.shifting() ? 0 : -1, false, false, true, true), flat);
        int gearR = p.drivetrain.gear;
        for (int t = 0; t < 100; t++) p.tick(new VehicleInput(1, 0, 0, false, 0, false, false, true, true), flat);
        check("reverse", String.format(Locale.ROOT, "gear %d, %.1f km/h after 5 s of throttle, %.0f rpm", gearR, p.forwardSpeed() * 3.6, p.drivetrain.rpm()),
                gearR == -1 && p.forwardSpeed() * 3.6 < -8 && p.forwardSpeed() * 3.6 > -36);
        // R refused while rolling forward
        VehiclePhysics rr = new VehiclePhysics(spec);
        rr.reset(0, 64, 0, 0);
        Driver rd = new Driver();
        for (int t = 0; t < 200; t++) rd.tick(rr, flat, rr.forwardSpeed() < 3 ? 1 : 0, 0, 0, false);
        for (int t = 0; t < 30; t++) rr.tick(new VehicleInput(0, 0, 0, false, rr.drivetrain.shifting() ? 0 : -1, false, false, true, true), flat);
        check("no reverse while rolling", String.format(Locale.ROOT, "%.1f km/h, gear %d", rr.forwardSpeed() * 3.6, rr.drivetrain.gear),
                rr.drivetrain.gear == 0 && rr.forwardSpeed() > 1.0);

        // 5. parked on a 15 degree slope, nose uphill and across: no creeping
        for (float yaw : new float[]{0, 90}) {
            TestWorld slope = new TestWorld(64, 0, Math.tan(Math.toRadians(15)), 1.0);
            VehiclePhysics s = new VehiclePhysics(spec);
            s.reset(0, 64.02, 0, yaw);
            for (int t = 0; t < 100; t++) s.tick(VehicleInput.PARKED, slope);
            Vector3d a = s.origin(new Vector3d());
            for (int t = 0; t < 100; t++) s.tick(VehicleInput.PARKED, slope);
            Vector3d b = s.origin(new Vector3d());
            check("slope hold " + (int) yaw, String.format(Locale.ROOT, "moved %.4f m in the last 5 s, asleep %s", a.distance(b), s.asleep), a.distance(b) < 0.02);
        }

        // 6. a 0.15 m kerb across the way at z = 5: driven over at walking pace
        TestWorld kerb = new TestWorld(64, 0, 0, 1.0).box(-10, 63, 5, 10, 64.15, 40);
        VehiclePhysics k = new VehiclePhysics(spec);
        k.reset(0, 64.0, 0, 0);
        Driver kd = new Driver();
        for (int t = 0; t < 400; t++) kd.tick(k, kerb, k.forwardSpeed() < 2.0 ? 1 : 0, 0, 0, false);
        k.origin(o);
        check("kerb 0.15", String.format(Locale.ROOT, "origin z %.2f, y %.3f", o.z, o.y), o.z > 10 && Math.abs(o.y - 64.15) < 0.03);

        // 7. a full block across the way: it does not climb it
        TestWorld wall = new TestWorld(64, 0, 0, 1.0).box(-10, 63, 5, 10, 65, 40);
        VehiclePhysics w = new VehiclePhysics(spec);
        w.reset(0, 64.0, 0, 0);
        double top = 0;
        Driver wd = new Driver();
        for (int t = 0; t < 400; t++) {
            wd.tick(w, wall, 1, 0, 0, false);
            top = Math.max(top, w.origin(new Vector3d()).y);
        }
        w.origin(o);
        check("wall 1.0", String.format(Locale.ROOT, "origin z %.2f (bumper at %.2f), highest y %.3f, speed %.2f", o.z, o.z + 2.39, top, w.forwardSpeed()),
                o.z + 2.39 < 5.2 && top < 64.35 && Math.abs(w.forwardSpeed()) < 1.0);

        // 8. full lock to the right at about 5 m/s: radius, roll, all wheels down
        VehiclePhysics r = new VehiclePhysics(spec);
        r.reset(0, 64.0, 0, 0);
        double minX = 0, maxX = 0, minZ = 0, maxZ = 0, worstUp = 1;
        boolean lifted = false;
        Driver td = new Driver();
        for (int t = 0; t < 700; t++) {
            td.tick(r, flat, r.forwardSpeed() < 5 ? 1 : 0, 0, 1, false);
            if (t < 300) continue;   // started, up to speed and into the circle first
            r.origin(o);
            minX = Math.min(minX, o.x);
            maxX = Math.max(maxX, o.x);
            minZ = Math.min(minZ, o.z);
            maxZ = Math.max(maxZ, o.z);
            worstUp = Math.min(worstUp, r.q.transform(new Vector3d(0, 1, 0)).y);
            for (boolean touch : r.contact) lifted |= !touch;
        }
        double radius = ((maxX - minX) + (maxZ - minZ)) / 4;
        check("turn right", String.format(Locale.ROOT, "radius %.1f m (circle x %.1f..%.1f), worst tilt %.1f deg, a wheel lifted %s", radius, minX, maxX,
                Math.toDegrees(Math.acos(Math.min(1, worstUp))), lifted), radius > 4.5 && radius < 10 && maxX < 1.0 && worstUp > Math.cos(Math.toRadians(8)) && !lifted);

        // 9. a sharp turn at 90 km/h: the steering limit keeps it on its wheels
        VehiclePhysics h = new VehiclePhysics(spec);
        h.reset(0, 64.0, 0, 0);
        Driver hd = new Driver();
        for (int t = 0; t < 900 && h.forwardSpeed() < 25; t++) hd.tick(h, flat, 1, 0, 0, false);
        double up = 1;
        for (int t = 0; t < 40; t++) {
            hd.tick(h, flat, 1, 0, 1, false);
            up = Math.min(up, h.q.transform(new Vector3d(0, 1, 0)).y);
        }
        check("swerve at speed", String.format(Locale.ROOT, "%.0f km/h, worst tilt %.1f deg", h.forwardSpeed() * 3.6, Math.toDegrees(Math.acos(Math.min(1, up)))),
                up > Math.cos(Math.toRadians(15)));

        // 10. hill start on 15 degrees: brake held in 1st, then throttle only
        TestWorld hill = new TestWorld(64, 0, Math.tan(Math.toRadians(15)), 1.0);
        VehiclePhysics hs = new VehiclePhysics(spec);
        hs.reset(0, 64.02, 0, 0);
        Driver hsd = new Driver();
        for (int t = 0; t < 120; t++) hsd.tick(hs, hill, 0, 1, 0, false);
        double zStart = hs.origin(new Vector3d()).z, zLow = zStart;
        boolean stalled = false;
        for (int t = 0; t < 200; t++) {
            hsd.tick(hs, hill, 1, 0, 0, false);
            zLow = Math.min(zLow, hs.origin(new Vector3d()).z);
            stalled |= !hs.drivetrain.running;
        }
        check("hill start 15", String.format(Locale.ROOT, "rolled back %.2f m, %.1f m up in 10 s, gear %d, stalled %s", zStart - zLow,
                hs.origin(new Vector3d()).z - zStart, hs.drivetrain.gear, stalled), zStart - zLow < 0.4 && hs.origin(new Vector3d()).z - zStart > 10 && !stalled);

        // 11. 2H against 4H up 18 degrees of loose ground (grip 0.5): the rear wheels alone cannot, all four can
        for (boolean four : new boolean[]{false, true}) {
            TestWorld loose = new TestWorld(64, 0, Math.tan(Math.toRadians(18)), 0.5);
            VehiclePhysics l = new VehiclePhysics(spec);
            l.reset(0, 64.02, 0, 0);
            l.drivetrain.fourWheel = four;
            Driver ld = new Driver();
            for (int t = 0; t < 100; t++) ld.tick(l, loose, 0, 1, 0, false);
            double z1 = l.origin(new Vector3d()).z;
            for (int t = 0; t < 200; t++) ld.tick(l, loose, 1, 0, 0, false);
            double climbed = l.origin(new Vector3d()).z - z1;
            check(four ? "4H loose 18" : "2H loose 18", String.format(Locale.ROOT, "%.1f m in 10 s, %.0f rpm", climbed, l.drivetrain.rpm()), four ? climbed > 5 : climbed < 1);
        }

        // 11b. the transfer key: standing 2H 4H 4L 2H; rolling (10 km/h) 2H 4H 2H, 4L kept; at 30 km/h nothing
        {
            Drivetrain tk = new Drivetrain(spec);
            VehicleInput press = new VehicleInput(0, 0, 0, true, 0, true, false, true, true);
            StringBuilder seq = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                tk.controls(press, 0, 0);
                seq.append(tk.transferMode());
            }
            tk.controls(press, 10 / 3.6, 0);
            seq.append(' ').append(tk.transferMode());
            tk.controls(press, 10 / 3.6, 0);
            seq.append(tk.transferMode());
            tk.controls(press, 0, 0);
            tk.controls(press, 0, 0);
            seq.append(' ').append(tk.transferMode());
            tk.controls(press, 10 / 3.6, 0);
            seq.append(tk.transferMode());
            tk.controls(press, 0, 0);
            tk.controls(press, 30 / 3.6, 0);
            seq.append(' ').append(tk.transferMode());
            check("transfer key", seq + " (modes 0 2H, 1 4H, 2 4L)", seq.toString().equals("120 10 22 0"));
        }

        // 11c. 4H against 4L up 32 degrees of firm ground, a standing start: low range pulls up it without stalling
        for (boolean low : new boolean[]{false, true}) {
            TestWorld steep = new TestWorld(64, 0, Math.tan(Math.toRadians(32)), 1.0);
            VehiclePhysics l = new VehiclePhysics(spec);
            l.reset(0, 64.02, 0, 0);
            l.drivetrain.fourWheel = true;
            l.drivetrain.low = low;
            Driver ld = new Driver();
            for (int t = 0; t < 120; t++) ld.tick(l, steep, 0, 1, 0, false);
            double z1 = l.origin(new Vector3d()).z, zMin = z1;
            boolean stall = false;
            for (int t = 0; t < 200; t++) {
                ld.tick(l, steep, 1, 0, 0, false);
                zMin = Math.min(zMin, l.origin(new Vector3d()).z);
                stall |= !l.drivetrain.running;
            }
            double climbed = l.origin(new Vector3d()).z - z1;
            check(low ? "4L steep 32" : "4H steep 32", String.format(Locale.ROOT, "%.1f m in 10 s, rolled back %.2f m, gear %d, %.0f rpm, stalled %s",
                    climbed, z1 - zMin, l.drivetrain.gear, l.drivetrain.rpm(), stall), !low || climbed > 4 && !stall);
        }

        // 11d. 4L top speed on the flat (the cut-off in 5th, 2.28 times lower than in 4H: about 70 km/h)
        {
            VehiclePhysics l = new VehiclePhysics(spec);
            l.reset(0, 64.02, 0, 0);
            l.drivetrain.fourWheel = true;
            l.drivetrain.low = true;
            Driver ld = new Driver();
            for (int t = 0; t < 1200; t++) ld.tick(l, flat, 1, 0, 0, false);
            double kmh = l.forwardSpeed() * 3.6;
            check("4L top", String.format(Locale.ROOT, "%.0f km/h in gear %d", kmh, l.drivetrain.gear), kmh > 60 && kmh < 80);
        }

        // 11e. the transfer lever: 2H -> 4H -> 4L -> 2H at 60 fps; each move gets there, never overshoots, stops in N on the
        // way in or out of low, the hand back on the wheel after each
        {
            TransferLever tl = new TransferLever();
            double now = 0;
            tl.update(now, 0);
            int[] modes = {1, 2, 0};
            boolean ok = true;
            StringBuilder out = new StringBuilder();
            for (int m : modes) {
                double from = tl.fore, to = TransferLever.tilt(m), lo = Math.min(from, to) - 1e-6, hi = Math.max(from, to) + 1e-6;
                double start = now, settled = Double.NaN, maxRate = 0, prev = tl.fore, atN = 0;
                for (int f = 0; f < 90; f++) {
                    now += 1 / 60.0;
                    tl.update(now, m);
                    ok &= tl.fore >= lo && tl.fore <= hi;
                    maxRate = Math.max(maxRate, Math.abs(tl.fore - prev) * 60);
                    if (Math.abs(tl.fore - TransferLever.NEUTRAL) < 1e-9) atN += 1 / 60.0;
                    prev = tl.fore;
                    if (Double.isNaN(settled) && Math.abs(tl.fore - to) < 1e-9) settled = now - start;
                }
                ok &= Math.abs(tl.fore - to) < 1e-9 && tl.hand == 0 && !tl.handGoing;
                ok &= (m == 2 || from == TransferLever.tilt(2)) == (atN > 0.05);
                out.append(String.format(Locale.ROOT, "%.0f->%.0f deg in %.2f s (max %.0f deg/s, %.2f s in N); ", from, to, settled, maxRate, atN));
            }
            check("transfer lever", out.toString(), ok);
        }

        // 12. fuel: the tank runs dry while idling (it stops); cranking without fuel never fires
        VehiclePhysics nf = new VehiclePhysics(spec);
        nf.reset(0, 64, 0, 0);
        Driver nd = new Driver();
        for (int t = 0; t < 100; t++) nd.tick(nf, flat, 0, 0, 0, true);
        boolean ran = nf.drivetrain.running;
        double flowIdle = nf.drivetrain.fuelFlow * 3600;
        nd.fuel = false;
        nf.tick(new VehicleInput(0, 0, 0, true, 0, false, false, false, true), flat);
        boolean stopped = !nf.drivetrain.running;
        for (int t = 0; t < 100; t++) nf.tick(new VehicleInput(0, 0, 0, true, 0, false, true, false, true), flat);
        check("fuel", String.format(Locale.ROOT, "ran %s at %.2f L/h idling, stopped when dry %s, fired again without fuel %s", ran, flowIdle, stopped,
                nf.drivetrain.running), ran && stopped && !nf.drivetrain.running && flowIdle > 0.4 && flowIdle < 1.5);

        System.out.println(failures == 0 ? "vehicleCheck: all PASS" : "vehicleCheck: " + failures + " FAIL");
        if (failures > 0) System.exit(1);
    }

    private static void check(String name, String numbers, boolean pass) {
        if (!pass) failures++;
        System.out.println((pass ? "PASS  " : "FAIL  ") + name + ": " + numbers);
    }

    private static double maxAbs(double[] a) {
        double m = 0;
        for (double x : a) m = Math.max(m, Math.abs(x));
        return m;
    }

    private static String fmt(double[] a) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%.4f", a[i]));
        return b.append(']').toString();
    }
}
