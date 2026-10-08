package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.energy.BuildingLights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A light on a building's lighting circuit (Building Lights V1, docs/models/building_lights_v1.md): LIT while its
 * building's Distribution Panel feeds the lighting circuit ({@link BuildingLights}). No block entity: the block checks
 * itself on a scheduled tick every {@link BuildingLights#PERIOD} ticks. Placing it schedules the first check; a light that
 * has none (placed by worldgen, or before Building Lights V1) gets one from a random tick, within about a minute.
 * <p>
 * What kind of check a tick is lives in its game time modulo 4, so the block needs no extra state: ordinary checks fall on
 * phase 0, the second look after an UNKNOWN (no panel registered yet) on phase 1, the first check of a light found by a
 * random tick on phase 2.
 * <p>
 * Old light data: the light before Building Lights V1 always emitted 14, and a world saved then keeps that light in its
 * chunks; the block emits nothing now, but Minecraft does not relight loaded chunks, and breaking such lights leaves
 * patches where equal levels hold each other up (user 2026-10-08: "灯都拆了还是亮的"). A dark light found by a random
 * tick that still sits in 14 or more block light is lit for one period: the light engine then replaces the old light with
 * this light's own, and takes it away cleanly when the next check turns it off.
 */
public abstract class BuildingLightBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final int ORDINARY = 0, SECOND_LOOK = 1, FOUND = 2, STALE_LIGHT = 14;

    protected BuildingLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide() && !old.is(this)) schedule(level, pos, 2, ORDINARY);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) schedule(level, pos, 2, FOUND);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int phase = (int) (level.getGameTime() & 3L);
        boolean lit = state.getValue(LIT), next, retry = false;
        if (phase == FOUND && !lit && level.getBrightness(LightLayer.BLOCK, pos) >= STALE_LIGHT) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
            schedule(level, pos, BuildingLights.PERIOD, ORDINARY);
            return;
        }
        switch (BuildingLights.period(level, pos, lit)) {
            case LIT -> next = true;
            case DARK -> next = false;
            default -> { next = lit && phase != SECOND_LOOK; retry = next; }   // a lit light waits once for its panel
        }
        if (next != lit) level.setBlock(pos, state.setValue(LIT, next), Block.UPDATE_CLIENTS);
        schedule(level, pos, retry ? BuildingLights.GRACE : BuildingLights.PERIOD, retry ? SECOND_LOOK : ORDINARY);
    }

    /** Schedules this light's next check about {@code delay} ticks ahead, on a game tick of the given phase (mod 4). */
    private void schedule(Level level, BlockPos pos, int delay, int phase) {
        delay += Math.floorMod(phase - (level.getGameTime() + delay), 4);
        level.scheduleTick(pos, this, delay);
    }
}
