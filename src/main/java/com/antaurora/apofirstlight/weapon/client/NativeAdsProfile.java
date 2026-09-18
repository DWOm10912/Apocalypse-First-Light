package com.antaurora.apofirstlight.weapon.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Central per-gun visual calibration. Anchor coordinates are Gecko render-space model units.
 * HIP Display remains in its original JSON; this describes that transform for the inverse solve.
 * No sight coordinate is used by hitscan. */
public record NativeAdsProfile(String anchor, float ax, float ay, float az, float eyeRelief,
        float hx, float hy, float hz, float rx, float ry, float rz, float scale,
        float compositionX, float compositionY, float rootPitch,
        float adsPitch, float adsYaw, float adsRoll) {
    public static NativeAdsProfile from(com.antaurora.apofirstlight.weapon.NativeAdsCalibration value) {
        return new NativeAdsProfile(value.anchor(),value.ax(),value.ay(),value.az(),value.eyeRelief(),
                value.hx(),value.hy(),value.hz(),value.rx(),value.ry(),value.rz(),value.scale(),
                value.compositionX(),value.compositionY(),value.rootPitch(),
                value.adsPitch(),value.adsYaw(),value.adsRoll());
    }
    public static NativeAdsProfile forStack(net.minecraft.world.item.ItemStack stack) {
        if(!(stack.getItem() instanceof com.antaurora.apofirstlight.weapon.NativeGunItem gun))return null;
        var base=from(gun.definition().adsCalibration());
        var mount=gun.definition().sightMount();
        if(mount==null||com.antaurora.apofirstlight.weapon.NativeAttachments.activeSight(stack).isEmpty())return base;
        return new NativeAdsProfile(mount.anchor()+"/reticle_dot",mount.aimX(),mount.aimY(),mount.aimZ(),base.eyeRelief,
                base.hx,base.hy,base.hz,base.rx,base.ry,base.rz,base.scale,base.compositionX,base.compositionY,base.rootPitch,
                base.adsPitch,base.adsYaw,base.adsRoll);
    }
    private static float rad(float d) { return (float)Math.toRadians(d); }
    public Matrix4f hip() {
        var m = new Matrix4f().translation(compositionX,compositionY,0)
                .translate(hx/16,hy/16,hz/16).rotate(new Quaternionf().rotationXYZ(rad(rx),rad(ry),rad(rz)))
                .scale(scale).translate(0,.01F,0); // ItemRenderer -.5 and GeoItemRenderer +(.5,.51,.5)
        if(rootPitch!=0) m.translate(0,.5F,.375F).rotateX(rad(rootPitch)).translate(0,-.5F,-.375F);
        return m;
    }
    public Matrix4f ads() {
        // Rotate in sight-local space around the selected iron/optic aim point.
        // T(-aim) is applied first to vertices, so the reference point remains
        // centered while per-gun pitch/yaw/roll aligns the authored sight axis.
        return new Matrix4f().translation(0,0,-eyeRelief).scale(scale)
                .rotate(new Quaternionf().rotationXYZ(rad(adsPitch),rad(adsYaw),rad(adsRoll)))
                .translate(-ax/16,-ay/16,-az/16);
    }
    public Matrix4f correction(boolean right) {
        Matrix4f correction=ads().mul(hip().invert());
        // Mirror the whole carrier for left-main-arm, not a second pose.
        if(!right) correction=new Matrix4f().scaling(-1,1,1).mul(correction).scale(-1,1,1);
        return correction;
    }
    public Vector3f translation() { return correction(true).getTranslation(new Vector3f()); }
}
