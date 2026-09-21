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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RoadMarkingBlock extends HorizontalDirectionalBlock {
    /** 0 keeps all existing cardinal models. 1..15 are NESW arms; 16 is an isolated ribbon dot. */
    // 17..31: NESW boundary strips (16 + mask), matching the legacy edge model's 2px inset.
    // 33..47: NE/SE/SW/NW corner joins for concave boundary vertices; 32 reserved.
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 47);
    public static final IntegerProperty RISES = IntegerProperty.create("rises", 0, 15);
    private static final VoxelShape[] RIBBON_SHAPES = ribbonShapes();
    public enum MarkingType {
        EDGE,
        DIVIDER
    }

    private static final VoxelShape EDGE_EAST_SHAPE = Block.box(14.0, 0.0, 0.0,
            16.0, 0.75, 16.0);
    private static final VoxelShape EDGE_SOUTH_SHAPE = rotateY90(EDGE_EAST_SHAPE);
    private static final VoxelShape EDGE_WEST_SHAPE = rotateY90(EDGE_SOUTH_SHAPE);
    private static final VoxelShape EDGE_NORTH_SHAPE = rotateY90(EDGE_WEST_SHAPE);

    private static final VoxelShape DIVIDER_CANONICAL_SHAPE = Block.box(
            7.0, 0.0, 2.0, 9.0, 0.75, 14.0);
    private static final VoxelShape DIVIDER_TURNED_SHAPE = rotateY90(DIVIDER_CANONICAL_SHAPE);

    private final MarkingType markingType;

    public RoadMarkingBlock(Properties properties, MarkingType markingType) {
        super(properties);
        this.markingType = markingType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONNECTIONS, 0).setValue(RISES, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
                .setValue(CONNECTIONS, transformMask(state.getValue(CONNECTIONS), rotation::rotate))
                .setValue(RISES, transformMask(state.getValue(RISES), rotation::rotate));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(CONNECTIONS, transformMask(state.getValue(CONNECTIONS), mirror::mirror))
                .setValue(RISES, transformMask(state.getValue(RISES), mirror::mirror));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, CONNECTIONS, RISES);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(CONNECTIONS) != 0) {
            VoxelShape shape = RIBBON_SHAPES[state.getValue(CONNECTIONS)];
            int rises = state.getValue(RISES);
            if ((rises & 1) != 0) shape = Shapes.or(shape, Block.box(7,0,0,9,16,.05));
            if ((rises & 2) != 0) shape = Shapes.or(shape, Block.box(15.95,0,7,16,16,9));
            if ((rises & 4) != 0) shape = Shapes.or(shape, Block.box(7,0,15.95,9,16,16));
            if ((rises & 8) != 0) shape = Shapes.or(shape, Block.box(0,0,7,.05,16,9));
            return shape;
        }
        if (markingType == MarkingType.DIVIDER) {
            return switch (state.getValue(FACING)) {
                case EAST, WEST -> DIVIDER_TURNED_SHAPE;
                case NORTH, SOUTH -> DIVIDER_CANONICAL_SHAPE;
                default -> DIVIDER_CANONICAL_SHAPE;
            };
        }
        return switch (state.getValue(FACING)) {
            case EAST -> EDGE_EAST_SHAPE;
            case SOUTH -> EDGE_SOUTH_SHAPE;
            case WEST -> EDGE_WEST_SHAPE;
            case NORTH -> EDGE_NORTH_SHAPE;
            default -> EDGE_EAST_SHAPE;
        };
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.empty();
    }

    private static VoxelShape rotateY90(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(
                rotated[0], Block.box((1.0 - maxZ) * 16.0, minY * 16.0, minX * 16.0,
                        (1.0 - minZ) * 16.0, maxY * 16.0, maxX * 16.0)));
        return rotated[0];
    }

    private static int transformMask(int mask, java.util.function.UnaryOperator<Direction> transform) {
        if (mask == 0 || mask == 16) return mask;
        if (mask >= 32) {
            Direction[] sides = {Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
            int result=32;
            for(int i=0;i<4;i++)if(((mask-32)&(1<<i))!=0) {
                var a=transform.apply(sides[i]);var b=transform.apply(sides[(i+1)%4]);
                for(int j=0;j<4;j++)if((sides[j]==a&&sides[(j+1)%4]==b)||(sides[j]==b&&sides[(j+1)%4]==a))result|=1<<j;
            }
            return result;
        }
        int boundary = mask > 16 ? 16 : 0;
        mask -= boundary;
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int result = 0;
        for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) {
            Direction rotated = transform.apply(directions[i]);
            for (int j = 0; j < 4; j++) if (directions[j] == rotated) result |= 1 << j;
        }
        return result + boundary;
    }
    private static VoxelShape[] ribbonShapes() {
        VoxelShape[] shapes = new VoxelShape[48];
        for (int mask = 1; mask <= 16; mask++) {
            VoxelShape shape = Block.box(7, 0, 7, 9, .75, 9);
            if ((mask & 1) != 0) shape = Shapes.or(shape, Block.box(7, 0, 0, 9, .75, 7));
            if ((mask & 2) != 0) shape = Shapes.or(shape, Block.box(9, 0, 7, 16, .75, 9));
            if ((mask & 4) != 0) shape = Shapes.or(shape, Block.box(7, 0, 9, 9, .75, 16));
            if ((mask & 8) != 0) shape = Shapes.or(shape, Block.box(0, 0, 7, 7, .75, 9));
            shapes[mask] = shape;
        }
        for (int mask = 1; mask < 16; mask++) {
            VoxelShape shape = Shapes.empty();
            if ((mask & 1) != 0) shape = Shapes.or(shape, Block.box(0,0,0,16,.75,2));
            if ((mask & 2) != 0) shape = Shapes.or(shape, Block.box(14,0,0,16,.75,16));
            if ((mask & 4) != 0) shape = Shapes.or(shape, Block.box(0,0,14,16,.75,16));
            if ((mask & 8) != 0) shape = Shapes.or(shape, Block.box(0,0,0,2,.75,16));
            shapes[16 + mask] = shape;
        }
        for(int mask=0;mask<16;mask++) {
            VoxelShape shape=Shapes.empty();
            if((mask&1)!=0)shape=Shapes.or(shape,Block.box(14,0,0,16,.75,2));
            if((mask&2)!=0)shape=Shapes.or(shape,Block.box(14,0,14,16,.75,16));
            if((mask&4)!=0)shape=Shapes.or(shape,Block.box(0,0,14,2,.75,16));
            if((mask&8)!=0)shape=Shapes.or(shape,Block.box(0,0,0,2,.75,2));
            shapes[32+mask]=shape;
        }
        return shapes;
    }
}
