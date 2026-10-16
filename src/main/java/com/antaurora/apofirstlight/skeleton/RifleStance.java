package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Player skeleton V1 P1 (docs/dev/player_skeleton_v1_design.md 4): where a rifle sits against the eye and where the
 * hands go on it. Pure math shared by the game (client/skeleton/SkeletonGunStance) and the offline checks.
 * <ul>
 * <li>aimed: NativeAdsProfile#ads at real size, the sight on the view line, {@code eye_relief * real / first-person
 * scale} in front of the eye (scaling the whole first-person picture about the eye keeps the aimed picture);</li>
 * <li>not aimed: the same, moved by {@link #READY} (view space), blended by the aim progress;</li>
 * <li>hands: a gun anchor (NativeHandBinding's frame: origin at the hand cap's centre, forearm along -y) becomes the IK
 * target of the rest arm frame.</li>
 * </ul>
 */
public final class RifleStance {
    /** The chest turns this far right of the view with a rifle (left shoulder forward). */
    public static final float CHEST_TWIST = 20F;
    /** Not aiming: the gun lower, right and forward of the aimed place (view space, blocks). */
    public static final Vector3f READY = new Vector3f(0.06F, -0.10F, 0.02F);
    /**
     * Real size over the model for the guns the skeleton carries (tools/check-skeleton-arm-reach.mjs: rack length over
     * the model's longest side), by gun id path. P1: the BR51 only.
     */
    private static final java.util.Map<String, Float> REAL_SCALE = java.util.Map.of("br51_01", 0.307F);

    /** NativeHandBinding: the hand cap's distance below the arm pivot (px). */
    public static final float DISTAL_Y = 10F;
    /**
     * How far (model px, about 18 cm) the support hand may slide back along the handguard when the arm cannot reach the
     * idle clip's grip: the vanilla arm is about 15 % shorter than a real one against a real-size gun.
     */
    public static final float SUPPORT_SLIDE = 3F;
    /**
     * The firing side's shoulder set with a rifle (HumanoidPoser.Input#shoulderRight; chest frame px: in, down, back): the
     * shoulder drawn 1.5 px in and 3 px forward into the stock. The vanilla shoulder pivot sits 5 px off the middle and
     * about 25 cm behind the eye socket, so without it the firing arm reaches the grip only straight and its forearm passes
     * the face when looking up (tools: PlayerSkeletonCheck camera clearance).
     */
    public static final Vector3f STOCK_SHOULDER = new Vector3f(1.5F, 0F, -3F);

    private RifleStance() {
    }

    /** The real-size factor of a gun (id path), or null when the skeleton does not carry it. */
    public static Float realScale(String gunPath) {
        return REAL_SCALE.get(gunPath);
    }

    /** NativeHandBinding#centreX: the arm box's centre off the pivot (px), right arm negative. */
    public static float centreX(boolean right, boolean slim) {
        return (right ? -1F : 1F) * (slim ? 0.5F : 1F);
    }

    /** The ADS calibration numbers of a gun (data native_guns ads.profile; the sight mount's aim point when fitted). */
    public record Profile(float aimX, float aimY, float aimZ, float eyeRelief, float firstPersonScale,
                          float adsPitch, float adsYaw, float adsRoll) {}

    /**
     * The view frame's part of the stance: from the gun's bare GeckoLib frame (blocks) to view space (blocks; +x right,
     * +y up, -z ahead, origin at the eye). {@code aim} 0..1, {@code realScale} the gun's real-size factor.
     */
    public static Matrix4f inView(Profile p, float realScale, float aim) {
        float ready = 1F - Math.max(0F, Math.min(1F, aim));
        return new Matrix4f()
                .translate(READY.x * ready, READY.y * ready, READY.z * ready)
                .translate(0, 0, -p.eyeRelief() * realScale / p.firstPersonScale())
                .scale(realScale)
                .rotate(new Quaternionf().rotationXYZ(rad(p.adsPitch()), rad(p.adsYaw()), rad(p.adsRoll())))
                .translate(-p.aimX() / 16F, -p.aimY() / 16F, -p.aimZ() / 16F);
    }

    /** The eye frame: view space to a frame with world axes (blocks), an eye at {@code eye} looking yaw / pitch (deg). */
    public static Matrix4f eye(Vector3f eye, float yaw, float pitch) {
        return new Matrix4f().translation(eye).rotateY(rad(-(yaw + 180F))).rotateX(rad(-pitch));
    }

    /**
     * One hand's IK target: the rest arm frame (arm px, origin at the shoulder pivot) to model px, for a gun whose bare
     * frame is {@code gunModel} in model blocks and an anchor frame (gun blocks) from GeoAnchorSampler.
     */
    public static Matrix4f handTarget(Matrix4f gunModel, Matrix4f anchor, boolean right, boolean slim, Matrix4f out) {
        Matrix4f m = new Matrix4f().scaling(16F).mul(gunModel).mul(anchor);
        Vector3f origin = m.getTranslation(new Vector3f());
        Quaternionf rotation = m.getUnnormalizedRotation(new Quaternionf());
        return out.translation(origin).rotate(rotation).translate(-centreX(right, slim), -DISTAL_Y, 0);
    }

    /**
     * The support hand's slide (HumanoidPoser.Input#slideLeft): toward the stock along the bore, model px, length
     * {@link #SUPPORT_SLIDE}, for a gun frame in model px. The bore is the aimed view line, so in the gun's frame it is
     * the ADS rotation's view +z.
     */
    public static Vector3f supportSlide(Matrix4f gunModelPx, Profile p, Vector3f out) {
        Vector3f back = new Quaternionf().rotationXYZ(rad(p.adsPitch()), rad(p.adsYaw()), rad(p.adsRoll())).conjugate()
                .transform(new Vector3f(0, 0, 1));
        gunModelPx.transformDirection(back);
        return back.lengthSquared() < 1e-12F ? out.zero() : out.set(back).normalize(SUPPORT_SLIDE);
    }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
}
