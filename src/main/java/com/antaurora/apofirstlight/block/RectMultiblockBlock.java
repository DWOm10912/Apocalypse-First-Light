package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A rectangular multi-cell block (Fuel Stop A1 details V1, docs/models/fuel_stop_a1_details_v1.md; the pattern of
 * FuelDispenserBlock): columns along FACING's clockwise side, rows up. One cell is the master: it draws the whole model (its
 * blockstate), holds the block entity if there is one and drops the item; the other cells only hold their part of the shape.
 * Placed whole by {@link com.antaurora.apofirstlight.item.RectMultiblockBlockItem} with the master at the clicked cell,
 * facing the player; breaking any cell removes them all; a cell whose structure is incomplete removes itself.
 * <p>
 * Shapes are given as boxes in the structure frame: north-facing block px, x from the first column's west edge along the
 * columns, y from the bottom row's floor, z 0 (the front, north) to 16; they are cut into the cells and turned with FACING.
 */
public abstract class RectMultiblockBlock<C extends Enum<C> & StringRepresentable & RectMultiblockBlock.GridCell>
        extends HorizontalDirectionalBlock implements MeshHitMultiCell {
    public interface GridCell {
        int col();

        int row();
    }

    private record Mutation(LevelAccessor level, BlockPos origin) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private final Map<String, VoxelShape> shapes = new ConcurrentHashMap<>();

    protected RectMultiblockBlock(Properties properties) {
        super(properties);
    }

    protected abstract EnumProperty<C> cellProperty();

    protected abstract C[] cells();

    public abstract C master();

    /** Collision and outline boxes in the structure frame (see the class comment), px. */
    protected abstract double[][] boxes();

    // ---- cells ----

    public BlockPos cellPosition(BlockPos origin, Direction facing, C cell) {
        return origin.relative(facing.getClockWise(), cell.col()).above(cell.row());
    }

    /** The first column's bottom cell of the structure this cell belongs to. */
    public BlockPos origin(BlockPos pos, BlockState state) {
        C cell = state.getValue(cellProperty());
        return pos.relative(state.getValue(FACING).getCounterClockWise(), cell.col()).below(cell.row());
    }

    public BlockPos masterPosition(BlockPos pos, BlockState state) {
        return cellPosition(origin(pos, state), state.getValue(FACING), master());
    }

    public boolean isMaster(BlockState state) {
        return state.getValue(cellProperty()) == master();
    }

    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return masterPosition(pos, state);
    }

    protected BlockState stateFor(Direction facing, C cell) {
        return defaultBlockState().setValue(FACING, facing).setValue(cellProperty(), cell);
    }

    private boolean matches(BlockState state, Direction facing, C cell) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(cellProperty()) == cell;
    }

    // ---- placement ----

    private BlockPos placementOrigin(BlockPlaceContext context, Direction facing) {
        return context.getClickedPos().relative(facing.getCounterClockWise(), master().col()).below(master().row());
    }

    private boolean canPlace(BlockPlaceContext context, BlockPos origin, Direction facing) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        for (C cell : cells()) {
            BlockPos position = cellPosition(origin, facing, cell);
            if (position.getY() < level.getMinBuildHeight() || position.getY() >= level.getMaxBuildHeight()) return false;
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            if (player != null && (!level.mayInteract(player, position) || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
            if (!level.getBlockState(position).canBeReplaced(BlockPlaceContext.at(context, position, Direction.UP)) || !level.getFluidState(position).isEmpty()) return false;
            if (!level.isUnobstructed(stateFor(facing, cell), position, CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlace(context, placementOrigin(context, facing), facing) ? stateFor(facing, master()) : null;
    }

    /** Runs inside the item's placement; a failed write restores every cell. */
    public boolean placeStructure(BlockPlaceContext context, BlockState masterState) {
        Direction facing = masterState.getValue(FACING);
        BlockPos origin = placementOrigin(context, facing);
        if (!canPlace(context, origin, facing)) return false;
        Level level = context.getLevel();
        Mutation mutation = new Mutation(level, origin);
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (C cell : cells()) {
                BlockPos position = cellPosition(origin, facing, cell);
                previous.put(position, level.getBlockState(position));
                // every cell carries the master's other properties (a gate's hinge, a sign's look)
                BlockState state = masterState.setValue(cellProperty(), cell);
                if (!level.setBlock(position, state, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (C cell : cells()) {
                BlockPos position = cellPosition(origin, facing, cell);
                level.updateNeighborsAt(position, this);
                // cells were set with UPDATE_KNOWN_SHAPE: let a cable already under a port connect
                level.getBlockState(position).updateNeighbourShapes(level, position, UPDATE_ALL);
            }
            return true;
        } finally {
            if (!success) previous.forEach((position, oldState) -> {
                if (level.getBlockState(position).is(this)) level.setBlock(position, oldState, UPDATE_ALL);
            });
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        level.scheduleTick(position, this, 1);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos position, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(position, this, 1);
    }

    /** A cell whose structure is incomplete removes itself. */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos origin = origin(position, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, origin))) return;
        for (C cell : cells()) {
            if (!level.hasChunkAt(cellPosition(origin, facing, cell))) {
                level.scheduleTick(position, this, 100);
                return;
            }
        }
        for (C cell : cells()) {
            if (!matches(level.getBlockState(cellPosition(origin, facing, cell)), facing, cell)) {
                level.removeBlock(position, false);
                return;
            }
        }
    }

    // ---- removal, drops ----

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos master = masterPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && !isMaster(state) && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, master, new ItemStack(this));
        }
        removeOthers(level, origin(position, state), state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return isMaster(state) ? super.getDrops(state, builder) : List.of();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) removeOthers(level, origin(position, state), state.getValue(FACING), position);
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removeOthers(LevelAccessor level, BlockPos origin, Direction facing, @Nullable BlockPos keep) {
        Mutation mutation = new Mutation(level, origin.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (C cell : cells()) {
                BlockPos other = cellPosition(origin, facing, cell);
                if ((keep == null || !other.equals(keep)) && level.hasChunkAt(other) && matches(level.getBlockState(other), facing, cell)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    // ---- shapes, rendering ----

    /** The master's model draws the whole structure; the other cells draw nothing. */
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        C cell = state.getValue(cellProperty());
        Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell.getSerializedName() + "/" + facing.getName(), k -> HorizontalShapeUtils.rotations(cellShape(cell)).get(facing));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    /** One cell's part of the boxes (north-facing cell px). */
    private VoxelShape cellShape(C cell) {
        double ox = 16 * cell.col(), oy = 16 * cell.row();
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes()) {
            double x0 = Math.max(box[0], ox), x1 = Math.min(box[3], ox + 16), y0 = Math.max(box[1], oy), y1 = Math.min(box[4], oy + 16);
            if (x1 <= x0 || y1 <= y0) continue;
            shape = Shapes.or(shape, Block.box(x0 - ox, y0 - oy, box[2], x1 - ox, y1 - oy, box[5]));
        }
        return shape.optimize();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
}
