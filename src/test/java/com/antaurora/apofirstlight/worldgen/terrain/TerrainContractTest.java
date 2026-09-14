package com.antaurora.apofirstlight.worldgen.terrain;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;

/** Standalone contract tests, following projectionMathTest; no Minecraft world bootstrap. */
public final class TerrainContractTest {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError(message + ": unexpected exception", failure);
        }
        throw new AssertionError(message + ": accepted");
    }

    // Mutable input exists only in tests, not in the production contract.
    private static final class Input {
        TerrainValidity validity = TerrainValidity.VALID;
        TerrainSource source = TerrainSource.CURRENT_POST_FEATURE;
        OptionalInt surface = OptionalInt.of(64);
        OptionalInt solid = OptionalInt.of(64);
        OptionalInt floor = OptionalInt.empty();
        OptionalInt fluidHeight = OptionalInt.empty();
        FluidCategory fluid = FluidCategory.NONE;
        Optional<ResourceLocation> fluidId = Optional.empty();
        SurfaceType type = SurfaceType.SOLID;
        ProtectionKnowledge protection = ProtectionKnowledge.UNKNOWN;

        TerrainSample sample() {
            return new TerrainSample(validity, source, surface, solid, floor, fluidHeight,
                    fluid, fluidId, type, protection);
        }
    }

    private static Input liquid(FluidCategory category) {
        Input input = new Input();
        input.fluid = category;
        // Synthetic IDs: no real industrial registry mappings are introduced by the test.
        input.fluidId = Optional.of(new ResourceLocation("terrain_contract_test", category.name().toLowerCase(java.util.Locale.ROOT)));
        input.fluidHeight = OptionalInt.of(TerrainHeights.surfaceAboveBlockY(62));
        input.surface = input.fluidHeight;
        input.solid = OptionalInt.of(51);
        input.type = SurfaceType.FLUID_SURFACE;
        return input;
    }

    private static void heights() {
        int[][] cases = {{63,64},{62,63},{-60,-59},{-64,-63},{-1,0},{0,1},
                {1,2},{318,319},{319,320},{511,512},
                {Integer.MIN_VALUE,Integer.MIN_VALUE+1},{Integer.MAX_VALUE-1,Integer.MAX_VALUE}};
        for (int[] pair : cases) {
            int result = TerrainHeights.surfaceAboveBlockY(pair[0]);
            require(result == pair[1], "height conversion " + pair[0]);
            Input input = new Input();
            input.surface = OptionalInt.of(result);
            input.solid = OptionalInt.of(result);
            TerrainSample sample = input.sample();
            require(sample.topSolidSurfaceY().getAsInt() == pair[1], "stored solid surface");
            require(sample.surfaceY().getAsInt() == pair[1], "stored general surface");
            require(sample.oceanFloorSurfaceY().isEmpty(), "no fabricated ocean floor");
        }
        rejects(ArithmeticException.class, () -> TerrainHeights.surfaceAboveBlockY(Integer.MAX_VALUE), "overflow");
        Input wet = liquid(FluidCategory.WATER);
        require(wet.sample().fluidSurfaceY().getAsInt() == 63, "fluid top 62 -> surface 63");
        wet.floor = OptionalInt.of(TerrainHeights.surfaceAboveBlockY(-60));
        require(wet.sample().oceanFloorSurfaceY().getAsInt() == -59, "ocean floor uses same semantics");
    }

    private static void unknownAndSources() {
        require(Arrays.equals(TerrainSource.values(), new TerrainSource[]{TerrainSource.NOISE_PRE_DECORATION,
                TerrainSource.STRUCTURE_PLANNING, TerrainSource.WRITABLE_PRECOMMIT,
                TerrainSource.CURRENT_POST_FEATURE}), "four explicit sources, no AUTO");
        require(Arrays.equals(TerrainValidity.values(), new TerrainValidity[]{TerrainValidity.VALID,
                TerrainValidity.UNKNOWN, TerrainValidity.INVALID}), "validity set");
        require(Arrays.equals(FluidCategory.values(), new FluidCategory[]{FluidCategory.NONE,
                FluidCategory.WATER, FluidCategory.INDUSTRIAL_WASTE, FluidCategory.OTHER_FLUID}), "exactly four fluid categories");
        require(SurfaceType.values().length == 4, "minimal surface categories");
        for (TerrainSource source : TerrainSource.values()) {
            TerrainSample unknown = TerrainSample.unknown(source);
            TerrainSample invalid = TerrainSample.invalid(source);
            require(unknown.validity() == TerrainValidity.UNKNOWN, "unknown stays unknown");
            require(invalid.validity() == TerrainValidity.INVALID, "invalid stays invalid");
            require(!unknown.equals(invalid), "unknown != invalid");
            require(unknown.equals(TerrainSample.unknown(source)), "record value equality");
            for (TerrainSample sample : new TerrainSample[]{unknown, invalid}) {
                require(sample.source() == source, "source retained without fallback");
                require(sample.surfaceY().isEmpty() && sample.topSolidSurfaceY().isEmpty()
                        && sample.oceanFloorSurfaceY().isEmpty() && sample.fluidSurfaceY().isEmpty(), "no height sentinels");
                require(sample.fluid() == FluidCategory.NONE && sample.fluidId().isEmpty(), "canonical placeholder");
                require(!sample.hasKnownNoFluid(), "unavailable is NOT known dry");
                require(sample.protection() == ProtectionKnowledge.UNKNOWN, "no invented protection clearance");
                require(sample.surfaceType() == SurfaceType.UNKNOWN, "no invented surface");
            }
            Input valid = new Input(); valid.source = source;
            require(valid.sample().hasKnownNoFluid(), "explicit valid dry classification");
            require(!valid.sample().equals(unknown), "VALID != UNKNOWN");
        }
        // Test-only lambda demonstrates the signature; there is no production adapter.
        int[] calls = {0};
        TerrainQuery query = (x, z, source) -> {
            require(x == -17 && z == 0, "coordinates passed without remapping");
            calls[0]++;
            return TerrainSample.unknown(source);
        };
        for (TerrainSource source : TerrainSource.values())
            require(query.sample(-17, 0, source).source() == source, "interface retains request provenance");
        require(calls[0] == 4, "one invocation per requested source, no fallback");
    }

    private static void invariants() {
        Input noneId = new Input(); noneId.fluidId = Optional.of(new ResourceLocation("test", "fluid"));
        rejects(IllegalArgumentException.class, noneId::sample, "NONE with ID");
        Input noneHeight = new Input(); noneHeight.fluidHeight = OptionalInt.of(0);
        rejects(IllegalArgumentException.class, noneHeight::sample, "NONE with fluid height including zero");
        for (FluidCategory category : new FluidCategory[]{FluidCategory.WATER, FluidCategory.INDUSTRIAL_WASTE, FluidCategory.OTHER_FLUID}) {
            TerrainSample wet = liquid(category).sample();
            require(wet.fluid() == category && !wet.hasKnownNoFluid(), "known fluid " + category);
            Input missingId = liquid(category); missingId.fluidId = Optional.empty();
            rejects(IllegalArgumentException.class, missingId::sample, "missing ID " + category);
            Input missingY = liquid(category); missingY.fluidHeight = OptionalInt.empty();
            rejects(IllegalArgumentException.class, missingY::sample, "missing fluid surface " + category);
        }
        Input drySurface = new Input(); drySurface.type = SurfaceType.FLUID_SURFACE;
        rejects(IllegalArgumentException.class, drySurface::sample, "fluid surface without fluid");
        Input mismatched = liquid(FluidCategory.WATER); mismatched.surface = OptionalInt.of(64);
        rejects(IllegalArgumentException.class, mismatched::sample, "different visible fluid heights");
        Input absent = liquid(FluidCategory.WATER); absent.surface = OptionalInt.empty();
        rejects(IllegalArgumentException.class, absent::sample, "visible fluid surface missing");
        for (SurfaceType type : new SurfaceType[]{SurfaceType.SOLID, SurfaceType.ICE}) {
            Input dry = new Input(); dry.type = type;
            require(dry.sample().surfaceType() == type && dry.sample().hasKnownNoFluid(), "dry solid/ice");
            Input coveredWater = liquid(FluidCategory.WATER); coveredWater.type = type; coveredWater.surface = OptionalInt.of(65);
            require(coveredWater.sample().fluid() == FluidCategory.WATER, "ice/solid can cover separate fluid");
        }
        for (ProtectionKnowledge protection : new ProtectionKnowledge[]{ProtectionKnowledge.KNOWN_CLEAR, ProtectionKnowledge.KNOWN_PROTECTED}) {
            Input live = new Input(); live.protection = protection;
            require(live.sample().protection() == protection, "live protection can be represented");
            live.source = TerrainSource.NOISE_PRE_DECORATION;
            rejects(IllegalArgumentException.class, live::sample, "noise cannot assert current protection");
        }
        // Every populated observation is forbidden on a non-VALID V1 sample.
        for (TerrainValidity validity : new TerrainValidity[]{TerrainValidity.UNKNOWN, TerrainValidity.INVALID}) {
            for (int field = 0; field < 8; field++) {
                Input input = new Input(); input.validity = validity;
                input.surface = OptionalInt.empty(); input.solid = OptionalInt.empty(); input.type = SurfaceType.UNKNOWN;
                switch (field) {
                    case 0 -> input.surface = OptionalInt.of(0);
                    case 1 -> input.solid = OptionalInt.of(-1);
                    case 2 -> input.floor = OptionalInt.of(64);
                    case 3 -> input.fluidHeight = OptionalInt.of(63);
                    case 4 -> input.fluid = FluidCategory.WATER;
                    case 5 -> input.fluidId = Optional.of(new ResourceLocation("test", "fluid"));
                    case 6 -> input.type = SurfaceType.SOLID;
                    case 7 -> input.protection = ProtectionKnowledge.KNOWN_CLEAR;
                    default -> throw new AssertionError();
                }
                rejects(IllegalArgumentException.class, input::sample, "non-VALID observation " + field);
            }
        }
    }

    private static void nullSafety() {
        for (int field = 0; field < 10; field++) {
            Input input = new Input();
            switch (field) {
                case 0 -> input.validity = null;
                case 1 -> input.source = null;
                case 2 -> input.surface = null;
                case 3 -> input.solid = null;
                case 4 -> input.floor = null;
                case 5 -> input.fluidHeight = null;
                case 6 -> input.fluid = null;
                case 7 -> input.fluidId = null;
                case 8 -> input.type = null;
                case 9 -> input.protection = null;
                default -> throw new AssertionError();
            }
            rejects(NullPointerException.class, input::sample, "null field " + field);
        }
        rejects(NullPointerException.class, () -> TerrainSample.unknown(null), "null unknown source");
        rejects(NullPointerException.class, () -> TerrainSample.invalid(null), "null invalid source");
    }

    public static void main(String[] args) throws Exception {
        heights();
        unknownAndSources();
        invariants();
        nullSafety();
        System.out.println("PASS terrain contract checks=" + checks + "; no worldgen adapters or world bootstrap");
        com.antaurora.apofirstlight.worldgen.core.CoreContractTest.main(args);
        com.antaurora.apofirstlight.worldgen.spatial.BoundsContractTest.main(args);
        com.antaurora.apofirstlight.worldgen.structure.StructureContractTest.main(args);
        com.antaurora.apofirstlight.worldgen.spatial.ClaimContractTest.main(args);
        com.antaurora.apofirstlight.worldgen.spatial.IndexProfileContractTest.main(args);
        com.antaurora.apofirstlight.worldgen.profile.CrossSystemClaimVersionRegressionTest.main(args);
    }
}
