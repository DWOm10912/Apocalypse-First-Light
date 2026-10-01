package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Power Cable V2: a bundled cable connecting in six directions to other cables and to AFL power ports
 * ({@link AflPowerPortBlock}). The block state only records which sides connect; the client model
 * (client/PowerCableBakedModel) picks straight runs, bends, junction boxes, end caps and plugs from them. FE transfer is
 * energy/PowerCableTransfer (unchanged). Old V1 states carried a {@code show_core} property, which is dropped on load.
 */
public final class PowerCableBlock extends PipeBlock {
    private static final double ARM = 2.2;       // half thickness of a run (the bundle is 3.7 px across)
    private static final double BOX = 3.2;       // junction box half size
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    public PowerCableBlock(Properties properties) {
        super(2.0F / 16.0F, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            BlockState neighborState = context.getLevel().getBlockState(pos.relative(direction));
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(direction, neighborState));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(direction, neighborState));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    // ---- shape: runs as 4.4 px square arms, a junction box where three or more meet (or none) ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) mask |= 1 << direction.ordinal();
        }
        VoxelShape shape = SHAPES[mask];
        if (shape == null) SHAPES[mask] = shape = buildShape(mask);
        return shape;
    }

    private static VoxelShape buildShape(int mask) {
        int count = Integer.bitCount(mask);
        double centre = count == 0 || count >= 3 ? BOX : ARM;
        VoxelShape shape = box(centre, centre, centre);
        for (Direction direction : Direction.values()) {
            if ((mask & 1 << direction.ordinal()) == 0) continue;
            double[] min = {8 - ARM, 8 - ARM, 8 - ARM};
            double[] max = {8 + ARM, 8 + ARM, 8 + ARM};
            int axis = direction.getAxis().ordinal();
            if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) max[axis] = 16;
            else min[axis] = 0;
            shape = Shapes.or(shape, Block.box(min[0], min[1], min[2], max[0], max[1], max[2]));
        }
        return shape.optimize();
    }

    private static VoxelShape box(double hx, double hy, double hz) {
        return Block.box(8 - hx, 8 - hy, 8 - hz, 8 + hx, 8 + hy, 8 + hz);
    }

    // ---- connections ----

    /** What a connected side of a cable leads to (the client model's choice of run end). */
    public enum Link { NONE, CABLE, PORT }

    public static Link link(BlockGetter level, BlockPos pos, BlockState cableState, Direction direction) {
        if (!cableState.getValue(PROPERTY_BY_DIRECTION.get(direction))) return Link.NONE;
        return level.getBlockState(pos.relative(direction)).is(AflBlocks.POWER_CABLE.get()) ? Link.CABLE : Link.PORT;
    }

    public static boolean isConnected(BlockState cableState, Direction direction) {
        return cableState.is(AflBlocks.POWER_CABLE.get())
                && cableState.getValue(PROPERTY_BY_DIRECTION.get(direction));
    }

    /** The existing machines' single port face (opposite FACING); generators and energy cells push power out of it. */
    public static Direction utilityPortFace(BlockState machineState) {
        return machineState.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }

    /** True when {@code face} of this block is an AFL power port ({@link AflPowerPortBlock}). */
    public static boolean isUtilityPortFace(BlockState machineState, Direction face) {
        return machineState.getBlock() instanceof AflPowerPortBlock port && port.hasPowerPort(machineState, face);
    }

    private static boolean connectsTo(Direction directionToNeighbor, BlockState neighborState) {
        if (neighborState.is(AflBlocks.POWER_CABLE.get())) {
            return true;
        }
        return isUtilityPortFace(neighborState, directionToNeighbor.getOpposite());
    }
}
