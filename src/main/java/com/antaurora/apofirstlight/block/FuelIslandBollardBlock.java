package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fuel Island Kit V1 bollard (tools/build-fuel-island-v1.mjs): a grey galvanised steel pipe, 1 m high, domed, bolted down,
 * three reflective sleeves. Its own block, set on the ground or on an island piece: on a curb or an end (ON_CURB, kept up
 * to date from the block below) it stands in the cell above and its model sinks 13 px onto the curb top; the collision
 * shape then only covers this cell's part of the pipe (the player stands on the curb, so the pipe still blocks the body),
 * the outline the whole pipe. Needs an island piece or a sturdy centre below.
 */
public class FuelIslandBollardBlock extends Block {
    public static final BooleanProperty ON_CURB = BooleanProperty.create("on_curb");
    private static final double SINK = 13.0, TOP = 16.58;
    private static final VoxelShape GROUND = Shapes.or(Block.box(6.55, 0, 6.55, 9.45, TOP, 9.45), Block.box(5.4, 0, 5.4, 10.6, 0.5, 10.6));
    private static final VoxelShape CURB_OUTLINE = Shapes.or(Block.box(6.55, -SINK, 6.55, 9.45, TOP - SINK, 9.45), Block.box(5.4, -SINK, 5.4, 10.6, 0.5 - SINK, 10.6));
    private static final VoxelShape CURB_COLLISION = Block.box(6.55, 0, 6.55, 9.45, TOP - SINK, 9.45);

    public FuelIslandBollardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ON_CURB, false));
    }

    private static boolean onCurb(BlockGetter level, BlockPos pos) {
        return FuelIslandCurbBlock.isIslandPiece(level.getBlockState(pos.below()));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ON_CURB, onCurb(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return onCurb(level, pos) || Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction != Direction.DOWN) return state;
        if (!state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return state.setValue(ON_CURB, onCurb(level, pos));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_CURB) ? CURB_OUTLINE : GROUND;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_CURB) ? CURB_COLLISION : GROUND;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ON_CURB);
    }
}
