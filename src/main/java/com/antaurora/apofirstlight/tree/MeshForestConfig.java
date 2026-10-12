package com.antaurora.apofirstlight.tree;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mesh Trees P4 (docs/rendering/mesh_trees_p4_formal.md, O): the natural mesh forest switch. A SERVER config (per world,
 * {@code serverconfig/apocalypse_firstlight-trees.toml}): world generation is the server's, a client never decides it.
 * Read when the server starts, before the biome modifiers run. Off: chunks generated from then on get the biomes' own
 * vanilla tree features again; trees already in the world stay, draw, collide and can be felled as before.
 */
public final class MeshForestConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("mesh_forest");
        ENABLED = b.comment("Natural mesh trees (white oak, eastern white pine, northern red oak, shagbark hickory, red maple, eastern hemlock)",
                        "in the AFL Terrain V2 overworld biomes, in place of their vanilla tree features. Affects chunks generated after the",
                        "server starts; trees already in the world are kept either way. Read when the server starts.")
                .define("meshForestEnabled", true);
        b.pop();
        SPEC = b.build();
    }

    private MeshForestConfig() {}
}
