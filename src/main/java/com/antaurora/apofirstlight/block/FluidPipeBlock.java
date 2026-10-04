package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid Pipe V2 (docs/models/fluid_pipe_v2.md): an 8 px square steel pipe with glass sides, connecting in six directions
 * to other pipes and to fluid ports (a tank's free top / bottom, the thermal generator's and the chemical reactor's fluid
 * faces). The block state records which sides connect; the client model (client/FluidPipeBakedModel) picks runs,
 * fittings, end flanges, port flanges, bands and wall clamps from them. Transfer is fluid/FluidPipeTransfer (unchanged).
 * <p>
 * Connection rules as Power Cable V2 (PowerCableBlock, docs/models/power_cable_v2.md): pipe-to-pipe links are chosen at
 * placement, not automatic, so parallel runs stay apart; a pipe never changes a link to a neighbouring pipe on its own, it
 * mirrors that neighbour's side; sneaking with an empty main hand, a right click cuts or joins one pipe-to-pipe side.
 * Ports always connect. V1 states carried {@code show_core}; it is dropped on load (the old run-axis rules are gone).
 */
public final class FluidPipeBlock extends PipeBlock {
    private static final double ARM = 4.0;       // half width of a run (tools/build-fluid-pipe-v2.mjs PIPE.h)
    private static final double BOX = 4.6;       // fitting half size (FIT.h)
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    public FluidPipeBlock(Properties properties) {
        super(4.0F / 16.0F, properties.lightLevel(state -> state.getValue(com.antaurora.apofirstlight.fluid.FluidLighting.LIGHT)));
        registerDefaultState(stateDefinition.any()
                .setValue(com.antaurora.apofirstlight.fluid.FluidLighting.LIGHT, 0)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    // ---- placement and links (as PowerCableBlock) ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the block the pipe was placed against; none when the click replaced a replaceable block in place
        BlockPos against = context.replacingClickedOnBlock() ? null : context.getClickedPos().relative(context.getClickedFace().getOpposite());
        return placedState(context.getLevel(), context.getClickedPos(), defaultBlockState(), against);
    }

    /**
     * Links of a new pipe: fluid ports always; the pipe it was placed against; every neighbouring line end it continues
     * straight (a lone pipe, or a pipe whose single link points away from here); and, only when no pipe was linked that
     * way, the neighbouring line ends it turns a corner onto (this also closes a loop). Pipes of other runs that already
     * have two or more links are left alone, so parallel runs do not merge.
     */
    private static BlockState placedState(BlockGetter level, BlockPos pos, BlockState state, @Nullable BlockPos against) {
        boolean pipeLinked = false;
        int corners = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            boolean connect;
            if (!isPipe(neighborState)) {
                connect = isFluidPort(neighborState, direction);
            } else {
                int links = links(neighborState);
                Direction only = links == 1 ? firstLink(neighborState) : null;
                connect = neighborPos.equals(against) || links == 0 || only == direction;
                pipeLinked |= connect;
                if (!connect && only != null && isPipe(level.getBlockState(neighborPos.relative(only)))) corners |= 1 << direction.ordinal();
            }
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connect);
        }
        if (!pipeLinked) {
            for (Direction direction : Direction.values())
                if ((corners & 1 << direction.ordinal()) != 0) state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), true);
        }
        return state;
    }

    /** The links a pipe set without a player gets (GameTests, structure tools): as if placed in mid air. */
    public static BlockState withStructuralConnections(BlockGetter level, BlockPos position, BlockState state) {
        return placedState(level, position, state, null);
    }

    /** A neighbouring pipe's side is mirrored (both ends always agree); fluid ports connect whenever present. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean connect = isPipe(neighborState)
                ? neighborState.getValue(PROPERTY_BY_DIRECTION.get(direction.getOpposite()))
                : isFluidPort(neighborState, direction);
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connect);
    }

    /** Sneak + right click with an empty main hand: cut or join the aimed pipe-to-pipe side. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!PowerCableBlock.canToggle(player, hand)) return InteractionResult.PASS;
        Direction side = toggleSide(state, pos, hit.getLocation(), hit.getDirection());
        if (!isPipe(level.getBlockState(pos.relative(side)))) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // the neighbour follows through updateShape
        level.setBlock(pos, state.setValue(PROPERTY_BY_DIRECTION.get(side), !state.getValue(PROPERTY_BY_DIRECTION.get(side))), UPDATE_ALL);
        return InteractionResult.CONSUME;
    }

    /** The side a toggle click means: on an arm, that arm's direction; on the run's middle or the fitting, the clicked face. */
    public static Direction toggleSide(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
        double core = (isFitting(state) ? BOX : ARM) / 16.0 + 0.003;
        double[] d = {hit.x - pos.getX() - 0.5, hit.y - pos.getY() - 0.5, hit.z - pos.getZ() - 0.5};
        Direction best = face;
        double max = core;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double v = d[axis.ordinal()];
            if (Math.abs(v) > max) {
                max = Math.abs(v);
                best = Direction.fromAxisAndDirection(axis, v > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            }
        }
        return best;
    }

    /** Client prompt: the pipe side a sneak toggle would change, if that side leads to another pipe. */
    @Nullable
    public static Direction promptToggleSide(BlockGetter level, BlockPos pos, BlockState state, Vec3 hit, Direction face) {
        Direction side = toggleSide(state, pos, hit, face);
        return isPipe(level.getBlockState(pos.relative(side))) ? side : null;
    }

    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                     net.minecraft.util.RandomSource random) {
        com.antaurora.apofirstlight.fluid.FluidPipeVisualManager.refreshLight(level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, com.antaurora.apofirstlight.fluid.FluidLighting.LIGHT);
    }

    // ---- shape: 8 px square runs, the fitting where they do not run straight through ----

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
        VoxelShape shape = fittingMask(mask) ? Block.box(8 - BOX, 8 - BOX, 8 - BOX, 8 + BOX, 8 + BOX, 8 + BOX)
                : Block.box(8 - ARM, 8 - ARM, 8 - ARM, 8 + ARM, 8 + ARM, 8 + ARM);
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

    /** A fitting: no link, or links that are not exactly one straight pair or a single end. */
    private static boolean fittingMask(int mask) {
        int count = Integer.bitCount(mask);
        if (count == 1) return false;
        if (count == 2) for (Direction d : Direction.values()) if ((mask & 1 << d.ordinal()) != 0 && (mask & 1 << d.getOpposite().ordinal()) != 0) return false;
        return true;
    }

    private static boolean isFitting(BlockState state) {
        int mask = 0;
        for (Direction direction : Direction.values()) if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) mask |= 1 << direction.ordinal();
        return fittingMask(mask);
    }

    // ---- connections ----

    /** What a connected side of a pipe leads to (the client model's choice of run end). */
    public enum Link { NONE, PIPE, PORT }

    public static Link link(BlockGetter level, BlockPos pos, BlockState pipeState, Direction direction) {
        if (!pipeState.getValue(PROPERTY_BY_DIRECTION.get(direction))) return Link.NONE;
        return isPipe(level.getBlockState(pos.relative(direction))) ? Link.PIPE : Link.PORT;
    }

    private static boolean isPipe(BlockState state) {
        return state.is(AflBlocks.FLUID_PIPE.get());
    }

    private static int links(BlockState state) {
        int count = 0;
        for (Direction direction : Direction.values()) if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) count++;
        return count;
    }

    @Nullable
    private static Direction firstLink(BlockState state) {
        for (Direction direction : Direction.values()) if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) return direction;
        return null;
    }

    public static boolean isConnected(BlockState state, Direction direction) {
        return state.getBlock() instanceof FluidPipeBlock
                && state.getValue(PROPERTY_BY_DIRECTION.get(direction));
    }

    /**
     * Whether fluid passes from the pipe at {@code pipePosition} to its neighbour on {@code directionToNeighbor}: to a pipe
     * when the pipe's side is linked (both sides always agree), to a fluid port whenever there is one.
     */
    public static boolean canPipeEdgeConnect(BlockGetter level, BlockPos pipePosition,
                                             BlockPos neighborPosition, Direction directionToNeighbor) {
        if (!neighborPosition.equals(pipePosition.relative(directionToNeighbor))) return false;
        BlockState pipeState = level.getBlockState(pipePosition);
        BlockState neighborState = level.getBlockState(neighborPosition);
        if (isPipe(neighborState)) return isPipe(pipeState) && pipeState.getValue(PROPERTY_BY_DIRECTION.get(directionToNeighbor));
        return isFluidPort(neighborState, directionToNeighbor);
    }

    /** A fluid port on the neighbour's face toward a pipe that lies {@code directionToNeighbor} from it. */
    public static boolean isFluidPort(BlockState neighborState, Direction directionToNeighbor) {
        return canConnectToTank(neighborState, directionToNeighbor) || canConnectToSidedMachine(neighborState, directionToNeighbor);
    }

    private static boolean canConnectToTank(BlockState neighborState, Direction directionToNeighbor) {
        if (!neighborState.is(AflBlocks.FLUID_TANK.get()) || !directionToNeighbor.getAxis().isVertical()) {
            return false;
        }
        return directionToNeighbor == Direction.DOWN
                ? !neighborState.getValue(FluidTankBlock.HAS_TANK_ABOVE)
                : !neighborState.getValue(FluidTankBlock.HAS_TANK_BELOW);
    }

    private static boolean canConnectToSidedMachine(BlockState neighborState, Direction directionToNeighbor) {
        if (neighborState.is(AflBlocks.THERMAL_GENERATOR.get())) {
            Direction face = directionToNeighbor.getOpposite();
            return face == ThermalGeneratorBlock.inputFluidFace(neighborState)
                    || face == ThermalGeneratorBlock.outputFluidFace(neighborState);
        }
        if (!neighborState.is(AflBlocks.CHEMICAL_REACTOR.get())) {
            return false;
        }
        Direction machineFace = directionToNeighbor.getOpposite();
        return ChemicalReactorBlock.isInputFluidFace(neighborState, machineFace)
                || ChemicalReactorBlock.isWasteFluidFace(neighborState, machineFace);
    }
}
