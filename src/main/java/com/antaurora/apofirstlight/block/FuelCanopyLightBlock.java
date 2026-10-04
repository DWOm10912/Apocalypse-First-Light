package com.antaurora.apofirstlight.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Fuel Canopy Kit V1 ceiling light: a ceiling block with a flush square LED luminaire. LIT is set by its network's
 * controller (FuelCanopyColumnBlockEntity) while the network is powered; lit, the lens is emissive (LabPBR, full-bright
 * baked quads) and the block gives {@link #LIGHT_LEVEL}.
 */
public class FuelCanopyLightBlock extends FuelCanopyCeilingBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int LIGHT_LEVEL = 15;

    public FuelCanopyLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
