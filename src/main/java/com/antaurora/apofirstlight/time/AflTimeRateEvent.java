package com.antaurora.apofirstlight.time;

import net.minecraftforge.eventbus.api.Event;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 5): the server's day length changed (the config file saved while it
 * runs, or a world started with another length than it was saved at). Posted on the Forge bus on the server thread.
 */
public class AflTimeRateEvent extends Event {
    private final int oldMinutes, newMinutes;

    public AflTimeRateEvent(int oldMinutes, int newMinutes) {
        this.oldMinutes = oldMinutes;
        this.newMinutes = newMinutes;
    }

    public int oldMinutes() { return oldMinutes; }

    public int newMinutes() { return newMinutes; }
}
