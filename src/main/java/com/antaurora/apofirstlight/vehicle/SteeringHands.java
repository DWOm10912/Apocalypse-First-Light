package com.antaurora.apofirstlight.vehicle;

/**
 * The driver's hands on the T15's steering wheel (docs/dev/seated_rider_v1_plan.md 12): push-pull shuffle steering, the
 * hands never crossing. Each hand holds the rim and goes round with it within its own side's reach ({@link #UP} toward
 * 12 o'clock, {@link #DOWN} toward 6); turning in further, the hand at its leading limit lets go and takes the rim again
 * {@link #BACK} deg the other side of its rest while the other hand holds (one hand off the rim at a time). Faster than
 * the hands can go (the wheel turns up to about 930 deg/s at the lock), the rim slides through the holding hand. When the
 * wheel goes back to the middle the hands hold it loosely (the rim slides through them at their limits); once it has been
 * still for {@link #SETTLE_AFTER} s, a hand more than {@link #SETTLE_OFF} deg off its rest (9 / 3 o'clock) goes back to it.
 * <p>Angles: each hand's place on the rim, deg from its rest, + with the wheel's + (clockwise as the driver sees it); the
 * right hand's + goes down, the left hand's + goes up. Pure; one per truck (client: only its driver's hands are drawn).
 * <p>The right hand away on the gear lever (ShiftLever, docs/dev/seated_rider_v1_plan.md 13): its place stays where it let
 * go (no regrips, no settling; the left hand alone holds, the rim sliding through it at its reach); back, it takes the rim
 * at its rest.
 */
public final class SteeringHands {
    /** Reach from the rest toward 12 o'clock and toward 6 (deg; SeatCheck: the seated arm's reach). */
    public static final double UP = 50, DOWN = 70;
    /** A hand that let go takes the rim again this far behind its rest (deg). */
    public static final double BACK = 30;
    /** Off the rim while a hand goes over (m, toward the driver at the middle of the move). */
    public static final double LIFT = 0.025;
    /** Settling the hands back to 9 and 3: the wheel still (below STILL deg/s) this long (s); a hand this far off (deg). */
    public static final double STILL = 30, SETTLE_AFTER = 0.3, SETTLE_OFF = 12;
    /** A hand's move over the rim: this long (s), plus PER_90 for every 90 deg it goes. */
    public static final double MOVE = 0.16, PER_90 = 0.14;

    /** Left (0) and right (1): its place on the rim (deg from its rest) and how far off the rim (m), as of the last update. */
    public final double[] angle = new double[2], lift = new double[2];
    /** Holding the rim (not on the way over). */
    public final boolean[] holding = {true, true};

    /** Where a holding hand grips the rim (deg on the wheel itself: its place is grip + wheel). */
    private final double[] grip = new double[2];
    private final double[] from = new double[2], to = new double[2], start = new double[2], length = new double[2];
    private double time = Double.NaN, wheel, still;
    /** The right hand is away on the gear lever. */
    private boolean away;

    /** The lowest and highest place a hand reaches (deg from its rest, with the wheel's sense). */
    public static double min(int hand) { return hand == 1 ? -UP : -DOWN; }

    public static double max(int hand) { return hand == 1 ? DOWN : UP; }

    /** Both hands back on their rests (a new driver, a jump in time). */
    public void reset(double now, double wheelDeg) {
        time = now;
        wheel = wheelDeg;
        still = 0;
        away = false;
        for (int i = 0; i < 2; i++) {
            holding[i] = true;
            grip[i] = -wheelDeg;
            angle[i] = 0;
            lift[i] = 0;
        }
    }

    /** To this time (s) with the wheel at this angle (deg); an older or the same time keeps the last state. */
    public void update(double now, double wheelDeg) {
        update(now, wheelDeg, false);
    }

    /** As {@link #update(double, double)}, the right hand away on the gear lever while {@code rightAway}. */
    public void update(double now, double wheelDeg, boolean rightAway) {
        if (Double.isNaN(time) || now - time > 0.5) {
            reset(now, wheelDeg);
            return;
        }
        double dt = now - time;
        if (dt <= 0) return;
        if (rightAway != away) {
            away = rightAway;
            holding[1] = !away;
            lift[1] = 0;
            if (!away) {
                // back from the lever: on the rim at its rest
                angle[1] = 0;
                grip[1] = -wheelDeg;
            }
        }
        double rate = (wheelDeg - wheel) / dt;
        still = Math.abs(rate) < STILL ? still + dt : 0;
        boolean turningIn = Math.abs(rate) >= STILL && wheelDeg * rate > 0;
        double dir = Math.signum(rate);
        for (int i = 0; i < 2; i++) {
            if (i == 1 && away) continue;
            if (!holding[i]) {
                double s = (now - start[i]) / length[i];
                if (s >= 1) {
                    holding[i] = true;
                    grip[i] = to[i] - wheelDeg;
                    angle[i] = to[i];
                    lift[i] = 0;
                } else {
                    double e = smooth(s);
                    angle[i] = from[i] + (to[i] - from[i]) * e;
                    lift[i] = LIFT * Math.sin(Math.PI * s);
                }
            }
            if (holding[i]) {
                // with the wheel, the rim sliding through the hand past its reach
                double a = clamp(grip[i] + wheelDeg, min(i), max(i));
                grip[i] = a - wheelDeg;
                angle[i] = a;
                lift[i] = 0;
            }
        }
        // one hand off the rim at a time
        if (holding[0] && holding[1]) {
            int hand = -1;
            double target = 0;
            if (turningIn) {
                // the hand with less room left goes over, at its limit or once the other has only just the room to hold
                // the wheel while it does (the latest start without the rim sliding)
                double[] room = new double[2];
                for (int i = 0; i < 2; i++) room[i] = ((dir > 0 ? max(i) : min(i)) - angle[i]) * dir;
                int i = room[0] <= room[1] ? 0 : 1;
                target = -dir * BACK;
                double over = Math.abs(rate) * moveLength(target - angle[i]);
                if (room[i] <= 1 || room[1 - i] <= over * 1.1) hand = i;
            } else if (still >= SETTLE_AFTER) {
                double off = SETTLE_OFF;
                for (int i = 0; i < 2; i++)
                    if (Math.abs(angle[i]) > off) {
                        off = Math.abs(angle[i]);
                        hand = i;
                    }
                target = 0;
            }
            if (hand >= 0) {
                holding[hand] = false;
                from[hand] = angle[hand];
                to[hand] = target;
                start[hand] = now;
                length[hand] = moveLength(target - angle[hand]);
            }
        }
        time = now;
        wheel = wheelDeg;
    }

    /** How long a hand takes to go this far over the rim (s). */
    static double moveLength(double deg) { return MOVE + PER_90 * Math.abs(deg) / 90; }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static double smooth(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
