package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Which Terrain V2 plan a noise router runs on (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md). The plan density
 * functions (plan_height, plan_stability, plan_terrain) carry it as an optional {@code "profile"} field; without the
 * field they are r1, so the default overworld (data/minecraft/worldgen/noise_settings/overworld.json) is unchanged.
 * <ul>
 *   <li>{@link #R1}: the frozen r1 plan on the r1 national map (TerrainPlanV2, 1408 x 1408 cells), lock profile
 *   {@code afl_terrain_v2};</li>
 *   <li>{@link #FEDERAL}: the experimental six-state plan on the C1 federal map (FederalTerrainPlan, 1760 x 1760 cells),
 *   only in worlds made with the "AFL Federation (experimental)" world preset; lock profile
 *   {@code afl_federal_experimental}.</li>
 * </ul>
 * A world records its profile in the version lock; a world of one profile never opens with the other's rules.
 */
public enum TerrainProfile implements StringRepresentable {
    R1("r1", "afl_terrain_v2"),
    FEDERAL("federal", "afl_federal_experimental");

    public static final Codec<TerrainProfile> CODEC = StringRepresentable.fromEnum(TerrainProfile::values);

    private final String id;
    /** The profile the version lock records (WorldgenFingerprint#profile). */
    public final String lockProfile;

    TerrainProfile(String id, String lockProfile) {
        this.id = id;
        this.lockProfile = lockProfile;
    }

    @Override
    public String getSerializedName() { return id; }

    /** The plan version this profile builds (the cache file name and the lock's plan label). */
    public String planVersion() { return this == R1 ? TerrainPlanV2.VERSION : FederalTerrainPlan.VERSION; }

    public int gridN() { return this == R1 ? TerrainPlanV2.N : FederalTerrainPlan.GRID_N; }

    public double gridOrigin() { return this == R1 ? TerrainPlanV2.ORIGIN : FederalTerrainPlan.GRID_ORIGIN; }

    /** Builds this profile's plan for a seed (about 10 s for r1, more for the federal grid; world load only). */
    public TerrainPlanV2 build(long seed) { return this == R1 ? new TerrainPlanV2(seed) : new FederalTerrainPlan(seed); }
}
