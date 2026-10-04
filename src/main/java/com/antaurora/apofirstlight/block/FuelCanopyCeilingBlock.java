package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fuel Canopy Kit V1 plain ceiling (tools/build-fuel-canopy-v1.mjs, docs/models/fuel_canopy_kit_v1.md): one block of the
 * canopy, a flat painted soffit below (neighbouring blocks join as one surface) and the roof above. Carries the canopy's
 * wiring (FuelCanopyNetwork).
 */
public class FuelCanopyCeilingBlock extends Block implements FuelCanopyNetwork.Part {
    public FuelCanopyCeilingBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) FuelCanopyNetwork.removed(level, pos);
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }
}
