package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Static, bottom-aligned road surface. One layer is exactly 1/16 block in both model and collision. */
public class RoadSurfaceBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        for (int layer = 1; layer <= 16; layer++) SHAPES[layer - 1] = Block.box(0, 0, 0, 16, layer, 16);
    }

    public RoadSurfaceBlock(Properties properties, int defaultLayers) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, defaultLayers));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }
}
