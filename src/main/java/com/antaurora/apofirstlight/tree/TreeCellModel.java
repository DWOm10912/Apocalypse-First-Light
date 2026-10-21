package com.antaurora.apofirstlight.tree;

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
import com.antaurora.apofirstlight.tree.TreeForms.Species;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesh Trees P0-A (docs/rendering/mesh_trees_p0a.md, B, D, E, F): the chunk model of the four sample cell blocks.
 * <ul>
 * <li>AFL's view (Embeddium or vanilla chunk building, which always pass the model data this model makes): the cell asks
 * the root index which trees cover it ({@link TreeClient#roots}) and draws their quads for its cell at each tree's level.
 * Wood goes to the solid layer, leaves to cutout_mipped (mipmaps keep distant crowns from shimmering); flat light from
 * the cell (no ambient occlusion: smooth light clamps corners into the cell, wrong for geometry outside it). Crown depth
 * occlusion is baked into the vertex colour; without a shader pack the vanilla direction shading too (by the normal,
 * blended), with one the pack lights by the normal.</li>
 * <li>Without model data (Voxy's model bakery, maps, the breaking overlay, other mods): the volume proxy, one leaf cube
 * per crown cell and a bark post per wood cell, so a voxel view sees the crown's outline, height and colour.</li>
 * </ul>
 * Baked quads are cached per variant, turn, level and shading (the layout is immutable); a model reload clears them.
 */
final class TreeCellModel implements IDynamicBakedModel {
    enum Kind { ROOT, WOOD, FOLIAGE, CANOPY, STUMP }

    /** What one cell draws in AFL's view; {@code data} is the model data that carries it. */
    static final class Cell {
        final List<BakedQuad> quads;
        final boolean proxy;
        final ModelData data;

        Cell(List<BakedQuad> quads, boolean proxy) {
            this.quads = quads;
            this.proxy = proxy;
            this.data = ModelData.builder().with(CELL, this).build();
        }
    }

    static final ModelProperty<Cell> CELL = new ModelProperty<>();
    static final Cell NOTHING = new Cell(List.of(), false), PROXY = new Cell(List.of(), true);
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid()), LEAVES = ChunkRenderTypeSet.of(RenderType.cutoutMipped());
    /**
     * Sprites by TreeShape sprite number (species * SLOTS + slot), then (P2, TreeCut) cut wood and end grain per species;
     * the two species' proxy faces separately.
     */
    private static final ResourceLocation[] SPRITES = spriteIds(), PROXY_LEAVES = proxyIds();
    private static final Map<Integer, Map<Integer, Cell>> BAKED = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<Direction, List<BakedQuad>>> PROXIES = new ConcurrentHashMap<>();
    /** P2: notched root cells (cut stage, side, cut height) and stumps, built on first sight. */
    private static final Map<Long, Cell> NOTCHED = new ConcurrentHashMap<>(), STUMPS = new ConcurrentHashMap<>();

    private final Kind kind;
    /** The crown blocks' species (their proxy face and tint); the wood blocks are shared by all species. */
    private final Species species;

    TreeCellModel(Kind kind, Species species) {
        this.kind = kind;
        this.species = species;
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("apocalypse_firstlight", "block/tree/" + name);
    }

    /** Per species bark, leaves, leaves_mid, leaves_far (TreeShape slots), then cut wood and end grain per species (TreeCut). */
    private static ResourceLocation[] spriteIds() {
        Species[] all = Species.values();
        var ids = new ResourceLocation[TreeCut.END_SPRITE + all.length];
        for (Species s : all) {
            int i = s.ordinal() * TreeShape.SLOTS;
            ids[i + TreeShape.BARK] = tex(s.id + "_bark");
            ids[i + TreeShape.LEAVES] = tex(s.leavesId);
            ids[i + TreeShape.LEAVES_MID] = tex(s.leavesId + "_mid");
            ids[i + TreeShape.LEAVES_FAR] = tex(s.leavesId + "_far");
            ids[TreeCut.CUT_SPRITE + s.ordinal()] = tex(s.id + "_cut");
            ids[TreeCut.END_SPRITE + s.ordinal()] = tex(s.id + "_end_grain");
        }
        return ids;
    }

    private static ResourceLocation[] proxyIds() {
        var ids = new ResourceLocation[Species.values().length];
        for (Species s : Species.values()) ids[s.ordinal()] = tex(s.leavesId + "_proxy");
        return ids;
    }

    static void clearCache() {
        BAKED.clear();
        PROXIES.clear();
        NOTCHED.clear();
        STUMPS.clear();
    }

    static int notchedCacheSize() { return NOTCHED.size() + STUMPS.size(); }

    static int cacheSize() {
        return BAKED.size();
    }

    private boolean woody() {
        return kind == Kind.ROOT || kind == Kind.WOOD || kind == Kind.STUMP;
    }

    // ------------------------------------------------------------------------------------------- model data

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        if (TreeClient.override == TreeClient.PROXY) return PROXY.data;
        if (kind == Kind.CANOPY) return NOTHING.data;
        int shade = AflShaderCompat.shaderPackInUse() ? 0 : 1;
        if (kind == Kind.STUMP) return stump(state, pos, shade).data;
        long start = System.nanoTime();
        var roots = new ArrayList<TreeIndex.Root>(2);
        TreeIndex.CLIENT.roots(pos.getX(), pos.getY(), pos.getZ(), roots);
        Cell found = null;
        List<BakedQuad> merged = null;
        for (var r : roots) {
            var cell = r.layout.cell(pos.getX() - r.x, pos.getY() - r.y, pos.getZ() - r.z);
            if (cell == null || (cell.kind == TreeShape.WOOD) != woody()) continue;
            int lod = TreeClient.stageOf(r);
            Cell c;
            // P2: a root being chopped draws its notch (near level only: the chopper is close)
            if (kind == Kind.ROOT && cell.x == 0 && cell.y == 0 && cell.z == 0 && lod == 0 && state.getBlock() == TreeBlocks.root()
                    && state.getValue(TreeBlocks.CHOP) > 0) c = notched(r.layout, state, pos, shade);
            else c = baked(r.layout, lod, shade).get(TreeShape.key(cell.x, cell.y, cell.z));
            if (c == null) continue;
            if (found == null) found = c;
            else {
                if (merged == null) merged = new ArrayList<>(found.quads);
                merged.addAll(c.quads);
            }
        }
        if (merged != null) found = new Cell(List.copyOf(merged), false);
        TreeClient.lookups.increment();
        TreeClient.lookupNanos.add(System.nanoTime() - start);
        if (found == null) {
            if (roots.isEmpty()) TreeClient.orphans.increment();
            return NOTHING.data;
        }
        TreeClient.carriers.increment();
        TreeClient.quadsOut.add(found.quads.size());
        return found.data;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        Cell c = data.get(CELL);
        if (c != null && !c.proxy && c.quads.isEmpty()) return ChunkRenderTypeSet.none();
        return woody() ? SOLID : LEAVES;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        Cell c = data.get(CELL);
        if (c == null) {
            // no model data: a voxel or map view, which asks for whatever layer it thinks the block has
            if (renderType == RenderType.translucent() || state == null) return List.of();
            return proxy(state, side);
        }
        RenderType layer = woody() ? RenderType.solid() : RenderType.cutoutMipped();
        if (renderType != null && renderType != layer) return List.of();
        if (c.proxy) return state == null ? List.of() : proxy(state, side);
        return side == null ? c.quads : List.of();
    }

    // ------------------------------------------------------------------------------------------------ baking

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    /** The atlas sprites by sprite number. */
    static TextureAtlasSprite[] sprites() {
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[SPRITES.length];
        for (int i = 0; i < sprites.length; i++) sprites[i] = sprite(SPRITES[i]);
        return sprites;
    }

    static long layoutKey(TreeShape.Layout layout) {
        return (layout.species.ordinal() * TreeShape.VARIANTS + layout.variant) * 4L + layout.turns;
    }

    /** The root cell with the notch of its state's cut stage and side, at the cut height of its position (LOD0 quads). */
    private static Cell notched(TreeShape.Layout layout, BlockState state, BlockPos pos, int shade) {
        int stage = state.getValue(TreeBlocks.CHOP), side = state.getValue(TreeBlocks.CUT);
        float cut = FellingRules.cutHeight(layout.species, pos.getX(), pos.getY(), pos.getZ());
        long key = ((((layoutKey(layout) * 8 + stage) * 8 + side) * 64 + Math.round(cut * 32)) << 1) | shade;
        return NOTCHED.computeIfAbsent(key, k -> {
            var root = layout.cell(0, 0, 0);
            var quads = TreeCut.notch(root == null ? List.of() : root.quads[0], cut, stage, FellingRules.sideAngle(side), layout.species.ordinal());
            return bakeAll(quads, shade);
        });
    }

    /** A stump: its state's trunk below the cut of its position, with the end-grain cap. */
    private static Cell stump(BlockState state, BlockPos pos, int shade) {
        var layout = TreeBlocks.layoutOf(state);
        float cut = FellingRules.cutHeight(layout.species, pos.getX(), pos.getY(), pos.getZ());
        long key = ((layoutKey(layout) * 64 + Math.round(cut * 32)) << 1) | shade;
        return STUMPS.computeIfAbsent(key, k -> {
            var root = layout.cell(0, 0, 0);
            return bakeAll(TreeCut.stump(root == null ? List.of() : root.quads[0], cut, layout.species.ordinal()), shade);
        });
    }

    private static Cell bakeAll(List<TreeShape.Quad> quads, int shade) {
        TextureAtlasSprite[] sprites = sprites();
        var list = new ArrayList<BakedQuad>(quads.size() * 2);
        for (var q : quads) bake(q, 0, 0, 0, sprites[q.sprite], shade, list);
        return new Cell(List.copyOf(list), false);
    }

    /**
     * One variant, turn, stage, shading: cell key -> what that cell draws (cells without quads at that stage absent). A
     * stage between two levels (TreeClient#STEPS) draws the nearer level's leaf cards whose fixed random rank is at least
     * the blend share and the farther level's below it, the nearer wood under half the share, the farther wood from half
     * on: the same baked quads as the two levels, picked by reference.
     */
    static Map<Integer, Cell> baked(TreeShape.Layout layout, int stage, int shade) {
        if (stage % TreeClient.STEPS == 0) return level(layout, stage / TreeClient.STEPS, shade);
        int key = (((layout.species.ordinal() * TreeShape.VARIANTS + layout.variant) * 4 + layout.turns) * TreeClient.STAGES + stage) * 2 + shade;
        var done = BAKED.get(key);
        if (done != null) return done;
        // the two levels first, outside: level() writes BAKED too, and a computeIfAbsent inside another one on the same
        // map throws "Recursive update" when the map resizes meanwhile (a chunk build failing now and then)
        int a = stage / TreeClient.STEPS;
        float t = (stage % TreeClient.STEPS) / (float) TreeClient.STEPS;
        var near = level(layout, a, shade);
        var far = level(layout, a + 1, shade);
        var out = new java.util.HashMap<Integer, Cell>();
        for (var cell : layout.cells.values()) {
            int ck = TreeShape.key(cell.x, cell.y, cell.z);
            var list = new ArrayList<BakedQuad>();
            pick(cell, a, near.get(ck), t, false, list);
            pick(cell, a + 1, far.get(ck), t, true, list);
            if (!list.isEmpty()) out.put(ck, new Cell(List.copyOf(list), false));
        }
        var made = Map.copyOf(out);
        var raced = BAKED.putIfAbsent(key, made);
        return raced != null ? raced : made;
    }

    /** A level's baked quads of a cell kept at blend share t: the nearer level (farther false) keeps what the farther drops. */
    private static void pick(TreeShape.Cell cell, int level, @Nullable Cell baked, float t, boolean farther, List<BakedQuad> out) {
        if (baked == null) return;
        var quads = cell.quads[level];
        int at = 0;
        for (int i = 0; i < quads.size(); i++) {
            var q = quads.get(i);
            int n = q.twoSided ? 2 : 1;
            boolean below = q.kind == TreeShape.FOLIAGE ? rank(cell, level, i) < t : t >= 0.5F;
            if (below == farther) for (int j = 0; j < n; j++) out.add(baked.quads.get(at + j));
            at += n;
        }
    }

    /** A quad's fixed random rank in 0..1 (cell, level, index). */
    private static float rank(TreeShape.Cell cell, int level, int i) {
        long h = (TreeShape.key(cell.x, cell.y, cell.z) * 0x9E3779B97F4A7C15L) ^ (level * 0xC2B2AE3D27D4EB4FL) ^ (i * 0x165667B19E3779F9L);
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33;
        return (h >>> 40) / (float) (1 << 24);
    }

    /** One variant, turn, level, shading: cell key -> what that cell draws (cells without quads at that level absent). */
    private static Map<Integer, Cell> level(TreeShape.Layout layout, int level, int shade) {
        int key = (((layout.species.ordinal() * TreeShape.VARIANTS + layout.variant) * 4 + layout.turns) * TreeClient.STAGES + level * TreeClient.STEPS) * 2 + shade;
        return BAKED.computeIfAbsent(key, k -> {
            TextureAtlasSprite[] sprites = sprites();
            var out = new java.util.HashMap<Integer, Cell>();
            for (var cell : layout.cells.values()) {
                var quads = cell.quads[level];
                if (quads.isEmpty()) continue;
                var list = new ArrayList<BakedQuad>(quads.size() * 2);
                for (var q : quads) bake(q, cell.x, cell.y, cell.z, sprites[q.sprite], shade, list);
                out.put(TreeShape.key(cell.x, cell.y, cell.z), new Cell(List.copyOf(list), false));
            }
            return Map.copyOf(out);
        });
    }

    /** A sample quad as chunk quads relative to its cell (front, and back for a card). Layout: DefaultVertexFormat.BLOCK. */
    static void bake(TreeShape.Quad q, int ox, int oy, int oz, TextureAtlasSprite sprite, int shade, List<BakedQuad> out) {
        bake(q, ox, oy, oz, sprite, shade, 0, out);
    }

    /** The same with a baked light (packed like LightTexture.pack; the renderer keeps the brighter of it and its own per channel). */
    static void bake(TreeShape.Quad q, int ox, int oy, int oz, TextureAtlasSprite sprite, int shade, int lightmap, List<BakedQuad> out) {
        boolean leaf = q.kind == TreeShape.FOLIAGE;
        float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
        for (int side = 0; side < (q.twoSided ? 2 : 1); side++) {
            int[] order = side == 0 ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};
            float sign = side == 0 ? 1 : -1;
            int[] data = new int[32];
            float fx = 0, fy = 0, fz = 0;
            for (int i = 0; i < 4; i++) {
                int c = order[i], o = i * 8;
                float nx = q.n[c * 3] * sign, ny = q.n[c * 3 + 1] * sign, nz = q.n[c * 3 + 2] * sign;
                fx += nx; fy += ny; fz += nz;
                data[o] = Float.floatToRawIntBits(q.p[c * 3] - ox);
                data[o + 1] = Float.floatToRawIntBits(q.p[c * 3 + 1] - oy);
                data[o + 2] = Float.floatToRawIntBits(q.p[c * 3 + 2] - oz);
                float light = q.ao[c];
                if (shade == 1) light *= leaf ? 0.82F + 0.18F * Math.abs(ny) : nx * nx * 0.6F + nz * nz * 0.8F + ny * ny * (ny >= 0 ? 1.0F : 0.5F);
                int g = Math.max(0, Math.min(255, Math.round(light * 255)));
                data[o + 3] = 0xFF000000 | g << 16 | g << 8 | g;               // ABGR
                data[o + 4] = Float.floatToRawIntBits(u0 + du * q.uv[c * 2]);
                data[o + 5] = Float.floatToRawIntBits(v0 + dv * q.uv[c * 2 + 1]);
                data[o + 6] = lightmap;                                         // 0: the chunk renderer's light only
                data[o + 7] = (((byte) (nx * 127)) & 0xFF) | (((byte) (ny * 127)) & 0xFF) << 8 | (((byte) (nz * 127)) & 0xFF) << 16;
            }
            out.add(new BakedQuad(data, leaf ? Math.max(0, q.tint) : -1, Direction.getNearest(fx, fy, fz), sprite, shade == 0, false));
        }
    }

    /** The volume proxy of a cell: a leaf cube (culled against neighbouring crown cells) or a bark post of the girth. */
    private List<BakedQuad> proxy(BlockState state, @Nullable Direction side) {
        // wood: a bark post (root wider); crowns: a leaf cube with the species' face and tint
        int which = kind == Kind.WOOD ? 4 : kind == Kind.ROOT || kind == Kind.STUMP ? 7 : -1 - species.ordinal();
        // side null: the faces inside the cell (a post's sides); a direction: the faces on that cell boundary
        List<BakedQuad> faces = PROXIES.computeIfAbsent(which, TreeCellModel::buildProxy).get(side);
        return faces == null ? List.of() : faces;
    }

    /** which: a post's half width in 1/16 block, or -1 - species for a crown cube. */
    private static Map<Direction, List<BakedQuad>> buildProxy(int which) {
        boolean leaf = which < 0;
        int sp = leaf ? -1 - which : 0;
        TextureAtlasSprite sprite = sprite(leaf ? PROXY_LEAVES[sp] : SPRITES[0]);
        float h = leaf ? 0.5F : which / 16F;
        var map = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        var inner = new ArrayList<BakedQuad>();
        for (Direction d : Direction.values()) {
            var q = new TreeShape.Quad();
            q.kind = leaf ? TreeShape.FOLIAGE : TreeShape.WOOD;
            q.tint = leaf ? Species.values()[sp].tint : -1;
            float[][] c = faceCorners(d, h);
            for (int i = 0; i < 4; i++) {
                q.p[i * 3] = c[i][0]; q.p[i * 3 + 1] = c[i][1]; q.p[i * 3 + 2] = c[i][2];
                q.n[i * 3] = d.getStepX(); q.n[i * 3 + 1] = d.getStepY(); q.n[i * 3 + 2] = d.getStepZ();
                q.uv[i * 2] = i == 1 || i == 2 ? 1 : 0; q.uv[i * 2 + 1] = i < 2 ? 1 : 0;
                q.ao[i] = 1;
            }
            var list = new ArrayList<BakedQuad>(1);
            // vanilla direction shading for the proxy in every case: it is a plain block
            bake(q, 0, 0, 0, sprite, 0, list);
            boolean onBoundary = leaf || d.getAxis() == Direction.Axis.Y;
            if (onBoundary) map.put(d, List.copyOf(list)); else inner.addAll(list);
        }
        var result = new java.util.HashMap<Direction, List<BakedQuad>>(map);
        result.put(null, List.copyOf(inner));
        return result;
    }

    /** Counter-clockwise from outside, a box of half width h around the cell's vertical axis (full height). */
    private static float[][] faceCorners(Direction d, float h) {
        float a = 0.5F - h, b = 0.5F + h;
        return switch (d) {
            case UP -> new float[][]{{a, 1, a}, {a, 1, b}, {b, 1, b}, {b, 1, a}};
            case DOWN -> new float[][]{{a, 0, b}, {a, 0, a}, {b, 0, a}, {b, 0, b}};
            case NORTH -> new float[][]{{b, 0, a}, {a, 0, a}, {a, 1, a}, {b, 1, a}};
            case SOUTH -> new float[][]{{a, 0, b}, {b, 0, b}, {b, 1, b}, {a, 1, b}};
            case WEST -> new float[][]{{a, 0, a}, {a, 0, b}, {a, 1, b}, {a, 1, a}};
            case EAST -> new float[][]{{b, 0, b}, {b, 0, a}, {b, 1, a}, {b, 1, b}};
        };
    }

    // -------------------------------------------------------------------------------------------- the rest

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return sprite(woody() ? SPRITES[0] : SPRITES[species.ordinal() * TreeShape.SLOTS + TreeShape.LEAVES]);
    }

    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
