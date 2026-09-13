package com.antaurora.apofirstlight.block;

import java.util.Map;
import java.util.List;

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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Two-cell dry wall sink. The lower cell renders the full model; the upper reserves its faucet. */
public final class CommercialWallMountedSinkBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape ORIGINAL_NORTH = Shapes.or(
            // Closed ceramic floor, with a hollow mouth above it.
            Block.box(2.35, 6.95, 6.0, 13.65, 7.55, 14.5),
            Block.box(.75, 7.35, 5.8, 2.8, 10.05, 15.6),
            Block.box(13.2, 7.35, 5.8, 15.25, 10.05, 15.6),
            Block.box(2.65, 7.35, 5.8, 13.35, 10.05, 7.35),
            Block.box(2.65, 7.35, 13.55, 13.35, 10.4, 16),
            // Faucet base, upright and forward spout.
            Block.box(7.1, 10.0, 13.75, 8.9, 12.45, 15.0),
            Block.box(7.45, 12.0, 11.1, 8.55, 13.55, 14.8),
            // Exposed pipe and wall fitting. Leave the rest of the under-sink air open.
            Block.box(7.35, 4.4, 9.5, 8.65, 7.2, 11.3),
            Block.box(7.15, 1.75, 9.6, 8.85, 4.6, 12.65),
            Block.box(7.35, 3.95, 12.1, 8.65, 4.85, 15.85),
            Block.box(7.15, 3.5, 15.4, 8.85, 5.2, 16),
            // Two narrow wall brackets, without an invisible full-height rear wall.
            Block.box(2.75, 6.1, 12.75, 3.65, 8.95, 16),
            Block.box(12.35, 6.1, 12.75, 13.25, 8.95, 16)
    ).optimize();
    // The approved source rises by five model units. Clip the shared full shape at the cell seam.
    private static final VoxelShape FULL_NORTH = ORIGINAL_NORTH.move(0, 5.0 / 16.0, 0);
    private static final VoxelShape LOWER_NORTH = Shapes.join(FULL_NORTH, Shapes.block(), BooleanOp.AND).optimize();
    private static final VoxelShape UPPER_NORTH = Shapes.join(
            FULL_NORTH.move(0, -1, 0), Shapes.block(), BooleanOp.AND).optimize();
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = HorizontalShapeUtils.rotations(LOWER_NORTH);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = HorizontalShapeUtils.rotations(UPPER_NORTH);

    public CommercialWallMountedSinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos upper = context.getClickedPos().above();
        Level level = context.getLevel();
        if (upper.getY() >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(upper)
                || !level.getBlockState(upper).canBeReplaced(BlockPlaceContext.at(context, upper, Direction.UP))
                || !level.getFluidState(context.getClickedPos()).isEmpty() || !level.getFluidState(upper).isEmpty())
            return null;
        var player = context.getPlayer();
        if (player != null && (!level.mayInteract(player, upper)
                || !player.mayUseItemAt(upper, Direction.UP, context.getItemInHand()))) return null;
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return level.isUnobstructed(state.setValue(HALF, DoubleBlockHalf.UPPER), upper,
                CollisionContext.empty()) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(position.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState lower = level.getBlockState(position.below());
        return lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                && lower.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == (half == DoubleBlockHalf.UPPER ? Direction.DOWN : Direction.UP)
                && (!neighbor.is(this) || neighbor.getValue(HALF) == half
                    || neighbor.getValue(FACING) != state.getValue(FACING)))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = position.below();
            BlockState lower = level.getBlockState(lowerPos);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative() && player.hasCorrectToolForDrops(lower))
                    Block.dropResources(lower, level, lowerPos, null, player, player.getMainHandItem());
                level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES)
                .get(state.getValue(FACING));
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
}
