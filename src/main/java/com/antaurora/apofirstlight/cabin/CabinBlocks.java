package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.LogMount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cabin Exterior V1 (docs/models/cabin_exterior_v1.md, 2026-10-12): the exterior blocks of the forest start home B
 * ("溪谷老宅", docs/worldgen/forest_start_home_b_v1_plan.md, M1). Mesh blocks ({@link CabinPieces}, drawn by
 * {@link CabinMeshModel}): the white oak log wall, three roofs, two doors, the double-hung window, two board and batten
 * walls, the porch post, railing and deck (M1.1), the stove pipe. JSON blocks: fieldstone, firewood stack, white oak
 * stairs and slab, the woven wire garden fence. Mining (AGENTS.md audit): wood on the axe without a tier and without a correct-tool
 * requirement (vanilla wood); fieldstone on any pickaxe (vanilla stone rules); the sheet metal roofs, the stove pipe and
 * the wire fence on a stone pickaxe ({@code minecraft:needs_stone_tool}, correct tool required). Each drops itself (the
 * doors from the lower half, a double slab two). Assets and data: tools/build-cabin-exterior-v1.mjs.
 * <p>Also registered here (same mesh loader, same creative tab run): the generic {@link WoodFurniture} (Wood Furniture V1,
 * docs/models/wood_furniture_v1.md, tools/build-wood-furniture-v1.mjs), on the axe without a tier like the wood above.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CabinBlocks {
    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();
    private static final String[] IDS = {"white_oak_log_wall", "green_standing_seam_roof", "galvanized_corrugated_roof", "rusty_corrugated_roof",
            "white_oak_cabin_door", "white_oak_board_door", "white_oak_double_hung_window", "brown_board_and_batten", "grey_board_and_batten",
            "white_oak_porch_post", "white_oak_porch_railing", "white_oak_porch_deck", "stove_pipe", "fieldstone", "firewood_stack", "white_oak_stairs",
            "white_oak_slab", "woven_wire_fence", "wooden_table", "wooden_chair", "wooden_kitchen_counter", "wooden_sink_counter",
            "wooden_gun_rack", "box_stove", "stove_heat_shield", "flagstone"};

    private CabinBlocks() {}

    static ResourceLocation id(String path) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path); }

    /** Quarter turns clockwise from north (the pieces are drawn facing north). */
    static int turn(Direction d) { return (d.get2DDataValue() + 2) & 3; }

    private static BlockBehaviour.Properties wood(MapColor color, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(strength, 3.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    private static BlockBehaviour.Properties metal(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(sound).requiresCorrectToolForDrops();
    }

    private static Block make(String id) {
        return switch (id) {
            // its outline and collision reach out to the log crests (not a full cube): it blocks the view and conducts
            // redstone like a full block, but never suffocates (2026-10-13): a suffocating block whose collision reaches into
            // the next cell makes that cell "suffocating" for LocalPlayer#moveTowardsClosestSpace, which pushed the player a
            // whole cell back from the wall
            case "white_oak_log_wall" -> new LogWall(wood(MapColor.WOOD, 2.0F)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> true).isRedstoneConductor((s, l, p) -> true));
            case "green_standing_seam_roof" -> new Roof("green", metal(MapColor.COLOR_GREEN, SoundType.METAL));
            case "galvanized_corrugated_roof" -> new Roof("galvanized", metal(MapColor.METAL, SoundType.METAL));
            case "rusty_corrugated_roof" -> new Roof("rust", metal(MapColor.TERRACOTTA_ORANGE, SoundType.METAL));
            case "white_oak_cabin_door" -> new CabinDoor("cabin", wood(MapColor.WOOD, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
            case "white_oak_board_door" -> new CabinDoor("board", wood(MapColor.WOOD, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
            case "white_oak_double_hung_window" -> new Window(wood(MapColor.WOOD, 1.5F).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false));
            case "brown_board_and_batten" -> new BoardAndBatten("brown", wood(MapColor.COLOR_BROWN, 2.0F));
            case "grey_board_and_batten" -> new BoardAndBatten("grey", wood(MapColor.COLOR_GRAY, 2.0F));
            // dynamic: the rail stubs' 1.5 high collision must count above the cell (no cached shape says it is large)
            case "white_oak_porch_post" -> new PorchPost(wood(MapColor.WOOD, 2.0F).noOcclusion().dynamicShape());
            case "white_oak_porch_railing" -> new PorchRailing(wood(MapColor.WOOD, 2.0F).noOcclusion());
            case "white_oak_porch_deck" -> new PorchDeck(wood(MapColor.WOOD, 2.0F).dynamicShape());   // the piers follow the neighbours
            case "stove_pipe" -> new StovePipe(metal(MapColor.COLOR_BLACK, SoundType.METAL).noOcclusion());
            case "fieldstone" -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE));
            case "firewood_stack" -> new Firewood(wood(MapColor.WOOD, 2.0F));
            case "white_oak_stairs" -> new StairBlock(() -> planks().defaultBlockState(), wood(MapColor.WOOD, 2.0F));
            case "white_oak_slab" -> new SlabBlock(wood(MapColor.WOOD, 2.0F));
            case "woven_wire_fence" -> new WovenWireFence(metal(MapColor.METAL, SoundType.CHAIN).noOcclusion());
            // Wood Furniture V1 (WoodFurniture): the generic furniture, one set for every wood
            case "wooden_table" -> new WoodFurniture.Table(wood(MapColor.WOOD, 2.5F).noOcclusion());
            case "wooden_chair" -> new WoodFurniture.Chair(wood(MapColor.WOOD, 2.0F).noOcclusion());
            case "wooden_kitchen_counter" -> new WoodFurniture.Counter(false, wood(MapColor.WOOD, 2.5F).noOcclusion());
            case "wooden_sink_counter" -> new WoodFurniture.Counter(true, wood(MapColor.WOOD, 2.5F).noOcclusion());
            case "wooden_gun_rack" -> new WoodFurniture.GunRack(wood(MapColor.WOOD, 2.0F).noOcclusion());
            // Box Stove V1 (BoxStove): the cast-iron box stove, its heat shield, the hearth's flagstone
            case "box_stove" -> new BoxStove.Stove(metal(MapColor.COLOR_BLACK, SoundType.METAL).strength(3.0F, 6.0F).noOcclusion().lightLevel(BoxStove.Stove::light));
            case "stove_heat_shield" -> new BoxStove.HeatShield(metal(MapColor.METAL, SoundType.METAL).noOcclusion());
            case "flagstone" -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE));
            default -> throw new IllegalArgumentException(id);
        };
    }

    private static Block planks() {
        Block b = ForgeRegistries.BLOCKS.getValue(id("white_oak_planks"));
        return b == null || b == Blocks.AIR ? Blocks.OAK_PLANKS : b;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            BLOCKS.clear();
            for (String id : IDS) {
                Block b = make(id);
                BLOCKS.add(b);
                helper.register(id(id), b);
            }
        });
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            ITEMS.clear();
            for (int i = 0; i < IDS.length; i++) {
                Block b = BLOCKS.get(i);
                Item item = b instanceof DoorBlock ? new DoubleHighBlockItem(b, new Item.Properties()) : new BlockItem(b, new Item.Properties());
                ITEMS.add(item);
                helper.register(id(IDS[i]), item);
            }
        });
        WoodFurniture.register(event);
        BoxStove.register(event);
    }

    /** Creative tab (AFL Building Blocks): after the wood building blocks (called from WoodBlocks#tabs with its last stack). */
    public static void tabs(BuildCreativeModeTabContentsEvent event, ItemStack after) {
        for (Item item : ITEMS) {
            ItemStack stack = new ItemStack(item);
            event.getEntries().putAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            after = stack;
        }
    }

    // ------------------------------------------------------------------------------------------------ shapes

    /** Boxes (pixels) drawn for the north side, turned r quarters clockwise seen from above: (x, z) -> (16 - z, x). */
    static VoxelShape turned(int r, double[]... boxes) {
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int k = 0; k < r; k++) {
                double nx0 = 16 - z1, nx1 = 16 - z0;
                z0 = x0; z1 = x1; x0 = nx0; x1 = nx1;
            }
            out = Shapes.or(out, Block.box(x0, b[1], z0, x1, b[4], z1));
        }
        return out.optimize();
    }

    /** The boxes mirrored across x 8 (a left-hinged door's panel from the right-hinged one). */
    static double[][] mirroredX(double[][] boxes) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i];
            out[i] = new double[]{16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5]};
        }
        return out;
    }

    private static boolean isLogWall(BlockState s) { return s.getBlock() instanceof LogWall; }

    // ---------------------------------------------------------------------------------------------- log wall

    /**
     * Three peeled logs per block, along X or along Z (the placed axis), interleaved at corners. Opaque like a full block
     * (occlusion, light, sturdy faces for fixtures, AO; not suffocation, see make); its outline and collision follow the logs (2026-10-13):
     * out to the crests on the faces they bulge from, a corner's round log ends beyond its open sides. Flags: the sides it
     * joins (bit per direction: N 1, E 2, S 4, W 8): a log wall there, or a door / window set in a wall running that way.
     * Joined both ways: a corner (both cores, round log ends pushed out on the open sides).
     */
    public static final class LogWall extends Block implements CabinPieces {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        /** The outline per joins (bits 0..3) and axis (bit 4: Z), filled on first use. */
        private static final VoxelShape[] SHAPES = new VoxelShape[32];

        LogWall(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
        }

        /**
         * The look's logs (CabinShapeData, as cabinPieces picks the pieces): a run out to its crests, a corner both runs and
         * its log ends. Not a dynamic shape: collision and outline ask the block every time, only vanilla's cached shape
         * (worked out without neighbours) is the plain run, which is right for everything it decides (light and sturdy
         * faces come from the full occlusion and support shapes below).
         */
        static VoxelShape shape(BlockState state, int f) {
            int key = f & 15 | (state.getValue(AXIS) == Direction.Axis.X ? 0 : 16);
            VoxelShape s = SHAPES[key];
            if (s == null) {
                char look = look(state, f);
                if (look == 'x') s = turned(0, CabinShapeData.LOG_X);
                else if (look == 'z') s = turned(0, CabinShapeData.LOG_Z);
                else {
                    boolean n = (f & 1) != 0, e = (f & 2) != 0, so = (f & 4) != 0, w = (f & 8) != 0;
                    int zs = !n && so ? 0 : !so && n ? 2 : 1, xs = !w && e ? 0 : !e && w ? 2 : 1;   // neg, mid, pos as the pieces
                    s = Shapes.or(turned(0, CabinShapeData.LOG_X), turned(0, CabinShapeData.LOG_Z));
                    if (!w) s = Shapes.or(s, turned(0, CabinShapeData.LOG_STUB_X[zs]));
                    if (!e) s = Shapes.or(s, turned(2, CabinShapeData.LOG_STUB_X[2 - zs]));
                    if (!n) s = Shapes.or(s, turned(0, CabinShapeData.LOG_STUB_Z[xs]));
                    if (!so) s = Shapes.or(s, turned(2, CabinShapeData.LOG_STUB_Z[2 - xs]));
                    s = s.optimize();
                }
                SHAPES[key] = s;
            }
            return s;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, joinFlags(level, pos)); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.block(); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.block(); }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return false; }

        @Override
        @SuppressWarnings("deprecation")
        public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 0.2F; }

        /** A wall (its collision is not a full cube, so vanilla would call it open ground). */
        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                    ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
        }

        static boolean joins(BlockGetter level, BlockPos pos, Direction d) {
            BlockState o = level.getBlockState(pos.relative(d));
            if (isLogWall(o)) return true;
            Direction facing = o.getBlock() instanceof Window || o.getBlock() instanceof CabinDoor ? o.getValue(HorizontalDirectionalBlock.FACING) : null;
            return facing != null && facing.getAxis() != d.getAxis();
        }

        static int joinFlags(BlockGetter level, BlockPos pos) {
            int f = 0;
            for (int i = 0; i < 4; i++) if (joins(level, pos, SIDES[i])) f |= 1 << i;
            return f;
        }

        /** 'x', 'z' or 'c' (corner) for a wall with these joins. */
        static char look(BlockState state, int f) {
            boolean ew = (f & 10) != 0, ns = (f & 5) != 0;
            return ew && ns ? 'c' : ew ? 'x' : ns ? 'z' : state.getValue(AXIS) == Direction.Axis.X ? 'x' : 'z';
        }

        /**
         * Bits 0..3: the joins (N, E, S, W); bits 4 + 4 i .. 7 + 4 i: the pad kind behind a wall fixture on side i that stands
         * out on the logs (block/LogMount, 2026-10-13; pieces log_wall/pad_<kind>, tools/build-cabin-exterior-v1.mjs PADS).
         */
        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            int f = joinFlags(level, pos);
            for (int i = 0; i < 4; i++) f |= LogMount.padToward(level, pos, SIDES[i]) << (4 + 4 * i);
            return f;
        }

        private static String flip(String s) { return s.equals("neg") ? "pos" : s.equals("pos") ? "neg" : "mid"; }

        @Override
        public void cabinPieces(BlockState state, int f, Sink sink) {
            for (int i = 0; i < 4; i++) {
                int pad = f >>> (4 + 4 * i) & 15;
                if (pad != 0) sink.add("log_wall/pad_" + pad, turn(SIDES[i]));
            }
            char look = look(state, f);
            if (look != 'c') {
                sink.add("log_wall/run_" + look, 0);
                return;
            }
            boolean n = (f & 1) != 0, e = (f & 2) != 0, s = (f & 4) != 0, w = (f & 8) != 0;
            sink.add("log_wall/core_x", 0);
            sink.add("log_wall/core_z", 0);
            String zs = !n && s ? "neg" : !s && n ? "pos" : "mid", xs = !w && e ? "neg" : !e && w ? "pos" : "mid";
            if (!w) sink.add("log_wall/stubx_" + zs, 0);
            if (!e) sink.add("log_wall/stubx_" + flip(zs), 2);
            if (!n) sink.add("log_wall/stubz_" + xs, 0);
            if (!s) sink.add("log_wall/stubz_" + flip(xs), 2);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // -------------------------------------------------------------------------------------------------- roofs

    public enum RoofShape implements StringRepresentable {
        LOWER("lower"), UPPER("upper"), RIDGE("ridge");

        private final String name;

        RoofShape(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    /**
     * A 1:2 roof (26.6 degrees) in half-block steps: a lower cell (surface 0 .. 1/2 up the slope), an upper cell (1/2 .. 1)
     * and a ridge cell (an odd-width roof's top, 1/2 .. 3/4 .. 1/2); facing is the downslope side (the eave). Light occlusion
     * and support are four steps under the surface: the cell keeps its sky light, its bottom shuts the light out of the room
     * below and holds a ceiling fixture. Outline and collision (2026-10-13) are the 20 cm slab itself in eight steps (the
     * ceiling boards underneath, the lower cell's dipping below the cell at the eave), down to the cell's bottom where the
     * roof fills down to a wall, with a stove pipe through it. Flags (from the neighbours): eave, top edge, the two rake
     * (gable) edges, the fill down to a wall below (logs along / across / corner, brown or grey boards), a stove pipe below
     * (a boot round it). The header beam under the eave is the state's {@code beam} (set by the builder).
     */
    public static final class Roof extends Block implements CabinPieces {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final EnumProperty<RoofShape> SHAPE = EnumProperty.create("shape", RoofShape.class);
        public static final BooleanProperty BEAM = BooleanProperty.create("beam");
        static final int EAVE = 1, TOP = 2, RAKE_NEG = 4, RAKE_POS = 8, PIPE = 128, INFILL_SHIFT = 4;
        /** The steel's height at the middle of a lower cell, an upper or ridge cell (block units); the roof's thickness. */
        public static final double LOWER_MID = 0.25, UPPER_MID = 0.75, THICKNESS = 0.2;
        private static final String[] INFILL = {null, "log_along", "log_across", "log_corner", "board_brown", "board_grey"};
        private static final VoxelShape[][] SHAPES = new VoxelShape[3][4];
        /** The slab outlines per shape, turn, fill (bit 0) and pipe (bit 1), filled on first use. */
        private static final VoxelShape[] SLABS = new VoxelShape[3 * 4 * 4];

        static {
            for (int r = 0; r < 4; r++) {
                int dir = r;
                for (RoofShape sh : RoofShape.values()) {
                    double[][] boxes = new double[4][];
                    for (int k = 0; k < 4; k++) {
                        double top = switch (sh) {
                            case LOWER -> 2 * (k + 1);
                            case UPPER -> 8 + 2 * (k + 1);
                            case RIDGE -> k == 0 || k == 3 ? 10 : 12;
                        };
                        boxes[k] = new double[]{0, 0, 4 * k, 16, top, 4 * k + 4};
                    }
                    SHAPES[sh.ordinal()][dir] = turned(dir, boxes);
                }
            }
        }

        final String material;

        Roof(String material, Properties p) {
            super(p);
            this.material = material;
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SHAPE, RoofShape.LOWER).setValue(BEAM, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, SHAPE, BEAM); }

        private static VoxelShape shape(BlockState s) { return SHAPES[s.getValue(SHAPE).ordinal()][turn(s.getValue(FACING))]; }

        /** Where the wire runs in this cell (Building Wiring's view): the middle of the slab at the cell's centre, block units. */
        public static double wireHeight(BlockState s) {
            return (s.getValue(SHAPE) == RoofShape.LOWER ? LOWER_MID : UPPER_MID) - THICKNESS / 2;
        }

        /**
         * The slab (CabinShapeData.ROOF), its fill down to a wall below (as cabinFlags' infill), a stove pipe below running
         * through it. Not a dynamic shape: collision and outline ask the block every time; vanilla's cached shape (no
         * neighbours) is the bare slab, which reaches below the cell at a lower cell's eave and so counts as large.
         */
        private static VoxelShape slab(BlockState s, BlockGetter level, BlockPos pos) {
            BlockState below = level.getBlockState(pos.below());
            boolean fill = isLogWall(below) || below.getBlock() instanceof BoardAndBatten;
            boolean pipe = below.getBlock() instanceof StovePipe && s.getValue(SHAPE) != RoofShape.RIDGE;
            int sh = s.getValue(SHAPE).ordinal(), r = turn(s.getValue(FACING)), key = (sh * 4 + r) * 4 + (fill ? 1 : 0) + (pipe ? 2 : 0);
            VoxelShape v = SLABS[key];
            if (v == null) {
                v = turned(r, (fill ? CabinShapeData.ROOF_FILL : CabinShapeData.ROOF)[sh]);
                if (pipe) v = Shapes.or(v, turned(0, CabinShapeData.PIPE)).optimize();
                SLABS[key] = v;
            }
            return v;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return slab(state, level, pos); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return shape(state); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return shape(state); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useShapeForLightOcclusion(BlockState state) { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        /** Placed from below the eave looking up the slope; next to a lower cell of the same slope it goes on as upper or ridge. */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction f = context.getHorizontalDirection().getOpposite();
            var level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState down = level.getBlockState(pos.relative(f)), up = level.getBlockState(pos.relative(f.getOpposite()));
            RoofShape shape = RoofShape.LOWER;
            if (same(down, f, RoofShape.LOWER)) shape = same(up, f.getOpposite(), RoofShape.LOWER) ? RoofShape.RIDGE : RoofShape.UPPER;
            return defaultBlockState().setValue(FACING, f).setValue(SHAPE, shape);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

        private static boolean same(BlockState o, Direction facing, RoofShape... shapes) {
            if (!(o.getBlock() instanceof Roof) || o.getValue(FACING) != facing) return false;
            for (RoofShape s : shapes) if (o.getValue(SHAPE) == s) return true;
            return false;
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            RoofShape sh = state.getValue(SHAPE);
            int flags = 0;
            if (sh == RoofShape.LOWER) {
                if (!same(level.getBlockState(pos.relative(f).below()), f, RoofShape.UPPER)) flags |= EAVE;
                if (!same(level.getBlockState(pos.relative(f.getOpposite())), f, RoofShape.UPPER, RoofShape.RIDGE)) flags |= TOP;
            } else if (sh == RoofShape.UPPER) {
                if (!same(level.getBlockState(pos.relative(f)), f, RoofShape.LOWER)) flags |= EAVE;
                if (!same(level.getBlockState(pos.relative(f.getOpposite()).above()), f, RoofShape.LOWER)) flags |= TOP;
            }
            Direction left = f.getCounterClockWise(), right = f.getClockWise();
            if (!same(level.getBlockState(pos.relative(left)), f, sh)) flags |= RAKE_NEG;
            if (!same(level.getBlockState(pos.relative(right)), f, sh)) flags |= RAKE_POS;
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            int infill = 0;
            if (isLogWall(below)) {
                char look = LogWall.look(below, LogWall.joinFlags(level, belowPos));
                char along = f.getAxis() == Direction.Axis.Z ? 'z' : 'x';
                infill = look == 'c' ? 3 : look == along ? 1 : 2;
            } else if (below.getBlock() instanceof BoardAndBatten bb) infill = bb.colour.equals("brown") ? 4 : 5;
            flags |= infill << INFILL_SHIFT;
            if (below.getBlock() instanceof StovePipe) flags |= PIPE;
            return flags;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String sh = state.getValue(SHAPE).getSerializedName(), m = material;
            sink.add("roof/" + m + "_" + sh, r);
            sink.add("roof/under_" + sh, r);
            boolean eave = (flags & EAVE) != 0, rn = (flags & RAKE_NEG) != 0, rp = (flags & RAKE_POS) != 0;
            if (eave) sink.add("roof/" + m + "_eave_" + sh, r);
            if ((flags & TOP) != 0) sink.add("roof/" + m + "_top_" + sh, r);
            if (rn) sink.add("roof/" + m + "_rake_neg_" + sh, r);
            if (rp) sink.add("roof/" + m + "_rake_pos_" + sh, r);
            if (eave && rn) sink.add("roof/" + m + "_eave_corner_neg_" + sh, r);
            if (eave && rp) sink.add("roof/" + m + "_eave_corner_pos_" + sh, r);
            int infill = flags >>> INFILL_SHIFT & 7;
            if (infill > 0 && infill < INFILL.length) sink.add("roof/infill_" + INFILL[infill] + "_" + sh, r);
            boolean ridge = state.getValue(SHAPE) == RoofShape.RIDGE;
            if (state.getValue(BEAM) && !ridge) sink.add("roof/beam_" + sh, r);
            if ((flags & PIPE) != 0 && !ridge) sink.add("roof/" + m + "_pipe_" + sh, r);
        }
    }

    // -------------------------------------------------------------------------------------------------- doors

    /**
     * Two-tall white oak doors (vanilla door behaviour, oak sounds) in a lined, cased opening. Outline and collision
     * (2026-10-13): the frame (linings, threshold, the casings on both faces) and the panel where it is drawn, closed or
     * swung open against its hinge jamb, instead of vanilla's 3 px slab.
     */
    public static final class CabinDoor extends DoorBlock implements CabinPieces {
        /** Per turn, half (upper 1), hinge (right 1), open (1): bits 3..2, 2, 1, 0. */
        private static final VoxelShape[] SHAPES = new VoxelShape[32];

        static {
            for (int r = 0; r < 4; r++) for (int half = 0; half < 2; half++) for (int right = 0; right < 2; right++) for (int open = 0; open < 2; open++) {
                double[][] panel = (open == 1 ? CabinShapeData.DOOR_PANEL_OPEN : CabinShapeData.DOOR_PANEL_CLOSED)[half];
                SHAPES[r << 3 | half << 2 | right << 1 | open] = Shapes.or(turned(r, CabinShapeData.DOOR_FRAME[half]),
                        turned(r, right == 1 ? panel : mirroredX(panel))).optimize();
            }
        }

        final String type;

        CabinDoor(String type, Properties p) {
            super(p, BlockSetType.OAK);
            this.type = type;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[turn(state.getValue(FACING)) << 3 | (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 4 : 0)
                    | (state.getValue(HINGE) == DoorHingeSide.RIGHT ? 2 : 0) | (state.getValue(OPEN) ? 1 : 0)];
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String half = state.getValue(HALF).getSerializedName();
            sink.add("door/" + type + "_frame_" + half, r);
            sink.add("door/" + type + "_panel_" + half + "_" + state.getValue(HINGE).getSerializedName() + "_" + (state.getValue(OPEN) ? "open" : "closed"), r);
        }
    }

    // ------------------------------------------------------------------------------------------------- window

    /**
     * A double-hung window, one block or two stacked (the same window and facing above or below: bottom and top halves of
     * one tall window). Facing is the outside. Outline and collision (2026-10-13): the frame, sill, stool and the sashes
     * (the glass stops you, the recess inside is open, as drawn); light passes (glass).
     */
    public static final class Window extends HorizontalDirectionalBlock implements CabinPieces {
        /** Per turn and part (single, bottom, top). */
        private static final VoxelShape[] SHAPES = new VoxelShape[12];

        static {
            for (int r = 0; r < 4; r++) for (int part = 0; part < 3; part++) SHAPES[r * 3 + part] = turned(r, CabinShapeData.WINDOW[part]);
        }

        Window(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        /** 0 single, 1 the bottom of a tall window (the same window above), 2 its top. */
        private static int part(int flags) { return (flags & 1) != 0 ? 1 : (flags & 2) != 0 ? 2 : 0; }

        /** Not a dynamic shape (the part follows the neighbours; collision and outline ask the block every time). */
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[turn(state.getValue(FACING)) * 3 + part(cabinFlags(state, level, pos))];
        }

        /** A wall opening (no longer a full cube, which vanilla would call open ground). */
        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

        private static boolean sameWindow(BlockState o, BlockState state) {
            return o.getBlock() == state.getBlock() && o.getValue(FACING) == state.getValue(FACING);
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            return (sameWindow(level.getBlockState(pos.above()), state) ? 1 : 0) | (sameWindow(level.getBlockState(pos.below()), state) ? 2 : 0);
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add("window/" + new String[]{"single", "bottom", "top"}[part(flags)], turn(state.getValue(FACING)));
        }
    }

    // ---------------------------------------------------------------------------------------- board and batten

    /** A full block of vertical boards; battens on each side that does not touch an opaque block. */
    public static final class BoardAndBatten extends Block implements CabinPieces {
        final String colour;

        BoardAndBatten(String colour, Properties p) {
            super(p);
            this.colour = colour;
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            int f = 0;
            for (int i = 0; i < 4; i++) {
                BlockPos at = pos.relative(Direction.from2DDataValue((i + 2) & 3));   // N, E, S, W
                if (!level.getBlockState(at).isSolidRender(level, at)) f |= 1 << i;
            }
            return f;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add("siding/" + colour + "_core", 0);
            for (int i = 0; i < 4; i++) if ((flags & 1 << i) != 0) sink.add("siding/" + colour + "_battens", i);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // -------------------------------------------------------------------------------------------------- porch

    /**
     * A chamfered 16 cm porch post; rail stubs toward neighbouring railings. Outline: the post and the stubs' rails;
     * collision: the post, the stubs 1.5 high like the railing.
     */
    public static final class PorchPost extends Block implements CabinPieces {
        private static final VoxelShape[] OUTLINES = new VoxelShape[16], COLLISIONS = new VoxelShape[16];

        static {
            for (int f = 0; f < 16; f++) {
                VoxelShape o = turned(0, CabinShapeData.POST), c = o;
                for (int i = 0; i < 4; i++) if ((f & 1 << i) != 0) {
                    o = Shapes.or(o, turned(i, CabinShapeData.POST_RAIL));
                    c = Shapes.or(c, turned(i, CabinShapeData.POST_RAIL_COLLISION));
                }
                OUTLINES[f] = o.optimize();
                COLLISIONS[f] = c.optimize();
            }
        }

        PorchPost(Properties p) { super(p); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return OUTLINES[cabinFlags(state, level, pos)]; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return COLLISIONS[cabinFlags(state, level, pos)];
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            int f = 0;
            for (int i = 0; i < 4; i++) if (level.getBlockState(pos.relative(Direction.from2DDataValue((i + 2) & 3))).getBlock() instanceof PorchRailing) f |= 1 << i;
            return f;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add("porch/post", 0);
            for (int i = 0; i < 4; i++) if ((flags & 1 << i) != 0) sink.add("porch/post_rail", i);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    /** A porch railing along X or Z: top and bottom rail, eight balusters. Collision 1.5 tall like a fence. */
    public static final class PorchRailing extends Block implements CabinPieces {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape[] OUTLINE = {turned(0, CabinShapeData.RAILING), turned(1, CabinShapeData.RAILING)};
        private static final VoxelShape[] COLLISION = {turned(0, CabinShapeData.RAILING_COLLISION), turned(1, CabinShapeData.RAILING_COLLISION)};

        PorchRailing(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                    ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
        }

        private static int i(BlockState s) { return s.getValue(AXIS) == Direction.Axis.X ? 0 : 1; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return OUTLINE[i(state)]; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return COLLISION[i(state)]; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) { sink.add("porch/railing", i(state)); }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    /**
     * M1.1 porch deck: 3 cm white oak boards along {@code axis} on two joists, at the block top (walkable like a full
     * block), open underneath; on each open side (not another deck, not an opaque block) a rim board under a nosing, at a
     * corner of two open sides the nosing's corner square; supports under the open edges (one at each open corner, one
     * under every second edge cell and under every post; M1.2: a 14 cm oak post on a 34 cm squared stone block). Support and
     * light occlusion are the boards: the top face is sturdy, the cell keeps the light from its open sides, sky light does
     * not fall through. Collision and outline: everything drawn (the boards, joists, rims, nosing, supports).
     */
    public static final class PorchDeck extends Block implements CabinPieces {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape SHAPE = Block.box(0, 15.52, 0, 16, 16, 16);
        private static final String[] SIDE = {"n", "e", "s", "w"}, CORNER = {"ne", "se", "sw", "nw"};
        /** The outline per pier mask (piers(), bits 0..7), open sides (bits 8..11) and axis (bit 12: Z); filled on first use. */
        private static final VoxelShape[] SHAPES = new VoxelShape[1 << 13];

        PorchDeck(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }

        /** The boards run across the player's view (along the edge they face). */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                    ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
        }

        /**
         * Everything the mesh pieces draw (CabinShapeData, as cabinPieces picks them): the boards and joists, on each open
         * side its rim board and nosing (and the nosing's corner), the porch supports (M1.1, user 2026-10-12: the piers had
         * no collision; 2026-10-13 the rims and joists too, the support posts end under the joists as drawn). The occlusion,
         * support and light shape stays the board alone. The block has a dynamic shape (all this depends on the neighbours),
         * so vanilla does not cache one shape per state.
         */
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            int f = cabinFlags(state, level, pos), p = piers(f);
            boolean x = state.getValue(AXIS) == Direction.Axis.X;
            int key = p | (f & 15) << 8 | (x ? 0 : 1 << 12);
            VoxelShape s = SHAPES[key];
            if (s == null) {
                s = turned(0, x ? CabinShapeData.DECK_X : CabinShapeData.DECK_Z);
                for (int i = 0; i < 4; i++) {
                    if ((f & 1 << i) != 0) {
                        s = Shapes.or(s, turned(i, CabinShapeData.DECK_RIM));
                        if ((f & 1 << (i + 1 & 3)) != 0) s = Shapes.or(s, turned(i, CabinShapeData.DECK_CORNER));
                    }
                    if ((p & 1 << i) != 0) s = Shapes.or(s, turned(i, CabinShapeData.PIER_CORNER));
                    if ((p & 16 << i) != 0) s = Shapes.or(s, turned(i, CabinShapeData.PIER_EDGE));
                }
                SHAPES[key] = s = s.optimize();
            }
            return s;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return SHAPE; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return SHAPE; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useShapeForLightOcclusion(BlockState state) { return true; }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return false; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        /**
         * Bit i (N 1, E 2, S 4, W 8): that side is open; 16: x even, 32: z even, 64: a porch post stands on the deck. An
         * edge pier stands on every second cell of an open edge (even x along a north / south edge, even z along an east /
         * west edge) and always under a post, so the piers are 1.3 to 2.3 m apart instead of a row of stilts.
         */
        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            int f = 0;
            for (int i = 0; i < 4; i++) {
                BlockPos at = pos.relative(Direction.from2DDataValue((i + 2) & 3));   // N, E, S, W
                BlockState o = level.getBlockState(at);
                if (!(o.getBlock() instanceof PorchDeck) && !o.isSolidRender(level, at)) f |= 1 << i;
            }
            if ((pos.getX() & 1) == 0) f |= 16;
            if ((pos.getZ() & 1) == 0) f |= 32;
            if (level.getBlockState(pos.above()).getBlock() instanceof PorchPost) f |= 64;
            return f;
        }

        /**
         * The piers of a deck with these flags: bit i (0..3) a corner pier at the corner after side i (turn i), bit 4 + i
         * an edge pier in the middle of side i.
         */
        static int piers(int f) {
            int p = 0;
            for (int i = 0; i < 4; i++) {
                boolean open = (f & 1 << i) != 0, next = (f & 1 << (i + 1 & 3)) != 0, prev = (f & 1 << (i + 3 & 3)) != 0;
                if (!open) continue;
                if (next) p |= 1 << i;
                boolean even = (f & ((i & 1) == 0 ? 16 : 32)) != 0;
                if (!next && !prev && (even || (f & 64) != 0)) p |= 16 << i;
            }
            return p;
        }

        @Override
        public void cabinPieces(BlockState state, int f, Sink sink) {
            String a = state.getValue(AXIS) == Direction.Axis.X ? "x" : "z";
            sink.add("porch/deck_" + a, 0);
            for (int i = 0; i < 4; i++) {
                if ((f & 1 << i) == 0) continue;
                sink.add("porch/deck_rim_" + a + "_" + SIDE[i], 0);
                if ((f & 1 << (i + 1 & 3)) != 0) sink.add("porch/deck_corner_" + a + "_" + CORNER[i], 0);
            }
            int p = piers(f);
            for (int i = 0; i < 4; i++) {
                if ((p & 1 << i) != 0) sink.add("porch/pier_corner", i);
                if ((p & 16 << i) != 0) sink.add("porch/pier_edge", i);
            }
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    // --------------------------------------------------------------------------------------------- stove pipe

    /** A 15 cm black stove pipe; a rain cap on top unless it goes on (another pipe, or a roof it passes through). */
    public static final class StovePipe extends Block implements CabinPieces {
        private static final VoxelShape SHAPE = turned(0, CabinShapeData.PIPE), CAPPED = turned(0, CabinShapeData.PIPE_CAPPED);

        StovePipe(Properties p) { super(p); }

        /** The pipe, or the pipe ending in its rain cap (the cap is wider than the pipe). */
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return goesOn(level, pos) ? SHAPE : CAPPED; }

        private static boolean goesOn(BlockGetter level, BlockPos pos) {
            BlockState up = level.getBlockState(pos.above());
            return up.getBlock() instanceof StovePipe || up.getBlock() instanceof Roof && up.getValue(Roof.SHAPE) != RoofShape.RIDGE;
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            return (goesOn(level, pos) ? 0 : 1) | BoxStove.pipePanel(level, pos) << 1;
        }

        /** Flags: bit 0 the rain cap (nothing above takes the pipe on); Box Stove V1: bits 1.. a heat shield's sheet behind it. */
        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add((flags & 1) == 0 ? "pipe/stove_pipe" : "pipe/stove_pipe_cap", 0);
            int panel = flags >> 1;
            if ((panel & 1) != 0) sink.add("shield/panel_" + (panel >> 3 & 15), panel >> 1 & 3);
        }
    }

    // ------------------------------------------------------------------------------------------- JSON blocks

    /** Split firewood stacked crosswise, axis X / Y / Z like a log; burns like one. */
    public static final class Firewood extends RotatedPillarBlock {
        Firewood(Properties p) { super(p); }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    /**
     * A woven wire garden fence: vanilla fence connections (it joins other non-wooden fences and sturdy faces), the wire
     * between, a green steel T-post ({@code post}) at every turn or end and every third block of a straight run.
     */
    public static final class WovenWireFence extends FenceBlock {
        public static final BooleanProperty POST = BooleanProperty.create("post");

        WovenWireFence(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(POST, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(POST);
        }

        private static BlockState withPost(BlockState s, BlockPos pos) {
            boolean x = s.getValue(EAST) && s.getValue(WEST), z = s.getValue(NORTH) && s.getValue(SOUTH);
            boolean straight = x && !s.getValue(NORTH) && !s.getValue(SOUTH) || z && !s.getValue(EAST) && !s.getValue(WEST);
            return s.setValue(POST, !straight || Mth.positiveModulo(pos.getX() + pos.getZ(), 3) == 0);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState s = super.getStateForPlacement(context);
            return s == null ? null : withPost(s, context.getClickedPos());
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            return withPost(super.updateShape(state, direction, neighbour, level, pos, neighbourPos), pos);
        }
    }

    @Nullable
    static Block block(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id) && i < BLOCKS.size()) return BLOCKS.get(i);
        return null;
    }
}
