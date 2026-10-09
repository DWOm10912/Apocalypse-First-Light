package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
 * Power Cable V2: a bundled cable connecting in six directions to other cables and to AFL power ports
 * ({@link AflPowerPortBlock}). The block state only records which sides connect; the client model
 * (client/PowerCableBakedModel) picks straight runs, bends, junction boxes, end caps and plugs from them. FE transfer is
 * energy/PowerCableTransfer (unchanged). Old V1 states carried a {@code show_core} property, which is dropped on load.
 * <p>
 * Cable-to-cable links (2026-10-01) are chosen, not automatic, so parallel runs stay apart: a new cable links to the
 * cable it was placed against and to line ends it continues ({@link #getStateForPlacement}); a cable never changes a
 * link to a neighbouring cable on its own, it mirrors that neighbour's side. Sneaking with an empty main hand,
 * a right click cuts or joins one cable-to-cable side ({@link #toggleSide}; PowerCableToggleEvents lets the click through).
 * Power ports always connect.
 */
public final class PowerCableBlock extends PipeBlock {
    private static final double ARM = 2.2;       // half thickness of a run (the bundle is 3.7 px across)
    private static final double BOX = 2.6;       // junction box half size (tools/build-power-cable-v2.mjs BOX)
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

    /**
     * Links of a new cable: power ports always; the cable it was placed against; every neighbouring line end it continues
     * straight (a lone cable, or a cable whose single link points away from here); and, only when no cable was linked
     * that way, the neighbouring line ends it turns a corner onto (this also closes a loop; a cable whose only link is a
     * power port is extended straight out of the port only). Cables of other runs that already have two or more links
     * are left alone, so parallel runs do not merge. Checked offline against straight runs, corners, loops, two runs
     * from neighbouring ports laid row by row or alternately, and branches.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // the block the cable was placed against; none when the click replaced a replaceable block in place
        BlockPos against = context.replacingClickedOnBlock() ? null : pos.relative(context.getClickedFace().getOpposite());
        BlockState state = defaultBlockState();
        boolean cableLinked = false;
        int corners = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            boolean connect;
            if (!neighborState.is(this)) {
                connect = isUtilityPortFace(neighborState, direction.getOpposite()) || throughCurb(level, neighborPos, neighborState, direction);
            } else {
                int links = links(neighborState);
                Direction only = links == 1 ? firstLink(neighborState) : null;
                connect = neighborPos.equals(against) || links == 0 || only == direction;
                cableLinked |= connect;
                if (!connect && only != null && level.getBlockState(neighborPos.relative(only)).is(this))
                    corners |= 1 << direction.ordinal();
            }
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connect);
        }
        if (!cableLinked) {
            for (Direction direction : Direction.values())
                if ((corners & 1 << direction.ordinal()) != 0) state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), true);
        }
        return state;
    }

    @Nullable
    private static Direction firstLink(BlockState state) {
        for (Direction direction : Direction.values()) if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) return direction;
        return null;
    }

    /** A neighbouring cable's side is mirrored (both ends always agree); power ports connect whenever present (also through a curb). */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean connect = neighborState.is(this)
                ? neighborState.getValue(PROPERTY_BY_DIRECTION.get(direction.getOpposite()))
                : isUtilityPortFace(neighborState, direction.getOpposite()) || throughCurb(level, neighborPos, neighborState, direction);
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connect);
    }

    /** Sneak + right click with an empty main hand: cut or join the aimed cable-to-cable side. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!canToggle(player, hand)) return InteractionResult.PASS;
        Direction side = toggleSide(state, pos, hit.getLocation(), hit.getDirection());
        if (!level.getBlockState(pos.relative(side)).is(this)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // the neighbour follows through updateShape
        level.setBlock(pos, state.setValue(PROPERTY_BY_DIRECTION.get(side), !state.getValue(PROPERTY_BY_DIRECTION.get(side))),
                UPDATE_ALL);
        return InteractionResult.CONSUME;
    }

    public static boolean canToggle(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty() && player.isSecondaryUseActive();
    }

    /**
     * The side a toggle click means: on an arm (beyond the centre core along an axis), that arm's direction; on the core,
     * the clicked face, so a side without an arm can be joined by clicking the core's face toward it.
     */
    public static Direction toggleSide(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
        double core = (isJunction(state) ? BOX : ARM) / 16.0 + 0.003;
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

    private static int links(BlockState state) {
        int count = 0;
        for (Direction direction : Direction.values()) if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) count++;
        return count;
    }

    private static boolean isJunction(BlockState state) {
        int count = links(state);
        return count == 0 || count >= 3;
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

    /**
     * Site Lighting V1 (2026-10-09): a cable under a curb feeds the power port on top of the curb, as a conduit runs under the
     * curb into a light pole's pier. The side up into a curb ({@code curbPos}) connects when the block standing on the curb
     * has a port in its bottom face (a light pole base on the lot's curb row); PowerCableTransfer then takes that block as the
     * endpoint. Only curbs: any other block between still breaks the line.
     */
    public static boolean throughCurb(BlockGetter level, BlockPos curbPos, BlockState curbState, Direction direction) {
        return direction == Direction.UP && curbState.getBlock() instanceof CurbBlock
                && isUtilityPortFace(level.getBlockState(curbPos.above()), Direction.DOWN);
    }

    /** Client prompt: the cable side a sneak toggle would change, if that side leads to another cable. */
    @Nullable
    public static Direction promptToggleSide(BlockGetter level, BlockPos pos, BlockState state, Vec3 hit, Direction face) {
        Direction side = toggleSide(state, pos, hit, face);
        return level.getBlockState(pos.relative(side)).is(AflBlocks.POWER_CABLE.get()) ? side : null;
    }
}
