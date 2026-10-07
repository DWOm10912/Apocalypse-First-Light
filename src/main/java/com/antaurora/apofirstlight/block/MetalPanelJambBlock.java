package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Metal Panel Jamb V1 (docs/models/metal_wall_panel_v1.md): the 0.3 m side plate of an entry portal, 5 px thick against one
 * edge of the cell (FACING = that edge), in the Metal Wall Panel's planks. Placement: against a plate, the same edge (a run
 * or a stack); against the side of another block, the edge touching it; on a floor or ceiling, the edge nearest the point
 * aimed at (the far edge when aiming at the middle).
 */
public class MetalPanelJambBlock extends HorizontalDirectionalBlock {
    public static final int THICK = 5;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, THICK),
            Direction.SOUTH, Block.box(0, 0, 16 - THICK, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, THICK, 16, 16),
            Direction.EAST, Block.box(16 - THICK, 0, 0, 16, 16, 16)));

    public MetalPanelJambBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState against = context.getLevel().getBlockState(pos.relative(face.getOpposite()));
        Direction edge;
        if (against.is(this)) edge = against.getValue(FACING);
        else if (face.getAxis().isHorizontal()) edge = face.getOpposite();
        else {
            Vec3 hit = context.getClickLocation();
            double fx = hit.x - pos.getX() - 0.5, fz = hit.z - pos.getZ() - 0.5;
            if (Math.abs(fx) < 0.125 && Math.abs(fz) < 0.125) edge = context.getHorizontalDirection();
            else if (Math.abs(fx) > Math.abs(fz)) edge = fx > 0 ? Direction.EAST : Direction.WEST;
            else edge = fz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return defaultBlockState().setValue(FACING, edge);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
