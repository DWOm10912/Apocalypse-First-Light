package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.FluidPipeBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import com.antaurora.apofirstlight.block.FluidPipePieces;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fluid Pipe V2 block model: assembles one pipe block from the baked OBJ pieces of tools/build-fluid-pipe-v2.mjs, steel
 * in the solid layer, glass ({@code <piece>_glass}) in the translucent layer. Which pieces: block/FluidPipePieces (shared
 * with the pipe's hit mesh). The key holds, per direction, NONE / PIPE /
 * PORT (2 bits each), then the band side (3 bits: 6 = a band, 7 = nothing, else a wall clamp toward that side).
 * Selection: none -> the fitting with glass on all faces; one -> an end (blind flange); two opposite -> two halves and,
 * every third block along the run (by world coordinate), a band, or a clamp toward a solid face beside it (floor first,
 * then walls, then ceiling); else the fitting, an arm per link and glass on its closed faces.
 */
public final class FluidPipeBakedModel implements IDynamicBakedModel {
    static final ModelProperty<Integer> LINKS = new ModelProperty<>();
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.translucent());

    private final Map<String, BakedModel> pieces;
    private final BakedModel particle;
    private final Map<Integer, List<BakedQuad>> solid = new ConcurrentHashMap<>();
    private final Map<Integer, List<BakedQuad>> glass = new ConcurrentHashMap<>();

    FluidPipeBakedModel(Map<String, BakedModel> pieces) {
        this.pieces = pieces;
        this.particle = pieces.get("box");
    }

    static int key(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return FluidPipePieces.key(level, pos, state);
    }

    private static int keyFromState(BlockState state) {
        return FluidPipePieces.keyFromState(state);
    }

    static List<String> pieceNames(int key) {
        return FluidPipePieces.pieceNames(key);
    }

    private List<BakedQuad> assemble(int key, boolean translucent) {
        List<BakedQuad> quads = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (String name : pieceNames(key)) {
            BakedModel piece = pieces.get(translucent ? name + "_glass" : name);
            if (piece != null) quads.addAll(piece.getQuads(null, null, random, ModelData.EMPTY, null));
        }
        return List.copyOf(quads);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (side != null || state == null) return List.of();
        Integer key = data.get(LINKS);
        int resolved = key != null ? key : keyFromState(state);
        if (renderType == RenderType.translucent()) return glass.computeIfAbsent(resolved, k -> assemble(k, true));
        if (renderType == null || renderType == RenderType.solid()) return solid.computeIfAbsent(resolved, k -> assemble(k, false));
        return List.of();
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                           @NotNull ModelData modelData) {
        return ModelData.builder().with(LINKS, key(level, pos, state)).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
        return LAYERS;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return particle.getParticleIcon();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
