package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 2.1): the C-min skin mesh. Each vanilla box (torso, arms,
 * legs, and their second layer) is cut at its joints into segments; a joint's ring of four vertices is shared by both
 * segments and placed half way through the child bone's rotation, so a bent joint neither cracks nor leaves a gap. Only
 * the box's two ends get caps. Faces, vertex order and UV follow vanilla's ModelPart.Cube (64 x 64 skin, no mirror), so
 * the rest pose is the vanilla model to the pixel; an inflated layer maps its UV over the inflated length as vanilla does.
 * Normals are per quad from the posed corners. Output: Java model px and UV in 0..1.
 */
public final class SkinMesh {
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private static final float TEX = 64F;

    private SkinMesh() {
    }

    /** One part (base or second layer) in the given pose. */
    public static void emit(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim, boolean overlay, Sink sink) {
        SkeletonRig rig = pose.rig;
        SkeletonRig.Box b = part.box(slim);
        float g = overlay ? rig.overlayInflate : 0F;
        int u = overlay ? part.overlayU() : part.u(), v = overlay ? part.overlayV() : part.v();
        int w = b.w(), h = b.h(), d = b.d();
        float x0 = b.x() - g, x1 = b.x() + w + g, z0 = b.z() - g, z1 = b.z() + d + g, top = b.y() - g;
        float stretch = (h + 2 * g) / h;
        int[] bones = part.bones();
        int rings = bones.length + 1;
        int[] level = new int[rings];
        level[0] = 0;
        for (int i = 0; i < part.cuts().length; i++) level[i + 1] = part.cuts()[i];
        level[rings - 1] = h;
        // posed corners per ring: c0 (minX, minZ), c1 (maxX, minZ), c2 (maxX, maxZ), c3 (minX, maxZ)
        Vector3f[][] ring = new Vector3f[rings][4];
        Matrix4f m = new Matrix4f();
        // the first bone's squash (the compressed torso, SkeletonPose#squash) pulls the top end toward its pivot
        float squash = pose.squash[bones[0]], pivotY = rig.pivot[bones[0]].y;
        for (int r = 0; r < rings; r++) {
            ringMatrix(pose, bones, r, m);
            float y = top + level[r] * stretch;
            if (r == 0 && squash != 1F) y = pivotY + (y - pivotY) * squash;
            ring[r][0] = m.transformPosition(new Vector3f(x0, y, z0));
            ring[r][1] = m.transformPosition(new Vector3f(x1, y, z0));
            ring[r][2] = m.transformPosition(new Vector3f(x1, y, z1));
            ring[r][3] = m.transformPosition(new Vector3f(x0, y, z1));
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
    }

    /** Ring r of a part: its first bone at the top end, its last bone at the bottom end, half way at the joints. */
    public static Matrix4f ringMatrix(SkeletonPose pose, int[] bones, int r, Matrix4f out) {
        if (r == 0) return out.set(pose.world[bones[0]]);
        if (r == bones.length) return out.set(pose.world[bones[bones.length - 1]]);
        int above = bones[r - 1], below = bones[r];
        int child = pose.rig.parent[below] == above ? below : above;
        return pose.half(child, out);
    }

    private static void side(Sink sink, Vector3f[] a, Vector3f[] c, int ca, float ua, int cb, float ub, float va, float vb) {
        quad(sink, a[ca], ua, va, a[cb], ub, va, c[cb], ub, vb, c[ca], ua, vb);
    }

    private static void quad(Sink sink, Vector3f p0, float u0, float v0, Vector3f p1, float u1, float v1,
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
