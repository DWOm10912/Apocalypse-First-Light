package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.FlattenedPlantBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Grass, ferns and flowers pressed down by a vehicle (docs/vehicles/first_pickup_v0_design.md section 8.4): a vehicle
 * driving over them (PickupTruckEntity#flattenPlants) turns them into this block, lying the way it went (FACING: where
 * the blades point). With nothing over it any more it rises half way after 20-40 s (STAGE 1) and stands up again as the
 * plant it was 5-10 s later. Grass, ferns and their tall forms are kept in KIND; any other plant (flowers, dead bushes) in
 * a block entity. A BushBlock, so the terrain smoothing treats it as cover and sinks it onto a slope like the others.
 * Mining: a plant (breaks at once by hand, no tool tag); drops nothing (it is only ever a pressed plant).
 */
public final class FlattenedPlantBlock extends BushBlock implements EntityBlock {
    public enum Kind implements StringRepresentable {
        GRASS("grass"), FERN("fern"), TALL_GRASS("tall_grass"), LARGE_FERN("large_fern"), OTHER("other");

        private final String name;

        Kind(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 1);
    private static final VoxelShape FLAT = Block.box(0, 0, 0, 16, 3, 16), RISING = Block.box(1, 0, 1, 15, 8, 15);
    /** Ticks lying flat (at least, then random more), rising, and the wait while something is still over it. */
    private static final int FLAT_TICKS = 400, RISE_TICKS = 100, WAIT_TICKS = 100;

    public FlattenedPlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.GRASS).setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, STAGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAGE) == 0 ? FLAT : RISING;
    }

    /** Whatever it grew on (sand, gravel, terracotta too: the plant was already there). */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    /**
     * Server: presses the plant at pos down (either half of a tall one), lying towards facing. False when there is none
     * (or it already lies).
     */
    public static boolean flatten(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof BushBlock) || block instanceof FlattenedPlantBlock) return false;
        BlockPos base = pos;
        boolean tall = block instanceof DoublePlantBlock;
        if (tall && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            base = pos.below();
            state = level.getBlockState(base);
            if (state.getBlock() != block) return false;
        }
        Kind kind;
        if (block == Blocks.GRASS) kind = Kind.GRASS;
        else if (block == Blocks.FERN) kind = Kind.FERN;
        else if (block == Blocks.TALL_GRASS) kind = Kind.TALL_GRASS;
        else if (block == Blocks.LARGE_FERN) kind = Kind.LARGE_FERN;
        else if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS) || block == Blocks.DEAD_BUSH) kind = Kind.OTHER;
        else return false;
        BlockState original = state;
        if (tall) level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(base, AflBlocks.FLATTENED_PLANT.get().defaultBlockState().setValue(FACING, facing).setValue(KIND, kind), Block.UPDATE_ALL);
        if (kind == Kind.OTHER && level.getBlockEntity(base) instanceof FlattenedPlantBlockEntity be) be.setOriginal(original);
        level.scheduleTick(base, AflBlocks.FLATTENED_PLANT.get(), FLAT_TICKS + level.random.nextInt(FLAT_TICKS));
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (VehicleColliders.over(level, new AABB(pos).inflate(0.25))) {
            level.scheduleTick(pos, this, WAIT_TICKS);
            return;
        }
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, RISE_TICKS + random.nextInt(RISE_TICKS));
            return;
        }
        BlockState original = original(state, level, pos);
        boolean tall = original.getBlock() instanceof DoublePlantBlock;
        if (tall && !level.isEmptyBlock(pos.above())) {
            // no room for the top half any more: the short form (grass, fern), else it stays down
            if (original.is(Blocks.TALL_GRASS)) original = Blocks.GRASS.defaultBlockState();
            else if (original.is(Blocks.LARGE_FERN)) original = Blocks.FERN.defaultBlockState();
            else {
                level.scheduleTick(pos, this, FLAT_TICKS);
                return;
            }
            tall = false;
        }
        if (tall) DoublePlantBlock.placeAt(level, original.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), pos, Block.UPDATE_ALL);
        else level.setBlock(pos, original, Block.UPDATE_ALL);
    }

    /** The plant it was. */
    private static BlockState original(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(KIND)) {
            case GRASS -> Blocks.GRASS.defaultBlockState();
            case FERN -> Blocks.FERN.defaultBlockState();
            case TALL_GRASS -> Blocks.TALL_GRASS.defaultBlockState();
            case LARGE_FERN -> Blocks.LARGE_FERN.defaultBlockState();
            case OTHER -> level.getBlockEntity(pos) instanceof FlattenedPlantBlockEntity be && be.original() != null ? be.original() : Blocks.GRASS.defaultBlockState();
        };
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(original(state, level, pos).getBlock());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(KIND) == Kind.OTHER ? new FlattenedPlantBlockEntity(pos, state) : null;
    }
}
