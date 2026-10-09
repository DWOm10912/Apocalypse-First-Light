package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.LightPoleBaseBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
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

/**
 * Site Lighting V1 (docs/models/site_lighting_v1.md, tools/build-site-lighting-v1.mjs): the foot of a parking-lot light
 * pole. A full-cell concrete pad (it hides the feed cable's cell below), a 600 mm cast pier 0.75 m tall, four anchor bolts,
 * the base plate and the pole's first metre. FACING (toward the player who placed it) is the side the hand hole in the
 * segment above shows. The power port is the bottom face (an underground cable, docs/models/power_cable_v2.md); the block
 * entity holds the pole's power and switches its head ({@link AreaLightBlock}) and the hidden light points under it. On a curb
 * cell (the base's pier sits in the middle, the curb's lip runs along the cell's road edge) the cable comes from under the
 * curb (PowerCableBlock#throughCurb); placing or removing the base links or unlinks that cable.
 */
public class LightPoleBaseBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock {
    // px, tools/build-site-lighting-v1.mjs BASE (pad 25 mm, pier r 300 to 750 mm, plate 300 mm at 780..805, pole 127 mm)
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 0.4, 16), Block.box(3.2, 0, 3.2, 12.8, 12, 12.8),
            Block.box(5.6, 12, 5.6, 10.4, 12.9, 10.4), LightPoleBlock.SHAPE);

    public LightPoleBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The one port: the bottom face, where the underground cable comes up into the pier. */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightPoleBaseBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<LightPoleBaseBlockEntity>) (tickerLevel, tickerPos, tickerState, base) -> base.serverTick();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) linkThroughCurb(level, pos, true);
    }

    /** The hidden light points go with the base (a pole taken down leaves no light hanging in the air). */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof LightPoleBaseBlockEntity base) base.clearGlows();
            if (!level.isClientSide) linkThroughCurb(level, pos, false);
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    /** A base on a curb: the cable under the curb links up into it, or lets go (the cable's own neighbour is the curb). */
    private static void linkThroughCurb(Level level, BlockPos pos, boolean on) {
        BlockPos curb = pos.below(), cable = curb.below();
        if (!(level.getBlockState(curb).getBlock() instanceof CurbBlock)) return;
        BlockState state = level.getBlockState(cable);
        if (state.is(AflBlocks.POWER_CABLE.get()) && state.getValue(PipeBlock.UP) != on) level.setBlock(cable, state.setValue(PipeBlock.UP, on), Block.UPDATE_ALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
