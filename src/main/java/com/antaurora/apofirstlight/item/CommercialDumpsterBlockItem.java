package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.CommercialDumpsterBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places the dumpster's two structural cells as one BlockItem operation. */
public final class CommercialDumpsterBlockItem extends BlockItem {
    public CommercialDumpsterBlockItem(CommercialDumpsterBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((CommercialDumpsterBlock) getBlock()).placeStructure(context, state);
    }
}
