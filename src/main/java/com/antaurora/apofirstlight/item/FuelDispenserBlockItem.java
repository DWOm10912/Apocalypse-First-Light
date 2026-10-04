package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places the fuel dispenser's six cells as one BlockItem operation. */
public final class FuelDispenserBlockItem extends BlockItem {
    public FuelDispenserBlockItem(FuelDispenserBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((FuelDispenserBlock) getBlock()).placeStructure(context, state);
    }
}
