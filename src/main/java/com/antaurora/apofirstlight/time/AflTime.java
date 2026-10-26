package com.antaurora.apofirstlight.time;

import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 5): the one place other systems read the clock from, instead of
 * writing 24000 or 1000 themselves. Three kinds of time:
 * <ul>
 *   <li>World time: the sun and the calendar, {@link #dayTime} (vanilla's day time, 24 000 units a day), now
 *   {@link #dayMinutes} real minutes a day (60 by default).</li>
 *   <li>Simulation time: real ticks, {@link #gameTime} and {@link #SIM_DT}, never slowed.</li>
 *   <li>Gameplay time: each system's own rule; this class only converts (game hours to ticks and back).</li>
 * </ul>
 * The server decides the day length; a client uses the one the server sent (WorldTimePacket). A jump of the clock (sleep, a
 * command) is posted as {@link AflTimeJumpEvent}, a new day length as {@link AflTimeRateEvent}.
 */
public final class AflTime {
    private AflTime() {}

    public static final long UNITS_PER_DAY = WorldClock.UNITS_PER_DAY, UNITS_PER_HOUR = UNITS_PER_DAY / 24;
    /** Seconds of simulation in one tick. */
    public static final double SIM_DT = 0.05;

    /** Real minutes in a day: the running server's, or on a client without one (a dedicated server's) the one it was sent. */
    public static int dayMinutes() {
        return ServerLifecycleHooks.getCurrentServer() != null ? WorldTimeServer.minutes() : WorldTimeClientState.minutes();
    }

    /** The same for the side of this level. */
    public static int dayMinutes(Level level) {
        return level.isClientSide ? WorldTimeClientState.minutes() : WorldTimeServer.minutes();
    }

    /** Real ticks in a day (60 minutes: 72 000). */
    public static long ticksPerDay() {
        return WorldClock.ticksPerDay(dayMinutes());
    }

    /** World time units a tick (60 minutes: 1/3). For display and conversions only; the clock steps in whole units. */
    public static double rate() {
        return (double) UNITS_PER_DAY / ticksPerDay();
    }

    /** The world time (vanilla's getDayTime: days × 24 000 + the time of day). */
    public static long dayTime(Level level) {
        return level.getDayTime();
    }

    /** Whole days since the world's time 0 (vanilla's day count: it turns at 06:00; a calendar uses {@link #date}). */
    public static long day(Level level) {
        return Math.floorDiv(level.getDayTime(), UNITS_PER_DAY);
    }

    /** Units into the day: 0 sunrise, 6 000 noon, 12 000 sunset, 18 000 midnight. */
    public static int timeOfDay(Level level) {
        return (int) Math.floorMod(level.getDayTime(), UNITS_PER_DAY);
    }

    /** Share of the day gone, 0 to 1, from sunrise (time 0). */
    public static double dayProgress(Level level) {
        return timeOfDay(level) / (double) UNITS_PER_DAY;
    }

    /** The clock-face hour, 0 to 24 with 0 midnight (time 0 is 6:00). */
    public static double hourOfDay(Level level) {
        return (timeOfDay(level) / (double) UNITS_PER_HOUR + 6) % 24;
    }

    /** The clock date: whole days, turning at midnight (vanilla's {@link #day} turns at 06:00). */
    public static long date(Level level) {
        return Math.floorDiv(level.getDayTime() + ClockTime.MIDNIGHT_OFFSET, UNITS_PER_DAY);
    }

    /** The 24-hour clock now, to the whole unit (3.6 s). */
    public static ClockTime clock(Level level) {
        return ClockTime.at(level.getDayTime(), 0);
    }

    /**
     * The 24-hour clock this frame, smooth enough for a second hand: on the client the share of a unit its clock has gathered
     * plus the frame's partial tick (while the daylight cycle runs), so it moves on every frame and never back; on the
     * server the gathered share alone.
     */
    public static ClockTime clock(Level level, float partialTick) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) return clock(level);
        double fraction = level.isClientSide
                ? (WorldTimeClientState.acc() + partialTick * (double) UNITS_PER_DAY) / WorldClock.ticksPerDay(WorldTimeClientState.minutes())
                : (double) WorldTimeServer.acc() / WorldClock.ticksPerDay(WorldTimeServer.minutes());
        return ClockTime.at(level.getDayTime(), fraction);
    }

    /** Ticks the world has run (vanilla's getGameTime): simulation time, not slowed. */
    public static long gameTime(Level level) {
        return level.getGameTime();
    }

    /** Real ticks in a game hour (60 minutes: 3 000). */
    public static double ticksPerGameHour() {
        return ticksPerDay() / 24.0;
    }

    /** Real seconds in a game hour (60 minutes: 150). */
    public static double realSecondsPerGameHour() {
        return ticksPerGameHour() * SIM_DT;
    }

    public static double gameHoursToTicks(double gameHours) {
        return gameHours * ticksPerGameHour();
    }

    public static double ticksToGameHours(double ticks) {
        return ticks / ticksPerGameHour();
    }

    /** An amount a game hour as an amount a tick (rain, evaporation); a caller that needs whole units keeps its own carry. */
    public static double perTick(double perGameHour) {
        return perGameHour / ticksPerGameHour();
    }
}
