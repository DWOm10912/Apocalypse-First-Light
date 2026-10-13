package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cabin Exterior V1 (docs/models/cabin_exterior_v1.md): the model of every cabin mesh block ({@link CabinPieces}).
 * <ul>
 * <li>Model JSON: {@code {"loader": "apocalypse_firstlight:cabin", "textures": {"particle": ...}, "item": [[piece, turn,
 * dx, dy, dz], ...]}}: the pieces are the block's choice per state and neighbour flags; "item" is the inventory view.</li>
 * <li>Pieces (cabin_mesh/&lt;name&gt;.json): {@code {"textures": [ids], "quads": [[tex, x, y, z, u, v, nx, ny, nz] x 4,
 * ...]}}, drawn for the north side, turned (x, z) -> (1 - z, x) per clockwise quarter. Loaded at every model bake.</li>
 * <li>A quad that lies on a block face and looks out of it goes in that face's list: culled against the neighbour like a
 * cube face and lit from that neighbour. Every other quad is unculled: the renderer lights it from the block's own cell,
 * which holds no light in an opaque block (a log wall, board and batten) and little under a roof. So it carries a baked
 * light (kept per channel as the brighter of baked and computed: Forge IForgeVertexConsumer#applyBakedLighting, Embeddium
 * ModelQuadUtil#mergeBakedLight) from the cell it faces: floor(centroid + normal * 0.02), the cell along its main normal
 * when that is the block itself; if that cell is opaque, the one along the main normal, else the block's own. Light
 * changes rebuild the section (TreeLogModel does the same for its corners).</li>
 * <li>Flat light, no ambient occlusion; without a shader pack the direction shading is baked by the normal (vanilla's
 * values), with one (and for items) plain colour and the pack or the item lighting shade by the normal.</li>
 * <li>Glass quads draw in the translucent layer, all else solid; an item with glass draws translucent.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CabinMeshModel {
    public static final String LOADER = "cabin";
    private static final String NS = ApocalypseFirstLight.MOD_ID;
    private static final float EPS = 1.0E-4F;
    /** (block state id << 32 | the block's flags) of the drawn look. */
    private static final ModelProperty<Long> KEY = new ModelProperty<>();
    /** Per unculled light group of that look: sky << 4 | block light. */
    private static final ModelProperty<int[]> LIGHTS = new ModelProperty<>();
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ChunkRenderTypeSet SOLID_GLASS = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.translucent());
    /** The pieces of the bake under way (dropped when it completes; each baked model keeps its own map). */
    private static Map<String, Piece> loading;

    private CabinMeshModel() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void baked(ModelEvent.BakingCompleted event) {
        synchronized (CabinMeshModel.class) {
            loading = null;
        }
    }

    // ------------------------------------------------------------------------------------------------- pieces

    /** One mesh piece: textures and per quad the texture index and 4 x (x, y, z, u, v, nx, ny, nz). */
    private record Piece(ResourceLocation[] textures, int[] tex, float[][] quads) {
        static Piece parse(JsonObject json) {
            JsonArray ts = GsonHelper.getAsJsonArray(json, "textures"), qs = GsonHelper.getAsJsonArray(json, "quads");
            var textures = new ResourceLocation[ts.size()];
            for (int i = 0; i < textures.length; i++) textures[i] = new ResourceLocation(ts.get(i).getAsString());
            int[] tex = new int[qs.size()];
            float[][] quads = new float[qs.size()][32];
            for (int i = 0; i < tex.length; i++) {
                JsonArray q = qs.get(i).getAsJsonArray();
                if (q.size() != 33) throw new JsonParseException("cabin piece quad of " + q.size() + " numbers");
                tex[i] = q.get(0).getAsInt();
                for (int k = 0; k < 32; k++) quads[i][k] = q.get(k + 1).getAsFloat();
            }
            return new Piece(textures, tex, quads);
        }
    }

    private static synchronized Map<String, Piece> pieces() {
        if (loading == null) {
            var map = new HashMap<String, Piece>();
            Minecraft.getInstance().getResourceManager().listResources("cabin_mesh", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
                if (!id.getNamespace().equals(NS)) return;
                String name = id.getPath().substring("cabin_mesh/".length(), id.getPath().length() - ".json".length());
                try (Reader reader = resource.openAsReader()) {
                    map.put(name, Piece.parse(JsonParser.parseReader(reader).getAsJsonObject()));
                } catch (Exception e) {
                    ApocalypseFirstLight.LOGGER.error("Cabin mesh piece {} did not load", id, e);
                }
            });
            loading = Map.copyOf(map);
        }
        return loading;
    }

    // --------------------------------------------------------------------------------------------- the loader

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            var item = new ArrayList<Placed>();
            if (json.has("item")) for (JsonElement e : GsonHelper.getAsJsonArray(json, "item")) {
                JsonArray a = e.getAsJsonArray();
                item.add(new Placed(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsInt() : 0, a.size() > 2 ? a.get(2).getAsFloat() : 0,
                        a.size() > 3 ? a.get(3).getAsFloat() : 0, a.size() > 4 ? a.get(4).getAsFloat() : 0));
            }
            return new Geometry(List.copyOf(item));
        }
    }

    private record Placed(String piece, int turn, float dx, float dy, float dz) {}

    private record Geometry(List<Placed> item) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides, ResourceLocation location) {
            Map<String, Piece> pieces = pieces();
            var atlas = new HashMap<ResourceLocation, TextureAtlasSprite>();
            for (Piece p : pieces.values()) for (ResourceLocation t : p.textures())
                atlas.computeIfAbsent(t, id -> sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS, id)));
            for (Placed p : item) if (!pieces.containsKey(p.piece())) ApocalypseFirstLight.LOGGER.warn("{}: item piece {} missing", location, p.piece());
            return new Baked(pieces, Map.copyOf(atlas), item, sprites.apply(context.getMaterial("particle")), context.getTransforms());
        }
    }

    // ------------------------------------------------------------------------------------------- assemblies

    /** One quad turned into place: 4 x (x, y, z), 4 x (u, v) in the sprite, 4 x normal; glass draws translucent. */
    private record Quad(float[] p, float[] uv, float[] n, TextureAtlasSprite sprite, boolean glass) {}

    /**
     * The quads of one look: per face the culled ones, the unculled ones grouped by light cell (keys: offset index
     * (ox + 1) * 9 + (oy + 1) * 3 + oz + 1, times 6, plus the main normal's direction).
     */
    private static final class Assembly {
        final Quad[][] faces = new Quad[6][];
        final Quad[][] groups;
        final int[] keys;
        final boolean glass;
        final Map<Integer, List<BakedQuad>> baked = new ConcurrentHashMap<>();

        Assembly(List<Quad> quads, boolean item) {
            var faceLists = new ArrayList<List<Quad>>(6);
            for (int d = 0; d < 6; d++) faceLists.add(new ArrayList<>());
            var grouped = new LinkedHashMap<Integer, List<Quad>>();
            boolean anyGlass = false;
            for (Quad q : quads) {
                anyGlass |= q.glass();
                Direction main = mainNormal(q.n());
                if (!item && onFace(q.p(), main)) {
                    faceLists.get(main.ordinal()).add(q);
                    continue;
                }
                int key = item ? 0 : lightKey(q, main);
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
            }
            for (int d = 0; d < 6; d++) faces[d] = faceLists.get(d).toArray(Quad[]::new);
            groups = new Quad[grouped.size()][];
            keys = new int[grouped.size()];
            int g = 0;
            for (var e : grouped.entrySet()) {
                keys[g] = e.getKey();
                groups[g++] = e.getValue().toArray(Quad[]::new);
            }
            glass = anyGlass;
        }

        /** layer: 0 solid, 1 translucent, 2 both; which: a face (0..5) or 6 + a group; light: sky << 4 | block. */
        List<BakedQuad> baked(int shade, int layer, int which, int light) {
            return baked.computeIfAbsent(((which * 2 + shade) * 3 + layer) << 8 | light, k -> {
                Quad[] qs = which < 6 ? faces[which] : groups[which - 6];
                int packed = which < 6 ? 0 : (light & 0xF) << 4 | (light >>> 4) << 20;    // LightTexture.pack(block, sky)
                var out = new ArrayList<BakedQuad>(qs.length);
                for (Quad q : qs) if (layer == 2 || q.glass() == (layer == 1)) out.add(bake(q, shade, packed));
                return List.copyOf(out);
            });
        }
    }

    private static Direction mainNormal(float[] n) {
        float x = 0, y = 0, z = 0;
        for (int i = 0; i < 4; i++) { x += n[i * 3]; y += n[i * 3 + 1]; z += n[i * 3 + 2]; }
        return Direction.getNearest(x, y, z);
    }

    /** All four corners on the block face the quad looks out of. */
    private static boolean onFace(float[] p, Direction d) {
        int axis = d.getAxis().ordinal();
        float plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        for (int i = 0; i < 4; i++) if (Math.abs(p[i * 3 + axis] - plane) > EPS) return false;
        return true;
    }

    private static int lightKey(Quad q, Direction main) {
        float cx = 0, cy = 0, cz = 0, nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < 4; i++) {
            cx += q.p()[i * 3] / 4; cy += q.p()[i * 3 + 1] / 4; cz += q.p()[i * 3 + 2] / 4;
            nx += q.n()[i * 3] / 4; ny += q.n()[i * 3 + 1] / 4; nz += q.n()[i * 3 + 2] / 4;
        }
        int ox = clamp((int) Math.floor(cx + nx * 0.02F)), oy = clamp((int) Math.floor(cy + ny * 0.02F)), oz = clamp((int) Math.floor(cz + nz * 0.02F));
        if (ox == 0 && oy == 0 && oz == 0) { ox = main.getStepX(); oy = main.getStepY(); oz = main.getStepZ(); }
        return ((ox + 1) * 9 + (oy + 1) * 3 + oz + 1) * 6 + main.ordinal();
    }

    private static int clamp(int v) { return Math.max(-1, Math.min(1, v)); }

    /** The light a group's quads take: the cell they face, else the cell along their main normal, else the block's own. */
    private static int light(BlockAndTintGetter level, BlockPos pos, int key) {
        int o = key / 6;
        var at = new BlockPos.MutableBlockPos(pos.getX() + o / 9 - 1, pos.getY() + o / 3 % 3 - 1, pos.getZ() + o % 3 - 1);
        if (level.getBlockState(at).isSolidRender(level, at)) {
            at.setWithOffset(pos, Direction.from3DDataValue(key % 6));
            if (level.getBlockState(at).isSolidRender(level, at)) at.set(pos);
        }
        return level.getBrightness(LightLayer.SKY, at) << 4 | level.getBrightness(LightLayer.BLOCK, at);
    }

    private static BakedQuad bake(Quad q, int shade, int lightmap) {
        TextureAtlasSprite s = q.sprite();
        float u0 = s.getU0(), du = s.getU1() - u0, v0 = s.getV0(), dv = s.getV1() - v0, fx = 0, fy = 0, fz = 0;
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            float nx = q.n()[i * 3], ny = q.n()[i * 3 + 1], nz = q.n()[i * 3 + 2];
            fx += nx; fy += ny; fz += nz;
            data[o] = Float.floatToRawIntBits(q.p()[i * 3]);
            data[o + 1] = Float.floatToRawIntBits(q.p()[i * 3 + 1]);
            data[o + 2] = Float.floatToRawIntBits(q.p()[i * 3 + 2]);
            float light = shade == 1 ? nx * nx * 0.6F + nz * nz * 0.8F + ny * ny * (ny >= 0 ? 1.0F : 0.5F) : 1.0F;
            int g = Math.max(0, Math.min(255, Math.round(light * 255)));
            data[o + 3] = 0xFF000000 | g << 16 | g << 8 | g;                       // ABGR
            data[o + 4] = Float.floatToRawIntBits(u0 + du * q.uv()[i * 2]);
            data[o + 5] = Float.floatToRawIntBits(v0 + dv * q.uv()[i * 2 + 1]);
            data[o + 6] = lightmap;
            data[o + 7] = (((byte) (nx * 127)) & 0xFF) | (((byte) (ny * 127)) & 0xFF) << 8 | (((byte) (nz * 127)) & 0xFF) << 16;
        }
        return new BakedQuad(data, -1, Direction.getNearest(fx, fy, fz), s, shade == 0, false);
    }

    // ------------------------------------------------------------------------------------------ the baked model

    private static final class Baked implements IDynamicBakedModel {
        private final Map<String, Piece> pieces;
        private final Map<ResourceLocation, TextureAtlasSprite> atlas;
        private final List<Placed> item;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final Map<Long, Assembly> assemblies = new ConcurrentHashMap<>();
        private final Set<String> warned = ConcurrentHashMap.newKeySet();
        private volatile Assembly itemView;

        Baked(Map<String, Piece> pieces, Map<ResourceLocation, TextureAtlasSprite> atlas, List<Placed> item, TextureAtlasSprite particle, ItemTransforms transforms) {
            this.pieces = pieces;
            this.atlas = atlas;
            this.item = item;
            this.particle = particle;
            this.transforms = transforms;
        }

        private void add(List<Quad> out, String name, int turn, float dx, float dy, float dz) {
            Piece piece = pieces.get(name);
            if (piece == null) {
                if (warned.add(name)) ApocalypseFirstLight.LOGGER.warn("Cabin mesh piece {} missing", name);
                return;
            }
            int r = Math.floorMod(turn, 4);
            for (int i = 0; i < piece.tex().length; i++) {
                float[] src = piece.quads()[i];
                float[] p = new float[12], uv = new float[8], n = new float[12];
                for (int v = 0; v < 4; v++) {
                    int o = v * 8;
                    float x = src[o], z = src[o + 2], nx = src[o + 5], nz = src[o + 7];
                    for (int k = 0; k < r; k++) {
                        float t = x; x = 1 - z; z = t;
                        t = nx; nx = -nz; nz = t;
                    }
                    p[v * 3] = x + dx; p[v * 3 + 1] = src[o + 1] + dy; p[v * 3 + 2] = z + dz;
                    uv[v * 2] = src[o + 3]; uv[v * 2 + 1] = src[o + 4];
                    n[v * 3] = nx; n[v * 3 + 1] = src[o + 6]; n[v * 3 + 2] = nz;
                }
                ResourceLocation tex = piece.textures()[piece.tex()[i]];
                TextureAtlasSprite sprite = atlas.get(tex);
                if (sprite == null) sprite = particle;
                out.add(new Quad(p, uv, n, sprite, tex.getPath().contains("glass")));
            }
        }

        private Assembly assembly(long key) {
            return assemblies.computeIfAbsent(key, k -> {
                BlockState state = Block.stateById((int) (k >>> 32));
                var quads = new ArrayList<Quad>();
                if (state.getBlock() instanceof CabinPieces block)
                    block.cabinPieces(state, (int) (long) k, (name, turn) -> add(quads, name, turn, 0, 0, 0));
                return new Assembly(quads, false);
            });
        }

        private Assembly itemView() {
            Assembly a = itemView;
            if (a == null) {
                var quads = new ArrayList<Quad>();
                for (Placed p : item) add(quads, p.piece(), p.turn(), p.dx(), p.dy(), p.dz());
                itemView = a = new Assembly(quads, true);
            }
            return a;
        }

        private static long key(BlockState state, int flags) {
            return (long) Block.getId(state) << 32 | (flags & 0xFFFFFFFFL);
        }

        @Override
        public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
            if (!(state.getBlock() instanceof CabinPieces block)) return data;
            long key = key(state, block.cabinFlags(state, level, pos));
            Assembly a = assembly(key);
            int[] lights = new int[a.keys.length];
            for (int g = 0; g < lights.length; g++) lights[g] = light(level, pos, a.keys[g]);
            return ModelData.builder().with(KEY, key).with(LIGHTS, lights).build();
        }

        private Assembly of(@Nullable BlockState state, ModelData data) {
            if (state == null) return itemView();
            Long key = data.get(KEY);
            return assembly(key != null && (int) (key >>> 32) == Block.getId(state) ? key : key(state, 0));
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                                 @NotNull ModelData data, @Nullable RenderType renderType) {
            int layer = renderType == null ? 2 : renderType == RenderType.solid() ? 0 : renderType == RenderType.translucent() ? 1 : -1;
            if (layer < 0) return List.of();
            Assembly a = of(state, data);
            int shade = state == null || AflShaderCompat.shaderPackInUse() ? 0 : 1;
            if (side != null) return a.baked(shade, layer, side.ordinal(), 0);
            int[] lights = state == null ? null : data.get(LIGHTS);
            if (lights != null && lights.length != a.groups.length) lights = null;
            if (a.groups.length == 1) return a.baked(shade, layer, 6, lights == null ? 0 : lights[0]);
            var out = new ArrayList<BakedQuad>();
            for (int g = 0; g < a.groups.length; g++) out.addAll(a.baked(shade, layer, 6 + g, lights == null ? 0 : lights[g]));
            return out;
        }

        @Override
        public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
            return of(state, data).glass ? SOLID_GLASS : SOLID;
        }

        @Override
        public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
            return List.of(RenderTypeHelper.getEntityRenderType(itemView().glass ? RenderType.translucent() : RenderType.solid(), false));
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) { return particle; }
        @SuppressWarnings("deprecation")
        @Override public @NotNull ItemTransforms getTransforms() { return transforms; }
        @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
