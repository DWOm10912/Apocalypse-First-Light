package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.EmergencyLightBlock;
import com.antaurora.apofirstlight.energy.BuildingLights;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emergency Light (Building Lights V1, docs/models/building_lights_v1.md): the battery. Every {@link #CHECK} ticks:
 * <ul>
 * <li>its building's lighting circuit has power ({@link BuildingLights}): MODE charging, the battery takes 1 FE/t from the
 * circuit until full ({@link #CAPACITY} = 10 min of charging);</li>
 * <li>a panel serves it but the circuit is dead (main off, branch off, empty buffer, tripped), or no panel answers for
 * {@link #GRACE_CHECKS} checks running (no building, or the panel gone): the heads run on the battery, 2 units a tick
 * (a full battery lasts 5 min, 6 in-game hours), then MODE off.</li>
 * </ul>
 * A new unit, and one in a building that has had no power since the war, starts flat. The charge is saved, and lost when
 * the unit is broken. (The test button, 3 s on the battery, was taken out on 2026-10-08 at the user's request.)
 */
public class EmergencyLightBlockEntity extends BlockEntity {
    public static final int CAPACITY = 12000, CHARGE = 1, DRAIN = 2, CHECK = 20, GRACE_CHECKS = 6;
    private static final String BATTERY_KEY = "Battery";
    private int battery;
    private int misses;

    public EmergencyLightBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.EMERGENCY_LIGHT.get(), pos, state);
    }

    /** Charge, 0..100 %. */
    public int percent() { return Math.round(100F * battery / CAPACITY); }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        // every CHECK ticks, spread over the units
        if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), CHECK) != 0) return;
        check();
    }

    private void check() {
        EmergencyLightBlock.Mode mode = getBlockState().getValue(EmergencyLightBlock.MODE), next;
        DistributionPanelBlockEntity panel = BuildingLights.panel(level, worldPosition);
        misses = panel != null ? 0 : Math.min(GRACE_CHECKS, misses + 1);
        if (panel != null && panel.lightingLive()) {
            int room = CAPACITY - battery;
            if (room > 0) { battery += panel.drawLighting(Math.min(room, CHECK * CHARGE), false); setChanged(); }
            next = EmergencyLightBlock.Mode.CHARGING;
        } else {
            if (mode == EmergencyLightBlock.Mode.ON) { battery = Math.max(0, battery - CHECK * DRAIN); setChanged(); }
            // no answer yet (a chunk just loaded, the panel has not registered): keep showing what it showed
            boolean outage = panel != null || misses >= GRACE_CHECKS;
            next = !outage ? (mode == EmergencyLightBlock.Mode.ON && battery == 0 ? EmergencyLightBlock.Mode.OFF : mode)
                    : battery > 0 ? EmergencyLightBlock.Mode.ON : EmergencyLightBlock.Mode.OFF;
        }
        setMode(next);
    }

    private void setMode(EmergencyLightBlock.Mode mode) {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(EmergencyLightBlock.MODE) && state.getValue(EmergencyLightBlock.MODE) != mode)
            level.setBlock(worldPosition, state.setValue(EmergencyLightBlock.MODE, mode), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(BATTERY_KEY, battery);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        battery = Math.max(0, Math.min(CAPACITY, tag.getInt(BATTERY_KEY)));
    }
}
