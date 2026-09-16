package com.antaurora.apofirstlight.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A fixed, single-cell structural brace. Its item determines the slope. */
public final class FixedDiagonalBraceBlock extends Block {
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS =
            EnumProperty.create("horizontal_axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);

    private final VoxelShape xShape;
    private final VoxelShape zShape;

    public FixedDiagonalBraceBlock(Properties properties, VoxelShape xShape, VoxelShape zShape) {
        super(properties);
        this.xShape = xShape;
        this.zShape = zShape;
        registerDefaultState(stateDefinition.any().setValue(HORIZONTAL_AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction.Axis axis;
        if (face.getAxis().isHorizontal()) {
            // The brace plane follows the clicked structural face: EAST/WEST
            // selects the X-Y model and NORTH/SOUTH selects the Z-Y model.
            axis = face.getAxis();
        } else {
            // A top/bottom placement faces the player toward the broad side of the brace plane.
            axis = context.getHorizontalDirection().getAxis() == Direction.Axis.X
                    ? Direction.Axis.Z : Direction.Axis.X;
        }
        return defaultBlockState().setValue(HORIZONTAL_AXIS, axis);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos,
                               CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos,
                                        CollisionContext context) {
        return shapeFor(state);
    }

    private VoxelShape shapeFor(BlockState state) {
        return state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X ? xShape : zShape;
    }
}
