package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.registry.AflCreativeTabs;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.tree.TreeForms.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
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

/**
 * Wood Building Blocks V1 (docs/models/wood_building_blocks_v1.md, 2026-10-12): the first building blocks of the AFL
 * wood system (docs/dev/wood_system_plan_v1.md), three kinds for each of the six species, one class per kind:
 * <ul>
 * <li>{@code <species>_planks}: a full block of boards (Planks).</li>
 * <li>{@code <species>_rounded_timber}: planed square timber with eased corners, axis X / Y / Z (RoundedTimber): the
 * full block for collision, outline and support, drawn as the log's rounded-square prism (TreeLogModel) with one corner
 * radius for every species, no bark.</li>
 * <li>{@code <species>_beam}: a half-block (8 x 8 / 16) beam one block long, axis X / Y / Z, waterloggable (Beam): its
 * collision and outline are the beam itself.</li>
 * </ul>
 * No recipes, no intermediate items (lumber), no stairs / slabs / fences (later rounds). Mining (AGENTS.md block audit,
 * wood follows vanilla rules): {@code minecraft:mineable/axe}, no tier tag, no correct-tool requirement (hand breaking
 * drops them, slowly), each drops itself (loot tables). Block states are only {@code axis} (and the beam's
 * {@code waterlogged}), so structure templates rotate them like vanilla logs. Creative tab: AFL Building Blocks after
 * the steel railing, per species planks, rounded timber, beam. Assets and data: tools/build-wood-blocks-v1.mjs.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WoodBlocks {
    /** Corner radius of the rounded timber (blocks): eased edges, squarer than every species' log (2.5/16..6/16). */
    public static final float TIMBER_RADIUS = 1.5F / 16;
    private static final int N = Species.values().length;
    private static final Block[] PLANKS = new Block[N], TIMBER = new Block[N], BEAMS = new Block[N];
    private static final Item[] PLANK_ITEMS = new Item[N], TIMBER_ITEMS = new Item[N], BEAM_ITEMS = new Item[N];

    private WoodBlocks() {}

    static ResourceLocation id(Species s, String kind) { return new ResourceLocation("apocalypse_firstlight", s.id + "_" + kind); }

    /** Vanilla wood values: hardness 2 (planks: resistance 3, vanilla planks), wood sound, bass note, lava ignites it. */
    private static BlockBehaviour.Properties wood(Species s, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(TreeLogBlock.endColor(s)).instrument(NoteBlockInstrument.BASS)
                .strength(2.0F, resistance).sound(SoundType.WOOD).ignitedByLava();
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            for (Species s : Species.values()) {
                int i = s.ordinal();
                PLANKS[i] = new Planks(wood(s, 3.0F));
                TIMBER[i] = new RoundedTimber(wood(s, 2.0F));
                BEAMS[i] = new Beam(wood(s, 2.0F));
                helper.register(id(s, "planks"), PLANKS[i]);
                helper.register(id(s, "rounded_timber"), TIMBER[i]);
                helper.register(id(s, "beam"), BEAMS[i]);
            }
        });
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            for (Species s : Species.values()) {
                int i = s.ordinal();
                PLANK_ITEMS[i] = new BlockItem(PLANKS[i], new Item.Properties());
                TIMBER_ITEMS[i] = new BlockItem(TIMBER[i], new Item.Properties());
                BEAM_ITEMS[i] = new BlockItem(BEAMS[i], new Item.Properties());
                helper.register(id(s, "planks"), PLANK_ITEMS[i]);
                helper.register(id(s, "rounded_timber"), TIMBER_ITEMS[i]);
                helper.register(id(s, "beam"), BEAM_ITEMS[i]);
            }
        });
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (PLANK_ITEMS[0] == null || event.getTabKey() != AflCreativeTabs.BUILDING_BLOCKS.getKey()) return;
        ItemStack after = new ItemStack(AflItems.STEEL_RAILING.get());
        for (Species s : Species.values()) for (Item item : new Item[]{PLANK_ITEMS[s.ordinal()], TIMBER_ITEMS[s.ordinal()], BEAM_ITEMS[s.ordinal()]}) {
            ItemStack stack = new ItemStack(item);
            event.getEntries().putAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            after = stack;
        }
        CabinBlocks.tabs(event, after);   // Cabin Exterior V1 (docs/models/cabin_exterior_v1.md): after the wood blocks
    }

    static boolean ready() { return TIMBER[0] != null; }

    static Block timber(Species s) { return TIMBER[s.ordinal()]; }

    // ------------------------------------------------------------------------------------------------- blocks

    /** Boards: a plain full block, burns like vanilla planks (catches 5, burns away 20). */
    public static final class Planks extends Block {
        Planks(Properties p) { super(p); }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    /**
     * Square timber: the full block like a log (collision, outline, support, light: opacity 15), the occlusion a cross
     * without the eased corners (as TreeLogBlock: the flat middle of each side culls against a neighbour, the corner strips
     * never). Solid timber burns like a log (5 / 5).
     */
    public static final class RoundedTimber extends RotatedPillarBlock {
        private static final VoxelShape[] OCCLUSION = new VoxelShape[3];

        static {
            double r = TIMBER_RADIUS * 16, s = 16 - r;
            OCCLUSION[0] = Shapes.or(Block.box(0, r, 0, 16, s, 16), Block.box(0, 0, r, 16, 16, s));
            OCCLUSION[1] = Shapes.or(Block.box(r, 0, 0, s, 16, 16), Block.box(0, 0, r, 16, 16, s));
            OCCLUSION[2] = Shapes.or(Block.box(r, 0, 0, s, 16, 16), Block.box(0, r, 0, 16, s, 16));
        }

        RoundedTimber(Properties p) { super(p); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return OCCLUSION[state.getValue(AXIS).ordinal()];
        }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return level.getMaxLightLevel(); }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return false; }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }

    /**
     * A half-block beam (4..12 / 16 across, the whole block along its axis), axis X / Y / Z, waterloggable like a vanilla
     * chain. Collision and outline are the beam; light passes beside it; its ends cull against a neighbouring beam's end or
     * a full block. Solid timber burns like a log (5 / 5).
     */
    public static final class Beam extends RotatedPillarBlock implements SimpleWaterloggedBlock {
        public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
        private static final VoxelShape[] SHAPES = {
                Block.box(0, 4, 4, 16, 12, 12), Block.box(4, 0, 4, 12, 16, 12), Block.box(4, 4, 0, 12, 12, 16)};

        Beam(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(WATERLOGGED);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(AXIS).ordinal()];
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = super.getStateForPlacement(context);
            return state == null ? null : state.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }

        @Override
        @SuppressWarnings("deprecation")
        public FluidState getFluidState(BlockState state) {
            return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
    }
}
