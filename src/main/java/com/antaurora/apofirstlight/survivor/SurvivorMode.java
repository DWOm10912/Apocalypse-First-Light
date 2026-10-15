package com.antaurora.apofirstlight.survivor;

import java.util.Locale;

/**
 * The AFL survival mode of a world (docs/gameplay/survivor_death_succession_v0.md 2). Separate from the vanilla
 * difficulty and from the vanilla hardcore flag (a vanilla hardcore world starts as HARD). Locked once set: nothing in
 * the game changes it afterwards, so a HARD run cannot be turned back into a NORMAL one after a death.
 * <ul>
 * <li>NORMAL: permadeath of the survivor; the world goes on with a random successor.</li>
 * <li>HARD: permadeath of the survivor and the end of the run; the world stays as a memorial.</li>
 * <li>UNSET: a fresh single-player world whose host has not chosen yet.</li>
 * </ul>
 */
public enum SurvivorMode {
    UNSET, NORMAL, HARD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SurvivorMode byId(String id) {
        for (SurvivorMode mode : values()) {
            if (mode.id().equals(id)) return mode;
        }
        return UNSET;
    }
}
