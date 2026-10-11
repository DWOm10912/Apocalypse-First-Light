package com.antaurora.apofirstlight.terrainsmooth;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Terrain Smooth V1 switch (docs/worldgen/terrain_smooth_v1.md): a SERVER config, so it belongs to the world and the
 * server decides. It is read once when the server starts and sent to every client (TerrainSmoothStatePacket); clients
 * have no switch of their own, so client and server never disagree about collision.
 */
public final class TerrainSmoothConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("terrain_smooth");
        ENABLED = b.comment("Natural surface slopes (gentle two-block slopes, about 27 degrees) on grass, dirt, coarse dirt, sand, gravel",
                        "and stone in the overworld, drawn and collided as slopes. Rendering and collision only: no block or chunk data",
                        "is written, so false restores the vanilla cubes of the same world. Read when the server starts.")
                .define("terrainSmoothEnabled", true);
        b.pop();
        SPEC = b.build();
    }

    private TerrainSmoothConfig() {}
}
