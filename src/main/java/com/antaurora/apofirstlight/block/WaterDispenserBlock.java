package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Static two-block office prop. The lower half owns the rendered model and item drop. */
public final class WaterDispenserBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER_NORTH = Shapes.or(
            // Feet and plinth.
            Block.box(1.7, 0.0, 2.1, 14.3, 1.0, 13.9),
            // Main cabinet shell.
            Block.box(2.0, 1.0, 2.15, 14.0, 16.0, 13.75),
            // Front controls and drip-tray projection.
            Block.box(2.85, 9.45, 1.4, 13.15, 16.0, 2.15)
    ).optimize();
    private static final VoxelShape UPPER_NORTH = Shapes.or(
            // Cabinet crown rendered above the lower block boundary.
            Block.box(1.95, 0.0, 1.7, 14.05, 4.55, 13.8),
            // Bottle socket and neck.
            Block.box(5.1, 4.4, 5.1, 10.9, 5.2, 10.9),
            // Bottle base, lower shoulder, body, upper shoulder and cap.
            Block.box(3.7, 5.2, 3.7, 12.3, 6.5, 12.3),
            Block.box(2.8, 6.5, 2.8, 13.2, 7.7, 13.2),
            Block.box(3.2, 7.7, 3.2, 12.8, 12.8, 12.8),
            Block.box(2.8, 12.8, 2.8, 13.2, 14.05, 13.2),
            Block.box(3.6, 14.05, 3.6, 12.4, 15.55, 12.4)
    ).optimize();
    // Follow the thermal generator fix exactly: collision remains model-derived, while
    // targeting exposes only one closed outer envelope and therefore no internal seams.
    private static final VoxelShape LOWER_OUTLINE_NORTH = Block.box(1.7, 0.0, 1.4, 14.3, 16.0, 14.35);
    private static final VoxelShape UPPER_OUTLINE_NORTH = Block.box(1.95, 0.0, 1.7, 14.05, 15.55, 13.8);
    private static final Map<Direction, VoxelShape> LOWER_COLLISION_SHAPES = horizontalRotations(LOWER_NORTH);
    private static final Map<Direction, VoxelShape> UPPER_COLLISION_SHAPES = horizontalRotations(UPPER_NORTH);
    private static final Map<Direction, VoxelShape> LOWER_OUTLINE_SHAPES = horizontalRotations(LOWER_OUTLINE_NORTH);
    private static final Map<Direction, VoxelShape> UPPER_OUTLINE_SHAPES = horizontalRotations(UPPER_OUTLINE_NORTH);

    public WaterDispenserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos lower = context.getClickedPos();
        BlockPos upper = lower.above();
        Level level = context.getLevel();
        if (upper.getY() >= level.getMaxBuildHeight()
                || !level.getBlockState(lower).canBeReplaced(context)
                || !level.getBlockState(upper).canBeReplaced(context)
                || !level.getBlockState(lower.below()).isFaceSturdy(level, lower.below(), Direction.UP)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(position.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(position.below());
            return lower.is(this)
                    && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                    && lower.getValue(FACING) == state.getValue(FACING);
        }
        return level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && (!neighborState.is(this)
                || neighborState.getValue(HALF) != DoubleBlockHalf.LOWER
                || neighborState.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.UP
                && (!neighborState.is(this)
                || neighborState.getValue(HALF) != DoubleBlockHalf.UPPER
                || neighborState.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN
                && !neighborState.isFaceSturdy(level, neighborPos, Direction.UP)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = position.below();
            BlockState lower = level.getBlockState(lowerPos);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative() && player.getMainHandItem().isCorrectToolForDrops(lower)) {
                    popResource(level, lowerPos, new ItemStack(AflItems.WATER_DISPENSER.get()));
                }
                level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return outlineShapesFor(state).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos position) {
        return outlineShapesFor(state).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return collisionShapesFor(state).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    private static Map<Direction, VoxelShape> outlineShapesFor(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_OUTLINE_SHAPES : UPPER_OUTLINE_SHAPES;
    }

    private static Map<Direction, VoxelShape> collisionShapesFor(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_COLLISION_SHAPES : UPPER_COLLISION_SHAPES;
    }

    private static Map<Direction, VoxelShape> horizontalRotations(VoxelShape north) {
        EnumMap<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        VoxelShape shape = north;
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            result.put(direction, shape.optimize());
            shape = rotateY90(shape);
        }
        return Map.copyOf(result);
    }

    private static VoxelShape rotateY90(VoxelShape shape) {
        VoxelShape rotated = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            rotated = Shapes.or(rotated, Shapes.box(
                    1.0 - box.maxZ, box.minY, box.minX,
                    1.0 - box.minZ, box.maxY, box.maxX));
        }
        return rotated.optimize();
    }
}
