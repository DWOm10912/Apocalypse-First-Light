package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AxisSteelStructureBlock extends RotatedPillarBlock {
    public static final VoxelShape Y_SHAPE = Block.box(6.5D, 0.0D, 6.5D, 9.5D, 16.0D, 9.5D);
    public static final VoxelShape X_SHAPE = Block.box(0.0D, 6.5D, 6.5D, 16.0D, 9.5D, 9.5D);
    public static final VoxelShape Z_SHAPE = Block.box(6.5D, 6.5D, 0.0D, 9.5D, 9.5D, 16.0D);

    public AxisSteelStructureBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForAxis(state.getValue(AXIS));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForAxis(state.getValue(AXIS));
    }

    private static VoxelShape shapeForAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> X_SHAPE;
            case Z -> Z_SHAPE;
            default -> Y_SHAPE;
        };
    }
}
