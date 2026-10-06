package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.blockentity.WaterDispenserBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Water Dispenser V2 (2026-10-01, tools/build-water-dispenser-v2.mjs, Pure Mesh): a two-block office water cooler,
 * decoration plus power. The lower half owns the block entity (the mesh, the indicator lights' power) and the item drop.
 * {@link #LIT} on both halves: the indicator LEDs' lit set (no block light, the LEDs are tiny), fed through the standard
 * power port on the lower half's back. No water, drinking or storage yet.
 */
public final class WaterDispenserBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock, MeshHitMultiCell {
    /** The hit mesh (docs/rendering/mesh_hit_runtime_v1.md): every cell hits on the lower half (its block entity draws the dispenser). */
    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // Model-fitted boxes (tools/build-water-dispenser-v2.mjs, north-facing block px), used for both outline and collision.
    // The cabinet stands at the back of the block, its back (with the recessed power port) on the boundary.
    private static final VoxelShape LOWER_NORTH = Shapes.or(
            // Cabinet, the drip tray's lip included.
            Block.box(2.4, 0.0, 4.65, 13.6, 16.0, 16.0),
            // Paper cup tube on the right side.
            Block.box(0.4, 10.9, 7.7, 2.4, 16.0, 9.9)
    ).optimize();
    private static final VoxelShape UPPER_NORTH = Shapes.or(
            // Cabinet top with the faceplate and LEDs.
            Block.box(2.4, 0.0, 4.75, 13.6, 4.6, 16.0),
            // Top cap.
            Block.box(2.7, 4.6, 5.3, 13.3, 5.2, 15.7),
            // Paper cup tube.
            Block.box(0.4, 0.0, 7.7, 2.4, 2.3, 9.9),
            // Bottle seat and bottle.
            Block.box(3.3, 5.2, 5.8, 12.7, 15.6, 15.2)
    ).optimize();
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = horizontalRotations(LOWER_NORTH);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = horizontalRotations(UPPER_NORTH);

    public WaterDispenserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(LIT, false));
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
        if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN) {
            if (!neighborState.is(this)
                    || neighborState.getValue(HALF) != DoubleBlockHalf.LOWER
                    || neighborState.getValue(FACING) != state.getValue(FACING)) {
                return Blocks.AIR.defaultBlockState();
            }
            return state.setValue(LIT, neighborState.getValue(LIT));
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

    /** Indicator lights on / off: LIT on both halves (the mesh's light set follows it). */
    public void setLit(Level level, BlockPos lower, boolean lit) {
        BlockState state = level.getBlockState(lower);
        if (!state.is(this) || state.getValue(HALF) != DoubleBlockHalf.LOWER || state.getValue(LIT) == lit) return;
        level.setBlock(lower, state.setValue(LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        BlockState upper = level.getBlockState(lower.above());
        if (upper.is(this) && upper.getValue(HALF) == DoubleBlockHalf.UPPER)
            level.setBlock(lower.above(), upper.setValue(LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** The power port (tools/build-water-dispenser-v2.mjs POWER_PORT, recessed into the back): the lower half's back face only. */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && face == state.getValue(FACING).getOpposite();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new WaterDispenserBlockEntity(position, state) : null;
    }

    /** Server, lower half: the indicator lights' power (WaterDispenserBlockEntity#serverTick). */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER || type != AflBlockEntities.WATER_DISPENSER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<WaterDispenserBlockEntity>) (l, p, s, dispenser) -> dispenser.serverTick();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
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
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LIT);
    }

    private static Map<Direction, VoxelShape> shapesFor(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES;
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
