package com.antaurora.apofirstlight.vehicle;

/**
 * The T15's gear lever and the driver's right hand on it (docs/dev/seated_rider_v1_plan.md 13): a five-speed H gate, 1 3 5
 * forward, 2 4 R back, the 3-4 plane in the middle where the lever rests in neutral. A gear change (the gearbox's target,
 * Drivetrain#shiftTarget) takes the right hand off the wheel to the knob ({@link #REACH} s); the lever then goes out of its
 * gear to the neutral row, across the gate and into the new gear ({@link #moveLength}, about the gearbox's own shift time);
 * the hand stays on the knob {@link #HOLD} s and goes back to the wheel ({@link #BACK} s). A change that comes while the
 * hand is still there (going up through the gears) starts at once; one that comes while the lever is still moving goes on
 * from where it is. Pure; one per truck, every client (the lever is drawn for everyone, the hand only with the skeleton).
 * <p>Tilts in deg: {@code fore} + the knob toward the front, {@code across} + toward the right; PickupTruckLayout#leverTurn.
 */
public final class ShiftLever {
    /** The knob's tilt at the gate's ends: fore / aft rows and the outer planes (deg; about 6 and 5 cm at the knob). */
    public static final double FORE = 11, ACROSS = 9;
    /** Hand to the knob, the lever's least move time, hand kept on the knob after it, hand back to the wheel (s). */
    public static final double REACH = 0.12, LEAST = 0.18, HOLD = 0.10, BACK = 0.22;
    /** The lever's path: out to the neutral row by this share of the move, across by the next. */
    private static final double OUT = 0.35, ACROSS_END = 0.6;

    /** Now: the lever's tilts (deg) and the hand's share on the knob (0 on the wheel .. 1 on the knob). */
    public double fore, across, hand;
    /** The hand is going to (or on) the knob, not on its way back. */
    public boolean handGoing;

    private double time = Double.NaN;
    /** The gear the lever is in or going to. */
    private int gear = Integer.MIN_VALUE;
    /** The move: from (column, row), to (column, row), start time and length (s); NaN: none. */
    private double fromCol, fromRow, toCol, toRow, moveStart = Double.NaN, moveLength;
    /** When the hand set off (to the knob or back) and from what share. */
    private double handStart, handFrom;
    /** The lever came to rest at this time (NaN: moving or the hand never came). */
    private double restAt = Double.NaN;

    /** The gate column of a gear: -1 (1, 2), 0 (3, 4, neutral), +1 (5, R). */
    public static int column(int gear) {
        return gear == 1 || gear == 2 ? -1 : gear == 5 || gear < 0 ? 1 : 0;
    }

    /** The gate row of a gear: +1 forward (1, 3, 5), -1 back (2, 4, R), 0 neutral. */
    public static int row(int gear) {
        return gear == 0 ? 0 : gear < 0 ? -1 : gear % 2 == 1 ? 1 : -1;
    }

    /** The lever's move time for a gearbox shift of this length (s; Drivetrain#shiftTotal, 0 when unknown). */
    public static double moveLength(double shiftTotal) {
        return Math.max(LEAST, shiftTotal * 0.8);
    }

    /**
     * To this time (s): {@code wanted} is the gear the gearbox is in or going to, {@code shiftTotal} its shift time (s).
     * An older or the same time keeps the last state; a first call or a jump of more than a second puts the lever in
     * the gear with the hand on the wheel.
     */
    public void update(double now, int wanted, double shiftTotal) {
        if (Double.isNaN(time) || now - time > 1.0 || gear == Integer.MIN_VALUE) {
            time = now;
            gear = wanted;
            fore = row(wanted) * FORE;
            across = column(wanted) * ACROSS;
            hand = 0;
            handGoing = false;
            moveStart = Double.NaN;
            restAt = Double.NaN;
            return;
        }
        if (now <= time) return;
        if (wanted != gear) {
            // a new target: the hand to the knob (if not there), the lever from where it is
            gear = wanted;
            fromCol = across / ACROSS;
            fromRow = fore / FORE;
            toCol = column(wanted);
            toRow = row(wanted);
            if (!handGoing) {
                handGoing = true;
                handStart = now;
                handFrom = hand;
            }
            double reachLeft = REACH * (1 - hand);
            moveStart = now + reachLeft;
            moveLength = moveLength(shiftTotal);
            restAt = Double.NaN;
        }
        // the hand
        if (handGoing) {
            hand = handFrom >= 1 ? 1 : Math.min(1, handFrom + (now - handStart) / REACH);
        } else if (hand > 0) {
            hand = Math.max(0, handFrom - (now - handStart) / BACK * handFrom);
        }
        // the lever
        if (!Double.isNaN(moveStart)) {
            double s = (now - moveStart) / moveLength;
            if (s >= 1) {
                fore = toRow * FORE;
                across = toCol * ACROSS;
                moveStart = Double.NaN;
                restAt = now;
            } else if (s > 0) {
                double col, row;
                if (Math.abs(toCol - fromCol) < 1e-3) {
                    // the same plane: straight through the neutral row
                    col = toCol;
                    row = fromRow + (toRow - fromRow) * smooth(s);
                } else if (s < OUT) {
                    col = fromCol;
                    row = fromRow * (1 - smooth(s / OUT));
                } else if (s < ACROSS_END) {
                    col = fromCol + (toCol - fromCol) * smooth((s - OUT) / (ACROSS_END - OUT));
                    row = 0;
                } else {
                    col = toCol;
                    row = toRow * smooth((s - ACROSS_END) / (1 - ACROSS_END));
                }
                fore = row * FORE;
                across = col * ACROSS;
            }
        }
        // back to the wheel once the lever has been at rest a moment
        if (handGoing && !Double.isNaN(restAt) && now - restAt >= HOLD) {
            handGoing = false;
            handStart = now;
            handFrom = hand;
            restAt = Double.NaN;
        }
        time = now;
    }

    /** The hand is anywhere off the wheel (on its way, on the knob or on its way back). */
    public boolean handOff() { return hand > 0 || handGoing; }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
