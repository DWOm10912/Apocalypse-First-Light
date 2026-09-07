package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Static four-cell station. No block entity, inventory, UI, power, or ticking renderer. */
public class GunWorkbenchBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private record Mutation(Level level, BlockPos root) {}
    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<Part, Map<Direction, VoxelShape>> SHAPES = createShapes();

    public enum Part implements StringRepresentable {
        BASE(0, 0), SIDE(1, 0), UPPER(0, 1), UPPER_SIDE(1, 1);
        public final int column, row;
        Part(int column, int row) { this.column = column; this.row = row; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public GunWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BASE));
    }

    public static BlockPos partPosition(BlockPos root, Direction facing, Part part) {
        return root.relative(facing.getClockWise(), part.column).above(part.row);
    }

    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        Part part = state.getValue(PART);
        return position.relative(state.getValue(FACING).getCounterClockWise(), part.column).below(part.row);
    }

    public BlockState stateFor(Direction facing, Part part) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part);
    }

    private boolean matches(BlockState state, Direction facing, Part part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    private boolean supported(LevelReader level, BlockPos root, Direction facing) {
        for (Part part : List.of(Part.BASE, Part.SIDE)) {
            BlockPos floor = partPosition(root, facing, part).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }

    public boolean canPlaceStructure(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos root = context.getClickedPos();
        if (root.getY() < level.getMinBuildHeight() || root.getY() + 1 >= level.getMaxBuildHeight()) return false;
        for (Part part : Part.values()) {
            BlockPos pos = partPosition(root, facing, part);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockPlaceContext local = BlockPlaceContext.at(context, pos, Direction.UP);
            if (!level.getBlockState(pos).canBeReplaced(local) || !level.getFluidState(pos).isEmpty()) return false;
            Player player = context.getPlayer();
            if (player != null && (!level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, Direction.UP, context.getItemInHand()))) return false;
            if (!level.isUnobstructed(stateFor(facing, part), pos, CollisionContext.empty())) return false;
        }
        return supported(level, root, facing);
    }

    @Override @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlaceStructure(context, facing) ? stateFor(facing, Part.BASE) : null;
    }

    /** Called inside BlockItem's normal placement path, including Forge placement snapshots/events. */
    public boolean placeStructure(BlockPlaceContext context, BlockState base) {
        Direction facing = base.getValue(FACING);
        if (!canPlaceStructure(context, facing)) return false;
        Level level = context.getLevel();
        BlockPos root = context.getClickedPos().immutable();
        Mutation key = new Mutation(level, root);
        if (!MUTATIONS.add(key)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (Part part : Part.values()) {
                BlockPos pos = partPosition(root, facing, part);
                previous.put(pos, level.getBlockState(pos));
                if (!level.setBlock(pos, stateFor(facing, part), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) return false;
            }
            success = true;
            for (Part part : Part.values()) level.updateNeighborsAt(partPosition(root, facing, part), this);
            return true;
        } finally {
            if (!success) previous.forEach((pos, old) -> {
                if (level.getBlockState(pos).is(this)) level.setBlock(pos, old, UPDATE_ALL);
            });
            MUTATIONS.remove(key);
            if (success && !level.isClientSide) level.scheduleTick(root, this, 1);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return supported(level, rootPosition(pos, state), state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Defer integrity checks until all four placement writes have finished.
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = rootPosition(pos, state);
        Direction facing = state.getValue(FACING);
        if (MUTATIONS.contains(new Mutation(level, root))) return;
        for (Part part : Part.values()) {
            if (!level.hasChunkAt(partPosition(root, facing, part))) {
                // Never force-load a neighbor. Retry only while an integrity check is deferred.
                level.scheduleTick(pos, this, 100);
                return;
            }
        }
        for (Part part : Part.values()) {
            if (!matches(level.getBlockState(partPosition(root, facing, part)), facing, part)) {
                level.removeBlock(pos, false); // Orphan repair must not mint another item.
                return;
            }
        }
        if (!supported(level, root, facing)) level.destroyBlock(root, true);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.equals(replacement)) {
            BlockPos root = rootPosition(pos, state);
            Direction facing = state.getValue(FACING);
            Mutation key = new Mutation(level, root);
            if (MUTATIONS.add(key)) {
                try {
                    for (Part part : Part.values()) {
                        BlockPos peer = partPosition(root, facing, part);
                        if (!peer.equals(pos) && level.hasChunkAt(peer)
                                && matches(level.getBlockState(peer), facing, part)) {
                            // Only the originally broken cell uses the normal loot path.
                            level.setBlock(peer, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                        }
                    }
                } finally { MUTATIONS.remove(key); }
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos root = rootPosition(pos, state);
        if (!level.hasChunkAt(root) || !matches(level.getBlockState(root), state.getValue(FACING), Part.BASE))
            return InteractionResult.PASS;
        return useAtRoot(level, root, player, hand);
    }

    /** Future UI/battery entry belongs here. V1 deliberately has no state or visible action. */
    protected InteractionResult useAtRoot(Level level, BlockPos root, Player player, InteractionHand hand) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }

    private static Map<Part, Map<Direction, VoxelShape>> createShapes() {
        // Source model X + 8 aligns the approved 32-wide station to two real cells.
        double[][] boxes = {
            {.4,0,14.1,2.2,32,16}, {29.8,0,14.1,31.6,32,16},
            {.4,0,.65,2.2,15.2,2.45}, {29.8,0,.65,31.6,15.2,2.45},
            {0,15.2,.9,32,16.5,15}, {2.3,2.35,2.3,29.7,3.2,14.1},
            {2.2,13.4,1.4,29.8,15.2,2.2}, {2.2,16.5,14.4,29.8,30.5,15.45},
            {0,30.17,9.8,32,32,16}, {.2,2.4,2.5,1,15.2,14.1},
            {31,2.4,2.5,31.85,15.2,14.1}, {.15,16.5,10.05,1,30.5,14.1},
            {31,16.5,10.05,31.85,30.5,14.1},
            {18.45,3.2,4.15,29.15,10.32,12.35}, {7.85,3.2,5.25,17.05,10.5,12.55},
            {3.4,3.2,7.3,6,9.9,10.3}, {24.8,16.5,.05,30,20.1,5.5}
        };
        Map<Part, Map<Direction, VoxelShape>> result = new EnumMap<>(Part.class);
        for (Part part : Part.values()) {
            VoxelShape shape = Shapes.empty();
            double ox = part.column * 16, oy = part.row * 16;
            for (double[] b : boxes) {
                double x0 = Math.max(ox,b[0])-ox, y0 = Math.max(oy,b[1])-oy;
                double x1 = Math.min(ox+16,b[3])-ox, y1 = Math.min(oy+16,b[4])-oy;
                if (x1 > x0 && y1 > y0) shape = Shapes.or(shape, Block.box(x0,y0,b[2],x1,y1,b[5]));
            }
            Map<Direction, VoxelShape> rotations = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                final VoxelShape[] rotated = {Shapes.empty()};
                final int steps = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
                shape.forAllBoxes((x0,y0,z0,x1,y1,z1) -> {
                    for (int i=0;i<steps;i++) { double a=x0,b=x1; x0=1-z1; x1=1-z0; z0=a; z1=b; }
                    rotated[0] = Shapes.or(rotated[0], Shapes.box(x0,y0,z0,x1,y1,z1));
                });
                rotations.put(facing, rotated[0].optimize());
            }
            result.put(part, rotations);
        }
        return result;
    }
}
