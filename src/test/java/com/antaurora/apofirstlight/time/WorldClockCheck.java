package com.antaurora.apofirstlight.time;

/**
 * World Time V1 offline check (docs/gameplay/world_time_v1_design.md 5): the world clock's fixed-point step. Run with
 * {@code gradlew worldClockCheck}.
 * <ul>
 *   <li>Over 10⁷ ticks at each day length the clock has moved exactly floor(ticks × 24 000 / ticksPerDay) units, and a day's
 *   ticks are exactly 24 000 units.</li>
 *   <li>20 minutes is vanilla (one unit every tick); 60 minutes moves one unit on every third tick and none between.</li>
 *   <li>A new day length carries the share over and keeps the accumulator in range; a bad saved accumulator starts over.</li>
 * </ul>
 */
public final class WorldClockCheck {
    private static int failures;

    public static void main(String[] args) {
        int[] lengths = {10, 20, 40, 60, 90, 120, 240};
        for (int minutes : lengths) {
            WorldClock clock = new WorldClock(minutes);
            long perDay = clock.ticksPerDay(), ticks = 10_000_000L, units = 0, oneDay = 0;
            for (long t = 1; t <= ticks; t++) {
                long u = clock.step();
                units += u;
                if (t == perDay) oneDay = units;
                if (clock.acc() < 0 || clock.acc() >= perDay) fail(minutes + " min: accumulator out of range at tick " + t);
            }
            long expected = Math.floorDiv(ticks * WorldClock.UNITS_PER_DAY, perDay);
            check(units == expected, minutes + " min: " + units + " units after 1e7 ticks, expected " + expected);
            check(oneDay == WorldClock.UNITS_PER_DAY, minutes + " min: one day of ticks gave " + oneDay + " units");
            System.out.printf("%3d min: %d ticks a day, %d units in 1e7 ticks, 1 day = %d units%n", minutes, perDay, units, oneDay);
        }

        WorldClock vanilla = new WorldClock(WorldClock.VANILLA_MINUTES);
        for (int t = 0; t < 100; t++) check(vanilla.step() == 1, "20 min: tick " + t + " did not move one unit");

        WorldClock sixty = new WorldClock(60);
        for (int t = 1; t <= 300; t++) {
            long u = sixty.step();
            check(u == (t % 3 == 0 ? 1 : 0), "60 min: tick " + t + " moved " + u);
        }

        WorldClock change = new WorldClock(60);
        change.step();                                   // a third of a unit gathered
        change.changeMinutes(120);
        check(change.acc() == WorldClock.UNITS_PER_DAY * 2, "60 -> 120 min: share " + change.acc() + ", expected a third of a unit");
        change.changeMinutes(10);
        check(change.acc() >= 0 && change.acc() < change.ticksPerDay(), "120 -> 10 min: accumulator out of range " + change.acc());
        change.changeMinutes(1_000);
        check(change.minutes() == WorldClock.MAX_MINUTES, "1000 min not clamped: " + change.minutes());

        WorldClock saved = new WorldClock(60);
        saved.set(60, 999_999_999L);
        check(saved.acc() == 0, "a bad saved accumulator was kept: " + saved.acc());
        saved.set(0, 5);
        check(saved.minutes() == WorldClock.MIN_MINUTES, "a saved length of 0 not clamped: " + saved.minutes());

        // the 24-hour clock (ClockTime): vanilla's 0 is 06:00, the date turns at midnight (time 18 000)
        checkClock(0, 0, 0, "06:00:00");
        checkClock(6_000, 0, 0, "12:00:00");
        checkClock(12_000, 0, 0, "18:00:00");
        checkClock(17_999, 0, 0, "23:59:56");
        checkClock(18_000, 0, 1, "00:00:00");
        checkClock(-6_000, 0, 0, "00:00:00");
        checkClock(-6_001, 0, -1, "23:59:56");
        checkClock(24_000 * 5 + 1_000, 0, 5, "07:00:00");
        checkClock(17_999, 0.5, 0, "23:59:58");
        checkClock(17_999, 1.5, 1, "00:00:01");        // a client's share past the next unit carries into the next date
        for (int hm = 0; hm < 24 * 60; hm++) {
            int h = hm / 60, m = hm % 60;
            ClockTime at = ClockTime.at(ClockTime.timeOfDayAt(h, m), 0);
            check(at.hour() == h && at.minute() == m, "timeOfDayAt " + h + ":" + m + " reads " + at.hhmmss());
            long t = ClockTime.dayTimeAt(7, h, m);
            ClockTime on = ClockTime.at(t, 0);
            check(on.date() == 7 && on.hour() == h && on.minute() == m, "dayTimeAt 7 " + h + ":" + m + " reads date " + on.date() + " " + on.hhmmss());
            check(Math.floorMod(t, WorldClock.UNITS_PER_DAY) == ClockTime.timeOfDayAt(h, m), "dayTimeAt and timeOfDayAt disagree at " + h + ":" + m);
        }
        double last = -1;
        for (int step = 0; step <= 3_000; step++) {      // a second hand across one unit and a date change never goes back
            ClockTime c = ClockTime.at(17_999 + step / 1_000, (step % 1_000) / 1_000.0);
            double s = c.date() * (double) ClockTime.SECONDS_PER_DAY + c.secondOfDay();
            check(s > last, "the clock went back at step " + step);
            last = s;
        }
        System.out.println("24-hour clock: 06:00 at time 0, date turns at 18000, all 1440 minutes round-trip");

        if (failures > 0) {
            System.out.println("World clock check FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("World clock check passed");
    }

    private static void checkClock(long dayTime, double fraction, long date, String hhmmss) {
        ClockTime c = ClockTime.at(dayTime, fraction);
        check(c.date() == date && c.hhmmss().equals(hhmmss),
                "time " + dayTime + " + " + fraction + " reads date " + c.date() + " " + c.hhmmss() + ", expected date " + date + " " + hhmmss);
    }

    private static void check(boolean ok, String message) {
        if (!ok) fail(message);
    }

    private static void fail(String message) {
        if (failures++ < 20) System.out.println("FAIL " + message);
    }
}
