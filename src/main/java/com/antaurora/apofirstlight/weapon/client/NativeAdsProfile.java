package com.antaurora.apofirstlight.weapon.client;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Central per-gun visual calibration. Anchor coordinates are Gecko render-space model units.
 * HIP Display remains in its original JSON; this describes that transform for the inverse solve.
 * No sight coordinate is used by hitscan. */
public record NativeAdsProfile(String anchor, float ax, float ay, float az, float eyeRelief,
        float hx, float hy, float hz, float rx, float ry, float rz, float scale,
        float compositionX, float compositionY, float rootPitch) {
    // Raised rear aperture and front post share Y=13.6875; legacy iron_view Y=14.8
    // is a camera placement helper, not the mechanical sight axis.
    public static final NativeAdsProfile RIFLE = new NativeAdsProfile("octagon9/rear-aperture-axis",
            0,13.6875F,15.46875F,.16F,
            3.8F,-7.2F,-11.5F,0,4,0,.45F,0,0,0);
    public static final NativeAdsProfile PISTOL = new NativeAdsProfile("sight_anchor/rear-axis",
            -2.98F,11.59F,9.04F,.34F,
            1.00148F,-7.2445F,-11.68624F,.54547F,.19151F,-.27948F,.41F,.10F,.045F,3);
    public static NativeAdsProfile forGun(ResourceLocation id) {
        return switch(id.toString()) {
            case "apocalypse_firstlight:br51_01" -> RIFLE;
            case "apocalypse_firstlight:p9_01" -> PISTOL;
            default -> null;
        };
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
        return new Matrix4f().translation(0,0,-eyeRelief).scale(scale).translate(-ax/16,-ay/16,-az/16);
    }
    public Matrix4f correction(boolean right) {
        Matrix4f correction=ads().mul(hip().invert());
        // Mirror the whole carrier for left-main-arm, not a second pose.
        if(!right) correction=new Matrix4f().scaling(-1,1,1).mul(correction).scale(-1,1,1);
        return correction;
    }
    public Vector3f translation() { return correction(true).getTranslation(new Vector3f()); }
}
