package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.StorageRackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Storage Rack V1 (docs/models/storage_rack_v1.md): a two-tall boltless steel rack, one block wide, five shelf levels; the
 * lower four hold goods. FACING is the front. LEFT / RIGHT (left = the facing's counter-clockwise side) are true when the
 * neighbour on that side is a storage rack with the same facing and half: neighbouring racks share the upright between
 * them. The lower half owns a 12-slot searchable container (3 x 4), opened from the front of either half.
 */
public class StorageRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    // canonical (facing north, front -Z), px; tools/build-storage-rack-v1.mjs RACK (front -2.2, back 7.8, top 31.2)
    private static final Map<Direction, VoxelShape> LOWER = CheckoutCounterBlock.rotations(Block.box(0, 0, 5.8, 16, 16, 15.8));
    private static final Map<Direction, VoxelShape> UPPER = CheckoutCounterBlock.rotations(Block.box(0, 0, 5.8, 16, 15.2, 15.8));

    public StorageRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(LEFT, false).setValue(RIGHT, false));
    }

    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private boolean joins(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        BlockState other = level.getBlockState(pos.relative(side));
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING) && other.getValue(HALF) == state.getValue(HALF);
    }

    /** The state with LEFT / RIGHT matching the neighbours. */
    private BlockState connected(LevelReader level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, joins(level, pos, state, facing.getCounterClockWise()))
                .setValue(RIGHT, joins(level, pos, state, facing.getClockWise()));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1 || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) return null;
        return connected(context.getLevel(), pos, defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    /** Places the upper half; a rack placed by a player holds the player's own things, never searched. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), connected(level, pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER)), Block.UPDATE_ALL);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StorageRackBlockEntity rack) rack.markPlacedByPlayer();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState lower = level.getBlockState(pos.below());
        return lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER && lower.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)
                && (!neighborState.is(this) || neighborState.getValue(HALF) == half || neighborState.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        Direction facing = state.getValue(FACING);
        if (direction == facing.getCounterClockWise() || direction == facing.getClockWise()) return connected(level, pos, state);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * The lower half carries the loot table: breaking the upper half removes the lower one, which drops the rack only for a
     * survival player with the right tool (creative, or the wrong tool: removed without a drop, as for the lower half).
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            BlockPos lower = pos.below();
            if (level.getBlockState(lower).is(this)) level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hit.getDirection() != state.getValue(FACING)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(lower(state, pos)) instanceof StorageRackBlockEntity rack)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.openMenu(rack);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StorageRackBlockEntity rack) rack.dropContentsOnce();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new StorageRackBlockEntity(pos, state) : null;
    }

    /** Server, lower half: rolls pending world loot at once, so the goods show from the start. */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<StorageRackBlockEntity>) (tickerLevel, tickerPos, tickerState, rack) -> rack.serverTick();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER).get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LEFT, RIGHT);
    }
}
