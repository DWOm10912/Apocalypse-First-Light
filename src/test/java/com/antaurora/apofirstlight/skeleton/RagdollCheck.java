package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollWorld;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ragdoll V1 offline check (docs/dev/ragdoll_v1_plan.md 7): a body falls in fixed scenes (flat ground, a wall, a ledge,
 * stairs, a pit, a high fall, a stepped slope, a head shot, a blast) and every scene must end still, out of the blocks,
 * joints closed and inside their limits. Pictures with {@code AFL_SNAP_RAGDOLL=<dir>}, per-tick numbers of one scene with
 * {@code AFL_RAGDOLL_TRACE=<scene>}. Run: {@code ./gradlew ragdollCheck}.
 */
public final class RagdollCheck {
    private static int failures;
    private static final double MAX_FINAL_DEPTH = 0.02, MAX_DEPTH = 0.10, MAX_GAP = 0.01, MAX_LIMIT_DEG = 3;

    record Push(String body, double ox, double oy, double oz, double jx, double jy, double jz) {}

    record Scene(String name, List<double[]> boxes, Vector3d origin, double yaw, Vector3d velocity, List<Push> pushes) {}

    public static void main(String[] args) throws Exception {
        cameraAngles();
        String snap = System.getenv("AFL_SNAP_RAGDOLL");
        File dir = snap == null ? null : new File(snap);
        if (dir != null) dir.mkdirs();
        RagdollDefinition def = RagdollDefinition.get();
        System.out.printf("ragdoll: %d bodies, %.1f kg, %d surface points%n", def.bodies.length, def.totalMass, points(def));
        for (Scene s : scenes()) run(def, s, dir);
        if (failures > 0) {
            System.out.println("\nragdoll checks: " + failures + " FAILED");
            System.exit(1);
        }
        System.out.println("\nragdoll checks: all passed");
    }

    private static int points(RagdollDefinition def) {
        int k = 0;
        for (RagdollDefinition.Body b : def.bodies) k += b.points().length;
        return k;
    }

    private static List<Scene> scenes() {
        List<Scene> out = new ArrayList<>();
        Vector3d still = new Vector3d();
        // facing yaw 0 looks along +z; the chest is about 1.2 m up
        out.add(new Scene("flat_forward", list(floor(0)), new Vector3d(0, 0, 0), 0, still, List.of(push("chest", 0, 1.25, 0, 0, 0, 40))));
        out.add(new Scene("flat_back", list(floor(0)), new Vector3d(0, 0, 0), 0, still, List.of(push("chest", 0, 1.25, 0, 0, 0, -40))));
        out.add(new Scene("flat_side", list(floor(0)), new Vector3d(0, 0, 0), 30, still, List.of(push("chest", 0, 1.25, 0, 40, 0, 0))));
        out.add(new Scene("crumple", list(floor(0)), new Vector3d(0, 0, 0), 0, still, List.of(push("thigh_r", 0, 0.6, 0, 0, 0, 12), push("thigh_l", 0, 0.6, 0, 0, 0, 12))));
        List<double[]> wall = list(floor(0));
        wall.add(new double[]{-3, 0, -1.6, 3, 3, -0.6});
        out.add(new Scene("wall_behind", wall, new Vector3d(0, 0, 0), 0, still, List.of(push("chest", 0, 1.25, 0, 0, 0, -45))));
        List<double[]> ledge = new ArrayList<>();
        ledge.add(new double[]{-3, -1, -3, 3, 0, 0.5});
        ledge.add(new double[]{-3, -2, 0.5, 3, -1, 4});
        out.add(new Scene("ledge", ledge, new Vector3d(0, 0, 0), 0, still, List.of(push("chest", 0, 1.25, 0, 0, 0, 45))));
        List<double[]> stairs = new ArrayList<>();
        stairs.add(new double[]{-3, -1, -3, 3, 0, 0.25});
        for (int k = 1; k <= 8; k++) stairs.add(new double[]{-3, -1 - 0.5 * k, 0.25 + 0.5 * (k - 1), 3, -0.5 * k, 0.25 + 0.5 * k});
        stairs.add(new double[]{-3, -6, 4.25, 3, -4, 7});
        out.add(new Scene("stairs_down", stairs, new Vector3d(0, 0, 0), 0, still, List.of(push("chest", 0, 1.25, 0, 0, 0, 45))));
        List<double[]> pit = new ArrayList<>();
        pit.add(new double[]{-3, -3, -3, 3, 0, 0.6});
        pit.add(new double[]{-3, -3, 1.6, 3, 0, 4});
        pit.add(new double[]{-3, -3, 0.6, -0.5, 0, 1.6});
        pit.add(new double[]{0.5, -3, 0.6, 3, 0, 1.6});
        pit.add(new double[]{-0.5, -4, 0.6, 0.5, -3, 1.6});
        out.add(new Scene("pit", pit, new Vector3d(0, 0, 0), 0, still, List.of(push("chest", 0, 1.25, 0, 0, 0, 45))));
        out.add(new Scene("high_fall", list(floor(0)), new Vector3d(0, 4, 0), 0, new Vector3d(0, 0, 1.5), List.of(push("chest", 0, 5.25, 0, 0, 0, 20))));
        List<double[]> slope = new ArrayList<>();
        for (int k = 0; k < 24; k++) slope.add(new double[]{-3, -4, -3 + 0.25 * k, 3, 1.5 - 0.125 * k, -3 + 0.25 * (k + 1)});
        slope.add(new double[]{-3, -4, 3, 3, -1.5, 8});
        out.add(new Scene("smooth_slope", slope, new Vector3d(0, 0.75, -1.5), 0, still, List.of(push("chest", 0, 2.0, -1.5, 0, 0, 40))));
        out.add(new Scene("headshot", list(floor(0)), new Vector3d(0, 0, 0), 0, still, List.of(push("head", 0, 1.6, 0, 0, 0, -18))));
        out.add(new Scene("blast", list(floor(0)), new Vector3d(0, 0, 0), 0, still,
                List.of(push("chest", 0, 1.25, 0, 0, 120, 140), push("pelvis", 0, 0.9, 0, 0, 60, 80))));
        return out;
    }

    private static double[] floor(double y) {
        return new double[]{-4, y - 1, -4, 4, y, 8};
    }

    private static List<double[]> list(double[]... boxes) {
        return new ArrayList<>(List.of(boxes));
    }

    private static Push push(String body, double ox, double oy, double oz, double jx, double jy, double jz) {
        return new Push(body, ox, oy, oz, jx, jy, jz);
    }

    private static void run(RagdollDefinition def, Scene s, File dir) throws Exception {
        RagdollWorld world = (minX, minY, minZ, maxX, maxY, maxZ, out) -> {
            for (double[] b : s.boxes()) if (b[3] > minX && b[0] < maxX && b[4] > minY && b[1] < maxY && b[5] > minZ && b[2] < maxZ) out.add(b);
        };
        RagdollBody body = new RagdollBody(def);
        SkeletonPose rest = new SkeletonPose(def.rig);
        rest.update();
        body.start(rest, s.origin(), s.yaw(), s.velocity());
        for (Push p : s.pushes()) {
            int b = def.index(p.body());
            Vector3d at = new Vector3d(s.origin().x + p.ox(), p.oy(), p.oz());
            body.impulse(b, at, new Vector3d(p.jx(), p.jy(), p.jz()));
        }
        double worstDepth = 0, topSpeed = 0;
        long time = 0;
        List<Integer> frames = List.of(4, 10, 20);
        SkeletonPose pose = new SkeletonPose(def.rig);
        Vector3d[] lastPos = new Vector3d[body.size()];
        for (int i = 0; i < body.size(); i++) lastPos[i] = new Vector3d(body.position(i));
        while (!body.asleep()) {
            long t0 = System.nanoTime();
            body.tick(world);
            time += System.nanoTime() - t0;
            worstDepth = Math.max(worstDepth, body.penetration());
            for (int i = 0; i < body.size(); i++) topSpeed = Math.max(topSpeed, body.velocity(i).length());
            if (s.name().equals(System.getenv("AFL_RAGDOLL_TRACE"))) {
                double sp = 0, sn = 0;
                int fs = 0, fw = 0;
                for (int i = 0; i < body.size(); i++) {
                    if (body.velocity(i).length() > sp) { sp = body.velocity(i).length(); fs = i; }
                    if (body.spin(i).length() > sn) { sn = body.spin(i).length(); fw = i; }
                }
                double mv = 0;
                int fm = 0;
                for (int i = 0; i < body.size(); i++) {
                    double d = body.position(i).distance(lastPos[i]);
                    if (d > mv) { mv = d; fm = i; }
                    lastPos[i].set(body.position(i));
                }
                System.out.printf("    t%3d speed %6.3f (%s) spin %6.3f (%s) moved %.4f (%s) depth %.3f gap %.4f%n", body.ticks(), sp, def.bodies[fs].name(), sn, def.bodies[fw].name(), mv, def.bodies[fm].name(), body.penetration(), body.jointGap());
            }
            if (dir != null && frames.contains(body.ticks())) {
                body.pose(pose, s.origin());
                picture(def, pose, s, body, new File(dir, s.name() + "_t" + body.ticks() + ".png"), 0);
            }
        }
        double depth = body.penetration(), gap = body.jointGap();
        int[] which = {0};
        double limit = Math.toDegrees(body.limitExcess(which));
        boolean quietSleep = body.ticks() < RagdollBody.MAX_TICKS && (body.touched() < 0 || body.ticks() - body.touched() < RagdollBody.GROUND_TICKS);
        System.out.printf("  %-13s still at tick %3d (%s, first touch %3d) | depth end %.3f max %.3f | joint gap %.4f | limit +%.1f deg (%s) | top speed %.1f | %.0f us/tick | head tilt %.0f deg | pelvis %s%n",
                s.name(), body.ticks(), quietSleep ? "quiet" : "CAP", body.touched(), depth, worstDepth, gap, limit,
                def.bodies[which[0]].name(), topSpeed, time / 1000.0 / body.ticks(), headTilt(body, def), fmt(body.position(def.pelvis)));
        if (depth > MAX_FINAL_DEPTH) fail(s.name() + ": ends " + String.format("%.3f", depth) + " m inside a block");
        if (worstDepth > MAX_DEPTH) fail(s.name() + ": sank " + String.format("%.3f", worstDepth) + " m into a block on the way");
        if (gap > MAX_GAP) fail(s.name() + ": a joint ends " + String.format("%.4f", gap) + " m apart");
        if (limit > MAX_LIMIT_DEG) fail(s.name() + ": " + def.bodies[which[0]].name() + " ends " + String.format("%.1f", limit) + " deg past its limit");
        if (!quietSleep) fail(s.name() + ": did not come to rest by itself");
        if (dir != null) {
            body.pose(pose, s.origin());
            for (int view = 0; view < 3; view++) picture(def, pose, s, body, new File(dir, s.name() + "_end_" + view + ".png"), view);
        }
    }

    /** R-C death camera: Minecraft yaw / pitch / roll from the head's forward and up (DeathCamera#angles). */
    private static void cameraAngles() {
        double s30 = Math.sin(Math.toRadians(30)), c30 = Math.cos(Math.toRadians(30));
        Object[][] cases = {
                {"south", new double[]{0, 0, 1}, new double[]{0, 1, 0}, new float[]{0, 0, 0}},
                {"east", new double[]{1, 0, 0}, new double[]{0, 1, 0}, new float[]{-90, 0, 0}},
                {"down, top south", new double[]{0, -1, 0}, new double[]{0, 0, 1}, new float[]{0, 90, 0}},
                {"up, top north", new double[]{0, 1, 0}, new double[]{0, 0, -1}, new float[]{0, -90, 0}},
                // the camera's up leaning 30 deg toward its right (south facing: right is west, -x): roll +30
                {"rolled right", new double[]{0, 0, 1}, new double[]{-s30, c30, 0}, new float[]{0, 0, 30}},
                {"pitched down 45", new double[]{0, -Math.sqrt(0.5), Math.sqrt(0.5)}, new double[]{0, Math.sqrt(0.5), Math.sqrt(0.5)}, new float[]{0, 45, 0}},
        };
        // the eye of a standing rest pose at the origin: about 1.6 m up, looking south (yaw 0), head up
        SkeletonPose rest = new SkeletonPose(SkeletonRig.get());
        rest.update();
        var eye = com.antaurora.apofirstlight.client.survivor.CorpsePoses.eye(rest, net.minecraft.world.phys.Vec3.ZERO);
        System.out.printf("  camera rest eye  at %s forward %s up %s%n", eye[0], eye[1], eye[2]);
        if (Math.abs(eye[0].y - 1.6) > 0.1 || eye[1].z < 0.999 || eye[2].y < 0.999) fail("death camera: rest eye");
        for (Object[] c : cases) {
            double[] f = (double[]) c[1], u = (double[]) c[2];
            float[] want = (float[]) c[3];
            float[] got = com.antaurora.apofirstlight.client.survivor.DeathCamera.angles(
                    new net.minecraft.world.phys.Vec3(f[0], f[1], f[2]), new net.minecraft.world.phys.Vec3(u[0], u[1], u[2]));
            boolean ok = true;
            for (int i = 0; i < 3; i++) ok &= Math.abs(net.minecraft.util.Mth.wrapDegrees(got[i] - want[i])) < 0.01;
            System.out.printf("  camera %-16s yaw %7.2f pitch %6.2f roll %6.2f%s%n", c[0], got[0], got[1], got[2], ok ? "" : "  (want " + java.util.Arrays.toString(want) + ")");
            if (!ok) fail("death camera angles: " + c[0]);
        }
    }

    /** How far the head box leans off its nearest face (deg): 0 = resting on a face, 45 = balanced on an edge. */
    private static double headTilt(RagdollBody body, RagdollDefinition def) {
        double best = 90;
        for (int a = 0; a < 3; a++) {
            Vector3d axis = body.orientation(def.head).transform(new Vector3d(a == 0 ? 1 : 0, a == 1 ? 1 : 0, a == 2 ? 1 : 0));
            best = Math.min(best, Math.toDegrees(Math.acos(Math.min(1, Math.abs(axis.y)))));
        }
        return best;
    }

    private static String fmt(Vector3d v) {
        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void fail(String message) {
        failures++;
        System.out.println("  FAIL " + message);
    }

    // ---------------------------------------------------------------- pictures

    private static final int W = 960, H = 540;

    private record Poly(float[] x, float[] y, float depth, Color color) {}

    private static void picture(RagdollDefinition def, SkeletonPose pose, Scene s, RagdollBody body, File out, int view) throws Exception {
        Vector3d c = body.position(def.pelvis);
        Vector3f at = new Vector3f((float) c.x, (float) c.y, (float) c.z);
        Vector3f eye = switch (view) {
            case 0 -> new Vector3f(at).add(3.2F, 1.4F, 0.4F);
            case 1 -> new Vector3f(at).add(-2.2F, 1.8F, 2.6F);
            default -> new Vector3f(at).add(0.01F, 4.0F, 0.02F);
        };
        Vector3f up = view == 2 ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0);
        Matrix4f viewM = new Matrix4f().lookAt(eye, at, up);
        float tanY = (float) Math.tan(Math.toRadians(35)), tanX = tanY * W / H;
        Vector3f light = new Vector3f(0.4F, 0.8F, 0.45F).normalize();
        List<Poly> polys = new ArrayList<>();
        // the body: skin quads, model px -> world
        Vector3d origin = s.origin();
        Quaterniond fix = RagdollBody.facing(0);
        java.util.function.Function<Vector3f, Vector3f> toWorld = m -> {
            Vector3d w = new Vector3d(m.x, m.y - RagdollBody.ORIGIN_Y, m.z).mul(RagdollDefinition.PX);
            fix.transform(w).add(origin);
            return new Vector3f((float) w.x, (float) w.y, (float) w.z);
        };
        for (PlayerSkeletonCheck.Quad q : PlayerSkeletonCheck.mesh(pose, false, false, k -> true)) {
            String part = def.rig.skin.get(q.part()).part();
            Color col = switch (part) {
                case "right_arm" -> new Color(205, 120, 95);
                case "left_arm" -> new Color(95, 135, 205);
                case "body" -> new Color(80, 150, 150);
                default -> new Color(70, 70, 140);
            };
            Vector3f[] v = new Vector3f[4];
            for (int k = 0; k < 4; k++) v[k] = viewM.transformPosition(toWorld.apply(q.p()[k]));
            add(polys, v, col, light, tanX, tanY, viewM);
        }
        int head = def.rig.head;
        float[][] hc = {{-4, -8, -4}, {4, -8, -4}, {4, 0, -4}, {-4, 0, -4}, {-4, -8, 4}, {4, -8, 4}, {4, 0, 4}, {-4, 0, 4}};
        int[][] hf = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {4, 5, 1, 0}, {3, 2, 6, 7}};
        for (int[] f : hf) {
            Vector3f[] v = new Vector3f[4];
            for (int k = 0; k < 4; k++)
                v[k] = viewM.transformPosition(toWorld.apply(pose.point(head, new Vector3f(hc[f[k]][0], hc[f[k]][1], hc[f[k]][2]), new Vector3f())));
            add(polys, v, f == hf[0] ? new Color(240, 200, 170) : new Color(200, 160, 130), light, tanX, tanY, viewM);
        }
        // the scene's boxes, faces cut into 0.5 m tiles so the painter's order holds near the body
        for (double[] b : s.boxes()) boxFaces(polys, b, viewM, light, tanX, tanY);
        polys.sort((a, b) -> Float.compare(a.depth(), b.depth()));
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(200, 210, 220));
        g.fillRect(0, 0, W, H);
        for (Poly p : polys) {
            int[] xs = new int[p.x().length], ys = new int[p.y().length];
            for (int k = 0; k < xs.length; k++) { xs[k] = Math.round(p.x()[k]); ys[k] = Math.round(p.y()[k]); }
            g.setColor(p.color());
            g.fillPolygon(new Polygon(xs, ys, xs.length));
        }
        g.setColor(Color.BLACK);
        g.drawString(s.name() + "  tick " + body.ticks(), 10, 20);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    private static void boxFaces(List<Poly> polys, double[] b, Matrix4f viewM, Vector3f light, float tanX, float tanY) {
        float tile = 0.5F;
        for (int axis = 0; axis < 3; axis++) for (int side = 0; side < 2; side++) {
            int u = (axis + 1) % 3, w = (axis + 2) % 3;
            double fixed = side == 0 ? b[axis] : b[axis + 3];
            for (double a = b[u]; a < b[u + 3] - 1e-9; a += tile) for (double c = b[w]; c < b[w + 3] - 1e-9; c += tile) {
                double a1 = Math.min(a + tile, b[u + 3]), c1 = Math.min(c + tile, b[w + 3]);
                double[][] corners = {{a, c}, {a1, c}, {a1, c1}, {a, c1}};
                Vector3f[] v = new Vector3f[4];
                for (int k = 0; k < 4; k++) {
                    float[] p = new float[3];
                    p[axis] = (float) fixed;
                    p[u] = (float) corners[k][0];
                    p[w] = (float) corners[k][1];
                    v[k] = viewM.transformPosition(new Vector3f(p[0], p[1], p[2]));
                }
                boolean checker = ((int) Math.floor(a / tile) + (int) Math.floor(c / tile)) % 2 == 0;
                add(polys, v, checker ? new Color(160, 160, 155) : new Color(140, 140, 135), light, tanX, tanY, viewM);
            }
        }
    }

    private static void add(List<Poly> polys, Vector3f[] v, Color c, Vector3f light, float tanX, float tanY, Matrix4f viewM) {
        float[] xs = new float[v.length], ys = new float[v.length];
        float depth = 0;
        for (int k = 0; k < v.length; k++) {
            if (v[k].z > -0.05F) return;
            xs[k] = W / 2F + v[k].x / -v[k].z / tanX * W / 2F;
            ys[k] = H / 2F - v[k].y / -v[k].z / tanY * H / 2F;
            depth += v[k].z;
        }
        Vector3f nrm = new Vector3f(v[2]).sub(v[0]).cross(new Vector3f(v[v.length - 1]).sub(v[1]));
        if (nrm.lengthSquared() < 1e-14F) return;
        nrm.normalize();
        Vector3f lv = new Matrix4f(viewM).transformDirection(new Vector3f(light)).normalize();
        float shade = 0.35F + 0.65F * Math.abs(nrm.dot(lv));
        polys.add(new Poly(xs, ys, depth / v.length,
                new Color(Math.min(255, (int) (c.getRed() * shade)), Math.min(255, (int) (c.getGreen() * shade)), Math.min(255, (int) (c.getBlue() * shade)))));
    }
}
