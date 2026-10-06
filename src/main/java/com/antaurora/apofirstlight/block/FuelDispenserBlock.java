package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Fuel Dispenser V1 (2026-10-04, tools/build-fuel-dispenser-v1.mjs, docs/models/fuel_dispenser_v1.md): a dual-sided,
 * high-hose dispenser on its own island curb segment, two cells along the island and three tall. FACING points at the
 * "front" customer face; the "back" face is the front turned 180 degrees. The MASTER cell (A0: the bottom of the column on
 * the facing's counter-clockwise side) carries the whole static model, the block entity (who holds which nozzle) and the
 * drop; the second column (B) stands on the master's clockwise side; the other five cells only hold shapes. Its bottom
 * cells carry the dispenser's own island segment; aimed at the top of a built island's straight curbs, it takes the place
 * of two of them ({@link #ISLAND}) and gives them back when broken (2026-10-04).
 * <p>
 * Each face has two holstered nozzles, gasoline at the customer's left and diesel at the right ({@link Nozzle}). A nozzle's
 * property is true while it hangs in its holster: the blockstate then shows its model (the nozzle and its parked hose).
 * With an empty main hand, a click on a holster takes that nozzle: a tethered {@link FuelNozzleItem} goes to the hand,
 * its model hides and FuelDispenserRenderer draws the live hose from the outlet to the hand. A click on the dispenser
 * with that nozzle hangs it back; letting go of it any other way returns it too (FuelDispenserBlockEntity). There is no
 * fuel and no power in V1.
 */
public final class FuelDispenserBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock, MeshHitMultiCell {
    /** The hit mesh (docs/rendering/mesh_hit_runtime_v1.md): every cell hits on the a0 cell (its multipart applies the body). */
    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return rootPosition(pos, state);
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    /** Set into an island (placed onto two straight curbs, which it replaces): breaking it puts the curbs back. */
    public static final BooleanProperty ISLAND = BooleanProperty.create("island");
    /**
     * The lamp under the header is on (all cells; the top cells give the block light): powered through the port on the
     * master's bottom face, lights only (CompressorAppliance, machine_balance/fuel_dispenser.json).
     */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Block light of the top cells while lit. */
    public static final int LIGHT_LEVEL = 12;

    /**
     * Model frame boxes (source px: the footprint centre at x 0, z 0; x along the island, y up, the front face toward -z)
     * of the collision and outline shapes, cut into the six cells.
     */
    private static final double[][] BOXES = {
            {-16, 0, -8, 16, 3, 8},                      // island curb
            {-10.4, 3, -4.6, 10.4, 4.2, 4.6},            // plinth
            {-10, 4.2, -4.2, -7.8, 40.67, 4.2},          // pillars
            {7.8, 4.2, -4.2, 10, 40.67, 4.2},
            {-7.9, 4.2, -4.12, 7.9, 29.4, 4.12},         // hydraulic cabinet, nozzle bay, band, display head
            {-5.4, 10.45, -5.3, 5.4, 20.15, 5.3},        // holster frames and the hung nozzles (both faces)
            {-4.6, 38.15, -2.2, 4.6, 40.65, 2.2},        // lamp box
            {-11.6, 40.6, -6.0, 11.6, 44.2, 6.0},        // header
    };
    /** A hit on the nozzle bay (source px) aims at a holster: within these heights, at least this far out from the centre plane. */
    private static final double AIM_Y0 = 9.5, AIM_Y1 = 21.5, AIM_Z = 3.0, AIM_X = 7.9;

    private record Mutation(LevelAccessor level, BlockPos root) {}
    private record ShapeKey(Cell cell, Direction facing) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<ShapeKey, VoxelShape> SHAPES = buildShapes();

    public FuelDispenserBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.A0).setValue(ISLAND, false).setValue(LIT, false);
        for (Nozzle nozzle : Nozzle.values()) state = state.setValue(nozzle.property, true);
        registerDefaultState(state);
    }

    // ---- cells ----

    public static BlockPos cellPosition(BlockPos root, Direction facing, Cell cell) {
        return root.relative(facing.getClockWise(), cell.dx).above(cell.dy);
    }

    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        Cell cell = state.getValue(CELL);
        return position.relative(state.getValue(FACING).getCounterClockWise(), cell.dx).below(cell.dy);
    }

    private BlockState stateFor(Direction facing, Cell cell, boolean island) {
        return defaultBlockState().setValue(FACING, facing).setValue(CELL, cell).setValue(ISLAND, island);
    }

    private boolean matches(BlockState state, Direction facing, Cell cell) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(CELL) == cell;
    }

    /** A straight island curb (not an end) running along the dispenser's island axis for this facing. */
    private static boolean curbAlong(BlockState state, Direction facing) {
        return state.is(AflBlocks.FUEL_ISLAND_CURB.get())
                && state.getValue(FACING).getClockWise().getAxis() == facing.getClockWise().getAxis();
    }

    /**
     * Where the dispenser goes: root (cell A0), facing, and whether it is set into an island. Aimed at the top of a straight
     * curb, it takes the place of that curb and its neighbour along the island (the clicked curb as A0, else as B0), facing
     * the player's side of the island; elsewhere it stands at the clicked cell facing the player.
     */
    private record Placement(BlockPos root, Direction facing, boolean island) {}

    @Nullable
    private Placement placement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos(), below = clicked.below();
        BlockState under = level.getBlockState(below);
        if (!under.is(AflBlocks.FUEL_ISLAND_CURB.get())) return new Placement(clicked, facing, false);
        Direction curb = under.getValue(FACING), f = facing.getAxis() == curb.getAxis() ? facing : curb;
        for (BlockPos root : new BlockPos[]{below, below.relative(f.getCounterClockWise())}) {
            if (curbAlong(level.getBlockState(root), f) && curbAlong(level.getBlockState(root.relative(f.getClockWise())), f)) {
                return new Placement(root.immutable(), f, true);
            }
        }
        return null;
    }

    private boolean canPlaceStructure(BlockPlaceContext context, Placement p) {
        Level level = context.getLevel();
        BlockPos root = p.root();
        if (root.getY() < level.getMinBuildHeight() || root.getY() + 2 >= level.getMaxBuildHeight()) return false;
        Player player = context.getPlayer();
        for (Cell cell : Cell.values()) {
            BlockPos position = cellPosition(root, p.facing(), cell);
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            if (player != null && (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
            if (p.island() && cell.dy == 0) {
                if (!curbAlong(level.getBlockState(position), p.facing())) return false;
                continue;
            }
            BlockPlaceContext localContext = BlockPlaceContext.at(context, position, Direction.UP);
            if (!level.getBlockState(position).canBeReplaced(localContext) || !level.getFluidState(position).isEmpty()) return false;
            if (!level.isUnobstructed(stateFor(p.facing(), cell, p.island()), position, CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Placement p = placement(context);
        if (p == null || !canPlaceStructure(context, p)) return null;
        for (Cell cell : Cell.values()) {
            if (cellPosition(p.root(), p.facing(), cell).equals(context.getClickedPos())) return stateFor(p.facing(), cell, p.island());
        }
        return null;
    }

    /** Runs inside BlockItem's placement (FuelDispenserBlockItem); a failed write restores every cell. */
    public boolean placeStructure(BlockPlaceContext context, BlockState masterState) {
        Placement p = placement(context);
        if (p == null || !canPlaceStructure(context, p)) return false;
        Level level = context.getLevel();
        BlockPos root = p.root();
        Direction facing = p.facing();
        Mutation mutation = new Mutation(level, root);
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (Cell cell : Cell.values()) {
                BlockPos position = cellPosition(root, facing, cell);
                previous.put(position, level.getBlockState(position));
                if (!level.setBlock(position, stateFor(facing, cell, p.island()), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (Cell cell : Cell.values()) {
                BlockPos position = cellPosition(root, facing, cell);
                level.updateNeighborsAt(position, this);
                // cells were set with UPDATE_KNOWN_SHAPE: let a cable already under the port connect
                level.getBlockState(position).updateNeighbourShapes(level, position, UPDATE_ALL);
            }
            return true;
        } finally {
            if (!success) previous.forEach((position, oldState) -> {
                if (level.getBlockState(position).is(this)) level.setBlock(position, oldState, UPDATE_ALL);
            });
            MUTATIONS.remove(mutation);
            if (success && !level.isClientSide) level.scheduleTick(root, this, 1);
        }
    }

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

    /** A cell whose structure is incomplete removes itself. Needs no floor: it may stand on the cable to its port. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos root = rootPosition(position, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, root))) return;
        for (Cell cell : Cell.values()) {
            if (!level.hasChunkAt(cellPosition(root, facing, cell))) {
                level.scheduleTick(position, this, 100);
                return;
            }
        }
        for (Cell cell : Cell.values()) {
            BlockState other = level.getBlockState(cellPosition(root, facing, cell));
            if (!matches(other, facing, cell)) {
                level.removeBlock(position, false);
                return;
            }
        }
    }

    // ---- nozzles ----

    public enum Grade {
        GASOLINE(() -> AflItems.FUEL_NOZZLE_GASOLINE.get()),
        DIESEL(() -> AflItems.FUEL_NOZZLE_DIESEL.get());

        private final Supplier<Item> item;

        Grade(Supplier<Item> item) {
            this.item = item;
        }

        public Item nozzleItem() {
            return item.get();
        }
    }

    /**
     * The four nozzles (FuelDispenserBlockEntity indexes them by ordinal). outlet: where the hose leaves its outlet under
     * the header (the live hose starts there); hood: the hung nozzle's hood, where the prompt is drawn; source px from
     * tools/build-fuel-dispenser-v1.mjs stats.nozzles.
     */
    public enum Nozzle {
        FRONT_GASOLINE("front_gasoline", Grade.GASOLINE, new double[]{10.7, 39.3, -4.6}, new double[]{3.6, 15.85, -4.16}),
        FRONT_DIESEL("front_diesel", Grade.DIESEL, new double[]{-10.7, 39.3, -4.6}, new double[]{-3.6, 15.85, -4.16}),
        BACK_GASOLINE("back_gasoline", Grade.GASOLINE, new double[]{-10.7, 39.3, 4.6}, new double[]{-3.6, 15.85, 4.16}),
        BACK_DIESEL("back_diesel", Grade.DIESEL, new double[]{10.7, 39.3, 4.6}, new double[]{3.6, 15.85, 4.16});

        public final BooleanProperty property;
        public final Grade grade;
        private final double[] outlet, hood;

        Nozzle(String id, Grade grade, double[] outlet, double[] hood) {
            this.property = BooleanProperty.create(id);
            this.grade = grade;
            this.outlet = outlet;
            this.hood = hood;
        }

        public Vec3 outlet(BlockPos master, Direction facing) {
            return world(master, facing, outlet[0], outlet[1], outlet[2]);
        }

        public Vec3 hood(BlockPos master, Direction facing) {
            return world(master, facing, hood[0], hood[1], hood[2]);
        }

        /** The holster a hit aims at (source px), or null: the nozzle bay's height, the face by z, the side by x. */
        @Nullable
        public static Nozzle aimed(double[] local) {
            if (local[1] < AIM_Y0 || local[1] > AIM_Y1 || Math.abs(local[2]) < AIM_Z || Math.abs(local[0]) > AIM_X) return null;
            boolean plusX = local[0] > 0;
            // the front face: gasoline at +X (its customer's left); the back face is the front turned 180 degrees
            return local[2] < 0 ? (plusX ? FRONT_GASOLINE : FRONT_DIESEL) : (plusX ? BACK_DIESEL : BACK_GASOLINE);
        }
    }

    /** Source px (the model frame, see {@link #BOXES}) of a world point. */
    public static double[] local(Vec3 point, BlockPos master, Direction facing) {
        Direction side = facing.getClockWise();
        double dx = point.x - master.getX() - 0.5, dz = point.z - master.getZ() - 0.5;
        return new double[]{16 * (dx * side.getStepX() + dz * side.getStepZ()) - 8, 16 * (point.y - master.getY()),
                -16 * (dx * facing.getStepX() + dz * facing.getStepZ())};
    }

    /** World point of source px (the inverse of {@link #local}). */
    public static Vec3 world(BlockPos master, Direction facing, double x, double y, double z) {
        Direction side = facing.getClockWise();
        double along = (x + 8) / 16, across = z / 16;
        return new Vec3(master.getX() + 0.5 + side.getStepX() * along - facing.getStepX() * across, master.getY() + y / 16,
                master.getZ() + 0.5 + side.getStepZ() * along - facing.getStepZ() * across);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos master = rootPosition(position, state);
        BlockState masterState = level.getBlockState(master);
        Direction facing = state.getValue(FACING);
        if (!matches(masterState, facing, Cell.A0) || !(level.getBlockEntity(master) instanceof FuelDispenserBlockEntity dispenser)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof FuelNozzleItem) {
            Nozzle nozzle = FuelNozzleItem.nozzleOf(held, level, master);
            if (nozzle == null) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return dispenser.hangUp((ServerPlayer) player, nozzle) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (!held.isEmpty()) return InteractionResult.PASS;
        Nozzle nozzle = Nozzle.aimed(local(hit.getLocation(), master, facing));
        if (nozzle == null || !masterState.getValue(nozzle.property)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return dispenser.take((ServerPlayer) player, nozzle) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** What a click would do (WorldInteractionHint), and where to draw it: take the aimed nozzle, or hang the held one up. */
    public record Prompt(String key, Vec3 anchor) {}

    @Nullable
    public static Prompt prompt(BlockGetter level, BlockPos position, BlockState state, Vec3 hitLocation, Player player) {
        BlockPos master = rootPosition(position, state);
        BlockState masterState = level.getBlockState(master);
        if (!(masterState.getBlock() instanceof FuelDispenserBlock) || masterState.getValue(CELL) != Cell.A0) return null;
        Direction facing = masterState.getValue(FACING);
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof FuelNozzleItem) {
            Nozzle nozzle = player.level() == level ? FuelNozzleItem.nozzleOf(held, player.level(), master) : null;
            return nozzle == null ? null : new Prompt("hang", nozzle.hood(master, facing));
        }
        if (!held.isEmpty()) return null;
        Nozzle nozzle = Nozzle.aimed(local(hitLocation, master, facing));
        if (nozzle == null || !masterState.getValue(nozzle.property)) return null;
        return new Prompt(nozzle.grade == Grade.DIESEL ? "take_diesel" : "take_gasoline", nozzle.hood(master, facing));
    }

    // ---- power, lamp ----

    /** The one power port: the bottom face of the master cell, where an underground cable comes up (docs/models/power_cable_v2.md). */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return face == Direction.DOWN && state.getValue(CELL) == Cell.A0;
    }

    /** Lamp on / off: LIT on every cell (the master's baked lamp model follows it, the top cells give the light). */
    public void setLit(Level level, BlockPos master, boolean lit) {
        BlockState state = level.getBlockState(master);
        if (!state.is(this) || state.getValue(CELL) != Cell.A0 || state.getValue(LIT) == lit) return;
        Direction facing = state.getValue(FACING);
        for (Cell cell : Cell.values()) {
            BlockPos position = cellPosition(master, facing, cell);
            BlockState other = level.getBlockState(position);
            if (matches(other, facing, cell)) level.setBlock(position, other.setValue(LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ---- removal, drops ----

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos root = rootPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && state.getValue(CELL) != Cell.A0
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
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
        if (!state.is(replacement.getBlock())) {
            // nozzles out of a dispenser that is going away leave their holders' hands
            if (level.getBlockEntity(position) instanceof FuelDispenserBlockEntity dispenser) dispenser.releaseAll();
            BlockPos root = rootPosition(position, state);
            removeOthers(level, root, state.getValue(FACING), position);
            if (state.getValue(ISLAND)) restoreCurbs(level, root, state.getValue(FACING));
        }
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    /**
     * A dispenser set into an island gives its two curbs back when it goes. Queued for after the removal: putting a block
     * into the cell being removed from inside its own removal would make that removal fail, and its drop with it.
     */
    private static void restoreCurbs(Level level, BlockPos root, Direction facing) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState curb = AflBlocks.FUEL_ISLAND_CURB.get().defaultBlockState().setValue(FACING, facing);
        BlockPos a = cellPosition(root, facing, Cell.A0).immutable(), b = cellPosition(root, facing, Cell.B0).immutable();
        server.getServer().tell(new TickTask(server.getServer().getTickCount(), () -> {
            for (BlockPos pos : new BlockPos[]{a, b}) if (server.isLoaded(pos) && server.getBlockState(pos).isAir()) server.setBlock(pos, curb, UPDATE_ALL);
        }));
    }

    private void removeOthers(LevelAccessor level, BlockPos root, Direction facing, @Nullable BlockPos keep) {
        Mutation mutation = new Mutation(level, root.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Cell cell : Cell.values()) {
                BlockPos other = cellPosition(root, facing, cell);
                if ((keep == null || !other.equals(keep)) && level.hasChunkAt(other) && matches(level.getBlockState(other), facing, cell)) {
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

    // ---- shapes, rendering ----

    /** The master's baked model draws the whole dispenser (blockstates/fuel_dispenser.json); the other cells draw nothing. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(CELL) == Cell.A0 ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES.get(new ShapeKey(state.getValue(CELL), state.getValue(FACING)));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    private static Map<ShapeKey, VoxelShape> buildShapes() {
        Map<ShapeKey, VoxelShape> shapes = new HashMap<>();
        for (Cell cell : Cell.values()) {
            Map<Direction, VoxelShape> rotations = HorizontalShapeUtils.rotations(cellShape(cell));
            for (Direction facing : Direction.Plane.HORIZONTAL) shapes.put(new ShapeKey(cell, facing), rotations.get(facing));
        }
        return Map.copyOf(shapes);
    }

    /** One cell's part of the boxes (north-facing block px). */
    private static VoxelShape cellShape(Cell cell) {
        double ox = -16 + 16 * cell.dx, oy = 16 * cell.dy;
        VoxelShape shape = Shapes.empty();
        for (double[] box : BOXES) {
            double x0 = Math.max(box[0], ox), x1 = Math.min(box[3], ox + 16), y0 = Math.max(box[1], oy), y1 = Math.min(box[4], oy + 16);
            if (x1 <= x0 || y1 <= y0) continue;
            shape = Shapes.or(shape, Block.box(x0 - ox, y0 - oy, box[2] + 8, x1 - ox, y1 - oy, box[5] + 8));
        }
        return shape.optimize();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(CELL) == Cell.A0 ? new FuelDispenserBlockEntity(position, state) : null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(CELL) != Cell.A0) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<FuelDispenserBlockEntity>) (tickerLevel, tickerPos, tickerState, dispenser) -> dispenser.serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, ISLAND, LIT);
        for (Nozzle nozzle : Nozzle.values()) builder.add(nozzle.property);
    }

    /** The six cells: a = the master's column, b = the column on its clockwise side; 0..2 = the level. */
    public enum Cell implements StringRepresentable {
        A0(0, 0), B0(1, 0), A1(0, 1), B1(1, 1), A2(0, 2), B2(1, 2);

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
