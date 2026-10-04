package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.FuelCanopyColumnBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Fuel Canopy Kit V1 column (tools/build-fuel-canopy-v1.mjs, docs/models/fuel_canopy_kit_v1.md): a dark grey steel tube
 * stacked one block a segment. SEGMENT is computed: the lowest segment (BASE) has a concrete pedestal and the power port
 * on its bottom face, where an underground cable comes up; the others are plain (SHAFT). TOP (computed) adds the head
 * plate under a canopy piece. Placed on a straight island curb the base takes that curb's place (ISLAND, FACING = the
 * curb's: it draws the curb segment, with the port in its bottom) and gives the curb back when it goes. The column carries
 * the canopy's wiring (FuelCanopyNetwork); the base's block entity takes the power and runs the network.
 */
public class FuelCanopyColumnBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock {
    public enum Segment implements StringRepresentable {
        BASE("base"), SHAFT("shaft");

        private final String name;

        Segment(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Segment> SEGMENT = EnumProperty.create("segment", Segment.class);
    public static final BooleanProperty ISLAND = BooleanProperty.create("island");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    // px, tools/build-fuel-canopy-v1.mjs COLUMN (tube r 4.5, full-cell footing 1 px, pedestal r 6.5, head plate r 5.6)
    private static final VoxelShape TUBE = Block.box(3.5, 0, 3.5, 12.5, 16, 12.5);
    private static final VoxelShape HEAD = Block.box(2.4, 14.6, 2.4, 13.6, 16, 13.6);
    private static final VoxelShape BASE_GROUND = Shapes.or(TUBE, Block.box(0, 0, 0, 16, 1, 16), Block.box(1.5, 1, 1.5, 14.5, 6.4, 14.5));
    private static final VoxelShape BASE_CURB = Shapes.or(TUBE, Block.box(0, 0, 0, 16, 3, 16), Block.box(1.5, 3, 1.5, 14.5, 9.4, 14.5));
    private static final VoxelShape[] SHAPES = {TUBE, BASE_GROUND, BASE_CURB, Shapes.or(TUBE, HEAD), Shapes.or(BASE_GROUND, HEAD), Shapes.or(BASE_CURB, HEAD)};

    public FuelCanopyColumnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SEGMENT, Segment.BASE)
                .setValue(ISLAND, false).setValue(TOP, false));
    }

    private BlockState computed(BlockGetter level, BlockPos pos, BlockState state) {
        boolean base = !(level.getBlockState(pos.below()).getBlock() instanceof FuelCanopyColumnBlock);
        return state.setValue(SEGMENT, base ? Segment.BASE : Segment.SHAFT).setValue(ISLAND, base && state.getValue(ISLAND))
                .setValue(TOP, FuelCanopyNetwork.isPart(level.getBlockState(pos.above())));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return computed(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()));
    }

    /** The base set into the straight island curb at {@code curb} (FuelCanopyColumnItem), facing as the curb. */
    public BlockState embedded(BlockGetter level, BlockPos curb, Direction facing) {
        return computed(level, curb, defaultBlockState().setValue(FACING, facing).setValue(ISLAND, true));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis() == Direction.Axis.Y ? computed(level, pos, state) : state;
    }

    // ---- power ----

    /** The one port: the base's bottom face (an underground cable, docs/models/power_cable_v2.md). */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return face == Direction.DOWN && state.getValue(SEGMENT) == Segment.BASE;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelCanopyColumnBlockEntity(pos, state);
    }

    /** Server, base only: the power buffer and, on the network's controller, the lamps. */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(SEGMENT) != Segment.BASE) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<FuelCanopyColumnBlockEntity>) (tickerLevel, tickerPos, tickerState, column) -> column.serverTick();
    }

    // ---- removal ----

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) {
            FuelCanopyNetwork.removed(level, pos);
            if (state.getValue(ISLAND) && state.getValue(SEGMENT) == Segment.BASE) restoreCurb(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    /**
     * A base set into an island gives its curb back. Queued for after the removal: putting a block into the cell being
     * removed from inside its own removal would make that removal fail, and its drop with it (as FuelDispenserBlock).
     */
    private static void restoreCurb(Level level, BlockPos pos, Direction facing) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState curb = AflBlocks.FUEL_ISLAND_CURB.get().defaultBlockState().setValue(FACING, facing);
        BlockPos at = pos.immutable();
        server.getServer().tell(new TickTask(server.getServer().getTickCount(), () -> {
            if (server.isLoaded(at) && server.getBlockState(at).isAir()) server.setBlock(at, curb, UPDATE_ALL);
        }));
    }

    // ---- shape ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int k = state.getValue(SEGMENT) == Segment.SHAFT ? 0 : state.getValue(ISLAND) ? 2 : 1;
        return SHAPES[state.getValue(TOP) ? k + 3 : k];
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEGMENT, ISLAND, TOP);
    }
}
