package com.antaurora.apofirstlight.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline pictures of the skeleton holding a gun (tuning aid, docs/dev/gun_handling_reference_v1.md 4): flat-shaded
 * polygons, painter's order; first person as the game's view (FOV 70, head hidden) and third person from a few cameras.
 * Run: {@code AFL_SNAP=<out dir> ./gradlew skeletonCheck}.
 */
final class SkeletonSnapshot {
    private static final int W = 960, H = 540;

    private record Poly(float[] x, float[] y, float depth, Color color) {}

    record GunMesh(List<String> bones, List<float[][]> faces) {}

    private SkeletonSnapshot() {
    }

    static void run(SkeletonRig rig, HumanoidPoser poser, String outDir) throws Exception {
        File dir = new File(outDir);
        dir.mkdirs();
        SkeletonPose pose = new SkeletonPose(rig);
        record Scene(String gun, String name, float raise, float lower, float aim, float pitch) {}
        Scene[] scenes = {
                new Scene("br51_01", "ready", 1, 0, 0, 0), new Scene("br51_01", "ADS", 1, 0, 1, 0),
                new Scene("br51_01", "sprint", 1, 1, 0, 0),
                new Scene("p9_01", "ready", 0, 0, 0, 0), new Scene("p9_01", "raised", 1, 0, 0, 0), new Scene("p9_01", "ADS", 1, 0, 1, 0)};
        for (Scene sc : scenes) {
            PlayerSkeletonCheck.Gun g = PlayerSkeletonCheck.Gun.of(sc.gun());
            GunMesh mesh = gunMesh(g.carried().asset());
            boolean rifle = g.carried().kind() == GunStance.Kind.RIFLE;
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, g.carried().kind().chestTwist * sc.raise() * (1 - sc.lower()),
                    0, sc.pitch(), rifle || sc.raise() > 0.5F, false);
            PlayerSkeletonCheck.gunSolve(poser, in, pose, g, sc.raise(), sc.lower(), sc.aim(), "static_idle", 0);
            Matrix4f gun = new Matrix4f(PlayerSkeletonCheck.lastGun);
            marks.clear();
            marks.add(in.targetRight.transformPosition(new Vector3f(rig.pivot[rig.handR]).sub(rig.pivot[rig.upperArmR])));
            marks.add(in.targetLeft.transformPosition(new Vector3f(rig.pivot[rig.handL]).sub(rig.pivot[rig.upperArmL])));
            marks.add(pose.point(rig.handR, rig.pivot[rig.handR], new Vector3f()));
            marks.add(pose.point(rig.handL, rig.pivot[rig.handL], new Vector3f()));
            float[] e = {1e9F, 1e9F, 1e9F, -1e9F, -1e9F, -1e9F};
            for (int i = 0; i < mesh.faces().size(); i++) {
                Matrix4f f = g.sampler().frame(mesh.bones().get(i), "static_idle", 0F, null, null);
                if (f == null) continue;
                for (float[] v : mesh.faces().get(i)) {
                    Vector3f q = f.transformPosition(new Vector3f(v[0], v[1], v[2])).mul(16F);
                    e[0] = Math.min(e[0], q.x); e[1] = Math.min(e[1], q.y); e[2] = Math.min(e[2], q.z);
                    e[3] = Math.max(e[3], q.x); e[4] = Math.max(e[4], q.y); e[5] = Math.max(e[5], q.z);
                }
            }
            System.out.printf("  %s mesh px x %.1f..%.1f y %.1f..%.1f z %.1f..%.1f | R anchor %s | L anchor %s | wrist target R %s posed %s%n",
                    sc.gun(), e[0], e[3], e[1], e[4], e[2], e[5],
                    g.sampler().frame("right_hand_anchor", "static_idle", 0F, null, null).getTranslation(new Vector3f()).mul(16F),
                    g.sampler().frame("left_hand_anchor", "static_idle", 0F, null, null).getTranslation(new Vector3f()).mul(16F),
                    marks.get(0), marks.get(2));
            String base = sc.gun().substring(0, sc.gun().indexOf('_')) + "_" + sc.name();
            // first person: the game's view
            PlayerSkeletonCheck.View fp = PlayerSkeletonCheck.view(false, 0, sc.pitch());
            draw(rig, pose, g, mesh, gun, fp.fromModel(), true, new File(dir, base + "_fp.png"));
            // third person: back-right (the usual F5), front-left, right side, top
            Vector3f at = new Vector3f(0, 1.3F, 0.3F);
            draw(rig, pose, g, mesh, gun, camera(new Vector3f(-0.9F, 1.75F, -1.5F), at), false, new File(dir, base + "_tp_back.png"));
            draw(rig, pose, g, mesh, gun, camera(new Vector3f(1.0F, 1.6F, 1.6F), at), false, new File(dir, base + "_tp_front.png"));
            draw(rig, pose, g, mesh, gun, camera(new Vector3f(-1.8F, 1.45F, 0.3F), at), false, new File(dir, base + "_tp_side.png"));
            draw(rig, pose, g, mesh, gun, camera(new Vector3f(-0.01F, 3.4F, 0.31F), new Vector3f(0, 1.3F, 0.3F)), false, new File(dir, base + "_tp_top.png"));
            System.out.println("snapshot " + base);
        }
    }

    /** The posed skeleton and a gun frame (model blocks) from the first-person view and four third-person cameras. */
    static void views(SkeletonRig rig, SkeletonPose pose, PlayerSkeletonCheck.Gun g, Matrix4f gun, float pitch, File dir, String base) throws Exception {
        GunMesh mesh = gunMesh(g.carried().asset());
        marks.clear();
        draw(rig, pose, g, mesh, gun, PlayerSkeletonCheck.view(false, 0, pitch).fromModel(), true, new File(dir, base + "_fp.png"));
        Vector3f at = new Vector3f(0, 1.3F, 0.3F);
        draw(rig, pose, g, mesh, gun, camera(new Vector3f(-0.9F, 1.75F, -1.5F), at), false, new File(dir, base + "_tp_back.png"));
        draw(rig, pose, g, mesh, gun, camera(new Vector3f(1.0F, 1.6F, 1.6F), at), false, new File(dir, base + "_tp_front.png"));
        draw(rig, pose, g, mesh, gun, camera(new Vector3f(-1.8F, 1.45F, 0.3F), at), false, new File(dir, base + "_tp_side.png"));
        draw(rig, pose, g, mesh, gun, camera(new Vector3f(-0.01F, 3.4F, 0.31F), at), false, new File(dir, base + "_tp_top.png"));
    }

    /** Model px -> view blocks for a camera at {@code eye} (render-origin blocks, world axes; the player faces +z) looking at {@code at}. */
    private static Matrix4f camera(Vector3f eye, Vector3f at) {
        Vector3f up = Math.abs(at.x - eye.x) + Math.abs(at.z - eye.z) < 0.05F ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        return new Matrix4f().lookAt(eye, at, up).mul(toWorld);
    }

    static GunMesh gunMesh(String asset) throws Exception {
        try (var in = SkeletonSnapshot.class.getClassLoader().getResourceAsStream("assets/apocalypse_firstlight/meshes/" + asset + ".aflmesh.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> bones = new ArrayList<>();
            List<float[][]> faces = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("parts")) {
                JsonObject part = e.getAsJsonObject();
                JsonArray verts = part.getAsJsonArray("vertices");
                for (JsonElement f : part.getAsJsonArray("faces")) {
                    JsonArray idx = f.getAsJsonArray();
                    float[][] face = new float[idx.size()][];
                    for (int k = 0; k < idx.size(); k++) {
                        JsonArray v = verts.get(idx.get(k).getAsInt()).getAsJsonArray();
                        face[k] = new float[]{v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()};
                    }
                    bones.add(part.get("bone").getAsString());
                    faces.add(face);
                }
            }
            return new GunMesh(bones, faces);
        }
    }

    /** Model px points marked on the pictures: the wrist targets right, left, then the posed wrists right, left. */
    static final List<Vector3f> marks = new ArrayList<>();
    /** Model px points marked yellow (a player action's anchors). */
    static final List<Vector3f> anchors = new ArrayList<>();
    /** Model blocks -> render-origin blocks for the third-person cameras (a seated rider is drawn scaled). */
    static Matrix4f toWorld = PlayerSkeletonCheck.toModel().invert();
    /** Grey polygons around the body (render-origin blocks): a cab's seat, wheel, floor and roof. */
    static final List<Vector3f[]> scenery = new ArrayList<>();

    /** The posed skeleton alone (player actions V1), from the given first-person view and the four third-person cameras. */
    static void bodyViews(SkeletonRig rig, SkeletonPose pose, Matrix4f firstPerson, Vector3f at, File dir, String base) throws Exception {
        draw(rig, pose, null, null, null, firstPerson, true, new File(dir, base + "_fp.png"));
        draw(rig, pose, null, null, null, camera(new Vector3f(-0.7F, 0.6F, -1.5F).add(at), new Vector3f(at)), false, new File(dir, base + "_tp_back.png"));
        draw(rig, pose, null, null, null, camera(new Vector3f(0.7F, 0.3F, 1.5F).add(at), new Vector3f(at)), false, new File(dir, base + "_tp_front.png"));
        draw(rig, pose, null, null, null, camera(new Vector3f(-1.7F, 0.1F, 0F).add(at), new Vector3f(at)), false, new File(dir, base + "_tp_side.png"));
        draw(rig, pose, null, null, null, camera(new Vector3f(1.7F, 0.1F, 0F).add(at), new Vector3f(at)), false, new File(dir, base + "_tp_left.png"));
    }

    private static void draw(SkeletonRig rig, SkeletonPose pose, PlayerSkeletonCheck.Gun g, GunMesh mesh, Matrix4f gun,
                             Matrix4f fromModel, boolean firstPerson, File out) throws Exception {
        float tanY = (float) Math.tan(Math.toRadians(35)), tanX = tanY * W / H;
        Vector3f light = new Vector3f(0.4F, 0.8F, 0.45F).normalize();
        List<Poly> polys = new ArrayList<>();
        // body
        for (PlayerSkeletonCheck.Quad q : PlayerSkeletonCheck.mesh(pose, false, false, k -> true)) {
            String part = rig.skin.get(q.part()).part();
            if (firstPerson && part.equals("head")) continue;
            Color c = switch (part) {
                case "right_arm" -> new Color(205, 120, 95);
                case "left_arm" -> new Color(95, 135, 205);
                case "head" -> new Color(225, 190, 160);
                case "body" -> new Color(80, 150, 150);
                default -> new Color(70, 70, 140);
            };
            Vector3f[] v = new Vector3f[4];
            for (int k = 0; k < 4; k++) v[k] = fromModel.transformPosition(new Vector3f(q.p()[k]).div(16F));
            add(polys, v, c, light, tanX, tanY);
        }
        // head (vanilla's own part, not in the skin): its 8 px cube on the head bone
        if (!firstPerson) {
            int head = rig.bone("head");
            float[][] c = {{-4, -8, -4}, {4, -8, -4}, {4, 0, -4}, {-4, 0, -4}, {-4, -8, 4}, {4, -8, 4}, {4, 0, 4}, {-4, 0, 4}};
            int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {4, 5, 1, 0}, {3, 2, 6, 7}};
            for (int[] f : faces) {
                Vector3f[] v = new Vector3f[4];
                for (int k = 0; k < 4; k++)
                    v[k] = fromModel.transformPosition(pose.point(head, new Vector3f(c[f[k]][0], c[f[k]][1], c[f[k]][2]), new Vector3f()).div(16F));
                add(polys, v, f == faces[0] ? new Color(240, 200, 170) : new Color(200, 160, 130), light, tanX, tanY);
            }
        }
        // gun, at its static idle
        java.util.Map<String, Matrix4f> frames = new java.util.HashMap<>();
        for (int i = 0; g != null && i < mesh.faces().size(); i++) {
            String bone = mesh.bones().get(i);
            Matrix4f f = frames.computeIfAbsent(bone, b -> {
                Matrix4f m = g.sampler().frame(b, "static_idle", 0F, null, null);
                return m == null ? null : new Matrix4f(fromModel).scale(1F / 16F).scale(16F).mul(gun).mul(m);
            });
            if (f == null) continue;
            float[][] face = mesh.faces().get(i);
            Vector3f[] v = new Vector3f[face.length];
            for (int k = 0; k < face.length; k++) v[k] = f.transformPosition(new Vector3f(face[k][0], face[k][1], face[k][2]));
            add(polys, v, new Color(60, 62, 66), light, tanX, tanY);
        }
        Matrix4f worldToView = new Matrix4f(fromModel).mul(new Matrix4f(toWorld).invert());
        for (Vector3f[] s : scenery) {
            Vector3f[] v = new Vector3f[s.length];
            for (int k = 0; k < s.length; k++) v[k] = worldToView.transformPosition(new Vector3f(s[k]));
            add(polys, v, new Color(150, 150, 150), light, tanX, tanY);
        }
        polys.sort((a, b) -> Float.compare(a.depth(), b.depth()));
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setColor(new Color(200, 210, 220));
        gr.fillRect(0, 0, W, H);
        for (Poly p : polys) {
            int[] xs = new int[p.x().length], ys = new int[p.y().length];
            for (int k = 0; k < xs.length; k++) { xs[k] = Math.round(p.x()[k]); ys[k] = Math.round(p.y()[k]); }
            gr.setColor(p.color());
            gr.fillPolygon(new Polygon(xs, ys, xs.length));
        }
        // markers: the anchors (yellow), the IK wrist targets (magenta), the posed wrists (green)
        java.util.function.BiConsumer<Vector3f, Color> mark = (viewPos, col) -> {
            if (viewPos.z > -0.03F) return;
            int x = Math.round(W / 2F + viewPos.x / -viewPos.z / tanX * W / 2F), y = Math.round(H / 2F - viewPos.y / -viewPos.z / tanY * H / 2F);
            gr.setColor(col);
            gr.fillOval(x - 4, y - 4, 9, 9);
        };
        for (String anchor : g == null ? new String[0] : new String[]{"right_hand_anchor", "left_hand_anchor"}) {
            Matrix4f m = g.sampler().frame(anchor, "static_idle", 0F, null, null);
            mark.accept(new Matrix4f(fromModel).mul(gun).mul(m).transformPosition(new Vector3f()), Color.YELLOW);
        }
        for (Vector3f w : anchors) mark.accept(fromModel.transformPosition(new Vector3f(w).div(16F)), Color.YELLOW);
        for (Vector3f w : marks) mark.accept(fromModel.transformPosition(new Vector3f(w).div(16F)), w == marks.get(0) || w == marks.get(1) ? Color.MAGENTA : Color.GREEN);
        if (firstPerson) {
            gr.setColor(Color.WHITE);
            gr.drawLine(W / 2 - 6, H / 2, W / 2 + 6, H / 2);
            gr.drawLine(W / 2, H / 2 - 6, W / 2, H / 2 + 6);
        }
        gr.dispose();
        ImageIO.write(img, "png", out);
    }

    /** A view-space polygon (blocks, -z ahead) to the picture, shaded by its normal; dropped when it crosses the near plane. */
    private static void add(List<Poly> polys, Vector3f[] v, Color c, Vector3f light, float tanX, float tanY) {
        float[] xs = new float[v.length], ys = new float[v.length];
        float depth = 0;
        for (int k = 0; k < v.length; k++) {
            if (v[k].z > -0.03F) return;
            xs[k] = W / 2F + v[k].x / -v[k].z / tanX * W / 2F;
            ys[k] = H / 2F - v[k].y / -v[k].z / tanY * H / 2F;
            depth += v[k].z;
        }
        Vector3f n = new Vector3f(v[2]).sub(v[0]).cross(new Vector3f(v[v.length - 1]).sub(v[1]));
        if (n.lengthSquared() < 1e-14F) return;
        n.normalize();
        float shade = 0.35F + 0.65F * Math.abs(n.dot(light));
        polys.add(new Poly(xs, ys, depth / v.length,
                new Color(Math.min(255, (int) (c.getRed() * shade)), Math.min(255, (int) (c.getGreen() * shade)), Math.min(255, (int) (c.getBlue() * shade)))));
    }
}
