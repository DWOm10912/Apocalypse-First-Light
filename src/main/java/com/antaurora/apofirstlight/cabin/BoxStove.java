package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmokingRecipe;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Box Stove V1 (docs/models/box_stove_v1.md, 2026-10-13): the cast-iron box stove and its heat shield, the forest start
 * home B's heating and cooking (docs/worldgen/forest_start_home_b_v1_plan.md, M2 F1; user 2026-10-13: stove A, the heat
 * shield a block of its own). Mesh blocks ({@link CabinPieces}, pieces cabin_mesh/stove/ and cabin_mesh/shield/,
 * tools/build-box-stove-v1.mjs), drawn facing north (front at z 0, the wall at z 1; the stove's door end at x 0).
 * <ul>
 * <li>{@link Stove}: burns wood (item tags {@code apocalypse_firstlight:stove_fuel/logs | wood | kindling}); the door opens
 * on a right-click (fuel goes in, a flint and steel or fire charge lights it), the top takes two things to cook (anything
 * with a smoking recipe: food, dirty water). {@code lit} is a heat source (temperature_v1.json) and lights the room; the
 * fire, its glow and the chimney's smoke are the client's (client BoxStoveFire).</li>
 * <li>{@link HeatShield}: a galvanised sheet on ceramic spools in front of a wall (facing = away from it); shields side by
 * side, above and below join into one sheet, and a stove or a stove pipe standing between shields (on their line) shows
 * the sheet behind it too (a cell holds one block).</li>
 * </ul>
 * Mining (AGENTS.md audit; below the industrial diamond default, user-approved in the B V1 plan): the stove a pickaxe of
 * iron ({@code minecraft:needs_iron_tool}), the shield sheet like the stove pipe and the sheet roofs a stone pickaxe
 * ({@code minecraft:needs_stone_tool}); correct tool required; each drops itself (the stove its cook-top items too).
 */
public final class BoxStove {
    public static BlockEntityType<BoxStoveBlockEntity> STOVE;
    public static final TagKey<Item> FUEL_LOGS = ItemTags.create(CabinBlocks.id("stove_fuel/logs")),
            FUEL_WOOD = ItemTags.create(CabinBlocks.id("stove_fuel/wood")), FUEL_KINDLING = ItemTags.create(CabinBlocks.id("stove_fuel/kindling"));
    /** Burn ticks: a log (two in-game hours), sawn wood, kindling; the firebox holds three logs' worth. */
    public static final int LOG_TICKS = 2400, WOOD_TICKS = 600, KINDLING_TICKS = 200, CAPACITY = 3 * LOG_TICKS;
    /** In the north frame (tools/build-box-stove-v1.mjs): the top plate's height, the two cook spots on it (x, z). */
    public static final double TOP = 0.56;
    public static final double[][] SPOTS = {{0.11, 0.46}, {0.31, 0.46}};

    private BoxStove() {}

    /** Called from CabinBlocks#register: the block entity type (after the blocks). */
    static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            STOVE = BlockEntityType.Builder.of(BoxStoveBlockEntity::new, CabinBlocks.block("box_stove")).build(null);
            helper.register(CabinBlocks.id("box_stove"), STOVE);
        });
    }

    /** The burn ticks an item adds (0: not stove fuel). */
    public static int fuelTicks(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (s.is(FUEL_LOGS)) return LOG_TICKS;
        if (s.is(FUEL_WOOD)) return WOOD_TICKS;
        return s.is(FUEL_KINDLING) ? KINDLING_TICKS : 0;
    }

    /** A fire-maker: flint and steel or a fire charge. */
    public static boolean igniter(ItemStack s) { return s.is(Items.FLINT_AND_STEEL) || s.is(Items.FIRE_CHARGE); }

    /** The smoking recipe of an item (food, dirty water: AFL's boiling recipes are smoking recipes). */
    public static Optional<SmokingRecipe> recipe(Level level, ItemStack s) {
        return s.isEmpty() ? Optional.empty() : level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SimpleContainer(s), level);
    }

    /** A point of the north frame turned to the facing (as the mesh pieces), in world space. */
    public static Vec3 world(Direction facing, BlockPos pos, double x, double y, double z) {
        for (int k = 0; k < CabinBlocks.turn(facing); k++) { double t = x; x = 1 - z; z = t; }
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    /** A world point in the north frame of a block facing {@code facing} at pos: {x, y, z}. */
    public static double[] frame(Direction facing, BlockPos pos, Vec3 p) {
        double x = p.x - pos.getX(), y = p.y - pos.getY(), z = p.z - pos.getZ();
        for (int k = 0; k < CabinBlocks.turn(facing); k++) { double t = z; z = 1 - x; x = t; }
        return new double[]{x, y, z};
    }

    // ---------------------------------------------------------------------------------------------- shield joins

    private static boolean shieldAt(BlockGetter level, BlockPos p, Direction f) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof HeatShield && s.getValue(HeatShield.FACING) == f;
    }

    /** A shield facing f beside pos on its line (to its left or right as seen from the front). */
    static boolean shieldBeside(BlockGetter level, BlockPos pos, Direction f) {
        return shieldAt(level, pos.relative(f.getClockWise()), f) || shieldAt(level, pos.relative(f.getCounterClockWise()), f);
    }

    /** Whether the cell shows a shield sheet facing f: a shield, or a stove facing f / a stove pipe with a shield beside it. */
    static boolean panelAt(BlockGetter level, BlockPos p, Direction f) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof HeatShield) return s.getValue(HeatShield.FACING) == f;
        boolean holder = s.getBlock() instanceof Stove && s.getValue(Stove.FACING) == f || s.getBlock() instanceof CabinBlocks.StovePipe;
        return holder && shieldBeside(level, p, f);
    }

    /**
     * The joined sides of a sheet facing f at pos (1: x- of the north frame, 2: x+, 4: up, 8: down; x- is the facing's
     * counter-clockwise side). Beside a shield cell, a stove facing f or a stove pipe always carries the sheet on (this
     * shield is the one beside it); beside a stove or pipe cell only shields count. Every read lies within one block.
     */
    static int panelMask(BlockGetter level, BlockPos pos, Direction f, boolean fromShield) {
        int m = 0;
        if (sideJoined(level, pos.relative(f.getCounterClockWise()), f, fromShield)) m |= 1;
        if (sideJoined(level, pos.relative(f.getClockWise()), f, fromShield)) m |= 2;
        if (panelAt(level, pos.above(), f)) m |= 4;
        if (panelAt(level, pos.below(), f)) m |= 8;
        return m;
    }

    private static boolean sideJoined(BlockGetter level, BlockPos n, Direction f, boolean fromShield) {
        BlockState s = level.getBlockState(n);
        if (s.getBlock() instanceof HeatShield) return s.getValue(HeatShield.FACING) == f;
        return fromShield && (s.getBlock() instanceof Stove && s.getValue(Stove.FACING) == f || s.getBlock() instanceof CabinBlocks.StovePipe);
    }

    /** A stove pipe's sheet behind it: 0 none, else 1 | turn << 1 | mask << 3 (the first facing with a shield beside). */
    static int pipePanel(BlockGetter level, BlockPos pos) {
        for (Direction f : Direction.Plane.HORIZONTAL)
            if (shieldBeside(level, pos, f)) return 1 | CabinBlocks.turn(f) << 1 | panelMask(level, pos, f, false) << 3;
        return 0;
    }

    // ---------------------------------------------------------------------------------------------------- stove

    /** What a right-click on the stove does (also the prompt: client WorldInteractionHint). */
    public enum Action { NONE, OPEN, CLOSE, FUEL, FULL, LIGHT, PUT, TAKE }

    /**
     * The cast-iron box stove (facing = its front, the long side to the room; its back to a wall). States: {@code open}
     * (the loading door), {@code lit}, {@code load} (0..3: the fuel left in logs' worth, the splits drawn inside).
     */
    public static final class Stove extends BaseEntityBlock implements CabinPieces {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN, LIT = BlockStateProperties.LIT;
        public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 3);
        private static final VoxelShape[] SHAPES = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++)   // the body and top plate, the flue collar and pipe stub
                SHAPES[r] = CabinBlocks.turned(r, new double[]{0, 0, 4.61, 11.39, 8.96, 10.11}, new double[]{6.72, 8.96, 6.72, 9.28, 16, 9.28});
        }

        Stove(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(LIT, false).setValue(LOAD, 0));
        }

        /** Block light: an open fire lights the room, a closed stove glows a little through its draft. */
        static int light(BlockState s) { return !s.getValue(LIT) ? 0 : s.getValue(OPEN) ? 12 : 6; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN, LIT, LOAD); }

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
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[CabinBlocks.turn(state.getValue(FACING))]; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BoxStoveBlockEntity(pos, state); }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != STOVE) return null;
            return level.isClientSide
                    ? (l, p, s, be) -> com.antaurora.apofirstlight.client.BoxStoveFire.tick((BoxStoveBlockEntity) be)
                    : (l, p, s, be) -> ((BoxStoveBlockEntity) be).serverTick(s);
        }

        /** The cook spot aimed at on the top (-1: not the top's cooking end). */
        public static int spotAt(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
            double[] f = frame(state.getValue(FACING), pos, hit);
            if (face != Direction.UP || f[1] < TOP - 0.01 || f[1] > TOP + 0.02 || f[0] > 0.42) return -1;
            return f[0] < 0.21 ? 0 : 1;
        }

        /** Where a cook spot's item lies (world). */
        public static Vec3 spotPoint(BlockState state, BlockPos pos, int spot) {
            return world(state.getValue(FACING), pos, SPOTS[spot][0], TOP + 0.03, SPOTS[spot][1]);
        }

        /** The loading door's middle (world), for the prompt. */
        public static Vec3 doorPoint(BlockState state, BlockPos pos) { return world(state.getValue(FACING), pos, -0.03, 0.345, 0.46); }

        /** The right-click with {@code held} at {@code hit} (the stove's fuel as this side knows it). */
        public static Action action(Level level, BlockState state, BlockPos pos, BoxStoveBlockEntity stove, ItemStack held, Vec3 hit, Direction face) {
            int spot = spotAt(state, pos, hit, face);
            if (spot >= 0) {
                if (held.isEmpty()) { if (stove.takeSpot(spot) >= 0) return Action.TAKE; }
                else if (recipe(level, held).isPresent()) return stove.freeSpot(spot) >= 0 ? Action.PUT : Action.FULL;
            }
            if (!state.getValue(OPEN)) return Action.OPEN;
            int fuel = fuelTicks(held);
            if (fuel > 0) return stove.fits(fuel) ? Action.FUEL : Action.FULL;
            if (igniter(held) && !state.getValue(LIT) && stove.fuel() > 0) return Action.LIGHT;
            return Action.CLOSE;
        }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof BoxStoveBlockEntity stove)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            Action a = action(level, state, pos, stove, held, hit.getLocation(), hit.getDirection());
            if (a == Action.NONE) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            boolean creative = player.getAbilities().instabuild;
            switch (a) {
                case OPEN, CLOSE -> {
                    level.setBlock(pos, state.setValue(OPEN, a == Action.OPEN), 3);
                    level.playSound(null, pos, a == Action.OPEN ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 0.85F);
                }
                case FUEL -> {
                    stove.addFuel(fuelTicks(held));
                    if (!creative) held.shrink(1);
                    level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
                }
                case LIGHT -> {
                    stove.light();
                    if (held.is(Items.FIRE_CHARGE)) {
                        if (!creative) held.shrink(1);
                        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
                    } else {
                        held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
                    }
                }
                case PUT -> {
                    int spot = stove.freeSpot(spotAt(state, pos, hit.getLocation(), hit.getDirection()));
                    stove.put(spot, creative ? held.copyWithCount(1) : held.split(1));
                    level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
                }
                case TAKE -> {
                    int spot = stove.takeSpot(spotAt(state, pos, hit.getLocation(), hit.getDirection()));
                    player.setItemInHand(hand, stove.take(spot));
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.0F);
                }
                default -> {}
            }
            return InteractionResult.CONSUME;
        }

        /** The top is hot while it burns (like a magma block; stepping carefully is safe). */
        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (state.getValue(LIT) && !entity.isSteppingCarefully() && entity instanceof LivingEntity)
                entity.hurt(level.damageSources().hotFloor(), 1.0F);
            super.stepOn(level, pos, state, entity);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BoxStoveBlockEntity stove)
                for (int k = 0; k < SPOTS.length; k++) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, stove.item(k));
            super.onRemove(state, level, pos, newState, moved);
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) {
            Direction f = state.getValue(FACING);
            return shieldBeside(level, pos, f) ? 1 | panelMask(level, pos, f, false) << 1 : 0;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = CabinBlocks.turn(state.getValue(FACING)), load = state.getValue(LOAD);
            sink.add("stove/body", r);
            sink.add(state.getValue(OPEN) ? "stove/door_open" : "stove/door_closed", r);
            if (load > 0) sink.add("stove/load_" + load, r);
            if ((flags & 1) != 0) sink.add("shield/panel_" + (flags >> 1 & 15), r);
        }
    }

    // ---------------------------------------------------------------------------------------------- heat shield

    /** A heat shield on a wall (facing = away from it; it needs a sturdy wall behind it). */
    public static final class HeatShield extends Block implements CabinPieces {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        private static final VoxelShape[] SHAPES = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++) SHAPES[r] = CabinBlocks.turned(r, new double[]{0, 0, 14.24, 16, 16, 16});
        }

        HeatShield(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        /** Against the first wall that holds it, from the horizontal direction nearest the player's look (as the gun rack). */
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
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[CabinBlocks.turn(state.getValue(FACING))]; }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return panelMask(level, pos, state.getValue(FACING), true); }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) { sink.add("shield/panel_" + (flags & 15), CabinBlocks.turn(state.getValue(FACING))); }
    }
}
