package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.IntakePumpBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places an intake pump's two cells as one BlockItem operation (the bank cell at the clicked position). */
public final class IntakePumpItem extends BlockItem {
    public IntakePumpItem(IntakePumpBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((IntakePumpBlock) getBlock()).placeStructure(context);
    }
}
