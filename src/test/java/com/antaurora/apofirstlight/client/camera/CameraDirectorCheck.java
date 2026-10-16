package com.antaurora.apofirstlight.client.camera;

import com.antaurora.apofirstlight.client.CrowbarFirstPerson;
import com.antaurora.apofirstlight.temperature.ClientShiver;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera director V1 offline check (docs/dev/camera_director_v1_plan.md 3, D1-D3): the fade and blend math, the priority
 * stack, the field of view steps, the input lock and HUD unions, and that the shake sources moved over (D2) give what
 * their old ComputeCameraAngles handlers put on the view, frame for frame. The old handlers' formulas are copied here as
 * they were before the move. Run: ./gradlew cameraCheck
 */
public final class CameraDirectorCheck {
    private static int failures, checks;

    private CameraDirectorCheck() {
    }

    public static void main(String[] args) {
        fades();
        blends();
        stack();
        locksAndHud();
        shakes();
        System.out.println("\ncamera director checks: " + checks + " run, " + failures + (failures == 0 ? " failed: PASS" : " FAILED"));
        if (failures > 0) System.exit(1);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    // ---------------------------------------------------------------- fades (D4 / D5 will move these views over)

    private static void fades() {
        // the maintenance view (MaintenanceModeClientState#blend: in 0.22 s, out 0.16 s from wherever it stood)
        for (double exitAt : new double[]{0.05, 0.11, 0.3}) {
            CameraFade f = new CameraFade(0.22, 0.16);
            f.open(0);
            double exitWeight = Double.NaN;
            int bad = 0;
            for (int i = 0; i <= 120; i++) {
                double t = i * 0.005;
                if (t >= exitAt && Double.isNaN(exitWeight)) {
                    exitWeight = maintenanceIn(exitAt);
                    f.close(exitAt);
                }
                double old = Double.isNaN(exitWeight) ? maintenanceIn(t) : exitWeight * (1 - CameraFade.smooth((t - exitAt) / 0.16));
                if (!near(f.weight(t), old, 1e-12)) bad++;
            }
            check(bad == 0, "maintenance fade, exit at " + exitAt + " s: " + bad + " samples differ");
        }
        // the recoil pull's bent view (PortableGeneratorPull#blend: in 10 ticks, out 8 ticks, min(in, 1 - out))
        for (double leaveAt : new double[]{4, 10, 25}) {
            CameraFade f = new CameraFade(10 / 20.0, 8 / 20.0);
            f.open(0);
            boolean left = false;
            double worst = 0;
            for (int i = 0; i <= 400; i++) {
                double tick = i * 0.1;
                if (tick >= leaveAt && !left) {
                    f.close(leaveAt / 20.0);
                    left = true;
                }
                double in = CameraFade.smooth(tick / 10), old = left ? Math.min(in, 1 - CameraFade.smooth((tick - leaveAt) / 8)) : in;
                worst = Math.max(worst, Math.abs(f.weight(tick / 20.0) - old));
            }
            if (leaveAt >= 10) check(worst < 1e-12, "recoil pull fade, let go at tick " + leaveAt + ": differs by " + worst);
            else System.out.printf("note: recoil pull let go mid fade-in (tick %.0f): the director's fade differs by %.3f (eases out from where it stood; the old view used min(in, out)) - decide when moving it over (D4)%n", leaveAt, worst);
        }
        CameraFade never = new CameraFade(0.2, 0.2);
        check(never.weight(5) == 0 && never.finished(5), "an unopened fade weighs nothing and is finished");
        CameraFade cut = new CameraFade(0, 0);
        cut.open(1);
        check(cut.weight(1) == 1, "a zero-length fade is in at once");
        cut.close(2);
        check(cut.weight(2) == 0 && cut.finished(2), "a zero-length fade is out at once");
    }

    private static double maintenanceIn(double t) {
        double x = Math.min(1, t / 0.22);
        return x * x * (3 - 2 * x);
    }

    // ---------------------------------------------------------------- blends

    private static void blends() {
        CameraPose a = new CameraPose(new Vec3(0, 64, 0), 170F, -10F, 0F, Double.NaN);
        CameraPose b = new CameraPose(new Vec3(2, 66, -4), -170F, 30F, 4F, 50.0);
        CameraPose half = CameraPose.blend(a, b, 0.5F);
        check(near(half.position().x, 1, 1e-9) && near(half.position().y, 65, 1e-9) && near(half.position().z, -2, 1e-9), "position halfway");
        check(near(((half.yaw() % 360) + 360) % 360, 180, 1e-3), "yaw goes the short way (170 -> -170 halfway = 180, got " + half.yaw() + ")");
        check(near(half.pitch(), 10, 1e-5) && near(half.roll(), 2, 1e-5), "pitch and roll halfway");
        CameraPose none = CameraPose.blend(a, b, 0F), all = CameraPose.blend(a, b, 1F);
        check(none.position().equals(a.position()) && none.yaw() == a.yaw() && none.pitch() == a.pitch(), "weight 0 keeps the pose");
        check(all == b, "weight 1 is the shot");
        check(near(CameraDirector.fov(70, List.of(new double[]{50, 0.5})), 60, 1e-9), "field of view eased halfway");
        check(near(CameraDirector.fov(70, List.of(new double[]{50, 1}, new double[]{90, 0.5})), 70, 1e-9), "field of view steps in order");
        check(CameraDirector.fov(70, List.of()) == 70, "no steps keep the game's field of view");
    }

    // ---------------------------------------------------------------- the stack

    private static CameraDirector.Request request(String id, int priority, CameraPose pose) {
        return new CameraDirector.Request(id, priority, (camera, current, partial) -> pose, CameraDirector.Clock.REAL, 0, 0);
    }

    private static void stack() {
        CameraDirector.requests().clear();
        CameraPose base = new CameraPose(new Vec3(0, 64, 0), 0F, 0F);
        CameraPose low = new CameraPose(new Vec3(1, 64, 0), 10F, 10F), high = new CameraPose(new Vec3(5, 70, 5), 90F, 45F, 0F, 40.0);
        CameraDirector.open(request("high", CameraDirector.PRIORITY_BODY_EYE, high));
        CameraDirector.open(request("low", CameraDirector.PRIORITY_MACHINE, low));
        List<CameraDirector.Request> rs = CameraDirector.requests();
        check(rs.size() == 2 && rs.get(0).id.equals("low") && rs.get(1).id.equals("high"), "requests kept lowest priority first");
        List<double[]> fov = new ArrayList<>();
        CameraPose out = CameraDirector.compose(rs, null, base, 0F, fov);
        check(out == high, "the higher priority wins at full weight (as the body eye over the bent pull view today)");
        check(fov.size() == 1 && fov.get(0)[0] == 40.0, "the shot's field of view is a step");
        CameraDirector.open(request("low", CameraDirector.PRIORITY_MACHINE, high));
        check(CameraDirector.requests().size() == 2, "the same id replaces its request");
        // a shot that gives no pose adds nothing
        CameraDirector.requests().clear();
        CameraDirector.open(new CameraDirector.Request("none", 100, (c, p, t) -> null, CameraDirector.Clock.REAL, 0, 0));
        check(CameraDirector.compose(CameraDirector.requests(), null, base, 0F, new ArrayList<>()) == null, "no pose, no placement");
        // the shot sees the pose so far (follow shots build on it)
        CameraDirector.requests().clear();
        CameraDirector.open(request("first", 100, low));
        CameraDirector.open(new CameraDirector.Request("follow", 200,
                (c, p, t) -> new CameraPose(p.position().add(0, 1, 0), p.yaw(), p.pitch()), CameraDirector.Clock.REAL, 0, 0));
        CameraPose f = CameraDirector.compose(CameraDirector.requests(), null, base, 0F, new ArrayList<>());
        check(f != null && f.position().equals(low.position().add(0, 1, 0)), "a shot builds on the pose below it");
        CameraDirector.requests().clear();
    }

    private static void locksAndHud() {
        CameraDirector.requests().clear();
        CameraPose p = new CameraPose(Vec3.ZERO, 0F, 0F);
        check(!CameraDirector.lookLocked() && CameraDirector.hud() == CameraDirector.Hud.NORMAL, "nothing open: no lock, normal HUD");
        CameraDirector.open(request("a", 200, p).locks(CameraDirector.Lock.MOVE, CameraDirector.Lock.LOOK).hud(CameraDirector.Hud.NO_HANDS));
        CameraDirector.open(new CameraDirector.Request("b", 400, (c, x, t) -> p, CameraDirector.Clock.REAL, 0, 10).hud(CameraDirector.Hud.NO_HUD));
        check(CameraDirector.lookLocked() && CameraDirector.locked(CameraDirector.Lock.MOVE) && !CameraDirector.locked(CameraDirector.Lock.USE), "locks are the union");
        check(CameraDirector.hud() == CameraDirector.Hud.NO_HUD, "the strictest HUD wins");
        CameraDirector.close("a");
        check(!CameraDirector.lookLocked(), "a closing request releases its locks at once");
        CameraDirector.close("b");
        check(CameraDirector.isOpen("b") && CameraDirector.hud() == CameraDirector.Hud.NO_HUD, "the HUD stays hidden while it eases out");
        CameraDirector.requests().clear();
    }

    // ---------------------------------------------------------------- D2: the shake sources, frame for frame

    private static void shakes() {
        // crowbar (CrowbarFirstPerson#camera before the move): kick on pitch * 0.8, roll * 0.25, 3 ticks after the impact
        int bad = 0;
        for (int i = -40; i <= 160; i++) {
            double age = i * 0.025;
            float oldPitch = 0, oldRoll = 0;
            if (age >= 0 && age < 3) {
                float kick = (float) (Math.sin(age / 3 * Math.PI) * (1 - age / 3));
                oldPitch = 0 + kick * .8f;
                oldRoll = 0 + kick * .25f;
            }
            float kick = CrowbarFirstPerson.kick(age);
            CameraShake out = new CameraShake();
            if (kick != 0) out.add(kick * .8f, 0, kick * .25f);
            if ((float) out.pitch != oldPitch || (float) out.roll != oldRoll || out.yaw != 0) bad++;
        }
        check(bad == 0, "crowbar knock: " + bad + " of 201 frames differ");
        // shivering (ClientShiver#camera before the move): yaw, pitch, roll each a float term
        bad = 0;
        double[][] bursts = {{0.35, 0.4, 3.1}, {0.45, 1.0, 7.9}, {0.4, 0.7, 0.0}};
        for (double[] b : bursts) {
            for (int i = 0; i < 90; i++) {
                double t = i * 0.005;
                if (t >= b[0]) break;
                double a = Math.sin(Math.PI * t / b[0]) * b[1], f = (t + b[2]) * 18 * Math.PI * 2;
                float oldYaw = 0 + (float) (a * 0.12 * Math.sin(f + 0.3));
                float oldPitch = 0 + (float) (a * 0.09 * Math.sin(f * 1.31 + 1.7));
                float oldRoll = 0 + (float) (a * 0.04 * Math.sin(f * 0.83 + 2.9));
                double[] d = ClientShiver.angles(t, b[0], b[1], b[2]);
                if ((float) d[0] != oldPitch || (float) d[1] != oldYaw || (float) d[2] != oldRoll) bad++;
            }
        }
        check(bad == 0, "shivering: " + bad + " frames differ");
        // one source alone: the director's single add equals the old handler's own add on the view
        bad = 0;
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 2000; i++) {
            float view = (float) (r.nextGaussian() * 60);
            double d = r.nextGaussian() * 3, k = r.nextDouble();
            float old = (float) (view + d * k);
            CameraShake out = new CameraShake();
            out.add(d * k, 0, 0);
            if ((float) (view + out.pitch * 1F) != old) bad++;
        }
        check(bad == 0, "a single source adds exactly as its old handler: " + bad + " of 2000 differ");
        // several sources in one frame: the old handlers added one after another in float, the director sums first
        double worst = 0;
        for (int i = 0; i < 2000; i++) {
            float view = (float) (r.nextGaussian() * 60), old = view;
            CameraShake out = new CameraShake();
            for (int s = 0; s < 3; s++) {
                double d = r.nextGaussian() * 3;
                old = (float) (old + d);
                out.add(d, 0, 0);
            }
            worst = Math.max(worst, Math.abs((float) (view + out.pitch) - old));
        }
        check(worst < 1e-4, "several sources at once differ by at most " + worst + " deg (float rounding only)");
        System.out.printf("note: the recoil pull's shake (PortableGeneratorPull#shake), the jerry can's and the guns' camera bones are the same code, only their result goes through the shake layer (single-source check above)%n");
    }
}
