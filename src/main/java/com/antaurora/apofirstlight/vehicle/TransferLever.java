package com.antaurora.apofirstlight.vehicle;

/**
 * The T15's transfer case lever and the driver's right hand on it (docs/vehicles/first_pickup_v0_design.md 8.14): one
 * straight gate, front to back 2H - 4H - N - 4L, as on a small pickup's part-time case. A change (Drivetrain#transferMode)
 * takes the right hand off the wheel to the knob ({@link ShiftLever#REACH} s); the lever then goes back or forward to the
 * new position, through the others on its way, stopping a moment in N when it goes into or out of low; the hand stays on
 * the knob {@link #HOLD} s and goes back to the wheel ({@link ShiftLever#BACK} s). Pure; one per truck, every client (the
 * lever is drawn for everyone, the hand only with the skeleton).
 * <p>Tilt in deg: {@code fore} + the knob toward the front; PickupTruckLayout#transferTurn.
 */
public final class TransferLever {
    /** The knob's tilt in each position (deg; the knob about 17 cm over the pivot: 2H 2.4 cm ahead .. 4L 4.1 cm back). */
    public static final double TWO_HIGH = 8, FOUR_HIGH = -1, NEUTRAL = -7, FOUR_LOW = -14;
    /** The lever's move: a base time plus this per degree (s); its pause in N into or out of low (s); hand on the knob after. */
    public static final double MOVE = 0.16, PER_DEG = 0.012, DETENT = 0.08, HOLD = 0.12;

    /** Now: the lever's tilt (deg) and the hand's share on the knob (0 on the wheel .. 1 on the knob). */
    public double fore, hand;
    /** The hand is going to (or on) the knob, not on its way back. */
    public boolean handGoing;

    private double time = Double.NaN;
    /** The position the lever is in or going to (0 2H, 1 4H, 2 4L). */
    private int mode = Integer.MIN_VALUE;
    /** The move: from, through (NaN: straight), to (deg), start time and length (s); NaN start: none. */
    private double from, via = Double.NaN, to, moveStart = Double.NaN, moveLength;
    private double handStart, handFrom;
    private double restAt = Double.NaN;

    /** The knob's tilt in a transfer mode (0 2H, 1 4H, 2 4L). */
    public static double tilt(int mode) {
        return mode == 2 ? FOUR_LOW : mode == 1 ? FOUR_HIGH : TWO_HIGH;
    }

    /**
     * To this time (s): {@code wanted} is the transfer mode now. An older or the same time keeps the last state; a first
     * call or a jump of more than a second puts the lever there with the hand on the wheel.
     */
    public void update(double now, int wanted) {
        if (Double.isNaN(time) || now - time > 1.0 || mode == Integer.MIN_VALUE) {
            time = now;
            mode = wanted;
            fore = tilt(wanted);
            hand = 0;
            handGoing = false;
            moveStart = Double.NaN;
            restAt = Double.NaN;
            return;
        }
        if (now <= time) return;
        if (wanted != mode) {
            boolean low = wanted == 2 || mode == 2;
            mode = wanted;
            from = fore;
            to = tilt(wanted);
            // into or out of low it goes through N (between 4H and 4L): a short stop there
            via = low && (from - NEUTRAL) * (to - NEUTRAL) < 0 ? NEUTRAL : Double.NaN;
            if (!handGoing) {
                handGoing = true;
                handStart = now;
                handFrom = hand;
            }
            moveStart = now + ShiftLever.REACH * (1 - hand);
            moveLength = MOVE + PER_DEG * Math.abs(to - from) + (Double.isNaN(via) ? 0 : DETENT);
            restAt = Double.NaN;
        }
        if (handGoing) {
            hand = handFrom >= 1 ? 1 : Math.min(1, handFrom + (now - handStart) / ShiftLever.REACH);
        } else if (hand > 0) {
            hand = Math.max(0, handFrom - (now - handStart) / ShiftLever.BACK * handFrom);
        }
        if (!Double.isNaN(moveStart)) {
            double t = now - moveStart;
            if (t >= moveLength) {
                fore = to;
                moveStart = Double.NaN;
                restAt = now;
            } else if (t > 0) {
                if (Double.isNaN(via)) {
                    fore = from + (to - from) * smooth(t / moveLength);
                } else {
                    // to N, the pause, on from N: each leg's time by its angle
                    double swing = moveLength - DETENT, first = swing * Math.abs(via - from) / Math.abs(to - from);
                    if (t < first) fore = from + (via - from) * smooth(t / first);
                    else if (t < first + DETENT) fore = via;
                    else fore = via + (to - via) * smooth((t - first - DETENT) / (swing - first));
                }
            }
        }
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
