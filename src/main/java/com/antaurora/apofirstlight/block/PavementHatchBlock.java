package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Pavement Markings V1: hatching (one diagonal) and cross-hatching (both). The stripes run corner to corner and carry on into
 * the neighbouring cells; where a stripe passes a cell's other corners, a small triangle of it lies in this cell. That
 * triangle is drawn only when a neighbouring hatch continues the stripe there, so a lone cell shows a clean diagonal (user
 * 2026-10-08: "左上角的斜线不是还有一点残留吗") and a field shows unbroken stripes. The corners are in model space (the
 * mesh drawn facing north, turned by FACING): NE / SW belong to the main diagonal, NW / SE to the cross-hatch's other one.
 */
public final class PavementHatchBlock extends PavementPaintBlock {
    public static final BooleanProperty NE = BooleanProperty.create("ne"), SW = BooleanProperty.create("sw"),
            NW = BooleanProperty.create("nw"), SE = BooleanProperty.create("se");
    private final boolean cross;

    public PavementHatchBlock(Properties properties, boolean cross) {
        super(properties, Paint.WHITE);
        this.cross = cross;
        registerDefaultState(defaultBlockState().setValue(NE, false).setValue(SW, false).setValue(NW, false).setValue(SE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NE, SW, NW, SE);
    }

    /** A neighbour that carries this cell's stripes on: the same block, and for one-way hatching the same stripe direction. */
    private boolean continues(BlockState self, BlockState other) {
        return other.is(this) && (cross || other.getValue(FACING).getAxis() == self.getValue(FACING).getAxis());
    }

    private BlockState withCorners(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction f = state.getValue(FACING);
        boolean north = continues(state, level.getBlockState(pos.relative(f))), east = continues(state, level.getBlockState(pos.relative(f.getClockWise()))),
                south = continues(state, level.getBlockState(pos.relative(f.getOpposite()))), west = continues(state, level.getBlockState(pos.relative(f.getCounterClockWise())));
        return state.setValue(NE, north || east).setValue(SW, south || west).setValue(NW, cross && (north || west)).setValue(SE, cross && (south || east));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withCorners(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                  BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        return updated.is(this) && direction.getAxis().isHorizontal() ? withCorners(updated, level, pos) : updated;
    }
}
