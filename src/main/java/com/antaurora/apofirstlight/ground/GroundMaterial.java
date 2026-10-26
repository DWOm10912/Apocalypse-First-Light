package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.earthwork.BulkMaterials;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * What the ground at a block is made of (GroundView#materialAt; terrain rework R0, docs/worldgen/terrain_rework_v1_design.md
 * 6.2): for water's infiltration and runoff, building foundations, later the material physics (R4). Loose ground carries
 * its bulk material's angles of repose and excavation (degrees, BulkMaterials); rock and snow have none (NaN). Roads
 * (road drainage D1, docs/worldgen/road_drainage_v1_design.md 4): pavement is ASPHALT, CONCRETE or COMPACTED_GRAVEL (no
 * angles: not loose ground, the shovel and the earthwork leave it), graded turf its topsoil.
 */
public record GroundMaterial(String id, Kind kind, double repose, double excavation) {
    public enum Kind { NONE, SOIL, SAND, GRAVEL, CLAY, MUD, ROCK, RUBBLE, SNOW, ASPHALT, CONCRETE, COMPACTED_GRAVEL }

    public static final GroundMaterial NONE = new GroundMaterial("none", Kind.NONE, Double.NaN, Double.NaN);
    public static final GroundMaterial ROCK = new GroundMaterial("rock", Kind.ROCK, Double.NaN, Double.NaN);
    public static final GroundMaterial SNOW = new GroundMaterial("snow", Kind.SNOW, Double.NaN, Double.NaN);
    public static final GroundMaterial ASPHALT = new GroundMaterial("asphalt", Kind.ASPHALT, Double.NaN, Double.NaN);
    public static final GroundMaterial CONCRETE = new GroundMaterial("concrete", Kind.CONCRETE, Double.NaN, Double.NaN);
    public static final GroundMaterial COMPACTED_GRAVEL = new GroundMaterial("compacted_gravel", Kind.COMPACTED_GRAVEL, Double.NaN, Double.NaN);

    /** The material of natural block b: its bulk material, rock, snow, else NONE. */
    public static GroundMaterial of(Block b) {
        BulkMaterials.Table t = BulkMaterials.table();
        int i = t.of(b);
        if (i >= 0) {
            BulkMaterials.Material m = t.get(i);
            return new GroundMaterial(m.id(), kind(m.id()), m.repose(), m.excavation());
        }
        if (Earthwork.rock(b)) return ROCK;
        return b == Blocks.SNOW_BLOCK || b instanceof NaturalGroundBlock g && !g.loose() ? SNOW : NONE;   // AFL's snow (N1)
    }

    /** Bulk material id's ground (graded turf: "topsoil"), NONE when the table has no such material. */
    public static GroundMaterial bulk(String id) {
        BulkMaterials.Table t = BulkMaterials.table();
        int i = t.index(id);
        BulkMaterials.Material m = t.get(i);
        return m.id().equals(id) ? new GroundMaterial(m.id(), kind(m.id()), m.repose(), m.excavation()) : NONE;
    }

    private static Kind kind(String id) {
        return switch (id) {
            case "sand", "red_sand" -> Kind.SAND;
            case "gravel" -> Kind.GRAVEL;
            case "clay" -> Kind.CLAY;
            case "mud" -> Kind.MUD;
            case "rubble" -> Kind.RUBBLE;
            default -> Kind.SOIL;   // topsoil, backfill and any data-pack soil
        };
    }
}
