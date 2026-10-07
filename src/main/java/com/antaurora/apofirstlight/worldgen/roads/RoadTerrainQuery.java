package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.terrain.FluidCategory;
import com.antaurora.apofirstlight.worldgen.terrain.ProtectionKnowledge;
import com.antaurora.apofirstlight.worldgen.terrain.SurfaceType;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainQuery;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainSample;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainSource;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainValidity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Invocation-local, bounded noise-column adapter. It never reads/loads a world chunk,
 * sees placed buildings or proves that a site is unprotected. Heights are the first
 * block position above a surface, matching {@link TerrainSample} and building G.
 */
public final class RoadTerrainQuery implements TerrainQuery {
    public static final int MAX_COLUMNS = 8192;
    private static final int MAX_SUPPORT_SCAN = 64;
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final int sampleBudget;
    private final Map<Long, TerrainSample> cache = new HashMap<>();
    private int failedColumns;
    private int budgetRefusals;
    private String lastFailure = "none";

    public RoadTerrainQuery(ServerLevel level, int sampleBudget) {
        this.level = Objects.requireNonNull(level);
        if (sampleBudget < 1 || sampleBudget > MAX_COLUMNS)
            throw new IllegalArgumentException("Road terrain budget must be 1.." + MAX_COLUMNS);
        this.sampleBudget = sampleBudget;
        this.generator = level.getChunkSource().getGenerator();
        this.randomState = level.getChunkSource().randomState();
    }

    @Override
    public TerrainSample sample(int x, int z, TerrainSource source) {
        Objects.requireNonNull(source);
        if (source != TerrainSource.NOISE_PRE_DECORATION) return TerrainSample.unknown(source);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        TerrainSample previous = cache.get(key);
        if (previous != null) return previous;
        if (cache.size() >= sampleBudget) {
            budgetRefusals++;
            return TerrainSample.unknown(source);
        }
        TerrainSample result;
        try {
            result = readColumn(x, z);
        } catch (RuntimeException exception) {
            failedColumns++;
            lastFailure = exception.getClass().getSimpleName();
            result = TerrainSample.invalid(source);
        }
        cache.put(key, result);
        return result;
    }

    private TerrainSample readColumn(int x, int z) {
        int surfaceY = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                level, randomState);
        if (surfaceY <= level.getMinBuildHeight() || surfaceY > level.getMaxBuildHeight())
            return TerrainSample.unknown(TerrainSource.NOISE_PRE_DECORATION);
        NoiseColumn column = generator.getBaseColumn(x, z, level, randomState);
        BlockState top = column.getBlock(surfaceY - 1);
        if (top.isAir()) return TerrainSample.unknown(TerrainSource.NOISE_PRE_DECORATION);
        OptionalInt solid = OptionalInt.empty();
        OptionalInt fluidY = OptionalInt.empty();
        Optional<ResourceLocation> fluidId = Optional.empty();
        FluidCategory fluid = FluidCategory.NONE;
        int bottom = Math.max(level.getMinBuildHeight(), surfaceY - MAX_SUPPORT_SCAN);
        for (int y = surfaceY - 1; y >= bottom; y--) {
            BlockState state = column.getBlock(y);
            var observedFluid = state.getFluidState();
            if (!observedFluid.isEmpty()) {
                if (fluidY.isEmpty()) {
                    fluidY = OptionalInt.of(y + 1);
                    fluidId = Optional.of(BuiltInRegistries.FLUID.getKey(observedFluid.getType()));
                    fluid = observedFluid.is(FluidTags.WATER) ? FluidCategory.WATER : FluidCategory.OTHER_FLUID;
                }
                continue;
            }
            if (!state.isAir() && !state.canBeReplaced()
                    && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, new BlockPos(x, y, z)).isEmpty()) {
                solid = OptionalInt.of(y + 1);
                break;
            }
        }
        SurfaceType type = !top.getFluidState().isEmpty() ? SurfaceType.FLUID_SURFACE
                : isIce(top) ? SurfaceType.ICE : solid.orElse(Integer.MIN_VALUE) == surfaceY
                ? SurfaceType.SOLID : SurfaceType.UNKNOWN;
        // The bounded column scan does not prove the ocean floor if support was not reached.
        return new TerrainSample(TerrainValidity.VALID, TerrainSource.NOISE_PRE_DECORATION,
                OptionalInt.of(surfaceY), solid, solid, fluidY, fluid, fluidId, type,
                ProtectionKnowledge.UNKNOWN);
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
    }

    public int sampledColumns() { return cache.size(); }
    public int failedColumns() { return failedColumns; }
    public int budgetRefusals() { return budgetRefusals; }
    public String lastFailure() { return lastFailure; }
}
