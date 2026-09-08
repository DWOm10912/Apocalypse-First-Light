package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

public final class StaticWorkstationBlockItem extends BlockItem {
    public StaticWorkstationBlockItem(StaticWorkstationBlock block, Properties properties) { super(block, properties); }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((StaticWorkstationBlock)getBlock()).placeStructure(context, state);
    }
}
