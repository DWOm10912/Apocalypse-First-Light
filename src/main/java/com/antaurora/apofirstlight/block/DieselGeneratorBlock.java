package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.fluid.FuelPourTarget;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Diesel standby generator V1 (docs/machines/diesel_standby_generator_v1.md, tools/build-diesel-generator-v1.mjs): a
 * canopy genset of about 100 kW on its sub-base fuel tank, 3 cells long (along FACING's clockwise side), 1 deep, 2 high.
 * The master is the middle column's bottom cell (C1R0): it draws the whole mesh, holds the block entity and has the
 * standard steel power port in its back face centre. FACING is the front (the service doors, the instrument panel),
 * toward the player who places it. No opened screen:
 * <ul>
 *   <li>right-click the instrument panel: start (the key to START, the engine cranks 3 s, then RUN) or stop (OFF); the
 *   emergency stop under it only stops;</li>
 *   <li>right-click a service door (or the open bay): both front doors open / close (all cells keep the same OPEN);</li>
 *   <li>right-click the fill box on the tank step: its lid opens / closes (FILL); with the lid open a jerry can poured at
 *   the generator fills the tank ({@link FuelPourTarget}).</li>
 * </ul>
 * Fuel also comes by pipe (2026-10-09, user): the standard fluid port on the radiator end, under the outlet louvre, the end
 * face of c2r0 (FACING's clockwise side; not the step end, where it crowded the fill box), fills the tank (block entity
 * DieselGeneratorPortBlockEntity on that cell; fill only).
 * Regions are in the structure frame (north-facing px: x from the first column's west edge, y up, z 0 = the front),
 * from the generator's FACTS.
 */
public class DieselGeneratorBlock extends RectMultiblockBlock<DieselGeneratorBlock.Cell> implements EntityBlock, AflPowerPortBlock, FuelPourTarget,
        com.antaurora.apofirstlight.fluid.AflFluidPortBlock {
    public enum Cell implements StringRepresentable, RectMultiblockBlock.GridCell {
        C0R0, C1R0, C2R0, C0R1, C1R1, C2R1;

        @Override public int col() { return ordinal() % 3; }
        @Override public int row() { return ordinal() / 3; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    /** The front service doors. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** The fill box's lid. */
    public static final BooleanProperty FILL = BooleanProperty.create("fill");
    // structure frame px (tools/build-diesel-generator-v1.mjs FACTS)
    private static final double[] TANK = {2.56, 0, 0.24, 45.44, 4.832, 15.76};
    private static final double[] CANOPY = {6.24, 4.8, 0.4, 45.76, 25.44, 15.6};
    private static final double[] FILL_BOX = {3.264, 4.8, 2.544, 6.336, 6.912, 7.616};
    private static final double[] STACK = {38.3, 25.4, 11.2, 39.9, 28.8, 12.8};
    private static final double[] PORT = {21, 5, 15.4, 27, 11, 16};
    /** The fuel supply box with the standard fluid port on the radiator end (its plate's face on the cell boundary, x 48). */
    private static final double[] FLUID_PORT = {44.96, 2, 2, 48, 13.76, 14};
    /** The fill box's four walls (its lid open: the stream from a can falls in, fluid/LiquidJet clips collision shapes). */
    private static final double[][] FILL_WALLS = {{3.264, 4.8, 2.544, 3.46, 6.912, 7.616}, {6.14, 4.8, 2.544, 6.336, 6.912, 7.616},
            {3.46, 4.8, 2.544, 6.14, 6.912, 2.74}, {3.46, 4.8, 7.42, 6.14, 6.912, 7.616}};
    // interaction regions (structure px): the panel and the e-stop on the right door, the doors, the fill box (and its open lid)
    private static final double[] PANEL = {11.6, 15.0, -0.1, 19.9, 21.8, 1.2};
    private static final double[] ESTOP = {14.8, 11.8, -0.1, 16.8, 13.8, 1.2};
    private static final double[] DOORS = {8.8, 5.6, -0.1, 43.2, 23.2, 1.6};
    private static final double[] BAY = {6.24, 4.8, -0.1, 45.76, 25.44, 4.0};
    private static final double[] FILL_REGION = {2.4, 4.4, 1.2, 7.2, 11.0, 8.4};
    /** Where the fill box's opening is (the neck), structure px. */
    public static final Vec3 FILL_OPENING = new Vec3(4.8, 6.24, 5.12);
    /** The exhaust stack's top, structure px. */
    public static final Vec3 STACK_TOP = new Vec3(39.12, 28.8, 12);
    /** The panel's middle and the e-stop, structure px (hint anchors, sounds). */
    public static final Vec3 PANEL_MIDDLE = new Vec3(15.76, 18.4, 0.4), DOOR_MIDDLE = new Vec3(26, 14.4, 0.5);
    private static final float PITCH_SPREAD = 0.06F;
    private final java.util.Map<String, net.minecraft.world.phys.shapes.VoxelShape> openFillShapes = new java.util.concurrent.ConcurrentHashMap<>();

    public DieselGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.C1R0)
                .setValue(OPEN, false).setValue(FILL, false));
    }

    @Override protected EnumProperty<Cell> cellProperty() { return CELL; }
    @Override protected Cell[] cells() { return Cell.values(); }
    @Override public Cell master() { return Cell.C1R0; }
    @Override protected double[][] boxes() { return new double[][]{TANK, CANOPY, FILL_BOX, STACK, PORT, FLUID_PORT}; }

    // ---- the structure frame ----

    /** A world point in the structure frame (px), from the structure's first column bottom cell and its facing. */
    public static Vec3 toStructure(Vec3 world, BlockPos origin, Direction facing) {
        double wx = (world.x - origin.getX()) * 16, wy = (world.y - origin.getY()) * 16, wz = (world.z - origin.getZ()) * 16;
        return switch (facing) {
            case EAST -> new Vec3(wz, wy, 16 - wx);
            case SOUTH -> new Vec3(16 - wx, wy, 16 - wz);
            case WEST -> new Vec3(16 - wz, wy, wx);
            default -> new Vec3(wx, wy, wz);
        };
    }

    /** A structure-frame point (px) in the world. */
    public static Vec3 toWorld(Vec3 s, BlockPos origin, Direction facing) {
        double x = s.x, z = s.z;
        double wx, wz;
        switch (facing) {
            case EAST -> { wx = 16 - z; wz = x; }
            case SOUTH -> { wx = 16 - x; wz = 16 - z; }
            case WEST -> { wx = z; wz = 16 - x; }
            default -> { wx = x; wz = z; }
        }
        return new Vec3(origin.getX() + wx / 16, origin.getY() + s.y / 16, origin.getZ() + wz / 16);
    }

    public Vec3 world(BlockPos pos, BlockState state, Vec3 structure) {
        return toWorld(structure, origin(pos, state), state.getValue(FACING));
    }

    private static boolean in(Vec3 p, double[] b) {
        return p.x >= b[0] && p.x <= b[3] && p.y >= b[1] && p.y <= b[4] && p.z >= b[2] && p.z <= b[5];
    }

    /** What a click at {@code hit} does (also the hint's prompt), or null. */
    public enum Action { START, STOP, ESTOP, OPEN_DOORS, CLOSE_DOORS, OPEN_FILL, CLOSE_FILL, POUR }

    @Nullable
    public Action action(BlockGetter level, BlockPos pos, BlockState state, Vec3 hit, Player player) {
        if (!(level.getBlockEntity(masterPosition(pos, state)) instanceof DieselGeneratorBlockEntity generator)) return null;
        Vec3 local = toStructure(hit, origin(pos, state), state.getValue(FACING));
        boolean open = state.getValue(OPEN), fill = state.getValue(FILL);
        // a jerry can at the open fill box pours (FuelCanItem#use starts it)
        var held = player.getMainHandItem().getItem();
        if (fill && (held instanceof FuelCanItem can && can.pours() || held instanceof com.antaurora.apofirstlight.item.CreativeFuelBarrelItem))
            return in(local, FILL_REGION) ? Action.POUR : null;
        if (in(local, FILL_REGION)) return fill ? Action.CLOSE_FILL : Action.OPEN_FILL;
        if (!open && in(local, ESTOP)) return generator.engineOn() ? Action.ESTOP : null;
        if (!open && in(local, PANEL)) return generator.engineOn() ? Action.STOP : Action.START;
        if (!open && in(local, DOORS)) return Action.OPEN_DOORS;
        if (open && in(local, BAY)) return Action.CLOSE_DOORS;
        return null;
    }

    /** The prompt anchor for an action (world). */
    public Vec3 anchor(BlockPos pos, BlockState state, Action action, Vec3 hit) {
        return switch (action) {
            case START, STOP -> world(pos, state, PANEL_MIDDLE.add(0, 4.2, -0.4));
            case ESTOP -> world(pos, state, new Vec3(15.76, 14.4, -0.4));
            case OPEN_FILL, CLOSE_FILL, POUR -> world(pos, state, FILL_OPENING.add(0, 4.0, 0));
            default -> hit;
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        Action action = action(level, pos, state, hit.getLocation(), player);
        if (action == null || action == Action.POUR) return InteractionResult.PASS;
        BlockPos master = masterPosition(pos, state);
        switch (action) {
            case START, STOP, ESTOP -> {
                if (!level.isClientSide && level.getBlockEntity(master) instanceof DieselGeneratorBlockEntity generator) {
                    if (action == Action.START) generator.start(player); else generator.stop(action == Action.ESTOP);
                }
            }
            case OPEN_DOORS, CLOSE_DOORS -> {
                boolean open = action == Action.OPEN_DOORS;
                setAll(level, pos, state, OPEN, open);
                Vec3 at = world(pos, state, DOOR_MIDDLE);
                level.playSound(player, at.x, at.y, at.z, open ? AflSounds.DISTRIBUTION_PANEL_OPEN.get() : AflSounds.DISTRIBUTION_PANEL_CLOSE.get(),
                        SoundSource.BLOCKS, 1.0F, 0.82F + (level.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD);
                level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, master);
            }
            case OPEN_FILL, CLOSE_FILL -> {
                boolean open = action == Action.OPEN_FILL;
                setAll(level, pos, state, FILL, open);
                Vec3 at = world(pos, state, FILL_OPENING);
                level.playSound(player, at.x, at.y, at.z, open ? AflSounds.DISTRIBUTION_PANEL_OPEN.get() : AflSounds.DISTRIBUTION_PANEL_CLOSE.get(),
                        SoundSource.BLOCKS, 0.7F, 1.25F + (level.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD);
                level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, master);
            }
            default -> {}
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void setAll(Level level, BlockPos pos, BlockState state, BooleanProperty property, boolean value) {
        BlockPos origin = origin(pos, state);
        Direction facing = state.getValue(FACING);
        for (Cell cell : Cell.values()) {
            BlockPos at = cellPosition(origin, facing, cell);
            BlockState other = level.getBlockState(at);
            if (other.is(this) && other.getValue(CELL) == cell) level.setBlock(at, other.setValue(property, value), UPDATE_CLIENTS | UPDATE_NEIGHBORS);
        }
    }

    // ---- fuel: the fill box takes a can's pour while its lid is open ----

    @Override
    public boolean takesPour(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(this) && state.getValue(FILL);
    }

    @Override
    public Vec3 pourOpening(BlockGetter level, BlockPos pos, BlockState state) {
        return world(pos, state, FILL_OPENING);
    }

    @Override
    @Nullable
    public IFluidHandler pourHandler(ServerLevel level, BlockPos pos, BlockState state) {
        return takesPour(level, pos, state) && level.getBlockEntity(masterPosition(pos, state)) instanceof DieselGeneratorBlockEntity generator ? generator.fuelTank() : null;
    }

    @Override
    public String refusal() {
        return "diesel_only";
    }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if (!state.getValue(FILL)) return super.getCollisionShape(state, level, pos, context);
        Cell cell = state.getValue(CELL);
        Direction facing = state.getValue(FACING);
        return openFillShapes.computeIfAbsent(cell.getSerializedName() + "/" + facing.getName(), k -> {
            double[][] boxes = {TANK, CANOPY, STACK, PORT, FLUID_PORT, FILL_WALLS[0], FILL_WALLS[1], FILL_WALLS[2], FILL_WALLS[3]};
            double ox = 16 * cell.col(), oy = 16 * cell.row();
            net.minecraft.world.phys.shapes.VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.empty();
            for (double[] b : boxes) {
                double x0 = Math.max(b[0], ox), x1 = Math.min(b[3], ox + 16), y0 = Math.max(b[1], oy), y1 = Math.min(b[4], oy + 16);
                if (x1 > x0 && y1 > y0) shape = net.minecraft.world.phys.shapes.Shapes.or(shape, Block.box(x0 - ox, y0 - oy, b[2], x1 - ox, y1 - oy, b[5]));
            }
            return HorizontalShapeUtils.rotations(shape.optimize()).get(facing);
        });
    }

    // ---- fuel by pipe: the standard fluid port on the radiator end (c2r0's outer end face) ----

    /** The cell holding the fluid port and its forwarding block entity. */
    public static final Cell FLUID_PORT_CELL = Cell.C2R0;

    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return state.getValue(CELL) == FLUID_PORT_CELL && face == state.getValue(FACING).getClockWise();
    }

    // ---- power: the standard port in the master's back face centre ----

    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return isMaster(state) && face == state.getValue(FACING).getOpposite();
    }

    // ---- block entity ----

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isMaster(state)) return new DieselGeneratorBlockEntity(pos, state);
        return state.getValue(CELL) == FLUID_PORT_CELL ? new com.antaurora.apofirstlight.blockentity.DieselGeneratorPortBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AflBlockEntities.DIESEL_GENERATOR.get() || !isMaster(state)) return null;
        return level.isClientSide
                ? (l, p, s, be) -> com.antaurora.apofirstlight.client.DieselGeneratorClient.tick((DieselGeneratorBlockEntity) be)
                : (l, p, s, be) -> ((DieselGeneratorBlockEntity) be).serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, OPEN, FILL);
    }
}
