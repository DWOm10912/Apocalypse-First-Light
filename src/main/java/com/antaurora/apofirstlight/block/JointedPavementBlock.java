package com.antaurora.apofirstlight.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Ground Materials V1 (docs/models/ground_materials_v1.md): jointed concrete flatwork, the sidewalk (tooled joints every
 * 2 m) and the pavement (saw cuts every 4 m). The joints follow world position (client/GroundJointModel), so no state
 * stores them; {@link #AXIS} is the way the walk or the drive runs (the player's facing on placement), and the broom lines
 * run across it. Rotating a structure by 90 degrees swaps it.
 */
public final class JointedPavementBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private final int jointSpacing;

    public JointedPavementBlock(Properties properties, int jointSpacing) {
        super(properties);
        this.jointSpacing = jointSpacing;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
    }

    /** Blocks between joint lines (the lines lie on world multiples of it). */
    public int jointSpacing() {
        return jointSpacing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation != Rotation.CLOCKWISE_90 && rotation != Rotation.COUNTERCLOCKWISE_90) return state;
        return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
