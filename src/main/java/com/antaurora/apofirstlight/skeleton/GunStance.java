package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 4, 12, 13): where a gun the skeleton carries sits and where
 * the hands go on it. Pure math shared by the game (client/skeleton/SkeletonGunStance) and the offline checks.
 * <ul>
 * <li>raised: NativeAdsProfile#ads at its body size ({@link Carried#scale}), the sight on the view line, {@code eye_relief
 * * scale / first-person scale} in front of the eye (scaling the whole first-person picture about the eye keeps the aimed
 * picture); not aiming, moved by the kind's {@link Kind#ready} (view space), blended by the aim progress;</li>
 * <li>shouldered (a rifle, {@link Kind#pocket}): the bore along the view, the butt on the chest; only first-person
 * aiming takes it up to the eye ({@link #onShoulder});</li>
 * <li>lowered (a pistol held one-handed, any gun while sprinting): against the chest, muzzle down, the firing hand at
 * {@link Kind#lowHand};</li>
 * <li>drawn and put away: along a path from its carry mount (rifle: the back, pistol: the thigh holster) through a middle
 * key to the stance, the hands timed by {@link #draw} / {@link #putAway};</li>
 * <li>hands: a gun anchor (NativeHandBinding's frame: origin at the hand cap's centre, forearm along -y) becomes the IK
 * target of the rest arm frame; a rifle's support hand sits under the handguard instead ({@link #supportUnder}).</li>
 * </ul>
 * Frames: a gun frame maps the gun's bare GeckoLib frame (blocks) to model blocks; "chest view" frames have view axes
 * (+x the body's right, +y up, -z ahead), blocks, origin at the chest bone's pivot.
 */
public final class GunStance {
    // the BR51's support hand, tuned offline (SupportLab) shouldered at 1.25 x (not referenced by Kind: no init cycle)
    private static final float SUPPORT_Z = -3F, SUPPORT_DOWN = -20F, SUPPORT_OUT = 75F, SUPPORT_ROLL = 135F;
    private static final Vector3f SUPPORT_POLE = new Vector3f(1F, 0.5F, 0F);
    /** How a gun is held. */
    public enum Kind {
        /**
         * Two hands always; the body bladed (chest right of the view, left shoulder forward), the butt on the chest right
         * of the centre, the support hand under the handguard as far forward as the arm reaches
         * (docs/dev/gun_handling_reference_v1.md 4.5).
         */
        RIFLE(30F, new Vector3f(0.06F, -0.10F, 0.02F), new Vector3f(1.5F, 0F, -3F), new Vector3f(), 3F,
                new Vector3f(0.08F, -0.02F, -0.26F), -35F, 25F, 0F,
                new Grip(-20, -40, -40), Grip.NONE, new Vector3f(-0.35F, 1F, 0.45F), new Vector3f(0.15F, 1F, 0.25F),
                null, 0F, new Vector3f(-1.5F, 1.5F, -2.2F)),
        /**
         * Both hands: the ready (TC 3-23.35 6-31: two-handed grip, upper arms close to the body, forearms about 45 deg, the
         * muzzle toward the target) by default, raised to the eye while aiming, shooting, reloading; square to the view.
         * Sprinting: one hand, lowered. The firing hand holds the gun nearly as its own anchor does: the wrist straight,
         * the forearm back along the bore but for the isosceles' slant toward the body's middle (yaw 15: the arms make a V
         * to the gun; the -30 / -10 / -30 grip looked like holding it from the side); the support hand wraps it from below
         * (both tuned offline over the look's pitch, docs/dev/p9_stance_loops_v1.md 4.1).
         */
        PISTOL(0F, new Vector3f(0.03F, -0.09F, 0.03F), new Vector3f(0.5F, 0F, -2F), new Vector3f(-0.5F, 0F, -2F), 0F,
                new Vector3f(0.17F, -0.12F, -0.17F), -50F, 0F, 0.62F,
                new Grip(-5, 15, -10), new Grip(-35, 15, 30), new Vector3f(-0.35F, 1F, 0.45F), new Vector3f(0.35F, 1F, 0.45F),
                new Vector3f(0.03F, 0.10F, -0.33F), -10F, null);

        /** Chest yaw right of the view (deg) while the gun is held. */
        public final float chestTwist;
        /** Raised but not aiming: the gun lower, right and forward of the aimed place (view space, blocks). */
        public final Vector3f ready;
        /** HumanoidPoser.Input#shoulderRight / #shoulderLeft with the gun up (chest frame px: in, down, back). */
        public final Vector3f shoulderRight, shoulderLeft;
        /** HumanoidPoser.Input#slideLeft's length (model px). */
        public final float supportSlide;
        /** Lowered: the firing hand's grip (chest view frame, blocks), the muzzle's pitch (deg, - = down) and yaw (+ = left). */
        public final Vector3f lowHand;
        public final float lowPitch, lowYaw;
        /**
         * Aimed: the rear sight's distance ahead of the eye (blocks), or 0 for the ADS profile's eye relief at real size. A
         * rifle's stock sets it (the profile); a pistol is held out at arm's length, much further than its old view-model
         * eye relief (0.47 at the view model's 0.53 scale: 0.23 at real size, the hands 15 cm from the eye, the arm skin
         * 2-4 cm). 0.62 (user 2026-10-17, third person: at 0.34 the gun was 1.4 px before the face, the elbows out, "挤"):
         * further than the arms reach at any look, so {@link #inReach} always brings it back to the arms' reach (0.98),
         * the arms out straight as the isosceles holds them. Only the skeleton's third person and the other players use
         * it: the own first person holds a gun through its old view model.
         */
        public final float sightDistance;
        /** How each hand sits on the gun against the gun's own anchor (the designer's arm runs straight back along the bore). */
        public final Grip gripRight, gripLeft;
        /** HumanoidPoser.Input#poleRight / #poleLeft with the gun in the hands (chest frame). */
        public final Vector3f poleRight, poleLeft;
        /** The two-handed ready, or null: the firing hand's grip (chest view frame, blocks) and the muzzle's pitch (deg). */
        public final Vector3f readyHand;
        public final float readyPitch;
        /** Shouldered ({@link #onShoulder}): the butt's place on the chest (rest model px), or null (the sight at the eye). */
        public final Vector3f pocket;

        Kind(float chestTwist, Vector3f ready, Vector3f shoulderRight, Vector3f shoulderLeft, float supportSlide,
             Vector3f lowHand, float lowPitch, float lowYaw, float sightDistance, Grip gripRight, Grip gripLeft,
             Vector3f poleRight, Vector3f poleLeft, Vector3f readyHand, float readyPitch, Vector3f pocket) {
            this.pocket = pocket;
            this.gripRight = gripRight;
            this.gripLeft = gripLeft;
            this.poleRight = poleRight;
            this.poleLeft = poleLeft;
            this.readyHand = readyHand;
            this.readyPitch = readyPitch;
            this.chestTwist = chestTwist;
            this.ready = ready;
            this.shoulderRight = shoulderRight;
            this.shoulderLeft = shoulderLeft;
            this.supportSlide = supportSlide;
            this.lowHand = lowHand;
            this.lowPitch = lowPitch;
            this.lowYaw = lowYaw;
            this.sightDistance = sightDistance;
        }
    }

    /**
     * A hand's hold against the gun's anchor: the hand target turned about the palm (anchor frame: +y up the arm toward the
     * shoulder, origin at the hand cap's centre) by these angles (deg, X then Y then Z).
     */
    public record Grip(float pitch, float yaw, float roll) {
        public static final Grip NONE = new Grip(0, 0, 0);
    }

    /**
     * A support hand under a handguard ({@link #supportUnder}): the hand cap's place on the gun's underside (gun px; y = the
     * underside there), the forearm's way from the hand toward the elbow (deg from the bore back toward the stock: down,
     * out to the gun's left), the hand's roll about it (deg) and the elbow's pole (chest frame).
     */
    public record Support(Vector3f at, float down, float out, float roll, Vector3f pole) {}

    /**
     * A gun the skeleton carries: how it is held, its real size over the model, its geo / animation asset, its size on
     * the body over the real one (a long gun's real length looks short against the block body's head and arms), the butt
     * plate's centre (gun px; shouldered guns), the support hand's place under its handguard (or null: the anchor) and how
     * much lower than its {@code right_hand_anchor} / {@code left_hand_anchor} the firing / support hand sits (model px:
     * the 4 px block hand is taller than a pistol's grip, so a hand centred on the anchor cuts into the slide; lower, its
     * top stays under the slide).
     */
    public record Carried(Kind kind, float realScale, String asset, float bodyScale, Vector3f butt, Support support, float handDrop,
                          float supportDrop) {
        /** The gun's size on the body (gun blocks to model blocks, with the profile's frame). */
        public float scale() {
            return realScale * bodyScale;
        }

        /** The firing hand's anchor (gun blocks): the gun's {@code right_hand_anchor} frame, {@link #handDrop} lower on the gun. */
        public Matrix4f rightHand(Matrix4f anchor) {
            return lower(anchor, handDrop);
        }

        /**
         * The support hand's anchor (gun blocks): the clip's {@code left_hand_anchor} frame, {@link #supportDrop} lower on the
         * gun while the clip keeps it on the grip ({@code rest}: its {@code static_idle} frame), none once a reload or an
         * inspect has taken it {@link #SUPPORT_DROP_FADE} px away (to the magazine, the slide).
         */
        public Matrix4f leftHand(Matrix4f anchor, Matrix4f rest) {
            if (supportDrop == 0F) return anchor;
            float away = anchor.getTranslation(new Vector3f()).distance(rest.getTranslation(new Vector3f())) * 16F * scale();
            return lower(anchor, supportDrop * (1F - smooth(away / SUPPORT_DROP_FADE)));
        }

        private Matrix4f lower(Matrix4f anchor, float drop) {
            return drop == 0F ? anchor : new Matrix4f().translation(0F, -drop / (16F * scale()), 0F).mul(anchor);
        }
    }

    /**
     * By gun id path. Real scale = rack length over the model's longest side (tools/check-skeleton-arm-reach.mjs).
     * P2 first part: the BR51 and the P9 (design 10.9); the other four follow once both pass. The BR51 is 1.25 x on the body
     * (user 2026-10-15: a rifle must look long; 1.25 x is the most the arms reach without growing), the support hand at
     * the handguard's rear (gun_handling_reference_v1.md 4.5). The P9 is as large in the hands as in its holster (its item
     * model's third-person size, 0.49467: 1.8953 x real; user 2026-10-17: at real size, 3.2 px long, the fists hid it); its
     * hands {@link #P9_HAND_DROP} / {@link #P9_SUPPORT_DROP} lower than its anchors.
     */
    private static final Map<String, Carried> CARRIED = Map.of(
            "br51_01", new Carried(Kind.RIFLE, 0.307F, "br51_01_v3_native", 1.25F, new Vector3f(0F, 7F, 26.7F),
                    new Support(new Vector3f(-0.2F, 8.6F, SUPPORT_Z), SUPPORT_DOWN, SUPPORT_OUT, SUPPORT_ROLL, SUPPORT_POLE), 0F, 0F),
            "p9_01", new Carried(Kind.PISTOL, 0.261F, "p9_01_v3_native", 0.49467F / 0.261F, null, null, GunStance.P9_HAND_DROP,
                    GunStance.P9_SUPPORT_DROP));

    /**
     * The P9's hands under their anchors (model px; Carried#handDrop, #supportDrop), measured on the posed block hands in
     * the AFL Rig Blockbench plugin (docs/dev/p9_stance_loops_v1.md 4.1): the right anchor is 0.89 px under the slide, the
     * block hand's top 2 px above its centre, so centred there it cut 1.1 px into the slide (user 2026-10-17, first
     * person); turned by its grip, 1.6 px lower its highest corner is under the beavertail. The left anchor is 1.1 px over
     * the right one: centred there the support hand rose 1.6 px over the slide's bottom beside it and hid the slide from the
     * eye; 2 px lower its top is 0.4 px under the slide (the first person's narrower arms the same).
     */
    public static final float P9_HAND_DROP = 1.6F, P9_SUPPORT_DROP = 2F;

    /** Carried#leftHand: how far (model px) a clip takes the support hand from the grip before its drop is gone. */
    public static final float SUPPORT_DROP_FADE = 1F;

    /**
     * First person (docs/dev/p9_stance_loops_v1.md 4.2): the camera sits on the eye socket, the body 4 px behind it
     * (HumanoidPoser's root shift), so the real arms hold a gun no further than about 6 px ahead of the eye, low on the
     * screen. With the gun held out (its stance loop and actions in the {@code view} frame) the first person draws the arms
     * reaching further, as the old view model and shooters' view models do: the view frame's keys this far ahead along the
     * look, the shoulders' reach (HumanoidPoser.Input#protraction) as much more. Full looking ahead or up, none looking
     * down (there the own body shows, the arms on it), eased between {@link #FIRST_PERSON_REACH_FROM} and {@link
     * #FIRST_PERSON_REACH_TO} degrees down. Third person never: others see the real arms.
     */
    public static final float FIRST_PERSON_REACH = 5F, FIRST_PERSON_REACH_FROM = 15F, FIRST_PERSON_REACH_TO = 45F;

    /** {@link #FIRST_PERSON_REACH} at the look's pitch (deg, + = down). */
    public static float firstPersonReach(float pitch) {
        return FIRST_PERSON_REACH * (1F - smooth((pitch - FIRST_PERSON_REACH_FROM) / (FIRST_PERSON_REACH_TO - FIRST_PERSON_REACH_FROM)));
    }

    /**
     * First person with the reach (docs/dev/p9_stance_loops_v1.md 4.3): the arms are drawn this wide (their skin boxes' x,
     * about the box's middle, so the palm stays on the grip), as thin as the old view model drew them (NativePlayerArmRenderer's
     * 0.62). Full 4 px block hands around a pistol's grip hid the gun from the eye. Only the width: held out, the box's x
     * is across the view and its z up the gun, so the hands' tops stay under the slide (thinner in z too they sank 0.76 px
     * and the grip showed above them).
     */
    public static final float FIRST_PERSON_ARM = 0.62F;

    /** How wide the arms are drawn (1 = as they are) for a first-person reach {@code ahead} (px, #firstPersonReach). */
    public static float firstPersonArm(float ahead) {
        return 1F - (1F - FIRST_PERSON_ARM) * Math.min(1F, ahead / FIRST_PERSON_REACH);
    }

    /** The gun's size on the body (Carried#scale) by id path, or 1 when the skeleton does not carry it. */
    public static float bodyScale(String gunPath) {
        Carried c = CARRIED.get(gunPath);
        return c == null ? 1F : c.bodyScale();
    }

    /** The P1 rifle constants, kept for the checks and the docs. */
    public static final float CHEST_TWIST = Kind.RIFLE.chestTwist, SUPPORT_SLIDE = Kind.RIFLE.supportSlide;
    public static final Vector3f READY = Kind.RIFLE.ready, STOCK_SHOULDER = Kind.RIFLE.shoulderRight;

    /**
     * {@link #inReach} brings an arm's-length gun no nearer the eye than this (blocks; its aimed place, the clip's own
     * motion aside): looking straight up the arms would come into the near plane, so there the hands stay a little short.
     */
    public static final float MIN_SIGHT = 0.27F;

    /** A shouldered gun comes back toward the eye at most this far (model px) where the hands fall short (#inReach). */
    public static final float POCKET_GIVE = 3F;
    /** The support hand's highest corner this far (model px) above the handguard's underside. */
    public static final float SUPPORT_OVERLAP = 0.3F;
    /** NativeHandBinding: the hand cap's distance below the arm pivot (px), vanilla's arm. */
    public static final float DISTAL_Y = 10F;
    /**
     * The rig the player skeleton solves on (PlayerSkeleton#poser): its hand cap ({@link SkeletonRig.Profile}) sets the
     * hand targets' arm frame; vanilla's for the vanilla rig.
     */
    private static SkeletonRig.Profile arm;
    /** A {@link Grip} turns the hand about this point, px up the arm from the hand cap (the hand bone is the last 2 px). */
    public static final float PALM_Y = 1.5F;

    private GunStance() {
    }

    /** The gun (id path) the skeleton carries, or null. */
    public static Carried carried(String gunPath) {
        return CARRIED.get(gunPath);
    }

    /** A gun's size on the body (id path, Carried#scale), or null when the skeleton does not carry it. */
    public static Float realScale(String gunPath) {
        Carried c = CARRIED.get(gunPath);
        return c == null ? null : c.scale();
    }

    /** NativeHandBinding#centreX: the arm box's centre off the pivot (px), right arm negative. */
    public static float centreX(boolean right, boolean slim) {
        if (arm != null) return (right ? 1F : -1F) * arm.handCapX();
        return (right ? -1F : 1F) * (slim ? 0.5F : 1F);
    }

    /** The hand cap's distance below the arm pivot (px) on the active rig ({@link #DISTAL_Y} for vanilla's). */
    public static float distalY() {
        return arm != null ? arm.handCapY() : DISTAL_Y;
    }

    /** The hand's half width (px) on the active rig. */
    static float handHalf(boolean slim) {
        return arm != null ? arm.handHalf() : slim ? 1.5F : 2F;
    }

    /** PlayerSkeleton#poser: the rig the hand targets are made for (its profile; null: vanilla's). */
    public static void useRig(SkeletonRig rig) {
        arm = rig.profile;
    }

    /** The ADS calibration numbers of a gun (data native_guns ads.profile; the sight mount's aim point when fitted). */
    public record Profile(float aimX, float aimY, float aimZ, float eyeRelief, float firstPersonScale,
                          float adsPitch, float adsYaw, float adsRoll) {}

    /**
     * The view frame's part of the raised stance: from the gun's bare GeckoLib frame (blocks) to view space (blocks; +x
     * right, +y up, -z ahead, origin at the eye). {@code aim} 0..1.
     */
    public static Matrix4f inView(Profile p, Kind kind, float realScale, float aim) {
        float ready = 1F - clamp01(aim);
        return new Matrix4f()
                .translate(kind.ready.x * ready, kind.ready.y * ready, kind.ready.z * ready)
                .translate(0, 0, -(kind.sightDistance > 0 ? kind.sightDistance : p.eyeRelief() * realScale / p.firstPersonScale()))
                .mul(sightAtOrigin(p, realScale));
    }

    /** The rifle's raised stance (P1). */
    public static Matrix4f inView(Profile p, float realScale, float aim) {
        return inView(p, Kind.RIFLE, realScale, aim);
    }

    /** The gun at real size with its sight at the origin and its bore along -z (view axes). */
    public static Matrix4f sightAtOrigin(Profile p, float realScale) {
        return new Matrix4f()
                .scale(realScale)
                .rotate(new Quaternionf().rotationXYZ(rad(p.adsPitch()), rad(p.adsYaw()), rad(p.adsRoll())))
                .translate(-p.aimX() / 16F, -p.aimY() / 16F, -p.aimZ() / 16F);
    }

    /**
     * The lowered stance in the chest view frame: the gun turned muzzle down by the kind's low pitch and yaw, its
     * {@code right_hand_anchor} at rest (gun blocks) on the kind's low hand point.
     */
    public static Matrix4f lowInChest(Profile p, Kind kind, float realScale, Matrix4f rightAnchorRest) {
        return gripAt(p, realScale, rightAnchorRest, kind.lowHand, kind.lowYaw, kind.lowPitch);
    }

    /**
     * A pistol's two-handed ready in the chest view frame: level but for the kind's ready pitch, its {@code right_hand_anchor}
     * at rest on the kind's ready hand point.
     */
    public static Matrix4f readyInChest(Profile p, Kind kind, float realScale, Matrix4f rightAnchorRest) {
        return gripAt(p, realScale, rightAnchorRest, kind.readyHand, 0F, kind.readyPitch);
    }

    /**
     * The rifle's middle key on its way off the back, in the chest view frame: swung round the right side, nearly upright
     * (muzzle up and a little out), the grip beside the right hip.
     */
    public static Matrix4f sideInChest(Profile p, float realScale, Matrix4f rightAnchorRest) {
        return gripAt(p, realScale, rightAnchorRest, new Vector3f(0.27F, -0.06F, -0.06F), -20F, 70F);
    }

    private static Matrix4f gripAt(Profile p, float realScale, Matrix4f rightAnchorRest, Vector3f at, float yaw, float pitch) {
        Matrix4f gun = sightAtOrigin(p, realScale);
        Vector3f grip = gun.transformPosition(rightAnchorRest.getTranslation(new Vector3f()));
        return new Matrix4f().translate(at).rotateY(rad(yaw)).rotateX(rad(pitch))
                .translate(-grip.x, -grip.y, -grip.z).mul(gun);
    }

    /**
     * Shouldered: the gun's frame (model blocks) with its bore along the view and its butt on the chest. {@code view}: the
     * view frame in model blocks (its rotation is taken), {@code chestSkin}: the chest bone's skinning matrix (model px).
     */
    public static Matrix4f onShoulder(Matrix4f view, Matrix4f chestSkin, Profile p, Kind kind, Carried carried) {
        Vector3f at = chestSkin.transformPosition(new Vector3f(kind.pocket)).div(16F);
        Vector3f s = view.getScale(new Vector3f());
        Quaternionf r = view.getUnnormalizedRotation(new Quaternionf());
        Vector3f butt = carried.butt();
        return new Matrix4f().translation(at).rotate(r).scale(s.x, s.y, s.z).scale(carried.scale())
                .rotate(new Quaternionf().rotationXYZ(rad(p.adsPitch()), rad(p.adsYaw()), rad(p.adsRoll())))
                .translate(-butt.x / 16F, -butt.y / 16F, -butt.z / 16F);
    }

    /**
     * The support hand's IK target under a handguard: the hand cap at {@code s.at}, lowered until the hand's highest corner
     * is {@link #SUPPORT_OVERLAP} px above the gun's underside (the fingers round it); the forearm along the support's way.
     */
    public static Matrix4f supportUnder(Matrix4f gunModel, Support s, boolean slim, Matrix4f out) {
        Quaternionf gunRot = gunModel.getNormalizedRotation(new Quaternionf());
        Vector3f cap = new Matrix4f().scaling(16F).mul(gunModel).transformPosition(new Vector3f(s.at()).div(16F));
        Vector3f elbow = new Vector3f(0, 0, 1).rotateX(rad(s.down())).rotateY(rad(-s.out()));
        Vector3f finger = gunRot.transform(elbow.negate()).normalize();
        Vector3f up = gunRot.transform(new Vector3f(0, 1, 0));
        Quaternionf r = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), finger);
        r.premul(new Quaternionf().rotationAxis(rad(s.roll()), finger.x, finger.y, finger.z));
        float w = handHalf(slim), high = -1e9F;
        for (float x : new float[]{-w, w}) for (float y : new float[]{-2F, 0F}) for (float z : new float[]{-2F, 2F})
            high = Math.max(high, r.transform(new Vector3f(x, y, z)).dot(up));
        cap.sub(up.mul(high - SUPPORT_OVERLAP));
        return out.translation(cap).rotate(r).translate(-centreX(false, slim), -distalY(), 0);
    }

    /** The chest view frame from the chest bone's skinning matrix (model px) and its rest pivot (model px). */
    public static Matrix4f chestView(Matrix4f chestSkin, Vector3f chestPivot) {
        return new Matrix4f().scaling(1F / 16F).mul(chestSkin).translate(chestPivot).scale(16F).rotateZ((float) Math.PI);
    }

    /** Rigid-with-scale blend of two gun frames: position lerped, rotation slerped, uniform scale lerped. */
    public static Matrix4f blend(Matrix4f a, Matrix4f b, float t) {
        if (t <= 0) return new Matrix4f(a);
        if (t >= 1) return new Matrix4f(b);
        Vector3f pa = a.getTranslation(new Vector3f()), pb = b.getTranslation(new Vector3f());
        float sa = a.getScale(new Vector3f()).x, sb = b.getScale(new Vector3f()).x;
        Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf()), qb = b.getUnnormalizedRotation(new Quaternionf());
        return new Matrix4f().translation(pa.lerp(pb, t)).rotate(qa.slerp(qb, t)).scale(sa + (sb - sa) * t);
    }

    /** Along a three-key path (mount, middle, stance), {@code along} 0..1. */
    public static Matrix4f path(Matrix4f mount, Matrix4f middle, Matrix4f stance, float along) {
        return along < 0.5F ? blend(mount, middle, smooth(along * 2F)) : blend(middle, stance, smooth(along * 2F - 1F));
    }

    /** One moment of a draw or put-away: the gun along its path (0 mount, 1 stance), and how much each hand is on it. */
    public record Phase(float along, float rightHand, float leftHand) {}

    /** Drawing, {@code f} 0..1 of the draw clip: the hand goes to the gun, brings it out, the support hand joins. */
    public static Phase draw(Kind kind, float f) {
        return new Phase(smooth((f - 0.3F) / 0.6F), smooth(f / 0.3F),
                kind == Kind.RIFLE ? smooth((f - 0.65F) / 0.3F) : smooth((f - 0.75F) / 0.25F));
    }

    /** Putting away, {@code f} 0..1 of the put-away clip: the reverse of {@link #draw}. */
    public static Phase putAway(Kind kind, float f) {
        return new Phase(1F - smooth((f - 0.1F) / 0.6F), 1F - smooth((f - 0.75F) / 0.25F), 1F - smooth(f / 0.25F));
    }

    /**
     * One hand's IK target: the rest arm frame (arm px, origin at the shoulder pivot) to model px, for a gun whose bare
     * frame is {@code gunModel} in model blocks and an anchor frame (gun blocks).
     */
    public static Matrix4f handTarget(Matrix4f gunModel, Matrix4f anchor, boolean right, boolean slim, Matrix4f out) {
        return handTarget(gunModel, anchor, Grip.NONE, right, slim, out);
    }

    /** {@link #handTarget} with the hand turned on the gun by {@code grip}. */
    public static Matrix4f handTarget(Matrix4f gunModel, Matrix4f anchor, Grip grip, boolean right, boolean slim, Matrix4f out) {
        // the turn in model px (the anchor frame is in scaled gun units); the palm is PALM_Y up the arm, toward the shoulder (-y)
        Matrix4f m = new Matrix4f().scaling(16F).mul(gunModel).mul(anchor);
        out.translation(m.getTranslation(new Vector3f())).rotate(m.getUnnormalizedRotation(new Quaternionf()));
        if (grip != null && (grip.pitch() != 0 || grip.yaw() != 0 || grip.roll() != 0))
            out.translate(0, -PALM_Y, 0).rotateXYZ(rad(grip.pitch()), rad(grip.yaw()), rad(grip.roll())).translate(0, PALM_Y, 0);
        return out.translate(-centreX(right, slim), -distalY(), 0);
    }

    /**
     * The support hand's slide (HumanoidPoser.Input#slideLeft): toward the stock along the bore, model px, {@code length}
     * at most, for a gun frame in model px. The bore is the aimed view line, so in the gun's frame it is the ADS rotation's
     * view +z.
     */
    public static Vector3f supportSlide(Matrix4f gunModelPx, Profile p, float length, Vector3f out) {
        if (length <= 0) return out.zero();
        Vector3f back = new Quaternionf().rotationXYZ(rad(p.adsPitch()), rad(p.adsYaw()), rad(p.adsRoll())).conjugate()
                .transform(new Vector3f(0, 0, 1));
        gunModelPx.transformDirection(back);
        return back.lengthSquared() < 1e-12F ? out.zero() : out.set(back).normalize(length);
    }

    /** The rifle's slide (P1). */
    public static Vector3f supportSlide(Matrix4f gunModelPx, Profile p, Vector3f out) {
        return supportSlide(gunModelPx, p, SUPPORT_SLIDE, out);
    }

    /**
     * A gun held out at arm's length (Kind#sightDistance): its stance (model blocks) moved back toward the eye, along the
     * view's {@code back} axis (model, unit), until both hands on their rest anchors are within reach (looking up, crouched).
     * The caller has set {@code in}'s arm modes, IK weights and shoulder offsets and solved the body; the hand targets are
     * overwritten here and set again by the caller.
     */
    public static Matrix4f inReach(Kind kind, HumanoidPoser poser, HumanoidPoser.Input in, SkeletonPose pose, Matrix4f stance,
                                   Vector3f back, Matrix4f rightRest, Matrix4f leftRest) {
        return inReach(kind, null, poser, in, pose, stance, back, rightRest, leftRest);
    }

    /**
     * {@link #inReach}; a shouldered gun ({@link Kind#pocket}) comes back at most {@link #POCKET_GIVE} (the butt into the
     * shoulder: looking far down, a reload's hand far out), its support hand under the handguard when {@code support}.
     */
    public static Matrix4f inReach(Kind kind, Support support, HumanoidPoser poser, HumanoidPoser.Input in, SkeletonPose pose,
                                   Matrix4f stance, Vector3f back, Matrix4f rightRest, Matrix4f leftRest) {
        float most;
        if (kind.pocket != null) most = POCKET_GIVE;
        else if (kind.sightDistance > 0) most = Math.max(0, kind.sightDistance - MIN_SIGHT) * 16F;
        else return stance;
        handTarget(stance, rightRest, kind.gripRight, true, in.slim, in.targetRight);
        if (support != null) supportUnder(stance, support, in.slim, in.targetLeft);
        else handTarget(stance, leftRest, kind.gripLeft, false, in.slim, in.targetLeft);
        float s = Math.min(poser.shortfall(in, pose, back), most);
        return s <= 0 ? stance : new Matrix4f().translation(new Vector3f(back).mul(s / 16F)).mul(stance);
    }

    /** How far a rifle comes toward the support shoulder for a reload or an inspect on the body (model px). */
    public static final float SUPPORT_REACH_MOST = 4F;

    /**
     * A rifle's reload or inspect on the body (BR-51 fix pass, user 2026-10-27 "可以，按你的建议改"): the clips turn the gun
     * for the first-person camera, and on the body some of their poses (the inspect showing the right side) put the support
     * hand's anchor out of its reach (1.24 x the arm). The stance moved toward the support shoulder until the left hand
     * reaches, at most {@link #SUPPORT_REACH_MOST}, as a rifleman brings the gun in to his left hand; both hands stay on
     * their anchors. Called after {@link #inReach} with the arms set up as for it.
     */
    public static Matrix4f supportReach(Kind kind, HumanoidPoser poser, HumanoidPoser.Input in, SkeletonPose pose, Matrix4f stance,
                                        Matrix4f rightAnchor, Matrix4f leftAnchor) {
        handTarget(stance, rightAnchor, kind.gripRight, true, in.slim, in.targetRight);
        handTarget(stance, leftAnchor, kind.gripLeft, false, in.slim, in.targetLeft);
        SkeletonRig rig = pose.rig;
        Vector3f wrist = in.targetLeft.transformPosition(new Vector3f(rig.pivot[rig.handL]).sub(rig.pivot[rig.upperArmL]));
        Vector3f dir = pose.world[rig.chest].transformPosition(new Vector3f(rig.pivot[rig.upperArmL])).sub(wrist);
        if (dir.lengthSquared() < 1e-6F) return stance;
        dir.normalize();
        float s = Math.min(poser.shortfall(in, pose, dir), SUPPORT_REACH_MOST);
        return s <= 0 ? stance : new Matrix4f().translation(new Vector3f(dir).mul(s / 16F)).mul(stance);
    }

    /** The eye frame: view space to a frame with world axes (blocks), an eye at {@code eye} looking yaw / pitch (deg). */
    public static Matrix4f eye(Vector3f eye, float yaw, float pitch) {
        return new Matrix4f().translation(eye).rotateY(rad(-(yaw + 180F))).rotateX(rad(-pitch));
    }

    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    private static float clamp01(float v) { return Math.max(0F, Math.min(1F, v)); }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
}
