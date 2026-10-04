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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Small static desktop prop with four-way facing and the office desk's 2.5-unit visual sink.
 * It does not attach itself to the desk or create any companion blocks. Office Props V2: the outline is one box around
 * the whole prop (no stepped outline); collision may follow the mesh more closely (the computer station).
 */
public final class OfficeDesktopDecorationBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty LOWERED = BooleanProperty.create("lowered");
    private static final double DESK_SINK = -2.5D / 16.0D;

    private final Map<Direction, VoxelShape> normalShapes;
    private final Map<Direction, VoxelShape> loweredShapes;
    private final Map<Direction, VoxelShape> normalCollision;
    private final Map<Direction, VoxelShape> loweredCollision;

    public OfficeDesktopDecorationBlock(Properties properties, VoxelShape northShape) {
        this(properties, northShape, northShape);
    }

    public OfficeDesktopDecorationBlock(Properties properties, VoxelShape northShape, VoxelShape northCollision) {
        super(properties);
        normalShapes = HorizontalShapeUtils.rotations(northShape);
        loweredShapes = HorizontalShapeUtils.rotations(northShape.move(0.0D, DESK_SINK, 0.0D));
        normalCollision = HorizontalShapeUtils.rotations(northCollision);
        loweredCollision = HorizontalShapeUtils.rotations(northCollision.move(0.0D, DESK_SINK, 0.0D));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LOWERED, false));
    }

    /** Outline: one box around monitor, keyboard and mouse. */
    public static VoxelShape computerStationShape() {
        return Block.box(0.0D, 0.0D, 4.3D, 16.0D, 12.75D, 13.6D);
    }

    /** Collision: keyboard + mouse (front, mouse on the NORTH user's right) and the monitor behind them. */
    public static VoxelShape computerStationCollision() {
        return Shapes.or(
                Block.box(0.25D, 0.0D, 4.3D, 15.4D, 0.95D, 8.0D),
                Block.box(0.0D, 0.0D, 8.8D, 16.0D, 12.75D, 13.6D)
        ).optimize();
    }

    public static VoxelShape keyboardShape() {
        return Block.box(1.8D, 0.0D, 6.2D, 14.2D, 0.95D, 9.75D);
    }

    public static VoxelShape mouseShape() {
        return Block.box(6.95D, 0.0D, 6.55D, 9.05D, 1.0D, 9.95D);
    }

    private static boolean isDesk(LevelReader level, BlockPos position) {
        return level.getBlockState(position.below()).getBlock() instanceof ModernOfficeDeskBlock;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LOWERED, isDesk(context.getLevel(), context.getClickedPos()));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        if (isDesk(level, position)) return true;
        BlockPos floor = position.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if (direction == Direction.DOWN) {
            if (!state.canSurvive(level, position)) return Blocks.AIR.defaultBlockState();
            return state.setValue(LOWERED, isDesk(level, position));
        }
        return super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return (state.getValue(LOWERED) ? loweredShapes : normalShapes).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return (state.getValue(LOWERED) ? loweredCollision : normalCollision).get(state.getValue(FACING));
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
        builder.add(FACING, LOWERED);
    }
}
