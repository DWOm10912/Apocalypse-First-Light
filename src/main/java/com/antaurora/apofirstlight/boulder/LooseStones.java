package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rock R1b (docs/gameplay/rock_r1_stones_boulders_design.md 2.2-2.4, 11.3): {@code loose_stones}, a few stones lying on
 * the ground in the cell above it (an air or water cell): {@code stones} pebbles (0-4), maybe a field stone
 * ({@code cobble}) and a flint ({@code flint}), field stones or river cobbles ({@code kind}: the same items, rounder and
 * flatter in the world), a {@code turn} that varies the layout, {@code waterlogged} on a riverbed.
 * <ul>
 * <li>Drawn by LooseStonesModel: each stone where the cell's position hash puts it, sitting on the ground under it (the
 * smoothed slope, the earthwork's skin).</li>
 * <li>Use (any hand, any item but the shovel, which digs: client/ShovelClient) picks one up: the field stone first, then the
 * flint, then the pebbles one by one; the block goes with the last.</li>
 * <li>Dropped as its items when broken, when its ground goes (updateShape), and when the earthwork redraws its column
 * (Earthwork#settle).</li>
 * </ul>
 * Mining audit (design 2.2): no tool needed (anything picks it up, strength 0.3), {@code minecraft:mineable/shovel}, never
 * requires the correct tool; drops the same items however it goes. No collision, no light blocking, pistons break it.
 * Terrain Smooth and the earthwork treat it as cover, as a plant (TerrainSmooth#cell, Earthwork#open). No outline
 * (SmoothOutline): the stones are part of the ground.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LooseStones extends Block implements SimpleWaterloggedBlock {
    public static final ResourceLocation ID = new ResourceLocation("apocalypse_firstlight", "loose_stones");

    public enum Kind implements StringRepresentable {
        FIELD, RIVER;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final IntegerProperty STONES = IntegerProperty.create("stones", 0, 4);
    public static final BooleanProperty COBBLE = BooleanProperty.create("cobble");
    public static final BooleanProperty FLINT = BooleanProperty.create("flint");
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Picking the stones out: the ray finds them a little under the cell floor (a lowered slope) and above it (a raised one). */
    private static final VoxelShape PICK = Shapes.box(0.04, -0.45, 0.04, 0.96, 0.35, 0.96);

    private static LooseStones block;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            block = new LooseStones();
            helper.register(ID, block);
        });
    }

    public static boolean ready() { return block != null; }
    public static LooseStones block() { return block; }

    public static boolean is(BlockState s) {
        return block != null && s.getBlock() == block;
    }

    LooseStones() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.3F).noCollission().noOcclusion()
                .sound(SoundType.STONE).pushReaction(PushReaction.DESTROY).isValidSpawn((s, l, p, e) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
        registerDefaultState(stateDefinition.any().setValue(STONES, 2).setValue(COBBLE, false).setValue(FLINT, false)
                .setValue(KIND, Kind.FIELD).setValue(TURN, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STONES, COBBLE, FLINT, KIND, TURN, WATERLOGGED);
    }

    // ------------------------------------------------------------------------------------------- contents

    /** What the block holds, as items: the pebbles, the field stone, the flint. */
    public static List<ItemStack> contents(BlockState s) {
        var out = new ArrayList<ItemStack>(3);
        if (s.getValue(STONES) > 0) out.add(new ItemStack(AflItems.PEBBLE.get(), s.getValue(STONES)));
        if (s.getValue(COBBLE)) out.add(new ItemStack(AflItems.FIELD_STONE.get()));
        if (s.getValue(FLINT)) out.add(new ItemStack(AflItems.FLINT.get()));
        return out;
    }

    private static boolean empty(BlockState s) {
        return s.getValue(STONES) == 0 && !s.getValue(COBBLE) && !s.getValue(FLINT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return contents(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack take;
        BlockState left;
        if (state.getValue(COBBLE)) { take = new ItemStack(AflItems.FIELD_STONE.get()); left = state.setValue(COBBLE, false); }
        else if (state.getValue(FLINT)) { take = new ItemStack(AflItems.FLINT.get()); left = state.setValue(FLINT, false); }
        else if (state.getValue(STONES) > 0) { take = new ItemStack(AflItems.PEBBLE.get()); left = state.setValue(STONES, state.getValue(STONES) - 1); }
        else { level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL); return InteractionResult.CONSUME; }
        // into the grid inventory (its weight counts there), else the vanilla inventory (creative), else at the player's feet
        if (!(player instanceof ServerPlayer sp && PlayerGrids.insert(sp, take)) && !player.getInventory().add(take)) player.drop(take, false);
        level.setBlock(pos, empty(left) ? state.getFluidState().createLegacyBlock() : left, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F + level.random.nextFloat() * 0.3F);
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------------------------------------- ground

    /** Stones need ground: a sturdy top or natural ground (a smoothed slope's block counts though its top is lowered). */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState s = level.getBlockState(below);
        return s.isFaceSturdy(level, below, Direction.UP) || TerrainSmooth.cell(s) == SmoothRules.NATURAL;
    }

    /** The ground gone: air, so Block#updateOrDestroy breaks it with its drops (a riverbed's water stays: Level#destroyBlock). */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (dir == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return PICK; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return !state.getValue(WATERLOGGED); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return switch (type) {
            case LAND -> true;
            case WATER -> state.getValue(WATERLOGGED);
            case AIR -> !state.getValue(WATERLOGGED);
        };
    }

    // ------------------------------------------------------------------------------------------- scatter

    /**
     * Development (design 6, {@code /afl dev rock scatter}): loose stones on the ground within radius of centre. Field: in
     * about one column of 5 on open ground (air or a plant, replaced); river: in about one of 3 water columns, on the bed
     * (waterlogged), and on dry ground next to it as field does. Each patch 0-4 pebbles, a field stone in about 1 of 3 (2 of
     * 5 in a river), a flint at flintShare. The number placed.
     */
    public static int scatter(ServerLevel level, BlockPos centre, int radius, Kind kind, float flintShare) {
        RandomSource rng = level.random;
        int placed = 0;
        var p = new BlockPos.MutableBlockPos();
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            int x = centre.getX() + dx, z = centre.getZ() + dz;
            p.set(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), z);
            BlockState at = level.getBlockState(p);
            boolean water = at.getFluidState().is(Fluids.WATER) && at.getFluidState().isSource() && at.is(Blocks.WATER);
            boolean open = at.isAir() || at.getFluidState().isEmpty() && at.canBeReplaced();
            if (!(water ? kind == Kind.RIVER : open)) continue;
            if (rng.nextFloat() >= (water ? 1 / 3F : 1 / 5F)) continue;
            boolean cobble = rng.nextFloat() < (kind == Kind.RIVER ? 0.4F : 0.33F), flint = rng.nextFloat() < flintShare;
            int stones = rng.nextInt(5);
            if (stones == 0 && !cobble && !flint) stones = 1 + rng.nextInt(2);
            BlockState s = block.defaultBlockState().setValue(STONES, stones).setValue(COBBLE, cobble).setValue(FLINT, flint)
                    .setValue(KIND, kind).setValue(TURN, rng.nextInt(4)).setValue(WATERLOGGED, water);
            if (!s.canSurvive(level, p)) continue;
            level.setBlock(p, s, Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }
}
