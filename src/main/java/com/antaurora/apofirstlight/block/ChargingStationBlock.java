package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charging Station V1 (first Pure Mesh machine, tools/build-charging-station-v1.mjs): a two-cell steel charging bench, the
 * master is the left cell as the viewer faces the front. One chargeable item (Forge Energy, can receive) lies on the tray;
 * a right click anywhere on the station places the held item or, with an empty hand, takes it back. Power only comes in
 * from cables: each cell has a standard AFL power port on its back face (the default {@link AflPowerPortBlock} face), both
 * feeding the master's buffer (ChargingStationBlockEntity). Drawn by the master's block entity renderer; the baked model is
 * particle only.
 */
public final class ChargingStationBlock extends Block implements EntityBlock, AflPowerPortBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Tray item resting point and prompt anchor, source px (tools/build-charging-station-v1.mjs ITEM, TRAY). */
    public static final double ITEM_X = 8.0, ITEM_Y = 13.0, ITEM_Z = 0.05;

    private record Mutation(LevelAccessor level, BlockPos master) {}
    private record ShapeKey(Part part, Direction facing) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    /**
     * Outline / collision boxes in source px (x -8..24 across both cells, front toward -z): cabinet with tray, bumper and
     * back port plates; raised back wall; stretcher; the end frames' cheeks (stepped at the sloped top) and legs.
     */
    private static final double[][] SOURCE_BOXES = {
            {-6.45, 5.0, -7.65, 22.45, 13.6, 8.0},
            {-6.45, 12.5, 5.6, 22.45, 15.4, 7.6},
            {-6.5, 1.2, 5.2, 22.5, 2.4, 6.4},
            {-8, 4.2, -8, -6.4, 13.6, 8}, {-8, 13.6, -6.4, -6.4, 16, 8},
            {22.4, 4.2, -8, 24, 13.6, 8}, {22.4, 13.6, -6.4, 24, 16, 8},
            {-8, 0, -8, -6.4, 4.2, -5.6}, {-8, 0, 6.4, -6.4, 4.2, 8},
            {22.4, 0, -8, 24, 4.2, -5.6}, {22.4, 0, 6.4, 24, 4.2, 8}};
    private static final Map<ShapeKey, VoxelShape> SHAPES = buildShapes();

    public ChargingStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LEFT));
    }

    /** Source-model +X points to the viewer's left; the right cell is toward the viewer's right. */
    public static BlockPos partPosition(BlockPos master, Direction facing, Part part) {
        return part == Part.LEFT ? master : master.relative(facing.getCounterClockWise());
    }

    public static BlockPos masterPosition(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.LEFT ? position : position.relative(state.getValue(FACING).getClockWise());
    }

    /** A source-px point of the station (master-relative, the cooler's convention) in world coordinates. */
    public static Vec3 sourceToWorld(BlockPos master, Direction facing, double x, double y, double z) {
        Direction leftward = facing.getClockWise();
        double midX = master.getX() + 0.5 - leftward.getStepX() * 0.5, midZ = master.getZ() + 0.5 - leftward.getStepZ() * 0.5;
        double across = (x - 8) / 16, forward = -z / 16;
        return new Vec3(midX + leftward.getStepX() * across + facing.getStepX() * forward, master.getY() + y / 16,
                midZ + leftward.getStepZ() * across + facing.getStepZ() * forward);
    }

    private BlockState stateFor(Direction facing, Part part) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part);
    }

    private boolean matches(BlockState state, Direction facing, Part part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
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
                    || !level.isUnobstructed(stateFor(facing, part), position, CollisionContext.empty())) return false;
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

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlaceStructure(context, facing) ? stateFor(facing, Part.LEFT) : null;
    }

    /**
     * Called by the BlockItem inside its placement transaction. Cells are set with full neighbour updates, so adjacent
     * power cables reshape and connect to the back ports at once.
     */
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
                if (!level.setBlock(position, stateFor(facing, part), UPDATE_ALL)) return false;
            }
            success = true;
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

    /** Structure check: a cell without its partner disappears; a station that lost its floor breaks (and drops). */
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
        for (Part part : Part.values()) {
            if (!matches(level.getBlockState(partPosition(master, facing, part)), facing, part)) {
                level.removeBlock(position, false);
                return;
            }
        }
        if (!supported(level, master, facing)) level.destroyBlock(master, true);
    }

    /** Tray: place the held chargeable item (one), or take the item back with an empty hand. No GUI. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos master = masterPosition(position, state);
        if (!(level.getBlockEntity(master) instanceof ChargingStationBlockEntity station) || !station.isMaster())
            return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (station.item().isEmpty()) {
            if (held.isEmpty() || !ChargingStationBlockEntity.canCharge(held)) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            playTraySound(level, master, state.getValue(FACING));
            station.place(held);
            if (!player.getAbilities().instabuild) held.shrink(1);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        playTraySound(level, master, state.getValue(FACING));
        ItemStack removed = station.take();
        if (!player.getInventory().add(removed)) player.drop(removed, false);
        return InteractionResult.CONSUME;
    }

    /** Placing and taking the tray item: the vanilla leather armour equip sound (user's choice), from the tray. */
    private static void playTraySound(Level level, BlockPos master, Direction facing) {
        Vec3 at = sourceToWorld(master, facing, ITEM_X, ITEM_Y + 1.0, ITEM_Z);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.BLOCKS, 1.0F,
                0.97F + level.random.nextFloat() * 0.06F);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        // The selected cell keeps its own normal loot evaluation. The peer cell is removed without drops.
        removePeers(level, masterPosition(position, state), state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    /** The tray item drops once when the master goes, whichever cell was broken; the FE buffer is lost. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && state.getValue(PART) == Part.LEFT
                && level.getBlockEntity(position) instanceof ChargingStationBlockEntity station) station.dropContentsOnce();
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
                if ((keep == null || !peer.equals(keep)) && level.hasChunkAt(peer)
                        && matches(level.getBlockState(peer), facing, part))
                    level.setBlock(peer, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(new ShapeKey(state.getValue(PART), state.getValue(FACING)));
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
            // the left (master) cell covers source x 8..24, the right cell -8..8; block-local x = source x - cell start
            double cell = part == Part.LEFT ? 8 : -8;
            VoxelShape north = Shapes.empty();
            for (double[] b : SOURCE_BOXES) {
                double x0 = Math.max(b[0], cell), x1 = Math.min(b[3], cell + 16);
                if (x1 <= x0) continue;
                north = Shapes.or(north, Block.box(x0 - cell, b[1], b[2] + 8, x1 - cell, b[4], b[5] + 8));
            }
            Map<Direction, VoxelShape> rotated = HorizontalShapeUtils.rotations(north.optimize());
            for (Direction facing : Direction.Plane.HORIZONTAL) result.put(new ShapeKey(part, facing), rotated.get(facing));
        }
        return Map.copyOf(result);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /** Both cells carry a block entity: the master holds the station, the right cell forwards its power port to it. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.LEFT || type != AflBlockEntities.CHARGING_STATION.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ChargingStationBlockEntity>) (l, p, s, station) -> station.serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public enum Part implements StringRepresentable {
        LEFT("left"), RIGHT("right");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
}
