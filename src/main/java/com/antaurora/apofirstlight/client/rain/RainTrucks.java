package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Box;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.RainPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The trucks the rain stops on (docs/rendering/rain_v1.md): each one's frame at a moment and its rain shell
 * (PickupTruckLayout#RAIN_SHELL plus the tailgate shut or down). The drop program gets the same parts and runs the same
 * test (afl_rain.vsh afl_truckClip); the splashes land on it and are kept out of it here.
 */
final class RainTrucks {
    /** A truck at one moment: origin (world), forward, right, up (unit), its parts. */
    static final class Frame {
        final PickupTruckEntity truck;
        final Vec3 o, f, r, u;
        final List<RainPart> parts;

        Frame(PickupTruckEntity truck, float pt) {
            this.truck = truck;
            o = truck.framePoint(0, 0, 0, pt);
            f = truck.frameDirection(1, 0, 0, pt);
            r = truck.frameDirection(0, 1, 0, pt);
            u = truck.frameDirection(0, 0, 1, pt);
            parts = new ArrayList<>(PickupTruckLayout.RAIN_SHELL);
            parts.add(new RainPart(truck.tailgateOpen() ? PickupTruckLayout.TAILGATE_DOWN : PickupTruckLayout.TAILGATE_SHUT, 0, 0, 1));
        }

        /** A world point in the truck's frame: forward, right, up. */
        double[] local(double x, double y, double z) {
            double dx = x - o.x, dy = y - o.y, dz = z - o.z;
            return new double[]{dx * f.x + dy * f.y + dz * f.z, dx * r.x + dy * r.y + dz * r.z, dx * u.x + dy * u.y + dz * u.z};
        }

        /** A world direction in the truck's frame. */
        double[] localDir(double x, double y, double z) {
            return new double[]{x * f.x + y * f.y + z * f.z, x * r.x + y * r.y + z * r.z, x * u.x + y * u.y + z * u.z};
        }

        /** Whether a world point is inside the shell. */
        boolean inside(double x, double y, double z) {
            double[] p = local(x, y, z);
            for (RainPart part : parts) {
                Box b = part.box();
                if (p[0] >= b.f0() && p[0] <= b.f1() && p[1] >= b.r0() && p[1] <= b.r1() && p[2] >= b.y0() && p[2] <= b.y1()
                        && part.nf() * p[0] + part.ny() * p[2] <= part.d()) return true;
            }
            return false;
        }
    }

    private RainTrucks() {
    }

    /** The trucks within range of a point, nearest first, at most max, at this partial tick. */
    static List<Frame> near(ClientLevel level, Vec3 at, double range, int max, float pt) {
        List<PickupTruckEntity> near = new ArrayList<>();
        for (Entity e : level.entitiesForRendering())
            if (e instanceof PickupTruckEntity truck && !truck.isRemoved() && truck.distanceToSqr(at) < range * range) near.add(truck);
        if (near.isEmpty()) return List.of();
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        List<Frame> out = new ArrayList<>(Math.min(max, near.size()));
        for (PickupTruckEntity truck : near) {
            if (out.size() >= max) break;
            out.add(new Frame(truck, pt));
        }
        return out;
    }

    /**
     * Where a drop falling straight down from (x, fromY, z) first meets a shell part: the parameter along the fall from
     * the start (metres), or NaN. Ray against the box cut by its plane (as afl_truckClip).
     */
    static double fall(Frame t, double x, double fromY, double z, double maxFall) {
        double[] a = t.local(x, fromY, z), d = t.localDir(0, -1, 0);
        double best = Double.NaN;
        for (RainPart part : t.parts) {
            double hit = hit(part, a, d, maxFall);
            if (hit >= 0 && !(hit >= best)) best = hit;
        }
        return best;
    }

    /** Entry distance of a ray (local frame, unit direction) into a part within max, or -1 (also when it starts inside). */
    static double hit(RainPart part, double[] a, double[] d, double max) {
        Box b = part.box();
        double[] lo = {b.f0(), b.r0(), b.y0()}, hi = {b.f1(), b.r1(), b.y1()};
        double enter = -Double.MAX_VALUE, leave = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (a[i] < lo[i] || a[i] > hi[i]) return -1;
                continue;
            }
            double t0 = (lo[i] - a[i]) / d[i], t1 = (hi[i] - a[i]) / d[i];
            enter = Math.max(enter, Math.min(t0, t1));
            leave = Math.min(leave, Math.max(t0, t1));
        }
        // the cut: nf * f + ny * y <= d
        double s = part.nf() * a[0] + part.ny() * a[2] - part.d(), ds = part.nf() * d[0] + part.ny() * d[2];
        if (Math.abs(ds) < 1e-9) {
            if (s > 0) return -1;
        } else if (ds < 0) {
            enter = Math.max(enter, -s / ds);
        } else {
            leave = Math.min(leave, -s / ds);
        }
        if (leave < enter || leave < 0 || enter > max || enter <= 0) return -1;
        return enter;
    }
}
