package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Two-cell chest freezer. Logical LEFT/master is the visual left half; front is -Z. */
public final class ChestFreezerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<LidState> LID = EnumProperty.create("lid", LidState.class);
    public static final int ANIMATION_TICKS = 14; // four source animations are 0.70 seconds

    private record Mutation(LevelAccessor level, BlockPos master) {}
    private record ShapeKey(Part part, Direction facing, LidState lid) {}
    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<ShapeKey, VoxelShape> SHAPES = buildShapes();

    public ChestFreezerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.LEFT).setValue(LID, LidState.CLOSED));
    }

    /** For NORTH, LEFT/master occupies the east cell and RIGHT/slave is west. */
    public static BlockPos partPosition(BlockPos master, Direction facing, Part part) {
        return part == Part.LEFT ? master : master.relative(facing.getCounterClockWise());
    }

    public static BlockPos masterPosition(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.LEFT ? position
                : position.relative(state.getValue(FACING).getClockWise());
    }

    private BlockState stateFor(Direction facing, Part part, LidState lid) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part).setValue(LID, lid);
    }

    private boolean matches(BlockState state, Direction facing, Part part, LidState lid) {
        return state.is(this) && state.getValue(FACING) == facing
                && state.getValue(PART) == part && state.getValue(LID) == lid;
    }

    public boolean canPlaceStructure(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        for (Part part : Part.values()) {
            BlockPos position = partPosition(master, facing, part);
            if (position.getY() < level.getMinBuildHeight() || position.getY() >= level.getMaxBuildHeight()
                    || !level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            if (!level.getBlockState(position).canBeReplaced(BlockPlaceContext.at(context, position, Direction.UP))
                    || !level.getFluidState(position).isEmpty()
                    || !level.isUnobstructed(stateFor(facing, part, LidState.CLOSED), position,
                    CollisionContext.empty())) return false;
            Player player = context.getPlayer();
            if (player != null && (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
        }
        return supported(level, master, facing);
    }

    private static boolean supported(LevelReader level, BlockPos master, Direction facing) {
        for (Part part : Part.values()) {
            BlockPos floor = partPosition(master, facing, part).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }

    @Override @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlaceStructure(context, facing) ? stateFor(facing, Part.LEFT, LidState.CLOSED) : null;
    }

    /** Invoked from the BlockItem's single placement transaction. */
    public boolean placeStructure(BlockPlaceContext context, BlockState masterState) {
        Direction facing = masterState.getValue(FACING);
        if (!canPlaceStructure(context, facing)) return false;
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos().immutable();
        BlockPos right = partPosition(master, facing, Part.RIGHT);
        BlockState oldLeft = level.getBlockState(master);
        BlockState oldRight = level.getBlockState(right);
        Mutation mutation = new Mutation(level, master);
        if (!MUTATIONS.add(mutation)) return false;
        boolean leftPlaced = false;
        boolean success = false;
        try {
            if (!level.setBlock(master, stateFor(facing, Part.LEFT, LidState.CLOSED),
                    UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            leftPlaced = true;
            if (!level.setBlock(right, stateFor(facing, Part.RIGHT, LidState.CLOSED),
                    UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            success = true;
            level.updateNeighborsAt(master, this);
            level.updateNeighborsAt(right, this);
            return true;
        } finally {
            if (!success && leftPlaced) {
                if (level.getBlockState(master).is(this)) level.setBlock(master, oldLeft, UPDATE_ALL);
                if (level.getBlockState(right).is(this)) level.setBlock(right, oldRight, UPDATE_ALL);
            }
            MUTATIONS.remove(mutation);
            if (success && !level.isClientSide) level.scheduleTick(master, this, 1);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        return supported(level, masterPosition(position, state), state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        level.scheduleTick(position, this, 1);
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos position, BlockState oldState, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(position, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos master = masterPosition(position, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, master))) return;
        for (Part part : Part.values()) {
            if (!level.hasChunkAt(partPosition(master, facing, part))) {
                level.scheduleTick(position, this, 40);
                return;
            }
        }
        BlockState masterState = level.getBlockState(master);
        if (!masterState.is(this) || masterState.getValue(PART) != Part.LEFT
                || masterState.getValue(FACING) != facing) {
            level.removeBlock(position, false);
            return;
        }
        LidState lid = masterState.getValue(LID);
        if (!matches(level.getBlockState(partPosition(master, facing, Part.RIGHT)), facing, Part.RIGHT, lid)) {
            level.removeBlock(position, false);
            return;
        }
        if (!supported(level, master, facing)) {
            level.destroyBlock(master, true);
            return;
        }
        if (level.getBlockEntity(master) instanceof ChestFreezerBlockEntity freezer) {
            freezer.completeDue(level.getGameTime());
            long remaining = freezer.ticksUntilCompletion(level.getGameTime());
            if (remaining > 0) level.scheduleTick(master, this, (int) remaining);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos master = masterPosition(position, state);
        double[] local = localHit(hit.getLocation(), master, state.getValue(FACING));
        // Only actual lid projection can activate it. An open half's floor remains a no-op.
        if (local[1] < 14.1 || local[1] > 16.1 || local[2] < 1.75 || local[2] > 14.25
                || local[0] < 1.65 || local[0] > 30.35) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState masterState = level.getBlockState(master);
        if (!masterState.is(this) || masterState.getValue(PART) != Part.LEFT
                || masterState.getValue(FACING) != state.getValue(FACING)) return InteractionResult.CONSUME;
        LidState stable = masterState.getValue(LID);
        if (!matches(level.getBlockState(partPosition(master, state.getValue(FACING), Part.RIGHT)),
                state.getValue(FACING), Part.RIGHT, stable)) return InteractionResult.CONSUME;
        LidState target;
        if (stable == LidState.CLOSED) target = local[0] < 16 ? LidState.LEFT_OPEN : LidState.RIGHT_OPEN;
        else if (stable == LidState.LEFT_OPEN && local[0] >= 16) target = LidState.CLOSED;
        else if (stable == LidState.RIGHT_OPEN && local[0] < 16) target = LidState.CLOSED;
        else return InteractionResult.CONSUME;
        if (level.getBlockEntity(master) instanceof ChestFreezerBlockEntity freezer
                && freezer.startTransition(target, level.getGameTime())) {
            level.scheduleTick(master, this, ANIMATION_TICKS);
            Direction other = state.getValue(FACING).getCounterClockWise();
            level.playSound(null, master.getX() + 0.5 + other.getStepX() * 0.5,
                    master.getY() + 0.85, master.getZ() + 0.5 + other.getStepZ() * 0.5,
                    AflSounds.CHEST_FREEZER_SLIDE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.CONSUME;
    }

    /** Logical 0..32 X, 0..16 Z frame, with master in X=0..16; source Geo is centered. */
    public static double[] localHit(Vec3 hit, BlockPos master, Direction facing) {
        double dx = hit.x - master.getX() - 0.5;
        double dz = hit.z - master.getZ() - 0.5;
        Direction toRight = facing.getCounterClockWise();
        return new double[]{8 + 16 * (dx * toRight.getStepX() + dz * toRight.getStepZ()),
                16 * (hit.y - master.getY()),
                8 - 16 * (dx * facing.getStepX() + dz * facing.getStepZ())};
    }

    /** Commit after seven ticks; all parts receive one stable state, never BOTH_OPEN. */
    public void commitLid(Level level, BlockPos master, LidState target) {
        BlockState state = level.getBlockState(master);
        if (!state.is(this) || state.getValue(PART) != Part.LEFT) return;
        Direction facing = state.getValue(FACING);
        BlockPos right = partPosition(master, facing, Part.RIGHT);
        if (!matches(level.getBlockState(right), facing, Part.RIGHT, state.getValue(LID))) return;
        Mutation mutation = new Mutation(level, master.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            level.setBlock(master, state.setValue(LID, target), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            level.setBlock(right, level.getBlockState(right).setValue(LID, target), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        } finally {
            MUTATIONS.remove(mutation);
        }
        level.updateNeighborsAt(master, this);
        level.updateNeighborsAt(right, this);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        removePeer(level, masterPosition(position, state), state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()))
            removePeer(level, masterPosition(position, state), state.getValue(FACING), position);
        super.onRemove(state, level, position, replacement, moved);
    }

    private void removePeer(LevelAccessor level, BlockPos master, Direction facing, BlockPos keep) {
        Mutation mutation = new Mutation(level, master.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos peer = partPosition(master, facing, part);
                if (!peer.equals(keep) && level.hasChunkAt(peer)) {
                    BlockState other = level.getBlockState(peer);
                    if (other.is(this) && other.getValue(PART) == part && other.getValue(FACING) == facing)
                        level.setBlock(peer, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state); }
    @Override public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { return shape(state); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state); }
    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }

    private static VoxelShape shape(BlockState state) {
        return SHAPES.get(new ShapeKey(state.getValue(PART), state.getValue(FACING), state.getValue(LID)));
    }

    private static Map<ShapeKey, VoxelShape> buildShapes() {
        Map<ShapeKey, VoxelShape> shapes = new HashMap<>();
        for (Part part : Part.values()) for (LidState lid : LidState.values()) {
            VoxelShape north = Shapes.or(body(part), lids(part, lid)).optimize();
            Map<Direction, VoxelShape> rotations = HorizontalShapeUtils.rotations(north);
            for (Direction facing : Direction.Plane.HORIZONTAL)
                shapes.put(new ShapeKey(part, facing, lid), rotations.get(facing));
        }
        return Map.copyOf(shapes);
    }

    private static VoxelShape body(Part part) {
        VoxelShape shape = Shapes.or(
                Block.box(0, 0, 0, 16, 3.6, 16),
                Block.box(0, 1.5, 0.3, 16, 13.65, 1.7),
                Block.box(0, 1.5, 14.3, 16, 13.65, 15.7),
                Block.box(0, 13.65, 0, 16, 15.8, 1.75),
                Block.box(0, 13.65, 14.25, 16, 15.8, 16));
        if (part == Part.LEFT) {
            shape = Shapes.or(shape, Block.box(14.3, 1.5, 1.7, 16, 15.8, 14.3),
                    Block.box(0, 13.65, 1.75, 0.55, 15.8, 14.25));
        } else {
            shape = Shapes.or(shape, Block.box(0, 1.5, 1.7, 1.7, 15.8, 14.3),
                    Block.box(15.45, 13.65, 1.75, 16, 15.8, 14.25));
        }
        return shape.optimize();
    }

    private static VoxelShape lids(Part part, LidState lid) {
        if ((lid == LidState.LEFT_OPEN && part == Part.LEFT)
                || (lid == LidState.RIGHT_OPEN && part == Part.RIGHT)) return Shapes.empty();
        // Closed lids cover the real frame + grip envelope; the non-open half
        // contains both glass lids stacked in their two tracks.
        double minX = part == Part.LEFT ? 0 : 1.75;
        double maxX = part == Part.LEFT ? 14.25 : 16;
        double minY = lid == LidState.CLOSED && part == Part.RIGHT ? 14.9 : 14.3;
        double maxY = lid == LidState.CLOSED && part == Part.LEFT ? 14.8 : 15.4;
        return Block.box(minX, minY, 1.55, maxX, maxY, 14.45);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LEFT ? new ChestFreezerBlockEntity(pos, state) : null;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART, LID); }

    public enum Part implements StringRepresentable {
        LEFT("left"), RIGHT("right");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public enum LidState implements StringRepresentable {
        CLOSED("closed"), LEFT_OPEN("left_open"), RIGHT_OPEN("right_open");
        private final String name;
        LidState(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
}
