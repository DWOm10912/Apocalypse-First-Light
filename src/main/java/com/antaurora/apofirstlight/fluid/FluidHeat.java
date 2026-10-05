package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * Hot liquids and the devices that cannot take them (user, 2026-10-05; docs/models/heat_resistant_fluid_set_v1.md). A
 * liquid hotter than {@link #MAX_COLD_K} (lava, 1300 K) is hot. The ordinary Fluid Pipe V2, Intake Pump and Fluid Tank V2
 * (tempered glass, rubber gaskets) still take it, but it melts them: the block goes, with no drop, with the hiss and smoke
 * of lava meeting water. Only the heat-resistant set (quartz glass, refractory ceramic lining) carries hot liquids.
 */
public final class FluidHeat {
    public static final int MAX_COLD_K = 400;

    private FluidHeat() {
    }

    public static boolean isHot(Fluid fluid) {
        return fluid != Fluids.EMPTY && fluid.getFluidType().getTemperature() > MAX_COLD_K;
    }

    public static boolean isHot(FluidStack fluid) {
        return !fluid.isEmpty() && isHot(fluid.getFluid());
    }

    /** A device block that a hot liquid reached: gone, no drop; its multiblock partners follow through their own removal. */
    public static void melt(ServerLevel level, BlockPos position) {
        if (level.getBlockState(position).isAir()) return;
        level.levelEvent(1501, position, 0);   // LevelEvent.LAVA_FIZZ: fizz and smoke
        level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
