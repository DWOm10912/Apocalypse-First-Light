package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
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
 * Chunk model of a small plant (grass, ferns, flowers, saplings, both halves of tall plants; Terrain Smooth V1): standing
 * on a sloped natural surface top, it moves up or down to the sloped surface under its own (randomly offset) position,
 * so it neither floats over a lowered slope nor sinks into a raised one; on graded_turf (the road's earthworks, whose
 * top is drawn on the same diagonal, SmoothRules#swNe) the same with the turf's corners. Elsewhere exactly the vanilla model.
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
        int support = pos.getY() - 1;
        if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) support--;
        BlockPos below = new BlockPos(pos.getX(), support, pos.getZ());
        BlockState ground = level.getBlockState(below);
        float[] c;   // above the support's floor
        if (ground.getBlock() instanceof GradedTurfBlock) c = GradedTurfBlock.corners(ground);   // the road's turf (C3.3a group B)
        else {
            c = TerrainSmoothClient.supportTop(level, below);   // R2b: the smooth top
            if (c == null) return base;
        }
        Vec3 off = state.getOffset(level, pos);
        double fx = Math.max(0, Math.min(1, 0.5 + off.x)), fz = Math.max(0, Math.min(1, 0.5 + off.z));
        // the drawn top at the plant (SmoothRules#surface: its diagonal or its fan)
        double surface = SmoothRules.surface(c, fx, fz);
        int shift = (int) Math.round((surface - 1) * 64);
        if (shift == 0) return base;
        TerrainSmoothClient.plantsMoved.increment();
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
        if (cache.size() > 32768) cache.clear();
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
