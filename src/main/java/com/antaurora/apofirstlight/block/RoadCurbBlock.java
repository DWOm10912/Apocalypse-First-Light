package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Static curb: full lower support plus a 4px wide, 3px raised lip; facing points toward asphalt. */
public final class RoadCurbBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty LAYERS = RoadSurfaceBlock.LAYERS;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    private static final VoxelShape[][][] SHAPES = new VoxelShape[16][4][4];

    public enum Shape implements StringRepresentable {
        STRAIGHT("straight"), INNER("inner"), OUTER("outer"), DRIVEWAY("driveway");
        private final String name;
        Shape(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    static {
        for (int layer = 1; layer <= 16; layer++) {
            for (Shape shape : Shape.values()) {
                for (int turns = 0; turns < 4; turns++) {
                    int base = Math.max(0, layer - 3);
                    VoxelShape result = base == 0 ? Shapes.empty() : Block.box(0, 0, 0, 16, base, 16);
                    if (shape == Shape.DRIVEWAY) result = Block.box(0, 0, 0, 16, layer, 16);
                    else if (shape == Shape.OUTER) result = Shapes.or(result, rotatedBox(12, base, 12, 16, layer, 16, turns));
                    else {
                        result = Shapes.or(result, rotatedBox(0, base, 12, 16, layer, 16, turns));
                        if (shape == Shape.INNER) result = Shapes.or(result, rotatedBox(12, base, 0, 16, layer, 12, turns));
                    }
                    SHAPES[layer - 1][shape.ordinal()][turns] = result.optimize();
                }
            }
        }
    }

    public RoadCurbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 16).setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, Shape.STRAIGHT));
    }

    private static VoxelShape rotatedBox(int x0, int y0, int z0, int x1, int y1, int z1, int turns) {
        for (int i = 0; i < turns; i++) {
            int previousX0 = x0, previousX1 = x1;
            x0 = 16 - z1; x1 = 16 - z0;
            z0 = previousX0; z1 = previousX1;
        }
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    private static int turns(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1][state.getValue(SHAPE).ordinal()][turns(state.getValue(FACING))];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        Direction facing = mirror.mirror(state.getValue(FACING));
        if (state.getValue(SHAPE) == Shape.INNER || state.getValue(SHAPE) == Shape.OUTER) facing = facing.getClockWise();
        return state.setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, FACING, SHAPE);
    }
}
