package com.antaurora.apofirstlight.world.biome;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.worldgen.geography.MacroBiomePolicy;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Seed-only immutable surface regions. No terrain/chunk queries or climate thresholds. */
public final class MainNationBiomeRegionPlan {
    private static final Map<Long, MainNationBiomeRegionPlan> CACHE = new LinkedHashMap<>(16, .75f, true);
    public static final int SAFETY_GAP = 128;
    private final long seed;
    private final MacroGeography geography;
    private final List<Pocket> pockets;
    private final int requestedCount;

    public enum Region { STARTUP_PLAINS, ADDITIONAL_PLAINS, DEFAULT_FALLOUT }

    public static synchronized MainNationBiomeRegionPlan forSeed(long seed) {
        var plan = CACHE.get(seed);
        if (plan == null) {
            plan = new MainNationBiomeRegionPlan(seed);
            CACHE.put(seed, plan);
            if (CACHE.size() > 16) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return plan;
    }

    private MainNationBiomeRegionPlan(long seed) {
        this.seed = seed;
        geography = MacroGeography.forSeed(seed);
        var random = new SplittableRandom(seed ^ 0x4D41494E504C414EL);
        int roll = random.nextInt(100);
        requestedCount = roll < 40 ? 0 : roll < 80 ? 1 : roll < 95 ? 2 : 3;
        var accepted = new ArrayList<Pocket>();
        int bound = (int) Math.ceil(geography.majorAxis() / 2 * 1.06);
        for (int slot = 0; slot < requestedCount; slot++) {
            for (int attempt = 0; attempt < 128; attempt++) {
                var candidate = new Pocket(random.nextInt(-bound, bound + 1),
                        random.nextInt(-bound, bound + 1), random.nextInt(96, 225),
                        random.nextInt(24, 49), random.nextDouble() * Math.PI * 2);
                double radius = candidate.maxRadius();
                if (Math.hypot(candidate.x(), candidate.z()) < 240 + radius + SAFETY_GAP) continue;
                var center = geography.sample(candidate.x(), candidate.z());
                // Mild near-coast preference, followed by unrestricted inland fallback.
                if (attempt < 32 && center.coastDistance() > radius + 1024) continue;
                if (accepted.stream().anyMatch(p -> Math.hypot(p.x() - candidate.x(), p.z() - candidate.z())
                        < p.maxRadius() + radius + SAFETY_GAP)) continue;
                if (!fitsMainland(candidate)) continue;
                accepted.add(candidate);
                break; // Exhaustion produces fewer pockets, never an invalid fallback.
            }
        }
        pockets = List.copyOf(accepted);
    }

    private boolean fitsMainland(Pocket pocket) {
        int r = pocket.maxRadius();
        // Conservative enclosing square plus coast margin keeps the whole star-shaped pocket inland.
        for (int dx = -r; ; dx = Math.min(r, dx + 32)) {
            for (int dz = -r; ; dz = Math.min(r, dz + 32)) {
                var sample = geography.sample(pocket.x() + dx, pocket.z() + dz);
                if (!mainlandLand(sample) || sample.coastDistance() < MacroGeography.COAST_WIDTH + 64) return false;
                if (dz == r) break;
            }
            if (dx == r) break;
        }
        return true;
    }

    private static boolean mainlandLand(MacroGeographySample sample) {
        return sample.nationId() == MacroGeographySample.NationId.MAIN_NATION
                && sample.landmassRole() == MacroGeographySample.LandmassRole.MAINLAND
                && sample.surfaceClass() == MacroGeographySample.SurfaceClass.LAND;
    }

    public List<Pocket> pockets() { return pockets; }
    public int requestedCount() { return requestedCount; }

    public Region regionAt(int x, int z) {
        return regionAt(x, z, geography.sample(x, z));
    }

    private Region regionAt(int x, int z, MacroGeographySample sample) {
        if (!mainlandLand(sample)) return Region.DEFAULT_FALLOUT;
        var startup = StartupPlainsEnclave.zoneAt(x, z, seed);
        if (startup == StartupPlainsEnclave.Zone.CORE_PLAINS
                || startup == StartupPlainsEnclave.Zone.FRINGE_PLAINS) return Region.STARTUP_PLAINS;
        for (var pocket : pockets) if (pocket.contains(x, z)) return Region.ADDITIONAL_PLAINS;
        return Region.DEFAULT_FALLOUT;
    }

    /** Shared final surface policy. Water/coast always win over the land region plan. */
    public ResourceKey<Biome> surfaceBiome(int x, int z, ResourceKey<Biome> original) {
        var sample = geography.sample(x, z);
        if (sample.isWater() || sample.surfaceClass() == MacroGeographySample.SurfaceClass.COAST)
            return MacroBiomePolicy.override(sample, original);
        if (sample.nationId() == MacroGeographySample.NationId.MAIN_NATION && sample.isLand())
            return regionAt(x, z, sample) == Region.DEFAULT_FALLOUT ? AflBiomes.FALLOUT_BARRENS : Biomes.PLAINS;
        return original;
    }

    /** Both biome filling and SurfaceSystem call this resolver on the same quart lattice. */
    public ResourceKey<Biome> biomeAt(int x, int y, int z, ResourceKey<Biome> original,
                                     DensityFunction preliminarySurface) {
        x = x >> 2 << 2;
        y = y >> 2 << 2;
        z = z >> 2 << 2;
        if (Biomes.DEEP_DARK.equals(original)) return AflBiomes.FALLOUT_BARRENS;
        if (AflVanillaBiomePolicy.isAllowedUndergroundBiome(original)) {
            var sample = geography.sample(x, z);
            boolean belowSurface = sample.isWater() ? sample.surfaceHeight() - y > 12
                    : preliminarySurface.compute(new DensityFunction.SinglePointContext(x, y + 12, z)) > .390625;
            if (belowSurface) return original;
        }
        return surfaceBiome(x, z, original);
    }

    public record Pocket(int x, int z, int radius, int amplitude, double phase) {
        public int maxRadius() { return radius + amplitude; }
        public double boundary(double angle) {
            // Low angular frequencies and positive radius guarantee a single connected star shape.
            return radius + amplitude * (.65 * Math.sin(3 * angle + phase)
                    + .35 * Math.sin(5 * angle - phase));
        }
        public boolean contains(int blockX, int blockZ) {
            double dx = (double) blockX - x, dz = (double) blockZ - z;
            return Math.hypot(dx, dz) <= boundary(Math.atan2(dz, dx));
        }
    }
}
