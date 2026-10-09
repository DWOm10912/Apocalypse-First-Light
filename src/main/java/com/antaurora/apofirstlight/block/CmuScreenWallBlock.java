package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Trash enclosure V1 screen wall (docs/models/fuel_stop_a1_details_v1.md, tools/build-trash-enclosure-v1.mjs): a 200 mm
 * ground face CMU wall hugging one or more edges of its cell (NORTH / EAST / SOUTH / WEST), so the space it screens keeps
 * whole cells. Placing chooses the edge nearest the aimed point (against a clicked block's face, the edge touching it; near
 * the middle of a cell, the far edge the player looks toward); placing on a wall cell adds the aimed edge if it is missing,
 * as a slab doubles. CAP: the cast stone cap on top, whenever the cell above is not this wall. One item per edge.
 */
public class CmuScreenWallBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty CAP = BooleanProperty.create("cap");
    private static final Map<Direction, BooleanProperty> EDGES = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    /** px: 200 mm, as the generator's WALL.t */
    private static final double T = 3.2;
    /** Aimed within this far (block) of the cell's middle, the edge comes from the player's facing. */
    private static final double MIDDLE = 0.25;
    private static final Map<Direction, VoxelShape> EDGE_SHAPES = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, T), Direction.EAST, Block.box(16 - T, 0, 0, 16, 16, 16),
            Direction.SOUTH, Block.box(0, 0, 16 - T, 16, 16, 16), Direction.WEST, Block.box(0, 0, 0, T, 16, 16));
    private final VoxelShape[] shapes = new VoxelShape[16];

    public CmuScreenWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false).setValue(CAP, true));
        for (int mask = 0; mask < 16; mask++) {
            VoxelShape shape = Shapes.empty();
            for (Direction d : Direction.Plane.HORIZONTAL) if ((mask & (1 << d.get2DDataValue())) != 0) shape = Shapes.or(shape, EDGE_SHAPES.get(d));
            shapes[mask] = shape.optimize();
        }
    }

    public static BooleanProperty edge(Direction direction) {
        return EDGES.get(direction);
    }

    /** The edge the player aims at in the cell being placed into. */
    private static Direction aimedEdge(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        double fx = Mth.clamp(hit.x - pos.getX(), 0, 1), fz = Mth.clamp(hit.z - pos.getZ(), 0, 1);
        Direction best = Direction.NORTH;
        double nearest = fz;
        if (1 - fx < nearest) { best = Direction.EAST; nearest = 1 - fx; }
        if (1 - fz < nearest) { best = Direction.SOUTH; nearest = 1 - fz; }
        if (fx < nearest) { best = Direction.WEST; nearest = fx; }
        return nearest > 0.5 - MIDDLE ? context.getHorizontalDirection() : best;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState existing = context.getLevel().getBlockState(pos);
        BlockState base = existing.is(this) ? existing : defaultBlockState().setValue(CAP, !context.getLevel().getBlockState(pos.above()).is(this));
        return base.setValue(edge(aimedEdge(context)), true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && !state.getValue(edge(aimedEdge(context)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.UP ? state.setValue(CAP, !neighborState.is(this)) : state;
    }

    private static int mask(BlockState state) {
        int mask = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) if (state.getValue(edge(d))) mask |= 1 << d.get2DDataValue();
        return mask;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[mask(state)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction d : Direction.Plane.HORIZONTAL) rotated = rotated.setValue(edge(rotation.rotate(d)), state.getValue(edge(d)));
        return rotated;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction d : Direction.Plane.HORIZONTAL) mirrored = mirrored.setValue(edge(mirror.mirror(d)), state.getValue(edge(d)));
        return mirrored;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, CAP);
    }
}
