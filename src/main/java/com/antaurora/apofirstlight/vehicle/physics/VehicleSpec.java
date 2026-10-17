package com.antaurora.apofirstlight.vehicle.physics;

/**
 * One vehicle type's physical data (docs/vehicles/first_pickup_v0_design.md section 3, V0-b package 1): the T15 for now.
 * Model frame, metres: +X right, +Y up, +Z back (the front is -Z), origin on the ground under the cab's middle with the
 * truck at rest (the mesh is built standing on its loaded wheels, so travel 0 is the static ride height).
 * <p>Numbers: the reference truck (Hilux LN106 single cab, 3L diesel, section 2.4): about 1450 kg empty, 57 % on the front
 * axle, wheelbase 2.85, track 1.42, 225/75 R15 tyres; springs for about 1.4 Hz at the front and 1.5 Hz at the rear, damping
 * about a third of critical. Engine and gearbox (package 2): the 3.0 L indirect-injection diesel (66 kW at 3600 rpm, 240 Nm
 * at 2400 rpm) and the 5-speed manual (3.93 / 2.33 / 1.45 / 1.00 / 0.85, reverse 4.48, axle 4.10), part-time 4WD.
 */
public final class VehicleSpec {
    /** Wheel order: front left, front right, rear left, rear right (as PickupTruckRenderer's hubs). */
    public static final int FL = 0, FR = 1, RL = 2, RR = 3;

    public final double mass, gravity = 9.81;
    /** Inertia about the model axes through the centre of mass (kg m2): pitch (X), yaw (Y), roll (Z). */
    public final double ix, iy, iz;
    /** Centre of mass (model frame). */
    public final double comX, comY, comZ;
    /** Wheel hubs at rest (model frame) and which are front / left. */
    public final double[] hubX, hubZ;
    public final double hubY, radius;
    public final boolean[] front, left;
    /** Suspension: travel up (bump) and down (droop) from rest; spring rate and damping per wheel; static compression. */
    public final double bump, droop;
    public final double[] spring, damping, staticCompression;
    /** Bump stop rate (N/m) and how far past the bump stop it pushes at most (m: a wall must not throw the truck). */
    public final double stopRate, stopDepth;
    public final double wheelbase, track;
    /**
     * Steering: the most the centre angle turns (rad), the cornering it allows at speed (m/s2: above walking pace the lock
     * is cut to what this lateral acceleration needs, so a full key press does not roll the truck), the rate (rad/s).
     */
    public final double maxSteer, steerGrip, steerRate, steerReturn;
    /** Anti-roll bars (N/m of travel difference across an axle, per wheel). */
    public final double[] antiRoll;
    /** The speed limiter (m/s: the engine's fuel is cut above it). */
    public final double topSpeed;
    /**
     * Engine (package 2): full-load torque (Nm) at these speeds (rpm, linear between); the governed idle and the speed the
     * full-load governor cuts the fuel off by (rpm); friction torque a + b x rpm (Nm); the rotating inertia (kg m2, with
     * the flywheel and the clutch's driving side); the speed below which it stalls (rpm); the starter's torque at rest and
     * the speed it reaches no more torque (rpm); glow plug preheat (s) and cranking until it fires (s, shortest / longest).
     */
    public final double[] torqueRpm, torqueNm;
    public final double idleRpm, cutRpm, frictionA, frictionB, engineInertia, stallRpm, starterTorque, starterRpm, preheat, crankMin, crankMax;
    /** Fuel: litres per kWh of the engine's own (indicated) work, and litres per hour running at all (pumps, idling losses). */
    public final double fuelPerKwh, fuelIdle;
    /** Gearbox: ratios of 1st..5th, the reverse's (positive; the sign is the gear's), the axle's, the driveline efficiency. */
    public final double[] gears;
    public final double reverseGear, finalDrive, efficiency;
    /** Clutch: the most torque it carries (Nm); the shift (s: out, lever across) and the time to let it in again (s). */
    public final double clutchTorque, shiftTime, engageTime;
    /**
     * Transfer case: 2H / 4H change only below this speed (m/s); the low range's ratio (4L, times every gear; 1: none) and
     * the speed below which it goes in or out (m/s: standing).
     */
    public final double fourWheelSpeed, lowRange, lowRangeSpeed;
    /** Brakes: all four at once (N), the front's share; rolling resistance; drag (0.5 rho Cd A). */
    public final double brakeForce, brakeFront, rolling, drag;
    /** Tyres: the slip angle of peak side force (rad), the sliding grip left past it, the speed below which slip is linear. */
    public final double slipPeak, slideGrip, slipSpeed;
    /** Hull sample points (model frame, x y z triples) checked against the world. */
    public final double[] hull;

    private VehicleSpec(Builder b) {
        mass = b.mass;
        ix = b.ix;
        iy = b.iy;
        iz = b.iz;
        comX = 0;
        comY = b.comY;
        comZ = b.comZ;
        hubX = new double[]{-b.halfTrack, b.halfTrack, -b.halfTrack, b.halfTrack};
        hubZ = new double[]{b.frontZ, b.frontZ, b.rearZ, b.rearZ};
        hubY = b.radius;
        radius = b.radius;
        front = new boolean[]{true, true, false, false};
        left = new boolean[]{true, false, true, false};
        bump = b.bump;
        droop = b.droop;
        wheelbase = b.rearZ - b.frontZ;
        track = 2 * b.halfTrack;
        // static axle loads from the centre of mass along the wheelbase
        double frontShare = (b.rearZ - b.comZ) / wheelbase;
        spring = new double[4];
        damping = new double[4];
        staticCompression = new double[4];
        for (int i = 0; i < 4; i++) {
            double load = mass * gravity * (front[i] ? frontShare : 1 - frontShare) / 2;
            spring[i] = front[i] ? b.springFront : b.springRear;
            damping[i] = front[i] ? b.dampFront : b.dampRear;
            staticCompression[i] = load / spring[i];
        }
        stopRate = b.stopRate;
        stopDepth = b.stopDepth;
        maxSteer = b.maxSteer;
        steerGrip = b.steerGrip;
        antiRoll = new double[]{b.antiRollFront, b.antiRollFront, b.antiRollRear, b.antiRollRear};
        steerRate = b.steerRate;
        steerReturn = b.steerReturn;
        topSpeed = b.topSpeed;
        torqueRpm = b.torqueRpm;
        torqueNm = b.torqueNm;
        idleRpm = b.idleRpm;
        cutRpm = b.cutRpm;
        frictionA = b.frictionA;
        frictionB = b.frictionB;
        engineInertia = b.engineInertia;
        stallRpm = b.stallRpm;
        starterTorque = b.starterTorque;
        starterRpm = b.starterRpm;
        preheat = b.preheat;
        crankMin = b.crankMin;
        crankMax = b.crankMax;
        fuelPerKwh = b.fuelPerKwh;
        fuelIdle = b.fuelIdle;
        gears = b.gears;
        reverseGear = b.reverseGear;
        finalDrive = b.finalDrive;
        efficiency = b.efficiency;
        clutchTorque = b.clutchTorque;
        shiftTime = b.shiftTime;
        engageTime = b.engageTime;
        fourWheelSpeed = b.fourWheelSpeed;
        lowRange = b.lowRange;
        lowRangeSpeed = b.lowRangeSpeed;
        brakeForce = b.brakeForce;
        brakeFront = b.brakeFront;
        rolling = b.rolling;
        drag = b.drag;
        slipPeak = b.slipPeak;
        slideGrip = b.slideGrip;
        slipSpeed = b.slipSpeed;
        hull = b.hull;
    }

    /** The Halstead Tamarack T15 (tools/pickup-t15/common.mjs: axles at -1.60 / +1.25, wheel centres at |x| 0.712, r 0.36). */
    public static final VehicleSpec T15 = t15();

    private static VehicleSpec t15() {
        Builder b = new Builder();
        b.mass = 1450;
        b.ix = 2600;
        b.iy = 2800;
        b.iz = 620;
        b.comY = 0.72;
        b.comZ = -0.375;
        b.frontZ = -1.60;
        b.rearZ = 1.25;
        b.halfTrack = 0.712;
        b.radius = 0.36;
        b.bump = 0.10;
        b.droop = 0.12;
        b.springFront = 40000;
        b.springRear = 32000;
        b.dampFront = 2800;
        b.dampRear = 2500;
        b.stopRate = 250000;
        b.stopDepth = 0.12;
        b.maxSteer = Math.toRadians(32);
        b.steerGrip = 0.75 * 9.81;
        b.antiRollFront = 18000;
        b.antiRollRear = 6000;
        b.steerRate = Math.toRadians(55);
        b.steerReturn = Math.toRadians(75);
        b.topSpeed = 100 / 3.6;
        b.torqueRpm = new double[]{0, 750, 1200, 1800, 2400, 3000, 3600, 4000, 4400};
        b.torqueNm = new double[]{120, 150, 195, 230, 240, 225, 175, 150, 120};
        b.idleRpm = 760;
        b.cutRpm = 4300;
        b.frictionA = 18;
        b.frictionB = 0.010;
        b.engineInertia = 0.25;
        b.stallRpm = 350;
        b.starterTorque = 80;
        b.starterRpm = 300;
        b.preheat = 0.8;
        b.crankMin = 0.5;
        b.crankMax = 1.5;
        b.fuelPerKwh = 0.28;
        b.fuelIdle = 0.2;
        b.gears = new double[]{3.93, 2.33, 1.45, 1.00, 0.85};
        b.reverseGear = 4.48;
        b.finalDrive = 4.10;
        b.efficiency = 0.90;
        b.clutchTorque = 350;
        b.shiftTime = 0.35;
        b.engageTime = 0.25;
        b.fourWheelSpeed = 20 / 3.6;
        b.lowRange = 2.28;   // a small pickup's part-time case (2.28 : 1 low range)
        b.lowRangeSpeed = 0.8;
        b.brakeForce = 14000;
        b.brakeFront = 0.65;
        b.rolling = 0.015;
        b.drag = 0.70;
        b.slipPeak = 0.12;
        b.slideGrip = 0.85;
        b.slipSpeed = 2.0;
        b.hull = t15Hull();
        return new VehicleSpec(b);
    }

    /** Points on the body's outside (model frame): bumpers, grille, sides, tailgate, cab, roof, two under the frame. */
    private static double[] t15Hull() {
        java.util.List<double[]> p = new java.util.ArrayList<>();
        for (double x : new double[]{-0.84, -0.42, 0, 0.42, 0.84}) {
            p.add(new double[]{x, 0.32, -2.39});   // front bumper
            p.add(new double[]{x, 0.62, -2.39});
            p.add(new double[]{x, 0.95, -2.27});   // grille, lamps
            p.add(new double[]{x * 1.02, 0.32, 2.36});   // rear step bumper
            p.add(new double[]{x * 1.02, 0.55, 2.36});
        }
        for (double x : new double[]{-0.75, 0, 0.75}) {
            p.add(new double[]{x, 0.85, 2.25});    // tailgate
            p.add(new double[]{x, 1.10, 2.25});
        }
        for (double side : new double[]{-1, 1}) {
            for (double z : new double[]{-2.2, -1.1, -0.6, -0.1, 0.4, 0.9, 1.8, 2.2}) p.add(new double[]{side * 0.845, 1.00, z});
            for (double z : new double[]{-2.2, -0.9, -0.3, 0.3, 0.6, 2.2}) p.add(new double[]{side * 0.845, 0.55, z});
            for (double z : new double[]{-0.55, -0.1, 0.28}) p.add(new double[]{side * 0.79, 1.45, z});
            p.add(new double[]{side * 0.75, 1.84, -0.42});   // roof corners (raised 10 cm 2026-10-16)
            p.add(new double[]{side * 0.75, 1.84, 0.30});
            p.add(new double[]{side * 0.45, 0.30, -0.6});    // frame rails under the cab and the bed
            p.add(new double[]{side * 0.45, 0.30, 0.3});
        }
        p.add(new double[]{0, 1.845, -0.05});
        double[] out = new double[p.size() * 3];
        for (int i = 0; i < p.size(); i++) System.arraycopy(p.get(i), 0, out, i * 3, 3);
        return out;
    }

    /** Full-load torque at an engine speed (Nm, rpm; linear between the table's points, flat past its ends). */
    public double fullTorque(double rpm) {
        double[] x = torqueRpm, y = torqueNm;
        if (rpm <= x[0]) return y[0];
        for (int i = 1; i < x.length; i++)
            if (rpm <= x[i]) return y[i - 1] + (y[i] - y[i - 1]) * (rpm - x[i - 1]) / (x[i] - x[i - 1]);
        return y[y.length - 1];
    }

    /** Overall ratio of a gear (engine turns per wheel turn; -1 reverse is negative, 0 neutral is 0). */
    public double ratio(int gear) {
        if (gear == 0) return 0;
        return (gear < 0 ? -reverseGear : gears[gear - 1]) * finalDrive;
    }

    /** The most the centre steer angle may turn at this speed (rad): the lock, or what steerGrip of cornering needs. */
    public double steerLimit(double speed) {
        return Math.min(maxSteer, Math.atan(wheelbase * steerGrip / Math.max(speed * speed, 1e-6)));
    }

    private static final class Builder {
        double mass, ix, iy, iz, comY, comZ, frontZ, rearZ, halfTrack, radius, bump, droop, springFront, springRear, dampFront,
                dampRear, stopRate, stopDepth, maxSteer, steerGrip, antiRollFront, antiRollRear, steerRate, steerReturn, topSpeed,
                idleRpm, cutRpm, frictionA, frictionB, engineInertia, stallRpm, starterTorque, starterRpm, preheat, crankMin, crankMax, fuelPerKwh, fuelIdle,
                reverseGear, finalDrive, efficiency, clutchTorque, shiftTime, engageTime, fourWheelSpeed, lowRange, lowRangeSpeed, brakeForce, brakeFront, rolling, drag, slipPeak, slideGrip, slipSpeed;
        double[] hull, torqueRpm, torqueNm, gears;
    }
}
