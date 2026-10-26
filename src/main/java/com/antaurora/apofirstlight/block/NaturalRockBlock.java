package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.worldgen.underground.RockType;
import net.minecraft.world.level.block.Block;

/**
 * A natural rock of the geology (U3a, docs/worldgen/underground_u3_rocks_ores_design.md 2.1): what a deep world's
 * underground is made of after GeologyPass. It is rock to every system that knew only {@code minecraft:stone}
 * (Earthwork#rock: survival takes no hand to it, the pick cuts it in the open, Terrain Smooth smooths it), and names its
 * {@link RockType} (rock_types data: its stones, density and the pick's cut).
 */
public class NaturalRockBlock extends Block {
    public final RockType rock;

    public NaturalRockBlock(RockType rock, Properties properties) {
        super(properties);
        this.rock = rock;
    }
}
