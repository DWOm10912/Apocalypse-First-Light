package com.antaurora.apofirstlight.skeleton.action;

import com.antaurora.apofirstlight.skeleton.GunStance;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Function;

/**
 * Player actions V1 (docs/dev/player_actions_v1_plan.md 2.2-2.5): one action at one moment onto the poser's input.
 * Looks are a pure function of (action, seconds since it started, seconds since it stopped, the parameter, the anchors), so
 * every client draws the same thing from the same state (design 10.6 rule 2). Two steps around HumanoidPoser#solveBody:
 * <ol>
 * <li>{@link #body}: the clips on the mask's bones, the step up to the anchor and the facing;</li>
 * <li>{@link #hands}: each hand's target from its timeline (body sockets, the action's anchors), IK weights and poles.
 * The hand is turned as the forearm the IK will give it, untwisted (no wrist bend, no forearm twist), then {@code roll}
 * about it. A change between
 * two targets is eased (smoothstep) and turns about the palm (docs/dev/gun_handling_reference_v1.md 4.5: the BR51
 * reload's end turned the arm's root frame and swung the palm in an arc).</li>
 * </ol>
 * Model px throughout (Java: y down, front -z, the player's right -x). Pure.
 */
public final class ActionPose {
    /** Model px per block (PlayerRenderer scales the model by 0.9375). */
    public static final float PX_PER_BLOCK = 16F / 0.9375F;
    private static final Vector3f POLE_R = new Vector3f(-0.35F, 1F, 0.45F), POLE_L = new Vector3f(0.35F, 1F, 0.45F);

    private ActionPose() {
    }

    /** One action at one moment. */
    public static final class Moment {
        public PlayerAction action;
        /** Seconds since it started; since it stopped (negative: still running). */
        public float seconds, stopped = -1F;
        /**
         * Seconds since it showed (its blend-in), NaN: {@link #seconds}. A source whose time runs back and forth (the jerry
         * can's pour) is not eased in again with it.
         */
        public float age = Float.NaN;
        /** The source's parameter 0..1 (the parameter clip's weight): a pull's travel, how low a fuel opening is. */
        public float param;
        /** Played by the left side (a left-handed player, the item in the left hand). */
        public boolean mirror;
        /** The action's anchors (model px), or null for one it does not know. */
        public Function<String, Vector3f> anchors = name -> null;
        /** Out ({@link #hands}): the held prop's placement this moment (its model frame to model px), or null. */
        public Matrix4f prop;
        /** Out ({@link #hands}): the prop is drawn open (PlayerAction#propOpenFrom). */
        public boolean propOpen;
    }

    /** How much of the action shows: eased in over its blend-in, out over its blend-out after it stopped. */
    public static float weight(Moment m) {
        float in = m.action.blendIn <= 0 ? 1F : GunStance.smooth((Float.isNaN(m.age) ? m.seconds : m.age) / m.action.blendIn);
        float out = m.stopped < 0 ? 1F : m.action.blendOut <= 0 ? 0F : 1F - GunStance.smooth(m.stopped / m.action.blendOut);
        return Math.max(0F, Math.min(in, out));
    }

    /** Before solveBody: the clips, the root's step and facing. False when the action does not show at all. */
    public static boolean body(HumanoidPoser.Input in, SkeletonRig rig, Moment m) {
        float w = weight(m);
        if (w <= 0) return false;
        PlayerAction a = m.action;
        in.actionMask = a.mask(rig, m.mirror);
        in.actionMirror = m.mirror;
        in.actionClip = a.clip == null ? null : SkeletonClips.byKey(a.clip);
        in.actionTime = m.seconds;
        in.actionWeight = w;
        in.paramClip = a.paramClip == null ? null : SkeletonClips.byKey(a.paramClip);
        in.paramWeight = w * Math.max(0F, Math.min(1F, m.param));
        if (a.approach != null) {
            Vector3f anchor = m.anchors.apply(a.approach.anchor());
            if (anchor != null) {
                float wa = w * (a.approach.in() > 0 ? GunStance.smooth(m.seconds / a.approach.in()) : 1F);
                // stand `distance` from the anchor on the player's side and `side` to the left, at most `max` from where
                // the player is (the root is at the model's x = z = 0; facing -z, the left is +x)
                Vector3f flat = new Vector3f(-anchor.x, 0, -anchor.z);
                float length = flat.length();
                if (length > 1e-3F) flat.div(length);
                else flat.set(0, 0, 1);
                Vector3f left = new Vector3f(flat.z, 0, -flat.x);   // the left of a body facing -flat
                Vector3f stand = new Vector3f(anchor.x, 0, anchor.z).add(new Vector3f(flat).mul(a.approach.distance() * PX_PER_BLOCK))
                        .add(left.mul((m.mirror ? -1 : 1) * a.approach.side() * PX_PER_BLOCK));   // a left-handed player stands to the other side
                float max = a.approach.max() * PX_PER_BLOCK;
                if (stand.length() > max) stand.mul(max / stand.length());
                in.rootOffset.set(stand).mul(wa);
                boolean square = a.approach.face().equals(a.approach.anchor());
                Vector3f face = square ? new Vector3f(stand).sub(flat) : m.anchors.apply(a.approach.face());
                if (face != null) {
                    float dx = face.x - stand.x, dz = face.z - stand.z;
                    if (dx * dx + dz * dz > 1e-4F) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));   // front (-z) turned toward the face anchor
                        in.rootYaw = in.rootYaw + wrap(yaw - in.rootYaw) * wa;
                        in.chestYaw *= 1F - wa;
                        in.chestPitch *= 1F - wa;   // the clip bends the body; the player's view does not add to it
                    }
                }
                float pitch = pitch(m);
                if (!Float.isNaN(pitch)) {
                    in.viewYaw = in.viewYaw + wrap(in.rootYaw - in.viewYaw) * wa;
                    in.viewPitch = in.viewPitch + (pitch - in.viewPitch) * wa;
                }
                if (wa > 0) in.eyeTarget = null;   // the body steps away from the eye on purpose (first person: the camera follows it)
            }
        }
        return true;
    }

    /** The view pitch (deg, + = down) a stepping action gives the head and the first-person view, or NaN for the player's own. */
    public static float pitch(Moment m) {
        PlayerAction.Approach a = m.action.approach;
        if (a == null || Float.isNaN(a.pitch())) return Float.NaN;
        return a.pitch() + (a.pitchHigh() - a.pitch()) * Math.max(0F, Math.min(1F, m.param));
    }

    /**
     * The own first-person camera while an action steps the body (model px): on the posed neck, the eye's rest offset from
     * it turned with the root only. As Minecraft's own camera, a look down does not lower it: the posed eye goes down in
     * front of the chest with the head, where the bent arms fill the screen (in-game, 2026-10-16: the jerry can's pour).
     */
    public static Vector3f cameraEye(SkeletonPose pose) {
        SkeletonRig rig = pose.rig;
        SkeletonRig.Socket s = rig.sockets.get("eye");
        Vector3f neck = pose.point(rig.head, rig.pivot[rig.head], new Vector3f());
        Vector3f rest = new Vector3f(s.pos()).sub(rig.pivot[rig.head]);
        return pose.rotation(rig.root, new Quaternionf()).transform(rest).add(neck);
    }

    /** The posed body's facing (model, level, unit): the root's front. */
    public static Vector3f facing(SkeletonPose pose) {
        Vector3f f = pose.rotation(pose.rig.root, new Quaternionf()).transform(new Vector3f(0, 0, -1));
        f.y = 0;
        return f.lengthSquared() < 1e-8F ? new Vector3f(0, 0, -1) : f.normalize();
    }

    /** After solveBody: the prop's placement ({@link Moment#prop}), then both hands' targets. */
    public static void hands(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Moment m) {
        m.prop = prop(pose, m);
        m.propOpen = m.prop != null && m.seconds >= m.action.propOpenFrom;
        float w = weight(m);
        if (w <= 0) return;
        List<PlayerAction.HandKey> right = m.mirror ? m.action.left : m.action.right,
                left = m.mirror ? m.action.right : m.action.left;
        hand(in, pose, poser, m, right, true, w);
        hand(in, pose, poser, m, left, false, w);
    }

    private static void hand(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Moment m, List<PlayerAction.HandKey> keys,
                             boolean right, float w) {
        if (keys.isEmpty()) return;
        int k = 0;
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).at() <= m.seconds) k = i;
        PlayerAction.HandKey cur = keys.get(k), prev = k > 0 ? keys.get(k - 1) : null;
        float p = prev == null ? 1F : cur.blend() <= 0 ? 1F : GunStance.smooth((m.seconds - cur.at()) / cur.blend());
        // the pole first: the hands are turned along the forearm the IK gives with it
        Vector3f pole = pole(cur, right, m.mirror);
        if (prev != null && p < 1F) pole = new Vector3f(pole(prev, right, m.mirror)).lerp(pole, p);
        (right ? in.poleRight : in.poleLeft).set(pole);
        Matrix4f to = cur.target().pose() ? null : target(in, pose, poser, m, cur, right);
        Matrix4f from = prev == null || prev.target().pose() ? null : target(in, pose, poser, m, prev, right);
        Matrix4f target;
        float weight;
        if (to == null && from == null) {
            weight = 0F;
            target = null;
        } else if (to == null) {
            weight = 1F - p;
            target = from;
        } else if (from == null) {
            weight = prev == null ? 1F : p;
            target = to;
        } else {
            weight = 1F;
            target = blend(from, to, p, right, in.slim);
        }
        if (right) in.right = HumanoidPoser.ArmMode.IK;
        else in.left = HumanoidPoser.ArmMode.IK;
        if (right) in.ikRightWeight = weight * w;
        else in.ikLeftWeight = weight * w;
        if (target != null) (right ? in.targetRight : in.targetLeft).set(target);
    }

    private static Vector3f pole(PlayerAction.HandKey key, boolean right, boolean mirror) {
        if (key.pole() == null) return new Vector3f(right ? POLE_R : POLE_L);
        Vector3f p = new Vector3f(key.pole());
        if (mirror) p.x = -p.x;
        return p;
    }

    /** A key's IK target (the rest arm frame to model px), or null when it resolves to nothing. */
    static Matrix4f target(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Moment m, PlayerAction.HandKey key, boolean right) {
        PlayerAction.Target t = m.mirror ? key.target().mirrored() : key.target();
        Vector3f grip = switch (t.kind()) {
            case "socket" -> {
                SkeletonRig.Socket s = pose.rig.sockets.get(t.name());
                yield s == null ? null : pose.point(s.bone(), s.pos(), new Vector3f());
            }
            case "block" -> m.anchors.apply(t.name());
            case "prop" -> {
                PlayerProps.Prop prop = m.action.prop == null || m.prop == null ? null : PlayerProps.get(m.action.prop);
                Vector3f local = prop == null ? null : point(prop, t.name(), m.mirror);
                yield local == null ? null : m.prop.transformPosition(local);
            }
            default -> null;   // held:, object:, clip_ik, entity: come with the later stages
        };
        if (grip == null) return null;
        return hold(poser.forearm(in, pose, right, grip), grip, right, in.slim, m.mirror ? -key.roll() : key.roll());
    }

    /**
     * The action's prop placed at this moment (its model frame to model px, PlayerProps.Prop#local), between the keys
     * about the gripped point, or null without a prop. Mirrored for the left side: the placement reflected across the
     * body's middle and the prop across its own symmetry plane (PlayerProps.Prop#reflection), which together are a turn,
     * so the prop is drawn as itself; a prop without one keeps its turn reflected only.
     */
    public static Matrix4f prop(SkeletonPose pose, Moment m) {
        PlayerAction a = m.action;
        if (a.prop == null || a.propKeys.isEmpty()) return null;
        PlayerProps.Prop prop = PlayerProps.get(a.prop);
        if (prop == null) return null;
        int k = 0;
        for (int i = 0; i < a.propKeys.size(); i++) if (a.propKeys.get(i).at() <= m.seconds) k = i;
        Matrix4f to = place(pose, prop, a.propKeys.get(k), m);
        while (to == null && k > 0) to = place(pose, prop, a.propKeys.get(--k), m);   // its anchor unknown: the key before
        PlayerAction.PropKey cur = a.propKeys.get(k), prev = k > 0 ? a.propKeys.get(k - 1) : null;
        if (to == null || prev == null) return to;
        float p = cur.blend() <= 0 ? 1F : GunStance.smooth((m.seconds - cur.at()) / cur.blend());
        if (p >= 1F) return to;
        Matrix4f from = place(pose, prop, prev, m);
        if (from == null) return to;
        Vector3f pivot = point(prop, cur.point(), m.mirror);
        Vector3f pa = from.transformPosition(new Vector3f(pivot)), pb = to.transformPosition(new Vector3f(pivot));
        Quaternionf qa = from.getUnnormalizedRotation(new Quaternionf()), qb = to.getUnnormalizedRotation(new Quaternionf());
        if (qa.dot(qb) < 0) qb.set(-qb.x, -qb.y, -qb.z, -qb.w);
        return new Matrix4f().translation(pa.lerp(pb, p)).rotate(qa.slerp(qb, p)).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /**
     * A named point of the prop in its model frame as drawn: a mirrored placement shows the prop's reflection, so the point
     * that looks like the named one is its reflection (the same point on the symmetry plane). Null when unknown.
     */
    public static Vector3f point(PlayerProps.Prop prop, String name, boolean mirror) {
        Vector3f local = prop.local(name);
        Matrix4f reflection = mirror && local != null ? prop.reflection() : null;
        return reflection == null ? local : reflection.transformPosition(local);
    }

    private static Matrix4f place(SkeletonPose pose, PlayerProps.Prop prop, PlayerAction.PropKey key, Moment m) {
        boolean mirror = m.mirror;
        Vector3f local = prop.local(key.point());
        if (local == null) return null;
        Vector3f rest = new Vector3f(key.pos());
        Quaternionf turn = new Quaternionf().rotateX((float) Math.toRadians(key.rot().x)).rotateY((float) Math.toRadians(key.rot().y))
                .rotateZ((float) Math.toRadians(key.rot().z));
        Matrix4f reflection = mirror ? prop.reflection() : null;
        if (mirror) {
            rest.x = -rest.x;
            // M R S (M: across x, S: the prop's own reflection) is a turn; without S, M R M
            if (reflection != null) {
                Vector3f d = new Vector3f(1, 1, 1);
                d.setComponent(prop.symmetry(), -1);
                turn = new Matrix4f().scale(-1, 1, 1).rotate(turn).scale(d).getNormalizedRotation(new Quaternionf());
            }
            else turn.set(turn.x, -turn.y, -turn.z, turn.w);
        }
        local = point(prop, key.point(), mirror);
        Vector3f at;
        Quaternionf r;
        if (key.frame().startsWith("anchor:")) {
            Vector3f anchor = m.anchors.apply(key.frame().substring(7));
            if (anchor == null) return null;
            Quaternionf root = pose.rotation(pose.rig.root, new Quaternionf());
            at = new Vector3f(anchor).add(root.transform(rest));
            r = root.mul(turn);
        } else if (key.frame().startsWith("shoulder_")) {
            boolean right = key.frame().equals("shoulder_r") != mirror;
            int upper = right ? pose.rig.upperArmR : pose.rig.upperArmL;
            Quaternionf root = pose.rotation(pose.rig.root, new Quaternionf());
            at = pose.point(upper, pose.rig.pivot[upper], new Vector3f()).add(root.transform(rest));
            r = root.mul(turn);
        } else {
            int bone = key.frame().equals("root") ? pose.rig.root : pose.rig.chest;
            at = pose.point(bone, rest, new Vector3f());
            r = pose.rotation(bone, new Quaternionf()).mul(turn);
        }
        return new Matrix4f().translation(at).rotate(r).translate(-local.x, -local.y, -local.z);
    }

    /**
     * A view anchor in the world (blocks): from the eye, the view's yaw and pitch (Minecraft's deg: yaw 0 looks +z, pitch +
     * down); mirrored, the right is the left.
     */
    public static org.joml.Vector3d viewAnchor(PlayerAction.ViewAnchor a, org.joml.Vector3d eye, float yaw, float pitch, boolean mirror) {
        return viewAnchor(a, eye, yaw, pitch, mirror, false);
    }

    /** {@link #viewAnchor}, with the anchor's own first-person offset when {@code firstPerson} (the own view) and it has one. */
    public static org.joml.Vector3d viewAnchor(PlayerAction.ViewAnchor a, org.joml.Vector3d eye, float yaw, float pitch, boolean mirror,
                                               boolean firstPerson) {
        Vector3f offset = firstPerson && a.firstPerson() != null ? a.firstPerson() : a.offset();
        double y = Math.toRadians(yaw), p = a.look() ? Math.toRadians(pitch) : 0;
        org.joml.Vector3d ahead = new org.joml.Vector3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        org.joml.Vector3d right = new org.joml.Vector3d(-Math.cos(y), 0, -Math.sin(y));
        org.joml.Vector3d up = new org.joml.Vector3d(right).cross(ahead);
        double r = mirror ? -offset.x : offset.x;
        return new org.joml.Vector3d(eye).fma(r, right).fma(offset.y, up).fma(offset.z, ahead);
    }

    /** The hand with its palm on {@code grip} (model px), turned as {@code forearm} (no twist), then {@code roll} deg about it. */
    public static Matrix4f hold(Quaternionf forearm, Vector3f grip, boolean right, boolean slim, float roll) {
        Quaternionf r = new Quaternionf(forearm);
        if (roll != 0) r.rotateY((float) Math.toRadians(roll));
        Vector3f cap = r.transform(new Vector3f(0, 1, 0)).mul(GunStance.PALM_Y).add(grip);
        return new Matrix4f().translation(cap).rotate(r).translate(-GunStance.centreX(right, slim), -GunStance.DISTAL_Y, 0);
    }

    /**
     * The hand with its palm on {@code grip} (model px), the hand straight on from the shoulder's line to it, turned
     * {@code roll} deg about that line.
     */
    public static Matrix4f reach(SkeletonPose pose, Vector3f grip, boolean right, boolean slim, float roll) {
        SkeletonRig rig = pose.rig;
        int upper = right ? rig.upperArmR : rig.upperArmL;
        Vector3f shoulder = pose.point(rig.chest, rig.pivot[upper], new Vector3f());
        return hold(new Vector3f(grip).sub(shoulder), grip, right, slim, roll);
    }

    /** The hand with its palm on {@code grip} (model px), pointing {@code along} (toward the fingers), turned {@code roll} deg about it. */
    public static Matrix4f hold(Vector3f along, Vector3f grip, boolean right, boolean slim, float roll) {
        Vector3f d = new Vector3f(along);
        if (d.lengthSquared() < 1e-6F) d.set(0, 1, 0);
        d.normalize();
        Quaternionf r = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d);
        if (roll != 0) r.premul(new Quaternionf().rotationAxis((float) Math.toRadians(roll), d.x, d.y, d.z));
        Vector3f cap = new Vector3f(d).mul(GunStance.PALM_Y).add(grip);
        return new Matrix4f().translation(cap).rotate(r).translate(-GunStance.centreX(right, slim), -GunStance.DISTAL_Y, 0);
    }

    /** Between two IK targets: the palm's place straight, the hand's turn the short way, both by {@code t}. */
    public static Matrix4f blend(Matrix4f a, Matrix4f b, float t, boolean right, boolean slim) {
        if (t <= 0) return new Matrix4f(a);
        if (t >= 1) return new Matrix4f(b);
        Vector3f palm = new Vector3f(GunStance.centreX(right, slim), GunStance.DISTAL_Y - GunStance.PALM_Y, 0);
        Vector3f pa = a.transformPosition(new Vector3f(palm)), pb = b.transformPosition(new Vector3f(palm));
        Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf()), qb = b.getUnnormalizedRotation(new Quaternionf());
        if (qa.dot(qb) < 0) qb.set(-qb.x, -qb.y, -qb.z, -qb.w);
        return new Matrix4f().translation(pa.lerp(pb, t)).rotate(qa.slerp(qb, t)).translate(-palm.x, -palm.y, -palm.z);
    }

    private static float wrap(float deg) {
        deg %= 360F;
        if (deg >= 180F) deg -= 360F;
        if (deg < -180F) deg += 360F;
        return deg;
    }
}
