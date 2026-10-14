package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Which Distribution Panel powers a building device (Building Wiring V1 phase 2, docs/gameplay/building_wiring_v1_design.md
 * 5): the hidden wires of a WIRED panel first ({@link WiringSupply}), else the old building footprint of a LEGACY_AREA panel
 * ({@link BuildingPowerZone}). A WIRED panel never serves by footprint, and a LEGACY_AREA panel never over wires, so a device
 * has one source at most. Lights, the emergency light, the wall outlets and the service entries all ask here.
 */
public final class BuildingSupply {
    private BuildingSupply() {}

    /**
     * A device's source: the panel and, over wires, the circuit (1-4) and the group the device follows (phase 3: 0 always on,
     * 1-8 a light group, 15 the photocell); legacy: the lighting or the outlet circuit, no groups.
     */
    public record Source(DistributionPanelBlockEntity panel, int circuit, int group, boolean outlets) {
        public boolean live() {
            return circuit > 0 ? panel.circuitLive(circuit) : outlets ? panel.outletsLive() : panel.lightingLive();
        }

        /** The device's group lets it on (lights; design 6): always on, the light group switched on, the photocell by night. */
        public boolean groupOn(Level level) {
            if (circuit <= 0 || outlets || group == WiringNode.ALWAYS) return true;
            if (group == WiringNode.PHOTOCELL) return !level.isDay();
            return panel.groupOn(circuit, group);
        }

        public int draw(int fe, boolean simulate) {
            return circuit > 0 ? panel.drawCircuit(circuit, fe, simulate) : outlets ? panel.drawOutlets(fe, simulate) : panel.drawLighting(fe, simulate);
        }
    }

    /**
     * The light at {@code device}: over wires by its own cell (its tap), else the footprint at {@code legacy} (a fixture on an
     * outside wall gives the wall it hangs on).
     */
    @Nullable
    public static Source lighting(Level level, BlockPos device, BlockPos legacy) {
        return source(level, device, legacy, false);
    }

    /** The wall outlet at {@code outlet}. */
    @Nullable
    public static Source outlet(Level level, BlockPos outlet) {
        return source(level, outlet, outlet, true);
    }

    /** The panel a service entry (inlet box, meter box) feeds: over its feeder, else the footprint at {@code wall}. */
    @Nullable
    public static BlockPos entryPanel(Level level, BlockPos entry, BlockPos wall) {
        BlockPos wired = WiringSupply.entryPanel(level, entry);
        if (wired != null && panel(level, wired, true) != null) return wired;
        BlockPos legacy = BuildingPowerZone.panelServing(level, wall);
        return legacy != null && panel(level, legacy, false) != null ? legacy : null;
    }

    @Nullable
    private static Source source(Level level, BlockPos device, BlockPos legacy, boolean outlets) {
        WiringSupply.Feed feed = WiringSupply.feed(level, device);
        if (feed != null) {
            DistributionPanelBlockEntity p = panel(level, feed.panel(), true);
            if (p != null) return new Source(p, feed.circuit(), feed.group(), outlets);
        }
        BlockPos at = BuildingPowerZone.panelServing(level, legacy);
        DistributionPanelBlockEntity p = at == null ? null : panel(level, at, false);
        return p == null ? null : new Source(p, 0, WiringNode.ALWAYS, outlets);
    }

    /** The loaded panel at {@code pos} in the given mode, or null. */
    @Nullable
    private static DistributionPanelBlockEntity panel(Level level, BlockPos pos, boolean wired) {
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof DistributionPanelBlockEntity p && p.wired() == wired ? p : null;
    }
}
