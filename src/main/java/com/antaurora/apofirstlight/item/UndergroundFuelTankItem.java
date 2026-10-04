package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places an underground fuel tank's 63 cells as one BlockItem operation (from the bottom centre cell). */
public final class UndergroundFuelTankItem extends BlockItem {
    public UndergroundFuelTankItem(UndergroundFuelTankBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((UndergroundFuelTankBlock) getBlock()).placeStructure(context);
    }
}
