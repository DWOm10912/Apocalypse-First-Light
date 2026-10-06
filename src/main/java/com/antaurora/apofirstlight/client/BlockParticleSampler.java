package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a block's breaking and digging particles take their pixels from (2026-10-06, the user: the particles showed whole
 * patches of a texture atlas, of every colour in it). Vanilla cuts a quarter of the block's particle texture, at random
 * (TerrainParticle): a quarter of a 16 px texture is 4 px of one block's look, but most AFL blocks give a whole atlas as
 * their particle texture (128 to 2048 px, often shared: one atlas for a blue drum, a red drum and a jerry can), so a
 * particle showed a 256 px square of anything. For a particle texture wider than {@link #SMALL} px this picks instead a
 * face the block's model actually draws (weighted by its area) and a point well inside it, and cuts a window of
 * {@link #TEXELS} texels there: each fleck is a bit of the block's own surface (mixin/client/TerrainParticleSampleMixin).
 * A model without faces (drawn by a block entity renderer) gets the same small window at a random point of its texture.
 */
public final class BlockParticleSampler {
    /** Particle textures this wide or narrower keep vanilla's quarter (a 16 or 32 px texture is one block's look). */
    public static final int SMALL = 32;
    /** Half the window, in texels of the face's own texture. */
    private static final float TEXELS = 1.5F;
    private static final int STRIDE = 8, POS = 0, UV = 4;   // DefaultVertexFormat.BLOCK, in ints

    /** One face: its corners' atlas UVs (4 x u, v), its texture's texel size in the atlas, and its weight. */
    private record Face(float[] uv, float texel) {}

    private record Faces(BakedModel model, List<Face> faces, float[] cumulative) {}

    private static final Map<BlockState, Faces> CACHE = new ConcurrentHashMap<>();

    private BlockParticleSampler() {
    }

    /** A window {u0, u1, v0, v1} (atlas UVs) for a particle of {@code state} drawn with {@code sprite}, or null for vanilla's. */
    @Nullable
    public static float[] sample(BlockState state, TextureAtlasSprite sprite, RandomSource random) {
        if (sprite.contents().width() <= SMALL && sprite.contents().height() <= SMALL) return null;
        Faces faces = faces(state);
        if (faces.faces.isEmpty()) {   // no faces to look at: a small window anywhere in the texture
            float tu = (sprite.getU1() - sprite.getU0()) / sprite.contents().width(), tv = (sprite.getV1() - sprite.getV0()) / sprite.contents().height();
            float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (0.1F + 0.8F * random.nextFloat());
            float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (0.1F + 0.8F * random.nextFloat());
            return new float[]{u + TEXELS * tu, u - TEXELS * tu, v - TEXELS * tv, v + TEXELS * tv};
        }
        float pick = random.nextFloat() * faces.cumulative[faces.cumulative.length - 1];
        int i = 0;
        while (i < faces.cumulative.length - 1 && faces.cumulative[i] < pick) i++;
        Face face = faces.faces.get(i);
        // a point well inside the face (bilinear over its corners), so the window keeps off its island's edge
        float s = 0.3F + 0.4F * random.nextFloat(), t = 0.3F + 0.4F * random.nextFloat();
        float[] c = face.uv;
        float u = (1 - s) * (1 - t) * c[0] + s * (1 - t) * c[2] + s * t * c[4] + (1 - s) * t * c[6];
        float v = (1 - s) * (1 - t) * c[1] + s * (1 - t) * c[3] + s * t * c[5] + (1 - s) * t * c[7];
        float spanU = Math.max(Math.max(c[0], c[2]), Math.max(c[4], c[6])) - Math.min(Math.min(c[0], c[2]), Math.min(c[4], c[6]));
        float spanV = Math.max(Math.max(c[1], c[3]), Math.max(c[5], c[7])) - Math.min(Math.min(c[1], c[3]), Math.min(c[5], c[7]));
        float hu = Math.min(TEXELS * face.texel, 0.15F * spanU), hv = Math.min(TEXELS * face.texel, 0.15F * spanV);
        return new float[]{u + hu, u - hu, v - hv, v + hv};
    }

    private static Faces faces(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        Faces cached = CACHE.get(state);
        if (cached != null && cached.model == model) return cached;   // (a resource reload bakes new models)
        List<Face> faces = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>();
        try {
            for (Direction side : Direction.values()) quads.addAll(model.getQuads(state, side, random, ModelData.EMPTY, null));
            quads.addAll(model.getQuads(state, null, random, ModelData.EMPTY, null));
        } catch (RuntimeException e) {
            quads.clear();   // a model that needs level data: vanilla's window then
        }
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            if (data.length < 4 * STRIDE) continue;
            float[] uv = new float[8], p = new float[12];
            for (int k = 0; k < 4; k++) {
                for (int a = 0; a < 3; a++) p[k * 3 + a] = Float.intBitsToFloat(data[k * STRIDE + POS + a]);
                uv[k * 2] = Float.intBitsToFloat(data[k * STRIDE + UV]);
                uv[k * 2 + 1] = Float.intBitsToFloat(data[k * STRIDE + UV + 1]);
            }
            float area = triangle(p, 0, 1, 2) + triangle(p, 0, 2, 3);
            if (area < 1e-6F) continue;
            TextureAtlasSprite sprite = quad.getSprite();
            faces.add(new Face(uv, (sprite.getU1() - sprite.getU0()) / sprite.contents().width()));
            weights.add(area);
        }
        float[] cumulative = new float[weights.size()];
        float sum = 0;
        for (int k = 0; k < cumulative.length; k++) cumulative[k] = sum += weights.get(k);
        Faces result = new Faces(model, faces, cumulative);
        CACHE.put(state, result);
        return result;
    }

    private static float triangle(float[] p, int a, int b, int c) {
        float ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
        float vx = p[c * 3] - p[a * 3], vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
        float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        return 0.5F * (float) Math.sqrt(x * x + y * y + z * z);
    }
}
