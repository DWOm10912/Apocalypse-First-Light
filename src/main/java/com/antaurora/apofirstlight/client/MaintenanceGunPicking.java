package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Ray against per-cube proxies; no entity and no oversized screen rectangle. */
public final class MaintenanceGunPicking {
    public static boolean hit(double mouseX,double mouseY,int width,int height){
        var s=MaintenanceModeClientState.INSTANCE;if(!s.active()||s.bench().isEmpty())return false;
        var origin=s.cameraPosition();
        double yaw=Math.toRadians(MaintenanceCameraController.yaw(s.facing())),pitch=Math.toRadians(MaintenanceCameraController.PITCH);
        var forward=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        var right=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));var up=right.cross(forward);
        double tangent=Math.tan(Math.toRadians(MaintenanceCameraController.FOV/2));
        var end=origin.add(forward.add(right.scale((2*mouseX/width-1)*(double)width/height*tangent))
                .add(up.scale((1-2*mouseY/height)*tangent)).normalize().scale(5));
        var p=new PoseStack();var pos=s.bench().getBlockPos();p.translate(pos.getX(),pos.getY(),pos.getZ());
        MaintenanceCameraController.benchTransform(p,s.facing());MaintenanceGunRendering.transform(s.bench().getItem(0),p);
        var inverse=new org.joml.Matrix4f(p.last().pose()).invert();
        var a=inverse.transformPosition(new Vector3f((float)origin.x,(float)origin.y,(float)origin.z));
        var b=inverse.transformPosition(new Vector3f((float)end.x,(float)end.y,(float)end.z));
        Vec3 start=new Vec3(a.x,a.y,a.z),stop=new Vec3(b.x,b.y,b.z);
        return MaintenanceGunRendering.bounds(s.bench().getItem(0)).stream().anyMatch(box->box.inflate(.012).contains(start)||box.inflate(.012).clip(start,stop).isPresent());
    }
}
