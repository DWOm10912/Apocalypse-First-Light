package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.NativeAttachment;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

public final class MaintenanceHotspots {
    public record Point(double x,double y){public boolean hit(double mx,double my){return Math.hypot(mx-x,my-y)<=20;}}
    public static Point project(NativeAttachment.Slot slot,int width,int height){
        var s=MaintenanceModeClientState.INSTANCE;
        if(!s.active()||s.bench().isEmpty())return null;
        var stack=s.bench().getItem(0);
        if(!(stack.getItem() instanceof NativeGunItem gun)||!gun.definition().id().toString().equals("apocalypse_firstlight:p9_01"))return null;
        var p=new PoseStack();var pos=s.bench().getBlockPos();p.translate(pos.getX(),pos.getY(),pos.getZ());
        MaintenanceCameraController.benchTransform(p,s.facing());MaintenanceGunRendering.transform(stack,p);
        var world=MaintenanceGunRendering.interactionPoint(stack,slot,p);if(world==null)return null;
        return projectWorld(new Vec3(world.x,world.y,world.z),width,height);
    }
    public static Point projectWorld(Vec3 world,int width,int height){
        var s=MaintenanceModeClientState.INSTANCE;
        // Exact basis of the fixed maintenance camera (no player bob/FOV), in GUI-scaled coordinates.
        double yaw=Math.toRadians(MaintenanceCameraController.yaw(s.facing())),pitch=Math.toRadians(MaintenanceCameraController.PITCH);
        var forward=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        var right=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));var up=right.cross(forward);
        var delta=world.subtract(s.cameraPosition());double depth=delta.dot(forward);
        if(depth<=.01)return null;
        double focal=height/(2*Math.tan(Math.toRadians(MaintenanceCameraController.FOV/2)));
        return new Point(width/2d+delta.dot(right)*focal/depth,height/2d-delta.dot(up)*focal/depth);
    }
}
