package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.block.LogMount;
import com.antaurora.apofirstlight.block.WallOutletBlock;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.antaurora.apofirstlight.item.ShovelItem;
import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.meshshape.AflOverhangPickBlock;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): the bedroom and storage furniture of the forest start
 * home B (M4 round 2 interior). User 2026-10-15: a Mesh wooden bed, wardrobe, nightstand and tool rack; an old big steel
 * chest freezer beside the commercial one; an AFL storage chest in place of the vanilla chest, vanilla logic with our
 * own ID (2026-10-15: a two-cell blanket chest instead). Mesh blocks (tools/build-wood-furniture-v2.mjs), drawn facing north:
 * the bed and tool rack {@link CabinPieces} (cabin_mesh/furniture/); the wardrobe, nightstand, storage chest and freezer AFL
 * Animated Block Meshes (Furniture Mesh V1, 2026-10-15, tools/furniture/furniture-mesh.mjs: doors, drawer and lids move
 * smoothly, the master cell's block entity the host, the resting piece chunk-drawn):
 * <ul>
 * <li>{@link Bed}: a vanilla bed (sleep, spawn point, the nether's explosion, the bounce), two cells; facing = foot to head.</li>
 * <li>{@link Wardrobe}: two cells high, 27 slots; {@code open} while anyone has it open (the doors swing out).</li>
 * <li>{@link Nightstand}: 9 slots (the drawer pulls out); the emergency radio stands on its top (EmergencyRadio surface
 * {@code nightstand}).</li>
 * <li>{@link ToolRack}: on a wall, three pegs for long tools and a shelf for two small items, the gun rack's hands-only
 * logic; on round logs it stands on a pad ({@link LogMount} kind 12).</li>
 * <li>{@link StorageChest}: a frame-and-panel blanket chest over two cells across, 27 slots; the lid raised by the player.</li>
 * <li>{@link VintageFreezer}: two cells across, 27 slots, a plug-in appliance (cord, pilot lamp, compressor, cold mist
 * while open and powered).</li>
 * </ul>
 * Mining (AGENTS.md audit): the wood pieces on the axe, no tier, no correct-tool requirement (vanilla wood); the freezer
 * as the commercial chest freezer (pickaxe, iron tier, correct tool required). Each drops itself (two-cell pieces from
 * one cell: the bed's head, the wardrobe's lower, the chest's and freezer's left) and its contents.
 */
public final class WoodFurnitureV2 {
    public static BlockEntityType<WoodToolRackBlockEntity> TOOL_RACK;
    public static BlockEntityType<StorageChestBlockEntity> CHEST;
    public static BlockEntityType<VintageFreezerBlockEntity> FREEZER;

    private WoodFurnitureV2() {}

    /** Called from CabinBlocks#register after the blocks (the wardrobe and nightstand use V1's counter type, WoodFurniture#register). */
    static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            TOOL_RACK = BlockEntityType.Builder.of(WoodToolRackBlockEntity::new, CabinBlocks.block("wooden_tool_rack")).build(null);
            CHEST = BlockEntityType.Builder.of(StorageChestBlockEntity::new, CabinBlocks.block("wooden_storage_chest")).build(null);
            FREEZER = BlockEntityType.Builder.of(VintageFreezerBlockEntity::new, CabinBlocks.block("vintage_chest_freezer")).build(null);
            helper.register(CabinBlocks.id("wooden_tool_rack"), TOOL_RACK);
            helper.register(CabinBlocks.id("wooden_storage_chest"), CHEST);
            helper.register(CabinBlocks.id("vintage_chest_freezer"), FREEZER);
        });
    }

    private static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // ------------------------------------------------------------------------------------------------------ bed

    /**
     * A single wooden bed over two cells: posts, a frame-and-panel head and foot board, a ticking mattress (top 0.54), a
     * wool blanket, sheet and pillow. All of vanilla's bed behaviour (BedBlock); no block entity (the look is the mesh).
     */
    public static final class Bed extends BedBlock implements CabinPieces {
        private static final VoxelShape[] HEAD = new VoxelShape[4], FOOT = new VoxelShape[4];

        static {
            // as the mesh: the mattress and frame to 0.54, the head board to the post caps, the foot board to 0.74
            for (int r = 0; r < 4; r++) {
                HEAD[r] = CabinBlocks.turned(r, new double[]{0.32, 0, 0, 15.68, 8.64, 14.8}, new double[]{0.32, 8.64, 13.92, 15.68, 16, 14.8});
                FOOT[r] = CabinBlocks.turned(r, new double[]{0.32, 0, 0.24, 15.68, 8.64, 16}, new double[]{0.32, 8.64, 0.24, 15.68, 11.84, 1.12});
            }
        }

        Bed(Properties p) { super(DyeColor.BROWN, p); }

        /** The pieces are drawn with the head at the south (+z): facing (foot to head) south is no turn. */
        private static int turn(BlockState state) { return CabinBlocks.turn(state.getValue(FACING).getOpposite()); }

        @Override
        @SuppressWarnings("deprecation")
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return (state.getValue(PART) == BedPart.HEAD ? HEAD : FOOT)[turn(state)];
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return null; }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add(state.getValue(PART) == BedPart.HEAD ? "furniture/bed_head" : "furniture/bed_foot", turn(state));
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // ------------------------------------------------------------------------------------------------ cabinets

    /**
     * A wood cabinet against a wall (facing = its front) whose things a {@link WoodCounterBlockEntity} keeps, a chest screen;
     * {@code open}: its doors / drawer / lid open, opened and closed by the player ({@link FurnitureOpening}, regions from
     * its Mesh Shape profile). The master cell holds the block entity (one-cell cabinets: their own).
     */
    abstract static class Cabinet extends BaseEntityBlock implements WoodFurniture.Storage, AflMeshInteractionBlock, MeshHitMultiCell {
        private final ResourceLocation shapeProfile;

        Cabinet(String id, Properties p) {
            super(p);
            shapeProfile = FurnitureOpening.profile(id);
        }

        @Override
        public ResourceLocation meshShapeProfile() { return shapeProfile; }

        @Override
        public String meshShapeState(BlockState state) { return state.getValue(OPEN) ? "open" : "closed"; }

        @Override
        public @Nullable String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
            return FurnitureOpening.hintKey(level, state, master(state, pos), region);
        }

        /** The cell with the block entity. */
        BlockPos master(BlockState state, BlockPos pos) { return pos; }

        /** The master draws the whole piece (Furniture Mesh V1): every cell hits on its mesh. */
        @Override
        public BlockPos meshHitMaster(BlockState state, BlockPos pos) { return master(state, pos); }

        boolean isMaster(BlockState state) { return true; }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return isMaster(state) ? new WoodCounterBlockEntity(pos, state) : null; }

        @Override
        @SuppressWarnings("deprecation")
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        /** Closed: open the doors / drawer / lid; open: the interior's screen (search or view), or close them. */
        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return FurnitureOpening.use(this, state, level, pos, master(state, pos), player);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WoodCounterBlockEntity be) {
                be.dropContentsOnce();
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, moved);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            if (!(level.getBlockEntity(pos) instanceof WoodCounterBlockEntity be)) return;
            if (!level.isClientSide) be.markPlacedByPlayer();
            if (stack.hasCustomHoverName()) be.setCustomName(stack.getHoverName());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean hasAnalogOutputSignal(BlockState state) { return true; }

        /** Comparator fullness from revealed slots only, so it never hints at unsearched loot. */
        @Override
        @SuppressWarnings("deprecation")
        public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            return level.getBlockEntity(master(state, pos)) instanceof WoodCounterBlockEntity be ? AflContainerSearch.revealedAnalogSignal(be) : 0;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // ------------------------------------------------------------------------------------------------ wardrobe

    /** A wardrobe two cells high (the lower holds the 27 slots): a drawer, two doors, a hat shelf and hanging rod inside. */
    public static final class Wardrobe extends Cabinet {
        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        private static final VoxelShape[] LOWER = new VoxelShape[4], UPPER = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++) {
                LOWER[r] = CabinBlocks.turned(r, new double[]{0.32, 0, 5.76, 15.68, 16, 14.8});
                UPPER[r] = CabinBlocks.turned(r, new double[]{0.32, 0, 5.76, 15.68, 13.76, 14.8}, new double[]{0, 13.76, 5.36, 16, 15.36, 14.8});
            }
        }

        Wardrobe(Properties p) {
            super("wooden_wardrobe", p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF, OPEN); }

        @Override
        BlockPos master(BlockState state, BlockPos pos) { return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below(); }

        @Override
        public int meshShapeCell(BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : 0; }

        @Override
        boolean isMaster(BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.LOWER; }

        @Override
        public int slots() { return 27; }

        @Override
        public double soundHeight() { return 1.0; }

        @Override
        public void setOpen(Level level, BlockPos pos, BlockState state, boolean open) {
            BlockPos lower = master(state, pos);
            for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
                BlockState s = level.getBlockState(p);
                if (s.is(this)) level.setBlock(p, s.setValue(OPEN, open), 3);
            }
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockPos pos = context.getClickedPos();
            Level level = context.getLevel();
            if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
            super.setPlacedBy(level, pos, state, placer, stack);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }

        /** The other half gone: this one goes too (the lower drops the wardrobe and its things, as a door's lower half). */
        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            DoubleBlockHalf half = state.getValue(HALF);
            if (d.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (d == Direction.UP))
                return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
            return state;
        }

        /** The upper half broken in creative or without the right tool: the lower goes without a drop (vanilla doors; the things still drop). */
        @Override
        public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state)) && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                BlockPos below = pos.below();
                BlockState lower = level.getBlockState(below);
                if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, below, Block.getId(lower));
                }
            }
            super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER)[CabinBlocks.turn(state.getValue(FACING))];
        }
    }

    // ---------------------------------------------------------------------------------------------- nightstand

    /** A nightstand (top at 66 cm): a drawer over an open shelf, 9 slots; the emergency radio stands on it. */
    public static final class Nightstand extends Cabinet {
        public static final float TOP = 0.66F;
        private static final VoxelShape[] SHAPES = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++) SHAPES[r] = CabinBlocks.turned(r, new double[]{3.68, 0, 8.16, 12.32, TOP * 16, 14.8});
        }

        Nightstand(Properties p) {
            super("wooden_nightstand", p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN); }

        @Override
        public int slots() { return 9; }

        @Override
        public double soundHeight() { return 0.55; }

        @Override
        public void setOpen(Level level, BlockPos pos, BlockState state, boolean open) { level.setBlock(pos, state.setValue(OPEN, open), 3); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[CabinBlocks.turn(state.getValue(FACING))];
        }
    }

    // ---------------------------------------------------------------------------------------------- tool rack

    /**
     * A wooden tool rack on a wall (facing = away from it; a sturdy wall behind): three pairs of pegs (slots 0..2, right to
     * left as seen from the front: long tools, any single non-block item that is not a gun, hung head up) under a shelf
     * (3, 4, left and right: any item that is not a block). Right-click with an item hangs / puts it where aimed (else the
     * next free place of its kind), with an empty hand takes what is aimed at. On round logs it stands out on a pad.
     */
    public static final class ToolRack extends BaseEntityBlock implements CabinPieces, LogMount.Padded {
        /** The north frame (tools/build-wood-furniture-v2.mjs): pegs, the tools' plane, the shelf top and places. */
        public static final double[] TOOL_X = {0.2, 0.5, 0.8}, SHELF_X = {0.7, 0.3};
        public static final double PEG_Y = 0.745, PEG_RADIUS = 0.011, TOOL_Z = 0.925, SHELF_Y = 0.882, SHELF_Z = 0.895, SHELF_FROM = 0.86;
        /** A hung tool: its model's square (the vanilla tool sprite's diagonal hangs plumb) and its middle's height. */
        public static final double TOOL_SCALE = 0.58, TOOL_MID_Y = 0.42;
        private static final VoxelShape[][] OUTLINE = new VoxelShape[2][4], COLLISION = new VoxelShape[2][4];

        static {
            VoxelShape outline = net.minecraft.world.phys.shapes.Shapes.or(Block.box(0, 0.5, 14.0, 16, 14.4, 16), Block.box(0.48, 10.88, 12.8, 15.52, 14.4, 16));
            VoxelShape collision = Block.box(0.48, 10.88, 12.8, 15.52, 14.4, 16);
            for (int m = 0; m < 2; m++) {
                VoxelShape o = m == 0 ? outline : LogMount.onPad(outline, LogMount.PAD_TOOL_RACK), c = m == 0 ? collision : LogMount.out(collision);
                for (int r = 0; r < 4; r++) {
                    OUTLINE[m][r] = CabinBlocks.turned(r, px(o));
                    COLLISION[m][r] = CabinBlocks.turned(r, px(c));
                }
            }
        }

        /** A shape's boxes in px (for {@link CabinBlocks#turned}). */
        private static double[][] px(VoxelShape s) {
            return s.toAabbs().stream().map(b -> new double[]{b.minX * 16, b.minY * 16, b.minZ * 16, b.maxX * 16, b.maxY * 16, b.maxZ * 16}).toArray(double[][]::new);
        }

        ToolRack(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LogMount.LOG, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LogMount.LOG); }

        @Override
        public int logPad(BlockState state) { return LogMount.PAD_TOOL_RACK; }

        /** Against the first wall that holds it, from the horizontal direction nearest the player's look. */
        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            for (Direction d : context.getNearestLookingDirections()) {
                if (!d.getAxis().isHorizontal()) continue;
                BlockState s = defaultBlockState().setValue(FACING, d.getOpposite());
                if (s.canSurvive(context.getLevel(), context.getClickedPos()))
                    return LogMount.placed(s, context.getLevel(), context.getClickedPos(), d.getOpposite());
            }
            return null;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            BlockPos wall = pos.relative(f.getOpposite());
            return level.getBlockState(wall).isFaceSturdy(level, wall, f);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            if (d == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
            return LogMount.updated(state, d, neighbour, state.getValue(FACING));
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        private static int mount(BlockState state) { return LogMount.mounted(state) ? 1 : 0; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return OUTLINE[mount(state)][CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return COLLISION[mount(state)][CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WoodToolRackBlockEntity(pos, state); }

        /**
         * What the pegs take: one item, not a block and not a gun (tools, a crowbar, a fishing rod, a sword); a shovel only
         * empty (it hangs by its D grip, blade down: a load would pour out; 2026-10-22).
         */
        public static boolean longTool(ItemStack s) {
            return !s.isEmpty() && s.getMaxStackSize() == 1 && !(s.getItem() instanceof BlockItem) && !(s.getItem() instanceof NativeGunItem)
                    && !(s.getItem() instanceof ShovelItem && ShovelItem.load(s).stored() > 0);
        }

        /** What the shelf takes: anything that is not a block, and not a shovel (1.48 m: the pegs only). */
        public static boolean shelfItem(ItemStack s) { return !s.isEmpty() && !(s.getItem() instanceof BlockItem) && !(s.getItem() instanceof ShovelItem); }

        /** A point in the north frame (moved out onto its pad on logs) turned to the facing, world coordinates. */
        static Vec3 local(BlockState state, BlockPos pos, double x, double y, double z) {
            z -= LogMount.depth(state);
            for (int k = 0; k < CabinBlocks.turn(state.getValue(FACING)); k++) { double t = x; x = 1 - z; z = t; }
            return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        }

        /** The slot aimed at: the shelf from its board up (left / right half), else the peg pair nearest across. */
        public static int slotAt(BlockState state, BlockPos pos, Vec3 hit) {
            double x = hit.x - pos.getX(), y = hit.y - pos.getY(), z = hit.z - pos.getZ();
            for (int k = 0; k < CabinBlocks.turn(state.getValue(FACING)); k++) { double t = z; z = 1 - x; x = t; }
            if (y >= SHELF_FROM) return x >= 0.5 ? 3 : 4;
            return x < 0.35 ? 0 : x < 0.65 ? 1 : 2;
        }

        /** Where a slot's item sits (for the prompt). */
        public static Vec3 slotPoint(BlockState state, BlockPos pos, int slot) {
            return slot < 3 ? local(state, pos, TOOL_X[slot], PEG_Y, TOOL_Z) : local(state, pos, SHELF_X[slot - 3], SHELF_Y + 0.06, SHELF_Z);
        }

        /** The slot an item in hand goes to when aimed at `aimed`: that one if it takes it and is free, else the first free of its kind; -1: none. */
        public static int target(WoodGunRackBlockEntity rack, ItemStack held, int aimed) {
            boolean tool = longTool(held), shelf = shelfItem(held);
            if (aimed < 3 && tool && rack.item(aimed).isEmpty()) return aimed;
            if (aimed >= 3 && shelf && rack.item(aimed).isEmpty()) return aimed;
            if (tool) for (int k = 0; k < 3; k++) if (rack.item(k).isEmpty()) return k;
            if (shelf) for (int k = 3; k < 5; k++) if (rack.item(k).isEmpty()) return k;
            return -1;
        }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof WoodToolRackBlockEntity rack)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            int aimed = slotAt(state, pos, hit.getLocation());
            if (held.isEmpty()) {
                if (hand != InteractionHand.MAIN_HAND || rack.item(aimed).isEmpty()) return InteractionResult.PASS;
                if (!level.isClientSide) {
                    player.setItemInHand(hand, rack.take(aimed));
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!longTool(held) && !shelfItem(held)) return InteractionResult.PASS;   // neither the pegs nor the shelf (a shovel: the pegs only)
            int slot = target(rack, held, aimed);
            if (slot < 0) return InteractionResult.CONSUME;   // full: no use of the item at the rack
            if (!level.isClientSide) {
                rack.put(slot, slot < 3 ? held.split(1) : held.copyAndClear());
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WoodToolRackBlockEntity rack)
                for (int k = 0; k < WoodGunRackBlockEntity.SLOTS; k++) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rack.item(k));
            super.onRemove(state, level, pos, newState, moved);
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add(LogMount.mounted(state) ? "furniture/tool_rack_log" : "furniture/tool_rack", CabinBlocks.turn(state.getValue(FACING)));
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // ----------------------------------------------------------------------------------- two cells across

    /**
     * A cabinet over two cells across: left = the master (the block entity), the north frame's x 0..1; right x 1..2, at
     * {@code facing.getClockWise()} (the block's own left and right, as vanilla's chest: from the front the left half is
     * on the right). Placed from the left cell; each half's profile state is its side and the lid (`left_open` ...), the
     * regions over the whole piece in each side's frame; one half gone, the other goes (the master drops the piece and its
     * things; the right half broken in creative or without the right tool: no drop).
     */
    public abstract static class Wide extends Cabinet {
        public enum Part implements StringRepresentable {
            LEFT, RIGHT;

            @Override
            public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
        }

        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

        Wide(String id, Properties p) { super(id, p); }

        /** From the left (master) cell toward the right one: the north frame's +x. */
        static Direction toRight(BlockState state) { return state.getValue(FACING).getClockWise(); }

        @Override
        BlockPos master(BlockState state, BlockPos pos) { return state.getValue(PART) == Part.LEFT ? pos : pos.relative(toRight(state).getOpposite()); }

        @Override
        boolean isMaster(BlockState state) { return state.getValue(PART) == Part.LEFT; }

        @Override
        public String meshShapeState(BlockState state) { return state.getValue(PART).getSerializedName() + (state.getValue(OPEN) ? "_open" : "_closed"); }

        /** The cell with the block entity of either half. */
        public static BlockPos masterPosition(BlockPos pos, BlockState state) {
            return state.getValue(PART) == Part.LEFT ? pos : pos.relative(state.getValue(FACING).getCounterClockWise());
        }

        /** A north-frame point (blocks, from the master cell's corner; x across both cells) in the world. */
        public static Vec3 local(BlockPos master, Direction facing, double x, double y, double z) {
            return WallOutletBlock.local(master, facing, (x - 0.5) * 16, y * 16, (z - 0.5) * 16);
        }

        @Override
        public void setOpen(Level level, BlockPos pos, BlockState state, boolean open) {
            BlockPos m = master(state, pos);
            for (BlockPos p : new BlockPos[]{m, m.relative(toRight(state))}) {
                BlockState s = level.getBlockState(p);
                if (s.is(this)) level.setBlock(p, s.setValue(OPEN, open), 3);
            }
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction facing = context.getHorizontalDirection().getOpposite();
            BlockPos right = context.getClickedPos().relative(facing.getClockWise());
            if (!context.getLevel().getBlockState(right).canBeReplaced(context) || !context.getLevel().getWorldBorder().isWithinBounds(right)) return null;
            return defaultBlockState().setValue(FACING, facing);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            level.setBlock(pos.relative(toRight(state)), state.setValue(PART, Part.RIGHT), 3);
            super.setPlacedBy(level, pos, state, placer, stack);
        }

        /** The other half gone: this one goes too (the master drops the piece and its things). */
        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            Direction toOther = state.getValue(PART) == Part.LEFT ? toRight(state) : toRight(state).getOpposite();
            if (d != toOther) return state;
            return neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART) && neighbour.getValue(FACING) == state.getValue(FACING)
                    ? state : Blocks.AIR.defaultBlockState();
        }

        /** The right half broken in creative or without the right tool: the master goes without a drop (its things still drop). */
        @Override
        public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state)) && state.getValue(PART) == Part.RIGHT) {
                BlockPos m = master(state, pos);
                BlockState ms = level.getBlockState(m);
                if (ms.is(this) && ms.getValue(PART) == Part.LEFT) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, m, Block.getId(ms));
                }
            }
            super.playerWillDestroy(level, pos, state, player);
        }
    }

    // -------------------------------------------------------------------------------------------- storage chest

    /**
     * A frame-and-panel blanket chest over two cells across (user 2026-10-15: concept A of design/buildings/
     * forest_start_home_v1/m4_sheets/chest_concepts.png, one piece over two cells, no joining of two chests), 124 x 50 cm,
     * 60 cm high: 27 slots kept by the left cell ({@link StorageChestBlockEntity}); the lid opened and closed by the
     * player, the screen from the open chest ({@link FurnitureOpening}: regions lid / interior). The open lid stands at the
     * back up to 1.10 over the floor, above the cells: picked there too.
     */
    public static final class StorageChest extends Wide implements AflOverhangPickBlock {
        /** [part][open][turn]. */
        private static final VoxelShape[][][] SHAPES = new VoxelShape[2][2][4];

        static {
            // tools/build-wood-furniture-v2.mjs CHEST: the case and mouldings to 0.565 (x 0.366..1.634, z 0.236..0.756); the
            // lid to 0.598, overhanging (x 0.355..1.645, z 0.225..0.754); open, the lid at the back (z 0.658..0.790) to the cell's top
            for (int r = 0; r < 4; r++) {
                SHAPES[0][0][r] = CabinBlocks.turned(r, new double[]{5.856, 0, 3.776, 16, 9.04, 12.096}, new double[]{5.68, 9.04, 3.6, 16, 9.568, 12.064});
                SHAPES[0][1][r] = CabinBlocks.turned(r, new double[]{5.856, 0, 3.776, 16, 9.04, 12.096}, new double[]{5.68, 9.04, 10.53, 16, 16, 12.64});
                SHAPES[1][0][r] = CabinBlocks.turned(r, new double[]{0, 0, 3.776, 10.144, 9.04, 12.096}, new double[]{0, 9.04, 3.6, 10.32, 9.568, 12.064});
                SHAPES[1][1][r] = CabinBlocks.turned(r, new double[]{0, 0, 3.776, 10.144, 9.04, 12.096}, new double[]{0, 9.04, 10.53, 10.32, 16, 12.64});
            }
        }

        StorageChest(Properties p) {
            super("wooden_storage_chest", p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, Part.LEFT).setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART, OPEN); }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return isMaster(state) ? new StorageChestBlockEntity(pos, state) : null; }

        /** The open lid above the cells (tools/build-wood-furniture-v2.mjs chestShapes: to 1.096). */
        @Override
        public List<AABB> overhangPickBoxes(BlockGetter level, BlockState state, BlockPos pos) {
            if (!state.getValue(OPEN)) return List.of();
            BlockPos m = master(state, pos);
            Direction f = state.getValue(FACING);
            return List.of(new AABB(local(m, f, 0.355, 1.0, 0.658), local(m, f, 1.645, 1.1, 0.792)));
        }

        @Override
        public int slots() { return 27; }

        @Override
        public double soundHeight() { return 0.6; }

        /** A wooden lid on its hinges (vanilla's chest). */
        @Override
        public SoundEvent openSound() { return SoundEvents.CHEST_OPEN; }

        @Override
        public SoundEvent closeSound() { return SoundEvents.CHEST_CLOSE; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(PART).ordinal()][state.getValue(OPEN) ? 1 : 0][CabinBlocks.turn(state.getValue(FACING))];
        }
    }

    // --------------------------------------------------------------------------------------- vintage freezer

    /**
     * A 1960s chest deep freezer over two cells across ({@link Wide}: left = the master), 160 x 74
     * cm, 90 cm high: one hinged lid ({@code open}: raised by the player, the commercial freezer's lids slide instead),
     * 27 slots, a pilot lamp ({@code lit}: the freezer has power), its cord from a C14 inlet low on the back (sneak with an
     * empty hand: the plug into the hand, as every appliance's).
     */
    public static final class VintageFreezer extends Wide implements AflOverhangPickBlock {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        /** tools/build-wood-furniture-v2.mjs FREEZER: the inlet (x, y) on the back plane; the back. */
        public static final double[] INLET = {0.38, 0.16};
        public static final double BACK = 0.92;
        private static final VoxelShape[][] SHAPES = new VoxelShape[2][4];

        static {
            for (int r = 0; r < 4; r++) {
                SHAPES[0][r] = CabinBlocks.turned(r, new double[]{3.2, 0, 2.88, 16, 14.4, 14.8});
                SHAPES[1][r] = CabinBlocks.turned(r, new double[]{0, 0, 2.88, 12.8, 14.4, 14.8});
            }
        }

        VintageFreezer(Properties p) {
            super("vintage_chest_freezer", p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, Part.LEFT).setValue(OPEN, false).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART, OPEN, LIT); }

        /** The open lid stands up to 1.61 over the floor, above the cells: picked there too (tools/build-wood-furniture-v2.mjs FREEZER_SHAPES). */
        @Override
        public List<AABB> overhangPickBoxes(BlockGetter level, BlockState state, BlockPos pos) {
            if (!state.getValue(OPEN)) return List.of();
            BlockPos m = master(state, pos);
            Direction f = state.getValue(FACING);
            AABB lid = new AABB(local(m, f, 0.195, 1.0, 0.767), local(m, f, 1.805, 1.61, 0.985));
            return List.of(lid);
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return isMaster(state) ? new VintageFreezerBlockEntity(pos, state) : null; }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (!isMaster(state) || type != FREEZER) return null;
            return level.isClientSide
                    ? (l, p, s, be) -> com.antaurora.apofirstlight.client.ColdMist.vintageFreezerTick(l, p, s.getValue(FACING), s.getValue(LIT), s.getValue(OPEN), be)
                    : (l, p, s, be) -> ((VintageFreezerBlockEntity) be).serverTick();
        }

        @Override
        public int slots() { return 27; }

        @Override
        public double soundHeight() { return 0.85; }

        /** A heavy steel lid on its gasket (vanilla iron trapdoor, deeper) until it has a recorded take. */
        @Override
        public SoundEvent openSound() { return SoundEvents.IRON_TRAPDOOR_OPEN; }

        @Override
        public SoundEvent closeSound() { return SoundEvents.IRON_TRAPDOOR_CLOSE; }

        @Override
        public float soundPitch() { return 0.7F; }

        /** Sneak with an empty hand: the plug; else the lid / the interior as every cabinet. */
        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && !player.isSpectator()) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                return player instanceof ServerPlayer server ? PowerPlugs.useDevice(level, master(state, pos), server) : InteractionResult.PASS;
            }
            return super.use(state, level, pos, player, hand, hit);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VintageFreezerBlockEntity freezer) freezer.plugCord().release();
            super.onRemove(state, level, pos, newState, moved);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(PART).ordinal()][CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }
    }
}
