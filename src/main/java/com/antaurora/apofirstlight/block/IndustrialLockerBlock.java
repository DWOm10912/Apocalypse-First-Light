package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.IndustrialLockerBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class IndustrialLockerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape NORTH_SHAPE = Shapes.box(2 / 16.0, 0, 1 / 16.0, 14 / 16.0, 1, 14 / 16.0);
    private static final VoxelShape SOUTH_SHAPE = Shapes.box(2 / 16.0, 0, 2 / 16.0, 14 / 16.0, 1, 15 / 16.0);
    private static final VoxelShape EAST_SHAPE = Shapes.box(1 / 16.0, 0, 2 / 16.0, 14 / 16.0, 1, 14 / 16.0);
    private static final VoxelShape WEST_SHAPE = Shapes.box(2 / 16.0, 0, 2 / 16.0, 15 / 16.0, 1, 14 / 16.0);
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();

    public IndustrialLockerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        BlockPos lower = context.getClickedPos();
        BlockPos upper = lower.above();
        if (!context.getLevel().getBlockState(lower).canBeReplaced(context)
                || !context.getLevel().getBlockState(upper).canBeReplaced(context)
                || !context.getLevel().getBlockState(lower.below()).isFaceSturdy(context.getLevel(), lower.below(), Direction.UP)) {
            return null;
        }
        Direction front = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, front).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        level.setBlock(position.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
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
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && direction == Direction.DOWN
                && !neighborState.isFaceSturdy(level, neighborPos, Direction.UP)) {
            if (!EXPLOSION_DESTROYING.remove(currentPos.immutable())) {
                dropContents(level, currentPos);
            }
            return Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        if (level.getBlockEntity(lower) instanceof IndustrialLockerBlockEntity locker) {
            player.openMenu(locker);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        if (!player.isCreative() && level.getBlockState(lower).getBlock() == this
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            popResource(level, lower, new ItemStack(AflItems.INDUSTRIAL_LOCKER.get()));
        }
        dropContents(level, lower);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(lower).is(this)) {
            level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.playerWillDestroy(level, position, state, player);
    }

    private static void dropContents(LevelAccessor level, BlockPos lower) {
        if (level instanceof Level serverLevel && serverLevel.getBlockEntity(lower) instanceof IndustrialLockerBlockEntity locker) {
            locker.dropContentsOnce();
        }
    }

    public static void markExplosion(BlockPos lower) {
        EXPLOSION_DESTROYING.add(lower.immutable());
    }

    public static void clearExplosionMarks() {
        EXPLOSION_DESTROYING.clear();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> Shapes.empty();
        };
    }

    @Override
    @Nullable
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new IndustrialLockerBlockEntity(position, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }
}
