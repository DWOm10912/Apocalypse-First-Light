package com.antaurora.apofirstlight.block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Bounded supplemental ray test for the open leaf's out-of-cell portion. */
public final class RestroomDoorRaycast {
    private RestroomDoorRaycast(){}
    public static BlockHitResult find(Level level,Player player,Vec3 start,Vec3 end){
        if(start.distanceToSqr(end)>1024)return null;
        BlockHitResult best=null;double nearest=start.distanceToSqr(end);
        BlockPos min=BlockPos.containing(Math.min(start.x,end.x)-1,Math.min(start.y,end.y)-1,Math.min(start.z,end.z)-1);
        BlockPos max=BlockPos.containing(Math.max(start.x,end.x)+1,Math.max(start.y,end.y)+1,Math.max(start.z,end.z)+1);
        BlockHitResult obstacle=level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(obstacle.getType()==HitResult.Type.BLOCK)nearest=Math.min(nearest,start.distanceToSqr(obstacle.getLocation())+1e-6);
        for(BlockPos pos:BlockPos.betweenClosed(min,max)){
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof RestroomStallDoorBlock)||!state.getValue(RestroomStallDoorBlock.OPEN))continue;
            // betweenClosed reuses a MutableBlockPos; BlockHitResult retains its reference.
            var hit=RestroomStallDoorBlock.leafShape(state).clip(start,end,pos.immutable());
            if(hit!=null && start.distanceToSqr(hit.getLocation())<nearest){nearest=start.distanceToSqr(hit.getLocation());best=hit;}
        }
        return best;
    }
}
