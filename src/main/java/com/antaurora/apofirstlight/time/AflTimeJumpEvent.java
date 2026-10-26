package com.antaurora.apofirstlight.time;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 5): the overworld's world time was set, not stepped: sleep, /time,
 * or another mod. Posted on the Forge bus on the server thread after the new time is in place. It only tells: nothing is
 * fast-forwarded for anyone (user 2026-10-24: sleep changes world time only); a system that settles by the calendar does its
 * own settling from {@link #from} and {@link #to}.
 */
public class AflTimeJumpEvent extends Event {
    public enum Cause { SLEEP, COMMAND, OTHER }

    private final ServerLevel level;
    private final long from, to;
    private final Cause cause;

    public AflTimeJumpEvent(ServerLevel level, long from, long to, Cause cause) {
        this.level = level;
        this.from = from;
        this.to = to;
        this.cause = cause;
    }

    public ServerLevel level() { return level; }

    /** The world time before the jump. */
    public long from() { return from; }

    /** The world time after it (smaller than from after /time set to an earlier time). */
    public long to() { return to; }

    public Cause cause() { return cause; }
}
