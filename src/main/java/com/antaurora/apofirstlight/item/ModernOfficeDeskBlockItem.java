package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.ModernOfficeDeskBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Places all three office-desk cells in the normal BlockItem transaction. */
public final class ModernOfficeDeskBlockItem extends BlockItem {
    public ModernOfficeDeskBlockItem(ModernOfficeDeskBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((ModernOfficeDeskBlock) getBlock()).placeStructure(context, state);
    }
}
