package com.antaurora.apofirstlight.terrainsmooth.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.List;

/**
 * The orientation of a vanilla top texture at a position, for the sloped tops drawn instead (C3.3a group B, 2026-10-15):
 * vanilla's grass block (and other ground blocks) pick one of four y-rotated variants per position from the block's
 * position seed, which scatters the top texture; a sloped top that projected the texture one fixed way showed a regular
 * tiling. A model asks the vanilla model for its UP quads with the renderer's position random (seeded like vanilla's own
 * call) and copies their corner UVs, so a sloped top shows exactly the orientation the flat block would have there.
 * <p>
 * Orientation code: for the top corners nw (0, 0), sw (0, 1), se (1, 1), ne (1, 0) (the order the sloped tops are built
 * in), 2 bits each (u | v << 1) of the texture corner drawn there.
 */
public final class TopTexture {
    /** u = x, v = z: the vanilla top unrotated */
    public static final int PLAIN = 0 | 2 << 2 | 3 << 4 | 1 << 6;

    private TopTexture() {}

    /** The orientation of the first of these UP quads (a full top face), PLAIN when there is none or it is not one. */
    public static int orientation(List<BakedQuad> up) {
        if (up.isEmpty()) return PLAIN;
        BakedQuad q = up.get(0);
        TextureAtlasSprite s = q.getSprite();
        int[] v = q.getVertices();
        int stride = v.length / 4, out = 0, seen = 0;
        float du = s.getU1() - s.getU0(), dv = s.getV1() - s.getV0();
        if (Math.abs(du) < 1e-9F || Math.abs(dv) < 1e-9F) return PLAIN;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(v[i * stride]), z = Float.intBitsToFloat(v[i * stride + 2]);
            float u = (Float.intBitsToFloat(v[i * stride + 4]) - s.getU0()) / du, w = (Float.intBitsToFloat(v[i * stride + 5]) - s.getV0()) / dv;
            int corner = x < 0.5F ? (z < 0.5F ? 0 : 1) : (z < 0.5F ? 3 : 2);
            out |= ((u > 0.5F ? 1 : 0) | (w > 0.5F ? 2 : 0)) << (2 * corner);
            seen |= 1 << corner;
        }
        return seen == 15 ? out : PLAIN;
    }

    /** UVs (0..1) of the corners nw, sw, se, ne for an orientation. */
    public static float[][] uv(int orientation) {
        float[][] out = new float[4][];
        for (int k = 0; k < 4; k++) {
            int c = orientation >> (2 * k) & 3;
            out[k] = new float[]{c & 1, c >> 1};
        }
        return out;
    }

    /**
     * UVs of points {fx, y, fz} of a top (a quarter of a fan top, dig R1), from the corner UVs (#uv, nw, sw, se, ne)
     * spread over the block, so the quarters show one texture as the plain top does.
     */
    public static float[][] uvAt(float[][] corners, float[][] points) {
        float[][] out = new float[points.length][];
        for (int i = 0; i < points.length; i++) {
            float x = points[i][0], z = points[i][2];
            float[] w = {(1 - x) * (1 - z), (1 - x) * z, x * z, x * (1 - z)};
            float u = 0, v = 0;
            for (int k = 0; k < 4; k++) {
                u += w[k] * corners[k][0];
                v += w[k] * corners[k][1];
            }
            out[i] = new float[]{u, v};
        }
        return out;
    }

    /**
     * Vertex colour of a sloped top without a shader pack: vanilla-like directional shading by its tilt (as the natural
     * slopes, SmoothSurfaceModel), white with one (the pack shades from the normals).
     */
    public static int topColor(float[][] p) {
        return TerrainSmoothClient.shaderPack ? -1 : SmoothSurfaceModel.gray(SmoothSurfaceModel.tiltShade(p));
    }

    /** Whether a shader pack is in use (the sloped tops' colours depend on it: a cache key). */
    public static boolean shaderPack() { return TerrainSmoothClient.shaderPack; }
}
