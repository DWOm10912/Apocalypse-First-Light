package com.antaurora.apofirstlight.weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Parent correction, shared by both existing first-person render paths. */
public final class FieldAttachmentTransform {
    public static void apply(PoseStack pose,boolean right,float partial){
        if(!FieldAttachmentViewState.isActive())return;
        var stack=Minecraft.getInstance().player.getMainHandItem();
        if(!(stack.getItem() instanceof NativeGunItem gun)||!FieldAttachmentViewState.matches(stack))return;
        var base=NativeAdsProfile.from(gun.definition().adsCalibration());
        var f=FieldAttachmentViewState.profile();
        var target=new Matrix4f().translation(f.x(),f.y(),f.z())
                .rotate(new Quaternionf().rotationXYZ(rad(f.pitch()),rad(f.yaw()),rad(f.roll())))
                .scale(base.scale()*f.scale()).translate(-f.centerX()/16,-f.centerY()/16,-f.centerZ()/16);
        var correction=target.mul(base.hip().invert());
        if(!right)correction=new Matrix4f().scaling(-1,1,1).mul(correction).scale(-1,1,1);
        float p=FieldAttachmentViewState.progress(partial);
        var t=correction.getTranslation(new Vector3f());
        pose.translate(t.x*p,t.y*p,t.z*p);
        pose.mulPose(new Quaternionf().slerp(correction.getUnnormalizedRotation(new Quaternionf()),p));
        var targetScale=correction.getScale(new Vector3f());
        pose.scale(1+(targetScale.x-1)*p,1+(targetScale.y-1)*p,1+(targetScale.z-1)*p);
    }
    private static float rad(float value){return (float)Math.toRadians(value);}
    private FieldAttachmentTransform(){}
}
