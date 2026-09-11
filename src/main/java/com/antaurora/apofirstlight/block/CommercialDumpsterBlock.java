package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Static 2x1 commercial dumpster. The master owns the item drop; both cells own collision. */
public final class CommercialDumpsterBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private record Mutation(LevelAccessor level, BlockPos root) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();

    private static final VoxelShape MASTER_NORTH = dumpsterHalfShape(0.65, 16.0);
    private static final VoxelShape SECONDARY_NORTH = dumpsterHalfShape(0.0, 15.35);
    private static final VoxelShape MASTER_COLLISION_NORTH = Shapes.or(
            MASTER_NORTH,
            Block.box(-1.1, 5.3, 3.65, 1.2, 10.3, 12.35)
    ).optimize();
    private static final VoxelShape SECONDARY_COLLISION_NORTH = Shapes.or(
            SECONDARY_NORTH,
            Block.box(14.8, 5.3, 3.65, 17.1, 10.3, 12.35)
    ).optimize();
    private static final VoxelShape MASTER_OUTLINE_NORTH = Block.box(-1.1, 0.0, 0.55, 16.0, 22.3, 15.5);
    private static final VoxelShape SECONDARY_OUTLINE_NORTH = Block.box(0.0, 0.0, 0.55, 17.1, 22.3, 15.5);

    private static final Map<Part, Map<Direction, VoxelShape>> COLLISION_SHAPES = Map.of(
            Part.MASTER, horizontalRotations(MASTER_COLLISION_NORTH),
            Part.SECONDARY, horizontalRotations(SECONDARY_COLLISION_NORTH)
    );
    private static final Map<Part, Map<Direction, VoxelShape>> OUTLINE_SHAPES = Map.of(
            Part.MASTER, horizontalRotations(MASTER_OUTLINE_NORTH),
            Part.SECONDARY, horizontalRotations(SECONDARY_OUTLINE_NORTH)
    );

    public CommercialDumpsterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.MASTER));
    }

    public static BlockPos partPosition(BlockPos root, Direction facing, Part part) {
        return part == Part.MASTER ? root : root.relative(facing.getClockWise());
    }

    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.MASTER
                ? position
                : position.relative(state.getValue(FACING).getCounterClockWise());
    }

    private BlockState stateFor(Direction facing, Part part) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part);
    }

    private boolean matches(BlockState state, Direction facing, Part part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    private boolean supported(LevelReader level, BlockPos root, Direction facing) {
        for (Part part : Part.values()) {
            BlockPos floor = partPosition(root, facing, part).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }

    public boolean canPlaceStructure(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos root = context.getClickedPos();
        if (root.getY() < level.getMinBuildHeight() || root.getY() >= level.getMaxBuildHeight()) return false;
        for (Part part : Part.values()) {
            BlockPos position = partPosition(root, facing, part);
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
            BlockPlaceContext localContext = BlockPlaceContext.at(context, position, Direction.UP);
            if (!level.getBlockState(position).canBeReplaced(localContext)
                    || !level.getFluidState(position).isEmpty()) return false;
            Player player = context.getPlayer();
            if (player != null && (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, context.getItemInHand()))) return false;
            if (!level.isUnobstructed(stateFor(facing, part), position, CollisionContext.empty())) return false;
        }
        return supported(level, root, facing);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlaceStructure(context, facing) ? stateFor(facing, Part.MASTER) : null;
    }

    /** Runs inside BlockItem's normal placement transaction, so a failed second write restores both cells. */
    public boolean placeStructure(BlockPlaceContext context, BlockState masterState) {
        Direction facing = masterState.getValue(FACING);
        if (!canPlaceStructure(context, facing)) return false;
        Level level = context.getLevel();
        BlockPos root = context.getClickedPos().immutable();
        Mutation mutation = new Mutation(level, root);
        if (!MUTATIONS.add(mutation)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (Part part : Part.values()) {
                BlockPos position = partPosition(root, facing, part);
                previous.put(position, level.getBlockState(position));
                if (!level.setBlock(position, stateFor(facing, part), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) {
                    return false;
                }
            }
            success = true;
            for (Part part : Part.values()) level.updateNeighborsAt(partPosition(root, facing, part), this);
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
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        return supported(level, rootPosition(position, state), state.getValue(FACING));
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

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        BlockPos root = rootPosition(position, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, root))) return;
        for (Part part : Part.values()) {
            if (!level.hasChunkAt(partPosition(root, facing, part))) {
                level.scheduleTick(position, this, 100);
                return;
            }
        }
        for (Part part : Part.values()) {
            if (!matches(level.getBlockState(partPosition(root, facing, part)), facing, part)) {
                level.removeBlock(position, false);
                return;
            }
        }
        if (!supported(level, root, facing)) level.destroyBlock(root, true);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos root = rootPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) == Part.SECONDARY
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, root, new ItemStack(AflItems.COMMERCIAL_DUMPSTER.get()));
        }
        removePeer(level, root, state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return state.getValue(PART) == Part.MASTER ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) {
            removePeer(level, rootPosition(position, state), state.getValue(FACING), position);
        }
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removePeer(LevelAccessor level, BlockPos root, Direction facing, @Nullable BlockPos keep) {
        Mutation mutation = new Mutation(level, root.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos peer = partPosition(root, facing, part);
                if ((keep == null || !peer.equals(keep)) && level.hasChunkAt(peer)
                        && matches(level.getBlockState(peer), facing, part)) {
                    level.setBlock(peer, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return OUTLINE_SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return COLLISION_SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    private static VoxelShape dumpsterHalfShape(double minX, double maxX) {
        return Shapes.or(
                Block.box(minX, 0.0, 0.8, maxX, 17.5, 4.6),
                Block.box(minX, 0.0, 4.6, maxX, 19.0, 8.5),
                Block.box(minX, 0.0, 8.5, maxX, 20.5, 12.5),
                Block.box(minX, 0.0, 12.5, maxX, 22.3, 15.5)
        ).optimize();
    }

    private static Map<Direction, VoxelShape> horizontalRotations(VoxelShape north) {
        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int turns = switch (facing) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
            VoxelShape rotated = north;
            for (int turn = 0; turn < turns; turn++) {
                VoxelShape previous = rotated;
                rotated = Shapes.empty();
                final VoxelShape[] accumulator = {rotated};
                previous.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                        accumulator[0] = Shapes.or(accumulator[0],
                                Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)));
                rotated = accumulator[0].optimize();
            }
            result.put(facing, rotated);
        }
        return result;
    }

    public enum Part implements StringRepresentable {
        MASTER("master"),
        SECONDARY("secondary");

        private final String serializedName;

        Part(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
