package com.antaurora.apofirstlight.block;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class StrippableRotatedPillarBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> strippedBlock;

    public StrippableRotatedPillarBlock(Properties properties, Supplier<? extends Block> strippedBlock) {
        super(properties);
        this.strippedBlock = strippedBlock;
    }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context,
                                           ToolAction toolAction, boolean simulate) {
        if (toolAction == ToolActions.AXE_STRIP) {
            return strippedBlock.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }
}
