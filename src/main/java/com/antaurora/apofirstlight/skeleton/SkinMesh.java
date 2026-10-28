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
 * height field over the torso's front ({@link #chest}): two rounded domes from adult anthropometry (r4, the constants'
 * comment), fullest 1.05 px 3.5 px below the shoulders, the fold at 4.65 px. A garment's shell over it bridges the
 * cleavage and hangs from the fullest point. It takes the skin's front straight on, so a skin needs nothing new.
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
    /**
     * The female chest r4 (user 2026-10-27 in game: "胸罩遮不住，下面露出来了……查一下真实的人体结构，现在形状也不圆，
     * 并且可能有点太小了"), from adult anthropometry at 1 px = 5.6 cm (1.8 m = 32 px): sternal notch to nipple about
     * 18.5 cm, nipple to the inframammary fold about 6.8 cm, nipples about 20 cm apart, base 12-14 cm wide, projection
     * 4 +- 1.3 cm. Each breast a dome on the chest wall round its nipple ({@link #LOBE_X}, {@link #APEX} down): the upper
     * pole a long gentle slope up to the second rib, the lower pole short and round down to the fold, a little narrower
     * toward the middle (the cleavage) than out to the side; {@link #PROJECTION} out at the nipple, the upper end of the
     * normal range so it reads from the front.
     */
    private static final float LOBE_X = 1.75F, APEX = 3.5F, PROJECTION = 1.05F;
    /** The dome's reach from the nipple (px): toward the middle, out to the side, up, down to the fold. */
    private static final float REACH_IN = 1.2F, REACH_OUT = 1.35F, REACH_UP = 2.1F, REACH_DOWN = 1.15F;
    /** How the dome falls off: the upper pole blends into the chest (above 1), the lower pole stays full to the fold (below 1). */
    private static final float FALL_UP = 1.25F, FALL_DOWN = 0.5F;
    /**
     * Clothes over it (SkinMesh#shell): cloth bridges the cleavage (at least this share of the nipple's height between
     * the two) and hangs from the fullest point instead of following the fold underneath, falling no faster than
     * {@link #DRAPE} px a px down.
     */
    private static final float BRIDGE = 0.92F, DRAPE = 0.7F;
    /** The chest's grid: 0.2 px steps across and down from {@link #CHEST_TOP}; the bare depth and the clothed one at each point. */
    private static final float CHEST_HALF = 3.3F, CHEST_TOP = 1.2F, CHEST_LOW = 5.2F;
    private static final int CHEST_X = 33, CHEST_L = 20;
    private static final float[][] CHEST_DEPTH = new float[CHEST_X + 1][CHEST_L + 1], CHEST_CLOTH = new float[CHEST_X + 1][CHEST_L + 1];

    static {
        for (int i = 0; i <= CHEST_X; i++) {
            float hang = 0F, step = (CHEST_LOW - CHEST_TOP) / CHEST_L;
            for (int j = 0; j <= CHEST_L; j++) {
                float x = chestX(i), l = chestLevel(j), d = chestDepth(x, l);
                CHEST_DEPTH[i][j] = d;
                float bridge = Math.abs(x) < LOBE_X ? BRIDGE * chestDepth(LOBE_X, l) * (1F + (1F / BRIDGE - 1F) * (x / LOBE_X) * (x / LOBE_X)) : 0F;
                float c = Math.max(d, bridge);
                hang = l > APEX ? Math.max(c, hang - DRAPE * step) : c;
                CHEST_CLOTH[i][j] = hang;
            }
        }
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
        float g = overlay ? pose.rig.overlayInflate : 0F;
        int u = overlay ? part.overlayU() : part.u(), v = overlay ? part.overlayV() : part.v();
        emit(pose, part, slim, g, u, v, thick, female, null, sink);
    }

    /**
     * A garment's shell over one part (old-body clothes V1, docs/dev/old_body_clothes_v1.md): the part's own rings (the
     * female torso and chest too), {@code inflate} px out, cut to the levels {@code from .. to} (px down the part; a cut
     * ring lies on the shell between its two rings, so the cut end bends with the body), the front pushed out by up to
     * {@code toe} px from {@code toeFrom} down (a shoe's toe; 0: none). The UV is the part's base layer on the vanilla
     * 64 net, so a garment texture is a skin with only the garment painted.
     */
    public record Shell(float inflate, float from, float to, float toeFrom, float toe) {
        public Shell(float inflate, float from, float to) {
            this(inflate, from, to, 0F, 0F);
        }
    }

    public static void shell(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim, float thick, boolean female, Shell s, Sink sink) {
        emit(pose, part, slim, s.inflate(), part.u(), part.v(), thick, female, s, sink);
    }

    /** How long the toe takes to reach its full {@link Shell#toe} (px down the leg): the instep's slope. */
    private static final float INSTEP = 0.75F;

    private static void emit(SkeletonPose pose, SkeletonRig.SkinPart part, boolean slim, float g, int u, int v, float thick, boolean female,
                             Shell shell, Sink sink) {
        SkeletonRig rig = pose.rig;
        SkeletonRig.Box b = part.box(slim);
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
        if (torso) rs = insert(pose, rs, WAIST_LEVELS);
        boolean toe = shell != null && shell.toe() > 0F;
        if (toe) rs = insert(pose, rs, new float[]{shell.toeFrom(), shell.toeFrom() + INSTEP});
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
            float ahead = toe ? shell.toe() * Math.max(0F, Math.min(1F, (level[r] - shell.toeFrom()) / INSTEP)) : 0F;
            ring[r][0] = m.transformPosition(new Vector3f(x0 + dx, y, z0 + dz - ahead));
            ring[r][1] = m.transformPosition(new Vector3f(x1 - dx, y, z0 + dz - ahead));
            ring[r][2] = m.transformPosition(new Vector3f(x1 - dx, y, z1 - dz));
            ring[r][3] = m.transformPosition(new Vector3f(x0 + dx, y, z1 - dz));
            ys[r] = y;
        }
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
        float f10 = v, f11 = v + d;
        if (torso) {
            boolean chest = shell == null || shell.from() <= CHEST_TOP && shell.to() >= CHEST_LOW;
            float from = shell == null ? 0F : Math.max(0F, shell.from()), to = shell == null ? h : Math.min(h, shell.to());
            torso(sink, rs, ring, ys, x0, x1, z0, d, from, to, chest ? (shell == null ? CHEST_DEPTH : CHEST_CLOTH) : null,
                    f4, f5, f6, f7, f8, f9, f10, f11);
            return;
        }
        // a garment's cut: the rings between its ends, a ring on the shell at each cut
        float[] lv = level;
        if (shell != null && (shell.from() > 0F || shell.to() < h)) {
            List<Vector3f[]> cr = new ArrayList<>();
            List<Float> cl = new ArrayList<>();
            float from = Math.max(0F, shell.from()), to = Math.min(h, shell.to());
            for (int r = 0; r < rings; r++) {
                if (r > 0 && level[r - 1] < from && level[r] > from) cut(cr, cl, ring[r - 1], ring[r], level[r - 1], level[r], from);
                if (level[r] >= from - 1e-4F && level[r] <= to + 1e-4F) {
                    cr.add(ring[r]);
                    cl.add(level[r]);
                }
                if (r > 0 && level[r - 1] < to && level[r] > to) cut(cr, cl, ring[r - 1], ring[r], level[r - 1], level[r], to);
            }
            ring = cr.toArray(new Vector3f[0][]);
            lv = new float[cl.size()];
            for (int i = 0; i < lv.length; i++) lv[i] = cl.get(i);
        }
        int n = ring.length;
        // vanilla DOWN (the top end, y min) and UP (the bottom end, y max)
        Vector3f[] t = ring[0], e = ring[n - 1];
        quad(sink, t[2], f6, f10, t[3], f5, f10, t[0], f5, f11, t[1], f6, f11);
        quad(sink, e[1], f7, f11, e[0], f6, f11, e[3], f6, f10, e[2], f7, f10);
        for (int s = 0; s < n - 1; s++) {
            Vector3f[] a = ring[s], c = ring[s + 1];
            float va = f11 + lv[s], vb = f11 + lv[s + 1];
            side(sink, a, c, 0, f5, 3, f4, va, vb); // WEST (x min)
            side(sink, a, c, 1, f6, 0, f5, va, vb); // NORTH (z min, the front)
            side(sink, a, c, 2, f8, 1, f6, va, vb); // EAST (x max)
            side(sink, a, c, 3, f9, 2, f8, va, vb); // SOUTH (z max, the back)
        }
    }

    /** A ring at {@code at} between two posed rings (on the straight quads between them), into the cut list. */
    private static void cut(List<Vector3f[]> cr, List<Float> cl, Vector3f[] a, Vector3f[] b, float la, float lb, float at) {
        float t = (at - la) / (lb - la);
        Vector3f[] r = new Vector3f[4];
        for (int k = 0; k < 4; k++) r[k] = new Vector3f(a[k]).lerp(b[k], t);
        cr.add(r);
        cl.add(at);
    }

    private static final float[] WAIST_LEVELS = new float[WAIST.length];

    static {
        for (int i = 0; i < WAIST.length; i++) WAIST_LEVELS[i] = WAIST[i][0];
    }

    /** More rings at the given levels (the female torso's WAIST, a shoe's instep): each on the bone of the band it falls in. */
    private static Rings insert(SkeletonPose pose, Rings rs, float[] at) {
        List<Float> level = new ArrayList<>();
        List<Matrix4f> matrix = new ArrayList<>();
        List<Integer> band = new ArrayList<>();
        for (int r = 0; r < rs.level().length; r++) {
            if (r > 0) {
                for (float l : at) {
                    if (l > rs.level()[r - 1] + 1e-4F && l < rs.level()[r] - 1e-4F) {
                        int bone = rs.band()[r - 1];
                        add(level, matrix, band, l, new Matrix4f(pose.world[bone]), bone);
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
        return -CHEST_HALF + i * 2 * CHEST_HALF / CHEST_X;
    }

    private static float chestLevel(int j) {
        return CHEST_TOP + j * (CHEST_LOW - CHEST_TOP) / CHEST_L;
    }

    /** How the dome falls off to the middle and the side (between FALL_UP and FALL_DOWN). */
    private static final float FALL_SIDE = 0.75F;

    /**
     * The bare chest's depth at x (px from the centre line) and a level: the deeper of the two domes. A dome is
     * {@link #PROJECTION} (1 - r^2)^e, r the distance from the nipple over the reach that way, e from FALL_UP straight up
     * through FALL_SIDE across to FALL_DOWN straight down, so the top slopes into the chest and the bottom curves round to
     * the fold.
     */
    private static float chestDepth(float x, float l) {
        float dx = Math.abs(x) - LOBE_X, dy = l - APEX;
        float nx = dx / (dx < 0F ? REACH_IN : REACH_OUT), ny = dy / (dy < 0F ? REACH_UP : REACH_DOWN);
        float rr = nx * nx + ny * ny;
        if (rr >= 1F) return 0F;
        float up = rr > 1e-6F ? ny * ny / rr : 0F;
        float e = FALL_SIDE + ((dy < 0F ? FALL_UP : FALL_DOWN) - FALL_SIDE) * up;
        return PROJECTION * (float) Math.pow(1F - rr, e);
    }

    /**
     * The female torso (and a garment's shell over it) as one surface, so nothing lies on top of anything (user 2026-10-27
     * in game: the zip and the buttons "一直左右闪烁", the bra's edges shaking). Rows at every ring, the garment's cut ends
     * and, over the chest, every grid level; each row's corners between the two rings around it as the straight faces are
     * there. The front and the back in the chest grid's columns (the front pushed out by the chest's depth there, placed
     * between the two rings as the face is, so it bends with the torso), the sides one column a px. Narrow columns keep
     * the waist's trapezoids from bending the texture (a vertical line on a wide trapezoid kinks at its diagonal, and the
     * kink jumped whenever the diagonal flipped). Shared edges have the same vertices, so no seams and no T-junctions.
     */
    private static void torso(Sink sink, Rings rs, Vector3f[][] ring, float[] ys, float x0, float x1, float z0, int depthPx,
                              float from, float to, float[][] chest, float f4, float f5, float f6, float f7, float f8, float f9,
                              float f10, float f11) {
        float[] level = rs.level();
        // the rows: {level, chest grid row or -1}
        List<float[]> rows = new ArrayList<>();
        rows.add(new float[]{from, -1});
        for (float l : level) if (l > from + 1e-3F && l < to - 1e-3F) rows.add(new float[]{l, -1});
        rows.add(new float[]{to, -1});
        if (chest != null) for (int j = 0; j <= CHEST_L; j++) {
            float l = chestLevel(j);
            if (l < from - 1e-3F || l > to + 1e-3F) continue;
            boolean merged = false;
            for (float[] row : rows) {
                if (Math.abs(row[0] - l) < 1e-3F) {
                    row[0] = l;
                    row[1] = j;
                    merged = true;
                }
            }
            if (!merged) rows.add(new float[]{l, j});
        }
        rows.sort((p, q) -> Float.compare(p[0], q[0]));
        // the front's and the back's columns: the box's edges and the chest grid's x, as shares of the width
        float cx = (x0 + x1) / 2F, w = x1 - x0;
        float[] col = new float[CHEST_X + 3];
        col[0] = 0F;
        for (int i = 0; i <= CHEST_X; i++) col[i + 1] = (cx + chestX(i) - x0) / w;
        col[CHEST_X + 2] = 1F;
        int n = rows.size(), cols = col.length;
        Vector3f[][] corner = new Vector3f[n][4], front = new Vector3f[n][cols], back = new Vector3f[n][cols];
        Vector3f a = new Vector3f(), b = new Vector3f();
        for (int k = 0; k < n; k++) {
            float l = rows.get(k)[0];
            int j = (int) rows.get(k)[1], r = 0;
            while (r < level.length - 2 && l > level[r + 1]) r++;
            float t = Math.max(0F, Math.min(1F, (l - level[r]) / (level[r + 1] - level[r])));
            for (int c = 0; c < 4; c++) corner[k][c] = new Vector3f(ring[r][c]).lerp(ring[r + 1][c], t);
            for (int i = 0; i < cols; i++) {
                back[k][i] = new Vector3f(corner[k][3]).lerp(corner[k][2], col[i]);
                float dep = chest != null && j >= 0 && i > 0 && i < cols - 1 ? chest[i - 1][j] : 0F;
                if (dep <= 0F) {
                    front[k][i] = new Vector3f(corner[k][0]).lerp(corner[k][1], col[i]);
                    continue;
                }
                // as emit makes the ring corners, pushed out by the depth, between the two rings
                float sa = inset(level[r], 1), sb = inset(level[r + 1], 1);
                float xa = x0 + sa + (w - 2 * sa) * col[i], xb = x0 + sb + (w - 2 * sb) * col[i];
                rs.matrix()[r].transformPosition(xa, ys[r], z0 + inset(level[r], 2) - dep, a);
                rs.matrix()[r + 1].transformPosition(xb, ys[r + 1], z0 + inset(level[r + 1], 2) - dep, b);
                front[k][i] = new Vector3f(a).lerp(b, t);
            }
        }
        // vanilla DOWN (the top end) and UP (the bottom end): front-to-back strips in the front's columns, a row a px deep
        for (int i = 0; i < cols - 1; i++) {
            float ua = f5 + (f6 - f5) * col[i], ub = f5 + (f6 - f5) * col[i + 1];
            float uc = f6 + (f7 - f6) * col[i], ud = f6 + (f7 - f6) * col[i + 1];
            for (int q = 0; q < depthPx; q++) {
                float qa = q / (float) depthPx, qb = (q + 1) / (float) depthPx;
                float va = f11 + (f10 - f11) * qa, vb = f11 + (f10 - f11) * qb;
                Vector3f t0 = cap(front[0][i], back[0][i], qa), t1 = cap(front[0][i + 1], back[0][i + 1], qa),
                        t2 = cap(front[0][i + 1], back[0][i + 1], qb), t3 = cap(front[0][i], back[0][i], qb);
                quad(sink, t2, ub, vb, t3, ua, vb, t0, ua, va, t1, ub, va);
                Vector3f e0 = cap(front[n - 1][i], back[n - 1][i], qa), e1 = cap(front[n - 1][i + 1], back[n - 1][i + 1], qa),
                        e2 = cap(front[n - 1][i + 1], back[n - 1][i + 1], qb), e3 = cap(front[n - 1][i], back[n - 1][i], qb);
                quad(sink, e1, ud, va, e0, uc, va, e3, uc, vb, e2, ud, vb);
            }
        }
        for (int k = 0; k < n - 1; k++) {
            Vector3f[] ca = corner[k], cb = corner[k + 1];
            float va = f11 + rows.get(k)[0], vb = f11 + rows.get(k + 1)[0];
            columns(sink, ca, cb, 0, f5, 3, f4, depthPx, va, vb); // WEST (x min)
            columns(sink, ca, cb, 2, f8, 1, f6, depthPx, va, vb); // EAST (x max)
            for (int i = cols - 1; i > 0; i--) {                    // NORTH (the front), x max to x min as vanilla's
                float ua = f5 + (f6 - f5) * col[i], ub = f5 + (f6 - f5) * col[i - 1];
                quad(sink, front[k][i], ua, va, front[k][i - 1], ub, va, front[k + 1][i - 1], ub, vb, front[k + 1][i], ua, vb);
            }
            for (int i = 0; i < cols - 1; i++) {                    // SOUTH (the back), x min to x max
                float ua = f9 + (f8 - f9) * col[i], ub = f9 + (f8 - f9) * col[i + 1];
                quad(sink, back[k][i], ua, va, back[k][i + 1], ub, va, back[k + 1][i + 1], ub, vb, back[k + 1][i], ua, vb);
            }
        }
    }

    private static Vector3f cap(Vector3f front, Vector3f back, float q) {
        return new Vector3f(front).lerp(back, q);
    }

    /** A side face between two rows from corner {@code ca} to {@code cb}, cut into {@code count} columns. */
    private static void columns(Sink sink, Vector3f[] a, Vector3f[] c, int ca, float ua, int cb, float ub, int count, float va, float vb) {
        for (int i = 0; i < count; i++) {
            float sa = i / (float) count, sb = (i + 1) / (float) count;
            float u0 = ua + (ub - ua) * sa, u1 = ua + (ub - ua) * sb;
            quad(sink, new Vector3f(a[ca]).lerp(a[cb], sa), u0, va, new Vector3f(a[ca]).lerp(a[cb], sb), u1, va,
                    new Vector3f(c[ca]).lerp(c[cb], sb), u1, vb, new Vector3f(c[ca]).lerp(c[cb], sa), u0, vb);
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
        // Only a clearly bent quad switches: a nearly flat one (breathing, a slight waist twist) flipping its diagonal every
        // frame moved the texture on a trapezoid back and forth (the female waist's zip, user 2026-10-27).
        float keep = fold(p0, p1, p2, p3);
        if (keep < FLAT && keep < fold(p1, p2, p3, p0)) {
            emit(sink, p1, u1, v1, p2, u2, v2, p3, u3, v3, p0, u0, v0);
            return;
        }
        emit(sink, p0, u0, v0, p1, u1, v1, p2, u2, v2, p3, u3, v3);
    }

    /** A quad whose two triangles are closer than this (cosine, about 2.6 degrees) keeps vanilla's diagonal. */
    private static final float FLAT = 0.999F;

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
