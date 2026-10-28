package com.antaurora.apofirstlight.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player skeleton V1 P1 numeric checks (docs/dev/player_skeleton_v1_design.md 8.3, 5.4, 10.11). Standalone, no game
 * bootstrap: the same rig, clips, poser, skin mesh and rifle stance classes the game uses. Run: gradlew skeletonCheck.
 * <ol>
 * <li>rest pose to the pixel: every quad of the skin mesh (Steve, Alex, both layers) lies on a face of vanilla's Cube
 * with vanilla's UV at every corner, and the quads cover each vanilla face exactly;</li>
 * <li>joints at their limits: the mesh stays closed (every edge shared by two quads: no crack) and no quad folds or turns
 * inside out;</li>
 * <li>BR51 held and aimed at real size: both hands within the arm's length, elbows within range;</li>
 * <li>camera clearance (5.4): nothing drawn closer than 0.12 blocks to the eye, the shoulders never in the top two
 * thirds of the picture, and empty hands nowhere on screen while looking level or up.</li>
 * </ol>
 */
public final class PlayerSkeletonCheck {
    private static int failures;
    private static final float PX = 0.9375F / 16F;

    public static void main(String[] args) throws Exception {
        SkeletonRig rig = SkeletonRig.get();
        HumanoidPoser poser = new HumanoidPoser(rig, SkeletonClips.locomotion());
        if (System.getenv("AFL_LAB6") != null) {
            SupportLab.reloadEnd(rig, poser);
            return;
        }
        if (System.getenv("AFL_LAB5") != null) {
            SupportLab.debugRight(rig, poser);
            return;
        }
        if (System.getenv("AFL_LAB4") != null) {
            SupportLab.extended(rig, poser);
            return;
        }
        if (System.getenv("AFL_LAB3") != null) {
            SupportLab.shoulder(rig, poser);
            return;
        }
        if (System.getenv("AFL_LAB2") != null) {
            SupportLab.run(rig, poser);
            return;
        }
        if (System.getenv("AFL_SNAP_ACTIONS") != null) {   // the player actions alone, with their pictures
            System.exit(ActionCheck.run(rig, poser, System.getenv("AFL_SNAP_ACTIONS")) > 0 ? 1 : 0);
        }
        if (System.getenv("AFL_SNAP_SEAT") != null) {   // the seated riders alone, with their pictures
            System.exit(SeatCheck.run(rig, poser, System.getenv("AFL_SNAP_SEAT")) > 0 ? 1 : 0);
        }
        if (System.getenv("AFL_SNAP") != null) {
            SkeletonSnapshot.run(rig, poser, System.getenv("AFL_SNAP"));
            return;
        }
        restPixels(rig, false);
        restPixels(rig, true);
        joints(rig);
        rifle(rig, poser);
        clips(rig, poser);
        grips(rig, poser);
        clearance(rig, poser);
        failures += ActionCheck.run(rig, poser, null);
        failures += SeatCheck.run(rig, poser, null);
        if (failures > 0) {
            System.out.println("\nskeleton checks: " + failures + " FAILED");
            System.exit(1);
        }
        System.out.println("\nskeleton checks: all passed");
    }

    private static void fail(String message) {
        failures++;
        System.out.println("  FAIL " + message);
    }

    // ------------------------------------------------------------------ mesh capture

    record Quad(Vector3f[] p, float[] u, float[] v, int part, boolean overlay, int index) {
        Vector3f normal() {
            return new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[1])).normalize();
        }
    }

    static List<Quad> mesh(SkeletonPose pose, boolean slim, boolean overlays, java.util.function.IntPredicate parts) {
        List<Quad> quads = new ArrayList<>();
        SkeletonRig rig = pose.rig;
        for (int i = 0; i < rig.skin.size(); i++) {
            if (!parts.test(i)) continue;
            for (boolean overlay : overlays ? new boolean[]{false, true} : new boolean[]{false}) {
                final int part = i;
                List<float[]> verts = new ArrayList<>();
                SkinMesh.emit(pose, rig.skin.get(i), slim, overlay, (x, y, z, u, v, nx, ny, nz) -> verts.add(new float[]{x, y, z, u, v}));
                for (int q = 0; q < verts.size() / 4; q++) {
                    Vector3f[] p = new Vector3f[4];
                    float[] u = new float[4], v = new float[4];
                    for (int k = 0; k < 4; k++) {
                        float[] a = verts.get(q * 4 + k);
                        p[k] = new Vector3f(a[0], a[1], a[2]);
                        u[k] = a[3];
                        v[k] = a[4];
                    }
                    quads.add(new Quad(p, u, v, part, overlay, q));
                }
            }
        }
        return quads;
    }

    // ------------------------------------------------------------------ 1. rest pose against vanilla's Cube

    /** Vanilla ModelPart.Cube's six polygons (net.minecraft.client.model.geom.ModelPart.Cube, 1.20.1), 64 x 64. */
    private static List<Quad> vanillaCube(SkeletonRig.Box b, int u, int v, float g) {
        float x0 = b.x() - g, y0 = b.y() - g, z0 = b.z() - g, x1 = b.x() + b.w() + g, y1 = b.y() + b.h() + g, z1 = b.z() + b.d() + g;
        Vector3f v7 = new Vector3f(x0, y0, z0), v0 = new Vector3f(x1, y0, z0), v1 = new Vector3f(x1, y1, z0), v2 = new Vector3f(x0, y1, z0),
                v3 = new Vector3f(x0, y0, z1), v4 = new Vector3f(x1, y0, z1), v5 = new Vector3f(x1, y1, z1), v6 = new Vector3f(x0, y1, z1);
        int w = b.w(), h = b.h(), d = b.d();
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
        float f10 = v, f11 = v + d, f12 = v + d + h;
        List<Quad> faces = new ArrayList<>();
        faces.add(polygon(new Vector3f[]{v4, v3, v7, v0}, f5, f10, f6, f11));   // DOWN
        faces.add(polygon(new Vector3f[]{v1, v2, v6, v5}, f6, f11, f7, f10));   // UP
        faces.add(polygon(new Vector3f[]{v7, v3, v6, v2}, f4, f11, f5, f12));   // WEST
        faces.add(polygon(new Vector3f[]{v0, v7, v2, v1}, f5, f11, f6, f12));   // NORTH
        faces.add(polygon(new Vector3f[]{v4, v0, v1, v5}, f6, f11, f8, f12));   // EAST
        faces.add(polygon(new Vector3f[]{v3, v4, v5, v6}, f8, f11, f9, f12));   // SOUTH
        return faces;
    }

    /** ModelPart.Polygon: vertex 0 -> (u1, v0), 1 -> (u0, v0), 2 -> (u0, v1), 3 -> (u1, v1). */
    private static Quad polygon(Vector3f[] p, float u0, float v0, float u1, float v1) {
        return new Quad(p, new float[]{u1 / 64F, u0 / 64F, u0 / 64F, u1 / 64F}, new float[]{v0 / 64F, v0 / 64F, v1 / 64F, v1 / 64F}, -1, false, -1);
    }

    private static void restPixels(SkeletonRig rig, boolean slim) {
        SkeletonPose pose = new SkeletonPose(rig);
        pose.update();
        int quads = 0;
        String name = slim ? "Alex (slim)" : "Steve (classic)";
        for (int i = 0; i < rig.skin.size(); i++) {
            SkeletonRig.SkinPart part = rig.skin.get(i);
            for (boolean overlay : new boolean[]{false, true}) {
                final int index = i;
                List<Quad> ours = mesh(pose, slim, true, k -> k == index).stream().filter(q -> q.overlay() == overlay).toList();
                List<Quad> faces = vanillaCube(part.box(slim), overlay ? part.overlayU() : part.u(), overlay ? part.overlayV() : part.v(),
                        overlay ? rig.overlayInflate : 0F);
                double[] covered = new double[faces.size()];
                for (Quad q : ours) {
                    quads++;
                    int face = -1;
                    for (int f = 0; f < faces.size(); f++) if (onFace(q, faces.get(f))) face = f;
                    if (face < 0) { fail(name + " " + part.part() + (overlay ? " layer" : "") + ": quad " + q.index() + " on no vanilla face"); continue; }
                    Quad f = faces.get(face);
                    for (int k = 0; k < 4; k++) {
                        float[] uv = uvAt(f, q.p()[k]);
                        if (Math.abs(uv[0] - q.u()[k]) > 1e-5F || Math.abs(uv[1] - q.v()[k]) > 1e-5F)
                            fail(name + " " + part.part() + (overlay ? " layer" : "") + ": quad " + q.index() + " corner " + k + " UV "
                                    + q.u()[k] * 64 + "," + q.v()[k] * 64 + " vanilla " + uv[0] * 64 + "," + uv[1] * 64);
                    }
                    covered[face] += area(q);
                }
                for (int f = 0; f < faces.size(); f++)
                    if (Math.abs(covered[f] - area(faces.get(f))) > 1e-3)
                        fail(name + " " + part.part() + (overlay ? " layer" : "") + ": face " + f + " covered " + covered[f] + " of " + area(faces.get(f)));
            }
        }
        System.out.println("rest pose, " + name + ": " + quads + " quads checked against vanilla's cubes (positions, UV, coverage)");
    }

    private static boolean onFace(Quad q, Quad face) {
        Vector3f n = face.normal();
        if (q.normal().dot(n) < 0.9999F) return false;
        float plane = n.dot(face.p()[0]);
        Vector3f lo = new Vector3f(face.p()[0]).min(face.p()[2]).min(face.p()[1]).min(face.p()[3]);
        Vector3f hi = new Vector3f(face.p()[0]).max(face.p()[2]).max(face.p()[1]).max(face.p()[3]);
        for (Vector3f p : q.p()) {
            if (Math.abs(n.dot(p) - plane) > 1e-4F) return false;
            if (p.x < lo.x - 1e-4F || p.y < lo.y - 1e-4F || p.z < lo.z - 1e-4F || p.x > hi.x + 1e-4F || p.y > hi.y + 1e-4F || p.z > hi.z + 1e-4F)
                return false;
        }
        return true;
    }

    /** The vanilla polygon's UV at a point on it (affine over the rectangle, from corners 0, 1, 3). */
    private static float[] uvAt(Quad f, Vector3f p) {
        Vector3f a = f.p()[0], ab = new Vector3f(f.p()[1]).sub(a), ad = new Vector3f(f.p()[3]).sub(a), ap = new Vector3f(p).sub(a);
        float s = ap.dot(ab) / ab.lengthSquared(), t = ap.dot(ad) / ad.lengthSquared();
        return new float[]{f.u()[0] + s * (f.u()[1] - f.u()[0]) + t * (f.u()[3] - f.u()[0]),
                f.v()[0] + s * (f.v()[1] - f.v()[0]) + t * (f.v()[3] - f.v()[0])};
    }

    private static double area(Quad q) {
        Vector3f a = new Vector3f(q.p()[1]).sub(q.p()[0]).cross(new Vector3f(q.p()[2]).sub(q.p()[0]));
        Vector3f b = new Vector3f(q.p()[2]).sub(q.p()[0]).cross(new Vector3f(q.p()[3]).sub(q.p()[0]));
        return (a.length() + b.length()) / 2.0;
    }

    // ------------------------------------------------------------------ 2. joints at their limits

    private static void joints(SkeletonRig rig) {
        System.out.println("\njoints at their limits (closed mesh, no fold, no face turned inside out):");
        record Case(String name, int bone, Quaternionf q) {}
        List<Case> cases = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            int fore = side == 0 ? rig.forearmR : rig.forearmL, hand = side == 0 ? rig.handR : rig.handL;
            int shin = side == 0 ? rig.shinR : rig.shinL, foot = side == 0 ? rig.footR : rig.footL;
            String s = side == 0 ? "_r" : "_l";
            for (float flex : new float[]{45, 90, 120, 145}) for (float twist : new float[]{-90, 0, 90})
                cases.add(new Case("elbow" + s + " " + flex + " twist " + twist, fore, new Quaternionf().rotationX(rad(-flex)).rotateY(rad(twist))));
            for (float x : new float[]{-60, 60}) for (float z : new float[]{-30, 30})
                cases.add(new Case("wrist" + s + " " + x + "/" + z, hand, new Quaternionf().rotationZYX(rad(z), 0, rad(x))));
            for (float flex : new float[]{90, 150}) cases.add(new Case("knee" + s + " " + flex, shin, new Quaternionf().rotationX(rad(flex))));
            for (float x : new float[]{-30, 30}) cases.add(new Case("ankle" + s + " " + x, foot, new Quaternionf().rotationX(rad(x))));
            // shoulders and hips (SkinMesh 2.1.1): forward, back, out and across
            int upper = side == 0 ? rig.upperArmR : rig.upperArmL, thigh = side == 0 ? rig.thighR : rig.thighL;
            for (float x : new float[]{-170, -90, 0, 45}) for (float z : new float[]{-90, -20, 0, 20, 90})
                cases.add(new Case("shoulder" + s + " " + x + "/" + z, upper, new Quaternionf().rotationZYX(rad(z), 0, rad(x))));
            for (float x : new float[]{-130, -90, -45, 0, 30}) for (float z : new float[]{-40, -10, 0, 10, 40})
                cases.add(new Case("hip" + s + " " + x + "/" + z, thigh, new Quaternionf().rotationZYX(rad(z), 0, rad(x))));
        }
        for (float twist : new float[]{-45, 0, 45}) for (float bend : new float[]{-15, 30}) for (float lean : new float[]{-15, 15})
            cases.add(new Case("waist twist " + twist + " bend " + bend + " side " + lean, rig.chest,
                    new Quaternionf().rotationZYX(rad(lean), rad(twist), rad(bend))));
        float worstDot = 1F, creaseOff = 0F;
        int creased = 0;
        String worst = "";
        for (boolean slim : new boolean[]{false, true}) for (Case c : cases) {
            SkeletonPose pose = new SkeletonPose(rig);
            pose.local[c.bone()].set(c.q());
            pose.update();
            List<Quad> quads = mesh(pose, slim, true, k -> true);
            // closed: per part and layer, every edge in exactly two quads
            Map<String, Integer> edges = new HashMap<>();
            for (Quad q : quads) for (int k = 0; k < 4; k++) {
                String a = key(q.p()[k]), b = key(q.p()[(k + 1) % 4]);
                String e = q.part() + (q.overlay() ? "L" : "B") + (a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a);
                edges.merge(e, 1, Integer::sum);
            }
            long open = edges.values().stream().filter(n -> n != 2).count();
            if (open > 0) fail((slim ? "slim " : "") + c.name() + ": " + open + " open edges (crack)");
            for (Quad q : quads) {
                Vector3f[] p = q.p();
                SkeletonRig.SkinPart part = rig.skin.get(q.part());
                SkinMesh.Rings rings = SkinMesh.rings(pose, part, slim);
                int s = (q.index() - 2) / 4;
                // a hip's bands (rings that only take part of the thigh's turn, SkinMesh 2.1.1) fold at the groin
                boolean hip = q.index() >= 2 && rings.band()[s + 1] == rings.band()[s];
                Vector3f n1 = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
                Vector3f n2 = new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[0]));
                if (n1.dot(n2) <= 0) {
                    float off = hip ? crease(pose, q, slim, true) : Float.MAX_VALUE;
                    if (off > CREASE) fail((slim ? "slim " : "") + c.name() + ": quad folds (" + part.part() + " " + q.index() + ")");
                    else { creased++; creaseOff = Math.max(creaseOff, off); }
                }
                // against the face's rest normal carried by the segment's bone
                // an end cap turns with its ring (a hip's top ring stays on the pelvis), a band with its bone, a hip's
                // bands (only part of the thigh's turn) half way between their two rings' turns
                Matrix4f end = rings.matrix()[q.index() == 0 ? 0 : rings.matrix().length - 1];
                Vector3f rest = switch (q.index() < 2 ? q.index() - 2 : (q.index() - 2) % 4) {
                    case -2 -> new Vector3f(0, -1, 0);
                    case -1 -> new Vector3f(0, 1, 0);
                    case 0 -> new Vector3f(-1, 0, 0);
                    case 1 -> new Vector3f(0, 0, -1);
                    case 2 -> new Vector3f(1, 0, 0);
                    default -> new Vector3f(0, 0, 1);
                };
                Quaternionf turn = q.index() < 2 ? end.getNormalizedRotation(new Quaternionf())
                        : hip ? rings.matrix()[s].getNormalizedRotation(new Quaternionf())
                        .slerp(rings.matrix()[s + 1].getNormalizedRotation(new Quaternionf()), 0.5F)
                        : pose.rotation(rings.band()[s], new Quaternionf());
                Vector3f expected = turn.transform(rest);
                float dot = q.normal().dot(expected);
                if (dot < worstDot && !hip) { worstDot = dot; worst = (slim ? "slim " : "") + c.name() + " " + part.part() + " quad " + q.index(); }
                if (dot <= 0) {
                    float off = hip ? crease(pose, q, slim, false) : Float.MAX_VALUE;
                    if (off > CREASE) fail((slim ? "slim " : "") + c.name() + ": " + part.part() + " quad " + q.index() + " inside out");
                    else { creased++; creaseOff = Math.max(creaseOff, off); }
                }
            }
        }
        System.out.printf("  %d poses x Steve/Alex; least aligned face: %.3f (cos of its turn off its bone) at %s%n", cases.size(), worstDot, worst);
        System.out.printf("  groin crease (hip bands folded or turned within %.0f px of the pelvis): %d quads, at most %.2f px out%n", CREASE, creased, creaseOff);
    }

    /** How far a hip band's fold may reach out of the pelvis's box and still lie in the groin crease (px). */
    private static final float CREASE = 1F;

    /**
     * How far a hip band's quad reaches out of the box its limb hangs from (the same layer, in that bone's frame): its
     * reflex corners for a fold, all its corners for a face turned inside out. A deeply bent hip folds the leg's front
     * bands up against the pelvis there (SkinMesh 2.1.1); within {@link #CREASE} the belly and the thigh cover them.
     */
    private static float crease(SkeletonPose pose, Quad q, boolean slim, boolean fold) {
        SkeletonRig rig = pose.rig;
        int holder = rig.parent[rig.skin.get(q.part()).bones()[0]];
        SkeletonRig.Box b = rig.skin.stream().filter(o -> java.util.Arrays.stream(o.bones()).anyMatch(x -> x == holder)).findFirst().orElseThrow().box(slim);
        float g = q.overlay() ? rig.overlayInflate : 0F, off = 0F;
        Matrix4f back = new Matrix4f(pose.world[holder]).invert();
        Vector3f[] c = q.p();
        Vector3f n = new Vector3f(c[2]).sub(c[0]).cross(new Vector3f(c[3]).sub(c[1]));
        for (int k = 0; k < 4; k++) {
            Vector3f turn = new Vector3f(c[k]).sub(c[(k + 3) % 4]).cross(new Vector3f(c[(k + 1) % 4]).sub(c[k]));
            if (fold && turn.dot(n) > 0) continue;
            Vector3f l = back.transformPosition(new Vector3f(c[k]));
            off = Math.max(off, Math.max(Math.max(b.x() - g - l.x, l.x - b.x() - b.w() - g),
                    Math.max(Math.max(b.y() - g - l.y, l.y - b.y() - b.h() - g), Math.max(b.z() - g - l.z, l.z - b.z() - b.d() - g))));
        }
        return off;
    }

    private static String key(Vector3f p) {
        return Math.round(p.x * 1e4) + "," + Math.round(p.y * 1e4) + "," + Math.round(p.z * 1e4);
    }

    // ------------------------------------------------------------------ 3. BR51 reach

    private static GunStance.Profile br51Profile() throws Exception {
        return profile("br51_01");
    }

    private static GunStance.Profile profile(String gun) throws Exception {
        try (var in = PlayerSkeletonCheck.class.getClassLoader().getResourceAsStream("data/apocalypse_firstlight/native_guns/" + gun + ".json")) {
            JsonObject p = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("ads").getAsJsonObject("profile");
            JsonArray aim = p.getAsJsonArray("aim"), rot = p.getAsJsonArray("ads_rotation");
            return new GunStance.Profile(aim.get(0).getAsFloat(), aim.get(1).getAsFloat(), aim.get(2).getAsFloat(),
                    p.get("eye_relief").getAsFloat(), p.get("scale").getAsFloat(),
                    rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat());
        }
    }

    /** Render-origin frame (blocks, world axes) to model blocks, body yaw 0 (LivingEntityRenderer#render). */
    static Matrix4f toModel() {
        return new Matrix4f().translation(0, 1.501F, 0).scale(1F / 0.9375F).scale(-1, -1, 1).rotateY(rad(-180F));
    }

    static Vector3f eyeO(boolean crouch) {
        return new Vector3f(0, crouch ? 1.27F + 0.125F : 1.62F, 0);
    }

    static HumanoidPoser.Input standing(boolean slim, float chestYaw, float viewYaw, float pitch, boolean rifle, boolean crouch) {
        HumanoidPoser.Input in = new HumanoidPoser.Input();
        in.slim = slim;
        in.crouch = crouch ? 1 : 0;
        in.chestYaw = chestYaw;
        in.viewYaw = viewYaw;
        in.viewPitch = pitch;
        in.chestPitch = HumanoidPoser.chestPitch(pitch, rifle);
        in.eyeTarget = toModel().transformPosition(eyeO(crouch)).mul(16F);
        return in;
    }

    private static void rifle(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        System.out.println("\nBR51 at real size (" + GunStance.realScale("br51_01") + "), both hands by IK: reach = shoulder->wrist / arm length (<= 1 holds); slide = px the support hand moved back along the gun (max " + GunStance.SUPPORT_SLIDE + ")");
        Gun br51 = Gun.of("br51_01");
        SkeletonPose pose = new SkeletonPose(rig);
        for (boolean crouch : new boolean[]{false, true}) for (float aim : new float[]{0, 1}) {
            StringBuilder line = new StringBuilder(String.format("  %-7s %-5s", crouch ? "crouch" : "stand", aim > 0 ? "ADS" : "ready"));
            for (float pitch : new float[]{-60, -30, 0, 30, 60}) {
                float worstR = 0, worstL = 0, elbowR = 0, elbowL = 0, slide = 0;
                for (boolean slim : new boolean[]{false, true}) {
                    HumanoidPoser.Input in = standing(slim, GunStance.CHEST_TWIST, 0, pitch, true, crouch);
                    HumanoidPoser.Report r = gunSolve(poser, in, pose, br51, 1, 0, aim, "static_idle", 0);
                    Matrix4f gm = lastGun;
                    if (System.getenv("AFL_DEBUG") != null && !slim) {   // joint places in view space (cm), for tuning
                        View v = view(crouch, 0, pitch);
                        java.util.function.Function<Vector3f, String> cm = p -> {
                            Vector3f c = v.view(p);
                            return String.format("(%.0f %.0f %.0f)", c.x * 100, c.y * 100, c.z * 100);
                        };
                        int[] bs = {rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL};
                        StringBuilder b = new StringBuilder(String.format("DBG %s %s %+.0f cm view: ", crouch ? "crouch" : "stand", aim > 0 ? "ADS" : "ready", pitch));
                        for (int k = 0; k < 6; k++) b.append(k == 3 ? " | L " : k == 0 ? "R " : " ").append(cm.apply(pose.point(bs[k], rig.pivot[bs[k]], new Vector3f())));
                        b.append(" | wristT R ").append(cm.apply(in.targetRight.transformPosition(new Vector3f(rig.pivot[rig.handR]).sub(rig.pivot[rig.upperArmR]))))
                                .append(" L ").append(cm.apply(in.targetLeft.transformPosition(new Vector3f(rig.pivot[rig.handL]).sub(rig.pivot[rig.upperArmL]))))
                                .append(" gun ").append(cm.apply(gm.getTranslation(new Vector3f()).mul(16)))
                                .append(" chestP ").append(in.chestPitch).append(" shift ").append(r.rootShift);
                        System.out.println(b);
                    }
                    worstR = Math.max(worstR, r.rightReach);
                    worstL = Math.max(worstL, r.leftReach);
                    elbowR = Math.max(elbowR, r.rightElbowDeg);
                    elbowL = Math.max(elbowL, r.leftElbowDeg);
                    slide = Math.max(slide, r.leftSlide);
                    if (r.rightReach > 1.02F || r.leftReach > 1.02F)
                        fail("BR51 " + (crouch ? "crouch " : "") + (aim > 0 ? "ADS" : "ready") + " pitch " + pitch + (slim ? " slim" : "")
                                + ": hand short (right " + r.rightReach + ", left " + r.leftReach + ")");
                }
                line.append(String.format(" | %+3.0f: R %.2f L %.2f slide %.1f elbows %3.0f/%3.0f", pitch, worstR, worstL, slide, elbowR, elbowL));
            }
            System.out.println(line);
        }
    }

    // ------------------------------------------------------------------ 3b. every clip of every carried gun (P2)

    /** A gun the skeleton carries, as the client's SkeletonGunStance sees it. */
    record Gun(String name, GunStance.Carried carried, GunStance.Profile profile, GeoAnchorSampler sampler, String still) {
        static Gun of(String name) throws Exception {
            GunStance.Carried c = GunStance.carried(name);
            GeoAnchorSampler s = GeoAnchorSampler.resource("apocalypse_firstlight", c.asset());
            String still = null;
            for (String b = "right_hand_anchor"; b != null; b = s.parent(b)) if (s.animates("draw", b)) still = b;
            return new Gun(name, c, PlayerSkeletonCheck.profile(name), s, still);
        }
    }

    /**
     * SkeletonGunStance#hold offline: the body solved first; the gun at its stance (a pistol between its two-handed ready
     * and the eye by {@code raise}, any gun toward its lowered place by {@code lower}), both hands on its anchors as
     * {@code clip} poses them at {@code t}, then the arms.
     */
    /** The gun frame (model blocks) and anchors' clip the last gunSolve placed. */
    static Matrix4f lastGun = new Matrix4f();

    static HumanoidPoser.Report gunSolve(HumanoidPoser poser, HumanoidPoser.Input in, SkeletonPose pose, Gun g,
                                                 float raise, float lower, float aim, String clip, float t) {
        HumanoidPoser.Report r = poser.solveBody(in, pose);
        GunStance.Kind kind = g.carried().kind();
        float real = g.carried().scale();
        if (kind.readyHand == null) raise = 1F;
        float up = raise * (1F - lower);
        Matrix4f rightRest = g.carried().rightHand(g.sampler().frame("right_hand_anchor", "static_idle", 0F));
        Matrix4f raised = toModel().mul(GunStance.eye(eyeO(in.crouch > 0.5F), in.viewYaw, in.viewPitch));
        Vector3f back = raised.transformDirection(new Vector3f(0, 0, 1)).normalize();
        Matrix4f chestSkin = pose.world[pose.rig.chest];
        if (kind.pocket != null) {
            // first person: aim takes the shouldered rifle to the eye (third person keeps it shouldered: labTp)
            Matrix4f shouldered = GunStance.onShoulder(raised, chestSkin, g.profile(), kind, g.carried());
            float toEye = labTp ? 0F : aim;
            raised = toEye > 0 ? GunStance.blend(shouldered, raised.mul(GunStance.inView(g.profile(), kind, real, 1F)), toEye) : shouldered;
        } else {
            raised.mul(GunStance.inView(g.profile(), kind, real, aim));
        }
        Matrix4f chest = GunStance.chestView(pose.world[pose.rig.chest], pose.rig.pivot[pose.rig.chest]);
        Matrix4f lowered = new Matrix4f(chest).mul(GunStance.lowInChest(g.profile(), kind, real, rightRest));
        Matrix4f base = kind.readyHand == null ? raised
                : GunStance.blend(new Matrix4f(chest).mul(GunStance.readyInChest(g.profile(), kind, real, rightRest)), raised, raise);
        Matrix4f gun = GunStance.blend(lowered, base, 1F - lower);
        float leftOn = kind == GunStance.Kind.RIFLE ? 1F : 1F - lower;
        in.right = in.left = HumanoidPoser.ArmMode.IK;
        in.ikRightWeight = 1F;
        in.ikLeftWeight = leftOn;
        in.shoulderRight.set(kind.shoulderRight).mul(up);
        in.shoulderLeft.set(kind.shoulderLeft).mul(up * leftOn);
        String still = clip.contains("draw") || clip.equals("put_away") ? g.still() : null;
        Matrix4f rightAnchor = g.carried().rightHand(g.sampler().frame("right_hand_anchor", clip, t, still, "static_idle"));
        Matrix4f leftAnchor = g.carried().leftHand(g.sampler().frame("left_hand_anchor", clip, t, still, "static_idle"),
                g.sampler().frame("left_hand_anchor", "static_idle", 0F));
        boolean acting0 = clip.startsWith("reload") || clip.startsWith("inspect");
        if (up > 0) gun = GunStance.inReach(kind, acting0 || labLeft != null ? null : g.carried().support(), poser, in, pose, gun, back,
                rightAnchor, leftAnchor);
        GunStance.handTarget(gun, rightAnchor, labR != null ? labR : kind.gripRight, true, in.slim, in.targetRight);
        GunStance.handTarget(gun, leftAnchor, labL != null ? labL : kind.gripLeft, false, in.slim, in.targetLeft);
        if (labLeft != null) labLeft.accept(gun, in);
        GunStance.Support support = g.carried().support();
        boolean acting = clip.startsWith("reload") || clip.startsWith("inspect");
        boolean supportOn = support != null && labLeft == null && !acting;
        in.right = in.left = HumanoidPoser.ArmMode.IK;
        in.ikRightWeight = 1F;
        in.ikLeftWeight = leftOn;
        in.poleRight.set(labPoleR != null ? labPoleR : kind.poleRight);
        in.poleLeft.set(labPoleL != null ? labPoleL : kind.poleLeft);
        if (supportOn) {
            GunStance.supportUnder(gun, support, in.slim, in.targetLeft);
            in.poleLeft.set(support.pole());
        }
        GunStance.supportSlide(new Matrix4f().scaling(16F).mul(gun), g.profile(), kind.supportSlide, in.slideLeft);
        in.shoulderRight.set(kind.shoulderRight).mul(up);
        in.shoulderLeft.set(kind.shoulderLeft).mul(up * leftOn);
        poser.solveArms(in, pose, r);
        lastGun.set(gun);
        return r;
    }

    private static void clips(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        System.out.println("\nevery clip at real size, hands on the clip's anchors by IK (pitch -60..60, stand / crouch, wide / slim; "
                + "reach <= 1.02 holds; the left hand counts only where it is on the gun); draw / put_away hold their still bone");
        SkeletonPose pose = new SkeletonPose(rig);
        record Stance(String name, float raise, float lower, float aim) {
            float up() { return raise * (1 - lower); }
            boolean left(boolean rifle) { return rifle || lower < 0.5F; }
        }
        for (Gun g : new Gun[]{Gun.of("br51_01"), Gun.of("p9_01")}) {
            boolean rifle = g.carried().kind() == GunStance.Kind.RIFLE;
            Stance[] stances = rifle ? new Stance[]{new Stance("ready", 1, 0, 0), new Stance("ADS", 1, 0, 1), new Stance("sprint", 1, 1, 0)}
                    : new Stance[]{new Stance("ready", 0, 0, 0), new Stance("raised", 1, 0, 0), new Stance("ADS", 1, 0, 1),
                    new Stance("sprint", 0, 1, 0)};
            System.out.println("  " + g.name() + " (" + g.carried().kind() + ", real " + g.carried().realScale() + ", still bone " + g.still() + ")");
            List<String> names = new ArrayList<>(g.sampler().clips());
            java.util.Collections.sort(names);
            for (Stance st : stances) for (String clip : names) {
                // NativeGunAds#action: no aiming while these play (the ready stance covers them)
                if (st.aim() > 0 && (clip.contains("reload") || clip.equals("draw") || clip.equals("put_away") || clip.startsWith("inspect")))
                    continue;
                float length = g.sampler().length(clip);
                float worstR = 0, worstL = 0, elbowR = 0, elbowL = 0;
                StringBuilder bad = new StringBuilder();
                float badFrom = -1, last = -1;
                for (float t = 0; t <= length + 1e-4F; t += 0.05F) {
                    boolean short_ = false;
                    for (boolean crouch : new boolean[]{false, true}) for (float pitch = -60; pitch <= 60; pitch += 30)
                        for (boolean slim : new boolean[]{false, true}) {
                            float up = rifle ? 1 - st.lower() : st.up();
                            HumanoidPoser.Input in = standing(slim, g.carried().kind().chestTwist * up, 0, pitch, rifle || st.raise() > 0.5F, crouch);
                            HumanoidPoser.Report r = gunSolve(poser, in, pose, g, st.raise(), st.lower(), st.aim(), clip, t);
                            boolean left = st.left(rifle);
                            worstR = Math.max(worstR, r.rightReach);
                            elbowR = Math.max(elbowR, r.rightElbowDeg);
                            if (left) {
                                worstL = Math.max(worstL, r.leftReach);
                                elbowL = Math.max(elbowL, r.leftElbowDeg);
                            }
                            if (r.rightReach > 1.02F || left && r.leftReach > 1.02F) short_ = true;
                        }
                    if (short_ && badFrom < 0) badFrom = t;
                    if (!short_ && badFrom >= 0) {
                        bad.append(String.format(" %.2f-%.2f s", badFrom, last));
                        badFrom = -1;
                    }
                    last = t;
                    if (length <= 0) break;
                }
                if (badFrom >= 0) bad.append(String.format(" %.2f-%.2f s", badFrom, last));
                System.out.printf("    %-6s %-18s %5.2f s  R %.2f L %s  elbows %3.0f/%s%s%n", st.name(), clip, length, worstR,
                        st.left(rifle) ? String.format("%.2f", worstL) : "  - ", elbowR,
                        st.left(rifle) ? String.format("%3.0f", elbowL) : "  -", bad.length() > 0 ? "  hand short:" + bad : "");
                if (bad.length() > 0) fail(g.name() + " " + st.name() + " " + clip + ": hand short at" + bad);
            }
            if (System.getenv("AFL_DEBUG") != null) for (Stance st : stances) {
                StringBuilder b = new StringBuilder(String.format("DBG %s %s reach R/L by pitch:", g.name(), st.name()));
                for (boolean crouch : new boolean[]{false, true}) for (boolean slim : new boolean[]{false, true}) {
                    b.append(crouch ? " | crouch" : " | stand").append(slim ? " slim" : " wide");
                    for (float pitch = -60; pitch <= 60; pitch += 30) {
                        HumanoidPoser.Input in = standing(slim, g.carried().kind().chestTwist * st.up(), 0, pitch, rifle || st.raise() > 0.5F, crouch);
                        HumanoidPoser.Report r = gunSolve(poser, in, pose, g, st.raise(), st.lower(), st.aim(), "static_idle", 0);
                        b.append(String.format(" %+.0f:%.2f/%.2f", pitch, r.rightReach, r.leftReach));
                    }
                }
                System.out.println(b);
            }
            if (System.getenv("AFL_DEBUG") != null) for (Stance st : stances) {
                // the anchor's authored arm axis against the IK forearm: how far the wrist must bend / twist
                HumanoidPoser.Input in = standing(false, g.carried().kind().chestTwist * st.up(), 0, 0, rifle || st.raise() > 0.5F, false);
                gunSolve(poser, in, pose, g, st.raise(), st.lower(), st.aim(), "static_idle", 0);
                StringBuilder b = new StringBuilder(String.format("DBG %s %s wrist:", g.name(), st.name()));
                for (boolean right : new boolean[]{true, false}) {
                    int fore = right ? rig.forearmR : rig.forearmL;
                    Quaternionf foreW = pose.rotation(fore, new Quaternionf());
                    Quaternionf want = (right ? in.targetRight : in.targetLeft).getUnnormalizedRotation(new Quaternionf()).normalize();
                    Quaternionf rel = new Quaternionf(foreW).conjugate().mul(want);
                    Vector3f yF = foreW.transform(new Vector3f(0, 1, 0)), yW = want.transform(new Vector3f(0, 1, 0));
                    float bend = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, yF.dot(yW)))));
                    Quaternionf twist = new Quaternionf(0, rel.y, 0, rel.w).normalize();
                    float tw = (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(twist.w))));
                    b.append(String.format(" %s bend %.0f twist %.0f", right ? "R" : "L", bend, tw));
                }
                System.out.println(b);
            }
            if (System.getenv("AFL_DEBUG") != null) for (Stance st : stances) for (float pitch : new float[]{0, 60}) {
                HumanoidPoser.Input in = standing(false, g.carried().kind().chestTwist * st.up(), 0, pitch, rifle || st.raise() > 0.5F, false);
                gunSolve(poser, in, pose, g, st.raise(), st.lower(), st.aim(), "static_idle", 0);
                View v = view(false, 0, pitch);
                StringBuilder b = new StringBuilder(String.format("DBG %s %s %+.0f view cm:", g.name(), st.name(), pitch));
                int[] bs = {rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL};
                String[] joint = {"shR", "elR", "wrR", "shL", "elL", "wrL"};
                for (int k = 0; k < 6; k++) {
                    Vector3f c = v.view(pose.point(bs[k], rig.pivot[bs[k]], new Vector3f()));
                    b.append(String.format(" %s(%.0f %.0f %.0f)", joint[k], c.x * 100, c.y * 100, c.z * 100));
                }
                for (Quad q : mesh(pose, false, true, k -> true)) {
                    if (!rig.skin.get(q.part()).part().equals("right_arm") || q.index() < 11) continue;
                    Vector3f c = v.view(bilerp(q.p(), 0.5F, 0.5F));
                    b.append(String.format(" q%d%s(%.0f %.0f %.0f)", q.index(), q.overlay() ? "L" : "", c.x * 100, c.y * 100, c.z * 100));
                }
                System.out.println(b);
            }
        }
    }

    // ------------------------------------------------------------------ 3c. how the hands sit (gun_handling_reference_v1.md)

    /** Tuning only (AFL_LAB): grips and poles tried in place of the kind's. */
    private static GunStance.Grip labR, labL;
    private static Vector3f labPoleR, labPoleL;
    /** Tuning only (AFL_LAB2, SupportLab): sets the left hand's target in place of the anchor's. */
    static java.util.function.BiConsumer<Matrix4f, HumanoidPoser.Input> labLeft;
    /** Solve as third person (a shouldered rifle stays on the shoulder while aiming). */
    static boolean labTp;

    /** A held pose, measured: wrist, forearm twist, hand off its target (deg), elbow below the shoulder (deg), FP cover. */
    record Hold(float wristR, float wristL, float twistR, float twistL, float errR, float errL, float dropR, float dropL,
                        float coverR, float coverL, float centreR, float centreL) {}

    private static float angle(Quaternionf q) {
        return (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(q.w))));
    }

    static Hold measure(SkeletonRig rig, SkeletonPose pose, HumanoidPoser.Input in, boolean crouch, float pitch) {
        float[] wrist = new float[2], twist = new float[2], err = new float[2], drop = new float[2];
        for (int k = 0; k < 2; k++) {
            boolean right = k == 0;
            int upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
            wrist[k] = angle(pose.local[hand]);
            Quaternionf f = pose.local[fore];
            twist[k] = angle(new Quaternionf(0, f.y, 0, f.w).normalize());
            Quaternionf want = (right ? in.targetRight : in.targetLeft).getUnnormalizedRotation(new Quaternionf()).normalize();
            err[k] = angle(pose.rotation(hand, new Quaternionf()).conjugate().mul(want));
            Vector3f sh = pose.point(upper, rig.pivot[upper], new Vector3f()), el = pose.point(fore, rig.pivot[fore], new Vector3f());
            Vector3f d = el.sub(sh);   // model px, y down
            drop[k] = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }
        // first-person cover: share of a 48 x 27 screen grid (and of its middle third) each arm's skin covers
        View v = view(crouch, 0, pitch);
        boolean[][][] cells = new boolean[2][48][27];
        for (Quad q : mesh(pose, in.slim, true, k -> true)) {
            String part = rig.skin.get(q.part()).part();
            int arm = part.equals("right_arm") ? 0 : part.equals("left_arm") ? 1 : -1;
            if (arm < 0) continue;
            for (int a = 0; a <= 16; a++) for (int b = 0; b <= 16; b++) {
                float[] sc = v.screen(v.view(bilerp(q.p(), a / 16F, b / 16F)));
                if (sc == null || Math.abs(sc[0]) > 1 || Math.abs(sc[1]) > 1) continue;
                cells[arm][Math.min(47, (int) ((sc[0] + 1) / 2 * 48))][Math.min(26, (int) ((sc[1] + 1) / 2 * 27))] = true;
            }
        }
        float[] cover = new float[2], centre = new float[2];
        for (int arm = 0; arm < 2; arm++) {
            int all = 0, mid = 0;
            for (int x = 0; x < 48; x++) for (int y = 0; y < 27; y++) if (cells[arm][x][y]) {
                all++;
                if (x >= 16 && x < 32 && y >= 9 && y < 18) mid++;
            }
            cover[arm] = all / (48F * 27F);
            centre[arm] = mid / (16F * 9F);
        }
        return new Hold(wrist[0], wrist[1], twist[0], twist[1], err[0], err[1], drop[0], drop[1], cover[0], cover[1], centre[0], centre[1]);
    }

    private static void grips(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        System.out.println("\nhow the hands sit (standing, wide, static_idle): wrist / forearm twist / hand off its target (deg), "
                + "elbow below the shoulder (deg), first-person cover of the screen and of its middle (%)");
        SkeletonPose pose = new SkeletonPose(rig);
        record Stance(String gun, String name, float raise, float lower, float aim) {}
        Stance[] stances = {new Stance("br51_01", "ready", 1, 0, 0), new Stance("br51_01", "ADS", 1, 0, 1),
                new Stance("p9_01", "ready", 0, 0, 0), new Stance("p9_01", "raised", 1, 0, 0), new Stance("p9_01", "ADS", 1, 0, 1)};
        Map<String, Gun> guns = new HashMap<>();
        for (Stance st : stances) {
            Gun g = guns.computeIfAbsent(st.gun(), k -> {
                try { return Gun.of(k); } catch (Exception e) { throw new RuntimeException(e); }
            });
            boolean rifle = g.carried().kind() == GunStance.Kind.RIFLE;
            StringBuilder line = new StringBuilder(String.format("  %-8s %-6s", g.name(), st.name()));
            for (float pitch : new float[]{-30, 0, 30}) {
                HumanoidPoser.Input in = standing(false, g.carried().kind().chestTwist * st.raise(), 0, pitch, rifle || st.raise() > 0.5F, false);
                gunSolve(poser, in, pose, g, st.raise(), st.lower(), st.aim(), "static_idle", 0);
                Hold h = measure(rig, pose, in, false, pitch);
                line.append(String.format(" | %+3.0f: wrist %2.0f/%2.0f twist %2.0f/%2.0f off %2.0f/%2.0f drop %3.0f/%3.0f cover %2.0f/%2.0f mid %2.0f/%2.0f",
                        pitch, h.wristR(), h.wristL(), h.twistR(), h.twistL(), h.errR(), h.errL(), h.dropR(), h.dropL(),
                        h.coverR() * 100, h.coverL() * 100, h.centreR() * 100, h.centreL() * 100));
            }
            System.out.println(line);
        }
        if (System.getenv("AFL_LAB") == null) return;
        // tuning: per kind and hand, the elbow pole (chest frame) with the least wrist + twist / 2, and its elbow drop
        for (String gunName : new String[]{"br51_01", "p9_01"}) {
            Gun g = guns.get(gunName);
            boolean rifle = g.carried().kind() == GunStance.Kind.RIFLE;
            List<Stance> mine = new ArrayList<>();
            for (Stance st : stances) if (st.gun().equals(gunName)) mine.add(st);
            for (int side = 0; side < 2; side++) {
                record Try(float cost, Vector3f pole, float wrist, float twist, float drop, float cover) {}
                List<Try> tries = new ArrayList<>();
                for (float px = -1; px <= 1.01F; px += 0.25F) for (float pz = -1; pz <= 1.01F; pz += 0.25F) {
                    Vector3f pole = new Vector3f(side == 0 ? px : -px, 1F, pz);
                    if (side == 0) labPoleR = pole;
                    else labPoleL = pole;
                    float wristSum = 0, twistSum = 0, dropSum = 0, coverSum = 0;
                    int n = 0;
                    for (Stance st : mine) for (float pitch : new float[]{-30, 0, 30}) {
                        HumanoidPoser.Input in = standing(false, g.carried().kind().chestTwist * st.raise(), 0, pitch, rifle || st.raise() > 0.5F, false);
                        gunSolve(poser, in, pose, g, st.raise(), st.lower(), st.aim(), "static_idle", 0);
                        Hold h = measure(rig, pose, in, false, pitch);
                        wristSum += side == 0 ? h.wristR() : h.wristL();
                        twistSum += side == 0 ? h.twistR() : h.twistL();
                        dropSum += side == 0 ? h.dropR() : h.dropL();
                        coverSum += side == 0 ? h.centreR() : h.centreL();
                        n++;
                    }
                    tries.add(new Try(wristSum / n + twistSum / n / 2, pole, wristSum / n, twistSum / n, dropSum / n, coverSum / n));
                }
                labPoleR = labPoleL = null;
                tries.sort((a, b) -> Float.compare(a.cost(), b.cost()));
                StringBuilder b = new StringBuilder(String.format("LAB %s %s poles (x,z: wrist / twist / drop / mid cover %%):", gunName, side == 0 ? "R" : "L"));
                for (Try t : tries) if (tries.indexOf(t) < 10 || Math.abs(t.pole().x) < 0.01F && Math.abs(t.pole().z) < 0.01F)
                    b.append(String.format(" [%+.2f,%+.2f] %.0f/%.0f/%.0f/%.0f", t.pole().x, t.pole().z, t.wrist(), t.twist(), t.drop(), t.cover() * 100));
                System.out.println(b);
            }
        }
    }

    // ------------------------------------------------------------------ 4. camera clearance

    /** Skin nearer than this to the eye must not be visible (design 5.4: 2.4 x the 0.05 near plane). */
    private static final float NEAREST = 0.12F;
    /**
     * With a gun shouldered the gun itself sits 0.08-0.12 from the eye (design 3, the rear sight) and the firing hand on
     * the grip beside it, so the limit is the near plane (0.05) with a 10 % margin: nothing visible is cut open.
     */
    private static final float NEAREST_GUN = 0.055F;
    private static final float VISIBLE_MARGIN = 1.25F;
    /**
     * Shoulders stay out of the picture's top two thirds while the view is within this pitch (deg) of level. Looking further
     * down the view looks at the body itself, and the picture's top is the ground ahead of the chest, where the shoulders are.
     */
    private static final float SHOULDER_PITCH = 45F;

    record View(Matrix4f fromModel, float tanY, float tanX) {
        /** Model px -> view blocks (+x right, +y up, -z ahead). */
        Vector3f view(Vector3f modelPx) {
            Vector4f v = fromModel.transform(new Vector4f(modelPx.x / 16F, modelPx.y / 16F, modelPx.z / 16F, 1F));
            return new Vector3f(v.x, v.y, v.z);
        }

        /** NDC x, y of a view point in front of the eye, or null behind it. */
        float[] screen(Vector3f v) {
            if (v.z > -0.05F) return null;
            return new float[]{v.x / -v.z / tanX, v.y / -v.z / tanY};
        }

        /** In the view cone widened by {@link #VISIBLE_MARGIN} (a wider FOV, view bobbing), at any depth in front of the eye. */
        boolean visible(Vector3f v) {
            return v.z < -1e-4F && Math.abs(v.x / -v.z) <= tanX * VISIBLE_MARGIN && Math.abs(v.y / -v.z) <= tanY * VISIBLE_MARGIN;
        }
    }

    static View view(boolean crouch, float yaw, float pitch) {
        // view <- render-origin frame <- model blocks
        Matrix4f eye = GunStance.eye(eyeO(crouch), yaw, pitch).invert();
        Matrix4f fromModel = eye.mul(toModel().invert());
        float tanY = (float) Math.tan(rad(35F));
        return new View(fromModel, tanY, tanY * 16F / 9F);
    }

    private static void clearance(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        System.out.println("\ncamera clearance (FOV 70, 16:9; head and hat hidden): nearest visible skin (view cone x" + VISIBLE_MARGIN
                + "; >= " + NEAREST + ", with a gun >= " + NEAREST_GUN + "), shoulders in the top two thirds (view pitch within +-"
                + SHOULDER_PITCH + "), lowest view pitch at which a hand shows (empty hands must not show looking level or up)");
        GeoAnchorSampler gun = GeoAnchorSampler.resource("apocalypse_firstlight", "br51_01");
        GunStance.Profile profile = br51Profile();
        Gun p9 = Gun.of("p9_01"), br51 = Gun.of("br51_01");
        // rifle: 0 none, 1 BR51 ready, 2 BR51 ADS, 3 P9 ready, 4 P9 raised, 5 P9 ADS
        record State(String name, float move, float run, boolean crouch, int rifle) {}
        State[] states = {
                new State("idle", 0, 0, false, 0), new State("walk", 1, 0, false, 0), new State("run", 1, 1, false, 0),
                new State("crouch", 0, 0, true, 0), new State("crouch walk", 1, 0, true, 0),
                new State("BR51 ready", 0, 0, false, 1), new State("BR51 ADS", 0, 0, false, 2),
                new State("BR51 walk", 1, 0, false, 1), new State("BR51 crouch ADS", 0, 0, true, 2),
                new State("P9 ready", 0, 0, false, 3), new State("P9 ready walk", 1, 0, false, 3),
                new State("P9 raised", 0, 0, false, 4), new State("P9 ADS", 0, 0, false, 5), new State("P9 crouch ADS", 0, 0, true, 5)};
        SkeletonPose pose = new SkeletonPose(rig);
        int head = rig.bone("head");
        for (State st : states) {
            float nearest = Float.MAX_VALUE, nearestPitch = 0;
            String nearestWhere = "";
            float handShows = Float.NaN;
            int shoulderHits = 0;
            String shoulderAt = "";
            float[] nearByPitch = new float[19];
            int[] shoulderByPitch = new int[19];
            java.util.Arrays.fill(nearByPitch, Float.MAX_VALUE);
            for (boolean slim : new boolean[]{false, true})
                for (float chest : new float[]{-45, 0, 45})
                    for (float lag : st.rifle() > 2 ? new float[]{0} : st.rifle() > 0 ? new float[]{-GunStance.CHEST_TWIST} : new float[]{-15, 0, 15})
                        for (float pitch = -90; pitch <= 90; pitch += 10)
                            for (float phase = 0; phase < (st.move() > 0 ? 1 : 0.01F); phase += 0.125F) {
                                float viewYaw = chest + lag;
                                HumanoidPoser.Input in = standing(slim, chest, viewYaw, pitch, st.rifle() > 0 && st.rifle() != 3, st.crouch());
                                in.move = st.move();
                                in.run = st.run();
                                in.walkPhase = phase;
                                if (st.rifle() > 2) {
                                    gunSolve(poser, in, pose, p9, st.rifle() == 3 ? 0 : 1, 0, st.rifle() == 5 ? 1 : 0, "static_idle", 0);
                                } else {
                                    if (st.rifle() > 0) gunSolve(poser, in, pose, br51, 1, 0, st.rifle() == 2 ? 1 : 0, "static_idle", 0);
                                    else poser.solve(in, pose);
                                }
                                View v = view(st.crouch(), viewYaw, pitch);
                                for (Quad q : mesh(pose, slim, true, k -> true)) {
                                    String part = rig.skin.get(q.part()).part();
                                    boolean armEnd = part.endsWith("_arm") && (q.index() == 1 || q.index() >= 2 + 4);   // forearm, hand, hand end
                                    for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
                                        Vector3f p = bilerp(q.p(), a / 2F, b / 2F);
                                        Vector3f c = v.view(p);
                                        if (!v.visible(c)) continue;
                                        float d = c.length();
                                        int pi = Math.round((pitch + 90) / 10);
                                        nearByPitch[pi] = Math.min(nearByPitch[pi], d);
                                        if (d < nearest) { nearest = d; nearestPitch = pitch; nearestWhere = part + (q.overlay() ? " layer" : "") + " quad " + q.index(); }
                                        if (armEnd && st.rifle() == 0) {
                                            float[] s = v.screen(c);
                                            if (s != null && Math.abs(s[0]) <= 1 && Math.abs(s[1]) <= 1 && (Float.isNaN(handShows) || pitch < handShows))
                                                handShows = pitch;
                                        }
                                    }
                                }
                                // shoulders: the top ring of each upper arm
                                for (int arm : new int[]{1, 2}) {
                                    SkeletonRig.SkinPart part = rig.skin.get(arm);
                                    SkeletonRig.Box box = part.box(slim);
                                    for (float[] corner : new float[][]{{box.x(), box.z()}, {box.x() + box.w(), box.z()},
                                            {box.x(), box.z() + box.d()}, {box.x() + box.w(), box.z() + box.d()}}) {
                                        Vector3f p = pose.point(part.bones()[0], new Vector3f(corner[0], box.y(), corner[1]), new Vector3f());
                                        float[] s = v.screen(v.view(p));
                                        if (Math.abs(pitch) <= SHOULDER_PITCH && s != null && Math.abs(s[0]) <= 1 && s[1] > -1F / 3F && s[1] <= 1) {
                                            shoulderHits++;
                                            shoulderByPitch[Math.round((pitch + 90) / 10)]++;
                                            shoulderAt = "pitch " + pitch + " chest " + chest + " view " + viewYaw;
                                        }
                                    }
                                }
                            }
            System.out.printf("  %-16s nearest %.3f (%s, pitch %.0f) | shoulders in top 2/3: %d%s | hands show from pitch %s%n",
                    st.name(), nearest, nearestWhere, nearestPitch, shoulderHits, shoulderHits > 0 ? " (" + shoulderAt + ")" : "",
                    st.rifle() > 0 ? "- (on the gun)" : Float.isNaN(handShows) ? "never" : String.format("%+.0f", handShows));
            if (System.getenv("AFL_DEBUG") != null) {
                StringBuilder b = new StringBuilder("    by pitch:");
                for (int i = 0; i < 19; i++) b.append(String.format(" %d:%.3f/%d", i * 10 - 90, nearByPitch[i], shoulderByPitch[i]));
                System.out.println(b);
            }
            if (nearest < (st.rifle() > 0 ? NEAREST_GUN : NEAREST)) fail(st.name() + ": skin " + nearest + " blocks from the eye (" + nearestWhere + ")");
            if (shoulderHits > 0) fail(st.name() + ": a shoulder in the top two thirds (" + shoulderAt + ")");
            if (st.rifle() == 0 && !Float.isNaN(handShows) && handShows <= 0) fail(st.name() + ": empty hand on screen at pitch " + handShows);
        }
    }

    private static Vector3f bilerp(Vector3f[] p, float s, float t) {
        Vector3f top = new Vector3f(p[0]).lerp(p[1], s), bottom = new Vector3f(p[3]).lerp(p[2], s);
        return top.lerp(bottom, t);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }

    private PlayerSkeletonCheck() {
    }
}
