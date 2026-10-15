package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.WeaponClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md, 2026-10-13): the generic wood furniture, first placed in the forest
 * start home B (M2.1). One set for every wood (user 2026-10-13: any planks make it, recipes later), in a neutral stained
 * finish; the building materials stay per species. Mesh blocks (tools/build-wood-furniture-v1.mjs), drawn facing north: the
 * table, chair and gun rack {@link CabinPieces} (cabin_mesh/furniture/); the counters AFL Animated Block Meshes (Furniture
 * Mesh V1, 2026-10-15, tools/furniture/furniture-mesh.mjs: the doors swing smoothly, the resting piece chunk-drawn):
 * <ul>
 * <li>{@link Table}: 76 cm, joins the tables beside it (north / east / south / west, like a fence) into one top.</li>
 * <li>{@link Chair}: right-click sits (an invisible {@link FurnitureSeat}), sneak stands up; facing = where the sitter looks.</li>
 * <li>{@link Counter}: the kitchen counter (18 slots) and the sink counter (9; the enamel sink and pitcher pump are looks
 * only in V1), a vanilla chest screen; {@code open} while someone has it open (the doors swing out).</li>
 * <li>{@link GunRack}: on a wall (facing = away from it); three notches for long guns, a shelf for two small items;
 * right-click with an item hangs or puts it, with an empty hand takes it ({@link WoodGunRackBlockEntity}).</li>
 * </ul>
 * Mining (AGENTS.md audit): wood furniture on the axe, no tier, no correct-tool requirement (vanilla wood); each drops
 * itself and its contents.
 */
public final class WoodFurniture {
    public static BlockEntityType<WoodCounterBlockEntity> COUNTER;
    public static BlockEntityType<WoodGunRackBlockEntity> GUN_RACK;
    public static EntityType<FurnitureSeat> SEAT;

    private WoodFurniture() {}

    /** Called from CabinBlocks#register: the block entity types (after the blocks) and the seat entity. */
    static void register(RegisterEvent event) {
        event.register(net.minecraftforge.registries.ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            // Wood Furniture V2 (2026-10-15): the wardrobe and nightstand keep their things the same way
            COUNTER = BlockEntityType.Builder.of(WoodCounterBlockEntity::new, CabinBlocks.block("wooden_kitchen_counter"),
                    CabinBlocks.block("wooden_sink_counter"), CabinBlocks.block("wooden_wardrobe"), CabinBlocks.block("wooden_nightstand")).build(null);
            GUN_RACK = BlockEntityType.Builder.of(WoodGunRackBlockEntity::new, CabinBlocks.block("wooden_gun_rack")).build(null);
            helper.register(CabinBlocks.id("wooden_counter"), COUNTER);
            helper.register(CabinBlocks.id("wooden_gun_rack"), GUN_RACK);
        });
        event.register(net.minecraftforge.registries.ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            SEAT = EntityType.Builder.<FurnitureSeat>of(FurnitureSeat::new, MobCategory.MISC).sized(0.25F, 0.25F).noSave().noSummon()
                    .fireImmune().clientTrackingRange(10).build("furniture_seat");
            helper.register(CabinBlocks.id("furniture_seat"), SEAT);
        });
    }

    private static final double[][] NONE = {};

    /**
     * Furniture whose things a {@link WoodCounterBlockEntity} keeps (the counters; Wood Furniture V2, 2026-10-15: the
     * wardrobe, the nightstand, the vintage chest freezer): its slot count (9, 18 or 27: a 9 x 1, 2 or 3 chest screen),
     * where and how its doors sound, and how it shows open.
     */
    public interface Storage {
        int slots();

        /** Sets the open look of the furniture at pos (every cell it has). */
        void setOpen(Level level, BlockPos pos, BlockState state, boolean open);

        /** Height of the doors' sound over the block entity's cell (blocks); it plays 0.4 out of the front. */
        default double soundHeight() { return 0.4; }

        default net.minecraft.sounds.SoundEvent openSound() { return com.antaurora.apofirstlight.registry.AflSounds.WOOD_CABINET_OPEN.get(); }

        default net.minecraft.sounds.SoundEvent closeSound() { return com.antaurora.apofirstlight.registry.AflSounds.WOOD_CABINET_CLOSE.get(); }

        default float soundPitch() { return 1.0F; }
    }

    // ---------------------------------------------------------------------------------------------------- table

    /** A 76 cm wooden table; joined to the tables beside it (north / east / south / west), legs at the outer corners. */
    public static final class Table extends Block implements CabinPieces {
        public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST,
                SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
        private static final BooleanProperty[] SIDES = {NORTH, EAST, SOUTH, WEST};
        private static final VoxelShape[] SHAPES = new VoxelShape[16];

        static {
            // as the mesh (tools/build-wood-furniture-v1.mjs table()): the top 2 cm in from an open side, the legs at the
            // open corners, the aprons 5 cm in from an open side between the legs (2026-10-13: the outline was the full cell)
            for (int m = 0; m < 16; m++) {
                boolean n = (m & 1) != 0, e = (m & 2) != 0, s = (m & 4) != 0, w = (m & 8) != 0;
                double t = 0.32, a = 0.64, b = 1.6, a0 = 0.8, a1 = 1.152, ya = 10, y0 = 11.6;
                VoxelShape v = Block.box(w ? 0 : t, y0, n ? 0 : t, e ? 16 : 16 - t, 12.16, s ? 16 : 16 - t);
                if (!n && !w) v = Shapes.or(v, Block.box(a, 0, a, b, y0, b));
                if (!n && !e) v = Shapes.or(v, Block.box(16 - b, 0, a, 16 - a, y0, b));
                if (!s && !e) v = Shapes.or(v, Block.box(16 - b, 0, 16 - b, 16 - a, y0, 16 - a));
                if (!s && !w) v = Shapes.or(v, Block.box(a, 0, 16 - b, b, y0, 16 - a));
                double x0 = w ? 0 : b, x1 = e ? 16 : 16 - b, z0 = n ? 0 : b, z1 = s ? 16 : 16 - b;
                if (!n) v = Shapes.or(v, Block.box(x0, ya, a0, x1, y0, a1));
                if (!s) v = Shapes.or(v, Block.box(x0, ya, 16 - a1, x1, y0, 16 - a0));
                if (!w) v = Shapes.or(v, Block.box(a0, ya, z0, a1, y0, z1));
                if (!e) v = Shapes.or(v, Block.box(16 - a1, ya, z0, 16 - a0, y0, z1));
                SHAPES[m] = v.optimize();
            }
        }

        Table(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NORTH, EAST, SOUTH, WEST); }

        static int mask(BlockState s) {
            return (s.getValue(NORTH) ? 1 : 0) | (s.getValue(EAST) ? 2 : 0) | (s.getValue(SOUTH) ? 4 : 0) | (s.getValue(WEST) ? 8 : 0);
        }

        private static BooleanProperty side(Direction d) { return SIDES[(d.get2DDataValue() + 2) & 3]; }

        private BlockState joined(BlockState s, LevelAccessor level, BlockPos pos) {
            for (Direction d : Direction.Plane.HORIZONTAL) s = s.setValue(side(d), level.getBlockState(pos.relative(d)).getBlock() instanceof Table);
            return s;
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) { return joined(defaultBlockState(), context.getLevel(), context.getClickedPos()); }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            return d.getAxis().isHorizontal() ? state.setValue(side(d), neighbour.getBlock() instanceof Table) : state;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            BlockState out = state;
            for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(side(rotation.rotate(d)), state.getValue(side(d)));
            return out;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) {
            BlockState out = state;
            for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(side(mirror.mirror(d)), state.getValue(side(d)));
            return out;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[mask(state)]; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        /** The boards run along the run: along z when joined only north / south, else along x. */
        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int m = mask(state);
            boolean ns = (m & 5) != 0, ew = (m & 10) != 0;
            sink.add("furniture/table_" + (ns && !ew ? "z" : "x") + "_" + m, 0);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // ---------------------------------------------------------------------------------------------------- chair

    /** A ladder-back wooden chair (seat 46 cm); right-click sits, sneak stands up. Facing = where the sitter looks. */
    public static final class Chair extends HorizontalDirectionalBlock implements CabinPieces {
        private static final VoxelShape[] SHAPES = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++)
                SHAPES[r] = CabinBlocks.turned(r, new double[]{4.64, 0, 4.8, 11.36, 7.44, 11.52}, new double[]{4.96, 7.44, 10.56, 11.04, 15.68, 11.2});
        }

        Chair(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (player.isSecondaryUseActive() || player.isPassenger() || FurnitureSeat.occupied(level, pos)) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return FurnitureSeat.sit(level, pos, state.getValue(FACING), player) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) { sink.add("furniture/chair", CabinBlocks.turn(state.getValue(FACING))); }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // ------------------------------------------------------------------------------------------------- counters

    /**
     * A wooden base cabinet, 90 cm high and 60 cm deep, its back against a wall (facing = the doors' side): the
     * kitchen counter (a drawer over two doors, 18 slots) or the sink counter (a drop-in enamel sink and a cast-iron pitcher
     * pump, 9 slots under the sink; the pump is looks only in V1). {@code open}: the doors swung out, opened and closed by
     * the player ({@link FurnitureOpening}, Mesh Shape profile wooden_counter: door / interior).
     */
    public static final class Counter extends BaseEntityBlock implements Storage, com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock {
        private static final net.minecraft.resources.ResourceLocation SHAPE_PROFILE = FurnitureOpening.profile("wooden_counter");
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
        private static final VoxelShape[] SHAPES = new VoxelShape[4], SINK_SHAPES = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++) {
                SHAPES[r] = CabinBlocks.turned(r, new double[]{0, 0, 5.92, 16, 14.4, 16});
                // the outline also takes the pump (its handle stays out: it reaches over the next cell)
                SINK_SHAPES[r] = Shapes.or(SHAPES[r], CabinBlocks.turned(r, new double[]{13.0, 14.4, 9.1, 15.0, 19.84, 11.06}));
            }
        }

        final boolean sink;

        Counter(boolean sink, Properties p) {
            super(p);
            this.sink = sink;
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
        }

        public boolean sink() { return sink; }

        /** Slots of this cabinet: 18 (the kitchen counter) or 9 (under the sink). */
        @Override
        public int slots() { return sink ? 9 : 18; }

        @Override
        public void setOpen(Level level, BlockPos pos, BlockState state, boolean open) { level.setBlock(pos, state.setValue(OPEN, open), 3); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            int r = CabinBlocks.turn(state.getValue(FACING));
            return sink ? SINK_SHAPES[r] : SHAPES[r];
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WoodCounterBlockEntity(pos, state); }

        /** Doors closed: open them; open: the interior's screen (search or view), or close them. */
        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return FurnitureOpening.use(this, state, level, pos, pos, player);
        }

        @Override
        public @Nullable String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
            return FurnitureOpening.hintKey(level, state, pos, region);
        }

        @Override
        public net.minecraft.resources.ResourceLocation meshShapeProfile() { return SHAPE_PROFILE; }

        @Override
        public String meshShapeState(BlockState state) { return state.getValue(OPEN) ? "open" : "closed"; }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock())) {
                if (level.getBlockEntity(pos) instanceof WoodCounterBlockEntity be) {
                    be.dropContentsOnce();
                    level.updateNeighbourForOutputSignal(pos, this);
                }
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
            return level.getBlockEntity(pos) instanceof WoodCounterBlockEntity be ? com.antaurora.apofirstlight.containersearch.AflContainerSearch.revealedAnalogSignal(be) : 0;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // ------------------------------------------------------------------------------------------------- gun rack

    /**
     * A wooden wall rack (facing = away from the wall; it needs a sturdy wall behind it): two brackets with three notches
     * (slots 0..2, bottom to top: long guns, any {@link NativeGunItem} but a pistol), a shelf under them (slots 3, 4, left
     * and right as seen from the front: pistols, ammunition, any small item that is not a block). Right-click with an item
     * hangs / puts it where aimed (else the next free place of its kind), with an empty hand takes what is aimed at.
     */
    public static final class GunRack extends BaseEntityBlock implements CabinPieces {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        /** In the north frame (tools/build-wood-furniture-v1.mjs): notch levels, the gun's middle across, the shelf. */
        public static final double[] NOTCH_Y = {0.42, 0.66, 0.9}, SHELF_X = {0.7, 0.3};
        public static final double GUN_Z = 0.825, SHELF_Y = 0.11, SHELF_Z = 0.86;
        private static final VoxelShape[] OUTLINE = new VoxelShape[4], COLLISION = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++) {
                OUTLINE[r] = CabinBlocks.turned(r, new double[]{0, 0, 11.2, 16, 16, 16});
                COLLISION[r] = CabinBlocks.turned(r, new double[]{0.8, 0.64, 11.2, 15.2, 2.05, 16}, new double[]{1.36, 0.64, 12.16, 1.84, 15.52, 16},
                        new double[]{14.16, 0.64, 12.16, 14.64, 15.52, 16});
            }
        }

        GunRack(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        /** Against the first wall that holds it, from the horizontal direction nearest the player's look (a clicked wall face: that wall). */
        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            for (Direction d : context.getNearestLookingDirections()) {
                if (!d.getAxis().isHorizontal()) continue;
                BlockState s = defaultBlockState().setValue(FACING, d.getOpposite());
                if (s.canSurvive(context.getLevel(), context.getClickedPos())) return s;
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
            return d == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
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

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return OUTLINE[CabinBlocks.turn(state.getValue(FACING))]; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return COLLISION[CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WoodGunRackBlockEntity(pos, state); }

        /** A long gun: a native gun that is not a pistol. */
        public static boolean longGun(ItemStack s) {
            return s.getItem() instanceof NativeGunItem g && g.definition().weaponClass() != WeaponClass.PISTOL;
        }

        /** What the shelf takes: anything that is neither a long gun nor a block. */
        public static boolean shelfItem(ItemStack s) { return !s.isEmpty() && !longGun(s) && !(s.getItem() instanceof BlockItem); }

        /** A world point in the north frame turned to the facing (as the mesh pieces). */
        static Vec3 local(BlockState state, BlockPos pos, double x, double y, double z) {
            for (int k = 0; k < CabinBlocks.turn(state.getValue(FACING)); k++) { double t = x; x = 1 - z; z = t; }
            return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        }

        /** The slot aimed at: the shelf below 0.26 (left / right half), else the notch whose gun covers that height. */
        public static int slotAt(BlockState state, BlockPos pos, Vec3 hit) {
            double x = hit.x - pos.getX(), y = hit.y - pos.getY(), z = hit.z - pos.getZ();
            for (int k = 0; k < CabinBlocks.turn(state.getValue(FACING)); k++) { double t = z; z = 1 - x; x = t; }
            if (y < 0.26) return x >= 0.5 ? 3 : 4;
            return y < 0.51 ? 0 : y < 0.75 ? 1 : 2;
        }

        /** Where a slot's item sits (for the prompt). */
        public static Vec3 slotPoint(BlockState state, BlockPos pos, int slot) {
            return slot < 3 ? local(state, pos, 0.5, NOTCH_Y[slot] - 0.03, GUN_Z) : local(state, pos, SHELF_X[slot - 3], SHELF_Y + 0.08, SHELF_Z);
        }

        /** The slot an item in hand goes to when aimed at `aimed`: that one if it takes it and is free, else the first free of its kind; -1: none. */
        public static int target(WoodGunRackBlockEntity rack, ItemStack held, int aimed) {
            int from, to;
            if (longGun(held)) { from = 0; to = 2; } else if (shelfItem(held)) { from = 3; to = 4; } else return -1;
            if (aimed >= from && aimed <= to && rack.item(aimed).isEmpty()) return aimed;
            for (int k = from; k <= to; k++) if (rack.item(k).isEmpty()) return k;
            return -1;
        }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof WoodGunRackBlockEntity rack)) return InteractionResult.PASS;
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
            if (!longGun(held) && !shelfItem(held)) return InteractionResult.PASS;
            int slot = target(rack, held, aimed);
            if (slot < 0) return InteractionResult.CONSUME;   // full: no shot or aim at the rack
            if (!level.isClientSide) {
                rack.put(slot, held.copyAndClear());
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WoodGunRackBlockEntity rack)
                for (int k = 0; k < WoodGunRackBlockEntity.SLOTS; k++) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rack.item(k));
            super.onRemove(state, level, pos, newState, moved);
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) { sink.add("furniture/gun_rack", CabinBlocks.turn(state.getValue(FACING))); }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }
}
