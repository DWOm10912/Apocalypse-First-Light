package com.antaurora.apofirstlight.time;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 4): the client's clock between the server's time packets (once a
 * second, which still set the time). Its day length is the server's (WorldTimePacket at login and on a change). No client
 * classes here, so AflTime can read it on either side.
 */
public final class WorldTimeClientState {
    private WorldTimeClientState() {}

    private static final WorldClock CLOCK = new WorldClock(WorldClock.DEFAULT_MINUTES);

    public static int minutes() { return CLOCK.minutes(); }

    public static long acc() { return CLOCK.acc(); }

    /** The ClientLevel#tickTime step: vanilla's next time (time + 1) becomes the time plus this tick's whole units. */
    public static long advance(long next) {
        return next - 1 + CLOCK.step();
    }

    /** From the server. */
    public static void apply(int minutes) {
        if (minutes != CLOCK.minutes()) CLOCK.changeMinutes(minutes);
    }

    /** Leaving a world: back to the default until the next server says. */
    public static void reset() {
        CLOCK.set(WorldClock.DEFAULT_MINUTES, 0);
    }
}
