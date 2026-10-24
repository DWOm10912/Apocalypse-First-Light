package com.antaurora.apofirstlight.vegetation;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A mesh plant (AFL vegetation V1, docs/gameplay/vegetation_v1_design.md; P1: Kentucky bluegrass, broadleaf cattail,
 * common yarrow; P2b: big and little bluestem, Pennsylvania sedge, bracken, broadleaf plantain, common dandelion, purple
 * coneflower, black-eyed Susan): one block is a square metre of one {@link Species}, drawn by client/vegetation (PlantModel: clumps of
 * blades from the species and position, distance levels per section, wind; near someone the blades are simulated,
 * VegetationDynamics). No collision and no outline: the crosshair goes through to the ground (user 2026-10-24: no
 * selection box; picking, P3, aims at plants its own way); replaceable like vanilla grass, gone with the soil under it.
 * A cattail stands in water too ({@link Wet}).
 * <p>
 * Trodden (docs/gameplay/vegetation_v1_design.md 12): a footfall (VegetationFootfalls: a living thing at least
 * {@value #TREAD_WIDTH} m wide, people, the infected, deer, cattle, not rabbits or chickens, one step per stride, left and
 * right feet apart) folds the blades under the sole: TRODDEN 1, HEADING the way it walked (8 ways, 0 south then clockwise
 * as Direction's 2D order), FX/FZ where the footprint is in the block (5 x 5, 0.2 m apart, middles 0.1 .. 0.9). A trail is
 * a string of footprints. A vehicle's wheel (FlattenedPlantBlock#flatten) lies the whole block down (TRODDEN 2). A trodden
 * block rises half way after a game day (two after a wheel), TRODDEN 3, and stands half a day later; stepped on again it
 * takes the new footprint and counts afresh. On the server, so every player sees the same trail and it is saved.
 */
public class MeshPlantBlock extends BushBlock {
    /** The species: its shape on the client (PlantShape's layouts, in this order), whether it stays drawn far out. */
    public enum Species {
        BLUEGRASS(false), CATTAIL(true), YARROW(false), BIG_BLUESTEM(true), LITTLE_BLUESTEM(false), PENNSYLVANIA_SEDGE(false), BRACKEN_FERN(false),
        PLANTAIN(false), DANDELION(false), PURPLE_CONEFLOWER(false), BLACK_EYED_SUSAN(false),
        CANADA_GOLDENROD(true), COMMON_REED(true), ALLEGHENY_BLACKBERRY(true);

        public final boolean tall;

        Species(boolean tall) {
            this.tall = tall;
        }
    }

    /** 0 standing, 1 trodden down, 2 under a wheel, 3 half risen. */
    public static final IntegerProperty TRODDEN = IntegerProperty.create("trodden", 0, 3);
    public static final IntegerProperty HEADING = IntegerProperty.create("heading", 0, 7);
    public static final IntegerProperty FX = IntegerProperty.create("fx", 0, 4), FZ = IntegerProperty.create("fz", 0, 4);
    public static final int STANDING = 0, TROD = 1, WHEEL = 2, RISING = 3;
    /** The footprint grid's spacing (m): index i is the middle 0.1 + 0.2 i of the block. */
    public static final float PRINT_STEP = 0.2F;
    public static final float TREAD_WIDTH = 0.5F;
    /** Ticks down after a foot (a game day), after a wheel (two), half risen (half a day), and the wait while still pressed. */
    private static final int TROD_TICKS = 24000, WHEEL_TICKS = 48000, RISE_TICKS = 12000, WAIT_TICKS = 200;

    public final Species species;

    public MeshPlantBlock(Properties properties, Species species) {
        super(properties);
        this.species = species;
        registerDefaultState(stateDefinition.any().setValue(TRODDEN, STANDING).setValue(HEADING, 0).setValue(FX, 2).setValue(FZ, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRODDEN, HEADING, FX, FZ);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Soil (the dirt tag: grass block, dirt, podzol, mud ...) and the road's graded turf. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.getBlock() instanceof GradedTurfBlock;
    }

    // ------------------------------------------------------------------------------------------- directions

    /** 0..7 from a horizontal direction: 0 south (+z), 2 west, 4 north, 6 east (Direction#get2DDataValue * 2). */
    public static int heading(double vx, double vz) {
        return Math.floorMod((int) Math.round(Math.atan2(-vx, vz) / (Math.PI / 4)), 8);
    }

    /** The unit vector of a heading. */
    public static float headingX(int heading) {
        return (float) -Math.sin(heading * Math.PI / 4);
    }

    public static float headingZ(int heading) {
        return (float) Math.cos(heading * Math.PI / 4);
    }

    /** The footprint grid index of a position in the block (0..1). */
    public static int printIndex(double local) {
        return Mth.clamp((int) Math.round((local - 0.1) / PRINT_STEP), 0, 4);
    }

    /** The middle of a footprint grid index in the block. */
    public static float printMiddle(int index) {
        return 0.1F + PRINT_STEP * index;
    }

    // ---------------------------------------------------------------------------------------------- pressing

    /**
     * Server: a foot came down in the block at (localX, localZ) (0..1) walking along a heading: the blades under the sole
     * fold (the newest footprint replaces an older one), the rise counted afresh. Under a wheel's track only the time.
     */
    public void footfall(ServerLevel level, BlockPos pos, BlockState state, int heading, double localX, double localZ) {
        if (state.getValue(TRODDEN) == WHEEL) {
            restart(level, pos, WHEEL_TICKS);
            return;
        }
        BlockState now = state.setValue(TRODDEN, TROD).setValue(HEADING, heading).setValue(FX, printIndex(localX)).setValue(FZ, printIndex(localZ));
        if (now != state) level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        restart(level, pos, TROD_TICKS);
    }

    /** Server: lies down along facing (a vehicle drove over it); false when it already lies so. */
    public boolean flatten(ServerLevel level, BlockPos pos, BlockState state, Direction facing) {
        return flatten(level, pos, state, facing.get2DDataValue() * 2);
    }

    /** Server: lies down along a heading (0..7) under a wheel; false when it already lies so. */
    public boolean flatten(ServerLevel level, BlockPos pos, BlockState state, int heading) {
        BlockState now = state.setValue(TRODDEN, WHEEL).setValue(HEADING, heading).setValue(FX, 2).setValue(FZ, 2);
        if (now == state) return false;
        level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        restart(level, pos, WHEEL_TICKS);
        return true;
    }

    /** The block's rise counted afresh: its scheduled tick replaced by one {@code ticks} (+-10 %) from now. */
    private void restart(ServerLevel level, BlockPos pos, int ticks) {
        level.getBlockTicks().clearArea(BoundingBox.fromCorners(pos, pos));
        level.scheduleTick(pos, this, ticks - ticks / 10 + level.random.nextInt(ticks / 5 + 1));
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int trodden = state.getValue(TRODDEN);
        if (trodden == STANDING) return;
        AABB over = new AABB(pos).inflate(0.25);
        if (VehicleColliders.over(level, over)
                || !level.getEntitiesOfClass(LivingEntity.class, over, e -> e.getBbWidth() >= TREAD_WIDTH && !e.isSpectator()).isEmpty()) {
            level.scheduleTick(pos, this, WAIT_TICKS);
            return;
        }
        if (trodden != RISING) {
            level.setBlock(pos, state.setValue(TRODDEN, RISING), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, RISE_TICKS - RISE_TICKS / 10 + random.nextInt(RISE_TICKS / 5 + 1));
            return;
        }
        level.setBlock(pos, state.setValue(TRODDEN, STANDING), Block.UPDATE_CLIENTS);
    }

    /**
     * A mesh plant of the shallows (broadleaf cattail): on wet ground (soil, mud, sand, clay, gravel) or under water a block
     * deep at most, the water kept in the block (waterlogged); never with water above it.
     */
    public static class Wet extends MeshPlantBlock implements SimpleWaterloggedBlock {
        public Wet(Properties properties, Species species) {
            super(properties, species);
            registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(BlockStateProperties.WATERLOGGED);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return super.mayPlaceOn(state, level, pos) || state.is(BlockTags.SAND) || state.is(Blocks.CLAY) || state.is(Blocks.GRAVEL);
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockPos below = pos.below();
            return mayPlaceOn(level.getBlockState(below), level, below) && !level.getFluidState(pos.above()).is(FluidTags.WATER);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,
                    context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        }

        @Override
        public FluidState getFluidState(BlockState state) {
            return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        public BlockState updateShape(BlockState state, Direction from, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            if (state.getValue(BlockStateProperties.WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return super.updateShape(state, from, neighbour, level, pos, neighbourPos);
        }
    }
}
