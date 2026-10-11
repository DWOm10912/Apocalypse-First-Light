package com.antaurora.apofirstlight.dev.terrainsmooth;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prototype chunk model of a small plant (grass, ferns, flowers, saplings, both halves of tall plants): standing on a
 * sloped block of the test area, it moves up or down to the sloped surface under its own (randomly offset) position, so
 * it neither floats over a lowered slope nor sinks into a raised one. Elsewhere exactly the vanilla model.
 */
final class SinkPlantModel extends BakedModelWrapper<BakedModel> {
    static final ModelProperty<Integer> SHIFT = new ModelProperty<>();   // in 1/64 block
    private final Map<Long, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    SinkPlantModel(BakedModel original) {
        super(original);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        SmoothWorld.Config cfg = SmoothWorld.CLIENT;
        if (!cfg.active() || !cfg.contains(pos.getX(), pos.getZ())) return base;
        SmoothPrototype.plantCalls.increment();
        int support = pos.getY() - 1;
        if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) support--;
        long g = SmoothWorld.geometry(level, cfg, pos.getX(), support, pos.getZ());
        if (g < 0) return base;
        float[] c = SmoothRules.corners(g);   // above the support's floor
        Vec3 off = state.getOffset(level, pos);
        double fx = Math.max(0, Math.min(1, 0.5 + off.x)), fz = Math.max(0, Math.min(1, 0.5 + off.z));
        // the sloped top is drawn as triangles (nw, sw, se) and (nw, se, ne): split on the nw-se diagonal
        double surface = SmoothRules.surface(c, fx, fz);
        int shift = (int) Math.round((surface - 1) * 64);
        if (shift == 0) return base;
        SmoothPrototype.plantMoved.increment();
        return base.derive().with(SHIFT, shift).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        Integer shift = data.get(SHIFT);
        List<BakedQuad> quads = super.getQuads(state, side, random, data, renderType);
        if (shift == null || quads.isEmpty()) return quads;
        int layer = renderType == null ? 0 : RenderType.chunkBufferLayers().indexOf(renderType) + 1;
        long key = (long) shift << 16 | (long) layer << 4 | (side == null ? 6 : side.ordinal());
        return cache.computeIfAbsent(key, k -> moved(quads, shift / 64F));
    }

    private static List<BakedQuad> moved(List<BakedQuad> in, float dy) {
        List<BakedQuad> out = new ArrayList<>(in.size());
        for (BakedQuad q : in) {
            int[] v = q.getVertices().clone();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) v[i * stride + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[i * stride + 1]) + dy);
            out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
        }
        return List.copyOf(out);
    }
}
