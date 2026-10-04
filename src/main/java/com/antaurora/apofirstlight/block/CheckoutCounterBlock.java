package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.CheckoutCounterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Checkout Counter V1 (docs/models/checkout_counter_v1.md): a modular store counter that joins its neighbours like
 * stairs. FACING is the customer side. SHAPE is computed: a perpendicular counter behind makes an outer corner (the
 * customer side wraps round the outside), one in front an inner corner; the four side flags say which horizontal
 * neighbours are counter pieces (counters or the pass-through gate), so the models put finished end caps only where a
 * line really ends. Plain and display (impulse trays on the customer face of straight pieces) are two blocks of this
 * class. Every piece holds a 9-slot searchable container (3 x 3), opened from a non-customer side (or the display trays);
 * the top (flush with the block top, a full support face) stays free for placing things on it, such as the cash register.
 */
public class CheckoutCounterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Shape implements StringRepresentable {
        STRAIGHT("straight"), OUTER_LEFT("outer_left"), OUTER_RIGHT("outer_right"), INNER_LEFT("inner_left"), INNER_RIGHT("inner_right");

        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);

    // canonical shapes (facing north, customer side -Z), px; tools/build-checkout-counter-v1.mjs COUNTER
    private static final VoxelShape STRAIGHT_SHAPE = Block.box(0, 0, 2.15, 16, 16, 15.6);
    private static final VoxelShape DISPLAY_SHAPE = Block.box(0, 0, 0.2, 16, 16, 15.6);
    private static final VoxelShape OUTER_SHAPE = Block.box(0, 0, 2.15, 13.85, 16, 16);
    private static final VoxelShape INNER_SHAPE = Shapes.or(Block.box(0, 0, 2.15, 15.6, 16, 15.6), Block.box(2.15, 0, 0, 15.6, 16, 2.15));
    /** The top is flush with the block top: a full top face for support (a cash register, a lantern), nothing on the sides. */
    private static final VoxelShape SUPPORT_TOP = Block.box(0, 15, 0, 16, 16, 16);
    private static final Map<Direction, VoxelShape> STRAIGHT_SHAPES = rotations(STRAIGHT_SHAPE), DISPLAY_SHAPES = rotations(DISPLAY_SHAPE),
            OUTER_SHAPES = rotations(OUTER_SHAPE), INNER_SHAPES = rotations(INNER_SHAPE);

    private final boolean display;

    public CheckoutCounterBlock(Properties properties, boolean display) {
        super(properties);
        this.display = display;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.STRAIGHT)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    /** The impulse-tray variant. */
    public boolean isDisplay() {
        return display;
    }

    // ---- joining ----

    /** Pieces that make corners: the counters (plain or display). */
    public static boolean isCounter(BlockState state) {
        return state.getBlock() instanceof CheckoutCounterBlock;
    }

    /** Pieces a counter line continues into (no end cap toward them): counters and the gate. */
    public static boolean joins(BlockState state) {
        return isCounter(state) || state.getBlock() instanceof CheckoutCounterGateBlock;
    }

    private static boolean sameLine(LevelAccessor level, BlockPos at, Direction facing) {
        BlockState state = level.getBlockState(at);
        return joins(state) && state.getValue(FACING) == facing;
    }

    /** The shape and the side flags for this state where it stands. */
    public static BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        for (var side : SIDES.entrySet()) state = state.setValue(side.getValue(), joins(level.getBlockState(pos.relative(side.getKey()))));
        return state.setValue(SHAPE, shapeAt(state.getValue(FACING), level, pos));
    }

    private static Shape shapeAt(Direction facing, LevelAccessor level, BlockPos pos) {
        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        if (isCounter(back)) {
            Direction turn = back.getValue(FACING);
            if (turn == facing.getClockWise() && !sameLine(level, pos.relative(facing.getClockWise()), facing)) return Shape.OUTER_RIGHT;
            if (turn == facing.getCounterClockWise() && !sameLine(level, pos.relative(facing.getCounterClockWise()), facing)) return Shape.OUTER_LEFT;
        }
        BlockState front = level.getBlockState(pos.relative(facing));
        if (isCounter(front)) {
            Direction turn = front.getValue(FACING);
            if (turn == facing.getCounterClockWise() && !sameLine(level, pos.relative(facing.getClockWise()), facing)) return Shape.INNER_LEFT;
            if (turn == facing.getClockWise() && !sameLine(level, pos.relative(facing.getCounterClockWise()), facing)) return Shape.INNER_RIGHT;
        }
        return Shape.STRAIGHT;
    }

    /** The direction the shape's canonical model is turned to (as the blockstate places it). */
    public static Direction modelFacing(BlockState state) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(SHAPE)) {
            case STRAIGHT, OUTER_RIGHT, INNER_LEFT -> facing;
            case OUTER_LEFT -> facing.getCounterClockWise();
            case INNER_RIGHT -> facing.getClockWise();
        };
    }

    /** True for a side the customer stands on (not a way into the cubbies). */
    public static boolean customerSide(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == facing || switch (state.getValue(SHAPE)) {
            case STRAIGHT -> false;
            case OUTER_RIGHT, INNER_RIGHT -> side == facing.getClockWise();
            case OUTER_LEFT, INNER_LEFT -> side == facing.getCounterClockWise();
        };
    }

    /** Whether a click on this face opens the container: any side but the customer's; on display pieces also the trays. */
    public boolean opensFrom(BlockState state, Direction face) {
        if (!face.getAxis().isHorizontal()) return false;
        if (display && state.getValue(SHAPE) == Shape.STRAIGHT && face == state.getValue(FACING)) return true;
        return !customerSide(state, face);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis().isHorizontal() ? connect(state, level, pos) : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (var side : SIDES.entrySet()) turned = turned.setValue(SIDES.get(rotation.rotate(side.getKey())), state.getValue(side.getValue()));
        return turned;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (var side : SIDES.entrySet()) mirrored = mirrored.setValue(SIDES.get(mirror.mirror(side.getKey())), state.getValue(side.getValue()));
        Shape shape = state.getValue(SHAPE);
        if (mirror != Mirror.NONE) shape = switch (shape) {   // a mirror swaps the hand of a corner
            case STRAIGHT -> Shape.STRAIGHT;
            case OUTER_LEFT -> Shape.OUTER_RIGHT;
            case OUTER_RIGHT -> Shape.OUTER_LEFT;
            case INNER_LEFT -> Shape.INNER_RIGHT;
            case INNER_RIGHT -> Shape.INNER_LEFT;
        };
        return mirrored.setValue(SHAPE, shape);
    }

    // ---- container ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || !opensFrom(state, hit.getDirection())) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof CheckoutCounterBlockEntity counter)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.openMenu(counter);
        return InteractionResult.CONSUME;
    }

    /** A counter placed by a player holds the player's own things, never searched. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CheckoutCounterBlockEntity counter) counter.markPlacedByPlayer();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CheckoutCounterBlockEntity counter) counter.dropContentsOnce();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CheckoutCounterBlockEntity(pos, state);
    }

    /** Server: rolls pending world loot at once, so the goods show from the start. */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CheckoutCounterBlockEntity>) (tickerLevel, tickerPos, tickerState, counter) -> counter.serverTick();
    }

    // ---- shapes ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction turned = modelFacing(state);
        return switch (state.getValue(SHAPE)) {
            case STRAIGHT -> (display ? DISPLAY_SHAPES : STRAIGHT_SHAPES).get(turned);
            case OUTER_LEFT, OUTER_RIGHT -> OUTER_SHAPES.get(turned);
            case INNER_LEFT, INNER_RIGHT -> INNER_SHAPES.get(turned);
        };
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SUPPORT_TOP;
    }

    /** A north-facing shape turned to each horizontal facing, as the blockstates turn the models. */
    static Map<Direction, VoxelShape> rotations(VoxelShape north) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] turned = {Shapes.empty()};
            north.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                double x0 = minX, x1 = maxX, z0 = minZ, z1 = maxZ;
                switch (facing) {
                    case SOUTH -> { x0 = 1 - maxX; x1 = 1 - minX; z0 = 1 - maxZ; z1 = 1 - minZ; }
                    case EAST -> { x0 = 1 - maxZ; x1 = 1 - minZ; z0 = minX; z1 = maxX; }
                    case WEST -> { x0 = minZ; x1 = maxZ; z0 = 1 - maxX; z1 = 1 - minX; }
                    default -> { }
                }
                turned[0] = Shapes.or(turned[0], Shapes.box(x0, minY, z0, x1, maxY, z1));
            });
            out.put(facing, turned[0].optimize());
        }
        return out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, NORTH, EAST, SOUTH, WEST);
    }
}
