package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.worldgen.network.road.BridgeMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCells;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hit meshes of the road network's bridges (docs/rendering/mesh_hit_runtime_v1.md 0.9; docs/worldgen/federal_highways_c3.md
 * 10.11; 2026-10-15). road_structure is invisible collision under the client's bridge mesh, so a road_structure block hits
 * on the drawn triangles inside its cell (the pavement and the concrete, cut to the block), not on its collision boxes:
 * the crosshair picks the concrete where it is seen and passes through the rest of the cell, the outline is that piece of
 * the mesh (its folds, and its cut at the block's sides), a bullet stops on the face it meets.
 * <ul>
 *   <li>client: the triangles RoadClient draws for the chunk (its current mesh, so a broken cell's repair is hit as it is
 *   drawn); none while that chunk's mesh is not built and scanned;</li>
 *   <li>server (bullets): the same composition (BridgeMesh.finish) on the structure cells it scans itself
 *   (RoadStructureBlock.scan), rescanned when used at most every {@link #RESCAN_NANOS}, only with the chunk and its ring
 *   loaded (never loads one).</li>
 * </ul>
 * A block with no drawn triangle in its cell (inside an abutment) has no hit mesh: its collision boxes, as before.
 */
public final class RoadStructureHits {
    private RoadStructureHits() {}

    /** A chunk's solid vertices as a renderer keeps them: {@code corners} per triangle (the first three are a, b, c), {@code stride} floats each, x y z first (x, z chunk-relative). */
    public record Drawn(float[] data, int stride, int corners) {}

    @FunctionalInterface
    public interface ClientSource {
        /** The drawn solid vertices of chunk (cx, cz), or null (not built); the same array while the mesh is unchanged. */
        @Nullable Drawn drawn(int cx, int cz);
    }

    private static volatile ClientSource client;

    /** RoadClient hands over its meshes (client only). */
    public static void client(ClientSource source) { client = source; }

    static final long RESCAN_NANOS = 500_000_000L;
    private static final int CHUNKS = 128;
    private static final double EPS = 1e-5;
    private static final Quaternionf IDENTITY = new Quaternionf();

    /** One chunk's triangles (9 floats each: x, z chunk-relative, y absolute), the triangles reaching each column, the blocks' meshes. */
    private static final class Chunk {
        final Object source;
        final float[] tri;
        final int[][] columns;
        final ConcurrentHashMap<Long, Optional<MeshHitModels.Shape>> blocks = new ConcurrentHashMap<>();
        // server: what the triangles were made from, and when the structure cells were last compared with the world
        final RoadWorks works;
        final Map<Integer, BridgeMesh.Expect> expect;
        final Map<Integer, BridgeMesh.Found> found;
        volatile long checked;

        Chunk(Object source, float[] tri, RoadWorks works, Map<Integer, BridgeMesh.Expect> expect, Map<Integer, BridgeMesh.Found> found) {
            this.source = source;
            this.tri = tri;
            this.works = works;
            this.expect = expect;
            this.found = found;
            this.checked = System.nanoTime();
            List<List<Integer>> cols = new ArrayList<>(256);
            for (int i = 0; i < 256; i++) cols.add(null);
            for (int t = 0; t * 9 + 8 < tri.length; t++) {
                int o = t * 9;
                float x0 = Math.min(tri[o], Math.min(tri[o + 3], tri[o + 6])), x1 = Math.max(tri[o], Math.max(tri[o + 3], tri[o + 6]));
                float z0 = Math.min(tri[o + 2], Math.min(tri[o + 5], tri[o + 8])), z1 = Math.max(tri[o + 2], Math.max(tri[o + 5], tri[o + 8]));
                int i0 = Math.max(0, (int) Math.floor(x0 - EPS)), i1 = Math.min(15, (int) Math.floor(x1 + EPS));
                int j0 = Math.max(0, (int) Math.floor(z0 - EPS)), j1 = Math.min(15, (int) Math.floor(z1 + EPS));
                for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
                    List<Integer> c = cols.get(i | j << 4);
                    if (c == null) cols.set(i | j << 4, c = new ArrayList<>());
                    c.add(t);
                }
            }
            columns = new int[256][];
            for (int i = 0; i < 256; i++) {
                List<Integer> c = cols.get(i);
                if (c != null) columns[i] = c.stream().mapToInt(Integer::intValue).toArray();
            }
        }
    }

    private static final ConcurrentHashMap<Long, Chunk> CLIENT = new ConcurrentHashMap<>(), SERVER = new ConcurrentHashMap<>();

    /** The hit mesh of the road_structure block at pos, or null (no road network, the chunk's mesh unknown, nothing drawn in the cell). */
    @Nullable
    static MeshHitModels.Shape shape(BlockGetter level, BlockPos pos) {
        if (!(level instanceof Level l)) return null;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        Chunk c = l.isClientSide() ? clientChunk(cx, cz) : serverChunk(l, cx, cz);
        if (c == null) return null;
        return c.blocks.computeIfAbsent(pos.asLong(), k -> Optional.ofNullable(block(c, pos))).orElse(null);
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    @Nullable
    private static Chunk clientChunk(int cx, int cz) {
        ClientSource src = client;
        Drawn d = src == null ? null : src.drawn(cx, cz);
        if (d == null) return null;
        long k = key(cx, cz);
        Chunk c = CLIENT.get(k);
        if (c != null && c.source == d.data()) return c;
        int n = d.data().length / (d.stride() * d.corners());
        float[] tri = new float[n * 9];
        for (int t = 0; t < n; t++)
            for (int v = 0; v < 3; v++) {
                int o = (t * d.corners() + v) * d.stride();
                tri[t * 9 + v * 3] = d.data()[o];
                tri[t * 9 + v * 3 + 1] = d.data()[o + 1];
                tri[t * 9 + v * 3 + 2] = d.data()[o + 2];
            }
        c = new Chunk(d.data(), tri, null, null, null);
        if (CLIENT.size() > CHUNKS) CLIENT.clear();
        CLIENT.put(k, c);
        return c;
    }

    @Nullable
    private static Chunk serverChunk(Level level, int cx, int cz) {
        RoadWorks works = RoadStructureBlock.boundWorks();
        if (works == null) return null;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (!level.hasChunk(cx + dx, cz + dz)) return null;
        long k = key(cx, cz);
        Chunk c = SERVER.get(k);
        long now = System.nanoTime();
        if (c != null && c.works == works && now - c.checked < RESCAN_NANOS) return c;
        Map<Integer, BridgeMesh.Expect> expect = c != null && c.works == works ? c.expect : BridgeMesh.expected(works, cx, cz);
        Map<Integer, BridgeMesh.Found> found = RoadStructureBlock.scan(level, cx, cz, expect);
        if (c != null && c.works == works && found.equals(c.found)) { c.checked = now; return c; }
        List<RoadMesh.Triangle> pavement = new ArrayList<>();
        for (RoadDesign d : works.roads()) pavement.addAll(RoadMesh.chunk(d, cx, cz));
        Map<Integer, RoadCells.Cell> cells = pavement.isEmpty() ? Map.of() : RoadCells.of(works.roads(), works, cx, cz);
        List<RoadMesh.Triangle> tris = BridgeMesh.finish(pavement, works.roads(), works, cx, cz, cells, new BitSet(), expect, found);
        int n = 0;
        for (RoadMesh.Triangle t : tris) if (solid(t)) n++;
        float[] tri = new float[n * 9];
        int o = 0;
        for (RoadMesh.Triangle t : tris) {
            if (!solid(t)) continue;
            for (RoadMesh.Vertex v : new RoadMesh.Vertex[]{t.a(), t.b(), t.c()}) {
                tri[o++] = (float) v.x();
                tri[o++] = (float) v.y();
                tri[o++] = (float) v.z();
            }
        }
        c = new Chunk(found, tri, works, expect, found);
        if (SERVER.size() > CHUNKS) SERVER.clear();
        SERVER.put(k, c);
        return c;
    }

    /** The asphalt and the concrete (the markings lie on the asphalt). */
    private static boolean solid(RoadMesh.Triangle t) {
        return t.material() == RoadMesh.Material.ASPHALT || t.material() == RoadMesh.Material.CONCRETE;
    }

    /** The triangles of c inside the block at pos, cut to it, block-local; null when none. */
    @Nullable
    private static MeshHitModels.Shape block(Chunk c, BlockPos pos) {
        int lx = pos.getX() & 15, lz = pos.getZ() & 15, y = pos.getY();
        int[] idx = c.columns[lx | lz << 4];
        if (idx == null) return null;
        List<Float> out = new ArrayList<>();
        List<double[]> poly = new ArrayList<>(9);
        for (int t : idx) {
            int o = t * 9;
            float y0 = Math.min(c.tri[o + 1], Math.min(c.tri[o + 4], c.tri[o + 7])), y1 = Math.max(c.tri[o + 1], Math.max(c.tri[o + 4], c.tri[o + 7]));
            if (y1 < y - EPS || y0 > y + 1 + EPS) continue;
            poly.clear();
            for (int v = 0; v < 3; v++) poly.add(new double[]{c.tri[o + v * 3] - lx, c.tri[o + v * 3 + 1] - y, c.tri[o + v * 3 + 2] - lz});
            List<double[]> p = poly;
            for (int axis = 0; axis < 3 && p.size() >= 3; axis++) {
                p = cut(p, axis, 0, false);
                p = cut(p, axis, 1, true);
            }
            for (int i = 1; i + 1 < p.size(); i++)
                for (double[] q : new double[][]{p.get(0), p.get(i), p.get(i + 1)}) {
                    out.add((float) q[0]);
                    out.add((float) q[1]);
                    out.add((float) q[2]);
                }
        }
        if (out.size() < 9) return null;
        float[] tri = new float[out.size()];
        for (int i = 0; i < tri.length; i++) tri[i] = out.get(i);
        MeshHitModel model = MeshHitModel.of(tri);
        if (model == null) return null;
        return new MeshHitModels.Shape(List.of(new MeshHitModels.Placed(model, IDENTITY, IDENTITY)));
    }

    /**
     * A convex polygon cut to component axis >= edge (upper false) or <= edge (upper true), widened by EPS: a face lying on
     * the block's side (a wall's side towards a cell that is not wall) stays whole.
     */
    private static List<double[]> cut(List<double[]> in, int axis, double edge, boolean upper) {
        List<double[]> out = new ArrayList<>(in.size() + 2);
        if (in.size() < 3) return out;   // cut away by the previous side
        double[] a = in.get(in.size() - 1);
        double da = (upper ? edge - a[axis] : a[axis] - edge) + EPS;
        for (double[] b : in) {
            double db = (upper ? edge - b[axis] : b[axis] - edge) + EPS;
            boolean ia = da >= 0, ib = db >= 0;
            if (ia != ib) {
                double t = da / (da - db);
                double[] q = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
                q[axis] = upper ? edge + EPS : edge - EPS;
                out.add(q);
            }
            if (ib) out.add(b);
            a = b;
            da = db;
        }
        return out;
    }
}
