package com.antaurora.apofirstlight.time;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 4): the fixed-point step of the world clock. A day stays 24 000
 * world time units; it takes {@code minutes × 1200} ticks. Each tick adds 24 000 to the accumulator and moves the clock on
 * by the whole days' worth of ticks in it, so the clock never drifts: at 60 minutes it moves exactly one unit every third
 * tick, however long it runs. Pure (no game classes): the server and the client each keep one, and WorldClockCheck runs it
 * offline.
 */
public final class WorldClock {
    public static final long UNITS_PER_DAY = 24_000, TICKS_PER_MINUTE = 1_200;
    public static final int MIN_MINUTES = 10, MAX_MINUTES = 240, DEFAULT_MINUTES = 60, VANILLA_MINUTES = 20;

    private int minutes;
    /** Ticks' worth of world time not yet moved on, in units × ticks: always 0 ≤ acc < ticksPerDay. */
    private long acc;

    public WorldClock(int minutes) {
        this.minutes = clampMinutes(minutes);
    }

    public static int clampMinutes(int minutes) {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
    }

    public static long ticksPerDay(int minutes) {
        return clampMinutes(minutes) * TICKS_PER_MINUTE;
    }

    public int minutes() { return minutes; }

    public long ticksPerDay() { return ticksPerDay(minutes); }

    public long acc() { return acc; }

    /** One tick with the daylight cycle on: the whole world time units the clock moves on (0 or 1 at 20 minutes or longer). */
    public long step() {
        long perDay = ticksPerDay();
        acc += UNITS_PER_DAY;
        long units = acc / perDay;
        acc -= units * perDay;
        return units;
    }

    /** A jump (a command, sleep): the clock starts the new time on a whole unit. */
    public void reset() {
        acc = 0;
    }

    /** From a save: a length out of range is clamped, an accumulator out of range starts over. */
    public void set(int minutes, long acc) {
        this.minutes = clampMinutes(minutes);
        this.acc = acc >= 0 && acc < ticksPerDay() ? acc : 0;
    }

    /** A new day length: the share of a unit already gathered carries over. */
    public void changeMinutes(int newMinutes) {
        long oldPerDay = ticksPerDay();
        minutes = clampMinutes(newMinutes);
        long perDay = ticksPerDay();
        acc = Math.min(perDay - 1, acc * perDay / oldPerDay);
    }
}
