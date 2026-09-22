package com.antaurora.apofirstlight.world.biome;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Minimal A/B/C policy check, a single seed; no Forge launch, level or chunks. */
public final class MainNationBiomeRegionPlanTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        // Same isolated registry initialization used by the existing headless contract runners.
        var bootstrap = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        long seed = -4332662446239654818L;
        var plan = MainNationBiomeRegionPlan.forSeed(seed);
        var geography = MacroGeography.forSeed(seed);
        check(plan == MainNationBiomeRegionPlan.forSeed(seed), "cached immutable plan");
        var constructor = MainNationBiomeRegionPlan.class.getDeclaredConstructor(long.class);
        constructor.setAccessible(true);
        check(plan.pockets().equals(constructor.newInstance(seed).pockets()), "same seed reconstruction");
        check(plan.regionAt(0, 0) == MainNationBiomeRegionPlan.Region.STARTUP_PLAINS, "startup exists");
        check(plan.pockets().size() <= 3 && plan.pockets().size() <= plan.requestedCount(), "0..3 pockets");
        for (var p : plan.pockets()) {
            check(p.radius() >= 96 && p.radius() <= 224 && p.amplitude() >= 24 && p.amplitude() <= 48, "scale");
            check(Math.hypot(p.x(), p.z()) >= 240 + p.maxRadius() + MainNationBiomeRegionPlan.SAFETY_GAP, "startup gap");
            check(plan.regionAt(p.x(), p.z()) == MainNationBiomeRegionPlan.Region.ADDITIONAL_PLAINS, "pocket center");
            for (var q : plan.pockets()) if (p != q)
                check(Math.hypot(p.x() - q.x(), p.z() - q.z()) >= p.maxRadius() + q.maxRadius()
                        + MainNationBiomeRegionPlan.SAFETY_GAP, "pocket gap");
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4, radius = p.boundary(angle) - 8;
                int x = p.x() + (int) (Math.cos(angle) * radius), z = p.z() + (int) (Math.sin(angle) * radius);
                check(plan.regionAt(x, z) == MainNationBiomeRegionPlan.Region.ADDITIONAL_PLAINS, "connected inland boundary");
            }
        }
        for (var island : geography.islands()) if (island.role() == MacroGeographySample.LandmassRole.SATELLITE_ISLAND)
            check(plan.surfaceBiome((int)island.x(), (int)island.z(), Biomes.PLAINS).equals(AflBiomes.FALLOUT_BARRENS), "satellite fallout");
        boolean coast = false, ocean = false, deep = false;
        // One short mainland-to-sea transect, not a multi-seed/worldgen matrix.
        for (int x = 4000; x <= 10000; x += 16) {
            var s = geography.sample(x, 0);
            var result = plan.surfaceBiome(x, 0, Biomes.PLAINS);
            if (s.isWater()) {
                check(result.equals(s.surfaceHeight() < 32 ? Biomes.DEEP_OCEAN : Biomes.OCEAN), "water precedence");
                deep |= s.surfaceHeight() < 32; ocean |= s.surfaceHeight() >= 32;
            } else if (s.surfaceClass() == MacroGeographySample.SurfaceClass.COAST) {
                check(result.equals(Biomes.BEACH), "coast precedence"); coast = true;
            } else {
                check(result.equals(plan.surfaceBiome(x, 0, Biomes.OCEAN)), "land ignores climate/marine original");
            }
        }
        check(coast && ocean && deep, "all marine cases reached");
        DensityFunction below = new ConstantDensity(1), above = new ConstantDensity(0);
        check(plan.biomeAt(0, -32, 0, Biomes.DEEP_DARK, below).equals(AflBiomes.FALLOUT_BARRENS), "deep dark fallback");
        check(AflVanillaBiomePolicy.isDisabled(Biomes.DEEP_DARK), "deep dark candidate filter");
        for (var cave : java.util.List.of(Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES)) {
            check(!AflVanillaBiomePolicy.isDisabled(cave), "cave candidate retained");
            check(plan.biomeAt(0, -32, 0, cave, below).equals(cave), "underground preserved");
            check(plan.biomeAt(0, 80, 0, cave, above).equals(Biomes.PLAINS), "surfaced cave follows planner");
        }
        System.out.println("Biome Region A/B/C PASS: seed=" + seed + " requested=" + plan.requestedCount()
                + " actual=" + plan.pockets().size() + " checks=" + checks + " pockets=" + plan.pockets());
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private record ConstantDensity(double value) implements DensityFunction.SimpleFunction {
        @Override public double compute(FunctionContext context) { return value; }
        @Override public double minValue() { return value; }
        @Override public double maxValue() { return value; }
        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("test fixture only");
        }
    }
}
