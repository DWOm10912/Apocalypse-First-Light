package com.antaurora.apofirstlight.worldgen.terrain;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable single-column observations; contains no world, registry or mutable cache.
 * Every height (including surfaceY) is the first block position ABOVE its surface,
 * never the top block's own Y: solid at 63 -> topSolidSurfaceY=64, fluid at 62 ->
 * fluidSurfaceY=63. Use {@link TerrainHeights#surfaceAboveBlockY(int)} at an explicit
 * block-Y boundary. Absent height means unavailable, not zero or a negative sentinel.
 *
 * <p>V1 deliberately does not carry partial observations for UNKNOWN/INVALID results:
 * all optionals must be empty, surface/protection UNKNOWN and fluid NONE. In these
 * non-VALID results NONE is only the canonical placeholder, NOT proven fluid absence.
 * Consumers must check validity first; {@link #hasKnownNoFluid()} offers that guarded
 * predicate. This restriction prevents failed queries leaking reassuring clear flags.
 * VALID samples may omit individual heights the declared source does not provide.
 *
 * <p>Ice is distinct from liquid; a solid/ice surface may still have a separately
 * observed fluid below it. No universal height ordering, world bounds, placement
 * policy, fluid-ID classification or protection scanner is implied here.
 */
public record TerrainSample(
        TerrainValidity validity,
        TerrainSource source,
        OptionalInt surfaceY,
        OptionalInt topSolidSurfaceY,
        OptionalInt oceanFloorSurfaceY,
        OptionalInt fluidSurfaceY,
        FluidCategory fluid,
        Optional<ResourceLocation> fluidId,
        SurfaceType surfaceType,
        ProtectionKnowledge protection) {

    public TerrainSample {
        Objects.requireNonNull(validity, "validity");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(surfaceY, "surfaceY");
        Objects.requireNonNull(topSolidSurfaceY, "topSolidSurfaceY");
        Objects.requireNonNull(oceanFloorSurfaceY, "oceanFloorSurfaceY");
        Objects.requireNonNull(fluidSurfaceY, "fluidSurfaceY");
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(fluidId, "fluidId");
        Objects.requireNonNull(surfaceType, "surfaceType");
        Objects.requireNonNull(protection, "protection");

        if (validity != TerrainValidity.VALID
                && (surfaceY.isPresent() || topSolidSurfaceY.isPresent()
                || oceanFloorSurfaceY.isPresent() || fluidSurfaceY.isPresent()
                || fluidId.isPresent() || fluid != FluidCategory.NONE
                || surfaceType != SurfaceType.UNKNOWN || protection != ProtectionKnowledge.UNKNOWN)) {
            throw new IllegalArgumentException("Non-VALID samples cannot assert observations");
        }
        if (fluid == FluidCategory.NONE && (fluidId.isPresent() || fluidSurfaceY.isPresent())) {
            throw new IllegalArgumentException("NONE cannot carry a fluid ID or surface height");
        }
        if (validity == TerrainValidity.VALID && fluid != FluidCategory.NONE
                && (fluidId.isEmpty() || fluidSurfaceY.isEmpty())) {
            throw new IllegalArgumentException("Known fluid requires both ID and surface height");
        }
        if (surfaceType == SurfaceType.FLUID_SURFACE
                && (fluid == FluidCategory.NONE || surfaceY.isEmpty()
                || !surfaceY.equals(fluidSurfaceY))) {
            throw new IllegalArgumentException("Fluid surface must match the observed fluid height");
        }
        if (source == TerrainSource.NOISE_PRE_DECORATION && protection != ProtectionKnowledge.UNKNOWN) {
            throw new IllegalArgumentException("Noise terrain cannot establish current-world protection");
        }
    }

    /** Unknown query with no fabricated heights, fluid absence or protection evidence. */
    public static TerrainSample unknown(TerrainSource source) {
        return unavailable(TerrainValidity.UNKNOWN, source);
    }

    /** Explicit failed/no-result query, distinct from insufficient source coverage. */
    public static TerrainSample invalid(TerrainSource source) {
        return unavailable(TerrainValidity.INVALID, source);
    }

    private static TerrainSample unavailable(TerrainValidity validity, TerrainSource source) {
        return new TerrainSample(validity, source, OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.empty(), OptionalInt.empty(), FluidCategory.NONE, Optional.empty(),
                SurfaceType.UNKNOWN, ProtectionKnowledge.UNKNOWN);
    }

    /** False for UNKNOWN and INVALID, regardless of the canonical NONE placeholder. */
    public boolean hasKnownNoFluid() {
        return validity == TerrainValidity.VALID && fluid == FluidCategory.NONE;
    }
}
