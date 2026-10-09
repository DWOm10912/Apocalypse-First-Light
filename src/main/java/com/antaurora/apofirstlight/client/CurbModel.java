package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CurbBlock;
import com.antaurora.apofirstlight.block.CurbGeometry;
import com.antaurora.apofirstlight.item.CurbBlockItem;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Curbs V1 (docs/models/curbs_v1.md): the block model of curb_sidewalk / curb_grass. The ground ("base": the jointed sidewalk
 * model or vanilla grass) plus the curb pieces of tools/build-curbs-v1.mjs (forge:obj, drawn for the north edge, baked in the
 * four turns), picked by {@link CurbGeometry#compute} from Forge's getModelData(level, pos, ...) at meshing. The pieces are
 * lit flat (their quads re-made without ambient occlusion): every piece face lies inside the cell or above it, so it takes
 * the light of this (light-passing) cell or the one above, never the opaque road beside it. Tint 0 is the ground (sidewalk
 * panel tone, biome grass colour), tint 1 the paint.
 * <p>
 * Model JSON: {@code {"loader": "apocalypse_firstlight:curb", "base": "<model>", "grass": false, "pieces": "<model prefix>",
 * "textures": {"particle": ...}}}.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CurbModel {
    public static final String LOADER = "curb";
    /** In step with tools/build-curbs-v1.mjs pieces(). */
    static final String[] PIECES = {"strip_full", "strip_lowered", "strip_low_ccw", "strip_low_cw", "strip_zero_ccw", "strip_zero_cw",
            "mid", "square_ccw", "square_cw", "outer", "post", "cap_ccw_full", "cap_ccw_low", "cap_cw_full", "cap_cw_low", "warning"};
    private static final Map<String, Integer> INDEX = new java.util.HashMap<>();
    static { for (int i = 0; i < PIECES.length; i++) INDEX.put(PIECES[i], i); }
    private static final ModelProperty<Integer> KEY = new ModelProperty<>();

    private CurbModel() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (!(state.getBlock() instanceof CurbBlock curb)) return -1;
            if (tint == 1) return state.getValue(CurbBlock.PAINT).tint();
            if (tint != 0) return -1;
            if (curb.back() == CurbBlock.Back.GRASS) return level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor();
            return pos != null ? GroundJointModel.panelTone(2, pos) : -1;
        }, AflBlocks.CURB_SIDEWALK.get(), AflBlocks.CURB_GRASS.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 1 ? CurbBlockItem.paint(stack).tint()
                : tint == 0 && stack.is(AflItems.CURB_GRASS.get()) ? GrassColor.getDefaultColor() : -1, AflItems.CURB_SIDEWALK.get(), AflItems.CURB_GRASS.get());
    }

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            return new Geometry(new ResourceLocation(GsonHelper.getAsString(json, "base")), GsonHelper.getAsString(json, "pieces"),
                    GsonHelper.getAsBoolean(json, "grass", false));
        }
    }

    private record Geometry(ResourceLocation base, String pieces, boolean grass) implements IUnbakedGeometry<Geometry> {
        ResourceLocation piece(String name) {
            return new ResourceLocation(pieces + name);
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> getter, IGeometryBakingContext context) {
            getter.apply(base).resolveParents(getter);
            for (String name : PIECES) getter.apply(piece(name)).resolveParents(getter);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides, ResourceLocation location) {
            BakedModel ground = baker.bake(base, BlockModelRotation.X0_Y0, sprites);
            if (ground == null) throw new JsonParseException(location + ": curb base model " + base + " did not bake");
            RandomSource random = RandomSource.create(42);
            @SuppressWarnings("unchecked") List<BakedQuad>[][] quads = new List[PIECES.length][4];
            for (int i = 0; i < PIECES.length; i++) for (int r = 0; r < 4; r++) {
                BakedModel model = baker.bake(piece(PIECES[i]), BlockModelRotation.by(0, r * 90), sprites);
                if (model == null) throw new JsonParseException(location + ": curb piece " + PIECES[i] + " did not bake");
                List<BakedQuad> list = new ArrayList<>(model.getQuads(null, null, random, ModelData.EMPTY, null));
                for (Direction d : Direction.values()) list.addAll(model.getQuads(null, d, random, ModelData.EMPTY, null));
                quads[i][r] = list.stream().map(q -> new BakedQuad(q.getVertices(), q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), false)).toList();
            }
            return new Baked(ground, quads, grass, sprites.apply(context.getMaterial("particle")), context.getTransforms(), context.useBlockLight());
        }
    }

    private static final class Baked implements IDynamicBakedModel {
        private final BakedModel ground;
        private final List<BakedQuad>[][] pieces;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final boolean blockLight;
        private final ChunkRenderTypeSet layers;
        private final RenderType layer;
        private final Map<Integer, List<BakedQuad>> assembled = new ConcurrentHashMap<>();

        Baked(BakedModel ground, List<BakedQuad>[][] pieces, boolean grass, TextureAtlasSprite particle, ItemTransforms transforms, boolean blockLight) {
            this.ground = ground;
            this.pieces = pieces;
            this.particle = particle;
            this.transforms = transforms;
            this.blockLight = blockLight;
            this.layer = grass ? RenderType.cutoutMipped() : RenderType.solid();   // vanilla grass sides carry a cut-out overlay
            this.layers = ChunkRenderTypeSet.of(layer);
        }

        @Override
        public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
            return ground.getModelData(level, pos, state, data).derive().with(KEY, CurbGeometry.compute(level, pos, state)).build();
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                                 @NotNull ModelData data, @Nullable RenderType renderType) {
            List<BakedQuad> base = ground.getQuads(state, side, random, data, renderType);
            if (side != null) return base;
            Integer key = data.get(KEY);
            List<BakedQuad> curb = assembled.computeIfAbsent(key == null ? CurbGeometry.ITEM_KEY : key, this::assemble);
            if (curb.isEmpty()) return base;
            if (base.isEmpty()) return curb;
            List<BakedQuad> all = new ArrayList<>(base.size() + curb.size());
            all.addAll(base);
            all.addAll(curb);
            return all;
        }

        /** The baked quads of one key's pieces (CurbGeometry#pieces, shared with the hit mesh). */
        private List<BakedQuad> assemble(int key) {
            List<BakedQuad> out = new ArrayList<>();
            for (CurbGeometry.Piece piece : CurbGeometry.pieces(key)) out.addAll(pieces[INDEX.get(piece.name())][piece.turn()]);
            return List.copyOf(out);
        }

        @Override
        public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
            return layers;
        }

        @Override
        public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
            return List.of(RenderTypeHelper.getEntityRenderType(layer, false));
        }

        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return blockLight; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
        @Override public @NotNull ItemTransforms getTransforms() { return transforms; }
    }
}
