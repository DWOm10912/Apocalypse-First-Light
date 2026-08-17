package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.jetbrains.annotations.Nullable;

public class SupermarketShelfSingleBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape NORTH_LOWER_SHAPE = Shapes.or(
            Shapes.box(0, 2 / 16.0, 15 / 16.0, 1, 1, 1),
            Shapes.box(0, 0, 4 / 16.0, 1, 2 / 16.0, 1),
            Shapes.box(0, 6 / 16.0, 7 / 16.0, 1, 7 / 16.0, 15 / 16.0),
            Shapes.box(0, 12 / 16.0, 7 / 16.0, 1, 13 / 16.0, 15 / 16.0),
            Shapes.box(1 / 16.0, 4 / 16.0, 13 / 16.0, 2 / 16.0, 6 / 16.0, 15 / 16.0),
            Shapes.box(14 / 16.0, 4 / 16.0, 13 / 16.0, 15 / 16.0, 6 / 16.0, 15 / 16.0)
    );

    private static final VoxelShape NORTH_UPPER_SHAPE = Shapes.or(
            Shapes.box(0, 0, 15 / 16.0, 1, 1, 1),
            Shapes.box(0, 2 / 16.0, 7 / 16.0, 1, 3 / 16.0, 15 / 16.0),
            Shapes.box(0, 8 / 16.0, 7 / 16.0, 1, 9 / 16.0, 15 / 16.0),
            Shapes.box(1 / 16.0, 0, 13 / 16.0, 2 / 16.0, 2 / 16.0, 15 / 16.0),
            Shapes.box(14 / 16.0, 0, 13 / 16.0, 15 / 16.0, 2 / 16.0, 15 / 16.0)
    );

    public SupermarketShelfSingleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
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
                || !context.getLevel().getBlockState(lower.below())
                .isFaceSturdy(context.getLevel(), lower.below(), Direction.UP)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
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
        return level.getBlockState(position.below())
                .isFaceSturdy(level, position.below(), Direction.UP);
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
    public void playerWillDestroy(Level level, BlockPos position, BlockState state,
                                  net.minecraft.world.entity.player.Player player) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? position.below() : position;
        if (!player.isCreative() && level.getBlockState(lower).getBlock() == this
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            popResource(level, lower, new ItemStack(AflItems.SUPERMARKET_SHELF_SINGLE.get()));
        }
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER
                && level.getBlockState(lower).is(this)) {
            level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                               CollisionContext context) {
        VoxelShape canonical = state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? NORTH_LOWER_SHAPE : NORTH_UPPER_SHAPE;
        return rotateShape(canonical, state.getValue(FACING));
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH) {
            return shape;
        }
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double x0;
            double x1;
            double z0;
            double z1;
            switch (facing) {
                case SOUTH -> {
                    x0 = 1 - maxX;
                    x1 = 1 - minX;
                    z0 = 1 - maxZ;
                    z1 = 1 - minZ;
                }
                case EAST -> {
                    x0 = 1 - maxZ;
                    x1 = 1 - minZ;
                    z0 = minX;
                    z1 = maxX;
                }
                case WEST -> {
                    x0 = minZ;
                    x1 = maxZ;
                    z0 = 1 - maxX;
                    z1 = 1 - minX;
                }
                default -> {
                    x0 = minX;
                    x1 = maxX;
                    z0 = minZ;
                    z1 = maxZ;
                }
            }
            rotated[0] = Shapes.or(rotated[0], Shapes.box(x0, minY, z0, x1, maxY, z1));
        });
        return rotated[0];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }
}
