package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.JointedPavementBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
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
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Ground Materials V1 (docs/models/ground_materials_v1.md): the block model of the jointed concrete (concrete_sidewalk,
 * concrete_pavement). Joint lines lie on world multiples of {@code spacing} blocks, half on each side of the block edge.
 * The top textures carry them on their west / north edges ({@code plain}, {@code edge_w}, {@code edge_n}, {@code corner});
 * a block mirrors the texture for an east / south joint and transposes it when the broom lines run along z (the block's
 * AXIS, the way the walk runs, is x). The position comes from Forge's getModelData(level, pos, ...) at meshing, so every
 * placement (player, WorldEdit, a structure, a rotated template) gets its joints and no state stores them. Sides and bottom:
 * {@code side}. Every face has tint 0, the panel tone ({@link #panelTone}).
 * <p>
 * Model JSON: {@code {"loader": "apocalypse_firstlight:ground_joints", "spacing": 2, "textures": {"particle", "plain",
 * "edge_w", "edge_n", "corner", "side"}}}.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GroundJointModel {
    public static final String LOADER = "ground_joints";
    private static final ModelProperty<Integer> JOINTS = new ModelProperty<>();
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.solid());

    private GroundJointModel() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> pos != null && state.getBlock() instanceof JointedPavementBlock block
                ? panelTone(block.jointSpacing(), pos) : -1, AflBlocks.CONCRETE_SIDEWALK.get(), AflBlocks.CONCRETE_PAVEMENT.get());
    }

    /** Each panel (the cells between two joint lines) its own tone, 95..100 % (a tint only darkens). */
    static int panelTone(int spacing, BlockPos pos) {
        int h = Math.floorDiv(pos.getX(), spacing) * 73856093 ^ Math.floorDiv(pos.getZ(), spacing) * 19349663;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        int c = Math.round(255 * (0.95F + 0.05F * ((h & 0xFFFF) / 65535.0F)));
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    /** The joint on one axis: 0 none, 1 on the west / north edge, 2 on the east / south edge. */
    static int joint(int coordinate, int spacing) {
        int m = Math.floorMod(coordinate, spacing);
        return m == 0 ? 1 : m == spacing - 1 ? 2 : 0;
    }

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            int spacing = GsonHelper.getAsInt(json, "spacing");
            if (spacing < 2) throw new JsonParseException("ground_joints: spacing must be at least 2");
            return new Geometry(spacing);
        }
    }

    private record Geometry(int spacing) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides, ResourceLocation location) {
            Function<String, TextureAtlasSprite> texture = name -> {
                if (!context.hasMaterial(name)) throw new JsonParseException(location + ": ground_joints needs the texture \"" + name + "\"");
                return sprites.apply(context.getMaterial(name));
            };
            TextureAtlasSprite[] tops = {texture.apply("plain"), texture.apply("edge_w"), texture.apply("edge_n"), texture.apply("corner")};
            TextureAtlasSprite side = texture.apply("side");
            BakedQuad[] top = new BakedQuad[18];
            for (int jx = 0; jx < 3; jx++) for (int jz = 0; jz < 3; jz++) for (int t = 0; t < 2; t++) {
                boolean transposed = t == 1;
                boolean west = transposed ? jz != 0 : jx != 0, north = transposed ? jx != 0 : jz != 0;
                top[(jx * 3 + jz) * 2 + t] = top(tops[(west ? 1 : 0) | (north ? 2 : 0)], jx == 2, jz == 2, transposed, location);
            }
            Map<Direction, BakedQuad> sides = new EnumMap<>(Direction.class);
            for (Direction d : Direction.values()) if (d != Direction.UP) sides.put(d, face(d, side, location));
            return new Baked(spacing, top, sides, texture.apply("particle"));
        }
    }

    private static BakedQuad face(Direction direction, TextureAtlasSprite sprite, ResourceLocation location) {
        return BAKERY.bakeQuad(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16),
                new BlockElementFace(direction, 0, "#face", new BlockFaceUV(new float[]{0, 0, 16, 16}, 0)),
                sprite, direction, BlockModelRotation.X0_Y0, null, true, location);
    }

    /** The top face, its texture mirrored (east / south joint) and transposed (broom lines along z) by world position. */
    private static BakedQuad top(TextureAtlasSprite sprite, boolean mirrorX, boolean mirrorZ, boolean transposed, ResourceLocation location) {
        BakedQuad quad = face(Direction.UP, sprite, location);
        int[] data = quad.getVertices().clone();
        for (int i = 0; i < 4; i++) {
            int o = i * 8;   // DefaultVertexFormat.BLOCK: position 0..2, colour 3, uv 4..5
            float x = Float.intBitsToFloat(data[o]), z = Float.intBitsToFloat(data[o + 2]);
            float a = mirrorX ? 1 - x : x, c = mirrorZ ? 1 - z : z;
            float s = transposed ? c : a, t = transposed ? a : c;   // texture s along x, t along z (image top = north)
            data[o + 4] = Float.floatToRawIntBits(sprite.getU(s * 16));
            data[o + 5] = Float.floatToRawIntBits(sprite.getV(t * 16));
        }
        return new BakedQuad(data, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade(), true);
    }

    private static final class Baked implements IDynamicBakedModel {
        private final int spacing;
        private final BakedQuad[] top;
        private final Map<Direction, BakedQuad> sides;
        private final TextureAtlasSprite particle;

        Baked(int spacing, BakedQuad[] top, Map<Direction, BakedQuad> sides, TextureAtlasSprite particle) {
            this.spacing = spacing;
            this.top = top;
            this.sides = sides;
            this.particle = particle;
        }

        @Override
        public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                               @NotNull ModelData data) {
            return data.derive().with(JOINTS, joint(pos.getX(), spacing) * 3 + joint(pos.getZ(), spacing)).build();
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                                 @NotNull ModelData data, @Nullable RenderType renderType) {
            if (side == null) return List.of();
            if (side != Direction.UP) return List.of(sides.get(side));
            Integer joints = data.get(JOINTS);
            int code = joints == null ? 4 : joints;   // no position (an item, a moving piece): joints on the west and north edges
            boolean transposed = state != null && state.hasProperty(JointedPavementBlock.AXIS)
                    && state.getValue(JointedPavementBlock.AXIS) == Direction.Axis.X;
            return List.of(top[code * 2 + (transposed ? 1 : 0)]);
        }

        @Override
        public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
            return LAYERS;
        }

        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
