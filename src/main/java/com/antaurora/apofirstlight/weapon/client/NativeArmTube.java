package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.skeleton.GunStance;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The first-person gun arm as one tube (docs/rendering/first_person_body_v1.md 9.1, user 2026-10-27 "PASS，开始做断肘和袖子吧"):
 * the Vanilla arm box as NativePlayerArmRenderer draws it, its near end bent at an elbow toward the shoulder as the third
 * person's skin mesh bends (SkinMesh: one ring shared by the forearm and the upper arm, turned half the bend), the upper arm
 * the box's cross-section running on out of the picture, so the box's hollow back end never shows. Offline:
 * tools/preview-fp-arms.mjs (armTube), sheets design/characters/fp_arm_stump_v1/.
 * <ul>
 * <li>The shoulder: the third person's shoulder pivot off the eye (rig afl_humanoid_v1: the pivot [+-5, 22, 0], the eye
 * socket [0, 27.63, -2.5], the chest pivot y 18) with the gun kind's shrug and chest twist (GunStance.Kind), at the
 * player's 0.9375, in view space (+x right, +y up, -z ahead). The upper arm turns {@link #BEND_SHARE} of the way to it,
 * keeping the shoulder's distance (user 2026-10-27 picked 60%: all the way, the view model's smaller gun folded the
 * elbow past 120 deg).</li>
 * <li>Texture: the forearm box keeps Vanilla's faces and UV but for its near end; the upper arm takes the box's top
 * {@link #UPPER_ROWS} rows mirrored, so the skin and a garment's sleeve run on across the elbow.</li>
 * <li>A layer: the skin (inflate 0), the sleeve layer (0.25), a garment's shell (its inflate, cut at its length down the
 * arm as SkinMesh#shell cuts it, an end cap at the cut).</li>
 * </ul>
 * At rest (idle, aimed) the elbow lies out of the picture, so the old view model's look is unchanged.
 */
public final class NativeArmTube {
    /** User 2026-10-27: "60%，开始写吧". */
    public static final float BEND_SHARE = 0.6F;
    /** The skin rows the upper arm takes from the box's top end (px). */
    public static final float UPPER_ROWS = 4F;
    private static final float ARM_H = 12F, ARM_D = 4F, TOP = -2F;

    private NativeArmTube() {}

    /** The bend at one arm: the elbow (view space), the axis and angle the upper arm turns by, its length. */
    public record Elbow(Vector3f at, Vector3f axis, float angle, float length) {}

    /** Vanilla's arm box in the zeroed part frame (px): x from, width (the 4 x 12 x 4 Classic, 3 px wide Slim arm). */
    private static float boxX(boolean right, boolean slim) {
        return right ? (slim ? -2F : -3F) : -1F;
    }

    private static int boxW(boolean slim) {
        return slim ? 3 : 4;
    }

    /** The third person's shoulder pivot off the eye, view space blocks (the class comment). */
    public static Vector3f shoulder(GunStance.Kind kind, boolean right) {
        Vector3f s = right ? kind.shoulderRight : kind.shoulderLeft;
        float side = right ? 1F : -1F, x = side * (5F - s.x), y = 4F - s.y, z = s.z;
        double a = Math.toRadians(kind.chestTwist);
        float cx = (float) (x * Math.cos(a) - z * Math.sin(a)), cz = (float) (x * Math.sin(a) + z * Math.cos(a));   // the left shoulder forward
        float k = 0.9375F / 16F;
        return new Vector3f(cx * k, (y - 9.63F) * k, (cz + 2.5F) * k);
    }

    /** The elbow of the arm drawn through {@code pose} (NativePlayerArmRenderer's arm frame: model px / 16 to view space). */
    public static Elbow elbow(Matrix4f pose, boolean right, boolean slim, Vector3f shoulder) {
        float cx = boxX(right, slim) + boxW(slim) / 2F;
        Vector3f at = pose.transformPosition(new Vector3f(cx / 16F, TOP / 16F, 0F));
        Vector3f up = pose.transformDirection(new Vector3f(0F, -1F, 0F)).normalize();   // from the hand toward the elbow
        Vector3f to = new Vector3f(shoulder).sub(at);
        float length = to.length();
        if (length < 1e-4F) return new Elbow(at, new Vector3f(1F, 0F, 0F), 0F, 0F);
        to.div(length);
        Vector3f axis = new Vector3f(up).cross(to);
        float sin = axis.length(), full = (float) Math.atan2(sin, up.dot(to));
        if (sin < 1e-6F) return new Elbow(at, new Vector3f(1F, 0F, 0F), 0F, length);
        return new Elbow(at, axis.div(sin), full * BEND_SHARE, length);
    }

    /**
     * One layer of the arm: inflated {@code g} px, its UV at ({@code u}, {@code v}) on the 64 skin net, down to {@code to}
     * px of the 12 (12: the hand's end with its cap). {@code elbow} null: the plain box (a garment's sleeve on a gun without
     * the elbow).
     */
    public static void emit(Matrix4f pose, boolean right, boolean slim, Elbow elbow, float g, int u, int v, float to,
                            VertexConsumer consumer, int light, int overlay) {
        float bx = boxX(right, slim);
        int w = boxW(slim);
        float x0 = bx - g, x1 = bx + w + g, z0 = -ARM_D / 2F - g, z1 = ARM_D / 2F + g, top = TOP - g, stretch = (ARM_H + 2F * g) / ARM_H;
        float d = ARM_D;
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w, f10 = v, f11 = v + d;
        SkinMesh.Sink sink = (x, y, z, tu, tv, nx, ny, nz) -> consumer.vertex(x, y, z, 1F, 1F, 1F, 1F, tu, tv, overlay, light, nx, ny, nz);
        // corners c0 (minX, minZ), c1 (maxX, minZ), c2 (maxX, maxZ), c3 (minX, maxZ), as SkinMesh
        Vector3f[] near = ring(pose, x0, x1, z0, z1, top), end = ring(pose, x0, x1, z0, z1, top + to * stretch);
        if (elbow == null || elbow.angle() == 0F && elbow.length() == 0F) {
            cap(sink, near, true, f5, f6, f7, f10, f11);
            sides(sink, near, end, f4, f5, f6, f8, f9, f11, f11 + to);
            cap(sink, end, false, f5, f6, f7, f10, f11);
            return;
        }
        Vector3f[] mid = new Vector3f[4], far = new Vector3f[4];
        Vector3f dir = turn(pose.transformDirection(new Vector3f(0F, -1F, 0F)).normalize(), elbow, 1F).mul(elbow.length());
        for (int k = 0; k < 4; k++) {
            Vector3f off = new Vector3f(near[k]).sub(elbow.at());
            mid[k] = turn(new Vector3f(off), elbow, 0.5F).add(elbow.at());
            far[k] = turn(off, elbow, 1F).add(elbow.at()).add(dir);
        }
        // the upper arm: the far ring is its top end, the texture's rows mirrored back from the elbow
        cap(sink, far, true, f5, f6, f7, f10, f11);
        sides(sink, far, mid, f4, f5, f6, f8, f9, f11 + UPPER_ROWS, f11);
        sides(sink, mid, end, f4, f5, f6, f8, f9, f11, f11 + to);
        cap(sink, end, false, f5, f6, f7, f10, f11);
    }

    private static Vector3f turn(Vector3f v, Elbow e, float share) {
        return v.rotateAxis(e.angle() * share, e.axis().x, e.axis().y, e.axis().z);
    }

    private static Vector3f[] ring(Matrix4f pose, float x0, float x1, float z0, float z1, float y) {
        return new Vector3f[]{pose.transformPosition(new Vector3f(x0 / 16F, y / 16F, z0 / 16F)),
                pose.transformPosition(new Vector3f(x1 / 16F, y / 16F, z0 / 16F)),
                pose.transformPosition(new Vector3f(x1 / 16F, y / 16F, z1 / 16F)),
                pose.transformPosition(new Vector3f(x0 / 16F, y / 16F, z1 / 16F))};
    }

    /** Vanilla DOWN (the top end) or UP (the bottom end), as SkinMesh. */
    private static void cap(SkinMesh.Sink sink, Vector3f[] r, boolean topEnd, float f5, float f6, float f7, float f10, float f11) {
        if (topEnd) SkinMesh.quad(sink, r[2], f6, f10, r[3], f5, f10, r[0], f5, f11, r[1], f6, f11);
        else SkinMesh.quad(sink, r[1], f7, f11, r[0], f6, f11, r[3], f6, f10, r[2], f7, f10);
    }

    /** The four sides between ring a (the top) and ring c, rows va .. vb, as SkinMesh#side. */
    private static void sides(SkinMesh.Sink sink, Vector3f[] a, Vector3f[] c, float f4, float f5, float f6, float f8, float f9,
                              float va, float vb) {
        side(sink, a, c, 0, f5, 3, f4, va, vb);   // WEST (x min)
        side(sink, a, c, 1, f6, 0, f5, va, vb);   // NORTH (z min)
        side(sink, a, c, 2, f8, 1, f6, va, vb);   // EAST (x max)
        side(sink, a, c, 3, f9, 2, f8, va, vb);   // SOUTH (z max)
    }

    private static void side(SkinMesh.Sink sink, Vector3f[] a, Vector3f[] c, int ca, float ua, int cb, float ub, float va, float vb) {
        SkinMesh.quad(sink, a[ca], ua, va, a[cb], ub, va, c[cb], ub, vb, c[ca], ua, vb);
    }
}
