package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
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

import java.util.List;
import java.util.function.Function;

/**
 * Cabin Exterior V1, M1.1 (docs/models/cabin_exterior_v1.md, 2026-10-12): a full cube whose faces show one tile of a
 * larger seamless texture ({@code columns} x {@code rows} blocks), picked by world position, so a wall of it repeats every
 * few blocks instead of every block (the fieldstone plinth's one-metre repeat read as tiles). The tiles are separate
 * sprites {@code tile0 .. tile<n-1>} (index row * columns + column, row 0 at the top of the big texture), each with its own
 * LabPBR maps. Each face follows vanilla's cube UVs (sides: u across the face as seen from outside, v down; top: u = x,
 * v = z; bottom: u = x, v = -z), so neighbouring blocks in one plane continue the big texture. The position comes from
 * Forge's getModelData at meshing (any placement works, no state stores it); an item or a block without a position
 * shows tile 0. Plain baked faces: vanilla smooth lighting and ambient occlusion, cull faces as a cube.
 * <p>
 * Model JSON: {@code {"loader": "apocalypse_firstlight:macro_tile", "columns": 3, "rows": 2, "textures": {"particle",
 * "tile0", ...}}}.
 * <p>
 * Box Stove V1 (docs/models/box_stove_v1.md, 2026-10-13): an optional separate top, {@code "top_columns"} x
 * {@code "top_rows"} tiles {@code top0 ..} for the upward face only (the flagstone: slabs on top, the fieldstone's rubble on
 * its sides and bottom).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MacroTileModel {
    public static final String LOADER = "macro_tile";
    /** Per face (Direction ordinal) the tile index, 4 bits each. */
    private static final ModelProperty<Integer> TILES = new ModelProperty<>();
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.solid());

    private MacroTileModel() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    /** The tile a face of the block at pos shows: its column along the face's u and its row along v. */
    static int tile(Direction face, BlockPos pos, int columns, int rows) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ(), u, v;
        switch (face) {
            case NORTH -> { u = -x - 1; v = -y - 1; }
            case SOUTH -> { u = x; v = -y - 1; }
            case WEST -> { u = z; v = -y - 1; }
            case EAST -> { u = -z - 1; v = -y - 1; }
            case UP -> { u = x; v = z; }
            default -> { u = x; v = -z - 1; }
        }
        return Math.floorMod(v, rows) * columns + Math.floorMod(u, columns);
    }

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            int columns = GsonHelper.getAsInt(json, "columns"), rows = GsonHelper.getAsInt(json, "rows");
            int topColumns = GsonHelper.getAsInt(json, "top_columns", 0), topRows = GsonHelper.getAsInt(json, "top_rows", 0);
            if (columns < 1 || rows < 1 || columns * rows > 16 || topColumns * topRows > 16 || topColumns < 0 || topRows < 0)
                throw new JsonParseException("macro_tile: 1..16 tiles");
            return new Geometry(columns, rows, topColumns, topRows);
        }
    }

    /** topColumns x topRows 0: the upward face shows the side tiles too. */
    private record Geometry(int columns, int rows, int topColumns, int topRows) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides, ResourceLocation location) {
            int n = columns * rows, nt = topColumns * topRows;
            BakedQuad[][] quads = new BakedQuad[6][Math.max(n, nt)];
            for (Direction d : Direction.values()) {
                boolean top = d == Direction.UP && nt > 0;
                for (int t = 0; t < (top ? nt : n); t++) {
                    String name = (top ? "top" : "tile") + t;
                    if (!context.hasMaterial(name)) throw new JsonParseException(location + ": macro_tile needs the texture \"" + name + "\"");
                    quads[d.ordinal()][t] = BAKERY.bakeQuad(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16),
                            new BlockElementFace(d, -1, "#" + name, new BlockFaceUV(new float[]{0, 0, 16, 16}, 0)),
                            sprites.apply(context.getMaterial(name)), d, BlockModelRotation.X0_Y0, null, true, location);
                }
            }
            return new Baked(columns, rows, nt > 0 ? topColumns : columns, nt > 0 ? topRows : rows, quads, sprites.apply(context.getMaterial("particle")),
                    context.getTransforms());
        }
    }

    private static final class Baked implements IDynamicBakedModel {
        private final int columns, rows, topColumns, topRows;
        private final BakedQuad[][] quads;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;

        Baked(int columns, int rows, int topColumns, int topRows, BakedQuad[][] quads, TextureAtlasSprite particle, ItemTransforms transforms) {
            this.columns = columns;
            this.rows = rows;
            this.topColumns = topColumns;
            this.topRows = topRows;
            this.quads = quads;
            this.particle = particle;
            this.transforms = transforms;
        }

        @Override
        public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                               @NotNull ModelData data) {
            int packed = 0;
            for (Direction d : Direction.values())
                packed |= (d == Direction.UP ? tile(d, pos, topColumns, topRows) : tile(d, pos, columns, rows)) << 4 * d.ordinal();
            return data.derive().with(TILES, packed).build();
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                                 @NotNull ModelData data, @Nullable RenderType renderType) {
            if (side == null) return List.of();
            Integer packed = data.get(TILES);
            int t = packed == null ? 0 : packed >>> 4 * side.ordinal() & 15;
            return List.of(quads[side.ordinal()][t]);
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
        @Override public @NotNull ItemTransforms getTransforms() { return transforms; }
    }
}
