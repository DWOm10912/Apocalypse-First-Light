package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.FuelDispenserSumpBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places a dispenser sump's four cells as one BlockItem operation (FuelDispenserSumpBlock#placeStructure). */
public final class FuelDispenserSumpItem extends BlockItem {
    public FuelDispenserSumpItem(FuelDispenserSumpBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((FuelDispenserSumpBlock) getBlock()).placeStructure(context);
    }
}
