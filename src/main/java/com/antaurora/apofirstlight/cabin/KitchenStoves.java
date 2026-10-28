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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Kitchen stoves (Cooking V1 K1c, docs/gameplay/cooking_v1_plan.md 7.4; user 2026-10-27: "两种都做，不过老宅只放老式搪瓷柴灶"):
 * modelled and baked in Blender (tools/stove/cookstove_v1, propane_range_v1; kitchen_build.py, kitchen_export.py), drawn
 * as cabin pieces (tools/build-kitchen-stoves-v1.mjs). Not lit in V1: a dutch oven or a skillet (item tag
 * {@link #COOKWARE}) stands on the cooktop, nothing more (no fire, no gas, no cooking yet).
 * <ul>
 *   <li>{@link Cookstove}: the forest home's old cream and green enamel wood cookstove, two cells high (the high back with
 *   the warming closet and the shelf, the flue elbowed back into the wall); one cook lid over the firebox, one pot.</li>
 *   <li>{@link Range}: the town houses' white 30 inch propane range, one cell; four burners in two pairs under two grates,
 *   one pot or pan a pair (a 12 inch skillet covers the next burner), on the front or the back burner as aimed.</li>
 * </ul>
 * Facing: the front, toward the room (the pieces are drawn facing north). Mining (AGENTS.md audit, as the box stove): the
 * cast-iron cookstove an iron pickaxe, the sheet-steel range a stone one; each drops itself (the cookstove from its lower
 * half) and what stands on it.
 */
public final class KitchenStoves {
    public static BlockEntityType<CooktopBlockEntity> COOKTOP;
    public static final TagKey<Item> COOKWARE = ItemTags.create(CabinBlocks.id("cookware"));

    private KitchenStoves() {}

    /** Called from CabinBlocks#register: the block entity type (after the blocks). */
    static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            COOKTOP = BlockEntityType.Builder.of(CooktopBlockEntity::new, CabinBlocks.block("wood_cookstove"), CabinBlocks.block("propane_range")).build(null);
            helper.register(CabinBlocks.id("cooktop"), COOKTOP);
        });
    }

    /** What a right-click on the cooktop does (also the prompt: client WorldInteractionHint). */
    public enum Action { NONE, PUT, TAKE }

    /** A stove with a cooktop: its spots, which one a hit on the top aims at, putting a pot on and taking it off. */
    public abstract static class Stove extends BaseEntityBlock implements CabinPieces {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

        Stove(Properties p) { super(p); }

        /** How many pots it takes. */
        public abstract int spots();

        /** Where a pot stands on spot k, on burner b (north frame: x, the cooktop's height, z). */
        public abstract double[] spot(int k, int burner);

        /** The spot and burner aimed at by a hit on the top (null: not the cooktop). */
        abstract int @Nullable [] aimed(BlockState state, BlockPos pos, Vec3 hit, Direction face);

        /** The block entity's cell (the cookstove's lower half). */
        BlockPos master(BlockState state, BlockPos pos) { return pos; }

        /** Which way a skillet's handle points on spot k (deg about up, from +x of the north frame toward -z). */
        public abstract float handleYaw(int k);

        public static double[] frame(BlockState state, BlockPos pos, Vec3 p) { return BoxStove.frame(state.getValue(FACING), pos, p); }

        /** A spot's point (world). */
        public Vec3 spotPoint(BlockState state, BlockPos pos, int k, int burner) {
            double[] s = spot(k, burner);
            return BoxStove.world(state.getValue(FACING), master(state, pos), s[0], s[1] + 0.06, s[2]);
        }

        public static Action action(CooktopBlockEntity top, ItemStack held, int[] aimed) {
            if (aimed == null) return Action.NONE;
            if (held.isEmpty()) return top.item(aimed[0]).isEmpty() ? Action.NONE : Action.TAKE;
            return held.is(COOKWARE) && top.item(aimed[0]).isEmpty() ? Action.PUT : Action.NONE;
        }

        /** The cooktop's block entity and the spot aimed at, for the prompt (null: nothing to do here). */
        public @Nullable Object[] aim(Level level, BlockState state, BlockPos pos, Vec3 hit, Direction face) {
            int[] a = aimed(state, pos, hit, face);
            if (a == null || !(level.getBlockEntity(master(state, pos)) instanceof CooktopBlockEntity top)) return null;
            return new Object[]{top, a};
        }

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
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CooktopBlockEntity(pos, state); }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            int[] a = aimed(state, pos, hit.getLocation(), hit.getDirection());
            if (a == null || !(level.getBlockEntity(master(state, pos)) instanceof CooktopBlockEntity top)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            Action act = action(top, held, a);
            if (act == Action.NONE) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            Vec3 at = spotPoint(state, pos, a[0], a[1]);
            if (act == Action.PUT) {
                top.put(a[0], a[1], player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1));
                level.playSound(null, at.x, at.y, at.z, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.6F, 0.8F);
            } else {
                player.setItemInHand(hand, top.take(a[0]));
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.0F);
            }
            return InteractionResult.CONSUME;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CooktopBlockEntity top)
                for (int k = 0; k < spots(); k++) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, top.item(k));
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // ---------------------------------------------------------------------------------------------- the cookstove

    /**
     * The old enamel wood cookstove (tools/stove/cookstove_v1/model.py): cooktop at 77 cm, its one cook lid at (0.76, 0.38)
     * over the firebox (the x 1 end: on the left as you face it), the high back from z 0.60, the warming closet and the
     * shelf to 1.44 m, the flue up behind them, elbowed back into the wall at 1.71 m. Two cells: {@code half}.
     */
    public static final class Cookstove extends Stove {
        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        /** The cook lid's top (north frame y) and middle (x, z); the high back's front. */
        public static final double TOP = 0.773, LID_X = 0.76, LID_Z = 0.38, BACK_Z = 0.60;
        private static final VoxelShape[] LOWER = new VoxelShape[4], UPPER = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++) {
                LOWER[r] = CabinBlocks.turned(r, new double[]{0.67, 0, 2.1, 15.33, 12.36, 11.9}, new double[]{0.67, 12.36, 6.4, 15.33, 16, 10.4},
                        new double[]{2.8, 12.36, 9.76, 4.88, 16, 11.84});
                UPPER[r] = CabinBlocks.turned(r, new double[]{0.32, 0, 6.4, 15.68, 7.1, 10.9}, new double[]{2.8, 0, 9.76, 4.88, 12.4, 11.84},
                        new double[]{2.8, 10.3, 11.84, 4.88, 12.4, 16});
            }
        }

        Cookstove(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF); }

        @Override
        public int spots() { return 1; }

        @Override
        public double[] spot(int k, int burner) { return new double[]{LID_X, TOP, LID_Z}; }

        @Override
        public float handleYaw(int k) { return 45; }

        @Override
        BlockPos master(BlockState state, BlockPos pos) { return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below(); }

        @Override
        int @Nullable [] aimed(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
            if (state.getValue(HALF) != DoubleBlockHalf.LOWER || face != Direction.UP) return null;
            double[] f = frame(state, pos, hit);
            return f[1] >= TOP - 0.02 && f[1] <= TOP + 0.02 && f[2] < BACK_Z ? new int[]{0, 0} : null;
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.newBlockEntity(pos, state) : null;
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add(state.getValue(HALF) == DoubleBlockHalf.LOWER ? "kitchen/wood_cookstove_body" : "kitchen/wood_cookstove_top", CabinBlocks.turn(state.getValue(FACING)));
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockPos pos = context.getClickedPos();
            Level level = context.getLevel();
            if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
            return super.getStateForPlacement(context);
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

        /** The other half gone: this one goes too (the lower drops the stove and its pot, as a door's lower half). */
        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            DoubleBlockHalf half = state.getValue(HALF);
            if (d.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (d == Direction.UP))
                return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
            return state;
        }

        /** The upper half broken in creative or without the right tool: the lower goes without a drop (vanilla doors; the pot still drops). */
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

    // ---------------------------------------------------------------------------------------------- the propane range

    /**
     * The 30 inch propane range (tools/stove/propane_range_v1/model.py): cabinet 76 x 64 cm to 91 cm, the grates' top at
     * 93.8 cm, burners at x 0.69 (the left pair as you face it: spot 0) and 0.31 (spot 1), z 0.27 (front) and 0.53 (back),
     * the backguard to 99.5 cm; the oven handle stands 7.5 cm out in front.
     */
    public static final class Range extends Stove {
        public static final double GRATE_TOP = 0.938;
        private static final double[] PAIR_X = {0.69, 0.31}, BURNER_Z = {0.27, 0.53};
        private static final VoxelShape[] SHAPES = new VoxelShape[4];

        static {
            for (int r = 0; r < 4; r++)
                SHAPES[r] = CabinBlocks.turned(r, new double[]{1.86, 0, 0, 14.14, 14.56, 11.52}, new double[]{2.08, 14.56, 1.76, 13.92, 15.0, 10.56},
                        new double[]{1.86, 14.56, 10.72, 14.14, 15.92, 11.52});
        }

        Range(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        @Override
        public int spots() { return 2; }

        @Override
        public double[] spot(int k, int burner) { return new double[]{PAIR_X[k], GRATE_TOP, BURNER_Z[burner]}; }

        @Override
        public float handleYaw(int k) { return k == 0 ? 45 : 135; }

        @Override
        int @Nullable [] aimed(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
            if (face != Direction.UP) return null;
            double[] f = frame(state, pos, hit);
            if (f[1] < GRATE_TOP - 0.02 || f[1] > GRATE_TOP + 0.02 || f[2] > 0.66) return null;
            return new int[]{f[0] >= 0.5 ? 0 : 1, f[2] < 0.40 ? 0 : 1};
        }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            sink.add("kitchen/propane_range_body", CabinBlocks.turn(state.getValue(FACING)));
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[CabinBlocks.turn(state.getValue(FACING))];
        }
    }
}
