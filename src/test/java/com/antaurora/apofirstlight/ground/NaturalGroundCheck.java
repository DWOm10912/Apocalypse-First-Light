package com.antaurora.apofirstlight.ground;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * N1c (docs/worldgen/natural_ground_n1_design.md 12): the natural ground swap leaves no vanilla natural ground in a new
 * world's generation, read from the sources (no game needed):
 * <ul>
 *   <li>every block the surface rules (the three noise settings) and AFL's configured features name, and every block the
 *   vanilla features of the AFL biomes lay ({@link #VANILLA_FEATURES}), is swapped ({@link NaturalGroundTable#SWAP}),
 *   kept ({@link NaturalGroundTable#KEPT}) or AFL's own;</li>
 *   <li>every vanilla ground block the generation code names (a soil, sand, gravel, clay, snow, sandstone, calcite or
 *   terracotta {@code Blocks} constant in the worldgen, tree, feature and bunker code) is swapped;</li>
 *   <li>every swapped-to state is an AFL block with a blockstate file that has that state (the property values in its
 *   variant keys), never a vanilla block.</li>
 * </ul>
 *   ./gradlew naturalGroundCheck
 */
public final class NaturalGroundCheck {
    private NaturalGroundCheck() {}

    static final Path RES = Path.of("src/main/resources"), JAVA = Path.of("src/main/java/com/antaurora/apofirstlight");

    /** What the vanilla features of the AFL biomes lay (biome JSONs, features list): disks, dirt and gravel blobs, trees' dirt, geodes, lava lakes, springs, the freeze layer. */
    static final Map<String, List<String>> VANILLA_FEATURES = Map.of(
            "disk_sand / disk_clay / disk_gravel", List.of("minecraft:sand", "minecraft:clay", "minecraft:gravel", "minecraft:grass_block", "minecraft:dirt"),
            "ore_dirt / ore_gravel", List.of("minecraft:dirt", "minecraft:gravel"),
            "trees (the dirt under a trunk)", List.of("minecraft:dirt", "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:moss_block"),
            "amethyst_geode", List.of("minecraft:calcite", "minecraft:smooth_basalt", "minecraft:amethyst_block", "minecraft:budding_amethyst"),
            "lake_lava / spring / underwater_magma", List.of("minecraft:lava", "minecraft:stone", "minecraft:water", "minecraft:magma_block"),
            "freeze_top_layer", List.of("minecraft:ice", "minecraft:snow"),
            "ore_andesite / diorite / granite / tuff", List.of("minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff"));

    /** A vanilla ground block constant in the generation code. */
    static final Pattern GROUND_CONST = Pattern.compile("Blocks\\.([A-Z_]*(DIRT|SAND|GRAVEL|CLAY|MUD|SNOW_BLOCK|POWDER_SNOW|PODZOL|MYCELIUM|MOSS_BLOCK|GRASS_BLOCK|SANDSTONE|CALCITE|TERRACOTTA))\\b");
    static final Pattern NAME = Pattern.compile("\"Name\"\\s*:\\s*\"([a-z0-9_:]+)\"");

    public static void main(String[] args) throws IOException {
        List<String> fails = new ArrayList<>();
        Map<String, Set<String>> seen = new TreeMap<>();
        // the surface rules and AFL's configured features
        List<Path> json = new ArrayList<>();
        for (String dir : List.of("data/minecraft/worldgen/noise_settings", "data/apocalypse_firstlight/worldgen/noise_settings",
                "data/apocalypse_firstlight/worldgen/configured_feature"))
            try (Stream<Path> s = Files.list(RES.resolve(dir))) { s.filter(p -> p.toString().endsWith(".json")).forEach(json::add); }
        for (Path p : json) {
            Matcher m = NAME.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) seen.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(p.getFileName().toString());
        }
        for (var e : VANILLA_FEATURES.entrySet()) for (String id : e.getValue()) seen.computeIfAbsent(id, k -> new TreeSet<>()).add(e.getKey());
        int swapped = 0, kept = 0;
        for (var e : seen.entrySet()) {
            String id = e.getKey();
            if (NaturalGroundTable.SWAP.containsKey(id)) swapped++;
            else if (NaturalGroundTable.KEPT.contains(id) || id.startsWith(NaturalGroundTable.AFL)) kept++;
            else fails.add("generation lays " + id + " (" + String.join(", ", e.getValue()) + "): neither swapped nor kept");
        }
        // the generation code's vanilla ground constants
        Set<String> code = new TreeSet<>();
        for (String dir : List.of("worldgen", "tree", "world/feature", "world/bunker", "registry/AflFeatures.java"))
            try (Stream<Path> s = Files.walk(JAVA.resolve(dir))) {
                for (Path p : (Iterable<Path>) s.filter(q -> q.toString().endsWith(".java"))::iterator) {
                    Matcher m = GROUND_CONST.matcher(Files.readString(p, StandardCharsets.UTF_8));
                    while (m.find()) {
                        String id = "minecraft:" + m.group(1).toLowerCase(java.util.Locale.ROOT);
                        code.add(id);
                        if (id.equals("minecraft:muddy_mangrove_roots")) continue;
                        if (!NaturalGroundTable.SWAP.containsKey(id)) fails.add(JAVA.relativize(p) + " names " + id + ": not swapped");
                    }
                }
            }
        // the swapped-to states: AFL blocks whose blockstate file has the state
        Set<String> to = new TreeSet<>(NaturalGroundTable.SWAP.values());
        to.add(NaturalGroundTable.GRAVEL_WET);
        for (String s : to) {
            String id = NaturalGroundTable.id(s);
            if (!id.startsWith(NaturalGroundTable.AFL) || NaturalGroundTable.SWAP.containsKey(id)) { fails.add("swaps to " + s + ": not an AFL block"); continue; }
            Path bs = RES.resolve("assets/apocalypse_firstlight/blockstates/" + id.substring(NaturalGroundTable.AFL.length()) + ".json");
            if (!Files.exists(bs)) { fails.add("swaps to " + s + ": no blockstate " + bs); continue; }
            JsonObject root = JsonParser.parseString(Files.readString(bs, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> props = s.indexOf('[') < 0 ? List.of() : List.of(s.substring(s.indexOf('[') + 1, s.length() - 1).split(","));
            if (!has(root, props)) fails.add("swaps to " + s + ": " + bs.getFileName() + " has no such state");
        }
        System.out.println("natural ground: " + seen.size() + " blocks the generation lays (" + swapped + " swapped, " + kept + " kept or AFL), "
                + code.size() + " vanilla ground constants in the generation code, " + to.size() + " swapped-to states");
        for (var e : NaturalGroundTable.SWAP.entrySet()) System.out.println("  " + e.getKey() + " -> " + e.getValue());
        System.out.println("  minecraft:gravel under water -> " + NaturalGroundTable.GRAVEL_WET);
        if (fails.isEmpty()) System.out.println("NATURAL GROUND CHECK PASS");
        else {
            fails.forEach(f -> System.out.println("FAIL " + f));
            System.out.println("NATURAL GROUND CHECK FAIL (" + fails.size() + ")");
            System.exit(1);
        }
    }

    /** Does a blockstate file (variants or multipart) have a state with these property values? */
    static boolean has(JsonObject root, List<String> props) {
        if (props.isEmpty()) return true;
        Set<String> found = new TreeSet<>();
        if (root.has("variants")) for (String key : root.getAsJsonObject("variants").keySet()) found.addAll(List.of(key.split(",")));
        if (root.has("multipart")) for (JsonElement part : root.getAsJsonArray("multipart")) {
            JsonObject p = part.getAsJsonObject();
            if (!p.has("when")) continue;
            for (var w : p.getAsJsonObject("when").entrySet())
                for (String v : w.getValue().getAsString().split("\\|")) found.add(w.getKey() + "=" + v);
        }
        return found.containsAll(props);
    }
}
