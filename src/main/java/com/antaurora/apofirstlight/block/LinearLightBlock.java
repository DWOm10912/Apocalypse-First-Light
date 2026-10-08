package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Linear LED Light (线形灯, Building Lights V1, docs/models/building_lights_v1.md): an 80 x 70 mm white aluminium profile
 * with an opal lens, 1 m per block, on a ceiling (FACING down, the row along AXIS) or a wall (along the wall). Sections in
 * a row join: JOINED_NEG / JOINED_POS = the same light, same mounting, continues on the world's negative / positive side
 * along the row, and only the row's ends carry end caps (tools/build-building-lights-v1.mjs picks the model). A ceiling
 * light placed at the end of a row continues it; else it runs the way the player looks. LIT while the building's
 * lighting circuit has power ({@link BuildingLightBlock}), 1 FE/t per block. Aluminium fixture: pickaxe + diamond tier,
 * drops itself; no collision.
 */
public class LinearLightBlock extends BuildingLightBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", direction -> direction != Direction.UP);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty JOINED_NEG = BooleanProperty.create("joined_neg");
    public static final BooleanProperty JOINED_POS = BooleanProperty.create("joined_pos");
    // the outline (tools/build-building-lights-v1.mjs SELECTION.linear), widened to 2 px and thickened to 1.6 px for aiming
    private static final VoxelShape DOWN_X = Block.box(0, 14.4, 7, 16, 16, 9), DOWN_Z = Block.box(7, 14.4, 0, 9, 16, 16);
    private static final Map<Direction, VoxelShape> WALL = HorizontalShapeUtils.rotations(Block.box(0, 7, 14.4, 16, 9, 16));

    public LinearLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(AXIS, Direction.Axis.X)
                .setValue(JOINED_NEG, false).setValue(JOINED_POS, false).setValue(LIT, false));
    }

    /** The row's direction: AXIS on a ceiling, along the wall on a wall. */
    public static Direction.Axis runAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? state.getValue(AXIS) : facing.getClockWise().getAxis();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) return null;
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction.Axis axis = face == Direction.DOWN ? ceilingAxis(level, pos, context.getHorizontalDirection().getAxis()) : face.getClockWise().getAxis();
        BlockState state = defaultBlockState().setValue(FACING, face).setValue(AXIS, axis);
        return canSurvive(state, level, pos) ? joins(state, level, pos) : null;
    }

    /** A ceiling light next to the end of a ceiling row continues that row (the looking axis first); else the looking axis. */
    private Direction.Axis ceilingAxis(LevelReader level, BlockPos pos, Direction.Axis looking) {
        Direction.Axis other = looking == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        for (Direction.Axis axis : new Direction.Axis[]{looking, other})
            for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                BlockState n = level.getBlockState(pos.relative(Direction.get(sign, axis)));
                if (n.is(this) && n.getValue(FACING) == Direction.DOWN && n.getValue(AXIS) == axis) return axis;
            }
        return looking;
    }

    private BlockState joins(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis axis = runAxis(state);
        return state.setValue(JOINED_NEG, sameRow(state, level.getBlockState(pos.relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis)))))
                .setValue(JOINED_POS, sameRow(state, level.getBlockState(pos.relative(Direction.get(Direction.AxisDirection.POSITIVE, axis)))));
    }

    private boolean sameRow(BlockState a, BlockState b) {
        return b.is(this) && b.getValue(FACING) == a.getValue(FACING) && runAxis(b) == runAxis(a);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        if (direction.getAxis() == runAxis(state))
            return state.setValue(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? JOINED_POS : JOINED_NEG, sameRow(state, neighbor));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.DOWN) return state.getValue(AXIS) == Direction.Axis.X ? DOWN_X : DOWN_Z;
        return WALL.get(facing);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    // turning a row turns its joins with it: the neighbour that was on the negative side may now be on the positive side
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction neg = rotation.rotate(Direction.get(Direction.AxisDirection.NEGATIVE, runAxis(state)));
        return turned(state, rotation.rotate(state.getValue(FACING)), neg);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction neg = mirror.mirror(Direction.get(Direction.AxisDirection.NEGATIVE, runAxis(state)));
        return turned(state, mirror.mirror(state.getValue(FACING)), neg);
    }

    private static BlockState turned(BlockState state, Direction facing, Direction neg) {
        boolean flip = neg.getAxisDirection() == Direction.AxisDirection.POSITIVE, jn = state.getValue(JOINED_NEG), jp = state.getValue(JOINED_POS);
        return state.setValue(FACING, facing).setValue(AXIS, neg.getAxis()).setValue(JOINED_NEG, flip ? jp : jn).setValue(JOINED_POS, flip ? jn : jp);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS, JOINED_NEG, JOINED_POS, LIT);
    }
}
