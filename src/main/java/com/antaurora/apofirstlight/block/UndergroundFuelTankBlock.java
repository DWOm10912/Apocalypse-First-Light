package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.fluid.AflFluidPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Underground Fuel Tank V1 (tools/build-underground-fuel-tank-v1.mjs, docs/models/underground_fuel_tank_v1.md): a fixed
 * 3 x 3 x 7 multiblock, placed whole by one item, holding one fuel (gasoline or diesel: a block each). Cells: ALONG 0..6
 * along AXIS, ACROSS 0..2, LEVEL 0..2; the port cell (3, 1, 2), at the top centre, is the master: it draws the tank, owns
 * the block entity and has the AFL fluid port on its top face. Placed from the bottom centre cell (3, 1, 0) at the
 * clicked position, the tank running away from the player. Breaking any cell removes the tank (one drop); the fuel in it
 * is lost.
 */
public class UndergroundFuelTankBlock extends Block implements EntityBlock, AflFluidPortBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty ALONG = IntegerProperty.create("along", 0, 6);
    public static final IntegerProperty ACROSS = IntegerProperty.create("across", 0, 2);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 2);
    public static final int PORT_ALONG = 3, PORT_ACROSS = 1, PORT_LEVEL = 2, ROOT_LEVEL = 0;
    // cross-section (px, the structure's x across, y up): the tank's axis at (24, 20), radius 19 plus the ribs
    private static final double CENTRE_X = 24, CENTRE_Y = 20, RADIUS = 19.6;
    private static final VoxelShape[][][] SHAPES_Z = new VoxelShape[7][3][3], SHAPES_X = new VoxelShape[7][3][3];

    static {
        for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) {
            double z0 = a == 0 ? 2 : 0, z1 = a == 6 ? 14 : 16;   // the dished heads, roughly
            VoxelShape z = Shapes.empty(), x = Shapes.empty();
            for (int k = 0; k < 4; k++) {   // 4 px columns across the cell
                double dx = c * 16 + k * 4 + 2 - CENTRE_X;
                if (Math.abs(dx) >= RADIUS) continue;
                double h = Math.sqrt(RADIUS * RADIUS - dx * dx), y0 = Math.max(l * 16, CENTRE_Y - h) - l * 16, y1 = Math.min(l * 16 + 16, CENTRE_Y + h) - l * 16;
                if (y1 <= y0) continue;
                z = Shapes.or(z, Block.box(k * 4, y0, z0, k * 4 + 4, y1, z1));
                x = Shapes.or(x, Block.box(z0, y0, k * 4, z1, y1, k * 4 + 4));
            }
            if (a == PORT_ALONG && c == PORT_ACROSS && l == PORT_LEVEL) {   // the manway: collar and lid, the riser and the port
                VoxelShape manway = Shapes.or(Block.box(1, 5.5, 1, 15, 11.2, 15), Block.box(2.5, 11.2, 2.5, 13.5, 16, 13.5));
                z = Shapes.or(z, manway);
                x = Shapes.or(x, manway);
            }
            SHAPES_Z[a][c][l] = z.optimize();
            SHAPES_X[a][c][l] = x.optimize();
        }
    }

    private record Mutation(LevelAccessor level, BlockPos root) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private final Supplier<? extends Fluid> fuel;

    public UndergroundFuelTankBlock(Supplier<? extends Fluid> fuel, Properties properties) {
        super(properties);
        this.fuel = fuel;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(ALONG, PORT_ALONG)
                .setValue(ACROSS, PORT_ACROSS).setValue(LEVEL, PORT_LEVEL));
    }

    /** The fuel this tank holds (the still source fluid). */
    public Fluid fuel() {
        return fuel.get();
    }

    // ---- cells ----

    private static Direction alongDir(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? Direction.SOUTH : Direction.EAST;
    }

    private static Direction acrossDir(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    public static BlockPos cellPosition(BlockPos root, Direction.Axis axis, int along, int across, int level) {
        return root.relative(alongDir(axis), along - PORT_ALONG).relative(acrossDir(axis), across - PORT_ACROSS).above(level - ROOT_LEVEL);
    }

    /** The bottom centre cell (3, 1, 0). */
    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        return position.relative(alongDir(axis), PORT_ALONG - state.getValue(ALONG)).relative(acrossDir(axis), PORT_ACROSS - state.getValue(ACROSS))
                .below(state.getValue(LEVEL) - ROOT_LEVEL);
    }

    /** The port cell (master) of the tank {@code position} belongs to. */
    public static BlockPos masterPosition(BlockPos position, BlockState state) {
        return cellPosition(rootPosition(position, state), state.getValue(AXIS), PORT_ALONG, PORT_ACROSS, PORT_LEVEL);
    }

    public static boolean isMaster(BlockState state) {
        return state.getValue(ALONG) == PORT_ALONG && state.getValue(ACROSS) == PORT_ACROSS && state.getValue(LEVEL) == PORT_LEVEL;
    }

    private BlockState stateFor(Direction.Axis axis, int along, int across, int level) {
        return defaultBlockState().setValue(AXIS, axis).setValue(ALONG, along).setValue(ACROSS, across).setValue(LEVEL, level);
    }

    private boolean matches(BlockState state, Direction.Axis axis, int along, int across, int level) {
        return state.is(this) && state.getValue(AXIS) == axis && state.getValue(ALONG) == along
                && state.getValue(ACROSS) == across && state.getValue(LEVEL) == level;
    }

    // ---- placement ----

    private static Direction.Axis placementAxis(BlockPlaceContext context) {
        return context.getHorizontalDirection().getAxis();
    }

    private boolean canPlaceStructure(BlockPlaceContext context, BlockPos root, Direction.Axis axis) {
        Level level = context.getLevel();
        if (root.getY() < level.getMinBuildHeight() || root.getY() + 2 >= level.getMaxBuildHeight()) return false;
        Player player = context.getPlayer();
        for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) {
            BlockPos position = cellPosition(root, axis, a, c, l);
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            if (player != null && !level.mayInteract(player, position)) return false;
            if (!level.getBlockState(position).canBeReplaced(BlockPlaceContext.at(context, position, Direction.UP))
                    || !level.getFluidState(position).isEmpty()) return false;
            if (!level.isUnobstructed(stateFor(axis, a, c, l), position, CollisionContext.empty())) return false;
        }
        return true;
    }

    /** The clicked cell's state (BlockItem tests it there): the bottom centre cell. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = placementAxis(context);
        return canPlaceStructure(context, context.getClickedPos(), axis) ? stateFor(axis, PORT_ALONG, PORT_ACROSS, ROOT_LEVEL) : null;
    }

    /** Runs inside BlockItem's placement (UndergroundFuelTankItem); a failed write restores every cell. */
    public boolean placeStructure(BlockPlaceContext context) {
        Direction.Axis axis = placementAxis(context);
        BlockPos root = context.getClickedPos().immutable();
        if (!canPlaceStructure(context, root, axis)) return false;
        Level level = context.getLevel();
        Mutation mutation = new Mutation(level, root);
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) {
                BlockPos position = cellPosition(root, axis, a, c, l);
                previous.put(position, level.getBlockState(position));
                if (!level.setBlock(position, stateFor(axis, a, c, l), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (BlockPos position : previous.keySet()) {
                level.updateNeighborsAt(position, this);
                // cells were set with UPDATE_KNOWN_SHAPE: let a pipe already over the port connect
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

    // ---- integrity, removal, drops ----

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos position, BlockPos neighborPosition) {
        level.scheduleTick(position, this, 1);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos position, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(position, this, 1);
    }

    /** A cell whose tank is incomplete removes itself. */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos root = rootPosition(position, state);
        Direction.Axis axis = state.getValue(AXIS);
        if (MUTATIONS.contains(new Mutation(level, root))) return;
        List<BlockPos> cells = new ArrayList<>();
        for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) cells.add(cellPosition(root, axis, a, c, l));
        for (BlockPos cell : cells) {
            if (!level.hasChunkAt(cell)) {
                level.scheduleTick(position, this, 100);
                return;
            }
        }
        int i = 0;
        for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) {
            if (!matches(level.getBlockState(cells.get(i++)), axis, a, c, l)) {
                level.removeBlock(position, false);
                return;
            }
        }
    }

    /** A survival player breaking a cell other than the master gets the tank (the master's loot table drops it otherwise). */
    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos root = rootPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && !isMaster(state) && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, position, new ItemStack(this));
        }
        removeOthers(level, root, state.getValue(AXIS), position);
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
        if (!state.is(replacement.getBlock())) removeOthers(level, rootPosition(position, state), state.getValue(AXIS), position);
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removeOthers(LevelAccessor level, BlockPos root, Direction.Axis axis, @Nullable BlockPos keep) {
        Mutation mutation = new Mutation(level, root.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) {
                BlockPos other = cellPosition(root, axis, a, c, l);
                if ((keep == null || !other.equals(keep)) && level.hasChunkAt(other) && matches(level.getBlockState(other), axis, a, c, l)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    // ---- port, block entity ----

    /** The one fluid port: the top face of the port cell (docs/models/fluid_pipe_v2.md, "流体接口规格"). */
    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return face == Direction.UP && isMaster(state);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isMaster(state) ? new UndergroundFuelTankBlockEntity(position, state) : null;
    }

    // ---- shape, render ----

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return (state.getValue(AXIS) == Direction.Axis.Z ? SHAPES_Z : SHAPES_X)[state.getValue(ALONG)][state.getValue(ACROSS)][state.getValue(LEVEL)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ALONG, ACROSS, LEVEL);
    }
}
