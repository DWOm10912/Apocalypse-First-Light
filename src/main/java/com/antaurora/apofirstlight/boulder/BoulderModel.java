package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rock R1a: the chunk model of the boulder blocks (root and cell), drawn as Mesh trees are (tree/TreeCellModel).
 * <ul>
 * <li>AFL's view (Embeddium or vanilla chunk building, which pass the model data this model makes): the cell asks the
 * client's index which rocks cover it and draws their quads for that cell. The generator gave every quad, buried ones too,
 * to the nearest cell the rock fills above the ground (or the anchor), so a dug-out rock shows its buried part. Solid
 * layer, flat light from the cell, no ambient occlusion; without a shader pack the vanilla direction shading is baked
 * into the vertex colour (by the normal), with one the pack lights by the normal and the sprites' _n / _s.</li>
 * <li>Without model data (a voxel or map view, other mods): a cube of the state's rock.</li>
 * </ul>
 * R3: a rock draws in its rock's sprites (the root's {@link BoulderBlocks#ROCK}); one model per rock, for the cube and the
 * particles. Baked quads are cached per node, turn, rock and shading; a model reload clears them (atlas UVs change).
 */
final class BoulderModel implements IDynamicBakedModel {
    static final class Cell {
        final List<BakedQuad> quads;
        final ModelData data;

        Cell(List<BakedQuad> quads) {
            this.quads = quads;
            this.data = ModelData.builder().with(CELL, this).build();
        }
    }

    static final ModelProperty<Cell> CELL = new ModelProperty<>();
    static final Cell NOTHING = new Cell(List.of());
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    /** The sprites by rock and quad sprite number. */
    private static final ResourceLocation[][] SPRITES = new ResourceLocation[BoulderRock.values().length][BoulderData.SPRITES.length];
    static {
        for (BoulderRock r : BoulderRock.values())
            for (int i = 0; i < BoulderData.SPRITES.length; i++)
                SPRITES[r.ordinal()][i] = new ResourceLocation("apocalypse_firstlight", "block/boulder/" + r.sprite(BoulderData.SPRITES[i]));
    }
    private static final Map<Integer, Map<Integer, Cell>> BAKED = new ConcurrentHashMap<>();
    private static final Map<BoulderRock, Map<Direction, List<BakedQuad>>> PROXY = new ConcurrentHashMap<>();
    private final BoulderRock rock;

    BoulderModel(BoulderRock rock) { this.rock = rock; }

    static void clearCache() {
        BAKED.clear();
        PROXY.clear();
    }

    // ------------------------------------------------------------------------------------------- model data

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        int shade = AflShaderCompat.shaderPackInUse() ? 0 : 1;
        var roots = new ArrayList<BoulderIndex.Root>(2);
        BoulderIndex.CLIENT.roots(pos.getX(), pos.getY(), pos.getZ(), roots);
        Cell found = null;
        List<BakedQuad> merged = null;
        for (var r : roots) {
            Cell c = baked(r.node, r.turn, r.rock, shade).get(BoulderData.key(r.formX(pos.getX(), pos.getZ()), pos.getY() - r.oy, r.formZ(pos.getX(), pos.getZ())));
            if (c == null) continue;
            if (found == null) found = c;
            else {
                if (merged == null) merged = new ArrayList<>(found.quads);
                merged.addAll(c.quads);
            }
        }
        if (merged != null) found = new Cell(List.copyOf(merged));
        return found == null ? NOTHING.data : found.data;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        Cell c = data.get(CELL);
        return c != null && c.quads.isEmpty() ? ChunkRenderTypeSet.none() : SOLID;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (renderType != null && renderType != RenderType.solid()) return List.of();
        Cell c = data.get(CELL);
        if (c == null) {
            if (state == null) return List.of();
            var faces = PROXY.computeIfAbsent(rock, BoulderModel::buildProxy).get(side);
            return faces == null ? List.of() : faces;
        }
        return side == null ? c.quads : List.of();
    }

    // ------------------------------------------------------------------------------------------------ baking

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    /** A node turned t of a rock at a shading: form cell key -> what that cell draws. */
    static Map<Integer, Cell> baked(BoulderData.Node n, int t, BoulderRock rock, int shade) {
        int key = ((n.index * 4 + (t & 3)) * BoulderRock.values().length + rock.ordinal()) * 2 + shade;
        var done = BAKED.get(key);
        if (done != null) return done;
        ResourceLocation[] ids = SPRITES[rock.ordinal()];
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[ids.length];
        for (int i = 0; i < sprites.length; i++) sprites[i] = sprite(ids[i]);
        var lists = new HashMap<Integer, List<BakedQuad>>();
        for (int q = 0; q < n.quads; q++) {
            int ck = n.quadCell[q];
            // the cell's form position from its key (BoulderData#key)
            int cx = ((ck >> 20) & 1023) - 512, cy = ((ck >> 10) & 1023) - 512, cz = (ck & 1023) - 512;
            int rx = BoulderData.turnX(t, cx, cz), rz = BoulderData.turnZ(t, cx, cz);
            lists.computeIfAbsent(ck, k -> new ArrayList<>()).add(bake(n, q, t, rx, cy, rz, sprites[n.quadSprite[q]], shade));
        }
        var out = new HashMap<Integer, Cell>();
        for (var e : lists.entrySet()) out.put(e.getKey(), new Cell(List.copyOf(e.getValue())));
        var made = Map.copyOf(out);
        var raced = BAKED.putIfAbsent(key, made);
        return raced != null ? raced : made;
    }

    /** One quad of a node turned t, relative to its (turned) cell rx, cy, rz. Layout: DefaultVertexFormat.BLOCK. */
    private static BakedQuad bake(BoulderData.Node n, int q, int t, int rx, int cy, int rz, TextureAtlasSprite sprite, int shade) {
        float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
        int[] data = new int[32];
        float fx = 0, fy = 0, fz = 0;
        for (int i = 0; i < 4; i++) {
            int o = i * 8, p = q * 12 + i * 3;
            float px = n.pos[p], py = n.pos[p + 1], pz = n.pos[p + 2];
            float nx0 = n.normal[p] / 127F, ny = n.normal[p + 1] / 127F, nz0 = n.normal[p + 2] / 127F;
            float nx = BoulderData.turnX(t, nx0, nz0), nz = BoulderData.turnZ(t, nx0, nz0);
            fx += nx; fy += ny; fz += nz;
            data[o] = Float.floatToRawIntBits(0.5F + BoulderData.turnX(t, px, pz) - rx);
            data[o + 1] = Float.floatToRawIntBits(py - cy);
            data[o + 2] = Float.floatToRawIntBits(0.5F + BoulderData.turnZ(t, px, pz) - rz);
            float light = shade == 1 ? nx * nx * 0.6F + nz * nz * 0.8F + ny * ny * (ny >= 0 ? 1.0F : 0.5F) : 1;
            int g = Math.max(0, Math.min(255, Math.round(light * 255)));
            data[o + 3] = 0xFF000000 | g << 16 | g << 8 | g;               // ABGR
            data[o + 4] = Float.floatToRawIntBits(u0 + du * n.uv[q * 8 + i * 2]);
            data[o + 5] = Float.floatToRawIntBits(v0 + dv * n.uv[q * 8 + i * 2 + 1]);
            data[o + 6] = 0;                                                // the chunk renderer's light
            data[o + 7] = (((byte) Math.round(nx * 127)) & 0xFF) | (((byte) Math.round(ny * 127)) & 0xFF) << 8 | (((byte) Math.round(nz * 127)) & 0xFF) << 16;
        }
        return new BakedQuad(data, -1, Direction.getNearest(fx, fy, fz), sprite, shade == 0, false);
    }

    /** A rock's cube without model data: one face per side, on the cell boundary. */
    private static Map<Direction, List<BakedQuad>> buildProxy(BoulderRock rock) {
        TextureAtlasSprite sprite = sprite(SPRITES[rock.ordinal()][0]);
        float u0 = sprite.getU0(), du = (sprite.getU1() - u0) / 2, v0 = sprite.getV0(), dv = (sprite.getV1() - v0) / 2;
        var map = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction d : Direction.values()) {
            float[][] c = faceCorners(d);
            int[] data = new int[32];
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                data[o] = Float.floatToRawIntBits(c[i][0]);
                data[o + 1] = Float.floatToRawIntBits(c[i][1]);
                data[o + 2] = Float.floatToRawIntBits(c[i][2]);
                data[o + 3] = 0xFFFFFFFF;
                data[o + 4] = Float.floatToRawIntBits(u0 + du * (i == 1 || i == 2 ? 1 : 0));
                data[o + 5] = Float.floatToRawIntBits(v0 + dv * (i < 2 ? 1 : 0));
                data[o + 6] = 0;
                data[o + 7] = (d.getStepX() * 127 & 0xFF) | (d.getStepY() * 127 & 0xFF) << 8 | (d.getStepZ() * 127 & 0xFF) << 16;
            }
            map.put(d, List.of(new BakedQuad(data, -1, d, sprite, true, false)));
        }
        return map;
    }

    /** Counter-clockwise from outside, the full cell. */
    private static float[][] faceCorners(Direction d) {
        return switch (d) {
            case UP -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case DOWN -> new float[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
            case NORTH -> new float[][]{{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}};
            case SOUTH -> new float[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
            case WEST -> new float[][]{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
            case EAST -> new float[][]{{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}};
        };
    }

    // -------------------------------------------------------------------------------------------- the rest

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return sprite(SPRITES[rock.ordinal()][0]); }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
