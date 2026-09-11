package com.antaurora.apofirstlight.block;

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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Static two-block office multifunction printer. */
public final class OfficeMultifunctionPrinterBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER_NORTH = Shapes.or(
            Block.box(1.0, 0.0, 1.7, 15.0, 1.8, 14.6),
            Block.box(1.0, 1.5, 1.8, 15.0, 16.0, 14.55),
            Block.box(1.0, 10.45, 1.68, 15.0, 16.0, 2.2)
    ).optimize();
    private static final VoxelShape UPPER_NORTH = Shapes.or(
            Block.box(1.0, 0.0, 1.8, 15.0, 3.25, 14.6),
            Block.box(0.85, 3.0, 1.7, 15.15, 5.1, 14.9),
            Block.box(1.4, 5.0, 2.95, 7.7, 7.75, 14.2),
            Block.box(7.2, 5.0, 3.5, 14.5, 8.3, 13.6),
            Block.box(3.8, 0.55, 0.8, 14.6, 4.2, 3.3)
    ).optimize();
    private static final Map<Direction, VoxelShape> LOWER_SHAPES =
            HorizontalShapeUtils.rotations(LOWER_NORTH);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES =
            HorizontalShapeUtils.rotations(UPPER_NORTH);

    public OfficeMultifunctionPrinterBlock(Properties properties) {
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
        BlockPos floor = position.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && (!neighbor.is(this)
                || neighbor.getValue(HALF) != DoubleBlockHalf.LOWER
                || neighbor.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.UP
                && (!neighbor.is(this)
                || neighbor.getValue(HALF) != DoubleBlockHalf.UPPER
                || neighbor.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN
                && !neighbor.isFaceSturdy(level, neighborPosition, Direction.UP)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPosition = position.below();
            BlockState lower = level.getBlockState(lowerPosition);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative() && player.getMainHandItem().isCorrectToolForDrops(lower)) {
                    Block.dropResources(lower, level, lowerPosition, null, player, player.getMainHandItem());
                }
                level.setBlock(lowerPosition, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return shapesFor(state).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos position) {
        return shapesFor(state).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return shapesFor(state).get(state.getValue(FACING));
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
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    private static Map<Direction, VoxelShape> shapesFor(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES;
    }
}
