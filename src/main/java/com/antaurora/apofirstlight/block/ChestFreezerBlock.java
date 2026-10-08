package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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

/**
 * Two-cell chest freezer with two sliding glass lids. Logical LEFT/master is the visual left half; front is -Z. One AFL
 * power port on the back of the master cell; with power (ChestFreezerBlockEntity) the status display and LED are on and
 * the compressor runs in cycles. Contents: the master's 18-slot searchable container, opened from the well of the open
 * half; the goods drawn inside follow how much it holds.
 */
public final class ChestFreezerBlock extends Block implements EntityBlock, MeshHitMultiCell {
    /** The hit mesh (docs/rendering/mesh_hit_runtime_v1.md): every cell hits on the left cell (its block entity draws the freezer). */
    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return masterPosition(pos, state);
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<LidState> LID = EnumProperty.create("lid", LidState.class);
    public static final int ANIMATION_TICKS = 14; // the mesh profile's 0.70 s slides
    /** tools/build-chest-freezer-v2.mjs LIDS / LID_TRAVEL, source px: each lid's grip (middle x, top y) when closed. */
    private static final double LEFT_GRIP_X = 8.25, LEFT_GRIP_Y = 14.8, RIGHT_GRIP_X = 7.75, RIGHT_GRIP_Y = 15.4;
    private static final double LID_TRAVEL = 14.15;

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
            // the cells are set with UPDATE_KNOWN_SHAPE; a power cable already lying behind the port reshapes now
            level.getBlockState(master).updateNeighbourShapes(level, master, UPDATE_CLIENTS);
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

    /** Placed by a player: its contents are the player's own, never searched. */
    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof ChestFreezerBlockEntity freezer) freezer.markPlacedByPlayer();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        BlockPos master = masterPosition(position, state);
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && !player.isSpectator()) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return player instanceof net.minecraft.server.level.ServerPlayer server
                    ? com.antaurora.apofirstlight.energy.PowerPlugs.useDevice(level, master, server) : InteractionResult.PASS;
        }
        double[] local = localHit(hit.getLocation(), master, state.getValue(FACING));
        BlockState masterState = level.getBlockState(master);
        // the well of the open half: search / view the contents (not while a lid slides)
        if (masterState.is(this) && masterState.getValue(PART) == Part.LEFT && inOpenWell(masterState.getValue(LID), local)) {
            if (!(level.getBlockEntity(master) instanceof ChestFreezerBlockEntity freezer) || freezer.lidMoving())
                return InteractionResult.CONSUME;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            player.openMenu(freezer);
            return InteractionResult.CONSUME;
        }
        if (!onLid(local)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!masterState.is(this) || masterState.getValue(PART) != Part.LEFT
                || masterState.getValue(FACING) != state.getValue(FACING)) return InteractionResult.CONSUME;
        LidState stable = masterState.getValue(LID);
        if (!matches(level.getBlockState(partPosition(master, state.getValue(FACING), Part.RIGHT)),
                state.getValue(FACING), Part.RIGHT, stable)) return InteractionResult.CONSUME;
        LidState target = clickTarget(stable, local);
        if (target == null) return InteractionResult.CONSUME;
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

    /** Only the actual lid projection can activate it. An open half's floor remains a no-op. */
    private static boolean onLid(double[] local) {
        return local[1] >= 14.1 && local[1] <= 16.1 && local[2] >= 1.75 && local[2] <= 14.25
                && local[0] >= 1.65 && local[0] <= 30.35;
    }

    /** The well (liner, below the lid tracks) of the half that stands open; a closed half's lid takes the hit first. */
    private static boolean inOpenWell(LidState stable, double[] local) {
        if (local[1] < 3.5 || local[1] >= 14.1 || local[2] < 1.6 || local[2] > 14.4) return false;
        return stable == LidState.LEFT_OPEN ? local[0] >= 1.6 && local[0] < 16
                : stable == LidState.RIGHT_OPEN && local[0] >= 16 && local[0] <= 30.4;
    }

    /**
     * The lid state a click starts (use() and the prompt): a closed lid opens (the half that was hit); with one half
     * open, a click on the stacked lids over the other half closes it. Null: not on a lid, or nothing to do.
     */
    @Nullable
    private static LidState clickTarget(LidState stable, double[] local) {
        if (!onLid(local)) return null;
        if (stable == LidState.CLOSED) return local[0] < 16 ? LidState.LEFT_OPEN : LidState.RIGHT_OPEN;
        if (stable == LidState.LEFT_OPEN && local[0] >= 16) return LidState.CLOSED;
        if (stable == LidState.RIGHT_OPEN && local[0] < 16) return LidState.CLOSED;
        return null;
    }

    /**
     * What a click at this hit would do (WorldInteractionHint): hint key suffix open / close / search / view, and where to
     * draw it: the grip of the lid that moves, or the middle of the open half's well. Nothing while a lid slides.
     */
    public record Prompt(String key, Vec3 anchor) {}

    @Nullable
    public static Prompt prompt(BlockGetter level, BlockPos position, BlockState state, Vec3 hitLocation) {
        BlockPos master = masterPosition(position, state);
        BlockState masterState = level.getBlockState(master);
        if (!(masterState.getBlock() instanceof ChestFreezerBlock) || masterState.getValue(PART) != Part.LEFT
                || !(level.getBlockEntity(master) instanceof ChestFreezerBlockEntity freezer) || freezer.lidMoving()) return null;
        Direction facing = masterState.getValue(FACING);
        LidState stable = masterState.getValue(LID);
        double[] local = localHit(hitLocation, master, facing);
        if (inOpenWell(stable, local))
            return new Prompt(freezer.isSearchCompleteForPrompt() ? "view" : "search",
                    MeshSourceFrame.toWorld(master, facing, stable == LidState.LEFT_OPEN ? 15.0 : 1.0, 12.0, 0));
        LidState target = clickTarget(stable, local);
        if (target == null) return null;
        // the master's lid opens toward the slave half (source -x), the slave's toward the master half
        Vec3 anchor = target == LidState.LEFT_OPEN || stable == LidState.LEFT_OPEN
                ? MeshSourceFrame.toWorld(master, facing, LEFT_GRIP_X - (stable == LidState.LEFT_OPEN ? LID_TRAVEL : 0), LEFT_GRIP_Y + 0.5, 0)
                : MeshSourceFrame.toWorld(master, facing, RIGHT_GRIP_X + (stable == LidState.RIGHT_OPEN ? LID_TRAVEL : 0), RIGHT_GRIP_Y + 0.5, 0);
        return new Prompt(target == LidState.CLOSED ? "close" : "open", anchor);
    }

    /** Where the compressor sounds come from: low in the master cell, behind the control panel (source px 16, 3, 0). */
    public static Vec3 compressorPosition(BlockPos master, Direction facing) {
        return MeshSourceFrame.toWorld(master, facing, 16.0, 3.0, 0.0);
    }

    /** The master's block entity runs the power and the compressor on the server, the cold mist on the client. */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != Part.LEFT || type != AflBlockEntities.CHEST_FREEZER.get()) return null;
        if (level.isClientSide) return (BlockEntityTicker<T>) (BlockEntityTicker<ChestFreezerBlockEntity>) (l, p, s, freezer) ->
                com.antaurora.apofirstlight.client.ColdMist.freezerTick(l, p, s, freezer);
        return (BlockEntityTicker<T>) (BlockEntityTicker<ChestFreezerBlockEntity>) (l, p, s, freezer) -> freezer.serverTick();
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
        if (!state.is(replacement.getBlock())) {
            removePeer(level, masterPosition(position, state), state.getValue(FACING), position);
            // the master's contents, by every removal path: revealed slots drop, never-seen loot is lost
            if (level.getBlockEntity(position) instanceof ChestFreezerBlockEntity freezer) { freezer.dropContentsOnce(); freezer.plugCord().release(); }
            level.updateNeighbourForOutputSignal(position, this);
        }
        super.onRemove(state, level, position, replacement, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparator fullness of the master's revealed slots only, so it never hints at unsearched loot. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos position) {
        return level.getBlockEntity(masterPosition(position, state)) instanceof ChestFreezerBlockEntity freezer
                ? AflContainerSearch.revealedAnalogSignal(freezer) : 0;
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

    /** Lid-start block events (ChestFreezerBlockEntity#startTransition) go to the master's block entity. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        var entity = level.getBlockEntity(pos);
        return entity != null && entity.triggerEvent(id, param);
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
