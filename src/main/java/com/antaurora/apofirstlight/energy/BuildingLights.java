package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Building Lights V1 (docs/models/building_lights_v1.md): the lights on a building's lighting circuit, over its hidden
 * wiring. A light (the square panel light, the linear light) asks every {@link #PERIOD} ticks whether the Distribution Panel
 * of its building feeds the lighting circuit and takes {@link #FE_PER_TICK} FE per tick for the coming period in one draw.
 * As the fuel canopy lights, a dark light only comes on when the panel can give two periods, so a weak supply does not
 * make the lights flicker. The emergency light charges from the same circuit.
 * <p>
 * {@link Check#UNKNOWN}: no panel serves the position. That is the answer for a light outside any building, but also
 * for one whose panel has not registered its building yet (right after a chunk loads; a panel recomputes its building
 * every 5 s), so a lit light waits {@link #GRACE} ticks for a second answer before it goes dark.
 */
public final class BuildingLights {
    public static final int PERIOD = 40, FE_PER_TICK = 1, GRACE = 120;

    public enum Check { LIT, DARK, UNKNOWN }

    private BuildingLights() {}

    /** The active panel whose building holds {@code pos}, or null. */
    @Nullable
    public static DistributionPanelBlockEntity panel(Level level, BlockPos pos) {
        BlockPos panel = BuildingPowerZone.panelServing(level, pos);
        if (panel == null || !level.isLoaded(panel)) return null;
        return level.getBlockEntity(panel) instanceof DistributionPanelBlockEntity p ? p : null;
    }

    /** One period of the light at {@code pos}: LIT (energy for the period drawn), DARK, or UNKNOWN (no panel serves it). */
    public static Check period(Level level, BlockPos pos, boolean litNow) {
        DistributionPanelBlockEntity panel = panel(level, pos);
        if (panel == null) return Check.UNKNOWN;
        if (!panel.lightingLive()) return Check.DARK;
        int need = PERIOD * FE_PER_TICK;
        if (panel.drawLighting(litNow ? need : 2 * need, true) < (litNow ? need : 2 * need)) return Check.DARK;
        panel.drawLighting(need, false);
        return Check.LIT;
    }
}
