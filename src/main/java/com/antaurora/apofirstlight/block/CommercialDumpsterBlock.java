package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.CommercialDumpsterBlockEntity;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.meshshape.AflOverhangPickBlock;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
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

/**
 * Commercial Dumpster V2 (2026-10-02, tools/build-commercial-dumpster-v2.mjs): a 2 x 1 front-load dumpster with two
 * hinged lids that open separately, an 18-slot searchable container (the MASTER's CommercialDumpsterBlockEntity, which
 * also draws the whole dumpster). One class for every body colour (one block each, see {@link #colour()}). The MASTER is
 * the viewer's right half and owns the item drop; the SECONDARY (to the master's clockwise side, the viewer's left)
 * forwards everything to it. {@link #LEFT_OPEN} / {@link #RIGHT_OPEN} on both cells drive the lid animations, the shapes
 * and screen validity. Aiming at a half: shut, it opens that lid; open, the half searches; an open lid standing above the
 * cells (picked through AflOverhangPickBlock) shuts it.
 */
public final class CommercialDumpsterBlock extends HorizontalDirectionalBlock implements EntityBlock, AflOverhangPickBlock, MeshHitMultiCell {
    /** The hit mesh (docs/rendering/mesh_hit_runtime_v1.md): every cell hits on the master cell (its block entity draws the dumpster). */
    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return rootPosition(pos, state);
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty LEFT_OPEN = BooleanProperty.create("left_open");
    public static final BooleanProperty RIGHT_OPEN = BooleanProperty.create("right_open");
    /** tools/build-commercial-dumpster-v2.mjs, source px (master cell's bottom centre, +x the viewer's left, -z the front). */
    private static final double BODY_Z0 = -6.5, BODY_Z1 = 7.4, RIM_FRONT = 17.2, RIM_BACK = 20.8, LID_ABOVE_RIM = 0.95, LID_RIB = 0.45;
    private static final double LEFT_X = 15.5, RIGHT_X = 0.5;
    /** An open lid's box (tools/build-commercial-dumpster-v2.mjs stats.lidOpen, source px): y, z; x is its half's. */
    private static final double OPEN_LID_Y0 = 21.49, OPEN_LID_Y1 = 36.06, OPEN_LID_Z0 = 2.29, OPEN_LID_Z1 = 6.75;
    private static final double RAIL_Z0 = 6.43, RAIL_TOP = 21.49;
    /** The inner faces of the walls and the floor's top (source px). */
    private static final double INNER_X0 = -6.4, INNER_X1 = 22.4, INNER_Z0 = -5.9, INNER_Z1 = 6.8, FLOOR_TOP = 2.2;

    private record Mutation(LevelAccessor level, BlockPos root) {}
    private record ShapeKey(Part part, Direction facing, boolean open) {}

    private static final Set<Mutation> MUTATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<ShapeKey, VoxelShape> SHAPES = buildShapes();

    private final String colour;
    private final ResourceLocation meshProfile;

    public CommercialDumpsterBlock(Properties properties, String colour) {
        super(properties);
        this.colour = colour;
        this.meshProfile = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/commercial_dumpster_" + colour + ".json");
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MASTER)
                .setValue(LEFT_OPEN, false).setValue(RIGHT_OPEN, false));
    }

    /** Body colour (green, blue, brown, gray): picks the atlas through the mesh profile. */
    public String colour() {
        return colour;
    }

    public ResourceLocation meshProfile() {
        return meshProfile;
    }

    public static BlockPos partPosition(BlockPos root, Direction facing, Part part) {
        return part == Part.MASTER ? root : root.relative(facing.getClockWise());
    }

    public static BlockPos rootPosition(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.MASTER ? position : position.relative(state.getValue(FACING).getCounterClockWise());
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
            if (!level.getBlockState(position).canBeReplaced(localContext) || !level.getFluidState(position).isEmpty()) return false;
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

    /** Placed by a player: its contents are the player's own, never searched. */
    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof CommercialDumpsterBlockEntity dumpster) dumpster.markPlacedByPlayer();
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

    // ---- lids, contents, prompts ----

    /** A half of the dumpster, as the player sees it: LEFT is the secondary cell, RIGHT the master. */
    public enum Side { LEFT, RIGHT }

    public enum Action {
        OPEN("open"), CLOSE("close"), SEARCH("search"), VIEW("view");
        private final String hintKey;
        Action(String hintKey) { this.hintKey = hintKey; }
        /** hint.apocalypse_firstlight.dumpster.* suffix. */
        public String hintKey() { return hintKey; }
    }

    /** onLid: aimed at an open lid standing above the cells. */
    public record Target(Side side, Action action, boolean onLid) {}

    public static BooleanProperty lid(Side side) {
        return side == Side.LEFT ? LEFT_OPEN : RIGHT_OPEN;
    }

    /** The rim height (source px) at a depth, along the slant from the front wall to the back. */
    public static double rim(double z) {
        double t = Math.max(0, Math.min(1, (z - BODY_Z0) / (BODY_Z1 - BODY_Z0)));
        return RIM_FRONT + t * (RIM_BACK - RIM_FRONT);
    }

    /**
     * What a hit on the dumpster's own shapes does: a half whose lid is shut opens it; an open half (a hollow shape, see
     * {@link #cell}) searches or views, anywhere on it. Shutting is on the open lid itself ({@link #aimed}).
     */
    public static Target target(BlockState masterState, double[] local, CommercialDumpsterBlockEntity dumpster) {
        Side side = local[0] >= 8.0 ? Side.LEFT : Side.RIGHT;
        if (!masterState.getValue(lid(side))) return new Target(side, Action.OPEN, false);
        return new Target(side, dumpster.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH, false);
    }

    /**
     * What the player aims at, from their own view ray (use() and the prompt; the client's pick pulls a hit on an open lid
     * back into the cell, so the hit point alone cannot tell): the nearest of the open lids (shut that one) and the
     * dumpster's own shapes ({@link #target}). Null: the ray meets neither.
     */
    @Nullable
    public static Target aimed(BlockGetter level, BlockPos master, BlockState masterState, Player player, CommercialDumpsterBlockEntity dumpster) {
        Direction facing = masterState.getValue(FACING);
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getViewVector(1.0F).scale(player.getBlockReach() + 1.0));
        double best = Double.MAX_VALUE;
        Target result = null;
        for (Side side : Side.values()) {
            if (!masterState.getValue(lid(side))) continue;
            var hit = openLidBox(master, facing, side).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < best) {
                best = eye.distanceToSqr(hit.get());
                result = new Target(side, Action.CLOSE, true);
            }
        }
        for (Part part : Part.values()) {
            BlockPos pos = partPosition(master, facing, part);
            BlockState cell = level.getBlockState(pos);
            if (!(cell.getBlock() instanceof CommercialDumpsterBlock)) continue;
            BlockHitResult hit = shape(cell).clip(eye, end, pos);
            if (hit != null && eye.distanceToSqr(hit.getLocation()) < best) {
                best = eye.distanceToSqr(hit.getLocation());
                result = target(masterState, local(hit.getLocation(), master, facing), dumpster);
            }
        }
        return result;
    }

    /** An open lid's box in the world, standing above its half. */
    private static AABB openLidBox(BlockPos master, Direction facing, Side side) {
        double x0 = side == Side.LEFT ? 8.15 : -6.9, x1 = side == Side.LEFT ? 22.9 : 7.85;
        return new AABB(world(master, facing, x0, OPEN_LID_Y0, OPEN_LID_Z0), world(master, facing, x1, OPEN_LID_Y1, OPEN_LID_Z1));
    }

    /** The crosshair picks this cell through its own half's lid while that lid stands open (client/AflMeshShapePicking). */
    @Override
    public List<AABB> overhangPickBoxes(BlockGetter level, BlockState state, BlockPos pos) {
        Side side = state.getValue(PART) == Part.MASTER ? Side.RIGHT : Side.LEFT;
        if (!state.getValue(lid(side))) return List.of();
        return List.of(openLidBox(rootPosition(pos, state), state.getValue(FACING), side));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        BlockPos master = rootPosition(position, state);
        BlockState masterState = level.getBlockState(master);
        if (!matches(masterState, state.getValue(FACING), Part.MASTER)
                || !(level.getBlockEntity(master) instanceof CommercialDumpsterBlockEntity dumpster)) return InteractionResult.PASS;
        Target target = aimed(level, master, masterState, player, dumpster);
        if (target == null) target = target(masterState, local(hit.getLocation(), master, state.getValue(FACING)), dumpster);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        switch (target.action()) {
            case OPEN -> setLid(level, master, masterState, target.side(), true, player);
            case CLOSE -> setLid(level, master, masterState, target.side(), false, player);
            default -> player.openMenu(dumpster);
        }
        return InteractionResult.CONSUME;
    }

    private void setLid(Level level, BlockPos master, BlockState masterState, Side side, boolean open, Player player) {
        // world loot is rolled as a lid first opens, so the bags inside show how much it holds (container goods)
        if (open && level.getBlockEntity(master) instanceof CommercialDumpsterBlockEntity dumpster) dumpster.unpackLootTable(player);
        Direction facing = masterState.getValue(FACING);
        Mutation mutation = new Mutation(level, master.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos position = partPosition(master, facing, part);
                BlockState cell = level.getBlockState(position);
                if (matches(cell, facing, part)) level.setBlock(position, cell.setValue(lid(side), open), UPDATE_ALL);
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
        // the clips sit on the 10-tick lid animation (tools/build-commercial-dumpster-sounds-v1.mjs): pitch stays near 1
        Vec3 at = world(master, facing, side == Side.LEFT ? LEFT_X : RIGHT_X, RIM_FRONT, 0);
        level.playSound(null, at.x, at.y, at.z, open ? AflSounds.COMMERCIAL_DUMPSTER_OPEN.get() : AflSounds.COMMERCIAL_DUMPSTER_CLOSE.get(),
                SoundSource.BLOCKS, 0.9F, 0.98F + level.random.nextFloat() * 0.04F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, master);
    }

    /** What a click would do (WorldInteractionHint), and where to draw it: the shut lid's grip, the open lid, or the mouth. */
    public record Prompt(String key, Vec3 anchor) {}

    @Nullable
    public static Prompt prompt(BlockGetter level, BlockPos position, BlockState state, Vec3 hitLocation, Player player) {
        BlockPos master = rootPosition(position, state);
        BlockState masterState = level.getBlockState(master);
        if (!(masterState.getBlock() instanceof CommercialDumpsterBlock) || masterState.getValue(PART) != Part.MASTER
                || !(level.getBlockEntity(master) instanceof CommercialDumpsterBlockEntity dumpster)) return null;
        Direction facing = masterState.getValue(FACING);
        Target target = aimed(level, master, masterState, player, dumpster);
        if (target == null) target = target(masterState, local(hitLocation, master, facing), dumpster);
        double x = target.side() == Side.LEFT ? LEFT_X : RIGHT_X;
        Vec3 anchor = switch (target.action()) {
            case OPEN -> world(master, facing, x, RIM_FRONT + 0.4, BODY_Z0 - 1.0);
            case CLOSE -> world(master, facing, x, (OPEN_LID_Y0 + OPEN_LID_Y1) / 2, OPEN_LID_Z0);
            default -> world(master, facing, x, rim(-2.0) + 0.3, -2.0);
        };
        return new Prompt(target.action().hintKey(), anchor);
    }

    /** Source px (master cell's bottom centre, +x the viewer's left, -z the front) of a world point. */
    public static double[] local(Vec3 point, BlockPos master, Direction facing) {
        Direction left = facing.getClockWise();
        double dx = point.x - master.getX() - 0.5, dz = point.z - master.getZ() - 0.5;
        return new double[]{16 * (dx * left.getStepX() + dz * left.getStepZ()), 16 * (point.y - master.getY()),
                -16 * (dx * facing.getStepX() + dz * facing.getStepZ())};
    }

    /** World point of source px (the inverse of {@link #local}). */
    public static Vec3 world(BlockPos master, Direction facing, double x, double y, double z) {
        Direction left = facing.getClockWise();
        return new Vec3(master.getX() + 0.5 + (left.getStepX() * x - facing.getStepX() * z) / 16, master.getY() + y / 16,
                master.getZ() + 0.5 + (left.getStepZ() * x - facing.getStepZ() * z) / 16);
    }

    // ---- removal, drops, comparator ----

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos root = rootPosition(position, state);
        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) == Part.SECONDARY
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, root, new ItemStack(this));
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
            // the master's contents, by every removal path: revealed slots drop, never-seen loot is lost
            if (level.getBlockEntity(position) instanceof CommercialDumpsterBlockEntity dumpster) dumpster.dropContentsOnce();
            level.updateNeighbourForOutputSignal(position, this);
        }
        super.onRemove(state, level, position, replacement, movedByPiston);
    }

    private void removePeer(LevelAccessor level, BlockPos root, Direction facing, @Nullable BlockPos keep) {
        Mutation mutation = new Mutation(level, root.immutable());
        if (!MUTATIONS.add(mutation)) return;
        try {
            for (Part part : Part.values()) {
                BlockPos peer = partPosition(root, facing, part);
                if ((keep == null || !peer.equals(keep)) && level.hasChunkAt(peer) && matches(level.getBlockState(peer), facing, part)) {
                    level.setBlock(peer, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        } finally {
            MUTATIONS.remove(mutation);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparator fullness of the master's revealed slots only, so it never hints at unsearched loot. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos position) {
        return level.getBlockEntity(rootPosition(position, state)) instanceof CommercialDumpsterBlockEntity dumpster
                ? AflContainerSearch.revealedAnalogSignal(dumpster) : 0;
    }

    // ---- shapes, rendering ----

    /** The master's block entity draws the dumpster's moving parts (AFL Animated Block Mesh Runtime); resting parts: the chunk model (client/blockmesh/AflStaticMeshModel, docs/dev/render_performance_v1.md) of the master. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.MASTER ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    private static VoxelShape shape(BlockState state) {
        Part part = state.getValue(PART);
        return SHAPES.get(new ShapeKey(part, state.getValue(FACING), state.getValue(part == Part.MASTER ? RIGHT_OPEN : LEFT_OPEN)));
    }

    private static Map<ShapeKey, VoxelShape> buildShapes() {
        Map<ShapeKey, VoxelShape> shapes = new HashMap<>();
        for (Part part : Part.values()) for (boolean open : new boolean[]{false, true}) {
            Map<Direction, VoxelShape> rotations = HorizontalShapeUtils.rotations(cell(part, open));
            for (Direction facing : Direction.Plane.HORIZONTAL) shapes.put(new ShapeKey(part, facing, open), rotations.get(facing));
        }
        return Map.copyOf(shapes);
    }

    /**
     * One cell's half (north-facing block px; master: the right half, its outer side at x 0; secondary: the left half,
     * its outer side at x 16), plus the side fork pocket.
     * Shut: the body in four steps under the slanted rim (each step as high as the rim at its back) with the lid and its
     * grip on top. Open: the hollow box as modelled (user 2026-10-03: a solid open half did not match the open box):
     * floor, front and back walls with their rim bars and the rear rail, the outer side wall in four steps under the
     * slant; the open lid stands above the cells and has no shape (it is picked through {@link #overhangPickBoxes}).
     */
    private static VoxelShape cell(Part part, boolean open) {
        boolean master = part == Part.MASTER;
        double x0 = master ? 0.6 : 0.0, x1 = master ? 16.0 : 15.4;
        double[] edges = {1.1, 4.6, 8.1, 11.6, 15.8};
        VoxelShape shape = Shapes.empty();
        if (open) {
            double fz0 = INNER_Z0 + 8, bz1 = INNER_Z1 + 8, wall0 = master ? 0.6 : INNER_X1 - 8, wall1 = master ? INNER_X0 + 8 : 15.4;
            shape = Shapes.or(shape, Block.box(master ? 1.0 : 0.0, 0, 1.5, master ? 16.0 : 15.0, FLOOR_TOP, 15.4),
                    Block.box(x0, 0, 1.1, x1, RIM_FRONT, fz0), Block.box(x0, 0, bz1, x1, RIM_BACK, 15.8),
                    Block.box(master ? 1.0 : 0.0, rim(RAIL_Z0), RAIL_Z0 + 8, master ? 16.0 : 15.0, RAIL_TOP, 15.8));
            double[] side = {fz0, 5.25, 8.45, 11.65, bz1};
            for (int i = 0; i + 1 < side.length; i++)
                shape = Shapes.or(shape, Block.box(wall0, 0, side[i], wall1, rim(side[i + 1] - 8), side[i + 1]));
        } else {
            for (int i = 0; i + 1 < edges.length; i++) {
                double top = rim(edges[i + 1] - 8) + LID_ABOVE_RIM + 0.05 + LID_RIB;
                shape = Shapes.or(shape, Block.box(x0, 0, edges[i], x1, top, edges[i + 1]));
            }
            shape = Shapes.or(shape, Block.box(master ? 1.1 : 0.0, RIM_FRONT - 1.0, 0.2, master ? 16.0 : 14.9, RIM_FRONT + 1.0, 1.1));
        }
        shape = Shapes.or(shape, master ? Block.box(0, 6.0, 2.6, 1.0, 9.6, 13.4) : Block.box(15.0, 6.0, 2.6, 16.0, 9.6, 13.4));
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.MASTER ? new CommercialDumpsterBlockEntity(position, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LEFT_OPEN, RIGHT_OPEN);
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
