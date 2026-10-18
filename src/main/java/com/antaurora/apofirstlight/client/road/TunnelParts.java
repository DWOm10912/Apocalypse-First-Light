package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tunnel's equipment parts (Tunnel V1; docs/worldgen/federal_tunnels_v1.md): the standalone Vextris meshes
 * apocalypse_firstlight:road/tunnel/&lt;mesh&gt; (tools/build-tunnel-v1.mjs) placed at TunnelMesh.Placement, baked into
 * RoadClient's vertex layout (relative to a chunk's corner, atlas UVs, four vertices per face: a triangle repeats its
 * last corner). All on the one sheet block/road/tunnel/equipment; all unlit (no power). The handrail segment (V1.1, the
 * rail that was part of the lining) is built here: a square tube with two brackets to the wall, on the lining's steel
 * texture. Drawn by TunnelFixtureClient (the fixtures standing in a chunk) and the fixture items' renderer.
 */
final class TunnelParts {
    static final ResourceLocation SHEET = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/tunnel/equipment");
    /** The bone of a mesh without variants. */
    static final String ROOT = "root";
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private TunnelParts() {}

    /**
     * The parts' vertices (RoadClient.STRIDE floats each) relative to chunk (cx, cz)'s corner (y absolute); equipment and
     * steel: the sheet's and the steel texture's sprites (u0, u1, v0, v1).
     */
    static float[] bake(List<TunnelMesh.Placement> placements, int cx, int cz, float[] equipment, float[] steel) {
        List<float[]> pieces = new ArrayList<>();
        int n = 0;
        for (TunnelMesh.Placement p : placements) {
            float[] local = "handrail".equals(p.mesh()) ? handrail(TunnelFixtures.railLength(p.bone()), steel) : mesh(p, equipment);
            if (local == null) continue;
            pieces.add(transform(local, p, cx * 16.0, cz * 16.0));
            n += local.length;
        }
        float[] out = new float[n];
        int o = 0;
        for (float[] f : pieces) {
            System.arraycopy(f, 0, out, o, f.length);
            o += f.length;
        }
        return out;
    }

    /** A part's mesh in its own frame (null: the mesh is missing). */
    private static float[] mesh(TunnelMesh.Placement p, float[] uv) {
        MeshModel model = VextrisMeshes.get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "road/tunnel/" + p.mesh()));
        if (model == null) {
            if (MISSING.add(p.mesh())) ApocalypseFirstLight.LOGGER.warn("[AFL roads] tunnel part mesh road/tunnel/{} is missing; not drawn", p.mesh());
            return null;
        }
        String bone = p.bone() == null ? ROOT : p.bone();
        int n = 0;
        for (MeshPart part : model.parts(bone, MeshLayer.CUTOUT)) n += part.faceCount();
        float[] out = new float[n * 4 * RoadClient.STRIDE];
        int o = 0;
        for (MeshPart part : model.parts(bone, MeshLayer.CUTOUT))
            for (int f = 0; f < part.faceCount(); f++) {
                int s = part.faceStart(f), size = part.faceSize(f);
                for (int k = 0; k < 4; k++) {
                    int c = s + Math.min(k, size - 1);
                    out[o++] = part.value(c, 0);
                    out[o++] = part.value(c, 1);
                    out[o++] = part.value(c, 2);
                    out[o++] = uv[0] + (uv[1] - uv[0]) * part.value(c, 3);
                    out[o++] = uv[2] + (uv[3] - uv[2]) * part.value(c, 4);
                    out[o++] = part.value(c, 5);
                    out[o++] = part.value(c, 6);
                    out[o++] = part.value(c, 7);
                }
            }
        return out;
    }

    /** Local vertices placed by p (origin, axes) relative to (ox, 0, oz). */
    private static float[] transform(float[] local, TunnelMesh.Placement p, double ox, double oz) {
        double[] ex = p.ex(), ey = p.ey(), ez = p.ez();
        float[] out = new float[local.length];
        for (int i = 0; i < local.length; i += RoadClient.STRIDE) {
            double x = local[i], y = local[i + 1], z = local[i + 2], nx = local[i + 5], ny = local[i + 6], nz = local[i + 7];
            out[i] = (float) (p.x() + ex[0] * x + ey[0] * y + ez[0] * z - ox);
            out[i + 1] = (float) (p.y() + ex[1] * x + ey[1] * y + ez[1] * z);
            out[i + 2] = (float) (p.z() + ex[2] * x + ey[2] * y + ez[2] * z - oz);
            out[i + 3] = local[i + 3];
            out[i + 4] = local[i + 4];
            out[i + 5] = (float) (ex[0] * nx + ey[0] * ny + ez[0] * nz);
            out[i + 6] = (float) (ex[1] * nx + ey[1] * ny + ez[1] * nz);
            out[i + 7] = (float) (ex[2] * nx + ey[2] * ny + ez[2] * nz);
        }
        return out;
    }

    /**
     * A handrail segment of length len in its frame (x along it, centred; y up from the walkway; z out of the wall): the
     * tube (TunnelMesh.RAIL_H high, RAIL_X off the wall, 2 RAIL square) with closed ends, and a bracket from the wall
     * BRACKET_IN in from each end (one in the middle of a short piece).
     */
    static float[] handrail(double len, float[] uv) {
        List<Float> out = new ArrayList<>();
        double h = len / 2, r = TunnelMesh.RAIL, y = TunnelMesh.RAIL_H, z = TunnelMesh.RAIL_X;
        box(out, uv, -h, y - r, z - r, h, y + r, z + r, 0b111111, len);
        double[] at = len >= 2 * TunnelMesh.BRACKET_IN + 0.2 ? new double[]{-h + TunnelMesh.BRACKET_IN, h - TunnelMesh.BRACKET_IN} : new double[]{0};
        // an arm from the wall under the tube's back half; not its wall face
        for (double bx : at) box(out, uv, bx - 0.03, y - r - 0.06, 0, bx + 0.03, y - r, z, 0b111111 & ~(1 << 4), 0.1);
        float[] f = new float[out.size()];
        for (int i = 0; i < f.length; i++) f[i] = out.get(i);
        return f;
    }

    /**
     * An axis box's faces (bit 2k: -axis k, bit 2k + 1: +axis k), counter-clockwise from outside; u along the face's first
     * axis over uLen m of texture (the sprite stretched), v a thin strip.
     */
    private static void box(List<Float> out, float[] uv, double x0, double y0, double z0, double x1, double y1, double z1, int faces, double uLen) {
        double[] lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int k = 0; k < 3; k++)
            for (int side = 0; side < 2; side++) {
                if ((faces & 1 << (2 * k + side)) == 0) continue;
                int a = (k + 1) % 3, b = (k + 2) % 3;
                double[][] c = new double[4][3];
                double[][] ab = {{lo[a], lo[b]}, {hi[a], lo[b]}, {hi[a], hi[b]}, {lo[a], hi[b]}};
                for (int q = 0; q < 4; q++) {
                    c[q][k] = side == 0 ? lo[k] : hi[k];
                    c[q][a] = ab[q][0];
                    c[q][b] = ab[q][1];
                }
                double[] nrm = new double[3];
                nrm[k] = side == 0 ? -1 : 1;
                // (c1 - c0) x (c2 - c0) along the normal: counter-clockwise seen from outside, else reversed
                double[] e1 = {c[1][0] - c[0][0], c[1][1] - c[0][1], c[1][2] - c[0][2]}, e2 = {c[2][0] - c[0][0], c[2][1] - c[0][1], c[2][2] - c[0][2]};
                double dot = (e1[1] * e2[2] - e1[2] * e2[1]) * nrm[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * nrm[1] + (e1[0] * e2[1] - e1[1] * e2[0]) * nrm[2];
                int[] order = dot >= 0 ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};
                for (int q : order) {
                    double ua = (c[q][a] - lo[a]) / Math.max(1e-6, uLen), vb = c[q][b] == lo[b] ? 0 : 0.1;
                    out.add((float) c[q][0]);
                    out.add((float) c[q][1]);
                    out.add((float) c[q][2]);
                    out.add(uv[0] + (uv[1] - uv[0]) * (float) Math.min(1, ua));
                    out.add(uv[2] + (uv[3] - uv[2]) * (float) vb);
                    out.add((float) nrm[0]);
                    out.add((float) nrm[1]);
                    out.add((float) nrm[2]);
                }
            }
    }
}
