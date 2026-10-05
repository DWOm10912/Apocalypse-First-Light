package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.SubmersibleFuelPumpBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.fluid.AflFluidPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Submersible Fuel Pump V1 (2026-10-05, tools/build-fuel-station-sump-v1.mjs, docs/models/fuel_station_sump_v1.md): the
 * pump head in its sump, set on an Underground Fuel Tank's port (the block under it: any block entity handing out a fluid
 * handler on its top face). FACING is the product outlet: the AFL fluid port on that face; the AFL power port is on the
 * opposite face (the junction box). Placed with the outlet the way the player faces. It runs whenever its FE buffer pays
 * for the tick and there is somewhere for the fuel to go (SubmersibleFuelPumpBlockEntity); no switch yet (the dispenser
 * will run it later).
 */
public final class SubmersibleFuelPumpBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock, AflFluidPortBlock {
    /** North-facing (outlet toward -Z) block px: sump walls, head, outlet neck and port, junction box and port. */
    private static final VoxelShape NORTH = Shapes.or(
            Block.box(0, 0, 0, 16, 16, 0.6), Block.box(0, 0, 15.4, 16, 16, 16),
            Block.box(0, 0, 0, 0.6, 16, 16), Block.box(15.4, 0, 0, 16, 16, 16),
            Block.box(2.6, 0, 2.6, 13.4, 11.2, 13.4),
            Block.box(2.4, 2.4, 0.6, 13.6, 13.6, 1.8),
            Block.box(5.4, 5.2, 12.2, 10.6, 10.8, 15.4)).optimize();
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH);

    public SubmersibleFuelPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** The power port: the face opposite the outlet, on the junction box (docs/models/power_cable_v2.md). */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    /** The fluid port: the outlet face (docs/models/fluid_pipe_v2.md, "流体接口规格"). */
    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new SubmersibleFuelPumpBlockEntity(position, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SubmersibleFuelPumpBlockEntity>) (tickerLevel, tickerPos, tickerState, pump) -> pump.serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
