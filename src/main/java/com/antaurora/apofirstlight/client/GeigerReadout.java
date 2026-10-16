package com.antaurora.apofirstlight.client;

import net.minecraft.util.RandomSource;

/**
 * Geiger counter V2 reading (docs/models/geiger_counter_v2_design.md): what the held counter's LCD and the Geiger HUD show,
 * from the server's dose rate (ClientGeigerData, every 10 ticks). The shown rate follows the measured one every frame in
 * log space (time constant {@link #TAU}), so a step from 0.02 to 40 RU/h glides through the decades instead of jumping
 * at each packet. Auto range on 4 digits: below 10 x.xxx, below 100 xx.xx, below 200 xxx.x, from 200 "OL"; dashes
 * until the first reading arrives (or after 2 s without one). Counts are simulated (Poisson, {@link #cps}): each one
 * flashes the count LED and is handed to the listener (GeigerSoundController plays a tick for it). The alarm is on from
 * {@link #ALARM} RU/h. Stepped once per frame by GeigerSoundController while a counter is held; both views read it.
 */
public final class GeigerReadout {
    /** Full scale (RU/h): from here the LCD shows OL. */
    public static final double RANGE = 200.0;
    /** Alarm line (RU/h), the heavy zone's start; fixed until the keys get settings. */
    public static final double ALARM = 10.0;
    /** Response time constant (s) of the shown rate in log space. */
    private static final double TAU = 0.8;
    /** Log offset (RU/h): keeps zero finite and the low end of the glide short. */
    private static final double OFFSET = 0.005;
    /** One count's LED flash (s). */
    private static final double FLASH = 0.035;
    /** Seven-segment bits a..g = 1..64 for 0..9; the letters O and L and the dash. */
    private static final int[] DIGIT = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
    private static final int O = 0x3F, L = 0x38, DASH = 0x40;
    private static final RandomSource RANDOM = RandomSource.create();
    /** Gets the number of counts of each step (at most a few per frame). */
    public interface CountListener { void counts(int n, double cps); }
    private static double shownLog = Double.NaN, ledUntil;
    private static long lastNanos;
    private static CountListener listener = (n, cps) -> {};

    /** What the 4-digit LCD shows: one segment mask per cell (left to right), the decimal point after cell dp (-1: none). */
    public record Display(int[] cells, int dp) {
        public boolean segment(int cell, int bit) { return (cells[cell] & (1 << bit)) != 0; }
        long key() { long k = dp + 1; for (int c : cells) k = k * 128 + c; return k; }
    }

    private GeigerReadout() {}

    public static void listen(CountListener l) { listener = l; }

    /** Game paused or nothing held: the next step starts fresh instead of covering the gap. */
    public static void pause() { lastNanos = 0; }

    /** Steps the shown rate, the counts and the count LED to now. */
    public static void advance() {
        long nanos = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (nanos - lastNanos) / 1e9);
        lastNanos = nanos;
        ClientGeigerData.Snapshot data = ClientGeigerData.snapshot();
        if (data.stale()) { shownLog = Double.NaN; return; }
        double target = Math.log(Math.max(0, data.measuredRate()) + OFFSET);
        shownLog = Double.isNaN(shownLog) ? target : shownLog + (target - shownLog) * (1 - Math.exp(-dt / TAU));
        double now = nanos / 1e9;
        double cps = cps(rate());
        int n = poisson(cps * dt);
        if (n > 0) { ledUntil = now + FLASH; listener.counts(n, cps); }
    }

    /** Poisson draw by multiplication (mean below ~30 per frame here). */
    private static int poisson(double mean) {
        if (mean <= 0) return 0;
        double limit = Math.exp(-Math.min(mean, 50)), p = 1;
        int k = 0;
        do { k++; p *= RANDOM.nextDouble(); } while (p > limit);
        return k - 1;
    }

    /** The shown rate (RU/h), NaN without a reading. */
    public static double rate() {
        return Double.isNaN(shownLog) ? Double.NaN : Math.max(0, Math.exp(shownLog) - OFFSET);
    }

    /** Counts per second of the pancake tube at a rate (RU/h): a little background plus a share of the rate. */
    public static double cps(double rate) { return 0.4 + 12.0 * rate; }

    public static boolean ledLit() { return !Double.isNaN(shownLog) && System.nanoTime() / 1e9 < ledUntil; }

    public static boolean alarm() { double r = rate(); return !Double.isNaN(r) && r >= ALARM; }

    public static Display display() { return format(rate()); }

    /** LCD text for a rate (NaN: dashes). */
    public static Display format(double rate) {
        if (Double.isNaN(rate)) return new Display(new int[]{DASH, DASH, DASH, DASH}, -1);
        if (rate >= RANGE) return new Display(new int[]{0, O, L, 0}, -1);
        int scale = rate < 10 ? 1000 : rate < 100 ? 100 : 10, dp = rate < 10 ? 0 : rate < 100 ? 1 : 2;
        int n = (int)Math.min(9999, Math.floor(rate * scale + 1e-9));
        int[] cells = new int[4];
        for (int i = 3; i >= 0; i--) { cells[i] = DIGIT[n % 10]; n /= 10; }
        return new Display(cells, dp);
    }
}
