package com.antaurora.apofirstlight.townhouse;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.cabin.CabinBlocks.RoofShape;
import com.antaurora.apofirstlight.cabin.CabinPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.Fluids;
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
import java.util.Locale;

import static com.antaurora.apofirstlight.cabin.CabinBlocks.id;
import static com.antaurora.apofirstlight.cabin.CabinBlocks.mirroredX;
import static com.antaurora.apofirstlight.cabin.CabinBlocks.turn;
import static com.antaurora.apofirstlight.cabin.CabinBlocks.turned;

/**
 * Town House A V1 (docs/worldgen/town_buildings_blocks_v1.md batch 1, 2026-10-21): the blocks of the small town house
 * (minimal traditional, docs/worldgen/town_buildings_concept_v1.md), built by {@link MinimalTraditionalKit}. JSON blocks:
 * five painted lap sidings and the common red brick ({@link FacedWall}: per face outside or plaster), the parged
 * foundation, the plaster partition and ceiling, the linoleum floor, the limestone coping. Mesh blocks ({@link CabinPieces},
 * the cabin's mesh loader): three asphalt shingle roofs on the cabin's 1:2 roof cells with closed valleys
 * ({@link ShingleRoof}), the double-hung and picture windows, the front, back and interior doors, the wrought iron railing,
 * the concrete stoop steps ({@link StoopSteps}; the smooth concrete block is their landing).
 * Mining (AGENTS.md audit): wood (siding, roofs, windows, doors, the linoleum floor on its boards) on the axe without a tier
 * and without a correct-tool requirement; brick, foundation, coping, stoop concrete and steps on any pickaxe with the
 * correct tool required
 * (vanilla stone rules); plaster on any pickaxe, breaks by hand too; the iron railing on a stone pickaxe
 * ({@code minecraft:needs_stone_tool}). Each drops itself (the doors and the steps from the lower part). Creative tab only (no recipes
 * before the recipe rework). Assets, data and {@link TownShapeData}: tools/build-town-house-v1.mjs.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TownHouseBlocks {
    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();
    /** The walls' materials as the roof infill pieces name them (town/infill_&lt;material&gt;_...), index in a roof's flags. */
    static final String[] WALL_MATERIALS = {"lap_white", "lap_cream", "lap_sage", "lap_blue_gray", "lap_tan", "brick_red"};
    private static final String[] IDS = {"lap_siding_white", "lap_siding_cream", "lap_siding_sage", "lap_siding_blue_gray", "lap_siding_tan",
            "asphalt_shingle_roof_charcoal", "asphalt_shingle_roof_weathered_brown", "asphalt_shingle_roof_slate",
            "steep_asphalt_shingle_roof_charcoal", "steep_asphalt_shingle_roof_weathered_brown", "steep_asphalt_shingle_roof_slate",
            "white_double_hung_window", "white_picture_window", "house_front_door", "house_back_door", "interior_panel_door",
            "wrought_iron_railing", "parged_foundation", "plaster_partition", "plaster_ceiling", "linoleum_floor", "red_brick_common",
            "limestone_coping", "smooth_concrete", "concrete_stoop_steps"};

    private TownHouseBlocks() {}

    private static BlockBehaviour.Properties wood(MapColor color, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(strength, 3.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties plaster() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.0F, 3.0F).sound(SoundType.STONE);
    }

    private static Block make(String id) {
        return switch (id) {
            case "lap_siding_white" -> new FacedWall(0, true, wood(MapColor.SNOW, 2.0F));
            case "lap_siding_cream" -> new FacedWall(1, true, wood(MapColor.SAND, 2.0F));
            case "lap_siding_sage" -> new FacedWall(2, true, wood(MapColor.COLOR_LIGHT_GREEN, 2.0F));
            case "lap_siding_blue_gray" -> new FacedWall(3, true, wood(MapColor.COLOR_LIGHT_BLUE, 2.0F));
            case "lap_siding_tan" -> new FacedWall(4, true, wood(MapColor.TERRACOTTA_YELLOW, 2.0F));
            // dynamic and solid like the cabin roofs (CabinBlocks#make): no state cache, rain and snow stop on it
            case "asphalt_shingle_roof_charcoal" -> new ShingleRoof("charcoal", wood(MapColor.COLOR_GRAY, 1.5F).dynamicShape().forceSolidOn());
            case "asphalt_shingle_roof_weathered_brown" -> new ShingleRoof("weathered_brown", wood(MapColor.COLOR_BROWN, 1.5F).dynamicShape().forceSolidOn());
            case "asphalt_shingle_roof_slate" -> new ShingleRoof("slate", wood(MapColor.COLOR_LIGHT_GRAY, 1.5F).dynamicShape().forceSolidOn());
            case "steep_asphalt_shingle_roof_charcoal" -> new SteepShingleRoof("charcoal", wood(MapColor.COLOR_GRAY, 1.5F).dynamicShape().forceSolidOn());
            case "steep_asphalt_shingle_roof_weathered_brown" -> new SteepShingleRoof("weathered_brown", wood(MapColor.COLOR_BROWN, 1.5F).dynamicShape().forceSolidOn());
            case "steep_asphalt_shingle_roof_slate" -> new SteepShingleRoof("slate", wood(MapColor.COLOR_LIGHT_GRAY, 1.5F).dynamicShape().forceSolidOn());
            case "white_double_hung_window" -> new HouseWindow(wood(MapColor.SNOW, 1.5F).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false));
            case "white_picture_window" -> new PictureWindow(wood(MapColor.SNOW, 1.5F).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false));
            case "house_front_door" -> new FrontDoor(wood(MapColor.SNOW, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
            case "house_back_door" -> new BackDoor(wood(MapColor.SNOW, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
            case "interior_panel_door" -> new HouseDoor("interior", wood(MapColor.SNOW, 2.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
            case "wrought_iron_railing" -> new IronRailing(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
            case "parged_foundation" -> new Block(stone(MapColor.STONE));
            case "plaster_partition" -> new Partition(plaster());
            case "plaster_ceiling" -> new Ceiling(plaster());
            case "linoleum_floor" -> new Floor(wood(MapColor.WOOD, 2.0F));
            case "red_brick_common" -> new FacedWall(5, false, stone(MapColor.COLOR_RED));
            case "limestone_coping" -> new Coping(stone(MapColor.SAND).noOcclusion());
            case "smooth_concrete" -> new Block(stone(MapColor.STONE));
            case "concrete_stoop_steps" -> new StoopSteps(stone(MapColor.STONE).noOcclusion().pushReaction(PushReaction.BLOCK));
            default -> throw new IllegalArgumentException(id);
        };
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
    }

    /** Creative tab (AFL Building Blocks): after the cabin blocks (called from WoodBlocks#tabs). */
    public static void tabs(BuildCreativeModeTabContentsEvent event, ItemStack after) {
        for (Item item : ITEMS) {
            ItemStack stack = new ItemStack(item);
            event.getEntries().putAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            after = stack;
        }
    }

    /** The registered block (after registration), for the building kits. */
    public static Block block(String id) {
        int i = List.of(IDS).indexOf(id);
        if (i < 0 || i >= BLOCKS.size()) throw new IllegalArgumentException(id);
        return BLOCKS.get(i);
    }

    // ------------------------------------------------------------------------------------------- faced walls

    /**
     * A full wall block with an outside and an inside: per horizontal face a boolean (true: the outside's painted lap
     * siding or brick; false: the inside's plaster); top and bottom plaster. The building kit sets the faces; placed by a
     * player all four are outside. Lap siding gets a white corner board where two outside faces meet (the model's
     * multipart). Under a shingle roof cell it fills the gable as itself ({@link ShingleRoof}).
     */
    public static final class FacedWall extends Block {
        public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST,
                SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
        /** Index in WALL_MATERIALS. */
        final int material;
        final boolean flammable;

        FacedWall(int material, boolean flammable, Properties p) {
            super(p);
            this.material = material;
            this.flammable = flammable;
            registerDefaultState(defaultBlockState().setValue(NORTH, true).setValue(EAST, true).setValue(SOUTH, true).setValue(WEST, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NORTH, EAST, SOUTH, WEST); }

        public static boolean outside(BlockState s, Direction d) { return s.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d)); }

        public static BlockState withOutside(BlockState s, Direction d, boolean out) { return s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), out); }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            BlockState out = state;
            for (Direction d : Direction.Plane.HORIZONTAL) out = withOutside(out, rotation.rotate(d), outside(state, d));
            return out;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) {
            BlockState out = state;
            for (Direction d : Direction.Plane.HORIZONTAL) out = withOutside(out, mirror.mirror(d), outside(state, d));
            return out;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return flammable ? 20 : 0; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return flammable ? 5 : 0; }
    }

    /** A wall a roof cell above fills as: the faced wall itself, or for a window or outer door's top the faced wall beside it. */
    static @org.jetbrains.annotations.Nullable BlockState wallFor(BlockGetter level, BlockPos at, BlockState below) {
        if (below.getBlock() instanceof FacedWall) return below;
        if (!isOpening(below)) return null;
        Direction run = below.getValue(HorizontalDirectionalBlock.FACING).getClockWise();
        for (Direction d : new Direction[]{run, run.getOpposite()}) {
            BlockState n = level.getBlockState(at.relative(d));
            if (n.getBlock() instanceof FacedWall) return n;
        }
        return null;
    }

    private static boolean isOpening(BlockState s) {
        return s.getBlock() instanceof HouseWindow || s.getBlock() instanceof PictureWindow
                || s.getBlock() instanceof HouseDoor d && !d.kind.equals("interior") && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    /** Fills down to it (no further reads: a neighbour roof cell's look may only read within one block). */
    private static boolean fills(BlockState below) { return below.getBlock() instanceof FacedWall || isOpening(below); }

    // ---------------------------------------------------------------------------------------- shingle roofs

    public enum Valley implements StringRepresentable {
        NONE("none"), NEG("neg"), POS("pos"), RIDGE("ridge");

        private final String name;

        Valley(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    /**
     * Three-tab asphalt shingles on the cabin's 1:2 roof cells ({@link CabinBlocks.Roof}: facing, lower / upper / ridge,
     * beam): white fascia and drip edge at the eave, white barge boards at the rakes, a cap on the ridge, step flashing
     * where the roof runs into a wall, a boot round a stove pipe. VALLEY (the building kit's, a player's roofs stay none):
     * a cell where a front gable's slope meets the main roof (facing and shape are the main roof's): neg, the gable's slope
     * facing the main roof's facing counter-clockwise (its eave on the cell's left seen from the main eave), pos clockwise,
     * ridge the cell where the gable's ridge runs into the main roof (an upper cell). A valley cell counts as the main roof
     * for the main roof's edges, as the gable's slope (ridge: the gable's ridge) for the gable's; it has no edges itself.
     * Under a faced wall (or a window / outer door top in one) the cell fills down as that wall: the wall's outside
     * material on its outside faces, plaster inside, on every side where the next cell does not fill on.
     */
    public static final class ShingleRoof extends CabinBlocks.Roof {
        public static final EnumProperty<Valley> VALLEY = EnumProperty.create("valley", Valley.class);
        static final int EAVE = 1, TOP = 2, RAKE_NEG = 4, RAKE_POS = 8, PIPE = 128, INFILL_SHIFT = 4, INFILL_WALL = 6,
                SIDES_SHIFT = 8, MATERIAL_SHIFT = 12, OUTSIDE_SHIFT = 16;
        /** Per valley, shape, turn, fill (bit 0) and pipe (bit 1), filled on first use. */
        private static final VoxelShape[] SLABS = new VoxelShape[4 * 3 * 4 * 4];
        final String colour;

        ShingleRoof(String colour, Properties p) {
            super("shingle_" + colour, p);
            this.colour = colour;
            registerDefaultState(defaultBlockState().setValue(VALLEY, Valley.NONE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(VALLEY);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) {
            BlockState s = super.mirror(state, mirror);
            Valley v = s.getValue(VALLEY);
            // a mirror swaps the cell's left and right seen from the eave
            if (mirror == Mirror.NONE || v == Valley.NONE || v == Valley.RIDGE) return s;
            return s.setValue(VALLEY, v == Valley.NEG ? Valley.POS : Valley.NEG);
        }

        /** The slab (filled down to a wall below), a pipe through it; a valley: the main roof's and the gable's slabs. */
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext context) {
            Valley v = s.getValue(VALLEY);
            RoofShape sh = s.getValue(SHAPE);
            int r = turn(s.getValue(FACING));
            BlockState below = level.getBlockState(pos.below());
            boolean fill = v == Valley.NONE && fills(below), pipe = v == Valley.NONE && sh != RoofShape.RIDGE && below.getBlock() instanceof CabinBlocks.StovePipe;
            int key = ((v.ordinal() * 3 + sh.ordinal()) * 4 + r) * 4 + (fill ? 1 : 0) + (pipe ? 2 : 0);
            VoxelShape out = SLABS[key];
            if (out == null) {
                out = switch (v) {
                    case NONE -> slabShape(sh, r, fill, pipe);
                    case NEG -> Shapes.or(slabShape(sh, r, false, false), slabShape(sh, (r + 3) & 3, false, false)).optimize();
                    case POS -> Shapes.or(slabShape(sh, r, false, false), slabShape(sh, (r + 1) & 3, false, false)).optimize();
                    case RIDGE -> Shapes.or(slabShape(RoofShape.UPPER, r, false, false), slabShape(RoofShape.RIDGE, (r + 1) & 3, false, false)).optimize();
                };
                SLABS[key] = out;
            }
            return out;
        }

        /** Whether o continues this slope (facing, one of shapes) for an edge test: a valley counts as both slopes it joins. */
        private static boolean same(BlockState o, Direction facing, RoofShape... shapes) {
            if (!(o.getBlock() instanceof CabinBlocks.Roof)) return false;
            Direction of = o.getValue(FACING);
            RoofShape os = o.getValue(SHAPE);
            Valley v = o.getBlock() instanceof ShingleRoof ? o.getValue(VALLEY) : Valley.NONE;
            for (RoofShape s : shapes) {
                if (v == Valley.RIDGE) {
                    if (of == facing && s == RoofShape.UPPER || s == RoofShape.RIDGE && (facing == of.getCounterClockWise() || facing == of.getClockWise())) return true;
                } else if (os == s && (of == facing || s == RoofShape.RIDGE && of == facing.getOpposite()   // a ridge tops both slopes
                        || v == Valley.NEG && facing == of.getCounterClockWise() || v == Valley.POS && facing == of.getClockWise())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            if (state.getValue(VALLEY) != Valley.NONE) return 0;
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
            if (!same(level.getBlockState(pos.relative(f.getCounterClockWise())), f, sh)) flags |= RAKE_NEG;
            if (!same(level.getBlockState(pos.relative(f.getClockWise())), f, sh)) flags |= RAKE_POS;
            flags |= gableInfill(level, pos);
            if (level.getBlockState(pos.below()).getBlock() instanceof CabinBlocks.StovePipe) flags |= PIPE;
            return flags;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            RoofShape shape = state.getValue(SHAPE);
            String sh = shape.getSerializedName(), m = "shingle_" + colour;
            Valley v = state.getValue(VALLEY);
            if (v == Valley.RIDGE) {
                sink.add("roof/" + m + "_valley_ridge", r);
                sink.add("roof/under_valley_ridge", r);
                return;
            }
            if (v != Valley.NONE) {
                sink.add("roof/" + m + "_valley_" + v.getSerializedName() + "_" + sh, r);
                sink.add("roof/under_valley_" + v.getSerializedName() + "_" + sh, r);
                return;
            }
            sink.add("roof/" + m + "_" + sh, r);
            sink.add("roof/under_" + sh, r);
            boolean eave = (flags & EAVE) != 0, rn = (flags & RAKE_NEG) != 0, rp = (flags & RAKE_POS) != 0;
            if (eave) sink.add("roof/" + m + "_eave_" + sh, r);
            if ((flags & TOP) != 0) sink.add("roof/" + m + "_top_" + sh, r);
            if (rn) sink.add("roof/" + m + "_rake_neg_" + sh, r);
            if (rp) sink.add("roof/" + m + "_rake_pos_" + sh, r);
            if (eave && rn) sink.add("roof/" + m + "_eave_corner_neg_" + sh, r);
            if (eave && rp) sink.add("roof/" + m + "_eave_corner_pos_" + sh, r);
            addInfill(flags, r, sh, sink);
            if (state.getValue(BEAM) && shape != RoofShape.RIDGE) sink.add("roof/beam_" + sh, r);
            if ((flags & PIPE) != 0 && shape != RoofShape.RIDGE) sink.add("roof/" + m + "_pipe_" + sh, r);
        }
    }

    // ------------------------------------------------------------------------------------------------ steep roof

    /**
     * Town House B's 1:1 asphalt shingle roof (2026-10-21): one cell shape rising a block per block (facing = the eave,
     * downhill), the 1:2 roof's edges (white fascia and drip edge at the eave, barge boards at the rakes, step flashing
     * against a wall above), the ridge on the cell edge between two cells facing apart, each with its half of the cap.
     * Under a faced wall it fills the gable as {@link ShingleRoof} does. Light and occlusion as the cabin's roofs (a
     * dynamic shape: steps for light, nothing for chunk meshing).
     */
    public static final class SteepShingleRoof extends HorizontalDirectionalBlock implements CabinPieces {
        static final int EAVE = 1, TOP = 2, RAKE_NEG = 4, RAKE_POS = 8, RIDGE = 128;
        /** Per turn: the slab bare / filled down to a wall below (bit 0); the four steps for light. */
        private static final VoxelShape[] SLABS = new VoxelShape[4 * 2], STEPS = new VoxelShape[4];

        static {
            double[][] steps = new double[4][];
            for (int k = 0; k < 4; k++) steps[k] = new double[]{0, 0, 4 * k, 16, 4 * k + 2, 4 * k + 4};
            for (int r = 0; r < 4; r++) {
                for (int fill = 0; fill < 2; fill++) SLABS[r * 2 + fill] = turned(r, TownShapeData.STEEP_ROOF[fill]);
                STEPS[r] = turned(r, steps);
            }
        }

        final String colour;

        SteepShingleRoof(String colour, Properties p) {
            super(p);
            this.colour = colour;
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        /** Placed from below the eave looking up the slope. */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }

        private static boolean same(BlockState o, Direction facing) { return o.getBlock() instanceof SteepShingleRoof && o.getValue(FACING) == facing; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SLABS[turn(state.getValue(FACING)) * 2 + (fills(level.getBlockState(pos.below())) ? 1 : 0)];
        }

        /** The steps for light only; nothing for chunk meshing (a render region), as {@link CabinBlocks.Roof}. */
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return level instanceof BlockAndTintGetter && !(level instanceof LevelReader) ? Shapes.empty() : STEPS[turn(state.getValue(FACING))];
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return STEPS[turn(state.getValue(FACING))]; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useShapeForLightOcclusion(BlockState state) { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        /** Eave 1 (no cell of the slope below), top 2 (none above, no ridge), rakes 4 / 8, ridge 128, gable infill. */
        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            int flags = 0;
            if (!same(level.getBlockState(pos.relative(f).below()), f)) flags |= EAVE;
            if (same(level.getBlockState(pos.relative(f.getOpposite())), f.getOpposite())) flags |= RIDGE;
            else if (!same(level.getBlockState(pos.relative(f.getOpposite()).above()), f)) flags |= TOP;
            if (!same(level.getBlockState(pos.relative(f.getCounterClockWise())), f)) flags |= RAKE_NEG;
            if (!same(level.getBlockState(pos.relative(f.getClockWise())), f)) flags |= RAKE_POS;
            return flags | gableInfill(level, pos);
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String m = "roof/shingle_" + colour;
            sink.add(m + "_steep", r);
            sink.add("roof/under_steep", r);
            boolean eave = (flags & EAVE) != 0, rn = (flags & RAKE_NEG) != 0, rp = (flags & RAKE_POS) != 0, ridge = (flags & RIDGE) != 0;
            if (eave) sink.add(m + "_eave_steep", r);
            if ((flags & TOP) != 0) sink.add(m + "_top_steep", r);
            if (rn) sink.add(m + "_rake_neg_steep", r);
            if (rp) sink.add(m + "_rake_pos_steep", r);
            if (eave && rn) sink.add(m + "_eave_corner_neg_steep", r);
            if (eave && rp) sink.add(m + "_eave_corner_pos_steep", r);
            if (ridge) sink.add(m + "_steep_cap", r);
            if (ridge && rn) sink.add(m + "_steep_cap_rake_neg", r);
            if (ridge && rp) sink.add(m + "_steep_cap_rake_pos", r);
            addInfill(flags, r, "steep", sink);
        }
    }

    /**
     * A roof cell's gable infill flags (ShingleRoof.INFILL_*): under a faced wall (or a window / outer door top in one),
     * the wall's material, its outside faces and the sides where no roof cell filling the same way stands beside it.
     */
    static int gableInfill(BlockGetter level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState wall = wallFor(level, belowPos, level.getBlockState(belowPos));
        if (wall == null) return 0;
        int sides = 0, outside = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int w = turn(d);
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            boolean roof = ns.getBlock() instanceof ShingleRoof && ns.getValue(ShingleRoof.VALLEY) == Valley.NONE || ns.getBlock() instanceof SteepShingleRoof;
            if (!(roof && fills(level.getBlockState(n.below())))) sides |= 1 << w;
            if (FacedWall.outside(wall, d)) outside |= 1 << w;
        }
        return ShingleRoof.INFILL_WALL << ShingleRoof.INFILL_SHIFT | sides << ShingleRoof.SIDES_SHIFT
                | ((FacedWall) wall.getBlock()).material << ShingleRoof.MATERIAL_SHIFT | outside << ShingleRoof.OUTSIDE_SHIFT;
    }

    private static final String[] INFILL_SIDE = {"n", "e", "s", "w"};

    /** The infill pieces of a roof cell's flags (the side n of a lower or steep cell lies above its boards). */
    static void addInfill(int flags, int r, String sh, CabinPieces.Sink sink) {
        if ((flags >>> ShingleRoof.INFILL_SHIFT & 7) != ShingleRoof.INFILL_WALL) return;
        String wall = WALL_MATERIALS[flags >>> ShingleRoof.MATERIAL_SHIFT & 15];
        for (int w = 0; w < 4; w++) {
            if ((flags >>> ShingleRoof.SIDES_SHIFT & 1 << w) == 0) continue;
            String side = INFILL_SIDE[(w - r) & 3];
            if ((sh.equals("lower") || sh.equals("steep")) && side.equals("n")) continue;
            sink.add("town/infill_" + ((flags >>> ShingleRoof.OUTSIDE_SHIFT & 1 << w) != 0 ? wall : "plaster") + "_" + sh + "_" + side, r);
        }
    }

    // ------------------------------------------------------------------------------------------------ windows

    public enum Lites implements StringRepresentable {
        ONE_OVER_ONE, SIX_OVER_SIX;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /** A double-hung window's louvred shutters (Town House B, 2026-10-21): none or a pair painted as the front doors. */
    public enum Shutters implements StringRepresentable {
        NONE, WHITE, GREEN, WINE, BLACK;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * The white double-hung window (1-over-1 or 6-over-6 lights), one block or two stacked (the same window and facing
     * above or below: one tall window's bottom and top). Facing is the outside. Under an eave (2026-10-21: a shingle roof
     * cell of the same facing right above the window's top) the outside head casing and drip cap are left out, the side
     * casings stop under the starter course. SHUTTERS: a pair of louvred shutters on the wall beside the casings, the
     * height of the whole window (shorter under an eave). Outline and collision: the frame, sill, stool, apron and the
     * sashes (not the shutters, flat on the wall); light passes (glass).
     */
    public static final class HouseWindow extends HorizontalDirectionalBlock implements CabinPieces {
        public static final EnumProperty<Lites> LITES = EnumProperty.create("lites", Lites.class);
        public static final EnumProperty<Shutters> SHUTTERS = EnumProperty.create("shutters", Shutters.class);
        static final int EAVE = 4;
        private static final VoxelShape[] SHAPES = new VoxelShape[12];

        static {
            for (int r = 0; r < 4; r++) for (int part = 0; part < 3; part++) SHAPES[r * 3 + part] = turned(r, TownShapeData.HOUSE_WINDOW[part]);
        }

        HouseWindow(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LITES, Lites.ONE_OVER_ONE).setValue(SHUTTERS, Shutters.NONE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LITES, SHUTTERS); }

        /** 0 single, 1 the bottom of a tall window (the same window above), 2 its top. */
        private static int part(int flags) { return (flags & 1) != 0 ? 1 : (flags & 2) != 0 ? 2 : 0; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[turn(state.getValue(FACING)) * 3 + part(cabinFlags(state, level, pos))];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

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

        private static boolean same(BlockState o, BlockState state) {
            return o.getBlock() == state.getBlock() && o.getValue(FACING) == state.getValue(FACING) && o.getValue(LITES) == state.getValue(LITES);
        }

        /** The same window above 1, below 2; an eave right above the window's top 4 (a shingle roof cell of the same facing). */
        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            BlockState above = level.getBlockState(pos.above());
            int flags = (same(above, state) ? 1 : 0) | (same(level.getBlockState(pos.below()), state) ? 2 : 0);
            boolean eave = (above.getBlock() instanceof ShingleRoof || above.getBlock() instanceof SteepShingleRoof)
                    && above.getValue(FACING) == state.getValue(FACING);
            return (flags & 1) == 0 && eave ? flags | EAVE : flags;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String part = new String[]{"single", "bottom", "top"}[part(flags)], eave = (flags & EAVE) != 0 ? "_eave" : "";
            sink.add("town/window_" + state.getValue(LITES).getSerializedName() + "_" + part + eave, r);
            Shutters sh = state.getValue(SHUTTERS);
            if (sh != Shutters.NONE) sink.add("town/shutters_" + sh.getSerializedName() + "_" + part + eave, r);
        }
    }

    /**
     * The white picture window: fixed glass, a run of cells joined into one window (the same window and facing above,
     * below, left and right: no frame between them), the head, sill and jambs at its edges. Facing is the outside. Flags:
     * the same window above 1, below 2, on the left seen from outside (facing counter-clockwise) 4, on the right 8.
     */
    public static final class PictureWindow extends HorizontalDirectionalBlock implements CabinPieces {
        private static final VoxelShape[] SHAPES = new VoxelShape[4 * 16];
        private static final String[] ENDS = {"both", "pos", "neg", "mid"};

        static {
            double[][] jambPos = mirroredX(TownShapeData.PICTURE_JAMB);
            for (int r = 0; r < 4; r++) for (int f = 0; f < 16; f++) {
                VoxelShape v = turned(r, TownShapeData.PICTURE_CORE);
                if ((f & 1) == 0) v = Shapes.or(v, turned(r, TownShapeData.PICTURE_HEAD));
                if ((f & 2) == 0) v = Shapes.or(v, turned(r, TownShapeData.PICTURE_SILL));
                if ((f & 4) == 0) v = Shapes.or(v, turned(r, TownShapeData.PICTURE_JAMB));
                if ((f & 8) == 0) v = Shapes.or(v, turned(r, jambPos));
                SHAPES[r * 16 + f] = v.optimize();
            }
        }

        PictureWindow(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[turn(state.getValue(FACING)) * 16 + cabinFlags(state, level, pos)];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

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

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            int flags = 0, bit = 1;
            for (BlockPos n : new BlockPos[]{pos.above(), pos.below(), pos.relative(f.getCounterClockWise()), pos.relative(f.getClockWise())}) {
                BlockState o = level.getBlockState(n);
                if (o.getBlock() == this && o.getValue(FACING) == f) flags |= bit;
                bit <<= 1;
            }
            return flags;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String ends = ENDS[flags >> 2 & 3];
            sink.add("town/picture_glass", r);
            if ((flags & 1) == 0) sink.add("town/picture_head_" + ends, r);
            if ((flags & 2) == 0) sink.add("town/picture_sill_" + ends, r);
            if ((flags & 4) == 0) sink.add("town/picture_jamb_neg", r);
            if ((flags & 8) == 0) sink.add("town/picture_jamb_pos", r);
        }
    }

    // -------------------------------------------------------------------------------------------------- doors

    public enum Paint implements StringRepresentable {
        WHITE, GREEN, WINE, BLACK;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * The house doors (vanilla door behaviour, oak sounds) in a lined, cased opening, drawn like the cabin's doors: facing
     * north draws the side the player stood on at the south (an outer door's outside), the leaf closed at that face and
     * swung a quarter into the opening against the hinge jamb. The interior door's frame is centred in the cell (in a
     * partition), its leaf swings toward the side it was placed from. Outline and collision: the frame and the leaf where
     * it is drawn (the back door's screen door while it is shut).
     */
    public static class HouseDoor extends DoorBlock implements CabinPieces {
        final String kind;
        /** Per turn, half, hinge, open and the screen door: bits 4..3, 2, 1, 0, 5. */
        private final VoxelShape[] shapes = new VoxelShape[64];

        HouseDoor(String kind, Properties p) {
            super(p, BlockSetType.OAK);
            this.kind = kind;
        }

        boolean screen(BlockState state) { return false; }

        String leaf(BlockState state) { return kind; }

        /** The frame piece of a half (lower / upper). */
        String frame(BlockState state, String half) { return kind.equals("interior") ? "town/door_interior_frame_" + half : "town/door_frame_" + half; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            int r = turn(state.getValue(FACING)), half = state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : 0;
            int right = state.getValue(HINGE) == DoorHingeSide.RIGHT ? 1 : 0, open = state.getValue(OPEN) ? 1 : 0, screen = screen(state) && open == 0 ? 1 : 0;
            int key = screen << 5 | r << 3 | half << 2 | right << 1 | open;
            VoxelShape v = shapes[key];
            if (v == null) {
                boolean interior = kind.equals("interior");
                double[][] panel = (interior ? open == 1 ? TownShapeData.INTERIOR_PANEL_OPEN : TownShapeData.INTERIOR_PANEL_CLOSED
                        : open == 1 ? TownShapeData.DOOR_PANEL_OPEN : TownShapeData.DOOR_PANEL_CLOSED)[half];
                v = Shapes.or(turned(r, (interior ? TownShapeData.INTERIOR_FRAME : TownShapeData.DOOR_FRAME)[half]), turned(r, right == 1 ? panel : mirroredX(panel)));
                if (screen == 1) v = Shapes.or(v, turned(r, TownShapeData.SCREEN_CLOSED[half]));
                shapes[key] = v = v.optimize();
            }
            return v;
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String half = state.getValue(HALF).getSerializedName(), tail = "_" + half + "_" + state.getValue(HINGE).getSerializedName() + "_" + (state.getValue(OPEN) ? "open" : "closed");
            sink.add(frame(state, half), r);
            sink.add("town/door_" + leaf(state) + tail, r);
            if (screen(state)) sink.add("town/door_screen" + tail, r);
        }
    }

    /**
     * The front door: three small lights over four raised panels, painted (white, green, wine red, black), a brass knob.
     * SURROUND (Town House B, 2026-10-21): a white Colonial Revival surround outside the casing, pilasters on plinths,
     * capitals, a frieze, a cornice and a shallow pediment above the opening (on the upper half, reaching into the cell above).
     */
    public static final class FrontDoor extends HouseDoor {
        public static final EnumProperty<Paint> PAINT = EnumProperty.create("paint", Paint.class);
        public static final BooleanProperty SURROUND = BooleanProperty.create("surround");

        FrontDoor(Properties p) {
            super("front", p);
            registerDefaultState(defaultBlockState().setValue(PAINT, Paint.WHITE).setValue(SURROUND, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(PAINT, SURROUND);
        }

        @Override
        String leaf(BlockState state) { return "front_" + state.getValue(PAINT).getSerializedName(); }

        @Override
        String frame(BlockState state, String half) { return state.getValue(SURROUND) ? "town/door_frame_surround_" + half : super.frame(state, half); }
    }

    /** The back door: nine lights over two panels, white; with screen, a wood screen door outside it (swings out with it). */
    public static final class BackDoor extends HouseDoor {
        public static final BooleanProperty SCREEN = BooleanProperty.create("screen");

        BackDoor(Properties p) {
            super("back", p);
            registerDefaultState(defaultBlockState().setValue(SCREEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(SCREEN);
        }

        @Override
        boolean screen(BlockState state) { return state.getValue(SCREEN); }
    }

    // ------------------------------------------------------------------------------------------------ railing

    /**
     * Black square-bar railing along the cell's edge it faces (facing north: the north edge), a top and a bottom rail with
     * pickets between them. On a stair climbing along the railing (the stoop steps' upper cell or a vanilla bottom stair) the
     * rails follow it down; over the steps' lower cell (foot) they end at a newel post on the last tread. A post at each end
     * of a run (no railing of the same facing beside it, level, a step up or down) and on a level run where a sloped one
     * goes on from it. Collision 1.5 high along the rails.
     */
    public static final class IronRailing extends HorizontalDirectionalBlock implements CabinPieces {
        private static final String[] SHAPE = {"straight", "stair_neg", "stair_pos", "foot_neg", "foot_pos"};
        static final int POST_NEG = 8, POST_POS = 16;
        /** Outline per turn, shape and posts (neg bit 0, pos bit 1); collision per turn and shape. */
        private static final VoxelShape[] OUTLINE = new VoxelShape[4 * 5 * 4], COLLISION = new VoxelShape[4 * 5];

        static {
            for (int r = 0; r < 4; r++) for (int s = 0; s < 5; s++) {
                COLLISION[r * 5 + s] = turned(r, TownShapeData.RAILING_COLLISION[s]);
                for (int p = 0; p < 4; p++) {
                    VoxelShape v = turned(r, TownShapeData.RAILING[s]);
                    if ((p & 1) != 0) v = Shapes.or(v, turned(r, TownShapeData.RAILING_POST[s][0]));
                    if ((p & 2) != 0) v = Shapes.or(v, turned(r, TownShapeData.RAILING_POST[s][1]));
                    OUTLINE[(r * 5 + s) * 4 + p] = v.optimize();
                }
            }
        }

        IronRailing(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        /** Hugs the edge in front of the player. */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }

        private static boolean railing(BlockGetter level, BlockPos at, Direction f) {
            for (BlockPos p : new BlockPos[]{at, at.above(), at.below()}) {
                BlockState o = level.getBlockState(p);
                if (o.getBlock() instanceof IronRailing && o.getValue(FACING) == f) return true;
            }
            return false;
        }

        /**
         * The shape from the block below: 0 straight; 1 / 2 stair_neg / stair_pos (climbing toward the facing's clockwise /
         * counter-clockwise side: a bottom stair or the steps' upper cell); 3 / 4 foot_neg / foot_pos (the steps' lower cell).
         */
        static int shape(BlockGetter level, BlockPos pos, Direction f) {
            BlockState below = level.getBlockState(pos.below());
            Direction up;
            int foot = 0;
            if (below.getBlock() instanceof StairBlock && below.getValue(StairBlock.HALF) == Half.BOTTOM) up = below.getValue(StairBlock.FACING);
            else if (below.getBlock() instanceof StoopSteps) {
                up = below.getValue(StoopSteps.FACING);
                if (below.getValue(StoopSteps.PART) == StepPart.LOWER) foot = 2;
            } else return 0;
            return up == f.getClockWise() ? 1 + foot : up == f.getCounterClockWise() ? 2 + foot : 0;
        }

        /** Whether a run's end toward `at` needs a post: no railing beyond it, or (a level run) a sloped one going on from it. */
        private static boolean post(BlockGetter level, BlockPos at, Direction f, boolean straight) {
            if (!railing(level, at, f)) return true;
            BlockState o = level.getBlockState(at);
            return straight && o.getBlock() instanceof IronRailing && o.getValue(FACING) == f && shape(level, at, f) != 0;
        }

        /** Shape (bits 0..2, {@link #shape}), post neg 8, post pos 16 (a foot always has its newel post). */
        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            int shape = shape(level, pos, f), flags = shape;
            if (shape == 3 || post(level, pos.relative(f.getCounterClockWise()), f, shape == 0)) flags |= POST_NEG;
            if (shape == 4 || post(level, pos.relative(f.getClockWise()), f, shape == 0)) flags |= POST_POS;
            return flags;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String s = SHAPE[Math.min(flags & 7, 4)];
            sink.add("town/railing_" + s, r);
            if ((flags & POST_NEG) != 0) sink.add("town/railing_post_neg_" + s, r);
            if ((flags & POST_POS) != 0) sink.add("town/railing_post_pos_" + s, r);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            int flags = cabinFlags(state, level, pos);
            return OUTLINE[(turn(state.getValue(FACING)) * 5 + Math.min(flags & 7, 4)) * 4 + (flags >> 3 & 3)];
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return COLLISION[turn(state.getValue(FACING)) * 5 + Math.min(shape(level, pos, state.getValue(FACING)), 4)];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
    }

    // ---------------------------------------------------------------------------------------------- stoop steps

    public enum StepPart implements StringRepresentable {
        UPPER, LOWER;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * A two-cell flight of cast concrete steps (upper, lower): six 1/6 risers from a landing one block up down to the ground,
     * five 1/3 treads, the lower cell's last third ground. facing = the high side (vanilla stairs' facing). Placed as the
     * lower cell, the upper one toward the facing; breaking either breaks both, the lower part drops the item (like a door).
     * The sides are left out beside a step of the same flight and part (a wide stoop of several). Collision: the treads.
     */
    public static final class StoopSteps extends HorizontalDirectionalBlock implements CabinPieces {
        public static final EnumProperty<StepPart> PART = EnumProperty.create("part", StepPart.class);
        private static final VoxelShape[] SHAPES = new VoxelShape[4 * 2];

        static {
            for (int r = 0; r < 4; r++) for (int p = 0; p < 2; p++) SHAPES[r * 2 + p] = turned(r, TownShapeData.STEPS[p]);
        }

        StoopSteps(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, StepPart.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }

        /** The other cell of the flight. */
        private static BlockPos partner(BlockState state, BlockPos pos) {
            Direction f = state.getValue(FACING);
            return pos.relative(state.getValue(PART) == StepPart.LOWER ? f : f.getOpposite());
        }

        /** Climbing away from the player; needs room for the upper cell. */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction f = context.getHorizontalDirection();
            BlockPos up = context.getClickedPos().relative(f);
            Level level = context.getLevel();
            if (!level.getBlockState(up).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(up)) return null;
            return defaultBlockState().setValue(FACING, f);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            level.setBlock(partner(state, pos), state.setValue(PART, StepPart.UPPER), 3);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            if (otherPos.equals(partner(state, pos)) && !(other.is(this) && other.getValue(FACING) == state.getValue(FACING) && other.getValue(PART) != state.getValue(PART)))
                return Blocks.AIR.defaultBlockState();
            return super.updateShape(state, dir, other, level, pos, otherPos);
        }

        /** In creative, breaking the upper cell must not drop the item from the lower one. */
        @Override
        public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide && player.isCreative() && state.getValue(PART) == StepPart.UPPER) {
                BlockPos low = partner(state, pos);
                BlockState o = level.getBlockState(low);
                if (o.is(this) && o.getValue(PART) == StepPart.LOWER) {
                    level.setBlock(low, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, low, Block.getId(o));
                }
            }
            super.playerWillDestroy(level, pos, state, player);
        }

        /** Sides: neg 1, pos 2 (no step of the same flight and part beside it). */
        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            int flags = 0;
            if (!same(state, level.getBlockState(pos.relative(f.getCounterClockWise())))) flags |= 1;
            if (!same(state, level.getBlockState(pos.relative(f.getClockWise())))) flags |= 2;
            return flags;
        }

        private boolean same(BlockState state, BlockState o) { return o.is(this) && o.getValue(FACING) == state.getValue(FACING) && o.getValue(PART) == state.getValue(PART); }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = turn(state.getValue(FACING));
            String part = state.getValue(PART).getSerializedName();
            sink.add("town/steps_" + part, r);
            if ((flags & 1) != 0) sink.add("town/steps_" + part + "_side_neg", r);
            if ((flags & 2) != 0) sink.add("town/steps_" + part + "_side_pos", r);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[turn(state.getValue(FACING)) * 2 + (state.getValue(PART) == StepPart.UPPER ? 0 : 1)];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useShapeForLightOcclusion(BlockState state) { return true; }

    }

    // ------------------------------------------------------------------------------------------ plaster, floor

    /**
     * A 25 cm plaster partition in the middle of the cell, joining like a pane (other partitions, interior doors running
     * its way, sturdy faces). base: it stands on a floor (white skirting at its foot; not over a partition, a door or an opening). ceiling:
     * the top storey's partition carries its own 12 cm ceiling slab (the building kit's; the partition stops under it).
     */
    public static final class Partition extends CrossCollisionBlock {
        public static final BooleanProperty CEILING = BooleanProperty.create("ceiling"), BASE = BooleanProperty.create("base");
        private static final VoxelShape SLAB = Block.box(0, 14, 0, 16, 16, 16);

        Partition(Properties p) {
            // CrossCollisionBlock takes half widths: 2 = the 4 px (6..10) the model draws
            super(2.0F, 2.0F, 16.0F, 16.0F, 16.0F, p);
            registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                    .setValue(WATERLOGGED, false).setValue(CEILING, false).setValue(BASE, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NORTH, EAST, SOUTH, WEST, WATERLOGGED, CEILING, BASE); }

        private static boolean attaches(BlockState o, BlockGetter level, BlockPos at, Direction d) {
            if (o.getBlock() instanceof Partition) return true;
            if (o.getBlock() instanceof HouseDoor door && door.kind.equals("interior")) return o.getValue(DoorBlock.FACING).getAxis() != d.getAxis();
            return !isExceptionForConnection(o) && o.isFaceSturdy(level, at, d.getOpposite());
        }

        private BlockState connected(BlockState s, BlockGetter level, BlockPos pos) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = pos.relative(d);
                s = s.setValue(PROPERTY_BY_DIRECTION.get(d), attaches(level.getBlockState(n), level, n, d));
            }
            return s.setValue(BASE, base(level.getBlockState(pos.below()), level, pos.below()));
        }

        /** Skirting at its foot: it stands on a floor (not a partition, not a door or an opening's air: a header). */
        private static boolean base(BlockState below, BlockGetter level, BlockPos at) {
            return !(below.getBlock() instanceof Partition) && below.isFaceSturdy(level, at, Direction.UP);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return connected(defaultBlockState(), context.getLevel(), context.getClickedPos())
                    .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState o, LevelAccessor level, BlockPos pos, BlockPos at) {
            if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            if (d == Direction.DOWN) return state.setValue(BASE, base(o, level, at));
            return d.getAxis().isHorizontal() ? state.setValue(PROPERTY_BY_DIRECTION.get(d), attaches(o, level, at, d)) : state;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            VoxelShape v = super.getShape(state, level, pos, context);
            return state.getValue(CEILING) ? Shapes.or(v, SLAB) : v;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            VoxelShape v = super.getCollisionShape(state, level, pos, context);
            return state.getValue(CEILING) ? Shapes.or(v, SLAB) : v;
        }
    }

    /** A 12 cm plaster ceiling at the top of the cell (the attic side: joists and insulation, ceiling_top). */
    public static final class Ceiling extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 14, 0, 16, 16, 16);

        Ceiling(Properties p) { super(p); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    }

    /** Linoleum on red oak floor boards: burns like the boards. */
    public static final class Floor extends Block {
        Floor(Properties p) { super(p); }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    /**
     * The chimney's limestone cap: 12 cm thick, 6 cm over the brick each side, along an axis; left / right: no coping beside it
     * that way along the axis (west / north, east / south), the end's overhang; flue: a clay flue liner 18 cm up (the kit's).
     */
    public static final class Coping extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right"), FLUE = BooleanProperty.create("flue");
        private static final VoxelShape BASE = Block.box(0, 0, 0, 16, 2, 16), WITH_FLUE = Shapes.or(BASE, Block.box(4.5, 2, 4.5, 11.5, 5, 11.5));

        Coping(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X).setValue(LEFT, true).setValue(RIGHT, true).setValue(FLUE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS, LEFT, RIGHT, FLUE); }

        private static Direction left(Direction.Axis a) { return a == Direction.Axis.X ? Direction.WEST : Direction.NORTH; }

        private static BlockState ends(BlockState s, BlockGetter level, BlockPos pos) {
            Direction l = left(s.getValue(AXIS));
            return s.setValue(LEFT, !(level.getBlockState(pos.relative(l)).getBlock() instanceof Coping))
                    .setValue(RIGHT, !(level.getBlockState(pos.relative(l.getOpposite())).getBlock() instanceof Coping));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return ends(defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis()), context.getLevel(), context.getClickedPos());
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState o, LevelAccessor level, BlockPos pos, BlockPos at) {
            return d.getAxis() == state.getValue(AXIS) ? ends(state, level, pos) : state;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
            BlockState s = quarter ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
            // the ends follow the turn: a quarter turn clockwise takes west (left of X) to north (left of Z), north to east (right of X)
            boolean flip = rotation == Rotation.CLOCKWISE_180 || rotation == Rotation.CLOCKWISE_90 && state.getValue(AXIS) == Direction.Axis.Z
                    || rotation == Rotation.COUNTERCLOCKWISE_90 && state.getValue(AXIS) == Direction.Axis.X;
            return flip ? s.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT)) : s;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) {
            boolean flip = mirror == Mirror.FRONT_BACK && state.getValue(AXIS) == Direction.Axis.X || mirror == Mirror.LEFT_RIGHT && state.getValue(AXIS) == Direction.Axis.Z;
            return flip ? state.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT)) : state;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(FLUE) ? WITH_FLUE : BASE; }
    }
}
