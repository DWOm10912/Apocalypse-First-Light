package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.RectMultiblockBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places a rectangular multi-cell block (RectMultiblockBlock) whole, as one BlockItem operation. */
public final class RectMultiblockBlockItem extends BlockItem {
    public RectMultiblockBlockItem(RectMultiblockBlock<?> block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((RectMultiblockBlock<?>) getBlock()).placeStructure(context, state);
    }
}
