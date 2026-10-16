package com.antaurora.apofirstlight.vehicle.physics;

/**
 * Engine, clutch, gearbox and transfer case (vehicle physics V0-b package 2, docs/vehicles/first_pickup_v0_design.md
 * section 8.5), stepped by {@link VehiclePhysics}:
 * <ul>
 * <li>Engine: one rotating inertia; full-load torque from the spec's curve times the fuel (the throttle pedal, the idle
 * governor holding {@code idleRpm}, cut off towards {@code cutRpm} and above the speed limiter), less friction. Stalls
 * below {@code stallRpm}; stops with no fuel or nobody at the wheel.</li>
 * <li>Ignition (the key held): preheat (glow plugs), then the starter cranks until it fires (a random time in the spec's
 * range; never without fuel). Letting go before it fires stops it; a new press while running switches it off.</li>
 * <li>Clutch (automatic): out in neutral, while shifting and at a standstill without throttle; with throttle at low road
 * speed it bites with the engine speed (900 rpm up to fully in at 1500 rpm, so the engine pulls away near 1300 rpm); in
 * once the two sides turn together. Torque per step: what would make both sides turn together this step (engine and the
 * truck's mass through the gears), at most its capacity times how far it is in. What the tyres could not take (spinning
 * wheels) goes back into the engine's speed.</li>
 * <li>Gears: R, N, 1..5, one step per key press; the lever goes across while the clutch is out ({@code shiftTime}), then
 * the clutch lets in again. A downshift that would over-rev, or reverse while rolling forward, is refused. Downshifting
 * blips the throttle to the new gear's speed.</li>
 * <li>2H / 4H: rear wheels, or all four (shared by axle load); changed only below {@code fourWheelSpeed}.</li>
 * <li>Fuel: litres per kWh of the engine's work plus a running base; the server takes it from the tank. Coolant:
 * warms to 88 C running, cools off (a gauge reading, no effect yet).</li>
 * </ul>
 */
public final class Drivetrain {
    /** Ignition: key off (or running), glow plugs preheating, starter cranking. */
    public static final int KEY_OFF = 0, PREHEAT = 1, CRANK = 2;
    private static final double RPM = 60 / (2 * Math.PI);
    /** Clutch: speeds (rpm) where launching begins to bite and is fully in; the slip (rpm) counted as turning together. */
    private static final double BITE = 900, BITE_FULL = 1500, LOCK_SLIP = 60;
    /** Below these road speeds at the clutch (rpm) it lets out: without throttle, and lugging with throttle. */
    private static final double OUT_IDLE = 850, OUT_LUG = 650;
    /** Throttle pedal travel per second (down, up); the idle governor's band (rpm); the cut-off band (rpm). */
    private static final double PEDAL_DOWN = 3.0, PEDAL_UP = 6.0, IDLE_BAND = 60, CUT_BAND = 250;
    /** Coolant: running and ambient temperature (C), time constants (s). */
    private static final double HOT = 88, AMBIENT = 20, WARM_TAU = 150, COOL_TAU = 900;

    private final VehicleSpec spec;
    public boolean running;
    /** Engine speed (rad/s). */
    public double omega;
    /** -1 reverse, 0 neutral, 1..5. */
    public int gear;
    /** How far the clutch is in (0 out .. 1 in) and whether it is fully in, both sides turning together. */
    public double clutch;
    public boolean locked;
    /** The throttle pedal (0..1, eased from the key). */
    public double pedal;
    /** A gear change under way: the gear it goes to, time left and its whole time (s). */
    public int shiftTarget;
    public double shiftLeft, shiftTotal;
    public boolean fourWheel;
    /** Ignition phase and its time so far (s). */
    public int ignition;
    public double ignitionTime;
    /** Coolant temperature (C) and the fuel used last tick (litres per second). */
    public double coolant = AMBIENT, fuelFlow;
    // the key last tick, and a press that must be let go before the next counts; cranking needed this time; fuel this tick
    private boolean keyHeld, keyLatch;
    private double crankNeeded;
    /** Time since it fired (s): it may not stall while it is still running up from cranking speed. */
    private double sinceFired = 10;
    private double fuelUsed;
    private long seed = 0x2545F4914F6CDD1DL;
    // this step: the torque the clutch carries (Nm, + driving), the fuel fraction
    private double clutchNow, fuelNow;
    /** The driven wheels' turn rate this step (rad/s; for the downshift blip). */
    private double lastWheel;

    public Drivetrain(VehicleSpec spec) {
        this.spec = spec;
    }

    public double rpm() { return omega * RPM; }

    public boolean shifting() { return shiftLeft > 0; }

    /** A gear change's progress (0 just begun .. 1 across), 1 when none. */
    public double shiftProgress() { return shiftLeft > 0 && shiftTotal > 0 ? 1 - shiftLeft / shiftTotal : 1; }

    /** The clutch pedal as a driver would hold it (1 down: out). */
    public double clutchPedal() { return shifting() || gear != 0 && running && !locked ? 1 - clutch : 0; }

    /** Overall ratio now (engine turns per wheel turn, signed). */
    public double ratio() { return spec.ratio(gear); }

    public void reset() {
        running = false;
        omega = 0;
        gear = 0;
        clutch = 0;
        locked = false;
        pedal = 0;
        shiftTarget = 0;
        shiftLeft = shiftTotal = 0;
        ignition = KEY_OFF;
        ignitionTime = 0;
        keyHeld = keyLatch = false;
        fuelFlow = 0;
    }

    public State capture() {
        return new State(running, (float) omega, (byte) gear, (float) clutch, locked, (float) pedal, (byte) shiftTarget, (float) shiftLeft, (float) shiftTotal,
                fourWheel, (byte) ignition, (float) ignitionTime, (float) coolant, (float) fuelFlow);
    }

    public void apply(State s) {
        running = s.running();
        omega = s.omega();
        gear = s.gear();
        clutch = s.clutch();
        locked = s.locked();
        pedal = s.pedal();
        shiftTarget = s.shiftTarget();
        shiftLeft = s.shiftLeft();
        shiftTotal = s.shiftTotal();
        fourWheel = s.fourWheel();
        ignition = s.ignition();
        ignitionTime = s.ignitionTime();
        coolant = s.coolant();
        fuelFlow = s.fuelFlow();
    }

    /** Whether it needs simulating (running, turning, starting or mid-shift). */
    public boolean busy() { return running || omega > 0.5 || ignition != KEY_OFF || shifting(); }

    // ---------------------------------------------------------------- per tick

    /** The keys of this tick: ignition, gear lever, transfer lever, throttle pedal. wheelOmega: the driven wheels (rad/s). */
    public void controls(VehicleInput in, double forwardSpeed, double wheelOmega) {
        fuelUsed = 0;
        if (!in.driver()) {
            running = false;
            ignition = KEY_OFF;
            keyHeld = keyLatch = false;
        } else {
            key(in);
            if (running && !in.fuel()) running = false;
        }
        double target = in.driver() ? in.throttle() : 0;
        pedal += clamp(target - pedal, -PEDAL_UP * VehiclePhysics.TICK, PEDAL_DOWN * VehiclePhysics.TICK);
        if (in.transfer() && Math.abs(forwardSpeed) < spec.fourWheelSpeed) fourWheel = !fourWheel;
        if (in.shift() != 0 && !shifting()) shift(in.shift(), forwardSpeed, wheelOmega);
    }

    private void key(VehicleInput in) {
        boolean press = in.ignition() && !keyHeld;
        keyHeld = in.ignition();
        if (!in.ignition()) keyLatch = false;
        if (running) {
            ignition = KEY_OFF;
            if (press && !keyLatch) {
                running = false;   // key back: off
                keyLatch = true;
            }
            return;
        }
        if (!in.ignition()) {
            ignition = KEY_OFF;
            return;
        }
        if (keyLatch) return;   // still held from switching it off
        if (ignition == KEY_OFF) {
            ignition = PREHEAT;
            ignitionTime = 0;
            crankNeeded = spec.crankMin + (spec.crankMax - spec.crankMin) * random();
        }
        ignitionTime += VehiclePhysics.TICK;
        if (ignition == PREHEAT && ignitionTime >= spec.preheat) {
            ignition = CRANK;
            ignitionTime = 0;
        } else if (ignition == CRANK && ignitionTime >= crankNeeded && in.fuel() && rpm() > 0.5 * spec.starterRpm) {
            running = true;   // fired
            sinceFired = 0;
            ignition = KEY_OFF;
            keyLatch = true;   // the same press does not switch it off again
        }
    }

    /** One step of the lever: R - N - 1 .. 5; refused into reverse while rolling forward, or down into an over-rev. */
    private void shift(int dir, double forwardSpeed, double wheelOmega) {
        int next = gear + (dir > 0 ? 1 : -1);
        if (next > spec.gears.length || next < -1) return;
        if (next == -1 && forwardSpeed > 1.0) return;
        if (next > 0 && forwardSpeed < -1.0) return;
        if (next > 0 && running && Math.abs(wheelOmega * spec.ratio(next)) * RPM > spec.cutRpm) return;
        shiftTarget = next;
        shiftTotal = gear == 0 || next == 0 ? spec.shiftTime * 0.6 : spec.shiftTime;
        shiftLeft = shiftTotal;
        locked = false;
    }

    // ---------------------------------------------------------------- per step

    /**
     * The clutch for this step (call first): the torque it carries (Nm at the clutch, + driving the truck forward in a
     * forward gear). wheelOmega: the driven wheels' turn rate (rad/s, + rolling forward).
     */
    public double clutchTorque(double wheelOmega, double dt, double forwardSpeed) {
        lastWheel = wheelOmega;
        double ratio = spec.ratio(gear);
        double drive = wheelOmega * ratio;   // the clutch's driven side (rad/s)
        if (shifting()) {
            shiftLeft -= dt;
            if (shiftLeft <= 0) {
                shiftLeft = 0;
                gear = shiftTarget;
                ratio = spec.ratio(gear);
                drive = wheelOmega * ratio;
            }
        }
        // the clutch's aim this step
        double aim;
        double driveRpm = drive * RPM, engineRpm = rpm();
        if (!running || gear == 0 || shifting()) {
            aim = 0;
            locked = false;
        } else {
            if (locked && driveRpm < (pedal > 0.05 ? OUT_LUG : OUT_IDLE)) locked = false;
            if (locked) aim = 1;
            else if (pedal > 0.05) aim = driveRpm > BITE_FULL ? 1 : clamp((engineRpm - BITE) / (BITE_FULL - BITE), 0, 1);
            else aim = driveRpm > OUT_IDLE + 150 ? 1 : 0;   // rolling in gear without throttle: in (engine braking), else out
            if (!locked && aim > 0.99 && clutch > 0.99 && Math.abs(engineRpm - driveRpm) < LOCK_SLIP && driveRpm > BITE) locked = true;
        }
        double rate = aim > clutch ? dt / spec.engageTime : dt * 10;
        clutch += clamp(aim - clutch, -rate, rate);
        if (clutch < 1e-4) {
            clutch = 0;
            clutchNow = 0;
        } else {
            // the torque that turns both sides together this step (the engine without the clutch, the truck's mass through the gears)
            fuelNow = fuel(forwardSpeed);
            double free = omega + dt / spec.engineInertia * (engineTorque(fuelNow) - friction());
            double massAtClutch = spec.mass * spec.radius * spec.radius / Math.max(ratio * ratio, 1e-6);
            double lock = (free - drive) / (dt / spec.engineInertia + dt / massAtClutch);
            double cap = clutch * spec.clutchTorque;
            clutchNow = clamp(lock, -cap, cap);
        }
        return clutchNow;
    }

    /** The force the clutch's torque puts on the road through the gears (N, + forward along the heading). */
    public double wheelForce(double clutchTorque) {
        return clutchTorque * spec.ratio(gear) * spec.efficiency / spec.radius;
    }

    /** The clutch torque that a road force corresponds to (inverse of {@link #wheelForce}). */
    public double clutchFor(double wheelForce) {
        double r = spec.ratio(gear);
        return r == 0 ? 0 : wheelForce * spec.radius / (r * spec.efficiency);
    }

    /** The engine for this step (call last): carried is the torque the clutch really took (the tyres' share of it). */
    public void engine(double carried, double dt, double forwardSpeed) {
        double starter = ignition == CRANK ? spec.starterTorque * Math.max(0, 1 - rpm() / spec.starterRpm) : 0;
        fuelNow = fuel(forwardSpeed);
        double torque = engineTorque(fuelNow) + starter - friction() - carried;
        omega = Math.max(0, omega + dt / spec.engineInertia * torque);
        sinceFired += dt;
        if (running && rpm() < spec.stallRpm && sinceFired > 0.5) running = false;
        if (running) fuelUsed += (fuelNow * spec.fullTorque(rpm()) * omega / 1000 * spec.fuelPerKwh + spec.fuelIdle) / 3600 * dt;
    }

    /** End of a tick: the fuel flow and the coolant. */
    public void endTick() {
        fuelFlow = fuelUsed / VehiclePhysics.TICK;
        double target = running ? HOT : AMBIENT, tau = running ? WARM_TAU : COOL_TAU;
        if (coolant > target && running) coolant = target;   // the thermostat holds it
        else coolant += (target - coolant) * VehiclePhysics.TICK / tau;
    }

    /** The fuel fraction now: pedal (or the downshift blip), the idle governor, the cut-off and the speed limiter. */
    private double fuel(double forwardSpeed) {
        if (!running) return 0;
        double rpm = rpm(), demand = pedal;
        if (shifting()) {
            // the lever across: off the throttle, but blip up to the lower gear's speed when shifting down
            double target = Math.abs(lastWheel * spec.ratio(shiftTarget)) * RPM;
            demand = shiftTarget != 0 && target > rpm ? clamp((target - rpm) / 300, 0, 1) : 0;
        }
        if (Math.abs(forwardSpeed) > spec.topSpeed - 0.5) demand *= clamp((spec.topSpeed - Math.abs(forwardSpeed)) / 0.5, 0, 1);
        double idle = clamp((spec.idleRpm - rpm) / IDLE_BAND, 0, 1);
        return Math.max(demand, idle) * clamp((spec.cutRpm - rpm) / CUT_BAND, 0, 1);
    }

    private double engineTorque(double fuel) {
        return fuel * spec.fullTorque(rpm());
    }

    private double friction() {
        return omega <= 0 ? 0 : spec.frictionA + spec.frictionB * rpm();
    }

    private double random() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 11) * 0x1.0p-53;
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }

    /** The drivetrain's part of a {@link VehicleState} (network). */
    public record State(boolean running, float omega, byte gear, float clutch, boolean locked, float pedal, byte shiftTarget, float shiftLeft,
                        float shiftTotal, boolean fourWheel, byte ignition, float ignitionTime, float coolant, float fuelFlow) {
        public static final State OFF = new State(false, 0, (byte) 0, 0, false, 0, (byte) 0, 0, 0, false, (byte) 0, 0, 20, 0);

        public boolean valid() {
            double sum = omega + clutch + pedal + shiftLeft + shiftTotal + ignitionTime + coolant + fuelFlow;
            return Double.isFinite(sum) && gear >= -1 && gear <= 5 && shiftTarget >= -1 && shiftTarget <= 5 && omega >= 0 && omega < 700
                    && fuelFlow >= 0 && fuelFlow < 0.02 && ignition >= 0 && ignition <= 2;
        }
    }
}
