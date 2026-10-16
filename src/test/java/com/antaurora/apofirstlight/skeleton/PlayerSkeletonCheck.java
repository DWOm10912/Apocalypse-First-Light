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
        restPixels(rig, false);
        restPixels(rig, true);
        joints(rig);
        rifle(rig, poser);
        clearance(rig, poser);
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

    private record Quad(Vector3f[] p, float[] u, float[] v, int part, boolean overlay, int index) {
        Vector3f normal() {
            return new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[1])).normalize();
        }
    }

    private static List<Quad> mesh(SkeletonPose pose, boolean slim, boolean overlays, java.util.function.IntPredicate parts) {
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
        }
        for (float twist : new float[]{-45, 0, 45}) for (float bend : new float[]{-15, 30}) for (float lean : new float[]{-15, 15})
            cases.add(new Case("waist twist " + twist + " bend " + bend + " side " + lean, rig.chest,
                    new Quaternionf().rotationZYX(rad(lean), rad(twist), rad(bend))));
        float worstDot = 1F;
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
                Vector3f n1 = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
                Vector3f n2 = new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[0]));
                if (n1.dot(n2) <= 0) fail((slim ? "slim " : "") + c.name() + ": quad folds (" + rig.skin.get(q.part()).part() + " " + q.index() + ")");
                // against the face's rest normal carried by the segment's bone
                SkeletonRig.SkinPart part = rig.skin.get(q.part());
                int segment = q.index() < 2 ? (q.index() == 0 ? 0 : part.bones().length - 1) : (q.index() - 2) / 4;
                Vector3f rest = switch (q.index() < 2 ? q.index() - 2 : (q.index() - 2) % 4) {
                    case -2 -> new Vector3f(0, -1, 0);
                    case -1 -> new Vector3f(0, 1, 0);
                    case 0 -> new Vector3f(-1, 0, 0);
                    case 1 -> new Vector3f(0, 0, -1);
                    case 2 -> new Vector3f(1, 0, 0);
                    default -> new Vector3f(0, 0, 1);
                };
                Vector3f expected = pose.rotation(part.bones()[segment], new Quaternionf()).transform(rest);
                float dot = q.normal().dot(expected);
                if (dot < worstDot) { worstDot = dot; worst = (slim ? "slim " : "") + c.name() + " " + part.part() + " quad " + q.index(); }
                if (dot <= 0) fail((slim ? "slim " : "") + c.name() + ": " + part.part() + " quad " + q.index() + " inside out");
            }
        }
        System.out.printf("  %d poses x Steve/Alex; least aligned face: %.3f (cos of its turn off its bone) at %s%n", cases.size(), worstDot, worst);
    }

    private static String key(Vector3f p) {
        return Math.round(p.x * 1e4) + "," + Math.round(p.y * 1e4) + "," + Math.round(p.z * 1e4);
    }

    // ------------------------------------------------------------------ 3. BR51 reach

    private static RifleStance.Profile br51Profile() throws Exception {
        try (var in = PlayerSkeletonCheck.class.getClassLoader().getResourceAsStream("data/apocalypse_firstlight/native_guns/br51_01.json")) {
            JsonObject p = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("ads").getAsJsonObject("profile");
            JsonArray aim = p.getAsJsonArray("aim"), rot = p.getAsJsonArray("ads_rotation");
            return new RifleStance.Profile(aim.get(0).getAsFloat(), aim.get(1).getAsFloat(), aim.get(2).getAsFloat(),
                    p.get("eye_relief").getAsFloat(), p.get("scale").getAsFloat(),
                    rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat());
        }
    }

    /** Render-origin frame (blocks, world axes) to model blocks, body yaw 0 (LivingEntityRenderer#render). */
    private static Matrix4f toModel() {
        return new Matrix4f().translation(0, 1.501F, 0).scale(1F / 0.9375F).scale(-1, -1, 1).rotateY(rad(-180F));
    }

    private static Vector3f eyeO(boolean crouch) {
        return new Vector3f(0, crouch ? 1.27F + 0.125F : 1.62F, 0);
    }

    /** Fills the rifle part of an input; returns the gun's frame in model blocks. */
    private static Matrix4f rifleInput(HumanoidPoser.Input in, GeoAnchorSampler gun, RifleStance.Profile profile, float aim) {
        Matrix4f gunModel = toModel().mul(RifleStance.eye(eyeO(in.crouch > 0.5F), in.viewYaw, in.viewPitch))
                .mul(RifleStance.inView(profile, RifleStance.realScale("br51_01"), aim));
        RifleStance.handTarget(gunModel, gun.frame("right_hand_anchor", "static_idle", 0), true, in.slim, in.targetRight);
        RifleStance.handTarget(gunModel, gun.frame("left_hand_anchor", "static_idle", 0), false, in.slim, in.targetLeft);
        in.right = in.left = HumanoidPoser.ArmMode.IK;
        in.poleLeft.set(0.15F, 1F, 0.25F);
        in.shoulderRight.set(RifleStance.STOCK_SHOULDER);
        RifleStance.supportSlide(new Matrix4f().scaling(16F).mul(gunModel), profile, in.slideLeft);
        return gunModel;
    }

    private static HumanoidPoser.Input standing(boolean slim, float chestYaw, float viewYaw, float pitch, boolean rifle, boolean crouch) {
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
        System.out.println("\nBR51 at real size (" + RifleStance.realScale("br51_01") + "), both hands by IK: reach = shoulder->wrist / arm length (<= 1 holds); slide = px the support hand moved back along the gun (max " + RifleStance.SUPPORT_SLIDE + ")");
        GeoAnchorSampler gun = GeoAnchorSampler.resource("apocalypse_firstlight", "br51_01");
        RifleStance.Profile profile = br51Profile();
        SkeletonPose pose = new SkeletonPose(rig);
        for (boolean crouch : new boolean[]{false, true}) for (float aim : new float[]{0, 1}) {
            StringBuilder line = new StringBuilder(String.format("  %-7s %-5s", crouch ? "crouch" : "stand", aim > 0 ? "ADS" : "ready"));
            for (float pitch : new float[]{-60, -30, 0, 30, 60}) {
                float worstR = 0, worstL = 0, elbowR = 0, elbowL = 0, slide = 0;
                for (boolean slim : new boolean[]{false, true}) {
                    HumanoidPoser.Input in = standing(slim, RifleStance.CHEST_TWIST, 0, pitch, true, crouch);
                    Matrix4f gm = rifleInput(in, gun, profile, aim);
                    HumanoidPoser.Report r = poser.solve(in, pose);
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

    private record View(Matrix4f fromModel, float tanY, float tanX) {
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

    private static View view(boolean crouch, float yaw, float pitch) {
        // view <- render-origin frame <- model blocks
        Matrix4f eye = RifleStance.eye(eyeO(crouch), yaw, pitch).invert();
        Matrix4f fromModel = eye.mul(toModel().invert());
        float tanY = (float) Math.tan(rad(35F));
        return new View(fromModel, tanY, tanY * 16F / 9F);
    }

    private static void clearance(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        System.out.println("\ncamera clearance (FOV 70, 16:9; head and hat hidden): nearest visible skin (view cone x" + VISIBLE_MARGIN
                + "; >= " + NEAREST + ", with a gun >= " + NEAREST_GUN + "), shoulders in the top two thirds (view pitch within +-"
                + SHOULDER_PITCH + "), lowest view pitch at which a hand shows (empty hands must not show looking level or up)");
        GeoAnchorSampler gun = GeoAnchorSampler.resource("apocalypse_firstlight", "br51_01");
        RifleStance.Profile profile = br51Profile();
        record State(String name, float move, float run, boolean crouch, int rifle) {}
        State[] states = {
                new State("idle", 0, 0, false, 0), new State("walk", 1, 0, false, 0), new State("run", 1, 1, false, 0),
                new State("crouch", 0, 0, true, 0), new State("crouch walk", 1, 0, true, 0),
                new State("BR51 ready", 0, 0, false, 1), new State("BR51 ADS", 0, 0, false, 2),
                new State("BR51 walk", 1, 0, false, 1), new State("BR51 crouch ADS", 0, 0, true, 2)};
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
                    for (float lag : st.rifle() > 0 ? new float[]{-RifleStance.CHEST_TWIST} : new float[]{-15, 0, 15})
                        for (float pitch = -90; pitch <= 90; pitch += 10)
                            for (float phase = 0; phase < (st.move() > 0 ? 1 : 0.01F); phase += 0.125F) {
                                float viewYaw = chest + lag;
                                HumanoidPoser.Input in = standing(slim, chest, viewYaw, pitch, st.rifle() > 0, st.crouch());
                                in.move = st.move();
                                in.run = st.run();
                                in.walkPhase = phase;
                                if (st.rifle() > 0) rifleInput(in, gun, profile, st.rifle() == 2 ? 1 : 0);
                                poser.solve(in, pose);
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
