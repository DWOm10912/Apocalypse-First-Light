package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.energy.BuildingLights;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Keyless porcelain lampholder (无开关瓷灯座, Building Lights V1 F; Building Wiring V1 phase 3, forest start home concept A,
 * docs/models/building_lights_v1.md): the pull-chain lampholder's porcelain and frosted 60 W A19 bulb without the chain, on a
 * ceiling (FACING down) or a wall (FACING away from it). It has no switch of its own: a wall switch works it over its light
 * group (its tap's group in the panel; Building Wiring V1 phase 3). LIT while its circuit has power and its group is on
 * ({@link BuildingLightBlock}); {@link BuildingLights#BULB_60W_PERIOD} FE a period. A wall lamp finds a LEGACY_AREA footprint
 * through the wall behind it. Light 14. Hand-breakable, drops itself, no collision; on a round log wall it stands on the
 * lampholder's pad (kind 2, {@link LogMount}).
 */
public class KeylessLampholderBlock extends BuildingLightBlock implements LogMount.Padded {
    public static final DirectionProperty FACING = PorcelainLampholderBlock.FACING;
    // px, tools/build-building-lights-v1.mjs SELECTION keylessCeiling / keylessWall
    private static final VoxelShape CEILING = Block.box(7.16, 13.6, 7.16, 8.84, 16, 8.84);
    private static final VoxelShape WALL_NORTH = Block.box(7.16, 5.88, 13.6, 8.84, 7.56, 16);
    private static final Map<Direction, VoxelShape> WALL = HorizontalShapeUtils.rotations(WALL_NORTH),
            WALL_ON_LOGS = LogMount.rotationsOnPad(WALL_NORTH, LogMount.PAD_LAMPHOLDER);

    public KeylessLampholderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, false).setValue(LogMount.LOG, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) return null;
        BlockState state = LogMount.placed(defaultBlockState().setValue(FACING, face), context.getLevel(), context.getClickedPos(), face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return LogMount.updated(super.updateShape(state, direction, neighbor, level, pos, neighborPos), direction, neighbor, state.getValue(FACING));
    }

    @Override
    protected int periodEnergy() {
        return BuildingLights.BULB_60W_PERIOD;
    }

    @Override
    protected BlockPos wiringPosition(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? pos : pos.relative(facing.getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? CEILING : (LogMount.mounted(state) ? WALL_ON_LOGS : WALL).get(facing);
    }

    @Override
    public int logPad(BlockState state) {
        return state.getValue(FACING) == Direction.DOWN ? 0 : LogMount.PAD_LAMPHOLDER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, LogMount.LOG);
    }
}
