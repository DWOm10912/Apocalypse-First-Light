package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Metal Panel Jamb V1 (docs/models/metal_wall_panel_v1.md): the 0.3 m side plate of an entry portal, 5 px thick against one
 * edge of the cell (FACING = that edge), in the Metal Wall Panel's planks. Placement: against a plate, the same edge (a run
 * or a stack); against the side of another block, the edge touching it; on a floor or ceiling, the edge nearest the point
 * aimed at (the far edge when aiming at the middle).
 * EYEBROW (Metal Eyebrow Canopy V1, docs/models/metal_eyebrow_canopy_v1.md): when a canopy sits next to the plate's inner
 * side facing along the plate (the facing's clockwise or counter-clockwise neighbour), the rest of the cell shows as that
 * canopy, so a canopy run continues through an entry portal between its jambs.
 */
public class MetalPanelJambBlock extends HorizontalDirectionalBlock {
    public static final int THICK = 5;
    public static final EnumProperty<Eyebrow> EYEBROW = EnumProperty.create("eyebrow", Eyebrow.class);
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, THICK),
            Direction.SOUTH, Block.box(0, 0, 16 - THICK, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, THICK, 16, 16),
            Direction.EAST, Block.box(16 - THICK, 0, 0, 16, 16, 16)));
    private static final Map<BlockState, VoxelShape> SHAPES = new HashMap<>();

    public enum Eyebrow implements StringRepresentable {
        NONE("none"), CW("cw"), CCW("ccw");
        private final String name;
        Eyebrow(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public MetalPanelJambBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EYEBROW, Eyebrow.NONE));
        for (BlockState s : stateDefinition.getPossibleStates()) {
            Direction f = s.getValue(FACING);
            Eyebrow e = s.getValue(EYEBROW);
            // the canopy part in the canopy's own frame: the plate on its west (counter-clockwise) or east side
            SHAPES.put(s, e == Eyebrow.NONE ? PLATES.get(f) : Shapes.or(PLATES.get(f), e == Eyebrow.CW
                    ? MetalEyebrowCanopyBlock.run(f.getClockWise(), THICK, 16) : MetalEyebrowCanopyBlock.run(f.getCounterClockWise(), 0, 16 - THICK)));
        }
    }

    /** The facing of the canopy this state carries through its cell, or null. */
    public static Direction eyebrowFacing(BlockState state) {
        if (!(state.getBlock() instanceof MetalPanelJambBlock)) return null;
        return switch (state.getValue(EYEBROW)) {
            case CW -> state.getValue(FACING).getClockWise();
            case CCW -> state.getValue(FACING).getCounterClockWise();
            default -> null;
        };
    }

    private static Eyebrow eyebrowOf(BlockState inner, Direction facing) {
        if (!(inner.getBlock() instanceof MetalEyebrowCanopyBlock)) return Eyebrow.NONE;
        Direction f = inner.getValue(FACING);
        return f == facing.getClockWise() ? Eyebrow.CW : f == facing.getCounterClockWise() ? Eyebrow.CCW : Eyebrow.NONE;
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
        return defaultBlockState().setValue(FACING, edge)
                .setValue(EYEBROW, eyebrowOf(context.getLevel().getBlockState(pos.relative(edge.getOpposite())), edge));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite()) return state.setValue(EYEBROW, eyebrowOf(neighborState, facing));
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state);
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
        if (mirror == Mirror.NONE) return state;
        Eyebrow e = state.getValue(EYEBROW);
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(EYEBROW, e == Eyebrow.CW ? Eyebrow.CCW : e == Eyebrow.CCW ? Eyebrow.CW : e);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EYEBROW);
    }
}
