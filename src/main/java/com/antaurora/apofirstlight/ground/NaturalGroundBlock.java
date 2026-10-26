package com.antaurora.apofirstlight.ground;

import net.minecraft.world.level.block.Block;

/**
 * Natural ground N1 (docs/worldgen/natural_ground_n1_design.md 2, 6): AFL's own natural ground, drawn like the rocks
 * (CC0 photos at 128 texels per metre, LabPBR; tools/ground/build-natural-ground.mjs). Terrain only: no loot (the shovel
 * gives bulk material, Earthwork), the item in the creative tab only. Terrain Smooth slopes it and the earthwork edits it as
 * it does vanilla's soils.
 */
public class NaturalGroundBlock extends Block {
    /** Its bulk material (BulkMaterials id: what a shovel takes out of it), or null where a shovel takes none (snow). */
    public final String bulk;

    public NaturalGroundBlock(String bulk, Properties properties) {
        super(properties);
        this.bulk = bulk;
    }

    /** Loose ground a shovel digs (Earthwork#soil). */
    public boolean loose() {
        return bulk != null;
    }
}
