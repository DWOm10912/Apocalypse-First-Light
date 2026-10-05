package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.IntakePumpBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.fluid.AflFluidPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intake Pump V1 (2026-10-05, tools/build-intake-pump-v1.mjs, docs/models/intake_pump_v1.md): a two-block bank-side pump
 * set. FACING points from the BANK cell (the master, on the ground: pump, motor, starter box, the block entity and the
 * drop) to the FRONT cell, which reaches over the liquid; the suction line drops from the front cell into the block below
 * it, the only block the pump draws from (IntakePumpBlockEntity). Placed from the clicked cell (the bank cell) toward the
 * player's horizontal facing; the front cell needs no liquid under it (the lamp then shows amber).
 * <p>
 * Ports: the AFL fluid port on the bank cell's top face (the discharge, docs/models/fluid_pipe_v2.md "流体接口规格") and
 * the AFL power port on the bank cell's face to the right of FACING (FACING.getClockWise(): the right hand of a player on
 * the bank looking at the liquid), on the starter box. The suction is not a port.
 * <p>
 * A click with an empty main hand on either cell turns the rotary isolator on or off ({@link #ON}: the handle model);
 * {@link #LAMP} is the status lamp, set by the block entity. No redstone.
 */
public final class IntakePumpBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock, AflFluidPortBlock {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** The rotary isolator (bank cell): handle vertical when on. */
    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** The status lamp (bank cell): dark (off or no power), amber (on, not pumping), green (pumping). */
    public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);

    /** Model px boxes (the bank cell's centre at the origin, front toward -Z, y up from -8) of the collision shapes. */
    private static final double[][] BOXES = {
            {-6.0, -8.0, -22.4, 6.0, -6.6, 7.4},       // skid, both cells
            {-4.6, -6.6, -2.4, 4.6, 3.4, 7.8},         // volute, motor, fan cowl
            {4.2, -6.6, -3.6, 8.0, 4.1, 7.2},          // starter box with the power port
            {-2.6, 3.4, -2.6, 2.6, 6.6, 2.6},          // discharge neck and reducer
            {-5.5, 6.6, -5.5, 5.5, 8.0, 5.5},          // the fluid port
            {-2.8, -4.6, -16.0, 2.8, 1.0, -2.0},       // suction line
            {-2.8, -8.0, -18.8, 2.8, -1.6, -13.2},     // elbow and the drop down to the cell's bottom
    };
    /** Model px of the isolator handle and the lamp (hint anchors). */
    public static final double[] SWITCH = {7.7, 0.6, 5.0}, LAMP_POINT = {7.4, -2.6, 5.0};

    private record Mutation(LevelAccessor level, BlockPos bank) {}
    private record ShapeKey(Part part, Direction facing) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<ShapeKey, VoxelShape> SHAPES = buildShapes();

    public IntakePumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BANK)
                .setValue(ON, false).setValue(LAMP, Lamp.OFF));
    }

    // ---- cells ----

    public static BlockPos bankPosition(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.BANK ? position : position.relative(state.getValue(FACING).getOpposite());
    }

    public static boolean isBank(BlockState state) {
        return state.getValue(PART) == Part.BANK;
    }

    /** The block the pump draws from: the one under the front cell. */
    public static BlockPos sourcePosition(BlockPos bank, Direction facing) {
        return bank.relative(facing).below();
    }

    private BlockState stateFor(Direction facing, Part part) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part);
    }

    private boolean matches(BlockState state, Direction facing, Part part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    private static BlockPos cellPosition(BlockPos bank, Direction facing, Part part) {
        return part == Part.BANK ? bank : bank.relative(facing);
    }

    /** World point of model px (the inverse frame of the generator's: +X = FACING.getClockWise(), -Z = FACING). */
    public static Vec3 world(BlockPos bank, Direction facing, double x, double y, double z) {
        Direction side = facing.getClockWise();
        return new Vec3(bank.getX() + 0.5 + (side.getStepX() * x - facing.getStepX() * z) / 16, bank.getY() + 0.5 + y / 16,
                bank.getZ() + 0.5 + (side.getStepZ() * x - facing.getStepZ() * z) / 16);
    }

    // ---- placement ----

    private boolean canPlace(BlockPlaceContext context, BlockPos bank, Direction facing) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        for (Part part : Part.values()) {
            BlockPos position = cellPosition(bank, facing, part);
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            if (position.getY() < level.getMinBuildHeight() || position.getY() >= level.getMaxBuildHeight()) return false;
            if (player != null && (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
            if (!level.getBlockState(position).canBeReplaced(BlockPlaceContext.at(context, position, Direction.UP))
                    || !level.getFluidState(position).isEmpty()) return false;
            if (!level.isUnobstructed(stateFor(facing, part), position, CollisionContext.empty())) return false;
        }
        return true;
    }

    /** The clicked cell is the bank cell; the front cell goes the way the player faces. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        return canPlace(context, context.getClickedPos(), facing) ? stateFor(facing, Part.BANK) : null;
    }

    /** Runs inside BlockItem's placement (IntakePumpItem); a failed write restores both cells. */
    public boolean placeStructure(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos bank = context.getClickedPos().immutable();
        if (!canPlace(context, bank, facing)) return false;
        Level level = context.getLevel();
        Mutation mutation = new Mutation(level, bank);
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new HashMap<>();
        boolean success = false;
        try {
            for (Part part : Part.values()) {
                BlockPos position = cellPosition(bank, facing, part);
                previous.put(position, level.getBlockState(position));
                if (!level.setBlock(position, stateFor(facing, part), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (Part part : Part.values()) {
                BlockPos position = cellPosition(bank, facing, part);
                level.updateNeighborsAt(position, this);
                // cells were set with UPDATE_KNOWN_SHAPE: let a pipe over the port or a cable at the box connect
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

    /** A cell whose other half is gone removes itself. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos bank = bankPosition(position, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, bank))) return;
        for (Part part : Part.values()) {
            BlockPos cell = cellPosition(bank, facing, part);
            if (!level.hasChunkAt(cell)) {
                level.scheduleTick(position, this, 100);
                return;
            }
            if (!matches(level.getBlockState(cell), facing, part)) {
                level.removeBlock(position, false);
                return;
            }
        }
    }

    /** A survival player breaking the front cell gets the pump (the bank cell's loot table drops it otherwise). */
    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos bank = bankPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && !isBank(state) && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, bank, new ItemStack(this));
        }
        removeOther(level, bank, state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return isBank(state) ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) removeOther(level, bankPosition(position, state), state.getValue(FACING), position);
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removeOther(LevelAccessor level, BlockPos bank, Direction facing, BlockPos keep) {
        Mutation mutation = new Mutation(level, bank.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos other = cellPosition(bank, facing, part);
                if (!other.equals(keep) && level.hasChunkAt(other) && matches(level.getBlockState(other), facing, part)) {
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

    // ---- switch ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        BlockPos bank = bankPosition(position, state);
        BlockState bankState = level.getBlockState(bank);
        if (!matches(bankState, state.getValue(FACING), Part.BANK)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean on = !bankState.getValue(ON);
        BlockState next = bankState.setValue(ON, on);
        if (!on) next = next.setValue(LAMP, Lamp.OFF);
        if (!on && bankState.getValue(LAMP) == Lamp.RUN) IntakePumpBlockEntity.playMotor(level, bank, bankState.getValue(FACING), false);
        level.setBlock(bank, next, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        Vec3 at = world(bank, bankState.getValue(FACING), SWITCH[0], SWITCH[1], SWITCH[2]);
        level.playSound(null, at.x, at.y, at.z, com.antaurora.apofirstlight.registry.AflSounds.INTAKE_PUMP_SWITCH.get(), SoundSource.BLOCKS, 1.0F, on ? 1.0F : 0.92F);
        return InteractionResult.CONSUME;
    }

    /** What a click would do (WorldInteractionHint): "on" / "off", drawn at the isolator handle. */
    public record Prompt(String key, Vec3 anchor) {}

    @Nullable
    public static Prompt prompt(BlockGetter level, BlockPos position, BlockState state, Player player) {
        if (!player.getMainHandItem().isEmpty()) return null;
        BlockPos bank = bankPosition(position, state);
        BlockState bankState = level.getBlockState(bank);
        if (!(bankState.getBlock() instanceof IntakePumpBlock) || !isBank(bankState)) return null;
        return new Prompt(bankState.getValue(ON) ? "off" : "on", world(bank, bankState.getValue(FACING), SWITCH[0], SWITCH[1], SWITCH[2]));
    }

    // ---- ports ----

    /** The power port: the bank cell's face to the right of FACING, on the starter box (docs/models/power_cable_v2.md). */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return isBank(state) && face == state.getValue(FACING).getClockWise();
    }

    /** The fluid port: the bank cell's top face, the discharge (docs/models/fluid_pipe_v2.md, "流体接口规格"). */
    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return isBank(state) && face == Direction.UP;
    }

    // ---- shapes, rendering ----

    /** The bank cell's baked model draws the whole pump set (blockstates/intake_pump.json); the front cell draws nothing. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isBank(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES.get(new ShapeKey(state.getValue(PART), state.getValue(FACING)));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    private static Map<ShapeKey, VoxelShape> buildShapes() {
        Map<ShapeKey, VoxelShape> shapes = new HashMap<>();
        for (Part part : Part.values()) {
            Map<Direction, VoxelShape> rotations = HorizontalShapeUtils.rotations(cellShape(part));
            for (Direction facing : Direction.Plane.HORIZONTAL) shapes.put(new ShapeKey(part, facing), rotations.get(facing));
        }
        return Map.copyOf(shapes);
    }

    /** One cell's part of the boxes, north-facing block px (the front cell is the model's z -24..-8). */
    private static VoxelShape cellShape(Part part) {
        double oz = part == Part.BANK ? -8 : -24;
        VoxelShape shape = Shapes.empty();
        for (double[] box : BOXES) {
            double z0 = Math.max(box[2], oz), z1 = Math.min(box[5], oz + 16), y0 = Math.max(box[1], -8), y1 = Math.min(box[4], 8);
            if (z1 <= z0 || y1 <= y0) continue;
            shape = Shapes.or(shape, Block.box(box[0] + 8, y0 + 8, z0 - oz, box[3] + 8, y1 + 8, z1 - oz));
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

    // ---- block entity ----

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isBank(state) ? new IntakePumpBlockEntity(position, state) : null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !isBank(state)) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<IntakePumpBlockEntity>) (tickerLevel, tickerPos, tickerState, pump) -> pump.serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, ON, LAMP);
    }

    public enum Part implements StringRepresentable {
        BANK, FRONT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Lamp implements StringRepresentable {
        OFF, IDLE, RUN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
