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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three-cell office desk. The center cell owns the item drop and each cell has local collision. Office Props V2
 * (tools/build-office-props-v2.mjs): the center cell's model draws the whole desk, the outline is one clean box per cell
 * (the desk's full height, no stepped outline) and collision is the top plus the end cells' square legs.
 */
public final class ModernOfficeDeskBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private record Mutation(LevelAccessor level, BlockPos root) {
    }

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();

    /** Outline and picking: the cell's full desk height (the same for every part and facing). */
    private static final VoxelShape OUTLINE = Block.box(0.0, 0.0, 0.0, 16.0, 13.5, 16.0);
    // collision: the top (y 12.45..13.5) and the 1.7-unit square legs of the end frames (mesh: x +-22.1, z +-6.1)
    private static final VoxelShape TOP = Block.box(0.0, 12.45, 0.0, 16.0, 13.5, 16.0);
    private static final VoxelShape LEFT_NORTH = Shapes.or(TOP,
            Block.box(1.05, 0.0, 1.05, 2.75, 12.45, 2.75),
            Block.box(1.05, 0.0, 13.25, 2.75, 12.45, 14.95)
    ).optimize();
    private static final VoxelShape RIGHT_NORTH = Shapes.or(TOP,
            Block.box(13.25, 0.0, 1.05, 14.95, 12.45, 2.75),
            Block.box(13.25, 0.0, 13.25, 14.95, 12.45, 14.95)
    ).optimize();

    private static final Map<Part, Map<Direction, VoxelShape>> COLLISION = Map.of(
            Part.LEFT, HorizontalShapeUtils.rotations(LEFT_NORTH),
            Part.CENTER, HorizontalShapeUtils.rotations(TOP),
            Part.RIGHT, HorizontalShapeUtils.rotations(RIGHT_NORTH)
    );

    public ModernOfficeDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.CENTER));
    }

    public static BlockPos partPosition(BlockPos root, Direction facing, Part part) {
        return switch (part) {
            case LEFT -> root.relative(facing.getCounterClockWise());
            case CENTER -> root;
            case RIGHT -> root.relative(facing.getClockWise());
        };
    }

    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        return switch (state.getValue(PART)) {
            case LEFT -> position.relative(state.getValue(FACING).getClockWise());
            case CENTER -> position;
            case RIGHT -> position.relative(state.getValue(FACING).getCounterClockWise());
        };
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
            if (!level.getBlockState(position).canBeReplaced(localContext) || !level.getFluidState(position).isEmpty()) {
                return false;
            }
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
        return canPlaceStructure(context, facing) ? stateFor(facing, Part.CENTER) : null;
    }

    public boolean placeStructure(BlockPlaceContext context, BlockState centerState) {
        Direction facing = centerState.getValue(FACING);
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
                if (!level.setBlock(position, stateFor(facing, part), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
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
        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) != Part.CENTER
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, root, new ItemStack(AflItems.MODERN_OFFICE_DESK.get()));
        }
        removePeers(level, root, state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return state.getValue(PART) == Part.CENTER ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) {
            removePeers(level, rootPosition(position, state), state.getValue(FACING), position);
        }
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removePeers(LevelAccessor level, BlockPos root, Direction facing, @Nullable BlockPos keep) {
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
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return COLLISION.get(state.getValue(PART)).get(state.getValue(FACING));
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

    public enum Part implements StringRepresentable {
        LEFT("left"),
        CENTER("center"),
        RIGHT("right");

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
