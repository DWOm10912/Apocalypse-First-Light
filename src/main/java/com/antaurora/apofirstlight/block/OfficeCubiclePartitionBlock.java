package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight four-way connecting office partition. Every position remains an
 * independent block; only its baked model and cached collision shape change.
 */
public final class OfficeCubiclePartitionBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final int NORTH_MASK = 1;
    private static final int SOUTH_MASK = 1 << 1;
    private static final int EAST_MASK = 1 << 2;
    private static final int WEST_MASK = 1 << 3;
    private static final VoxelShape[] SHAPES = buildShapes();

    public OfficeCubiclePartitionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos position = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(property(direction), connectsTo(context.getLevel().getBlockState(position.relative(direction))));
        }
        return state.canSurvive(context.getLevel(), position) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        BlockPos floor = position.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        BlockPos upper = position.above();
        return level.getBlockState(upper).getCollisionShape(level, upper).isEmpty();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(property(direction), connectsTo(neighborState));
        }
        if ((direction == Direction.UP || direction == Direction.DOWN) && !state.canSurvive(level, position)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, position, neighborPosition);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES[connectionMask(state)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return getShape(state, level, position, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            rotated = rotated.setValue(property(rotation.rotate(direction)), state.getValue(property(direction)));
        }
        return rotated;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    public static int connectionMask(BlockState state) {
        int mask = 0;
        if (state.getValue(NORTH)) mask |= NORTH_MASK;
        if (state.getValue(SOUTH)) mask |= SOUTH_MASK;
        if (state.getValue(EAST)) mask |= EAST_MASK;
        if (state.getValue(WEST)) mask |= WEST_MASK;
        return mask;
    }

    private boolean connectsTo(BlockState state) {
        return state.is(this);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Office partitions only connect horizontally: " + direction);
        };
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int mask = 0; mask < shapes.length; mask++) {
            shapes[mask] = shapeFor(mask).optimize();
        }
        return shapes;
    }

    private static VoxelShape shapeFor(int mask) {
        int count = Integer.bitCount(mask);
        if (count == 0) {
            return Shapes.or(eastWestPanel(), westEndPost(), eastEndPost());
        }
        if (count == 1) {
            if ((mask & EAST_MASK) != 0) return Shapes.or(eastWestPanel(), westEndPost());
            if ((mask & WEST_MASK) != 0) return Shapes.or(eastWestPanel(), eastEndPost());
            if ((mask & NORTH_MASK) != 0) return Shapes.or(northSouthPanel(), southEndPost());
            return Shapes.or(northSouthPanel(), northEndPost());
        }
        if (mask == (EAST_MASK | WEST_MASK)) return eastWestPanel();
        if (mask == (NORTH_MASK | SOUTH_MASK)) return northSouthPanel();

        VoxelShape shape = centerPost();
        if ((mask & NORTH_MASK) != 0) shape = Shapes.or(shape, Block.box(7.1D, 3.2D, 0.0D, 8.9D, 32.0D, 7.1D));
        if ((mask & SOUTH_MASK) != 0) shape = Shapes.or(shape, Block.box(7.1D, 3.2D, 8.9D, 8.9D, 32.0D, 16.0D));
        if ((mask & EAST_MASK) != 0) shape = Shapes.or(shape, Block.box(8.9D, 3.2D, 7.1D, 16.0D, 32.0D, 8.9D));
        if ((mask & WEST_MASK) != 0) shape = Shapes.or(shape, Block.box(0.0D, 3.2D, 7.1D, 7.1D, 32.0D, 8.9D));
        return shape;
    }

    private static VoxelShape eastWestPanel() {
        return Block.box(0.0D, 2.3D, 7.1D, 16.0D, 32.0D, 8.9D);
    }

    private static VoxelShape northSouthPanel() {
        return Block.box(7.1D, 2.3D, 0.0D, 8.9D, 32.0D, 16.0D);
    }

    private static VoxelShape westEndPost() {
        return Shapes.or(
                Block.box(0.0D, 0.0D, 6.7D, 1.8D, 0.55D, 9.3D),
                Block.box(0.2D, 0.55D, 7.05D, 1.3D, 32.0D, 8.95D));
    }

    private static VoxelShape eastEndPost() {
        return Shapes.or(
                Block.box(14.2D, 0.0D, 6.7D, 16.0D, 0.55D, 9.3D),
                Block.box(14.7D, 0.55D, 7.05D, 15.8D, 32.0D, 8.95D));
    }

    private static VoxelShape northEndPost() {
        return Shapes.or(
                Block.box(6.7D, 0.0D, 0.0D, 9.3D, 0.55D, 1.8D),
                Block.box(7.05D, 0.55D, 0.2D, 8.95D, 32.0D, 1.3D));
    }

    private static VoxelShape southEndPost() {
        return Shapes.or(
                Block.box(6.7D, 0.0D, 14.2D, 9.3D, 0.55D, 16.0D),
                Block.box(7.05D, 0.55D, 14.7D, 8.95D, 32.0D, 15.8D));
    }

    private static VoxelShape centerPost() {
        return Shapes.or(
                Block.box(6.95D, 0.0D, 6.95D, 9.05D, 0.55D, 9.05D),
                Block.box(7.1D, 0.55D, 7.1D, 8.9D, 32.0D, 8.9D));
    }
}
