package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Four-cell, independently hinged, no-inventory commercial cooler. */
public final class BeverageCoolerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty LEFT_OPEN = BooleanProperty.create("left_open");
    public static final BooleanProperty RIGHT_OPEN = BooleanProperty.create("right_open");
    public static final int ANIMATION_TICKS = 8;

    private record Mutation(LevelAccessor level, BlockPos master) {}
    private record ShapeKey(Part part, Direction facing, boolean leftOpen, boolean rightOpen) {}
    private record DoorTargetKey(Part part, Direction facing) {}
    private enum Door {LEFT, RIGHT, NONE}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<ShapeKey, VoxelShape> SHAPES = buildShapes();
    private static final Map<ShapeKey, VoxelShape> SELECTION_SHAPES = buildSelectionShapes();
    private static final Map<DoorTargetKey, VoxelShape> OPEN_DOOR_TARGETS = buildOpenDoorTargets();

    public BeverageCoolerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.LOWER_LEFT).setValue(LEFT_OPEN, false).setValue(RIGHT_OPEN, false));
    }

    /** Source-model +X points to the viewer's left; other cells go to the viewer's right. */
    public static BlockPos partPosition(BlockPos master, Direction facing, Part part) {
        BlockPos result = part.isLeft() ? master : master.relative(facing.getCounterClockWise());
        return part.isUpper() ? result.above() : result;
    }

    public static BlockPos masterPosition(BlockPos position, BlockState state) {
        Part part = state.getValue(PART);
        BlockPos lower = part.isUpper() ? position.below() : position;
        return part.isLeft() ? lower : lower.relative(state.getValue(FACING).getClockWise());
    }

    private BlockState stateFor(Direction facing, Part part, boolean left, boolean right) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part)
                .setValue(LEFT_OPEN, left).setValue(RIGHT_OPEN, right);
    }

    private boolean matches(BlockState state, Direction facing, Part part, boolean left, boolean right) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part
                && state.getValue(LEFT_OPEN) == left && state.getValue(RIGHT_OPEN) == right;
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
                    || !level.isUnobstructed(stateFor(facing, part, false, false), position,
                    CollisionContext.empty())) return false;
            Player player = context.getPlayer();
            if (player != null && (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
        }
        return supported(level, master, facing);
    }

    private static boolean supported(LevelReader level, BlockPos master, Direction facing) {
        for (Part part : List.of(Part.LOWER_LEFT, Part.LOWER_RIGHT)) {
            BlockPos floor = partPosition(master, facing, part).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlaceStructure(context, facing) ? stateFor(facing, Part.LOWER_LEFT, false, false) : null;
    }

    /** Called by the BlockItem inside its placement transaction. */
    public boolean placeStructure(BlockPlaceContext context, BlockState masterState) {
        Direction facing = masterState.getValue(FACING);
        if (!canPlaceStructure(context, facing)) return false;
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos().immutable();
        Mutation mutation = new Mutation(level, master);
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (Part part : Part.values()) {
                BlockPos position = partPosition(master, facing, part);
                previous.put(position, level.getBlockState(position));
                if (!level.setBlock(position, stateFor(facing, part, false, false),
                        UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (Part part : Part.values()) level.updateNeighborsAt(partPosition(master, facing, part), this);
            return true;
        } finally {
            if (!success) previous.forEach((position, oldState) -> {
                if (level.getBlockState(position).is(this)) level.setBlock(position, oldState, UPDATE_ALL);
            });
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
        if (!masterState.is(this) || masterState.getValue(PART) != Part.LOWER_LEFT
                || masterState.getValue(FACING) != facing) {
            level.removeBlock(position, false);
            return;
        }
        for (Part part : Part.values()) {
            if (!matches(level.getBlockState(partPosition(master, facing, part)), facing, part,
                    masterState.getValue(LEFT_OPEN), masterState.getValue(RIGHT_OPEN))) {
                level.removeBlock(position, false);
                return;
            }
        }
        if (!supported(level, master, facing)) {
            level.destroyBlock(master, true);
            return;
        }
        if (level.getBlockEntity(master) instanceof BeverageCoolerBlockEntity cooler) {
            cooler.completeDue(level.getGameTime());
            long remaining = cooler.ticksUntilNextCompletion(level.getGameTime());
            if (remaining > 0) level.scheduleTick(master, this, (int) remaining);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos master = masterPosition(position, state);
        Door door = hitDoor(hit.getLocation(), master, state.getValue(FACING), state);
        if (door == Door.NONE) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState masterState = level.getBlockState(master);
        if (!masterState.is(this) || masterState.getValue(PART) != Part.LOWER_LEFT
                || masterState.getValue(FACING) != state.getValue(FACING)) return InteractionResult.CONSUME;
        for (Part part : Part.values()) {
            if (!matches(level.getBlockState(partPosition(master, state.getValue(FACING), part)),
                    state.getValue(FACING), part, masterState.getValue(LEFT_OPEN),
                    masterState.getValue(RIGHT_OPEN))) return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(master) instanceof BeverageCoolerBlockEntity cooler) {
            boolean left = door == Door.LEFT;
            boolean target = !masterState.getValue(left ? LEFT_OPEN : RIGHT_OPEN);
            if (cooler.startDoor(left, target, level.getGameTime())) {
                level.scheduleTick(master, this, ANIMATION_TICKS);
            }
        }
        return InteractionResult.CONSUME;
    }

    /** Hit coordinates and outline boxes are derived from the same north-facing source axes. */
    private static Door hitDoor(Vec3 hit, BlockPos master, Direction facing, BlockState state) {
        Direction left = facing.getClockWise();
        double midX = master.getX() + 0.5 - left.getStepX() * 0.5;
        double midZ = master.getZ() + 0.5 - left.getStepZ() * 0.5;
        double dx = hit.x - midX, dz = hit.z - midZ;
        double x = 8 + 16 * (dx * left.getStepX() + dz * left.getStepZ());
        double z = -16 * (dx * facing.getStepX() + dz * facing.getStepZ());
        double y = (hit.y - master.getY()) * 16;
        if (y < 3.45 || y > 28.9) return Door.NONE;
        if ((x >= 8.05 && x <= 23.1 && z >= -8.25 && z <= -6.75)
                || (state.getValue(LEFT_OPEN) && x >= 21.3 && x <= 24.7
                && z >= -23.5 && z <= -4.0)) return Door.LEFT;
        if ((x >= -7.1 && x <= 7.95 && z >= -8.25 && z <= -6.75)
                || (state.getValue(RIGHT_OPEN) && x >= -8.7 && x <= -5.3
                && z >= -23.5 && z <= -4.0)) return Door.RIGHT;
        return Door.NONE;
    }

    /** State changes only after the corresponding eight-tick animation has finished. */
    public void commitDoor(Level level, BlockPos master, boolean left, boolean targetOpen) {
        BlockState state = level.getBlockState(master);
        if (!state.is(this) || state.getValue(PART) != Part.LOWER_LEFT) return;
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (!matches(level.getBlockState(partPosition(master, facing, part)), facing, part,
                    state.getValue(LEFT_OPEN), state.getValue(RIGHT_OPEN))) return;
        }
        Mutation mutation = new Mutation(level, master.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos pos = partPosition(master, facing, part);
                BlockState partState = level.getBlockState(pos);
                level.setBlock(pos, partState.setValue(left ? LEFT_OPEN : RIGHT_OPEN, targetOpen),
                        UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
        for (Part part : Part.values()) level.updateNeighborsAt(partPosition(master, facing, part), this);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos master = masterPosition(position, state);
        // The selected part keeps its own normal loot evaluation. Peer cells are removed without drops.
        removePeers(level, master, state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()))
            removePeers(level, masterPosition(position, state), state.getValue(FACING), position);
        super.onRemove(state, level, position, replacement, moved);
    }

    private void removePeers(LevelAccessor level, BlockPos master, Direction facing, @Nullable BlockPos keep) {
        Mutation mutation = new Mutation(level, master.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos peer = partPosition(master, facing, part);
                if ((keep == null || !peer.equals(keep)) && level.hasChunkAt(peer)) {
                    BlockState peerState = level.getBlockState(peer);
                    if (peerState.is(this) && peerState.getValue(PART) == part
                            && peerState.getValue(FACING) == facing)
                        level.setBlock(peer, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return selectionShape(state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return selectionShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    private static VoxelShape shape(BlockState state) {
        return SHAPES.get(new ShapeKey(state.getValue(PART), state.getValue(FACING),
                state.getValue(LEFT_OPEN), state.getValue(RIGHT_OPEN)));
    }

    private static VoxelShape selectionShape(BlockState state) {
        return SELECTION_SHAPES.get(new ShapeKey(state.getValue(PART), state.getValue(FACING),
                state.getValue(LEFT_OPEN), state.getValue(RIGHT_OPEN)));
    }

    /** Door-only outline, including the section beyond its owner cell, for explicit ray tests. */
    public static VoxelShape openDoorTarget(Part part, Direction facing) {
        return OPEN_DOOR_TARGETS.get(new DoorTargetKey(part, facing));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }

    private static Map<ShapeKey, VoxelShape> buildShapes() {
        Map<ShapeKey, VoxelShape> result = new HashMap<>();
        for (Part part : Part.values()) {
            for (boolean left : new boolean[]{false, true}) for (boolean right : new boolean[]{false, true}) {
                VoxelShape north = Shapes.or(body(part), door(part, part.isLeft() ? left : right)).optimize();
                Map<Direction, VoxelShape> rotated = HorizontalShapeUtils.rotations(north);
                for (Direction facing : Direction.Plane.HORIZONTAL)
                    result.put(new ShapeKey(part, facing, left, right), rotated.get(facing));
            }
        }
        return Map.copyOf(result);
    }

    private static Map<ShapeKey, VoxelShape> buildSelectionShapes() {
        Map<ShapeKey, VoxelShape> result = new HashMap<>();
        for (Part part : Part.values()) {
            Map<Direction, VoxelShape> handle = HorizontalShapeUtils.rotations(openDoorHandle(part));
            for (Direction facing : Direction.Plane.HORIZONTAL)
                for (boolean left : new boolean[]{false, true})
                    for (boolean right : new boolean[]{false, true}) {
                        ShapeKey key = new ShapeKey(part, facing, left, right);
                        boolean thisDoorOpen = part.isLeft() ? left : right;
                        result.put(key, thisDoorOpen
                                ? Shapes.or(SHAPES.get(key), handle.get(facing)).optimize()
                                : SHAPES.get(key));
                    }
        }
        return Map.copyOf(result);
    }

    private static Map<DoorTargetKey, VoxelShape> buildOpenDoorTargets() {
        Map<DoorTargetKey, VoxelShape> result = new HashMap<>();
        for (Part part : Part.values()) {
            Map<Direction, VoxelShape> rotated = HorizontalShapeUtils.rotations(door(part, true));
            for (Direction facing : Direction.Plane.HORIZONTAL)
                result.put(new DoorTargetKey(part, facing), rotated.get(facing));
        }
        return Map.copyOf(result);
    }

    /** A small selectable hinge area stays inside the footprint when the leaf swings out. */
    private static VoxelShape openDoorHandle(Part part) {
        double minY = part.isUpper() ? 0 : 3.58;
        double maxY = part.isUpper() ? 12.75 : 16;
        return part.isLeft()
                ? Block.box(13.3, minY, 0, 16, maxY, 4)
                : Block.box(0, minY, 0, 2.7, maxY, 4);
    }

    private static VoxelShape body(Part part) {
        double sideMin = part.isLeft() ? 14.95 : 0;
        double sideMax = part.isLeft() ? 16 : 1.05;
        VoxelShape shape = Shapes.or(
                Block.box(sideMin, 0, 1.2, sideMax, 16, 16),
                Block.box(0, 0, 14.8, 16, 16, 16));
        if (!part.isUpper()) shape = Shapes.or(shape, Block.box(0, 0, 0.45, 16, 3.65, 16));
        else shape = Shapes.or(shape, Block.box(0, 12.9, 1.2, 16, 16, 16));
        // Thin, merged shelf decks retain the accessible front aisle.
        for (double shelfY : new double[]{3.65, 8.37, 13.22, 18.07, 22.92}) {
            double localY = shelfY - (part.isUpper() ? 16 : 0);
            if (localY >= 0 && localY < 16)
                shape = Shapes.or(shape, Block.box(part.isLeft() ? 0 : 1.05, localY, 2.9,
                        part.isLeft() ? 14.95 : 16, localY + 0.64, 13.1));
        }
        return shape.optimize();
    }

    private static VoxelShape door(Part part, boolean open) {
        double minY = part.isUpper() ? 0 : 3.58;
        double maxY = part.isUpper() ? 12.75 : 16;
        if (open) return part.isLeft()
                ? Block.box(14.35, minY, -15.5, 16.6, maxY, 1.1)
                : Block.box(-0.6, minY, -15.5, 1.65, maxY, 1.1);
        return part.isLeft()
                ? Block.box(0.06, minY, 0, 15.02, maxY, 1.1)
                : Block.box(0.98, minY, 0, 15.94, maxY, 1.1);
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER_LEFT ? new BeverageCoolerBlockEntity(pos, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LEFT_OPEN, RIGHT_OPEN);
    }

    public enum Part implements StringRepresentable {
        LOWER_LEFT("lower_left"), LOWER_RIGHT("lower_right"),
        UPPER_LEFT("upper_left"), UPPER_RIGHT("upper_right");
        private final String name;
        Part(String name) { this.name = name; }
        public boolean isLeft() { return this == LOWER_LEFT || this == UPPER_LEFT; }
        public boolean isUpper() { return this == UPPER_LEFT || this == UPPER_RIGHT; }
        @Override public String getSerializedName() { return name; }
    }
}
