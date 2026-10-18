package com.antaurora.apofirstlight.vehicle;

/**
 * The T15's tandem windshield wipers (docs/vehicles/t15_windshield_rain_design.md), one per truck on each client. The
 * switch (PickupTruckEntity#wiperMode, set on the server from the driver's key) is synced; the stroke runs here from when
 * this client saw the switch, so everyone watching sees the same stroke within a ping.
 * <p>
 * One stroke is out and back: the parked arms (along the glass base, toward the passenger side) turn up the glass to the
 * end of their sweep and down again, on the motor crank's sine (slow at both ends). Low and high run strokes back to back;
 * intermittent runs one low stroke, then waits parked. Switched off mid-stroke the arms finish it and park (the motor's
 * park switch). With the power off (engine stopped, key not at preheat) they stop where they are, as on the real truck.
 */
public final class WiperSystem {
    public static final int OFF = 0, INTERMITTENT = 1, LOW = 2, HIGH = 3, MODES = 4;
    /** Stroke time (s): low 45 and high 65 strokes a minute (typical two-speed motors); the intermittent wait (s). */
    public static final double LOW_STROKE = 60.0 / 45, HIGH_STROKE = 60.0 / 65, PAUSE = 4.0;
    /** Each arm's turn to the end of its stroke (deg; tools/pickup-t15/common.mjs WIPERS, the driver's side longer). */
    public static final float SWEEP_L = 105, SWEEP_R = 92;
    /** The wiper stalk's step down per mode (deg about the column axis; common.mjs HINGES.wiper_stalk) and its snap time (s). */
    private static final float[] STALK = {0, 7, 14, 21};
    private static final double STALK_TIME = 0.08;

    /** Where in the stroke (0 parked .. 1 parked again; 0.5 at the end of the sweep). */
    private double phase;
    /** Intermittent: time parked so far. */
    private double parked;
    private int mode = OFF;
    private double stalk;
    private double last = Double.NaN;

    /**
     * To this frame time (s). mode: the switch; powered: the truck's electrics are on. Several calls with one time do
     * nothing more.
     */
    public void update(double now, int mode, boolean powered) {
        double dt = Double.isNaN(last) ? 0 : Math.max(0, Math.min(0.25, now - last));
        last = now;
        if (dt == 0) return;
        if (mode != this.mode) {
            this.mode = mode;
            parked = PAUSE;   // a newly chosen intermittent wipes at once
        }
        double to = STALK[Math.max(0, Math.min(MODES - 1, mode))];
        stalk += Math.copySign(Math.min(Math.abs(to - stalk), dt * STALK[MODES - 1] / STALK_TIME), to - stalk);
        if (!powered) return;
        if (phase == 0) {
            if (mode == OFF) return;
            if (mode == INTERMITTENT && (parked += dt) < PAUSE) return;
        }
        phase += dt / (mode == HIGH ? HIGH_STROKE : LOW_STROKE);
        if (phase >= 1) {
            // parked again: a running mode carries straight into the next stroke, intermittent and off stop
            phase = mode == LOW || mode == HIGH ? phase - 1 : 0;
            parked = 0;
        }
    }

    /** The arms' turn as a share of their sweep (0 parked .. 1 at the end). */
    public double sweep() {
        return (1 - Math.cos(2 * Math.PI * phase)) / 2;
    }

    /** The left (driver's side) and right arms' turns (deg, + up the glass). */
    public float left() { return (float) sweep() * SWEEP_L; }

    public float right() { return (float) sweep() * SWEEP_R; }

    /** Where in the stroke (0..1; the windshield water reads the blades from it). */
    public double phase() { return phase; }

    /** The stalk's step down (deg). */
    public float stalk() { return (float) stalk; }

    /** The blades are moving. */
    public boolean moving() { return phase > 0; }
}
