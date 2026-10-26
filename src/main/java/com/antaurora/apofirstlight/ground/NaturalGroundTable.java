package com.antaurora.apofirstlight.ground;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * N1c (docs/worldgen/natural_ground_n1_design.md 3, 12): which AFL block every vanilla natural ground block of the
 * generation becomes, as block state strings (pure Java: the offline check NaturalGroundCheck reads it without the
 * game's registries; NaturalGroundPass parses it once).
 * <ul>
 *   <li>grass, podzol, mycelium, moss block, dirt, coarse and rooted dirt: one topsoil, the top in {@code cover};</li>
 *   <li>mud, sand, red sand, clay: AFL's own; snow block and powder snow: snow (no sinking);</li>
 *   <li>gravel: angular, round where water is over it ({@link #GRAVEL_WET}: river and lake beds, beaches);</li>
 *   <li>sandstone, red sandstone, calcite: the rocks (sandstone, red sandstone, limestone);</li>
 *   <li>badlands terracotta as real beds: red and orange red sandstone, white and yellow sandstone, the rest shale.</li>
 * </ul>
 * {@link #KEPT} is what the generation lays and N1 leaves: stone and deepslate (the deep world's GeologyPass makes them
 * rock; their texture is AFL's), vanilla rock blobs, ice (with B's physical water), water, lava, bedrock.
 */
public final class NaturalGroundTable {
    private NaturalGroundTable() {}

    static final String AFL = "apocalypse_firstlight:";
    /** Gravel under water (the round bed gravel; {@code minecraft:gravel} elsewhere is angular). */
    public static final String GRAVEL_WET = AFL + "gravel[kind=round]";

    /** Vanilla block id to the AFL block state, in a fixed order. */
    public static final Map<String, String> SWAP = table();

    private static Map<String, String> table() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("minecraft:grass_block", AFL + "topsoil[cover=grass]");
        m.put("minecraft:podzol", AFL + "topsoil[cover=litter]");
        m.put("minecraft:mycelium", AFL + "topsoil[cover=litter]");
        m.put("minecraft:moss_block", AFL + "topsoil[cover=moss]");
        m.put("minecraft:dirt", AFL + "topsoil[cover=bare]");
        m.put("minecraft:coarse_dirt", AFL + "topsoil[cover=bare]");
        m.put("minecraft:rooted_dirt", AFL + "topsoil[cover=bare]");
        m.put("minecraft:mud", AFL + "mud");
        m.put("minecraft:sand", AFL + "sand");
        m.put("minecraft:red_sand", AFL + "red_sand");
        m.put("minecraft:gravel", AFL + "gravel[kind=angular]");
        m.put("minecraft:clay", AFL + "clay");
        m.put("minecraft:snow_block", AFL + "snow");
        m.put("minecraft:powder_snow", AFL + "snow");
        m.put("minecraft:sandstone", AFL + "sandstone");
        m.put("minecraft:red_sandstone", AFL + "red_sandstone");
        m.put("minecraft:calcite", AFL + "limestone");
        for (String c : List.of("red", "orange")) m.put("minecraft:" + c + "_terracotta", AFL + "red_sandstone");
        for (String c : List.of("white", "yellow")) m.put("minecraft:" + c + "_terracotta", AFL + "sandstone");
        m.put("minecraft:terracotta", AFL + "shale");
        for (String c : List.of("brown", "light_gray", "gray", "black", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"))
            m.put("minecraft:" + c + "_terracotta", AFL + "shale");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** Laid by the generation and left as it is in N1 (class comment). */
    public static final List<String> KEPT = List.of("minecraft:stone", "minecraft:deepslate", "minecraft:bedrock", "minecraft:water",
            "minecraft:lava", "minecraft:air", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:snow",
            "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:magma_block",
            "minecraft:obsidian", "minecraft:amethyst_block", "minecraft:budding_amethyst", "minecraft:smooth_basalt");

    /** The block id of a state string ({@code ns:name[props]} to {@code ns:name}). */
    public static String id(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }
}
