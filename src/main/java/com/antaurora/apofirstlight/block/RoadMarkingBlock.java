package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RoadMarkingBlock extends HorizontalDirectionalBlock {
    public enum MarkingType {
        EDGE,
        DIVIDER
    }

    private static final VoxelShape EDGE_EAST_SHAPE = Block.box(14.0, 0.0, 0.0,
            16.0, 0.75, 16.0);
    private static final VoxelShape EDGE_SOUTH_SHAPE = rotateY90(EDGE_EAST_SHAPE);
    private static final VoxelShape EDGE_WEST_SHAPE = rotateY90(EDGE_SOUTH_SHAPE);
    private static final VoxelShape EDGE_NORTH_SHAPE = rotateY90(EDGE_WEST_SHAPE);

    private static final VoxelShape DIVIDER_CANONICAL_SHAPE = Shapes.or(
            Block.box(7.0, 0.0, 2.0, 9.0, 0.75, 6.0),
            Block.box(7.0, 0.0, 10.0, 9.0, 0.75, 14.0));
    private static final VoxelShape DIVIDER_TURNED_SHAPE = rotateY90(DIVIDER_CANONICAL_SHAPE);

    private final MarkingType markingType;

    public RoadMarkingBlock(Properties properties, MarkingType markingType) {
        super(properties);
        this.markingType = markingType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (markingType == MarkingType.DIVIDER) {
            return switch (state.getValue(FACING)) {
                case EAST, WEST -> DIVIDER_TURNED_SHAPE;
                case NORTH, SOUTH -> DIVIDER_CANONICAL_SHAPE;
                default -> DIVIDER_CANONICAL_SHAPE;
            };
        }
        return switch (state.getValue(FACING)) {
            case EAST -> EDGE_EAST_SHAPE;
            case SOUTH -> EDGE_SOUTH_SHAPE;
            case WEST -> EDGE_WEST_SHAPE;
            case NORTH -> EDGE_NORTH_SHAPE;
            default -> EDGE_EAST_SHAPE;
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
