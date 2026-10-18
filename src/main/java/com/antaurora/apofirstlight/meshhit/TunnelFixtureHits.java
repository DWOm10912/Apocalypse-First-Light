package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hit meshes of the tunnel fixtures and road signs (block/TunnelFixtureBlock; docs/rendering/mesh_hit_runtime_v1.md 0.10,
 * 2026-10-16). A fixture cell is invisible and the client draws its parts from the Vextris meshes road/tunnel/&lt;mesh&gt;
 * (tools/build-tunnel-v1.mjs; the handrail from TunnelFixtures#handrail), so the cell hits on those triangles, each part
 * placed as it is drawn and cut to the cell: the crosshair picks a sign's face or post where it is seen and passes beside
 * them, a bullet stops on the face it meets; not on the cell's 1/8 block columns, a staircase around a part standing at
 * an angle. The selection outline is the aimed part's whole mesh (its feature edges) from any of its cells. Both sides
 * read the meshes from the jar. A cell that took over a road_structure adds the bridge's hit mesh in it
 * (RoadStructureHits); while that one is unknown, or a part's mesh is missing, the cell's boxes as before.
 */
public final class TunnelFixtureHits {
    private TunnelFixtureHits() {}

    /** The bone of a mesh without variants (as client/road/TunnelParts). */
    private static final String ROOT = "root";
    /** Cut cells kept per side before the cache starts over. */
    private static final int CELLS = 4096;
    /** How far a look reaches for the outline's bridge test, blocks. */
    private static final double REACH = 64;
    private static final Quaternionf IDENTITY = new Quaternionf();

    /** A part's triangles (9 floats each) relative to its anchor cell (the block holding its origin), and their model. */
    private record Placed(int ax, int ay, int az, float[] tri, MeshHitModel model) {}

    /** A cell's cut hit mesh while its block entity holds the same entries (it replaces the list on every change). */
    private record Cell(List<TunnelFixtureBlockEntity.Entry> entries, Optional<MeshHitModels.Shape> shape) {}

    /** Each mesh's part-local triangles by bone; empty: the mesh is missing. */
    private static final Map<String, Optional<Map<String, float[]>>> MESHES = new ConcurrentHashMap<>();
    /** The placed parts (weak: a part goes with its block entity's entry). */
    private static final Map<TunnelFixtures.Part, Optional<Placed>> PLACED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Long, Cell> CLIENT = new ConcurrentHashMap<>(), SERVER = new ConcurrentHashMap<>();

    /** The hit mesh of the fixture cell at pos, or null (no parts yet, a mesh missing, the taken-over bridge's mesh unknown). */
    @Nullable
    static MeshHitModels.Shape shape(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TunnelFixtureBlockEntity be) || !be.placed()) return null;
        Map<Long, Cell> cache = level instanceof Level l && l.isClientSide() ? CLIENT : SERVER;
        List<TunnelFixtureBlockEntity.Entry> entries = be.entries();
        long key = pos.asLong();
        Cell c = cache.get(key);
        if (c == null || c.entries() != entries) {
            if (cache.size() > CELLS) cache.clear();
            cache.put(key, c = new Cell(entries, Optional.ofNullable(cut(entries, pos))));
        }
        MeshHitModels.Shape parts = c.shape().orElse(null);
        if (parts == null || be.structure() == 0) return parts;
        MeshHitModels.Shape bridge = RoadStructureHits.shape(level, pos);
        if (bridge == null) return null;
        List<MeshHitModels.Placed> both = new ArrayList<>(parts.parts);
        both.addAll(bridge.parts);
        return new MeshHitModels.Shape(both);
    }

    /**
     * The selection outline of the fixture cell at pos for a look from eye along dir: the whole mesh of the part it aims at
     * (TunnelFixtureBlockEntity#aimed), in the cell's coordinates; the bridge's piece when the look meets that first (a cell
     * that took over a road_structure); null: neither (the cell's boxes).
     */
    @Nullable
    static MeshHitModels.Shape outline(BlockGetter level, BlockPos pos, Vec3 eye, Vec3 dir) {
        if (!(level.getBlockEntity(pos) instanceof TunnelFixtureBlockEntity be) || !be.placed()) return null;
        TunnelFixtureBlockEntity.Entry e = be.aimed(eye, dir);
        Placed p = e == null ? null : placed(e.part());
        if (be.structure() > 0) {
            MeshHitModels.Shape bridge = RoadStructureHits.shape(level, pos);
            MeshBlockHitResult b = bridge == null ? null : bridge.clip(eye, eye.add(dir.normalize().scale(REACH)), pos);
            if (b != null && (p == null || b.getLocation().distanceTo(eye) < ray(e.part(), eye, dir) * dir.length())) return bridge;
        }
        if (p == null) return null;
        return new MeshHitModels.Shape(List.of(new MeshHitModels.Placed(p.model(), IDENTITY, IDENTITY)))
                .moved(p.ax() - pos.getX(), p.ay() - pos.getY(), p.az() - pos.getZ());
    }

    /** Where a ray from eye along dir first meets the part's mesh (units of dir); +inf: never; NaN: its mesh is missing. */
    public static double ray(TunnelFixtures.Part part, Vec3 eye, Vec3 dir) {
        Placed p = placed(part);
        if (p == null) return Double.NaN;
        double[] hit = p.model().intersect(eye.x - p.ax(), eye.y - p.ay(), eye.z - p.az(), dir.x, dir.y, dir.z, 1e6);
        return hit == null ? Double.POSITIVE_INFINITY : hit[0];
    }

    /** The triangles of the cell's parts inside it, cut to it, block-local; null: a part's mesh is missing. */
    @Nullable
    private static MeshHitModels.Shape cut(List<TunnelFixtureBlockEntity.Entry> entries, BlockPos pos) {
        List<Float> out = new ArrayList<>();
        List<double[]> poly = new ArrayList<>(3);
        for (TunnelFixtureBlockEntity.Entry e : entries) {
            Placed p = placed(e.part());
            if (p == null) return null;
            double ox = p.ax() - pos.getX(), oy = p.ay() - pos.getY(), oz = p.az() - pos.getZ();
            float[] t = p.tri();
            for (int i = 0; i + 8 < t.length; i += 9) {
                if (Math.max(t[i], Math.max(t[i + 3], t[i + 6])) + ox < -1e-4 || Math.min(t[i], Math.min(t[i + 3], t[i + 6])) + ox > 1 + 1e-4
                        || Math.max(t[i + 1], Math.max(t[i + 4], t[i + 7])) + oy < -1e-4 || Math.min(t[i + 1], Math.min(t[i + 4], t[i + 7])) + oy > 1 + 1e-4
                        || Math.max(t[i + 2], Math.max(t[i + 5], t[i + 8])) + oz < -1e-4 || Math.min(t[i + 2], Math.min(t[i + 5], t[i + 8])) + oz > 1 + 1e-4)
                    continue;
                poly.clear();
                for (int v = 0; v < 3; v++) poly.add(new double[]{t[i + v * 3] + ox, t[i + v * 3 + 1] + oy, t[i + v * 3 + 2] + oz});
                List<double[]> q = poly;
                for (int axis = 0; axis < 3 && q.size() >= 3; axis++) {
                    q = RoadStructureHits.cut(q, axis, 0, false);
                    q = RoadStructureHits.cut(q, axis, 1, true);
                }
                for (int k = 1; k + 1 < q.size(); k++) add(out, q.get(0), q.get(k), q.get(k + 1));
            }
        }
        float[] tri = new float[out.size()];
        for (int i = 0; i < tri.length; i++) tri[i] = out.get(i);
        MeshHitModel model = MeshHitModel.of(tri);
        return new MeshHitModels.Shape(model == null ? List.of() : List.of(new MeshHitModels.Placed(model, IDENTITY, IDENTITY)));
    }

    /** A triangle's corners into out, unless it has no area (a sliver left by the cut). */
    private static void add(List<Float> out, double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        if (nx * nx + ny * ny + nz * nz < 1e-14) return;
        for (double[] q : new double[][]{a, b, c}) {
            out.add((float) q[0]);
            out.add((float) q[1]);
            out.add((float) q[2]);
        }
    }

    @Nullable
    private static Placed placed(TunnelFixtures.Part part) {
        Optional<Placed> p = PLACED.get(part);
        if (p == null) {
            p = Optional.ofNullable(place(part.placement));
            PLACED.put(part, p);
        }
        return p.orElse(null);
    }

    /** A part's triangles placed as TunnelParts draws it (origin, axes), relative to the block holding its origin. */
    @Nullable
    private static Placed place(TunnelMesh.Placement p) {
        float[] local = local(p.mesh(), p.bone());
        if (local == null) return null;
        int ax = (int) Math.floor(p.x()), ay = (int) Math.floor(p.y()), az = (int) Math.floor(p.z());
        double bx = p.x() - ax, by = p.y() - ay, bz = p.z() - az;
        double[] ex = p.ex(), ey = p.ey(), ez = p.ez();
        float[] tri = new float[local.length];
        for (int i = 0; i + 2 < local.length; i += 3) {
            double x = local[i], y = local[i + 1], z = local[i + 2];
            tri[i] = (float) (bx + ex[0] * x + ey[0] * y + ez[0] * z);
            tri[i + 1] = (float) (by + ex[1] * x + ey[1] * y + ez[1] * z);
            tri[i + 2] = (float) (bz + ex[2] * x + ey[2] * y + ez[2] * z);
        }
        MeshHitModel model = MeshHitModel.of(tri);
        return model == null ? null : new Placed(ax, ay, az, tri, model);
    }

    /** A part's triangles in its own frame: its mesh's parts of its bone (as TunnelParts draws them), or the handrail's or a guide sign's boxes; null: missing. */
    @Nullable
    private static float[] local(String mesh, @Nullable String bone) {
        if ("handrail".equals(mesh)) return boxes(TunnelFixtures.handrail(TunnelFixtures.railLength(bone)));
        // a guide sign: its panel and tab, posts and stringers as drawn (C3.3b group 2, GuideSigns)
        if ("guide_sign".equals(mesh)) return boxes(com.antaurora.apofirstlight.worldgen.network.road.GuideSigns.drawn(bone));
        Map<String, float[]> bones = MESHES.computeIfAbsent(mesh, TunnelFixtureHits::read).orElse(null);
        return bones == null ? null : bones.get(bone == null ? ROOT : bone);
    }

    /** Boxes {x0, y0, z0, x1, y1, z1} as triangles, every side. */
    private static float[] boxes(double[][] boxes) {
        float[] out = new float[boxes.length * 108];
        int o = 0;
        for (double[] b : boxes)
            for (int k = 0; k < 3; k++)
                for (int side = 0; side < 2; side++) {
                    int a = (k + 1) % 3, c = (k + 2) % 3;
                    double[][] ac = {{b[a], b[c]}, {b[a + 3], b[c]}, {b[a + 3], b[c + 3]}, {b[a], b[c + 3]}};
                    double[][] q = new double[4][3];
                    for (int i = 0; i < 4; i++) {
                        q[i][k] = b[k + 3 * side];
                        q[i][a] = ac[i][0];
                        q[i][c] = ac[i][1];
                    }
                    for (int i : new int[]{0, 1, 2, 0, 2, 3}) for (int j = 0; j < 3; j++) out[o++] = (float) q[i][j];
                }
        return out;
    }

    /** A mesh's triangles by bone from assets/apocalypse_firstlight/meshes/road/tunnel/&lt;mesh&gt;.vxmesh.json (faces of 3 or 4 corners, convex). */
    private static Optional<Map<String, float[]>> read(String mesh) {
        String path = "/assets/" + ApocalypseFirstLight.MOD_ID + "/meshes/road/tunnel/" + mesh + ".vxmesh.json";
        try (InputStream in = TunnelFixtureHits.class.getResourceAsStream(path)) {
            if (in == null) {
                ApocalypseFirstLight.LOGGER.warn("[AFL MESH HIT] tunnel part mesh {} is missing; its cells hit on their boxes", path);
                return Optional.empty();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, List<Float>> byBone = new HashMap<>();
            for (JsonElement pe : root.getAsJsonArray("parts")) {
                JsonObject part = pe.getAsJsonObject();
                String bone = part.has("bone") ? part.get("bone").getAsString() : ROOT;
                JsonArray vs = part.getAsJsonArray("vertices");
                double[][] v = new double[vs.size()][];
                for (int i = 0; i < v.length; i++) {
                    JsonArray a = vs.get(i).getAsJsonArray();
                    v[i] = new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
                }
                List<Float> out = byBone.computeIfAbsent(bone, k -> new ArrayList<>());
                for (JsonElement fe : part.getAsJsonArray("faces")) {
                    JsonArray f = fe.getAsJsonArray();
                    for (int k = 1; k + 1 < f.size(); k++) add(out, v[f.get(0).getAsInt()], v[f.get(k).getAsInt()], v[f.get(k + 1).getAsInt()]);
                }
            }
            Map<String, float[]> bones = new HashMap<>();
            for (Map.Entry<String, List<Float>> e : byBone.entrySet()) {
                float[] tri = new float[e.getValue().size()];
                for (int i = 0; i < tri.length; i++) tri[i] = e.getValue().get(i);
                bones.put(e.getKey(), tri);
            }
            return Optional.of(bones);
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.error("[AFL MESH HIT] could not read tunnel part mesh {}", path, e);
            return Optional.empty();
        }
    }
}
