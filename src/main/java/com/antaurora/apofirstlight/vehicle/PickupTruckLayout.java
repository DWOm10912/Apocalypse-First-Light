package com.antaurora.apofirstlight.vehicle;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
                new Box(0.55, 0.80, -0.80, 0.80, 1.07, 1.50),      // windshield (stepped)
                new Box(-0.30, 0.55, -0.80, 0.80, 1.07, 1.845),    // cab top to the roof (raised 10 cm 2026-10-16)
                new Box(-2.255, -0.36, -HW, HW, 0.0, 0.76),        // bed floor
                new Box(-0.40, -0.30, -HW, HW, 0.76, 1.11),        // bed front wall (and the gap to the cab)
                wall, wall.mirror(), housing, housing.mirror(),
                new Box(-2.37, -2.255, -0.88, 0.88, 0.0, 0.58));   // rear step bumper
    }

    // ---- rain shell (client/rain) ----

    /**
     * A part of the shell the rain stops on: a box, kept to where nf * f + ny * y <= d (a slanted face cut across it; no
     * cut: 0, 0, 1).
     */
    public record RainPart(Box box, double nf, double ny, double d) {}

    /**
     * What the rain stops on (docs/rendering/rain_v1.md): the body's outside as drawn, not the collision steps. The cab
     * above the belt is cut along the windshield's glass plane (common.mjs wsX: x 0.955 at the belt, 0.42 at the roof
     * header) and the hood along its top line (hy); the collision's windshield step left the lower glass open and a
     * strip of dashboard under it, where splashes and streaks showed inside the cab (user in game 2026-10-17). The
     * tailgate (shut or down) comes after these.
     */
    public static final List<RainPart> RAIN_SHELL;

    static {
        double glass = (0.42 - 0.955) / (1.845 - 0.045 - 1.07);   // windshield x per metre of height (negative)
        double hood = (1.005 - 1.077) / (2.25 - 1.08);            // hood top y per metre forward (negative)
        Box wall = new Box(-2.255, -0.36, 0.757, HW, 0.76, 1.11), housing = new Box(-1.74, -0.76, 0.56, 0.757, 0.76, 0.89);
        RAIN_SHELL = List.of(
                new RainPart(new Box(2.275, 2.40, -0.86, 0.86, 0.0, 0.62), 0, 0, 1),                    // front bumper
                new RainPart(new Box(0.955, 2.275, -HW, HW, 0.0, 1.09), -hood, 1, 1.077 - hood * 1.08 + 0.005),   // hood
                new RainPart(new Box(-0.30, 0.955, -HW, HW, 0.0, 1.07), 0, 0, 1),                      // cab below the belt
                new RainPart(new Box(-0.30, 0.955, -0.80, 0.80, 1.07, 1.845), 1, -glass, 0.955 - glass * 1.07 + 0.008),   // cab, glass
                new RainPart(new Box(-2.255, -0.36, -HW, HW, 0.0, 0.76), 0, 0, 1),                     // bed floor
                new RainPart(new Box(-0.40, -0.30, -HW, HW, 0.76, 1.11), 0, 0, 1),                     // bed front wall
                new RainPart(wall, 0, 0, 1), new RainPart(wall.mirror(), 0, 0, 1),
                new RainPart(housing, 0, 0, 1), new RainPart(housing.mirror(), 0, 0, 1),
                new RainPart(new Box(-2.37, -2.255, -0.88, 0.88, 0.0, 0.58), 0, 0, 1));               // rear step bumper
    }

    // ---- seats ----

    /**
     * A seat: where the rider sits (forward, right, the cushion's top under the middle of the hips), which way the body
     * faces (degrees from the heading, + to the right) and how far the head turns each side.
     */
    public record Seat(double f, double r, double cushion, float yaw, float look, boolean cab, boolean right) {}

    /** Cushion tops and floors: cab (bench seat, lowered 3 cm 2026-10-16; floor pan), bed (wheel housing tops, bed floor). */
    public static final double CAB_CUSHION = 0.815, CAB_FLOOR = 0.535, BED_CUSHION = 0.89, BED_FLOOR = 0.772;

    /** Driver, passenger (on the cab's bench), then four on the bed's wheel housings (facing in). */
    public static final Seat[] SEATS = {
            new Seat(-0.06, -0.37, CAB_CUSHION, 0, 120, true, false),
            new Seat(-0.06, 0.37, CAB_CUSHION, 0, 120, true, true),
            new Seat(-1.00, -0.63, BED_CUSHION, 90, 105, false, false),
            new Seat(-1.50, -0.63, BED_CUSHION, 90, 105, false, false),
            new Seat(-1.00, 0.63, BED_CUSHION, -90, 105, false, true),
            new Seat(-1.50, 0.63, BED_CUSHION, -90, 105, false, true)};

    /** Vanilla's riding player: eyes 1.62 above its position, hips 0.703, so the eyes are 0.917 above the hips. */
    public static final double EYE_OVER_HIP = 1.62 - 0.703;
    public static final int DRIVER = 0, PASSENGER = 1;

    // ---- the rider's points (model frame: x the truck's right, y up, z back; tools/pickup-t15/common.mjs) ----

    /** The steering wheel: hub, column axis (into the panel; PickupTruckRenderer turns the wheel about it), rim radius. */
    public static final double WHEEL_X = -0.37, WHEEL_Y = 1.125, WHEEL_Z = -0.40, COLUMN_Y = -0.45399, COLUMN_Z = -0.89101, RIM = 0.19;
    /** Steering wheel turns per road wheel turn: 540 deg each way (1.5 turns) at the road wheels' lock. */
    public static final double STEERING_RATIO = 540.0 / Math.toDegrees(com.antaurora.apofirstlight.vehicle.physics.VehicleSpec.T15.maxSteer);
    /** Door hinge (the left door; the right mirrors), the inside pull on the armrest, the outside handle, where one stands. */
    public static final double HINGE_X = 0.825, HINGE_Z = -0.916;
    public static final double[] PULL = {0.76, 0.95, -0.12}, OUTER = {0.85, 0.98, 0.057}, ENTRY = {1.45, 0, -0.30};

    /**
     * Where a hand is on the steering wheel (model frame): {@code angle} rad round the rim from its rest at 9 (left) or
     * 3 o'clock (right), + clockwise as the driver sees it (SteeringHands), {@code lift} m off it toward the driver.
     */
    public static double[] grip(boolean right, double angle, double lift) {
        double c = Math.cos(angle), s = Math.sin(angle), v = right ? RIM : -RIM;
        // (v, 0, 0) about the unit column axis (0, COLUMN_Y, COLUMN_Z), at right angles to it; lifted back along it
        return new double[]{WHEEL_X + v * c, WHEEL_Y + COLUMN_Z * v * s - COLUMN_Y * lift, WHEEL_Z - COLUMN_Y * v * s - COLUMN_Z * lift};
    }

    /** The wheel's hub and a point 0.1 m along the column axis from it (the seated pose's hands turn about it). */
    public static final double[] HUB = {WHEEL_X, WHEEL_Y, WHEEL_Z}, AXIS = {WHEEL_X, WHEEL_Y + 0.1 * COLUMN_Y, WHEEL_Z + 0.1 * COLUMN_Z};

    /**
     * The gear lever (tools/pickup-t15 PIVOTS.shift, the knob anchor shift_knob; model frame): it tilts about the top of
     * its boot; the knob's middle with the lever upright (neutral).
     */
    public static final double[] SHIFT_PIVOT = {-0.02, 0.725, -0.43}, SHIFT_KNOB = {-0.055, 1.02, -0.33};
    /** The hand on the knob: the palm's point this far over the knob's middle (m); its way over from the rim arcs up this much. */
    public static final double KNOB_PALM = 0.02, HAND_ARC = 0.04;

    /** The lever's turn about its pivot for its tilts (deg; ShiftLever: fore + the knob to the front, across + to the right). */
    public static Quaternionf leverTurn(double fore, double across) {
        return new Quaternionf().rotationX((float) Math.toRadians(-fore)).rotateZ((float) Math.toRadians(-across));
    }

    /**
     * The transfer case lever beside it (tools/pickup-t15 PIVOTS.transfer, the knob anchor transfer_knob; model frame): it
     * tilts fore and aft about the top of its boot; the knob's middle as drawn (TransferLever 0 deg).
     */
    public static final double[] TRANSFER_PIVOT = {0.07, 0.725, -0.38}, TRANSFER_KNOB = {0.07, 0.895, -0.33};

    /** The transfer lever's turn about its pivot (deg; TransferLever: + the knob to the front). */
    public static Quaternionf transferTurn(double fore) {
        return new Quaternionf().rotationX((float) Math.toRadians(-fore));
    }

    /** Where the hand holds the transfer lever's knob (model frame) at this tilt (deg). */
    public static double[] transferKnob(double fore) {
        Vector3f v = transferTurn(fore).transform(new Vector3f((float) (TRANSFER_KNOB[0] - TRANSFER_PIVOT[0]),
                (float) (TRANSFER_KNOB[1] - TRANSFER_PIVOT[1]), (float) (TRANSFER_KNOB[2] - TRANSFER_PIVOT[2])));
        return new double[]{TRANSFER_PIVOT[0] + v.x, TRANSFER_PIVOT[1] + v.y + KNOB_PALM, TRANSFER_PIVOT[2] + v.z};
    }

    /** Where the hand holds the knob (model frame) with the lever at these tilts (deg). */
    public static double[] knob(double fore, double across) {
        Vector3f v = leverTurn(fore, across).transform(new Vector3f((float) (SHIFT_KNOB[0] - SHIFT_PIVOT[0]),
                (float) (SHIFT_KNOB[1] - SHIFT_PIVOT[1]), (float) (SHIFT_KNOB[2] - SHIFT_PIVOT[2])));
        return new double[]{SHIFT_PIVOT[0] + v.x, SHIFT_PIVOT[1] + v.y + KNOB_PALM, SHIFT_PIVOT[2] + v.z};
    }

    /**
     * The right hand between its place on the rim and the knob (ShiftLever#hand: 0 on the rim .. 1 on the knob): along a
     * smooth line, arcing up {@link #HAND_ARC} on the way so it clears the rim and the dash.
     */
    public static double[] rightHand(double[] rim, double fore, double across, double hand) {
        return rightHand(rim, knob(fore, across), hand);
    }

    /** The right hand between its place on the rim and a knob (model frame; 0 on the rim .. 1 on the knob). */
    public static double[] rightHand(double[] rim, double[] k, double hand) {
        if (hand <= 0) return rim;
        double t = Math.min(1, hand), e = t * t * (3 - 2 * t), arc = HAND_ARC * Math.sin(Math.PI * t);
        return new double[]{rim[0] + (k[0] - rim[0]) * e, rim[1] + (k[1] - rim[1]) * e + arc, rim[2] + (k[2] - rim[2]) * e};
    }

    /**
     * A point on a door (left-door numbers {x, y, z}, mirrored for side +1) swung by the door's angle (deg), as
     * PickupTruckRenderer turns it: the left door about +Y by -angle, the right by +angle, through the hinge.
     */
    public static double[] door(int side, double[] p, double angle) {
        double px = HINGE_X * side, x = p[0] * side - px, z = p[2] - HINGE_Z;
        double a = Math.toRadians(side < 0 ? -angle : angle), c = Math.cos(a), s = Math.sin(a);
        return new double[]{px + x * c + z * s, p[1], HINGE_Z - x * s + z * c};
    }
    /** Where one gets out of a cab door (the rig's door_*_entry). */
    public static final double EXIT_F = 0.30, EXIT_R = 1.45;

    // ---- clickable parts ----

    public enum Zone { HOOD, DOOR_L, DOOR_R, FILLER, TAILGATE, SEAT, BED }

    /**
     * A part under the eyes: which, how far along the look (blocks), and the seat (SEAT only; for a door, {@link #LEAF}
     * when the look meets the swung-open door itself rather than its opening; else -1).
     */
    public record Pick(Zone zone, double distance, int seat) {
        /** The look meets an open door's own leaf (DOOR_L / DOOR_R only). */
        public boolean leaf() { return (zone == Zone.DOOR_L || zone == Zone.DOOR_R) && seat == LEAF; }
    }

    /** Pick#seat of a door pick on the swung-open leaf. */
    public static final int LEAF = 1;

    private static final Box HOOD = new Box(0.95, 2.42, -0.88, 0.88, 0.45, 1.12), HOOD_UP = new Box(1.05, 1.70, -0.80, 0.80, 1.12, 1.80);
    private static final Box DOOR = new Box(-0.19, 0.95, -0.90, -0.74, 0.46, 1.80), DOOR_SWUNG = new Box(0.42, 0.96, -1.95, -0.80, 0.46, 1.80);
    public static final Box FILLER = new Box(-0.72, -0.52, -0.90, -0.80, 0.85, 1.03);
    private static final Box TAILGATE = new Box(-2.42, -2.18, -0.76, 0.76, 0.58, 1.13), TAILGATE_FLAT = new Box(-2.78, -2.18, -0.76, 0.76, 0.52, 0.70);
    private static final Box BED = new Box(-2.22, -0.40, -0.76, 0.76, 0.74, 1.16);
    /** The fill neck behind the filler door (where the stream and the nozzle go). */
    public static final double[] FILL_NECK = {-0.62, -0.83, 0.94};

    /**
     * Ray entry distance (local frame, unit direction) into a cab seat, cushion and backrest (MOVE_OVER: a cab rider
     * looks at the other seat), or -1.
     */
    public static double cabSeatHit(int seat, double[] o, double[] d, double max) {
        Seat s = SEATS[seat];
        double r0 = Math.abs(s.r()) - 0.22, r1 = Math.abs(s.r()) + 0.22;
        Box b = s.r() < 0 ? new Box(s.f() - 0.32, s.f() + 0.26, -r1, -r0, 0.55, 1.40) : new Box(s.f() - 0.32, s.f() + 0.26, r0, r1, 0.55, 1.40);
        return b.hit(o, d, max);
    }

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
        if (doorL) best = nearer(best, Zone.DOOR_L, DOOR_SWUNG.hit(o, d, max), LEAF);
        if (doorR) best = nearer(best, Zone.DOOR_R, DOOR_SWUNG.mirror().hit(o, d, max), LEAF);
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
