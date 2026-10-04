package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Fuel Island Kit V1 half-round end (tools/build-fuel-island-v1.mjs): half a cell of straight curb, then a half round of
 * radius 8 px, the steel angle following it. Its joining side is the facing's counter-clockwise side (the model's -X), the
 * round side the clockwise one. Placed next to an island piece (curb, end, the dispenser's segment) it turns to join it;
 * otherwise it faces the player like the curb.
 */
public class FuelIslandEndBlock extends FuelIslandCurbBlock {
    /** North-facing shape: the straight half, then the half round in four steps (half widths at the steps' outer edges). */
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(Shapes.or(
            Block.box(0, 0, 0, 8, 3, 16),
            Block.box(8, 0, 0.25, 10, 3, 15.75),
            Block.box(10, 0, 1.07, 12, 3, 14.93),
            Block.box(12, 0, 2.71, 14, 3, 13.29),
            Block.box(14, 0, 5.22, 15.6, 3, 10.78)).optimize());

    public FuelIslandEndBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal() && isIslandPiece(level.getBlockState(pos.relative(direction)))) {
                return defaultBlockState().setValue(FACING, direction.getClockWise());   // the joining side toward that piece
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
