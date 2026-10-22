package com.antaurora.apofirstlight.worldgen.underground;

/**
 * The rocks and loose ground the U1 geology answers with (docs/worldgen/underground_geology_caves_v0_design.md 10.1).
 * C only names them: the blocks, hardness, tools and drops belong to E's rock_types data (not built yet, E R1b
 * a9c80b91), so {@link #id} is the proposed rock_types id and nothing here is placed in a world.
 * <p>
 * karst: how soluble (0 none, 1 limestone); anchor: whether a rope anchor holds in it (competent rock);
 * soil: loose ground above the rock (E's earthwork materials, mapped in the design 19.4).
 */
public enum RockType {
    GRANITE("granite", 0, true, false, 0xB9A79A),
    GNEISS("gneiss", 0, true, false, 0x8F8780),
    SCHIST("schist", 0, true, false, 0x7C8279),
    SANDSTONE("sandstone", 0, true, false, 0xC98E5C),
    LIMESTONE("limestone", 1.0, true, false, 0xC9C6B4),
    DOLOMITE("dolomite", 0.6, true, false, 0xB0AE9E),
    SHALE("shale", 0, false, false, 0x5F6266),
    COAL_SEAM("coal_seam", 0, false, false, 0x2A2A2C),
    ROCK_SALT("rock_salt", 0, false, false, 0xE6E1E8),
    MARL("marl", 0.2, false, false, 0xD8CFA8),
    TILL("till", 0, false, true, 0x8B7D5E),
    LOESS("loess", 0, false, true, 0xC4AE7E),
    ALLUVIUM("alluvium", 0, false, true, 0x7A6A50),
    LAKE_CLAY("lake_clay", 0, false, true, 0x8C8A80),
    SAND("sand", 0, false, true, 0xE0CF9C),
    RED_SOIL("red_soil", 0, false, true, 0xA65A3C),
    SCREE("scree", 0, false, true, 0x9A948C);

    public final String id;
    public final double karst;
    public final boolean anchor, soil;
    /** Colour on the offline maps and sections. */
    public final int rgb;

    RockType(String id, double karst, boolean anchor, boolean soil, int rgb) {
        this.id = id;
        this.karst = karst;
        this.anchor = anchor;
        this.soil = soil;
        this.rgb = rgb;
    }

    public boolean hard() { return this == GRANITE || this == GNEISS || this == SCHIST || this == SANDSTONE; }
}
