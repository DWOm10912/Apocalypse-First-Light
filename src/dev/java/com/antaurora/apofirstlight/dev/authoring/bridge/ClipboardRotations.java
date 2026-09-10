package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.sk89q.worldedit.extent.clipboard.*;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;

final class ClipboardRotations {
    static Clipboard rotate(Clipboard clip,int degrees)throws Exception{
        if(degrees!=0&&degrees!=90&&degrees!=180&&degrees!=270)throw new IllegalArgumentException("ROTATION_MUST_BE_0_90_180_270");
        var transform=new AffineTransform().rotateY(degrees);var origin=clip.getOrigin();var min=clip.getMinimumPoint();var max=clip.getMaximumPoint();
        int x1=Integer.MAX_VALUE,y1=Integer.MAX_VALUE,z1=Integer.MAX_VALUE,x2=Integer.MIN_VALUE,y2=Integer.MIN_VALUE,z2=Integer.MIN_VALUE;
        for(int x:new int[]{min.getBlockX(),max.getBlockX()})for(int y:new int[]{min.getBlockY(),max.getBlockY()})for(int z:new int[]{min.getBlockZ(),max.getBlockZ()}){
            var point=transform.apply(BlockVector3.at(x,y,z).subtract(origin).toVector3());int xx=(int)Math.round(point.getX()),yy=(int)Math.round(point.getY()),zz=(int)Math.round(point.getZ());x1=Math.min(x1,xx);y1=Math.min(y1,yy);z1=Math.min(z1,zz);x2=Math.max(x2,xx);y2=Math.max(y2,yy);z2=Math.max(z2,zz);
        }
        var result=new BlockArrayClipboard(new CuboidRegion(BlockVector3.ZERO,BlockVector3.at(x2-x1,y2-y1,z2-z1)));result.setOrigin(BlockVector3.ZERO);
        var holder=new ClipboardHolder(clip);holder.setTransform(transform);Operations.complete(holder.createPaste(result).to(BlockVector3.at(-x1,-y1,-z1)).copyEntities(false).copyBiomes(false).build());return result;
    }
}
