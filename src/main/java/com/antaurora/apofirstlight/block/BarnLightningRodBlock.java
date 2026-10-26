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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 4 B): an old barn lightning rod, real size (tools/
 * build-lightning-rods-v1.mjs, model V2): a cast iron tripod stand, a twisted copper-skinned rod through a cobalt glass
 * ball, an arrow vane, the point 1.80 m up. Two cells as a door: the lower half carries the stand, the cable and the rod to
 * the cell line, the upper half the rest and the tip (LightningRodTip). Placed from the lower cell with room above; either half gone takes the other. No collision.
 */
public class BarnLightningRodBlock extends HorizontalDirectionalBlock implements LightningRodTip {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** The point, 800 mm into the upper cell. */
    public static final double TIP = 0.8;
    // the generator's SELECTION boxes, the thin rod widened to 1 px for aiming, the stand's feet as one thin pad
    private static final VoxelShape LOWER_NORTH = Shapes.or(Block.box(7.4, 0, 7.4, 8.6, 5.02, 8.6), Block.box(3.6, 0, 3.6, 12.4, 0.5, 12.4),
            Block.box(7.5, 0, 7.5, 8.5, 16, 8.5), Block.box(7.6, 0, 0, 8.4, 1.05, 7.6), Block.box(7.6, 0, 6.8, 8.4, 3.9, 7.7));
    private static final VoxelShape UPPER_NORTH = Shapes.or(Block.box(7.5, 0, 7.5, 8.5, 12.8, 8.5), Block.box(7.08, 0.85, 7.08, 8.92, 3.95, 8.92),
            Block.box(2.7, 6.3, 7.6, 13.76, 8.72, 8.4), Block.box(7.2, 9.8, 7.2, 8.8, 12.8, 8.8));
    private static final Map<Direction, VoxelShape> LOWER = HorizontalShapeUtils.rotations(LOWER_NORTH);
    private static final Map<Direction, VoxelShape> UPPER = HorizontalShapeUtils.rotations(UPPER_NORTH);

    public BarnLightningRodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return Block.canSupportCenter(level, pos.below(), Direction.UP);
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction from, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        // the other half's side: it must be the other half still
        if (from.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (from == Direction.UP))
            return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        if (half == DoubleBlockHalf.LOWER && from == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, from, neighbour, level, pos, neighbourPos);
    }

    /** A creative player breaking the upper half: the lower goes without dropping (as vanilla's doors and tall plants). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER).get(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public double tip(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? TIP : Double.NaN;
    }
}
