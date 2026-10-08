package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.EmergencyLightBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Emergency Light (应急灯, Building Lights V1, docs/models/building_lights_v1.md): a two-head battery unit on a wall, 1.5 x
 * real size (FACING = away from the wall). On the building's lighting circuit like the ceiling lights: while it has power
 * the unit charges and shows its green charge LED (MODE charging); when the power goes it lights its heads from the
 * battery (MODE on, light 10) until the battery is flat (MODE off). The battery is in the block entity. No interaction
 * (the test button was taken out, user 2026-10-08). A small plastic fitting: hand-breakable, drops itself, no collision.
 */
public class EmergencyLightBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Mode implements StringRepresentable {
        OFF("off"), CHARGING("charging"), ON("on");
        private final String name;
        Mode(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    // tools/build-building-lights-v1.mjs SELECTION.emergency
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(Block.box(4.3, 5.9, 14.0, 11.7, 10.0, 16));

    public EmergencyLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, Mode.OFF));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EmergencyLightBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.EMERGENCY_LIGHT.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<EmergencyLightBlockEntity>) (l, p, s, light) -> light.serverTick();
    }
}
