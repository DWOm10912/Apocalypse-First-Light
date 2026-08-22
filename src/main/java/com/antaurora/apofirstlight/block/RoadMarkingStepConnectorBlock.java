package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin, non-colliding paint placed against a one-block highway surface riser. */
public final class RoadMarkingStepConnectorBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty LEFT_SIDE = BooleanProperty.create("left_side");

    private static final VoxelShape LEFT_EAST_SHAPE = Block.box(
            15.0, -16.0, 0.0, 16.0, 0.0, 2.0);
    private static final VoxelShape LEFT_SOUTH_SHAPE = rotateY90(LEFT_EAST_SHAPE);
    private static final VoxelShape LEFT_WEST_SHAPE = rotateY90(LEFT_SOUTH_SHAPE);
    private static final VoxelShape LEFT_NORTH_SHAPE = rotateY90(LEFT_WEST_SHAPE);

    private static final VoxelShape RIGHT_EAST_SHAPE = Block.box(
            15.0, -16.0, 14.0, 16.0, 0.0, 16.0);
    private static final VoxelShape RIGHT_SOUTH_SHAPE = rotateY90(RIGHT_EAST_SHAPE);
    private static final VoxelShape RIGHT_WEST_SHAPE = rotateY90(RIGHT_SOUTH_SHAPE);
    private static final VoxelShape RIGHT_NORTH_SHAPE = rotateY90(RIGHT_WEST_SHAPE);

    public RoadMarkingStepConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.EAST)
                .setValue(LEFT_SIDE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, LEFT_SIDE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean leftSide = state.getValue(LEFT_SIDE);
        return switch (state.getValue(FACING)) {
            case EAST -> leftSide ? LEFT_EAST_SHAPE : RIGHT_EAST_SHAPE;
            case SOUTH -> leftSide ? LEFT_SOUTH_SHAPE : RIGHT_SOUTH_SHAPE;
            case WEST -> leftSide ? LEFT_WEST_SHAPE : RIGHT_WEST_SHAPE;
            case NORTH -> leftSide ? LEFT_NORTH_SHAPE : RIGHT_NORTH_SHAPE;
            default -> leftSide ? LEFT_EAST_SHAPE : RIGHT_EAST_SHAPE;
        };
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.empty();
    }

    private static VoxelShape rotateY90(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(
                rotated[0], Block.box((1.0 - maxZ) * 16.0, minY * 16.0, minX * 16.0,
                        (1.0 - minZ) * 16.0, maxY * 16.0, maxX * 16.0)));
        return rotated[0];
    }
}
