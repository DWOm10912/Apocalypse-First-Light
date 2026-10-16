package com.antaurora.apofirstlight.vehicle;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The T15's own frame data for collision, seats and the clickable parts (docs/vehicles/first_pickup_v0_design.md section
 * 8.2), taken from the body's dimensions (tools/pickup-t15/common.mjs) and the rig anchors
 * (src/main/blockbench/vehicles/pickup_t15.rig.json). Metres: forward along the heading, right to the truck's right, up
 * from the ground under the entity position (the cab's middle, the model origin).
 */
public final class PickupTruckLayout {
    /** A box in the truck's frame. */
    public record Box(double f0, double f1, double r0, double r1, double y0, double y1) {
        Box mirror() { return new Box(f0, f1, -r1, -r0, y0, y1); }

        /** Ray entry distance (along a unit direction, local frame) or -1. */
        double hit(double[] o, double[] d, double max) {
            double t0 = 0, t1 = max;
            double[] lo = {f0, r0, y0}, hi = {f1, r1, y1};
            for (int i = 0; i < 3; i++) {
                if (Math.abs(d[i]) < 1e-9) {
                    if (o[i] < lo[i] || o[i] > hi[i]) return -1;
                    continue;
                }
                double a = (lo[i] - o[i]) / d[i], b = (hi[i] - o[i]) / d[i];
                if (a > b) { double t = a; a = b; b = t; }
                t0 = Math.max(t0, a);
                t1 = Math.min(t1, b);
                if (t0 > t1) return -1;
            }
            return t0;
        }
    }

    // ---- collision (parked) ----

    private static final double HW = 0.845;
    /** Always there: front, cab, bed floor, walls, wheel housings, bumpers. */
    public static final List<Box> BODY;
    /** The tailgate shut (a wall) and down (a flat plate at the hinge). */
    public static final Box TAILGATE_SHUT = new Box(-2.255, -2.205, -0.757, 0.757, 0.76, 1.11),
            TAILGATE_DOWN = new Box(-2.75, -2.255, -0.757, 0.757, 0.57, 0.64);

    static {
        Box wall = new Box(-2.255, -0.36, 0.757, HW, 0.76, 1.11), housing = new Box(-1.74, -0.76, 0.56, 0.757, 0.76, 0.89);
        BODY = List.of(
                new Box(2.275, 2.40, -0.86, 0.86, 0.0, 0.62),      // front bumper
                new Box(1.65, 2.275, -HW, HW, 0.0, 1.03),          // nose (hood front half)
                new Box(0.95, 1.65, -HW, HW, 0.0, 1.07),           // cowl (hood rear half)
                new Box(-0.30, 0.95, -HW, HW, 0.0, 1.07),          // cab below the belt
                new Box(0.55, 0.80, -0.80, 0.80, 1.07, 1.45),      // windshield (stepped)
                new Box(-0.30, 0.55, -0.80, 0.80, 1.07, 1.745),    // cab top to the roof
                new Box(-2.255, -0.36, -HW, HW, 0.0, 0.76),        // bed floor
                new Box(-0.40, -0.30, -HW, HW, 0.76, 1.11),        // bed front wall (and the gap to the cab)
                wall, wall.mirror(), housing, housing.mirror(),
                new Box(-2.37, -2.255, -0.88, 0.88, 0.0, 0.58));   // rear step bumper
    }

    // ---- seats ----

    /**
     * A seat: where the rider sits (forward, right, the hips' height), which way the body faces (degrees from the
     * heading, + to the right) and how far the head turns each side.
     */
    public record Seat(double f, double r, double hip, float yaw, float look, boolean cab, boolean right) {}

    /**
     * Driver, passenger (hips pressed into the 0.845 cushion), then four on the bed's wheel housings (hips on the 0.89
     * top, facing in).
     */
    public static final Seat[] SEATS = {
            new Seat(-0.06, -0.37, 0.73, 0, 120, true, false),
            new Seat(-0.06, 0.37, 0.73, 0, 120, true, true),
            new Seat(-1.00, -0.63, 0.91, 90, 105, false, false),
            new Seat(-1.50, -0.63, 0.91, 90, 105, false, false),
            new Seat(-1.00, 0.63, 0.91, -90, 105, false, true),
            new Seat(-1.50, 0.63, 0.91, -90, 105, false, true)};

    /** Vanilla's riding player: eyes 1.62 above its position, hips 0.703, so the eyes are 0.917 above the hips. */
    public static final double EYE_OVER_HIP = 1.62 - 0.703;
    /**
     * Interim (until D's seated pose, docs/vehicles/first_pickup_v0_design.md 8.2): the vanilla model is drawn this much
     * smaller around its eyes (client/vehicle/VehicleRiderScale), so the head stays at the camera and the hips come down
     * by EYE_OVER_HIP * (1 - scale). Tunable with /afl dev vehicle seat (shared by the integrated server and client).
     */
    public static volatile float riderScale = 0.75f;
    /** Dev override of the cab seats' hip height (NaN: the seat's own). */
    public static volatile double cabHip = Double.NaN;

    /** The rider's position height for a seat: drawn scaled around the eyes, the hips land on the seat's. */
    public static double riderY(Seat s, double eyeHeight) {
        double hip = s.cab() && !Double.isNaN(cabHip) ? cabHip : s.hip();
        return hip + EYE_OVER_HIP * riderScale - eyeHeight;
    }
    public static final int DRIVER = 0, PASSENGER = 1;
    /** Where one gets out of a cab door (the rig's door_*_entry). */
    public static final double EXIT_F = 0.30, EXIT_R = 1.45;

    // ---- clickable parts ----

    public enum Zone { HOOD, DOOR_L, DOOR_R, FILLER, TAILGATE, SEAT, BED }

    /** A part under the eyes: which, how far along the look (blocks), and the seat (SEAT only, else -1). */
    public record Pick(Zone zone, double distance, int seat) {}

    private static final Box HOOD = new Box(0.95, 2.42, -0.88, 0.88, 0.45, 1.12), HOOD_UP = new Box(1.05, 1.70, -0.80, 0.80, 1.12, 1.80);
    private static final Box DOOR = new Box(-0.19, 0.95, -0.90, -0.74, 0.46, 1.70), DOOR_SWUNG = new Box(0.42, 0.96, -1.95, -0.80, 0.46, 1.70);
    public static final Box FILLER = new Box(-0.72, -0.52, -0.90, -0.80, 0.85, 1.03);
    private static final Box TAILGATE = new Box(-2.42, -2.18, -0.76, 0.76, 0.58, 1.13), TAILGATE_FLAT = new Box(-2.78, -2.18, -0.76, 0.76, 0.52, 0.70);
    private static final Box BED = new Box(-2.22, -0.40, -0.76, 0.76, 0.74, 1.16);
    /** The fill neck behind the filler door (where the stream and the nozzle go). */
    public static final double[] FILL_NECK = {-0.62, -0.83, 0.94};

    /** A seat's click box on the wheel housing. */
    private static Box seatBox(Seat s) {
        double r0 = Math.abs(s.r()) - 0.10, r1 = Math.abs(s.r()) + 0.14;
        return s.r() < 0 ? new Box(s.f() - 0.24, s.f() + 0.24, -r1, -r0, 0.74, 0.97) : new Box(s.f() - 0.24, s.f() + 0.24, r0, r1, 0.74, 0.97);
    }

    /**
     * The part a look ray meets first (local frame: origin and unit direction as forward / right / up), or null. The bed's
     * open space counts only when nothing else is hit; a seat inside it counts even behind the bed's own surface.
     */
    @Nullable
    public static Pick pick(double[] o, double[] d, double max, boolean hoodOpen, boolean doorL, boolean doorR, boolean tailgateDown) {
        Pick best = null;
        best = nearer(best, Zone.HOOD, HOOD.hit(o, d, max), -1);
        if (hoodOpen) best = nearer(best, Zone.HOOD, HOOD_UP.hit(o, d, max), -1);
        best = nearer(best, Zone.DOOR_L, DOOR.hit(o, d, max), -1);
        best = nearer(best, Zone.DOOR_R, DOOR.mirror().hit(o, d, max), -1);
        if (doorL) best = nearer(best, Zone.DOOR_L, DOOR_SWUNG.hit(o, d, max), -1);
        if (doorR) best = nearer(best, Zone.DOOR_R, DOOR_SWUNG.mirror().hit(o, d, max), -1);
        best = nearer(best, Zone.FILLER, FILLER.hit(o, d, max), -1);
        best = nearer(best, Zone.TAILGATE, (tailgateDown ? TAILGATE_FLAT : TAILGATE).hit(o, d, max), -1);
        for (int i = 0; i < SEATS.length; i++) if (!SEATS[i].cab()) best = nearer(best, Zone.SEAT, seatBox(SEATS[i]).hit(o, d, max), i);
        if (best == null) {
            double t = BED.hit(o, d, max);
            if (t >= 0) best = new Pick(Zone.BED, t, -1);
        }
        return best;
    }

    private static Pick nearer(@Nullable Pick best, Zone zone, double t, int seat) {
        return t >= 0 && (best == null || t < best.distance()) ? new Pick(zone, t, seat) : best;
    }

    /** A point in the truck frame to the world. */
    public static Vec3 world(Vec3 origin, float yaw, double f, double r, double y) {
        double a = Math.toRadians(yaw), s = Math.sin(a), c = Math.cos(a);
        return new Vec3(origin.x - s * f - c * r, origin.y + y, origin.z + c * f - s * r);
    }

    /** A world point to the truck frame {forward, right, up}. */
    public static double[] local(Vec3 origin, float yaw, Vec3 p) {
        double a = Math.toRadians(yaw), s = Math.sin(a), c = Math.cos(a), dx = p.x - origin.x, dz = p.z - origin.z;
        return new double[]{-s * dx + c * dz, -c * dx - s * dz, p.y - origin.y};
    }

    /** A world direction to the truck frame. */
    public static double[] localDir(float yaw, Vec3 v) {
        double a = Math.toRadians(yaw), s = Math.sin(a), c = Math.cos(a);
        return new double[]{-s * v.x + c * v.z, -c * v.x - s * v.z, v.y};
    }

    private PickupTruckLayout() {
    }
}
