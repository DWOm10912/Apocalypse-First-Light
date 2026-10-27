package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 2.1): the C-min skin mesh. Each vanilla box (torso, arms,
 * legs, and their second layer) is cut at its joints into segments; a joint's ring of four vertices is shared by both
 * segments and placed half way through the child bone's rotation, so a bent joint neither cracks nor leaves a gap. Only
 * the box's two ends get caps. Faces, vertex order and UV follow vanilla's ModelPart.Cube (64 x 64 skin, no mirror), so
 * the rest pose is the vanilla model to the pixel; an inflated layer maps its UV over the inflated length as vanilla does.
 * Normals are per quad from the posed corners. Output: Java model px and UV in 0..1.
 * <p>
 * Shoulders and hips (2.1.1, user 2026-10-26 "好，先修髋部和肩部"): a limb's top end does not turn all the way with the
 * limb, so the box no longer swings its corners out of the torso. The arm's top ring takes half the upper arm's turn (the
 * shoulder pivot is 2 px below it). The leg's top ring stays on the pelvis (it meets the torso's bottom end) and the
 * {@link #HIP} rings below it mix the pelvis's and the thigh's matrices a third more each px (linear blend skinning,
 * SkeletonPose#blend): a ring corner goes straight from where the pelvis holds it to where the thigh does, so a bent
 * hip's back rounds off instead of a sharp corner swinging out round the pivot. Turning the rings about the pivot
 * instead (part of the thigh's rotation) gave that shard; holding them below the pelvis gave a spike on the hip's side.
 * Deeply bent, the front bands fold up against the pelvis as the groin's crease, inside the belly and the thigh
 * (PlayerSkeletonCheck allows it within 1 px of the pelvis box). The thigh keeps 3 px before the knee's ring and the upper
 * arm all of its 6, more than the 2 px a bent knee or elbow ring swings along the limb. At rest all rings lie on the
 * box's faces, so the rest pose is still vanilla's.
 * <p>
 * The female body (docs/dev/female_body_v1.md, user 2026-10-27 picked D): the same bones and boxes, so every clip fits it.
 * The torso gets four more rings ({@link #WAIST}: narrower at the waist, back to the box at the hips) and the chest a
 * height field over the torso's front ({@link #chest}): two forms with a shallow valley between them, fullest 0.7 px
 * 3.6 px below the shoulders, the lower edge two arcs, all of it back in by the chest / pelvis ring (6 px). It takes the
 * shirt's front straight on, so a skin needs nothing new.
 */
public final class SkinMesh {
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    /** A part's rings from its top end down: box px along its length (0 .. h), the skinning matrix, the bone each band below a ring follows. */
    public record Rings(float[] level, Matrix4f[] matrix, int[] band) {}

    private static final float TEX = 64F;
    /** Hip rings from the leg's top end: {px down the leg, the thigh's share against the pelvis (SkeletonPose#blend)}; even steps, so no band takes a jump of the turn. */
    private static final float[][] HIP = {{0F, 0F}, {1F, 1F / 3F}, {2F, 2F / 3F}, {3F, 1F}};
    /** The female torso's rings: {px down from the shoulders, in at each side, in at the front and the back}; 0, 6 and 12 are the box's own. */
    private static final float[][] WAIST = {{4F, 0F, 0F}, {6F, 0.25F, 0.125F}, {7.5F, 0.5F, 0.25F}, {9.5F, 0F, 0F}};
    /** The female chest: its side profile through each form's middle {px down, depth out of the torso's front}. */
    private static final float[][] BUST = {{1F, 0.03F}, {2F, 0.38F}, {2.9F, 0.62F}, {3.6F, 0.7F}, {4.3F, 0.62F}, {5F, 0.38F},
            {5.6F, 0.12F}, {5.9F, 0.03F}};
    /**
     * A form's middle and where the chest ends at the sides (px from the centre line); the fullest level and where a form's
     * middle meets the torso again; the shirt across the valley (share of the depth); how much higher the lower edge ends
     * in the middle and at the sides (px).
     */
    private static final float LOBE_X = 1.55F, LOBE_HALF = 3.05F, APEX = 3.6F, LOW = 5.9F, BRIDGE = 0.8F, RISE_IN = 1F, RISE_OUT = 1.3F;
    /** The chest's grid (the offline sheet's D): 0.2 px steps across and down, the depth at each point. */
    private static final int CHEST_X = 30, CHEST_L = 24;
    private static final float[][] CHEST_DEPTH = new float[CHEST_X + 1][CHEST_L + 1];

    static {
        for (int i = 0; i <= CHEST_X; i++)
            for (int j = 0; j <= CHEST_L; j++) CHEST_DEPTH[i][j] = chestDepth(chestX(i), chestLevel(j));
    }

    private SkinMesh() {
    }

    /** One part (base or second layer) in the given pose. */
    public static void emit(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim, boolean overlay, Sink sink) {
        emit(pose, part, slim, overlay, 1F, false, sink);
    }

    public static void emit(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim, boolean overlay, float thick, Sink sink) {
        emit(pose, part, slim, overlay, thick, false, sink);
    }

    /**
     * One part, {@code thick} as wide (its box's x about the middle; 1 = as it is; depth and length as they are): the own
     * first person's narrower arms with a pistol held out (GunStance#firstPersonArm). The UV stays the box's.
     * {@code female}: the female torso and chest (the class comment).
     */
    public static void emit(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim, boolean overlay, float thick, boolean female, Sink sink) {
        SkeletonRig rig = pose.rig;
        SkeletonRig.Box b = part.box(slim);
        float g = overlay ? rig.overlayInflate : 0F;
        int u = overlay ? part.overlayU() : part.u(), v = overlay ? part.overlayV() : part.v();
        int w = b.w(), h = b.h(), d = b.d();
        float x0 = b.x() - g, x1 = b.x() + w + g, z0 = b.z() - g, z1 = b.z() + d + g, top = b.y() - g;
        if (thick != 1F) {
            float cx = b.x() + w / 2F;
            x0 = cx + (x0 - cx) * thick;
            x1 = cx + (x1 - cx) * thick;
        }
        float stretch = (h + 2 * g) / h;
        int[] bones = part.bones();
        Rings rs = rings(pose, part, slim);
        boolean torso = female && part.part().equals("body");
        if (torso) rs = waist(pose, rs);
        float[] level = rs.level();
        int rings = level.length;
        // posed corners per ring: c0 (minX, minZ), c1 (maxX, minZ), c2 (maxX, maxZ), c3 (minX, maxZ)
        Vector3f[][] ring = new Vector3f[rings][4];
        float[] ys = new float[rings];
        // the first bone's squash (the compressed torso, SkeletonPose#squash) pulls the top end toward its pivot
        float squash = pose.squash[bones[0]], pivotY = rig.pivot[bones[0]].y;
        for (int r = 0; r < rings; r++) {
            Matrix4f m = rs.matrix()[r];
            float y = top + level[r] * stretch;
            if (r == 0 && squash != 1F) y = pivotY + (y - pivotY) * squash;
            float dx = torso ? inset(level[r], 1) : 0F, dz = torso ? inset(level[r], 2) : 0F;
            ring[r][0] = m.transformPosition(new Vector3f(x0 + dx, y, z0 + dz));
            ring[r][1] = m.transformPosition(new Vector3f(x1 - dx, y, z0 + dz));
            ring[r][2] = m.transformPosition(new Vector3f(x1 - dx, y, z1 - dz));
            ring[r][3] = m.transformPosition(new Vector3f(x0 + dx, y, z1 - dz));
            ys[r] = y;
        }
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
        float f10 = v, f11 = v + d;
        // vanilla DOWN (the top end, y min) and UP (the bottom end, y max)
        Vector3f[] t = ring[0], e = ring[rings - 1];
        quad(sink, t[2], f6, f10, t[3], f5, f10, t[0], f5, f11, t[1], f6, f11);
        quad(sink, e[1], f7, f11, e[0], f6, f11, e[3], f6, f10, e[2], f7, f10);
        for (int s = 0; s < rings - 1; s++) {
            Vector3f[] a = ring[s], c = ring[s + 1];
            float va = f11 + level[s], vb = f11 + level[s + 1];
            side(sink, a, c, 0, f5, 3, f4, va, vb); // WEST (x min)
            side(sink, a, c, 1, f6, 0, f5, va, vb); // NORTH (z min, the front)
            side(sink, a, c, 2, f8, 1, f6, va, vb); // EAST (x max)
            side(sink, a, c, 3, f9, 2, f8, va, vb); // SOUTH (z max, the back)
        }
        if (torso) chest(sink, rs, ys, x0, x1, z0, f5, f6, f11);
    }

    /** The female torso's rings: the box's own and {@link #WAIST}'s, each new one on the bone of the band it falls in. */
    private static Rings waist(SkeletonPose pose, Rings rs) {
        List<Float> level = new ArrayList<>();
        List<Matrix4f> matrix = new ArrayList<>();
        List<Integer> band = new ArrayList<>();
        for (int r = 0; r < rs.level().length; r++) {
            if (r > 0) {
                for (float[] w : WAIST) {
                    if (w[0] > rs.level()[r - 1] && w[0] < rs.level()[r]) {
                        int bone = rs.band()[r - 1];
                        add(level, matrix, band, w[0], new Matrix4f(pose.world[bone]), bone);
                    }
                }
            }
            add(level, matrix, band, rs.level()[r], rs.matrix()[r], rs.band()[r]);
        }
        float[] levels = new float[level.size()];
        int[] bands = new int[band.size()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = level.get(i);
            bands[i] = band.get(i);
        }
        return new Rings(levels, matrix.toArray(new Matrix4f[0]), bands);
    }

    /** How far the female torso is in at a ring's level: {@code axis} 1 at each side, 2 at the front and the back (0 off WAIST). */
    private static float inset(float level, int axis) {
        for (float[] w : WAIST) if (Math.abs(w[0] - level) < 1e-4F) return w[axis];
        return 0F;
    }

    private static float chestX(int i) {
        return -LOBE_HALF + i * 2 * LOBE_HALF / CHEST_X;
    }

    private static float chestLevel(int j) {
        return BUST[0][0] + j * (BUST[BUST.length - 1][0] - BUST[0][0]) / CHEST_L;
    }

    /** BUST's depth at a level along a form's middle. */
    private static float sideProfile(float l) {
        for (int k = 0; k < BUST.length - 1; k++) {
            float l0 = BUST[k][0], l1 = BUST[k + 1][0];
            if (l >= l0 && l <= l1) return BUST[k][1] + (BUST[k + 1][1] - BUST[k][1]) * (l - l0) / (l1 - l0);
        }
        return 0F;
    }

    /**
     * The chest's depth at x (px from the centre line) and a level: each form's middle has BUST's profile; outward the form
     * rounds off to the side, between the two the shirt sags only to {@link #BRIDGE}; below the fullest point the profile
     * is squeezed to end at the lower edge, which rises toward the middle and the sides.
     */
    private static float chestDepth(float x, float l) {
        float ax = Math.abs(x), across, low;
        if (ax >= LOBE_HALF) return 0F;
        if (ax >= LOBE_X) {
            float u = (ax - LOBE_X) / (LOBE_HALF - LOBE_X);
            across = (float) Math.sqrt(Math.max(0F, 1F - u * u));
            low = LOW - RISE_OUT * u * u;
        } else {
            float u = 1F - ax / LOBE_X;
            across = 1F - (1F - BRIDGE) * u * u;
            low = LOW - RISE_IN * (float) Math.pow(u, 1.5);
        }
        if (l > APEX) l = APEX + (l - APEX) * (LOW - APEX) / Math.max(0.2F, low - APEX);
        return sideProfile(l) * across;
    }

    /**
     * The female chest over the torso's front: each grid point placed between the two rings around its level as the front
     * face is there (so it bends with the torso), then pushed out by its depth; UV the front face's straight on, corner
     * order and winding as vanilla's NORTH face.
     */
    private static void chest(Sink sink, Rings rs, float[] ys, float x0, float x1, float z0, float f5, float f6, float f11) {
        float[] level = rs.level();
        Vector3f[][] p = new Vector3f[CHEST_X + 1][CHEST_L + 1];
        float cx = (x0 + x1) / 2F;
        Vector3f a = new Vector3f(), b = new Vector3f();
        for (int j = 0; j <= CHEST_L; j++) {
            float l = chestLevel(j);
            int r = 0;
            while (r < level.length - 2 && l > level[r + 1]) r++;
            float t = (l - level[r]) / (level[r + 1] - level[r]);
            float front = z0 + inset(level[r], 2) + (inset(level[r + 1], 2) - inset(level[r], 2)) * t + 0.01F;
            for (int i = 0; i <= CHEST_X; i++) {
                float x = cx + chestX(i), z = front - CHEST_DEPTH[i][j];
                rs.matrix()[r].transformPosition(x, ys[r], z, a);
                rs.matrix()[r + 1].transformPosition(x, ys[r + 1], z, b);
                p[i][j] = new Vector3f(a).lerp(b, t);
            }
        }
        float du = (f6 - f5) / (x1 - x0);
        for (int i = 0; i < CHEST_X; i++) {
            float ua = f5 + (cx + chestX(i) - x0) * du, ub = f5 + (cx + chestX(i + 1) - x0) * du;
            for (int j = 0; j < CHEST_L; j++) {
                float deepest = Math.max(Math.max(CHEST_DEPTH[i][j], CHEST_DEPTH[i + 1][j]), Math.max(CHEST_DEPTH[i][j + 1], CHEST_DEPTH[i + 1][j + 1]));
                if (deepest < 0.004F) continue;
                float va = f11 + chestLevel(j), vb = f11 + chestLevel(j + 1);
                quad(sink, p[i + 1][j], ub, va, p[i][j], ua, va, p[i][j + 1], ua, vb, p[i + 1][j + 1], ub, vb);
            }
        }
    }

    /**
     * A part's rings: its first bone at the top end, its last bone at the bottom end, half way at the joints; a limb
     * (its first bone's parent outside the part) also gets its shoulder or hip rings (the class comment).
     */
    public static Rings rings(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim) {
        SkeletonRig rig = pose.rig;
        int[] bones = part.bones(), cuts = part.cuts();
        int root = bones[0], parent = rig.parent[root];
        List<Float> level = new ArrayList<>();
        List<Matrix4f> matrix = new ArrayList<>();
        List<Integer> band = new ArrayList<>();
        boolean limb = parent >= 0;
        for (int bone : bones) limb &= bone != parent;
        float pivot = rig.pivot[root].y - part.box(slim).y();
        if (limb && pivot >= 1F) {   // shoulder: the pivot below the top end
            add(level, matrix, band, 0F, pose.half(root, new Matrix4f()), root);
        } else if (limb) {           // hip: the pivot at the top end
            for (float[] r : HIP) add(level, matrix, band, r[0], pose.blend(root, r[1], new Matrix4f()), root);
        } else {
            add(level, matrix, band, 0F, new Matrix4f(pose.world[root]), root);
        }
        for (int i = 0; i < cuts.length; i++) {
            int above = bones[i], below = bones[i + 1];
            int child = rig.parent[below] == above ? below : above;
            add(level, matrix, band, cuts[i], pose.half(child, new Matrix4f()), below);
        }
        add(level, matrix, band, part.box(slim).h(), new Matrix4f(pose.world[bones[bones.length - 1]]), -1);
        float[] levels = new float[level.size()];
        int[] bands = new int[band.size()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = level.get(i);
            bands[i] = band.get(i);
        }
        return new Rings(levels, matrix.toArray(new Matrix4f[0]), bands);
    }

    private static void add(List<Float> level, List<Matrix4f> matrix, List<Integer> band, float at, Matrix4f m, int bone) {
        level.add(at);
        matrix.add(m);
        band.add(bone);
    }

    private static void side(Sink sink, Vector3f[] a, Vector3f[] c, int ca, float ua, int cb, float ub, float va, float vb) {
        quad(sink, a[ca], ua, va, a[cb], ub, va, c[cb], ub, vb, c[ca], ua, vb);
    }

    /** One quad to the sink, split so a bent one does not fold (also the Block-Mesh V2 body, client/skeleton/BlockMeshBody). */
    public static void quad(Sink sink, Vector3f p0, float u0, float v0, Vector3f p1, float u1, float v1,
                             Vector3f p2, float u2, float v2, Vector3f p3, float u3, float v3) {
        // A bent joint makes a side quad non-planar or concave; quads draw as triangles 0-1-2 and 2-3-0, so start at
        // the corner whose diagonal keeps both triangles facing the same way (no fold). Flat quads keep vanilla's order.
        if (fold(p0, p1, p2, p3) < fold(p1, p2, p3, p0)) {
            emit(sink, p1, u1, v1, p2, u2, v2, p3, u3, v3, p0, u0, v0);
            return;
        }
        emit(sink, p0, u0, v0, p1, u1, v1, p2, u2, v2, p3, u3, v3);
    }

    /** How alike the two triangles of the 0-2 split face (cosine of their normals; 1 = flat). */
    private static float fold(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        Vector3f a = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0)), b = new Vector3f(p2).sub(p0).cross(new Vector3f(p3).sub(p0));
        float l = a.length() * b.length();
        return l < 1e-12F ? 1F : a.dot(b) / l - 1e-4F;
    }

    private static void emit(Sink sink, Vector3f p0, float u0, float v0, Vector3f p1, float u1, float v1,
                             Vector3f p2, float u2, float v2, Vector3f p3, float u3, float v3) {
        Vector3f n = new Vector3f(p2).sub(p0).cross(new Vector3f(p3).sub(p1));
        if (n.lengthSquared() < 1e-12F) n.set(0, -1, 0);
        else n.normalize();
        sink.vertex(p0.x, p0.y, p0.z, u0 / TEX, v0 / TEX, n.x, n.y, n.z);
        sink.vertex(p1.x, p1.y, p1.z, u1 / TEX, v1 / TEX, n.x, n.y, n.z);
        sink.vertex(p2.x, p2.y, p2.z, u2 / TEX, v2 / TEX, n.x, n.y, n.z);
        sink.vertex(p3.x, p3.y, p3.z, u3 / TEX, v3 / TEX, n.x, n.y, n.z);
    }
}
