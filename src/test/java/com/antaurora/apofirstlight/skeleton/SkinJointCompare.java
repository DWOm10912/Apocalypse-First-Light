package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The old body's joints, one ring against two (user 2026-10-26 "出一圈和两圈的近景对比图"): the vanilla rig posed by the
 * game's solver (sat, getting up, crouched, an elbow bent hard), its skin boxes drawn as SkinMesh does (one shared ring per
 * joint, half the child's turn) and with two rings 1 px apart (a quarter and three quarters of the turn), written as OBJ
 * (Blender px: z up from the ground, the front +y, the body's right +x; uv on the 64 x 64 skin) for a close-up render.
 * Since the shoulder and hip rings (SkinMesh 2.1.1) main writes each shot before (this one-ring mesh) and after (SkinMesh):
 * the solver's sit, get-up, crouch and run, standing with both upper arms turned (arms_*) or both thighs (hips_*).
 * Offline only.
 *   gradlew skinJointCompare -Pout=<dir>
 */
public final class SkinJointCompare {
    private static final float FLOOR = 24F, TEX = 64F;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--layers")) {
            layers();
            return;
        }
        Path out = Path.of(args.length > 0 ? args[0] : "build/skin_joints");
        Files.createDirectories(out);
        SkeletonRig rig = SkeletonRig.get();
        HumanoidPoser poser = new HumanoidPoser(rig, SkeletonClips.locomotion());
        // shoulders and hips (user 2026-10-26 "好，先修髋部和肩部"): each shot as before (the one-ring mesh below, SkinMesh
        // up to 2026-10-26) and after (SkinMesh now, with its shoulder and hip rings)
        record Shot(String name, String clip, float time, float armX, float armZ) {}
        for (Shot s : new Shot[]{new Shot("sat", "body.sit", 0.5F, 0, 0), new Shot("getting_up", "body.stand_up", 0.2F, 0, 0),
                new Shot("getting_up_45", "body.stand_up", 0.45F, 0, 0), new Shot("crouch", "locomotion.crouch_idle", 0.2F, 0, 0),
                new Shot("run", "locomotion.run", 0.15F, 0, 0), new Shot("arms_forward", null, 0F, -90, 0),
                new Shot("arms_out", null, 0F, 0, 90), new Shot("arms_across", null, 0F, -60, -30), new Shot("arms_back", null, 0F, 45, 0),
                new Shot("hips_90", null, 0F, -90, 0), new Shot("hips_130", null, 0F, -130, 0), new Shot("hips_90_out", null, 0F, -90, 40)}) {
            SkeletonPose pose = pose(rig, poser, s.clip(), s.time());
            boolean hips = s.name().startsWith("hips");
            if (s.clip() == null) {   // standing, both upper arms (or thighs) turned (Java model axes; z mirrored for the left)
                pose.local[hips ? rig.thighR : rig.upperArmR].set(new Quaternionf().rotationZYX((float) Math.toRadians(s.armZ()), 0, (float) Math.toRadians(s.armX())));
                pose.local[hips ? rig.thighL : rig.upperArmL].set(new Quaternionf().rotationZYX((float) Math.toRadians(-s.armZ()), 0, (float) Math.toRadians(s.armX())));
                pose.update();
            }
            for (boolean after : new boolean[]{false, true}) {
                StringBuilder o = new StringBuilder();
                int[] n = {0};
                for (SkeletonRig.SkinPart part : rig.skin) {
                    for (boolean overlay : new boolean[]{false, true}) {
                        if (!after) {
                            emit(pose, part, overlay, 1, o, n);
                            continue;
                        }
                        List<Vector3f> p = new ArrayList<>();
                        List<float[]> uv = new ArrayList<>();
                        SkinMesh.emit(pose, part, false, overlay, (x, y, z, u, v, nx, ny, nz) -> {
                            p.add(new Vector3f(x, y, z));
                            uv.add(new float[]{u * TEX, v * TEX});
                        });
                        for (int q = 0; q < p.size(); q += 4)
                            quad(o, n, p.get(q), uv.get(q)[0], uv.get(q)[1], p.get(q + 1), uv.get(q + 1)[0], uv.get(q + 1)[1],
                                    p.get(q + 2), uv.get(q + 2)[0], uv.get(q + 2)[1], p.get(q + 3), uv.get(q + 3)[0], uv.get(q + 3)[1]);
                    }
                }
                head(pose, false, o, n);
                head(pose, true, o, n);
                Files.writeString(out.resolve(s.name() + (after ? "_after" : "_before") + ".obj"), o);
            }
        }
        System.out.println("skin joints written to " + out.toAbsolutePath());
    }

    /**
     * The elbow flicker (user 2026-10-26 "手肘那边"): SkinMesh#quad picks each bent quad's diagonal by its own shape, so the
     * skin and its second layer 0.25 px out can split one band side different ways and cut through each other. Over the
     * locomotion clips: how many side quads the two layers split differently, and how far such a quad is off flat.
     */
    private static void layers() {
        SkeletonRig rig = SkeletonRig.get();
        HumanoidPoser poser = new HumanoidPoser(rig, SkeletonClips.locomotion());
        for (String clip : new String[]{"locomotion.idle", "locomotion.walk", "locomotion.run", "locomotion.crouch_walk", "body.sit", "body.stand_up"}) {
            SkeletonClips.Clip c = SkeletonClips.byKey(clip);
            int quads = 0, differ = 0;
            float worst = 0F;
            String where = "";
            for (int i = 0; i < 40; i++) {
                float t = c.length * i / 40F;
                SkeletonPose pose = pose(rig, poser, clip, t);
                for (SkeletonRig.SkinPart part : rig.skin) {
                    List<float[]> base = new ArrayList<>(), over = new ArrayList<>();
                    SkinMesh.emit(pose, part, false, false, (x, y, z, u, v, nx, ny, nz) -> base.add(new float[]{x, y, z, u * TEX - part.u(), v * TEX - part.v()}));
                    SkinMesh.emit(pose, part, false, true, (x, y, z, u, v, nx, ny, nz) -> over.add(new float[]{x, y, z, u * TEX - part.overlayU(), v * TEX - part.overlayV()}));
                    for (int q = 2; q < base.size() / 4; q++) {   // past the two end caps
                        quads++;
                        float[] b0 = base.get(q * 4), o0 = over.get(q * 4);
                        if (Math.abs(b0[3] - o0[3]) < 1e-3F && Math.abs(b0[4] - o0[4]) < 1e-3F) continue;
                        differ++;
                        float off = offFlat(base, q * 4);
                        if (off > worst) {
                            worst = off;
                            where = String.format(Locale.ROOT, "%s band %d at %.2f s", part.part(), (q - 2) / 4, t);
                        }
                    }
                }
            }
            System.out.printf(Locale.ROOT, "%-24s side quads %5d, split unlike the second layer %4d, worst %.2f px off flat (%s)%n", clip, quads, differ, worst, where);
        }
    }

    /** The fourth corner's distance from the plane of the first three (px). */
    private static float offFlat(List<float[]> v, int o) {
        Vector3f p0 = vec(v.get(o)), p1 = vec(v.get(o + 1)), p2 = vec(v.get(o + 2)), p3 = vec(v.get(o + 3));
        Vector3f n = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0));
        return n.lengthSquared() < 1e-12F ? 0F : Math.abs(new Vector3f(p3).sub(p0).dot(n.normalize()));
    }

    private static Vector3f vec(float[] a) {
        return new Vector3f(a[0], a[1], a[2]);
    }

    private static SkeletonPose pose(SkeletonRig rig, HumanoidPoser poser, String clip, float time) {
        HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, 0, 0, 0, false, false);
        in.eyeTarget = null;
        in.chestPitch = 0F;
        if (clip != null) {
            boolean[] mask = new boolean[rig.size()];
            java.util.Arrays.fill(mask, true);
            SkeletonClips.Clip c = SkeletonClips.byKey(clip);
            if (c == null) throw new IllegalStateException("no clip " + clip);
            in.stance.add(new HumanoidPoser.Input.StanceClip(c, time, 1F, mask, false));
        }
        SkeletonPose pose = new SkeletonPose(rig);
        HumanoidPoser.Report report = poser.solveBody(in, pose);
        in.right = in.left = HumanoidPoser.ArmMode.CLIP;
        poser.solveArms(in, pose, report);
        return pose;
    }

    /** A child bone's matrix {@code t} of the way through its turn off its parent (SkeletonPose#half at 0.5). */
    private static Matrix4f part(SkeletonPose pose, int b, float t) {
        Vector3f p = pose.rig.pivot[b], off = pose.offset[b];
        Quaternionf q = new Quaternionf().slerp(pose.local[b], t);
        return new Matrix4f(pose.world[pose.rig.parent[b]]).translate(off.x * t, off.y * t, off.z * t).translate(p).rotate(q).translate(-p.x, -p.y, -p.z);
    }

    /** SkinMesh#emit with {@code rings} rings per joint (1: as the game; 2: 0.5 px either side, 1/4 and 3/4 of the turn). */
    private static void emit(SkeletonPose pose, SkeletonRig.SkinPart part, boolean overlay, int rings, StringBuilder o, int[] n) {
        SkeletonRig rig = pose.rig;
        SkeletonRig.Box b = part.box(false);
        float g = overlay ? rig.overlayInflate : 0F;
        int u = overlay ? part.overlayU() : part.u(), v = overlay ? part.overlayV() : part.v();
        int w = b.w(), h = b.h(), d = b.d();
        float x0 = b.x() - g, x1 = b.x() + w + g, z0 = b.z() - g, z1 = b.z() + d + g, top = b.y() - g, stretch = (h + 2 * g) / h;
        int[] bones = part.bones();
        List<Float> level = new ArrayList<>();
        List<Matrix4f> mat = new ArrayList<>();
        level.add(0F);
        mat.add(new Matrix4f(pose.world[bones[0]]));
        for (int i = 0; i < part.cuts().length; i++) {
            int above = bones[i], below = bones[i + 1];
            boolean down = rig.parent[below] == above;   // the torso runs chest -> pelvis, up the chain
            int child = down ? below : above;
            float cut = part.cuts()[i];
            if (rings == 1) {
                level.add(cut);
                mat.add(part(pose, child, 0.5F));
            } else {
                level.add(cut - 0.5F);
                mat.add(part(pose, child, down ? 0.25F : 0.75F));
                level.add(cut + 0.5F);
                mat.add(part(pose, child, down ? 0.75F : 0.25F));
            }
        }
        level.add((float) h);
        mat.add(new Matrix4f(pose.world[bones[bones.length - 1]]));
        int r = level.size();
        Vector3f[][] ring = new Vector3f[r][4];
        for (int k = 0; k < r; k++) {
            float y = top + level.get(k) * stretch;
            Matrix4f m = mat.get(k);
            ring[k][0] = m.transformPosition(new Vector3f(x0, y, z0));
            ring[k][1] = m.transformPosition(new Vector3f(x1, y, z0));
            ring[k][2] = m.transformPosition(new Vector3f(x1, y, z1));
            ring[k][3] = m.transformPosition(new Vector3f(x0, y, z1));
        }
        box(ring, level, u, v, w, d, o, n);
    }

    /** The head and hat (vanilla's 8 px box on the head bone, uv 0,0 / 32,0, the hat 0.5 px out). */
    private static void head(SkeletonPose pose, boolean hat, StringBuilder o, int[] n) {
        float g = hat ? 0.5F : 0F;
        Matrix4f m = pose.world[pose.rig.head];
        Vector3f[][] ring = new Vector3f[2][4];
        float[] ys = {-8 - g, g};
        for (int k = 0; k < 2; k++) {
            ring[k][0] = m.transformPosition(new Vector3f(-4 - g, ys[k], -4 - g));
            ring[k][1] = m.transformPosition(new Vector3f(4 + g, ys[k], -4 - g));
            ring[k][2] = m.transformPosition(new Vector3f(4 + g, ys[k], 4 + g));
            ring[k][3] = m.transformPosition(new Vector3f(-4 - g, ys[k], 4 + g));
        }
        box(ring, List.of(0F, 8F), hat ? 32 : 0, 0, 8, 8, o, n);
    }

    /** SkinMesh's faces and UV: the two ends, then each ring-to-ring band's four sides. */
    private static void box(Vector3f[][] ring, List<Float> level, int u, int v, int w, int d, StringBuilder o, int[] n) {
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w, f10 = v, f11 = v + d;
        Vector3f[] t = ring[0], e = ring[ring.length - 1];
        quad(o, n, t[2], f6, f10, t[3], f5, f10, t[0], f5, f11, t[1], f6, f11);
        quad(o, n, e[1], f7, f11, e[0], f6, f11, e[3], f6, f10, e[2], f7, f10);
        for (int s = 0; s < ring.length - 1; s++) {
            Vector3f[] a = ring[s], c = ring[s + 1];
            float va = f11 + level.get(s), vb = f11 + level.get(s + 1);
            quad(o, n, a[0], f5, va, a[3], f4, va, c[3], f4, vb, c[0], f5, vb);
            quad(o, n, a[1], f6, va, a[0], f5, va, c[0], f5, vb, c[1], f6, vb);
            quad(o, n, a[2], f8, va, a[1], f6, va, c[1], f6, vb, c[2], f8, vb);
            quad(o, n, a[3], f9, va, a[2], f8, va, c[2], f8, vb, c[3], f9, vb);
        }
    }

    private static void quad(StringBuilder o, int[] n, Object... c) {
        for (int k = 0; k < 4; k++) {
            Vector3f p = (Vector3f) c[k * 3];
            o.append(String.format(Locale.ROOT, "v %.4f %.4f %.4f%nvt %.5f %.5f%n", -p.x, -p.z, FLOOR - p.y,
                    (Float) c[k * 3 + 1] / TEX, 1F - (Float) c[k * 3 + 2] / TEX));
        }
        int a = n[0] + 1;
        o.append(String.format("f %d/%d %d/%d %d/%d %d/%d%n", a, a, a + 1, a + 1, a + 2, a + 2, a + 3, a + 3));
        n[0] += 4;
    }
}
