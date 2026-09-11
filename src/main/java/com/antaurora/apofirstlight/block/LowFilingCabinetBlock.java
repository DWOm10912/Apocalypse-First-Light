package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Static, horizontally facing two-drawer office filing cabinet. */
public final class LowFilingCabinetBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape BODY_NORTH = Shapes.or(
            Block.box(0.2, 0.0, 1.0, 15.8, 0.9, 16.0),
            Block.box(0.0, 0.9, 1.0, 16.0, 16.0, 16.0)
    ).optimize();
    private static final VoxelShape DRAWER_1_HANDLE_NORTH =
            Block.box(5.0, 3.65, 0.0, 11.0, 4.75, 1.15);
    private static final VoxelShape DRAWER_2_HANDLE_NORTH =
            Block.box(5.0, 11.05, 0.0, 11.0, 12.15, 1.15);
    private static final VoxelShape NORTH = Shapes.or(
            BODY_NORTH, DRAWER_1_HANDLE_NORTH, DRAWER_2_HANDLE_NORTH).optimize();
    private static final Map<Direction, VoxelShape> BODY_SHAPES = HorizontalShapeUtils.rotations(BODY_NORTH);
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH);
    private static final List<Map<Direction, VoxelShape>> DRAWER_HANDLE_SHAPES = List.of(
            HorizontalShapeUtils.rotations(DRAWER_1_HANDLE_NORTH),
            HorizontalShapeUtils.rotations(DRAWER_2_HANDLE_NORTH)
    );

    public LowFilingCabinetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        BlockPos floor = position.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        return direction == Direction.DOWN && !state.canSurvive(level, position)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return getShape(state, level, position, context);
    }

    /** Returns the isolated handle shape for a one-based drawer number. */
    public static VoxelShape drawerHandleShape(BlockState state, int drawerNumber) {
        if (drawerNumber < 1 || drawerNumber > DRAWER_HANDLE_SHAPES.size()) return Shapes.empty();
        return DRAWER_HANDLE_SHAPES.get(drawerNumber - 1).get(state.getValue(FACING));
    }

    /** Returns the cabinet body without any drawer handles. */
    public static VoxelShape cabinetBodyShape(BlockState state) {
        return BODY_SHAPES.get(state.getValue(FACING));
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
