package com.antaurora.apofirstlight.dev.highwaymesh;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** 32 finite axis-aligned collision states. Invisible, no item/loot/BE and no entity-physics patch. */
public final class DevRoadSurfaceBlock extends Block {
    public static final IntegerProperty LAYERS=IntegerProperty.create("layers",1,32);
    private static final VoxelShape[] SHAPES=new VoxelShape[32];
    static {for(int i=0;i<32;i++)SHAPES[i]=Block.box(0,0,0,16,(i+1)*.5,16);}
    public DevRoadSurfaceBlock(){super(Properties.of().strength(-1,3600000).noLootTable().noOcclusion()
            .isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));registerDefaultState(stateDefinition.any().setValue(LAYERS,32));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LAYERS);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES[s.getValue(LAYERS)-1];}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return getShape(s,l,p,c);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    @Override public int getLightBlock(BlockState s,BlockGetter l,BlockPos p){return 0;}
    @Override public float getShadeBrightness(BlockState s,BlockGetter l,BlockPos p){return 1;}
    @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
}
