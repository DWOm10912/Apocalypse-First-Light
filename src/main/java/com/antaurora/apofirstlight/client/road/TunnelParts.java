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
 * texture. The interchange guide signs (C3.3b group 2) are built too (GuideSignParts), their legends a second stream.
 * Drawn by TunnelFixtureClient (the fixtures standing in a chunk) and the fixture items' renderer.
 */
final class TunnelParts {
    static final ResourceLocation SHEET = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/tunnel/equipment");
    /** The bone of a mesh without variants. */
    static final String ROOT = "root";
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private TunnelParts() {}

    /**
     * Baked parts: the solid stream and the overlay stream (vertices RoadClient.STRIDE floats each, relative to the chunk's
     * corner, y absolute; the overlay: the guide signs' legends, drawn as markings) and the vertex ranges that take one
     * light, of a given point instead of their own (a guide sign's face and legend: GuideSignParts).
     */
    record Baked(float[] solid, float[] overlay, List<Anchor> anchors) {}

    /** Vertices [from, to) of a stream (0 solid, 1 overlay) lit as the block at (x, y, z) (chunk-relative, y absolute). */
    record Anchor(int stream, int from, int to, double x, double y, double z) {}

    /**
     * The parts' vertices relative to chunk (cx, cz)'s corner (y absolute); equipment and steel: the sheet's and the steel
     * texture's sprites (u0, u1, v0, v1); guide: the guide signs' sheet's.
     */
    static Baked bake(List<TunnelMesh.Placement> placements, int cx, int cz, float[] equipment, float[] steel, float[] guide) {
        List<float[]> solid = new ArrayList<>(), overlay = new ArrayList<>();
        List<Anchor> anchors = new ArrayList<>();
        int ns = 0, no = 0;
        double ox = cx * 16.0, oz = cz * 16.0;
        for (TunnelMesh.Placement p : placements) {
            if ("guide_sign".equals(p.mesh())) {
                GuideSignParts.Local g = GuideSignParts.build(p.bone(), guide);
                double[] a = g.anchor();
                double ax = p.x() + p.ex()[0] * a[0] + p.ey()[0] * a[1] + p.ez()[0] * a[2] - ox, ay = p.y() + p.ex()[1] * a[0] + p.ey()[1] * a[1] + p.ez()[1] * a[2],
                        az = p.z() + p.ex()[2] * a[0] + p.ey()[2] * a[1] + p.ez()[2] * a[2] - oz;
                int sv = ns / RoadClient.STRIDE, ov = no / RoadClient.STRIDE;
                anchors.add(new Anchor(0, sv, sv + g.front(), ax, ay, az));
                anchors.add(new Anchor(1, ov, ov + g.overlay().length / RoadClient.STRIDE, ax, ay, az));
                solid.add(transform(g.solid(), p, ox, oz));
                overlay.add(transform(g.overlay(), p, ox, oz));
                ns += g.solid().length;
                no += g.overlay().length;
                continue;
            }
            float[] local = "handrail".equals(p.mesh()) ? handrail(TunnelFixtures.railLength(p.bone()), steel) : mesh(p, equipment);
            if (local == null) continue;
            solid.add(transform(local, p, ox, oz));
            ns += local.length;
        }
        return new Baked(join(solid, ns), join(overlay, no), anchors);
    }

    private static float[] join(List<float[]> pieces, int n) {
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
     * A handrail segment of length len in its frame (TunnelFixtures#handrail: the tube with closed ends, the brackets from
     * the wall).
     */
    static float[] handrail(double len, float[] uv) {
        List<Float> out = new ArrayList<>();
        double[][] boxes = TunnelFixtures.handrail(len);
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i];
            // the tube closed; an arm without its wall face
            box(out, uv, b[0], b[1], b[2], b[3], b[4], b[5], i == 0 ? 0b111111 : 0b111111 & ~(1 << 4), i == 0 ? len : 0.1);
        }
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
