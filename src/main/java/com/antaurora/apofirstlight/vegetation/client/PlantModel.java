package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.client.TerrainSmoothClient;
import com.antaurora.apofirstlight.vegetation.MeshPlantBlock;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chunk model of a mesh plant block (AFL vegetation P1, docs/gameplay/vegetation_v1_design.md 10-13): per block,
 * from its species and position, a layout (PlantShape), the stage of the block (VegetationClient: distance level and blend, doc 17.3), its
 * pose from the block state (a footprint, under a wheel, rising: MeshPlantBlock) and, on a smoothed slope, each clump
 * moved to the drawn ground under its foot (TerrainSmoothClient#groundCorners). A block in the near layer
 * (VegetationDynamics: someone close by, its blades simulated) is left out here.
 * <p>
 * Baked quads are cached per layout, stage, pose and shading (no shift); a slope copies them per block, each clump by its
 * own height. The LOD0 leaf blades solid (no cutout to eat their edges), the cards cutout mipped; flat light from the
 * cell, no ambient occlusion; without a pack each vertex's brightness by its normal is baked. Tint 0 on the green parts (the
 * biome's grass colour, as the ground), none on what has its own colour (culms, spikes, flowers, dead leaves). A tall
 * species keeps its far cards at every distance. What stands takes the sheet the shader moves ({@link #SPRITE}),
 * what lies or leans its still copy ({@link #STILL_SPRITE}). Without model data (maps, Voxy, the break overlay): nothing.
 */
public final class PlantModel implements IDynamicBakedModel {
    static final ResourceLocation SPRITE = new ResourceLocation("apocalypse_firstlight", "block/vegetation/mesh_plants"),
            STILL_SPRITE = new ResourceLocation("apocalypse_firstlight", "block/vegetation/mesh_plants_still");
    private static final ModelProperty<Cell> CELL = new ModelProperty<>();
    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped()),
            SOLID = ChunkRenderTypeSet.of(RenderType.solid()), BOTH = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutoutMipped());
    /** Poses: 0 standing; 1 + ((trodden - 1) * 8 + heading) * 25 + fx * 5 + fz for MeshPlantBlock's trodden 1-3, 8 headings, 5 x 5 footprints. */
    static final int POSES = 1 + 3 * 8 * 25;
    private static final Map<Integer, Cell> BAKED = new ConcurrentHashMap<>();
    private static final Cell NOTHING = new Cell(List.of(), new float[0], List.of(), new float[0]);
    private final MeshPlantBlock.Species species;

    PlantModel(MeshPlantBlock.Species species) {
        this.species = species;
    }

    /** What a block draws: its solid and cutout quads and each quad's clump foot (x, z pairs), where a slope moves it. */
    static final class Cell {
        final List<BakedQuad> solid, cutout;
        final float[] solidFeet, cutoutFeet;
        final ModelData data;

        Cell(List<BakedQuad> solid, float[] solidFeet, List<BakedQuad> cutout, float[] cutoutFeet) {
            this.solid = solid;
            this.solidFeet = solidFeet;
            this.cutout = cutout;
            this.cutoutFeet = cutoutFeet;
            this.data = ModelData.builder().with(CELL, this).build();
        }

        int size() {
            return solid.size() + cutout.size();
        }
    }

    static void clearCache() {
        BAKED.clear();
    }

    static int cacheSize() {
        return BAKED.size();
    }

    /** The layout of a position (a fixed hash: the same on every client). */
    static int variant(BlockPos pos) {
        long h = pos.getX() * 0x9E3779B97F4A7C15L ^ pos.getZ() * 0xC2B2AE3D27D4EB4FL ^ pos.getY() * 0x165667B19E3779F9L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return (int) ((h >>> 1) % PlantShape.VARIANTS);
    }

    /** A block's rank for thinning far out (VegetationClient#keep), 0..1, not tied to its variant. */
    static float thin(BlockPos pos) {
        long h = pos.getX() * 0xD1B54A32D192ED03L ^ pos.getZ() * 0xABC98388FB8FAC03L ^ pos.getY() * 0x8CB92BA72F3D8DD7L;
        h ^= h >>> 32; h *= 0x9E3779B97F4A7C15L; h ^= h >>> 29;
        return (h >>> 40) / (float) (1 << 24);
    }

    /** A block state's pose number. */
    static int pose(BlockState state) {
        if (!state.hasProperty(MeshPlantBlock.TRODDEN)) return 0;
        int t = state.getValue(MeshPlantBlock.TRODDEN);
        return t == MeshPlantBlock.STANDING ? 0 : 1 + ((t - 1) * 8 + state.getValue(MeshPlantBlock.HEADING)) * 25
                + state.getValue(MeshPlantBlock.FX) * 5 + state.getValue(MeshPlantBlock.FZ);
    }

    /** What presses a block in a pose. */
    static PlantShape.Press press(int pose) {
        if (pose <= 0) return PlantShape.Press.NONE;
        int fz = (pose - 1) % 5, fx = (pose - 1) / 5 % 5, heading = (pose - 1) / 25 % 8, t = (pose - 1) / 200 + 1;
        return new PlantShape.Press(t, MeshPlantBlock.headingX(heading), MeshPlantBlock.headingZ(heading),
                MeshPlantBlock.printMiddle(fx), MeshPlantBlock.printMiddle(fz));
    }

    // ------------------------------------------------------------------------------------------- model data

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        long start = System.nanoTime();
        if (VegetationDynamics.hides(pos)) {
            VegetationClient.count(start, 0);
            return NOTHING.data;
        }
        int stage = VegetationClient.stage(pos, PlantShape.nearQuads(species.ordinal()));
        int pose = pose(state);
        // a trail or a wheel's track is drawn even far out (as a flat strip at LOD2), and a tall species at every distance
        if (pose > 0 || species.tall) stage = Math.min(stage, (PlantShape.LEVELS - 1) * VegetationClient.STEPS);
        // farther out the low plants thin out, a block at a time by its own rank (doc 25), so the ground there is not a mush
        // of one-pixel tufts; tall plants and trails stay
        else if (thin(pos) >= VegetationClient.keep(stage)) stage = VegetationClient.NOTHING_STAGE;
        if (stage >= VegetationClient.NOTHING_STAGE) {
            VegetationClient.count(start, 0);
            return NOTHING.data;
        }
        int shade = AflShaderCompat.shaderPackInUse() ? 0 : 1;
        Cell cell = baked(species.ordinal(), PlantModel.variant(pos), stage, pose, shade);
        float[] corners = cell.size() == 0 ? null : TerrainSmoothClient.groundCorners(level, pos.below());
        if (corners != null) cell = new Cell(moved(cell.solid, cell.solidFeet, corners), cell.solidFeet, moved(cell.cutout, cell.cutoutFeet, corners), cell.cutoutFeet);
        VegetationClient.count(start, cell.size());
        return cell.data;
    }

    /** Quads with each clump moved up or down to the drawn slope under its foot. */
    private static List<BakedQuad> moved(List<BakedQuad> quads, float[] feet, float[] corners) {
        var out = new ArrayList<BakedQuad>(quads.size());
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad q = quads.get(i);
            float dy = (float) SmoothRules.surface(corners, feet[i * 2], feet[i * 2 + 1]) - 1;
            int[] v = q.getVertices().clone();
            int stride = v.length / 4;
            for (int k = 0; k < 4; k++) v[k * stride + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[k * stride + 1]) + dy);
            out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
        }
        return out;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        Cell c = data.get(CELL);
        if (c == null) return ChunkRenderTypeSet.none();
        boolean s = !c.solid.isEmpty(), k = !c.cutout.isEmpty();
        return s && k ? BOTH : s ? SOLID : k ? CUTOUT : ChunkRenderTypeSet.none();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        Cell c = data.get(CELL);
        if (c == null || side != null) return List.of();
        if (renderType == RenderType.solid()) return c.solid;
        if (renderType == RenderType.cutoutMipped()) return c.cutout;
        if (renderType != null) return List.of();
        var all = new ArrayList<BakedQuad>(c.size());
        all.addAll(c.solid);
        all.addAll(c.cutout);
        return all;
    }

    // ------------------------------------------------------------------------------------------------ baking

    /** A species' layout at a stage in a pose: the nearer level's clumps ranked at or above the blend share, the farther's below. */
    static Cell baked(int species, int variant, int stage, int pose, int shade) {
        int key = (((species * PlantShape.VARIANTS + variant) * VegetationClient.STAGES + stage) * POSES + pose) * 2 + shade;
        Cell done = BAKED.get(key);
        if (done != null) return done;
        int level = stage / VegetationClient.STEPS;
        float t = (stage % VegetationClient.STEPS) / (float) VegetationClient.STEPS;
        PlantShape.Press press = press(pose);
        var quads = new ArrayList<PlantShape.Quad>();
        for (var clump : PlantShape.clumps(species, variant, level))
            if (clump.rank >= t) PlantShape.clump(clump, press, quads);
        if (t > 0) for (var clump : PlantShape.clumps(species, variant, level + 1))
            if (clump.rank < t) PlantShape.clump(clump, press, quads);
        TextureAtlasSprite standing = sprite(), still = stillSprite();
        var solid = new ArrayList<BakedQuad>();
        var cutout = new ArrayList<BakedQuad>();
        float[] solidFeet = new float[quads.size() * 2], cutoutFeet = new float[quads.size() * 2];
        for (var q : quads) {
            var list = q.solid ? solid : cutout;
            float[] feet = q.solid ? solidFeet : cutoutFeet;
            feet[list.size() * 2] = q.footX;
            feet[list.size() * 2 + 1] = q.footZ;
            list.add(bake(q, q.sprite == PlantShape.STILL ? still : standing, shade));
        }
        Cell made = new Cell(List.copyOf(solid), java.util.Arrays.copyOf(solidFeet, solid.size() * 2),
                List.copyOf(cutout), java.util.Arrays.copyOf(cutoutFeet, cutout.size() * 2));
        Cell raced = BAKED.putIfAbsent(key, made);
        return raced != null ? raced : made;
    }

    static TextureAtlasSprite sprite() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SPRITE);
    }

    static TextureAtlasSprite stillSprite() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(STILL_SPRITE);
    }

    /** DefaultVertexFormat.BLOCK: position, colour (ABGR), uv, light (0: the chunk renderer's), normal. */
    private static BakedQuad bake(PlantShape.Quad q, TextureAtlasSprite sprite, int shade) {
        int[] data = new int[32];
        float fx = 0, fy = 0, fz = 0;
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            float nx = q.n[i * 3], ny = q.n[i * 3 + 1], nz = q.n[i * 3 + 2];
            fx += nx; fy += ny; fz += nz;
            data[o] = Float.floatToRawIntBits(q.p[i * 3]);
            data[o + 1] = Float.floatToRawIntBits(q.p[i * 3 + 1]);
            data[o + 2] = Float.floatToRawIntBits(q.p[i * 3 + 2]);
            // without a pack the brightness by the normal is baked (PlantShape); a pack lights by the normal itself
            float light = shade == 1 ? q.lit[i] : q.light[i];
            int r = channel(light * q.tone[0]), g = channel(light * q.tone[1]), b = channel(light * q.tone[2]);
            data[o + 3] = 0xFF000000 | b << 16 | g << 8 | r;
            data[o + 4] = Float.floatToRawIntBits(sprite.getU(PlantShape.inColumn(q.uv[i * 2], q.uv) * 16));
            data[o + 5] = Float.floatToRawIntBits(sprite.getV(q.uv[i * 2 + 1] * 16));
            data[o + 6] = 0;
            data[o + 7] = (((byte) (nx * 127)) & 0xFF) | (((byte) (ny * 127)) & 0xFF) << 8 | (((byte) (nz * 127)) & 0xFF) << 16;
        }
        return new BakedQuad(data, q.tinted ? 0 : -1, Direction.getNearest(fx, fy, fz), sprite, shade == 0, false);
    }

    private static int channel(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255)));
    }

    // -------------------------------------------------------------------------------------------- the rest

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return sprite(); }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
