package com.antaurora.apofirstlight.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Restroom material variant of the office partition; connections remain self-only. */
public final class RestroomPartitionBlock extends OfficeCubiclePartitionBlock {
    public static final IntegerProperty DOOR_SUPPORT = IntegerProperty.create("door_support",0,15);
    public RestroomPartitionBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DOOR_SUPPORT,0));
    }
    public static int bit(Direction d){return switch(d){case NORTH->1;case SOUTH->2;case EAST->4;case WEST->8;default->0;};}
    private BlockState attachments(BlockState state,LevelAccessor level,BlockPos pos){
        if(!state.is(this))return state;
        int mask=0;
        for(Direction d:Direction.Plane.HORIZONTAL)if(RestroomStallDoorBlock.supports(level.getBlockState(pos.relative(d)),d))mask|=bit(d);
        return state.setValue(DOOR_SUPPORT,mask);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        BlockState s=super.getStateForPlacement(c);return s==null?null:attachments(s,c.getLevel(),c.getClickedPos());
    }
    @Override public BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
        return attachments(super.updateShape(s,d,n,l,p,np),l,p);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(DOOR_SUPPORT);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        if(s.getValue(DOOR_SUPPORT)==0)return super.getShape(s,l,p,c);
        return RestroomDoorwayShapes.get(connectionMask(s),s.getValue(DOOR_SUPPORT));
    }
    @Override public BlockState rotate(BlockState s,net.minecraft.world.level.block.Rotation r){
        BlockState result=super.rotate(s,r);int mask=0;
        for(Direction d:Direction.Plane.HORIZONTAL)if((s.getValue(DOOR_SUPPORT)&bit(d))!=0)mask|=bit(r.rotate(d));
        return result.setValue(DOOR_SUPPORT,mask);
    }
    @Override public BlockState mirror(BlockState s,net.minecraft.world.level.block.Mirror m){
        BlockState result=super.mirror(s,m);int mask=0;
        for(Direction d:Direction.Plane.HORIZONTAL)if((s.getValue(DOOR_SUPPORT)&bit(d))!=0)mask|=bit(m.mirror(d));
        return result.setValue(DOOR_SUPPORT,mask);
    }
}
