package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.GunWorkbenchBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

public final class GunWorkbenchBlockItem extends BlockItem {
    public GunWorkbenchBlockItem(GunWorkbenchBlock block, Properties properties) { super(block, properties); }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((GunWorkbenchBlock)getBlock()).placeStructure(context, state);
    }
}
