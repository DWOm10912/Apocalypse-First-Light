package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesh Trees P2.2 (docs/rendering/mesh_trees_p2_felling.md, V): the log blocks' model, chunk and item, the rounded-square
 * prism of {@link LogShape} per species and axis.
 * <ul>
 * <li>Flat sides and end caps in their face's list: culled against neighbours like cube faces, lit from that neighbour.</li>
 * <li>Corner strips in the unculled list. They lie off the block's faces, where the renderer lights a quad from the block's
 * own cell, and a fully opaque block's own cell holds no light (0: black corners). So the model data carries, per corner,
 * the brightest light of the cells the corner faces (its two side neighbours and their diagonal), and the corner's quads
 * carry it baked: Forge's vanilla pipeline and Embeddium both keep the brighter of baked and computed light per channel
 * (IForgeVertexConsumer#applyBakedLighting, ModelQuadUtil#mergeBakedLight). Light changes rebuild the section.</li>
 * <li>Flat light, no ambient occlusion (smooth light for a quad off the faces would read the dark inside of the block, and
 * mixing it with lit corners leaves dark seams). As the tree cells: the direction shading baked by the normal (vanilla's
 * values on the flat faces, smooth round the corners) without a shader pack; with one, and for items, plain colour and
 * the pack or the item lighting shade by the normal.</li>
 * <li>Item: the upright log, the JSON model's transforms (vanilla's block item placement).</li>
 * </ul>
 */
final class TreeLogModel implements IDynamicBakedModel {
    /** Per corner (8 bits each, corner 0 lowest): sky light << 4 | block light of the brightest cell it faces. */
    static final ModelProperty<Integer> LIGHT = new ModelProperty<>();
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    /** Faces by direction ordinal, per (species, shade, axis). */
    private static final Map<Integer, List<BakedQuad>[]> FACES = new ConcurrentHashMap<>();
    /** One corner's strips at one light, per (species, shade, axis, corner, light). */
    private static final Map<Integer, List<BakedQuad>> CORNERS = new ConcurrentHashMap<>();

    private final Species species;
    private final BakedModel base;

    TreeLogModel(Species species, BakedModel base) {
        this.species = species;
        this.base = base;
    }

    static void clearCache() {
        FACES.clear();
        CORNERS.clear();
    }

    private static ResourceLocation bark(Species s) { return new ResourceLocation("apocalypse_firstlight", "block/tree/" + s.id + "_bark"); }

    private static ResourceLocation end(Species s) { return new ResourceLocation("apocalypse_firstlight", "block/tree/" + s.id + "_log_end"); }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    // ------------------------------------------------------------------------------------------- model data

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        if (!state.hasProperty(RotatedPillarBlock.AXIS)) return data;
        int axis = state.getValue(RotatedPillarBlock.AXIS).ordinal(), packed = 0;
        var at = new BlockPos.MutableBlockPos();
        for (int k = 0; k < 4; k++) {
            int[] sides = LogShape.cornerSides(k, axis);
            Direction a = Direction.from3DDataValue(sides[0]), b = Direction.from3DDataValue(sides[1]);
            int sky = 0, block = 0;
            for (int n = 0; n < 3; n++) {
                at.set(pos);
                if (n != 1) at.move(a);
                if (n != 0) at.move(b);
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            }
            packed |= (sky << 4 | block) << (k * 8);
        }
        return ModelData.builder().with(LIGHT, packed).build();
    }

    // ---------------------------------------------------------------------------------------------- quads

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return SOLID;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (renderType != null && renderType != RenderType.solid()) return List.of();
        // an item (no state) stands upright and leaves the shading to the item lighting
        int axis = state != null && state.hasProperty(RotatedPillarBlock.AXIS) ? state.getValue(RotatedPillarBlock.AXIS).ordinal() : 1;
        int shade = state == null || AflShaderCompat.shaderPackInUse() ? 0 : 1;
        int key = (species.ordinal() * 2 + shade) * 3 + axis;
        if (side != null) return faces(key)[side.ordinal()];
        Integer light = data.get(LIGHT);
        int l = light == null ? 0 : light;
        var out = new ArrayList<BakedQuad>(4 * LogShape.ARC);
        for (int k = 0; k < 4; k++) out.addAll(corner(key, k, (l >>> (k * 8)) & 0xFF));
        return out;
    }

    private List<BakedQuad>[] faces(int key) {
        return FACES.computeIfAbsent(key, k -> {
            @SuppressWarnings("unchecked") List<BakedQuad>[] lists = new List[6];
            var build = new ArrayList<List<BakedQuad>>(6);
            for (int d = 0; d < 6; d++) build.add(new ArrayList<>());
            TextureAtlasSprite[] sprites = {sprite(bark(species)), sprite(end(species))};
            for (var part : parts(k % 3))
                if (part.cull() != LogShape.NONE) TreeCellModel.bake(part.quad(), 0, 0, 0, sprites[part.quad().sprite], shadeOf(k), build.get(part.cull()));
            for (int d = 0; d < 6; d++) lists[d] = List.copyOf(build.get(d));
            return lists;
        });
    }

    private List<BakedQuad> corner(int key, int corner, int light) {
        return CORNERS.computeIfAbsent((key * 4 + corner) << 8 | light, k -> {
            var list = new ArrayList<BakedQuad>(LogShape.ARC);
            TextureAtlasSprite sprite = sprite(bark(species));
            int packed = (light & 0xF) << 4 | (light >>> 4) << 20;           // LightTexture.pack(block, sky)
            for (var part : parts(key % 3))
                if (part.corner() == corner) TreeCellModel.bake(part.quad(), 0, 0, 0, sprite, shadeOf(key), packed, list);
            return List.copyOf(list);
        });
    }

    private static int shadeOf(int key) { return key / 3 % 2; }

    private List<LogShape.Part> parts(int axis) {
        return LogShape.vanillaOrder(LogShape.axis(LogShape.build(species), axis));
    }

    // -------------------------------------------------------------------------------------------- the rest

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return sprite(bark(species)); }
    @SuppressWarnings("deprecation")
    @Override public @NotNull ItemTransforms getTransforms() { return base.getTransforms(); }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
