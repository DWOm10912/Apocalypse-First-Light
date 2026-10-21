package com.antaurora.apofirstlight.map;

import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalEcologyBiomes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The map's colours (AFL Map V1, docs/ui/afl_map_v1_plan.md 4) and its biome table: a biome is a small index into one
 * table shared by the client's ground records and the raster. The ecology biomes keep their EcologyBiomes /
 * FederalEcologyBiomes index (0..20) and colour; fallout barrens 21; any other biome (vanilla, another mod) is given the
 * next free index the first time it is seen, with the colour it brings (its grass colour), for this session.
 */
public final class MapStyle {
    private MapStyle() {}

    public static final int WATER = 0x6FA3CF, STREET = 0xF7F4EC, WALK = 0xD9D3C7, CURB = 0x8A857C, HWY = 0xF0B54A, HWY_EDGE = 0x8E6420,
            ROAD = 0xFBF8F0, ROAD_EDGE = 0x8A857C, BUILDING = 0xCDC2B0, BUILDING_EDGE = 0x8A806F, APRON = 0xE4DFD4, FOG = 0x1D2228,
            FOG_GRID = 0x262C33, PAPER = 0xEDE8DA, DRIVE = 0xE3DCCB, DRIVE_EDGE = 0x9C927F;
    /** The ground colours lean this far toward paper: a printed map, not a satellite view. */
    public static final double PAPER_MIX = 0.18;

    public static final int ECOLOGY = EcologyBiomes.IDS.length + FederalEcologyBiomes.IDS.length, FALLOUT = ECOLOGY, MAX = 255;
    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static volatile int[] colours;
    private static volatile String[] ids;

    static {
        int[] c = new int[MAX];
        String[] n = new String[MAX];
        for (int i = 0; i < EcologyBiomes.IDS.length; i++) { c[i] = EcologyBiomes.COLOURS[i]; n[i] = EcologyBiomes.IDS[i]; }
        for (int i = 0; i < FederalEcologyBiomes.IDS.length; i++) {
            c[EcologyBiomes.IDS.length + i] = FederalEcologyBiomes.COLOURS[i];
            n[EcologyBiomes.IDS.length + i] = FederalEcologyBiomes.IDS[i];
        }
        c[FALLOUT] = 0x8C8472;
        n[FALLOUT] = "fallout_barrens";
        for (int i = 0; i <= FALLOUT; i++) INDEX.put("apocalypse_firstlight:" + n[i], i);
        // the ecology's sea and beach are the vanilla biomes in the world (MainNationBiomeRegionPlan)
        INDEX.put("minecraft:ocean", EcologyBiomes.SEA);
        INDEX.put("minecraft:deep_ocean", EcologyBiomes.SEA);
        INDEX.put("minecraft:beach", EcologyBiomes.BEACH);
        colours = c;
        ids = n;
    }

    /** The index of a biome by its registry id; a new one gets the next free index and the given colour (-1: none left). */
    public static synchronized int index(String registryId, int colourIfNew) {
        Integer i = INDEX.get(registryId);
        if (i != null) return i;
        int next = -1;
        for (int k = FALLOUT + 1; k < MAX; k++) if (ids[k] == null) { next = k; break; }
        if (next < 0) return -1;
        int[] c = Arrays.copyOf(colours, MAX);
        String[] n = Arrays.copyOf(ids, MAX);
        c[next] = colourIfNew & 0xFFFFFF;
        n[next] = registryId;
        colours = c;
        ids = n;
        INDEX.put(registryId, next);
        return next;
    }

    /** The registry id of an index (ecology biomes under apocalypse_firstlight:), or null. */
    public static String registryId(int index) {
        if (index < 0 || index >= MAX) return null;
        String n = ids[index];
        if (n == null) return null;
        if (index == EcologyBiomes.SEA) return "minecraft:ocean";
        if (index == EcologyBiomes.BEACH) return "minecraft:beach";
        return index <= FALLOUT ? "apocalypse_firstlight:" + n : n;
    }

    /** A biome's own colour (0xRRGGBB). */
    public static int colour(int index) {
        return index >= 0 && index < MAX ? colours[index] : 0x808080;
    }

    /** A biome's map colour: its own toward paper (the legend's swatch); the sea is water. */
    public static int mapColour(int index) {
        if (index == EcologyBiomes.SEA) return WATER;
        return mix(colour(index), PAPER, PAPER_MIX);
    }

    public static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int scale(int c, double k) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * k), g = (int) Math.min(255, ((c >> 8) & 255) * k), b = (int) Math.min(255, (c & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
