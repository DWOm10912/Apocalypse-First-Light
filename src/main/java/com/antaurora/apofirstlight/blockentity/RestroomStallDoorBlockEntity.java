package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.RestroomStallDoorBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Transient motion is deliberately not persisted: reload restores the committed pose. */
public final class RestroomStallDoorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private Boolean pending;
    private long finish;
    public RestroomStallDoorBlockEntity(BlockPos p,BlockState s){super(AflBlockEntities.RESTROOM_STALL_DOOR.get(),p,s);}
    public boolean begin(){
        if(level==null||level.isClientSide||pending!=null||!level.getBlockState(worldPosition.above()).is(getBlockState().getBlock()))return false;
        pending=!getBlockState().getValue(RestroomStallDoorBlock.OPEN);
        finish=level.getGameTime()+RestroomStallDoorBlock.ANIMATION_TICKS;
        triggerAnim("door",pending?"open":"close");return true;
    }
    public void complete(){
        if(pending==null||level==null)return;
        if(level.getGameTime()<finish){level.scheduleTick(worldPosition,getBlockState().getBlock(),(int)(finish-level.getGameTime()));return;}
        boolean target=pending;pending=null;
        if(getBlockState().getBlock() instanceof RestroomStallDoorBlock door)door.commit(level,worldPosition,target);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        var controller=new AnimationController<RestroomStallDoorBlockEntity>(this,"door",0,s->s.setAndContinue(RawAnimation.begin().thenLoop(
                getBlockState().getValue(RestroomStallDoorBlock.OPEN)?"door_open_pose":"door_closed_pose")));
        controller.triggerableAnim("open",RawAnimation.begin().thenPlay("restroom_stall_door_open"));
        controller.triggerableAnim("close",RawAnimation.begin().thenPlay("restroom_stall_door_close"));controllers.add(controller);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public double getTick(Object o){return level==null?0:level.getGameTime();}
    @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(1,0,1).expandTowards(0,1,0);}
}
