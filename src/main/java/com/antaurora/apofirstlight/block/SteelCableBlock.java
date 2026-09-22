package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Direction points uphill. Segment combines slope and phase, avoiding unused combinations. */
public final class SteelCableBlock extends Block {
    public enum Segment implements StringRepresentable {
        VERTICAL(0,0), R1(1,0), R2_0(2,0), R2_1(2,1), R3_0(3,0), R3_1(3,1), R3_2(3,2);
        public final int run, phase;
        Segment(int run,int phase) { this.run=run; this.phase=phase; }
        public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Segment of(int run,int phase) {
            for(var s:values()) if(s.run==run && s.phase==phase)return s;
            throw new IllegalArgumentException("Cable segment");
        }
    }
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Segment> SEGMENT=EnumProperty.create("segment",Segment.class);
    // V1.1C rigid model inset: same slope/phase/owned block, endpoint inside existing structure.
    public static final double SLOPED_INSET = 0.5;
    public static final double SLOPED_DROP = 0.25;
    private static final VoxelShape[][] SHAPES=new VoxelShape[7][4];
    static {
        for(var s:Segment.values())for(var d:Direction.Plane.HORIZONTAL) {
            VoxelShape shape=Shapes.empty();
            if(s==Segment.VERTICAL)shape=Block.box(7,0,7,9,16,9);
            else for(int i=0;i<16;i++) {
                double lo=(s.phase+i/16.0)*16/s.run-1-SLOPED_DROP*16;
                double hi=(s.phase+(i+1)/16.0)*16/s.run+1-SLOPED_DROP*16;
                double q=i+SLOPED_INSET*16;
                shape=Shapes.or(shape,switch(d) {
                    case EAST -> Block.box(q,lo,7,q+1,hi,9);
                    case WEST -> Block.box(15-q,lo,7,16-q,hi,9);
                    case SOUTH -> Block.box(7,lo,q,9,hi,q+1);
                    default -> Block.box(7,lo,15-q,9,hi,16-q);
                });
            }
            SHAPES[s.ordinal()][d.get2DDataValue()]=shape.optimize();
        }
    }
    public SteelCableBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SEGMENT,Segment.VERTICAL));
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING,SEGMENT); }
    public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState(); }
    public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {
        return SHAPES[s.getValue(SEGMENT).ordinal()][s.getValue(FACING).get2DDataValue()];
    }
    public BlockState rotate(BlockState s,Rotation r) { return s.setValue(FACING,r.rotate(s.getValue(FACING))); }
    public BlockState mirror(BlockState s,Mirror m) { return rotate(s,m.getRotation(s.getValue(FACING))); }
}
