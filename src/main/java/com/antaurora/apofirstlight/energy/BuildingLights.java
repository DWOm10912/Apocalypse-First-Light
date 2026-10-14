package com.antaurora.apofirstlight.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Building Lights V1 (docs/models/building_lights_v1.md): the lights on a building's lighting circuit, over its hidden
 * wiring. A light (the square panel light, the linear light) asks every {@link #PERIOD} ticks whether the Distribution Panel
 * of its building feeds the lighting circuit and takes its energy for the coming period in one draw: {@link #FE_PER_TICK}
 * FE per tick for the LED fixtures, the bulb's real wattage for an incandescent lamp (2026-10-13: the 60 W porcelain
 * lampholder, {@link #BULB_60W_PERIOD} FE a period; 1 FE/t = 0.25 kW).
 * As the fuel canopy lights, a dark light only comes on when the panel can give two periods, so a weak supply does not
 * make the lights flicker. The emergency light charges from the same circuit.
 * <p>
 * The source ({@link BuildingSupply}, Building Wiring V1 phase 2): a WIRED panel's circuit whose wire taps the light, else a
 * LEGACY_AREA panel's lighting circuit by the building footprint. Over wires the light also follows its tap's group (phase
 * 3): a wall switch's light group in the panel, or the photocell (dark by day); a light switched off takes nothing.
 * <p>
 * {@link Check#UNKNOWN}: no panel serves the position. That is the answer for a light outside any building, but also
 * for one whose panel has not registered its building yet (right after a chunk loads; a panel recomputes its building
 * every 5 s), so a lit light waits {@link #GRACE} ticks for a second answer before it goes dark.
 */
public final class BuildingLights {
    public static final int PERIOD = 40, FE_PER_TICK = 1, GRACE = 120;
    /** A 60 W bulb over one period: 0.24 FE/t x 40 ticks = 9.6, rounded. */
    public static final int BULB_60W_PERIOD = 10;

    public enum Check { LIT, DARK, UNKNOWN }

    private BuildingLights() {}

    /**
     * One period of the light at {@code device} (footprint lookup at {@code legacy}) that takes {@code need} FE a period: LIT
     * (energy for the period drawn), DARK, or UNKNOWN (no panel serves it).
     */
    public static Check period(Level level, BlockPos device, BlockPos legacy, boolean litNow, int need) {
        BuildingSupply.Source source = BuildingSupply.lighting(level, device, legacy);
        if (source == null) return Check.UNKNOWN;
        if (!source.groupOn(level)) return Check.DARK;   // its light group switched off, or the photocell by day: drawing nothing
        if (!source.live()) return Check.DARK;
        if (source.draw(litNow ? need : 2 * need, true) < (litNow ? need : 2 * need)) return Check.DARK;
        source.draw(need, false);
        return Check.LIT;
    }
}
