package com.antaurora.apofirstlight.time;

import java.util.Locale;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 14): the world time on a 24-hour clock, for watches, clocks and
 * readouts. Vanilla's time 0 is 06:00 and its day count turns at 06:00; here the {@link #date} turns at midnight (time
 * 18 000), as a watch's would. A world time unit is 3.6 clock seconds, so a caller that draws a second hand passes the share
 * of a unit the clock has gathered (AflTime#clock(Level, float) does it on the client). Pure (no game classes).
 *
 * @param date        whole days since the world's first midnight-to-midnight day (0 from time -6 000, i.e. 00:00 of day 0)
 * @param secondOfDay clock seconds since midnight, 0 to 86 400, with the fraction
 */
public record ClockTime(long date, double secondOfDay) {
    public static final int SECONDS_PER_DAY = 86_400;
    /** Units from midnight to vanilla's time 0 (06:00). */
    public static final long MIDNIGHT_OFFSET = 6_000;

    /** The clock at world time {@code dayTime} plus {@code fraction} of a unit (0 for the whole-unit time). */
    public static ClockTime at(long dayTime, double fraction) {
        long base = dayTime + MIDNIGHT_OFFSET;
        long date = Math.floorDiv(base, WorldClock.UNITS_PER_DAY);
        double units = Math.floorMod(base, WorldClock.UNITS_PER_DAY) + fraction;
        while (units >= WorldClock.UNITS_PER_DAY) { units -= WorldClock.UNITS_PER_DAY; date++; }
        while (units < 0) { units += WorldClock.UNITS_PER_DAY; date--; }
        return new ClockTime(date, units * SECONDS_PER_DAY / WorldClock.UNITS_PER_DAY);
    }

    /**
     * The time of day in units (0 to 23 999, vanilla's: 0 is 06:00) at which the clock first reads {@code hour:minute}; a
     * unit is 3.6 s, so it is the first unit whose clock time is at or after the whole minute.
     */
    public static int timeOfDayAt(int hour, int minute) {
        int minuteOfDay = Math.floorMod(hour * 60 + minute, 24 * 60);
        long fromMidnight = (minuteOfDay * 50L + 2) / 3;            // ceil(minute × 1000 / 60)
        return (int) Math.floorMod(fromMidnight - MIDNIGHT_OFFSET, WorldClock.UNITS_PER_DAY);
    }

    /** The world time of {@code hour:minute} on clock date {@code date}. */
    public static long dayTimeAt(long date, int hour, int minute) {
        int minuteOfDay = Math.floorMod(hour * 60 + minute, 24 * 60);
        return date * WorldClock.UNITS_PER_DAY - MIDNIGHT_OFFSET + (minuteOfDay * 50L + 2) / 3;
    }

    public int hour() { return (int) (secondOfDay / 3600); }

    public int minute() { return (int) (secondOfDay / 60) % 60; }

    public int second() { return (int) secondOfDay % 60; }

    /** Hours since midnight with the fraction, 0 to 24 (an hour hand). */
    public double hours() { return secondOfDay / 3600; }

    /** "HH:MM". */
    public String hhmm() {
        return String.format(Locale.ROOT, "%02d:%02d", hour(), minute());
    }

    /** "HH:MM:SS". */
    public String hhmmss() {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hour(), minute(), second());
    }
}
