package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.terrainsmooth.client.TopTexture;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The chunk model of graded_turf (docs/worldgen/highway_r4_sample_v1.md, E): a sloped top (two triangles of one quad, the
 * NW-SE diagonal) with the grass top texture tinted by the biome (tint 0), sides down to the floor, all built from the
 * block state and cached per state. Flat lighting (no ambient occlusion: vanilla AO assumes faces on the cell planes).
 * C3.3a group B (docs/worldgen/federal_highways_c3.md 11.11): it looks like the grass block beside it: the top texture in
 * the orientation of the vanilla grass block's variant at the position (TopTexture), shaded by its tilt without a shader
 * pack as the natural slopes are, and the sides with the grass block's side layers (the fringe follows the top edge).
 * Model JSON: {@code {"loader": "apocalypse_firstlight:graded_turf", "textures": {"top": ..., "side": ..., "particle": ...}}}.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GradedTurfModel {
    public static final String LOADER = "graded_turf";

    private GradedTurfModel() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint != 0 ? -1
                : level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(), AflBlocks.GRADED_TURF.get());
    }

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            return new Geometry();
        }
    }

    private record Geometry() implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides, ResourceLocation location) {
            for (String m : new String[]{"top", "side", "particle"})
                if (!context.hasMaterial(m)) throw new JsonParseException(location + ": graded_turf needs the texture \"" + m + "\"");
            return new Baked(sprites.apply(context.getMaterial("top")), sprites.apply(context.getMaterial("side")),
                    sprites.apply(context.getMaterial("particle")), context.getTransforms());
        }
    }

    /**
     * Quads of one state and top orientation: the unculled ones (top, and sides that rise above the cell) and the culled
     * sides, each for the solid layer (top, the grass side, the bottom) and the cutout layer (the grass side's tinted
     * overlay), and the top alone. Chunk meshing (a render type given) draws the sloped top unculled. Callers without a
     * render type (Xaero's map colour asks for the UP face of the model, the breaking overlay) get the solid quads with the
     * top as the UP face, so a map shows tinted grass, not the particle (dirt) texture.
     */
    private record Quads(List<BakedQuad> free, Map<Direction, List<BakedQuad>> culled, List<BakedQuad> cutoutFree,
                         Map<Direction, List<BakedQuad>> cutoutCulled, List<BakedQuad> top, List<BakedQuad> freeWithoutTop) {}

    private static final class Baked implements IDynamicBakedModel {
        private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutoutMipped());
        private final TextureAtlasSprite top, side, particle;
        private final ItemTransforms transforms;
        /** per state: [orientation code (TopTexture, 8 bits) | shader pack << 8] */
        private final Map<BlockState, Quads[]> cache = new ConcurrentHashMap<>();

        Baked(TextureAtlasSprite top, TextureAtlasSprite side, TextureAtlasSprite particle, ItemTransforms transforms) {
            this.top = top;
            this.side = side;
            this.particle = particle;
            this.transforms = transforms;
        }

        /** The vanilla grass block's model: its top variants (orientation) and side layers are the turf's look. */
        private static BakedModel grass() {
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.GRASS_BLOCK.defaultBlockState());
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, @NotNull RandomSource random,
                                                 @NotNull ModelData data, @Nullable RenderType renderType) {
            if (state == null || !(state.getBlock() instanceof GradedTurfBlock)) return List.of();
            boolean cutout = renderType == RenderType.cutoutMipped();
            if (renderType != null && renderType != RenderType.solid() && !cutout) return List.of();
            // the orientation the vanilla grass block takes at this position (the renderer seeded random as for it)
            int orientation = TopTexture.orientation(grass().getQuads(Blocks.GRASS_BLOCK.defaultBlockState(), Direction.UP, random, ModelData.EMPTY, null));
            int key = orientation | (TopTexture.shaderPack() ? 256 : 0);
            Quads[] byKey = cache.computeIfAbsent(state, s -> new Quads[512]);
            Quads q = byKey[key];
            if (q == null) byKey[key] = q = build(state, orientation);
            if (renderType == null) {
                if (direction == null) return q.freeWithoutTop();
                if (direction == Direction.UP) return q.top();
            }
            if (cutout) return direction == null ? q.cutoutFree() : q.cutoutCulled().getOrDefault(direction, List.of());
            if (direction == null) return q.free();
            return q.culled().getOrDefault(direction, List.of());
        }

        private Quads build(BlockState state, int orientation) {
            float[] c = GradedTurfBlock.corners(state);   // nw, ne, sw, se
            float nw = c[0], ne = c[1], sw = c[2], se = c[3];
            List<BakedQuad> free = new ArrayList<>(), cutoutFree = new ArrayList<>();
            Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class), cutoutCulled = new EnumMap<>(Direction.class);
            // top: nw (0,0) -> sw (0,1) -> se (1,1) -> ne (1,0), normal up; the texture as the vanilla grass block's variant at
            // the position (TopTexture), shaded by its tilt like the natural slopes without a shader pack
            float[][] t = {{0, nw, 0}, {0, sw, 1}, {1, se, 1}, {1, ne, 0}};
            BakedQuad topQuad = quad(t, TopTexture.uv(orientation), top, 0, Direction.UP, TopTexture.topColor(t));
            free.add(topQuad);
            float tall = Math.max(Math.max(nw, ne), Math.max(sw, se));
            boolean above = tall > 1.0001F;
            // sides, seen from outside, counter-clockwise: bottom-left, bottom-right, top-right, top-left; the grass block's
            // side layers (dirt with the fringe, and the tinted overlay) mapped down from the top edge, so the fringe follows
            // the slope like the natural slopes' bands (SmoothSurfaceModel)
            List<BakedQuad> layers = grass().getQuads(Blocks.GRASS_BLOCK.defaultBlockState(), Direction.NORTH, RandomSource.create(42), ModelData.EMPTY, null);
            side(free, culled, cutoutFree, cutoutCulled, layers, above, Direction.NORTH, new float[]{1, 0, 0}, new float[]{0, 0, 0}, ne, nw);
            side(free, culled, cutoutFree, cutoutCulled, layers, above, Direction.SOUTH, new float[]{0, 0, 1}, new float[]{1, 0, 1}, sw, se);
            side(free, culled, cutoutFree, cutoutCulled, layers, above, Direction.WEST, new float[]{0, 0, 0}, new float[]{0, 0, 1}, nw, sw);
            side(free, culled, cutoutFree, cutoutCulled, layers, above, Direction.EAST, new float[]{1, 0, 1}, new float[]{1, 0, 0}, se, ne);
            // bottom (culled against the block below, which is normally solid ground)
            float[][] b = {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
            culled.computeIfAbsent(Direction.DOWN, k -> new ArrayList<>()).add(quad(b, new float[][]{{0, 1}, {0, 0}, {1, 0}, {1, 1}}, side, -1, Direction.DOWN, -1));
            return new Quads(List.copyOf(free), culled, List.copyOf(cutoutFree), cutoutCulled, List.of(topQuad), List.copyOf(free.subList(1, free.size())));
        }

        /**
         * One side face between floor points a (left) and b (right, seen from outside), up to heights ha (above a), hb (above
         * b): per grass side layer, the untinted one in the solid layer, the tinted overlay in the cutout layer (its
         * transparent pixels need the cutout). Without such layers (a resource pack's flat side), the model's own side.
         */
        private void side(List<BakedQuad> free, Map<Direction, List<BakedQuad>> culled, List<BakedQuad> cutoutFree,
                          Map<Direction, List<BakedQuad>> cutoutCulled, List<BakedQuad> layers, boolean above, Direction dir,
                          float[] a, float[] b, float ha, float hb) {
            if (ha < 1e-4F && hb < 1e-4F) return;
            float[][] p = {{a[0], 0, a[2]}, {b[0], 0, b[2]}, {b[0], hb, b[2]}, {a[0], ha, a[2]}};
            float[][] uv = {{0, Math.min(1, ha)}, {1, Math.min(1, hb)}, {1, 0}, {0, 0}};
            if (layers.isEmpty()) {
                BakedQuad q = quad(p, uv, side, -1, dir, -1);
                if (above) free.add(q); else culled.computeIfAbsent(dir, k -> new ArrayList<>()).add(q);
                return;
            }
            for (BakedQuad o : layers) {
                BakedQuad q = quad(p, uv, o.getSprite(), o.getTintIndex(), dir, -1);
                boolean overlay = o.isTinted();
                if (above) (overlay ? cutoutFree : free).add(q);
                else (overlay ? cutoutCulled : culled).computeIfAbsent(dir, k -> new ArrayList<>()).add(q);
            }
        }

        private static BakedQuad quad(float[][] p, float[][] uv, TextureAtlasSprite sprite, int tint, Direction dir, int color) {
            // normal from the two triangles (0,1,2) and (0,2,3)
            float[] n = new float[3];
            for (int[] tri : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                float ux = p[tri[1]][0] - p[tri[0]][0], uy = p[tri[1]][1] - p[tri[0]][1], uz = p[tri[1]][2] - p[tri[0]][2];
                float vx = p[tri[2]][0] - p[tri[0]][0], vy = p[tri[2]][1] - p[tri[0]][1], vz = p[tri[2]][2] - p[tri[0]][2];
                n[0] += uy * vz - uz * vy;
                n[1] += uz * vx - ux * vz;
                n[2] += ux * vy - uy * vx;
            }
            float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (l < 1e-9F) { n[0] = dir.getStepX(); n[1] = dir.getStepY(); n[2] = dir.getStepZ(); l = 1; }
            int packed = (((byte) (n[0] / l * 127)) & 0xFF) | ((((byte) (n[1] / l * 127)) & 0xFF) << 8) | ((((byte) (n[2] / l * 127)) & 0xFF) << 16);
            int[] data = new int[32];
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                data[o] = Float.floatToRawIntBits(p[i][0]);
                data[o + 1] = Float.floatToRawIntBits(p[i][1]);
                data[o + 2] = Float.floatToRawIntBits(p[i][2]);
                data[o + 3] = color;
                data[o + 4] = Float.floatToRawIntBits(sprite.getU(uv[i][0] * 16));
                data[o + 5] = Float.floatToRawIntBits(sprite.getV(uv[i][1] * 16));
                data[o + 6] = 0;
                data[o + 7] = packed;
            }
            return new BakedQuad(data, tint, dir, sprite, true, false);
        }

        @Override
        public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
            return LAYERS;
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
        @Override public @NotNull ItemTransforms getTransforms() { return transforms; }
    }
}
