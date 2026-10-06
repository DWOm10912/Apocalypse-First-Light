package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.blockentity.FuelDispenserSumpBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.fluid.AflFluidPortBlock;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fuel Dispenser Sump V1 (UDC, 2026-10-05, tools/build-fuel-station-sump-v1.mjs, docs/models/fuel_station_sump_v1.md): the
 * box under a Fuel Dispenser, two cells along the island and two deep (the forecourt layer and the pipe layer under it).
 * The dispenser has two bottom faces but needs three lines (gasoline, diesel, power); the sump takes them all in the pipe
 * layer and hands them up (FuelDispenserSumpBlockEntity):
 * <ul>
 *   <li>gasoline: AFL fluid port on the A end of the lower A cell ({@code FACING.getCounterClockWise()});</li>
 *   <li>diesel: AFL fluid port on the B end of the lower B cell ({@code FACING.getClockWise()});</li>
 *   <li>power: AFL power port on the back of the lower A cell ({@code FACING.getOpposite()}).</li>
 * </ul>
 * Cells as the dispenser's: A = the master's column, B on its clockwise side; 0 = lower, 1 = upper. Placed with the lower A
 * cell at the clicked position, facing the player; when a dispenser stands two cells above the clicked position it lines up
 * with that dispenser instead (same facing, under its A and B columns).
 */
public final class FuelDispenserSumpBlock extends HorizontalDirectionalBlock implements EntityBlock, AflFluidPortBlock, AflPowerPortBlock, MeshHitMultiCell {
    /** The hit mesh (docs/rendering/mesh_hit_runtime_v1.md): every cell hits on the a0 cell (its variant is the body). */
    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return rootPosition(pos, state);
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);

    private record Mutation(LevelAccessor level, BlockPos root) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();

    public FuelDispenserSumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.A0));
    }

    // ---- cells ----

    public static BlockPos cellPosition(BlockPos root, Direction facing, Cell cell) {
        return root.relative(facing.getClockWise(), cell.dx).above(cell.dy);
    }

    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        Cell cell = state.getValue(CELL);
        return position.relative(state.getValue(FACING).getCounterClockWise(), cell.dx).below(cell.dy);
    }

    public BlockState stateFor(Direction facing, Cell cell) {
        return defaultBlockState().setValue(FACING, facing).setValue(CELL, cell);
    }

    private boolean matches(BlockState state, Direction facing, Cell cell) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(CELL) == cell;
    }

    /** The dispenser cell over this sump cell's column (the cell above the upper sump cell), if a dispenser stands there. */
    @Nullable
    public static BlockPos dispenserAbove(BlockGetter level, BlockPos position, BlockState state) {
        BlockPos up = position.above(2 - state.getValue(CELL).dy);
        BlockState above = level.getBlockState(up);
        return above.getBlock() instanceof FuelDispenserBlock && above.getValue(FuelDispenserBlock.CELL).dy == 0
                ? FuelDispenserBlock.rootPosition(up, above) : null;
    }

    // ---- placement ----

    private record Placement(BlockPos root, Direction facing) {}

    private static Placement placement(BlockPlaceContext context) {
        BlockPos clicked = context.getClickedPos();
        BlockState above = context.getLevel().getBlockState(clicked.above(2));
        if (above.getBlock() instanceof FuelDispenserBlock && above.getValue(FuelDispenserBlock.CELL).dy == 0) {
            BlockPos dispenser = FuelDispenserBlock.rootPosition(clicked.above(2), above);
            return new Placement(dispenser.below(2), above.getValue(FuelDispenserBlock.FACING));
        }
        return new Placement(clicked, context.getHorizontalDirection().getOpposite());
    }

    private boolean canPlace(BlockPlaceContext context, Placement p) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        for (Cell cell : Cell.values()) {
            BlockPos position = cellPosition(p.root(), p.facing(), cell);
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            if (position.getY() < level.getMinBuildHeight() || position.getY() >= level.getMaxBuildHeight()) return false;
            if (player != null && (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
            if (!level.getBlockState(position).canBeReplaced(BlockPlaceContext.at(context, position, Direction.UP))
                    || !level.getFluidState(position).isEmpty()) return false;
            if (!level.isUnobstructed(stateFor(p.facing(), cell), position, CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Placement p = placement(context);
        if (!canPlace(context, p)) return null;
        for (Cell cell : Cell.values()) {
            if (cellPosition(p.root(), p.facing(), cell).equals(context.getClickedPos())) return stateFor(p.facing(), cell);
        }
        return null;
    }

    /** Runs inside BlockItem's placement (FuelDispenserSumpItem); a failed write restores every cell. */
    public boolean placeStructure(BlockPlaceContext context) {
        Placement p = placement(context);
        return canPlace(context, p) && placeCells(context.getLevel(), p.root(), p.facing());
    }

    /** Writes the four cells (no checks: the caller has made room); also used by the dev station builder. */
    public boolean placeCells(Level level, BlockPos root, Direction facing) {
        Mutation mutation = new Mutation(level, root.immutable());
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new HashMap<>();
        boolean success = false;
        try {
            for (Cell cell : Cell.values()) {
                BlockPos position = cellPosition(root, facing, cell);
                previous.put(position, level.getBlockState(position));
                if (!level.setBlock(position, stateFor(facing, cell), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (Cell cell : Cell.values()) {
                BlockPos position = cellPosition(root, facing, cell);
                level.updateNeighborsAt(position, this);
                // cells were set with UPDATE_KNOWN_SHAPE: let pipes and a cable at the ports connect
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
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        level.scheduleTick(position, this, 1);
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos position, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(position, this, 1);
    }

    /** A cell whose structure is incomplete removes itself. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos root = rootPosition(position, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, root))) return;
        for (Cell cell : Cell.values()) {
            BlockPos other = cellPosition(root, facing, cell);
            if (!level.hasChunkAt(other)) {
                level.scheduleTick(position, this, 100);
                return;
            }
            if (!matches(level.getBlockState(other), facing, cell)) {
                level.removeBlock(position, false);
                return;
            }
        }
    }

    /** A survival player breaking any other cell gets the sump (the lower A cell's loot table drops it otherwise). */
    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos root = rootPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && state.getValue(CELL) != Cell.A0 && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, root, new ItemStack(this));
        }
        removeOthers(level, root, state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return state.getValue(CELL) == Cell.A0 ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) removeOthers(level, rootPosition(position, state), state.getValue(FACING), position);
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removeOthers(LevelAccessor level, BlockPos root, Direction facing, BlockPos keep) {
        Mutation mutation = new Mutation(level, root.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Cell cell : Cell.values()) {
                BlockPos other = cellPosition(root, facing, cell);
                if (!other.equals(keep) && level.hasChunkAt(other) && matches(level.getBlockState(other), facing, cell)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    // ---- ports ----

    /** Gasoline: the A end of the lower A cell. Diesel: the B end of the lower B cell. */
    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return grade(state, face) != null;
    }

    /** The line a fluid port face carries, or null. */
    @Nullable
    public static FuelDispenserBlock.Grade grade(BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        Cell cell = state.getValue(CELL);
        if (cell == Cell.A0 && face == facing.getCounterClockWise()) return FuelDispenserBlock.Grade.GASOLINE;
        if (cell == Cell.B0 && face == facing.getClockWise()) return FuelDispenserBlock.Grade.DIESEL;
        return null;
    }

    /** The power port: the back of the lower A cell (docs/models/power_cable_v2.md). */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return state.getValue(CELL) == Cell.A0 && face == state.getValue(FACING).getOpposite();
    }

    // ---- shapes, rendering ----

    /** The lower A cell's baked model draws the whole sump (blockstates/fuel_dispenser_sump.json). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(CELL) == Cell.A0 ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return Shapes.block();
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

    // ---- block entity: the two lower cells (the port cells) ----

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(CELL).dy == 0 ? new FuelDispenserSumpBlockEntity(position, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL);
    }

    public enum Cell implements StringRepresentable {
        A0(0, 0), B0(1, 0), A1(0, 1), B1(1, 1);

        public final int dx, dy;

        Cell(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
