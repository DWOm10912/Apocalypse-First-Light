package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class RetailShelfSingleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();
    private static final double[] LAYER_BOUNDARIES = {0.20D, 0.56D, 0.94D, 1.31D, 1.69D};

    private static final VoxelShape NORTH_LOWER_SHAPE = Shapes.or(
            Shapes.box(0, 2 / 16.0, 15 / 16.0, 1, 1, 1),
            Shapes.box(0, 0, 7 / 16.0, 1, 2 / 16.0, 1),
            Shapes.box(0, 6 / 16.0, 10 / 16.0, 1, 7 / 16.0, 15 / 16.0),
            Shapes.box(0, 12 / 16.0, 10 / 16.0, 1, 13 / 16.0, 15 / 16.0),
            Shapes.box(1 / 16.0, 4 / 16.0, 13 / 16.0, 2 / 16.0, 6 / 16.0, 15 / 16.0),
            Shapes.box(14 / 16.0, 4 / 16.0, 13 / 16.0, 15 / 16.0, 6 / 16.0, 15 / 16.0)
    );

    private static final VoxelShape NORTH_UPPER_SHAPE = Shapes.or(
            Shapes.box(0, 0, 15 / 16.0, 1, 1, 1),
            Shapes.box(0, 2 / 16.0, 10 / 16.0, 1, 3 / 16.0, 15 / 16.0),
            Shapes.box(0, 8 / 16.0, 10 / 16.0, 1, 9 / 16.0, 15 / 16.0),
            Shapes.box(1 / 16.0, 0, 13 / 16.0, 2 / 16.0, 2 / 16.0, 15 / 16.0),
            Shapes.box(14 / 16.0, 0, 13 / 16.0, 15 / 16.0, 2 / 16.0, 15 / 16.0)
    );

    public RetailShelfSingleBlock(Properties properties) {
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
            if (!EXPLOSION_DESTROYING.remove(currentPos.immutable())) {
                dropContents(level, currentPos);
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state,
                                  net.minecraft.world.entity.player.Player player) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? position.below() : position;
        if (!player.isCreative() && level.getBlockState(lower).getBlock() == this) {
            popResource(level, lower, new ItemStack(AflItems.RETAIL_SHELF_SINGLE.get()));
        }
        dropContents(level, lower);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER
                && level.getBlockState(lower).is(this)) {
            level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.playerWillDestroy(level, position, state, player);
    }

    private static void dropContents(LevelAccessor level, BlockPos lower) {
        if (level instanceof Level serverLevel
                && serverLevel.getBlockEntity(lower) instanceof RetailShelfSingleBlockEntity shelf) {
            shelf.dropContentsOnce();
        }
    }

    public static void markExplosion(BlockPos lower) {
        EXPLOSION_DESTROYING.add(lower.immutable());
    }

    public static void clearExplosionMarks() {
        EXPLOSION_DESTROYING.clear();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        if (!(level.getBlockEntity(lower) instanceof RetailShelfSingleBlockEntity shelf)) {
            return InteractionResult.PASS;
        }

        int slot = getClickedSlot(level.getBlockState(lower).getValue(FACING), lower, hit);
        if (slot < 0) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (shelf.isEmpty(slot) && !held.isEmpty()) {
            shelf.insertOne(slot, held);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            return InteractionResult.CONSUME;
        }
        if (!shelf.isEmpty(slot) && held.isEmpty()) {
            ItemStack removed = shelf.removeOne(slot);
            if (!player.getInventory().add(removed)) {
                player.drop(removed, false);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static int getClickedSlot(Direction facing, BlockPos lower, BlockHitResult hit) {
        double localX = hit.getLocation().x - lower.getX();
        double localY = hit.getLocation().y - lower.getY();
        double localZ = hit.getLocation().z - lower.getZ();
        int layer = getLayer(localY);
        if (layer < 0) {
            return -1;
        }

        double frontLeftToRight = switch (facing) {
            case NORTH -> 1.0D - localX;
            case SOUTH -> localX;
            case EAST -> localZ;
            case WEST -> 1.0D - localZ;
            default -> -1.0D;
        };
        if (frontLeftToRight < 0.0D || frontLeftToRight >= 1.0D) {
            return -1;
        }
        int column = Math.min(2, (int) (frontLeftToRight * 3.0D));
        return layer * 3 + column;
    }

    private static int getLayer(double localY) {
        for (int layer = 0; layer < 4; layer++) {
            if (localY >= LAYER_BOUNDARIES[layer] && localY < LAYER_BOUNDARIES[layer + 1]) {
                return layer;
            }
        }
        return -1;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                               CollisionContext context) {
        VoxelShape canonical = state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? NORTH_LOWER_SHAPE : NORTH_UPPER_SHAPE;
        return rotateShape(canonical, state.getValue(FACING));
    }

    @Override
    @Nullable
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new RetailShelfSingleBlockEntity(position, state) : null;
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
