package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thin, axis-rotatable structural steel beam.
 *
 * The collision/selection shape follows the 4x4 px beam envelope while the
 * exported model supplies the recessed faces and connection details.
 */
public final class SteelBeamBlock extends RotatedPillarBlock {
    private static final VoxelShape X_SHAPE = Block.box(0, 6, 6, 16, 10, 10);
    private static final VoxelShape Y_SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 6, 0, 10, 10, 16);

    public SteelBeamBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForAxis(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shapeForAxis(state);
    }

    private static VoxelShape shapeForAxis(BlockState state) {
        return switch (state.getValue(BlockStateProperties.AXIS)) {
            case X -> X_SHAPE;
            case Z -> Z_SHAPE;
            case Y -> Y_SHAPE;
        };
    }
}
