package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The placeholder building of every kind until its own kit exists (Town buildings V1, docs/worldgen/town_buildings_v1.md):
 * a massing box of AFL blocks on the site's footprint, no detail. Walls of one facade material by kind, STOREY blocks a
 * storey, a flat roof_tpo roof, a sealed concrete floor over a concrete footing, the inside empty, one doorway in the
 * middle of the front. Not a design: it shows where and how big each building stands.
 */
public final class WhiteBoxKit implements BuildingKit {
    /** A storey's height, blocks (T3). */
    public static final int STOREY = 4;
    /** The doorway's width and height, blocks. */
    static final int DOOR_W = 2, DOOR_H = 3;

    private final String wall;

    /** wall: the facade block's id in this mod. */
    public WhiteBoxKit(String wall) { this.wall = wall; }

    @Override
    public int height(TownBuildings.Site site) { return TownBuildings.KINDS.get(site.kind()).storeys() * STOREY + 1; }

    @Override
    public void build(TownBuildings.Site site, BuildingCanvas c) {
        BlockState walls = block(wall), roof = block("roof_tpo"), floor = block("sealed_concrete_floor"), footing = block("reinforced_concrete"),
                air = Blocks.AIR.defaultBlockState();
        int top = c.height - 1, u1 = c.width - 1, w1 = c.depth - 1;
        c.fill(0, -2, 0, u1, -2, w1, footing);
        c.fill(0, -1, 0, u1, -1, w1, floor);
        c.fill(0, 0, 0, u1, top - 1, w1, air);
        c.fill(0, 0, 0, u1, top - 1, 0, walls);
        c.fill(0, 0, w1, u1, top - 1, w1, walls);
        c.fill(0, 0, 0, 0, top - 1, w1, walls);
        c.fill(u1, 0, 0, u1, top - 1, w1, walls);
        c.fill(0, top, 0, u1, top, w1, roof);
        int d0 = (c.width - DOOR_W) / 2;
        c.fill(d0, 0, 0, d0 + DOOR_W - 1, DOOR_H - 1, 0, air);
    }

    /** An AFL block by id (reinforced_concrete when missing: never a vanilla stand-in). */
    static BlockState block(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, id));
        if (b == null || b == Blocks.AIR) b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "reinforced_concrete"));
        return b.defaultBlockState();
    }
}
