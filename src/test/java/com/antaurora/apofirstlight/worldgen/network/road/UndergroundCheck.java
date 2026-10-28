package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.underground.CaveTrail;
import com.antaurora.apofirstlight.worldgen.underground.GeoProtect;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel.Column;
import com.antaurora.apofirstlight.worldgen.underground.RockType;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Anchor;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Bowl;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Capsule;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Cave;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveLink;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Ellipsoid;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Entrance;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.EntranceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Exit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.FacilityPart;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Kit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Notch;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreBody;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pitch;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Place;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Need;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Step;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.PlaceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Platform;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Shape;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Succession;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Talus;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlanner;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundRoutes;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.InflaterInputStream;

/**
 * U1 offline check of the underground plan (docs/worldgen/underground_geology_caves_v0_design.md 14.1, 19.10): per
 * seed the full network build (as StartMapCheck), then the geology model at minY -256, the C works' protection and the
 * underground plan, and:
 * <ul>
 *   <li>the geology by state (bedrock shares; geology follows landform, not borders), the map and six sections;</li>
 *   <li>the four layers' air by Monte Carlo against the design targets, layer 1 clear of everything but throats;</li>
 *   <li>counts of systems, giant caves, canyons, entrances by kind, ore districts, mines, the facility;</li>
 *   <li>the connectivity graph, the routes in and out for every kit (no traps), least kit to every target;</li>
 *   <li>every rope pitch's two ends standable and anchored in competent rock;</li>
 *   <li>no shape or entrance breaking a road, town, home or facility rule;</li>
 *   <li>every succession candidate with at least one reliable exit;</li>
 *   <li>determinism (two builds' digests, queries in two orders) and the planning times, plus minY -192 and -64 counts.</li>
 * </ul>
 * Writes build/underground/&lt;seed&gt;_*.png and build/underground/report.txt. Never touches a world or a save.
 * <pre>
 *   UndergroundCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS)
 * </pre>
 */
public final class UndergroundCheck {
    private UndergroundCheck() {}

    static final File OUT = new File("build/underground");
    static final StringBuilder REPORT = new StringBuilder();

    static void out(String fmt, Object... a) {
        String s = a.length == 0 ? fmt : String.format(Locale.ROOT, fmt, a);
        System.out.println(s);
        REPORT.append(s).append('\n');
    }

    public static void main(String[] args) throws Exception {
        OUT.mkdirs();
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        int fails = 0, seeds = 0;
        List<String> summary = new ArrayList<>();
        for (String f : bins) {
            String name = Path.of(f).getFileName().toString();
            String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
            Path planFile = plans.get(unsigned);
            if (planFile == null) { out("== seed " + unsigned + ": no plan (run systemPlans)"); continue; }
            long seed = Long.parseUnsignedLong(unsigned);
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null) { out("== seed " + unsigned + ": STALE terrain plan " + f); continue; }
            seeds++;
            out("== seed " + unsigned);
            FederalMap map = FederalMap.forSeed(seed);
            RouteTerrain terrain = new RouteTerrain(s, map);
            String[] kept = new String[1];
            RoadCounty.Memo memo = new RoadCounty.Memo() {
                @Override public String load() { return kept[0]; }
                @Override public void store(String site) { kept[0] = site; }
            };
            long tr = System.nanoTime();
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(Files.readString(planFile), terrain, memo);
            RoadCounty.Home h = r.home();
            List<RoadDesign> roads = RoadNetworkJson.designs(h == null ? TownPlan.append(r.json(), r.towns()) : RoadCounty.append(TownPlan.append(r.json(), r.towns()), h));
            out("  roads/towns/home built in %d ms (%d roads, %d towns, home %s)", (System.nanoTime() - tr) / 1_000_000, roads.size(), r.towns().size(), h == null ? "none" : "yes");
            Result res = check(unsigned, seed, s, map, roads, r.towns(), h);
            summary.add(unsigned + ": " + res.line + (res.bad.isEmpty() ? "" : " FAIL " + res.bad));
            for (String b : res.bad) out("  FAIL " + b);
            fails += res.bad.isEmpty() ? 0 : 1;
        }
        out("== summary");
        for (String l : summary) out("  " + l);
        out(fails == 0 ? "UNDERGROUND CHECK PASS (" + seeds + " seeds)" : "UNDERGROUND CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        Files.writeString(new File(OUT, "report.txt").toPath(), REPORT.toString());
        if (fails > 0) System.exit(1);
    }

    record Result(String line, List<String> bad) {}

    /**
     * U3b batch 1 (underground_u3_rocks_ores_design.md 14): each placed kind's bodies (up to 25) sampled block by block
     * through OreBodies (lean/rich cells, empty bodies; a coal seam body by its rich columns), the vein districts' metals,
     * and the ore blocks and raw ores as real content (blockstate, loot, names, model, mesh, mass, grid size).
     */
    static void oreRocks(List<String> bad, long seed, UndergroundPlan u) {
        EnumMap<UndergroundPlan.OreKind, List<OreBody>> byKind = new EnumMap<>(UndergroundPlan.OreKind.class);
        for (OreBody o : u.ores) if (com.antaurora.apofirstlight.worldgen.underground.OreBodies.placed(o.kind()))
            byKind.computeIfAbsent(o.kind(), q -> new ArrayList<>()).add(o);
        StringBuilder sb = new StringBuilder();
        for (var e : byKind.entrySet()) {
            List<OreBody> list = e.getValue();
            int n = Math.min(25, list.size()), empty = 0;
            long lean = 0, rich = 0;
            for (int i = 0; i < n; i++) {
                OreBody o = list.get(i * list.size() / n);
                int r = (int) Math.ceil(com.antaurora.apofirstlight.worldgen.underground.OreBodies.reach(o));
                int[] yr = com.antaurora.apofirstlight.worldgen.underground.OreBodies.yRange(o);
                int x0 = (int) Math.floor(o.x()) - r, z0 = (int) Math.floor(o.z()) - r;
                long cells = 0;
                for (int z = z0; z <= z0 + 2 * r; z++) for (int x = x0; x <= x0 + 2 * r; x++) {
                    if (e.getKey() == UndergroundPlan.OreKind.COAL_SEAM) {
                        if (com.antaurora.apofirstlight.worldgen.underground.OreBodies.inSeam(o, seed, x, z)) { rich++; cells++; }
                        continue;
                    }
                    for (int y = yr[0]; y <= yr[1]; y++) {
                        int gr = com.antaurora.apofirstlight.worldgen.underground.OreBodies.grade(o, seed, x, y, z);
                        if (gr < 0) continue;
                        cells++;
                        if (gr == 0) lean++; else rich++;
                    }
                }
                if (cells == 0) empty++;
            }
            sb.append(String.format(Locale.ROOT, " %s %d bodies (%d sampled, %d empty) lean %d rich %d;", e.getKey().name().toLowerCase(Locale.ROOT),
                    list.size(), n, empty, lean, rich));
            if (lean + rich == 0) bad.add("ore " + e.getKey() + ": the sampled bodies lay no ore rock");
            if (empty * 10 > n) bad.add("ore " + e.getKey() + ": " + empty + " of " + n + " sampled bodies empty");
            if (e.getKey() != UndergroundPlan.OreKind.COAL_SEAM && rich == 0) bad.add("ore " + e.getKey() + ": no rich ore");
        }
        out("  ore rock (batch 1, block cells; a coal seam: rich columns):%s", sb);
        int[] metals = new int[3];
        for (UndergroundPlan.District d : u.districts) if (d.kind() == UndergroundPlan.OreKind.HYDROTHERMAL_VEIN)
            metals[com.antaurora.apofirstlight.worldgen.underground.OreBodies.veinMetal(seed, d.id())]++;
        out("  vein districts by metal: gold %d, copper %d, lead %d", metals[0], metals[1], metals[2]);
        long iron = u.districts.stream().filter(d -> d.kind() == UndergroundPlan.OreKind.BANDED_IRON).count();
        if (iron == 0) bad.add("no banded iron district (shield edge)");

        java.nio.file.Path res = java.nio.file.Path.of("src/main/resources");
        if (!Files.isDirectory(res)) res = java.nio.file.Path.of("D:/Minecraft Modding/AFL-federal-worldgen-v1/src/main/resources");
        String en, zh, mass, gridSize, blockMass;
        try {
            en = Files.readString(res.resolve("assets/apocalypse_firstlight/lang/en_us.json"));
            zh = Files.readString(res.resolve("assets/apocalypse_firstlight/lang/zh_cn.json"));
            mass = Files.readString(res.resolve("data/apocalypse_firstlight/item_mass/core_v1.json"));
            gridSize = Files.readString(res.resolve("data/apocalypse_firstlight/item_size/core_v1.json"));
            blockMass = Files.readString(res.resolve("data/apocalypse_firstlight/item_mass/afl_content_v1.json"));
        } catch (Exception ex) {
            bad.add("ore files: " + ex.getMessage());
            return;
        }
        int ok = 0;
        for (String b : new String[]{"coal_seam", "banded_iron_formation", "quartz_vein", "lead_zinc_dolomite", "copper_breccia"}) {
            String key = "\"block.apocalypse_firstlight." + b + "\"";
            if (!Files.exists(res.resolve("assets/apocalypse_firstlight/blockstates/" + b + ".json"))) bad.add(b + " has no blockstate");
            else if (!Files.exists(res.resolve("data/apocalypse_firstlight/loot_tables/blocks/" + b + ".json"))) bad.add(b + " has no loot table");
            else if (!en.contains(key) || !zh.contains(key)) bad.add(b + " has no name in en_us or zh_cn");
            else if (!blockMass.contains("\"apocalypse_firstlight:" + b + "\"")) bad.add(b + " has no mass");
            else ok++;
        }
        for (String id : new String[]{"coal_lump", "raw_hematite", "raw_chalcopyrite", "gold_quartz", "raw_galena", "raw_sphalerite"}) {
            String key = "\"item.apocalypse_firstlight." + id + "\"", desc = "\"tooltip.apocalypse_firstlight." + id + ".description\"";
            if (!Files.exists(res.resolve("assets/apocalypse_firstlight/models/item/" + id + ".json"))) bad.add(id + " has no item model");
            else if (!Files.exists(res.resolve("assets/apocalypse_firstlight/meshes/" + id + ".aflmesh.json"))) bad.add(id + " has no mesh");
            else if (!en.contains(key) || !zh.contains(key) || !en.contains(desc) || !zh.contains(desc)) bad.add(id + " has no name or description in en_us or zh_cn");
            else if (!mass.contains("\"apocalypse_firstlight:" + id + "\"") || !gridSize.contains("\"apocalypse_firstlight:" + id + "\"")) bad.add(id + " has no mass or grid size");
            else ok++;
        }
        out("  ore blocks and raw ores: %d of 11 complete", ok);
    }

    /**
     * U3a GeologyPass (docs/worldgen/underground_u3_rocks_ores_design.md 2.2, 9): GeologyModel#rocks against #rockAt block
     * by block (must be 0 apart), the rocks the pass lays by state and by depth, and the pass's column work per chunk
     * (the section scan and writes not counted: the game's /afl dev underground reports the whole pass).
     */
    static void geologyPass(List<String> bad, long seed, TerrainPlanSurface s, FederalMap map, GeologyModel g) {
        java.util.Random rnd = new java.util.Random(seed ^ 0x03A0_6E01L);
        double size = s.gridN * TerrainPlanSurface.CELL;
        int cols = 0, cells = 0, apart = 0, deepest = Integer.MIN_VALUE;
        String firstApart = null;
        Map<FederalStateId, long[]> byState = new TreeMap<>();
        String[] bandName = {"0-50 m", "50-150 m", "150-300 m", "300 m+"};
        long[][] byBand = new long[4][RockType.values().length];
        RockType[] col = new RockType[1024];
        while (cols < 3000) {
            double x = s.gridOrigin + rnd.nextDouble() * size, z = s.gridOrigin + rnd.nextDouble() * size;
            if (s.waterAt(x, z) || !map.isLand(x, z)) continue;
            FederalStateId st = map.stateAt(x, z);
            if (st == null) continue;
            x = Math.floor(x) + 0.5;
            z = Math.floor(z) + 0.5;
            Column c = g.column(x, z);
            int y0 = g.minY, y1 = Math.min(y0 + col.length - 1, (int) Math.ceil(c.h) + 8);
            g.rocks(c, y0, y1, col);
            RockType bed = com.antaurora.apofirstlight.worldgen.underground.GeologyPass.bedrock(g, c);
            long[] t = byState.computeIfAbsent(st, q -> new long[RockType.values().length]);
            for (int y = y0; y <= y1; y++) {
                RockType ref = g.rockAt(c, y + 0.5), got = col[y - y0];
                cells++;
                if (ref != got) {
                    apart++;
                    if (firstApart == null) firstApart = String.format(Locale.ROOT, "(%.1f, %d, %.1f) rockAt %s, rocks %s", x, y, z, ref, got);
                }
                if (y + 0.5 > c.h) continue;
                RockType r = com.antaurora.apofirstlight.worldgen.underground.GeologyPass.placed(got, bed);
                t[r.ordinal()]++;
                double depth = c.h - (y + 0.5);
                byBand[depth < 50 ? 0 : depth < 150 ? 1 : depth < 300 ? 2 : 3][r.ordinal()]++;
            }
            cols++;
        }
        out("  geology pass: %d columns, %d blocks, rocks vs rockAt apart %d%s", cols, cells, apart, firstApart == null ? "" : " (first " + firstApart + ")");
        if (apart > 0) bad.add(apart + " blocks where GeologyModel#rocks is not rockAt");
        out("  rocks laid by state (share of the blocks under the plan surface):");
        for (var e : byState.entrySet()) out("    %-14s%s", e.getKey(), shares(e.getValue(), 5));
        out("  rocks laid by depth under the plan surface:");
        for (int b = 0; b < 4; b++) out("    %-10s%s", bandName[b], shares(byBand[b], 5));
        for (RockType r : RockType.values()) {
            long n = 0;
            for (long[] v : byState.values()) n += v[r.ordinal()];
            if (n > 0 && r.soil) bad.add("the pass lays " + r.id);
        }

        // the column work per chunk: 256 columns, y from the floor to 8 above the plan
        long t0 = System.nanoTime();
        int chunks = 0;
        Column c = new Column();
        while (chunks < 60) {
            int cx = (int) Math.floor((s.gridOrigin + rnd.nextDouble() * size) / 16), cz = (int) Math.floor((s.gridOrigin + rnd.nextDouble() * size) / 16);
            if (!map.isLand(cx * 16 + 8, cz * 16 + 8)) continue;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                g.column(cx * 16 + lx + 0.5, cz * 16 + lz + 0.5, c);
                g.rocks(c, g.minY, Math.min(g.minY + col.length - 1, (int) Math.ceil(c.h) + 8), col);
            }
            chunks++;
        }
        double ms = (System.nanoTime() - t0) / 1e6 / chunks;
        out("  geology pass column work: %.2f ms a chunk (%d land chunks; target under 2 ms with the writes)", ms, chunks);
        if (ms > 2) out("  NOTE geology pass column work over 2 ms a chunk");

        // the shipped rock_types files: one per rock block, equal to the built-in table, the boulder look EntranceRocks gives
        java.nio.file.Path dir = java.nio.file.Path.of("src/main/resources/data/apocalypse_firstlight/rock_types");
        if (!Files.isDirectory(dir)) dir = java.nio.file.Path.of("D:/Minecraft Modding/AFL-federal-worldgen-v1/src/main/resources/data/apocalypse_firstlight/rock_types");
        int files = 0;
        for (RockType r : com.antaurora.apofirstlight.earthwork.RockTypes.BLOCKS) {
            java.nio.file.Path f = dir.resolve(r.id + ".json");
            try {
                var rock = com.antaurora.apofirstlight.earthwork.RockTypes.parse(r.id, com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject());
                if (!rock.equals(com.antaurora.apofirstlight.earthwork.RockTypes.of(r))) bad.add("rock_types/" + r.id + " differs from the built-in table");
                if (!rock.look().equals(UndergroundPlan.rockLook(r)))
                    bad.add("rock_types/" + r.id + " boulder look " + rock.look() + " is not EntranceRocks'");
                files++;
            } catch (Exception ex) {
                bad.add("rock_types/" + r.id + ": " + ex.getMessage());
            }
        }
        out("  rock_types: %d of %d files read and equal to the built-in table", files, com.antaurora.apofirstlight.earthwork.RockTypes.BLOCKS.size());

        // U3a-2 / U3a-3: each rock's stone is a real item (model, both names, mass, grid size); a block cut block by block
        // takes ceil(1000 / strike) strikes and gives its stones and pebbles by volume (BoulderStrike STONE / PEBBLE litres)
        java.nio.file.Path res = dir.getParent().getParent().getParent();
        String en = "", zh = "", mass = "", gridSize = "";
        try {
            en = Files.readString(res.resolve("assets/apocalypse_firstlight/lang/en_us.json"));
            zh = Files.readString(res.resolve("assets/apocalypse_firstlight/lang/zh_cn.json"));
            mass = Files.readString(res.resolve("data/apocalypse_firstlight/item_mass/core_v1.json"));
            gridSize = Files.readString(res.resolve("data/apocalypse_firstlight/item_size/core_v1.json"));
        } catch (Exception ex) {
            bad.add("stone item files: " + ex.getMessage());
        }
        StringBuilder cuts = new StringBuilder();
        for (RockType r : com.antaurora.apofirstlight.earthwork.RockTypes.BLOCKS) {
            var rock = com.antaurora.apofirstlight.earthwork.RockTypes.of(r);
            String id = rock.stone().substring(rock.stone().indexOf(':') + 1);
            String key = "\"item.apocalypse_firstlight." + id + "\"";
            if (!Files.exists(res.resolve("assets/apocalypse_firstlight/models/item/" + id + ".json"))) bad.add(id + " has no item model");
            if (!Files.exists(res.resolve("assets/apocalypse_firstlight/meshes/" + id + ".aflmesh.json"))) bad.add(id + " has no mesh");
            if (!en.contains(key) || !zh.contains(key)) bad.add(id + " has no name in en_us or zh_cn");
            if (!mass.contains("\"" + rock.stone() + "\"") || !gridSize.contains("\"" + rock.stone() + "\"")) bad.add(id + " has no mass or grid size");
            int per = Math.max(1, (int) Math.round(rock.strike() / com.antaurora.apofirstlight.earthwork.RockCarve.SUB_LITRES));
            int strikes = (int) Math.ceil((double) com.antaurora.apofirstlight.earthwork.CarveChunk.CELLS / per);
            cuts.append(String.format(Locale.ROOT, " %s %d", r.id, strikes));
        }
        out("  rock carve (strikes a block's volume; 10 stones and 25 pebbles a block):%s", cuts);
        carve(bad, seed);
        shaft(bad, seed);
        relief(bad);
        smoothRock(bad);
    }

    /**
     * U3a-3 round 2 (docs/worldgen/underground_u3_rocks_ores_design.md 13.5): 40 strikes into a small rock mass with a wall,
     * a floor and a block of dirt in it (RockCarve#piece), then every carved block's surface net (CarveNet) with the
     * vanilla faces of every uncarved full block (cut into 1/8 m squares) must close: every directed edge has its reverse
     * as often (no gap, no flipped face); each strike takes its full piece, connected and touching open space; the
     * collision boxes (RockCarve#shapeOf) hold exactly the sub-cells left.
     */
    /**
     * Digging a shaft (user 2026-10-24: straight walls, no funnel; RockCarve PIT_ACROSS, CLEAR_LEFT, RIM_LEFT): a 3 x 7 x 3 block
     * rock column, the middle column dug from the top block (y 5) down to y 2 with granite pieces (26 sub-cells) aimed at
     * the floor near the middle; with the pit rule (fails: a level left uncleared, or wall bites over 10% of the shaft) and
     * without it (reported: the funnel the rule removes).
     */
    static void shaft(List<String> bad, long seed) {
        StringBuilder sb = new StringBuilder();
        for (int mode = 1; mode >= 0; mode--) {
            final boolean pitRule = mode == 1;
            final int W = 3, H = 7;
            int[] kind = new int[W * H * W];   // 0 air, 1 rock
            for (int z = 0; z < W; z++) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) kind[x + W * (y + H * z)] = y <= 5 ? 1 : 0;
            java.util.Map<Integer, long[]> carved = new java.util.HashMap<>();
            int[] wall = new int[1];
            class World implements com.antaurora.apofirstlight.earthwork.RockCarve.Grid {
                int kindOf(int bx, int by, int bz) {
                    return bx < 0 || by < 0 || bz < 0 || bx >= W || by >= H || bz >= W ? 1 : kind[bx + W * (by + H * bz)];
                }

                @Override
                public boolean solid(int x, int y, int z) {
                    int bx = Math.floorDiv(x, 8), by = Math.floorDiv(y, 8), bz = Math.floorDiv(z, 8);
                    if (bx < 0 || by < 0 || bz < 0 || bx >= W || by >= H || bz >= W || kindOf(bx, by, bz) != 1) return false;
                    long[] b = carved.get(bx + W * (by + H * bz));
                    return b == null || !com.antaurora.apofirstlight.earthwork.CarveChunk.gone(b, x & 7, y & 7, z & 7);
                }

                @Override
                public boolean open(int x, int y, int z) {
                    int bx = Math.floorDiv(x, 8), by = Math.floorDiv(y, 8), bz = Math.floorDiv(z, 8);
                    if (bx < 0 || by < 0 || bz < 0 || bx >= W || by >= H || bz >= W) return false;
                    return kindOf(bx, by, bz) == 0 || !solid(x, y, z);
                }

                @Override
                public void take(int x, int y, int z) {
                    int bx = Math.floorDiv(x, 8), by = Math.floorDiv(y, 8), bz = Math.floorDiv(z, 8);
                    carved.computeIfAbsent(bx + W * (by + H * bz), k -> new long[8])[y & 7] |= 1L << ((x & 7) | (z & 7) << 3);
                    if (bx != 1 || bz != 1) wall[0]++;
                }
            }
            World w = new World();
            java.util.Random rnd = new java.util.Random(seed ^ 0x5AF7L);
            int strikes = 0;
            while (strikes < 600 && kind[1 + W * (2 + H)] == 1) {
                // aim at the floor near the middle of the shaft: the highest rock sub-cell under a point 0.3 m round it
                double ax = 1.5 + (rnd.nextDouble() - 0.5) * 0.6, az = 1.5 + (rnd.nextDouble() - 0.5) * 0.6;
                int sx = (int) Math.floor(ax * 8), sz = (int) Math.floor(az * 8), top = -1;
                for (int y = H * 8 - 1; y >= 0 && top < 0; y--) if (w.solid(sx, y, sz)) top = y;
                if (top < 0) break;
                double ay = (top + 1) / 8.0;
                double dx = (rnd.nextDouble() - 0.5) * 0.3, dz = (rnd.nextDouble() - 0.5) * 0.3;
                int by = top >> 3;
                long pit = Long.MIN_VALUE;
                if (pitRule) {
                    int walls = 0;
                    for (int[] o : new int[][]{{0, 1}, {2, 1}, {1, 0}, {1, 2}}) if (w.kindOf(o[0], by + 1, o[1]) == 1) walls++;
                    if (walls >= 2) pit = net.minecraft.core.BlockPos.asLong(1, by, 1);
                }
                int got = com.antaurora.apofirstlight.earthwork.RockCarve.piece(w, ax, ay, az, dx, -1, dz, 26, rnd.nextLong(), pit);
                strikes++;
                // the rim over the pit floor goes with the blow (RockCarve RIM_LEFT)
                long[] over = pit == Long.MIN_VALUE || got == 0 ? null : carved.get(1 + W * (by + 1 + H));
                if (over != null && kind[1 + W * (by + 1 + H)] == 1
                        && com.antaurora.apofirstlight.earthwork.RockCarve.rimClears(com.antaurora.apofirstlight.earthwork.CarveChunk.count(over)))
                    java.util.Arrays.fill(over, -1L);
                for (var it = carved.entrySet().iterator(); it.hasNext(); ) {
                    var en = it.next();
                    if (com.antaurora.apofirstlight.earthwork.RockCarve.clears(com.antaurora.apofirstlight.earthwork.CarveChunk.count(en.getValue()))) {
                        kind[en.getKey()] = 0;
                        it.remove();
                    }
                }
            }
            int cleared = 0;
            StringBuilder left = new StringBuilder();
            for (int y = 5; y >= 2; y--) {
                long[] b = carved.get(1 + W * (y + H));
                if (kind[1 + W * (y + H)] == 0) cleared++;
                else left.append(String.format(Locale.ROOT, " y%d %d%%", y, b == null ? 100 : 100 * (512 - com.antaurora.apofirstlight.earthwork.CarveChunk.count(b)) / 512));
            }
            sb.append(String.format(Locale.ROOT, " %s: %d strikes, %d of 4 levels cleared%s, wall bites %d sub-cells;", pitRule ? "pit rule" : "without",
                    strikes, cleared, left.length() == 0 ? "" : " (left:" + left + ")", wall[0]));
            if (pitRule && cleared < 4) bad.add("shaft: " + cleared + " of 4 levels cleared in " + strikes + " strikes");
            if (pitRule && wall[0] > 4 * 512 / 10) bad.add("shaft: wall bites " + wall[0] + " sub-cells");
        }
        out("  shaft (3 x 7 x 3 rock, dug down 4 blocks):%s", sb);
    }

    static void carve(List<String> bad, long seed) {
        final int W = 6;
        // rock: y <= 3 except a room x >= 4 from y 2 up; dirt (a full cube that is not rock) at (2, 3, 2); outside: air
        int[] kind = new int[W * W * W];   // 0 air, 1 rock, 2 dirt
        for (int z = 0; z < W; z++) for (int y = 0; y < W; y++) for (int x = 0; x < W; x++)
            kind[x + W * (y + W * z)] = y <= 3 && !(x >= 4 && y >= 2) ? 1 : 0;
        kind[2 + W * (3 + W * 2)] = 2;
        java.util.Map<Integer, long[]> carved = new java.util.HashMap<>();
        class World implements com.antaurora.apofirstlight.earthwork.RockCarve.Grid {
            final List<int[]> taken = new ArrayList<>();

            int kindOf(int bx, int by, int bz) {
                return bx < 0 || by < 0 || bz < 0 || bx >= W || by >= W || bz >= W ? 0 : kind[bx + W * (by + W * bz)];
            }

            @Override
            public boolean solid(int x, int y, int z) {
                int bx = Math.floorDiv(x, 8), by = Math.floorDiv(y, 8), bz = Math.floorDiv(z, 8);
                if (kindOf(bx, by, bz) != 1) return false;
                long[] b = carved.get(bx + W * (by + W * bz));
                return b == null || !com.antaurora.apofirstlight.earthwork.CarveChunk.gone(b, x & 7, y & 7, z & 7);
            }

            @Override
            public boolean open(int x, int y, int z) { return !filled(x, y, z); }

            /** solid in the meshing sense: full cubes and the carving's sub-cells left */
            boolean filled(int x, int y, int z) {
                int k = kindOf(Math.floorDiv(x, 8), Math.floorDiv(y, 8), Math.floorDiv(z, 8));
                return k == 2 || k == 1 && solid(x, y, z);
            }

            @Override
            public void take(int x, int y, int z) {
                int bx = Math.floorDiv(x, 8), by = Math.floorDiv(y, 8), bz = Math.floorDiv(z, 8);
                carved.computeIfAbsent(bx + W * (by + W * bz), k -> new long[8])[y & 7] |= 1L << ((x & 7) | (z & 7) << 3);
                taken.add(new int[]{x, y, z});
            }
        }
        World w = new World();
        java.util.Random rnd = new java.util.Random(seed ^ 0xCA125E01L);
        int shortPieces = 0, loose = 0, sealedOff = 0, emptied = 0, pieces = 0;
        double across = 0, along = 0;
        for (int strike = 0; strike < 40; strike++) {
            // a face of rock toward open space: the room's wall (a -x blow), its floor or the top (from above)
            int face = rnd.nextInt(3);
            double ax, ay, az, dx, dy, dz;
            if (face == 0) {
                ax = 4.0; ay = 2 + rnd.nextDouble() * 1.9; az = 0.1 + rnd.nextDouble() * (W - 0.2);
                dx = -1; dy = (rnd.nextDouble() - 0.5) * 0.6; dz = (rnd.nextDouble() - 0.5) * 0.6;
            } else if (face == 1) {
                ax = 4.1 + rnd.nextDouble() * 1.8; ay = 2.0; az = 0.1 + rnd.nextDouble() * (W - 0.2);
                dx = (rnd.nextDouble() - 0.5) * 0.6; dy = -1; dz = (rnd.nextDouble() - 0.5) * 0.6;
            } else {
                ax = 0.1 + rnd.nextDouble() * 3.8; ay = 4.0; az = 0.1 + rnd.nextDouble() * (W - 0.2);
                dx = (rnd.nextDouble() - 0.5) * 0.8; dy = -1; dz = (rnd.nextDouble() - 0.5) * 0.8;
            }
            // later strikes go into the hollows already made: aim a little behind the face
            if (strike >= 20) {
                ax += dx * 0.15;
                ay += dy * 0.15;
                az += dz * 0.15;
            }
            int want = 13 + rnd.nextInt(39);
            w.taken.clear();
            int got = com.antaurora.apofirstlight.earthwork.RockCarve.piece(w, ax, ay, az, dx, dy, dz, want, rnd.nextLong());
            if (got == 0) continue;
            pieces++;
            if (got < want) shortPieces++;
            // connected, and touching space that was open before the strike
            java.util.Set<Long> set = new java.util.HashSet<>();
            for (int[] c : w.taken) set.add(((long) c[0] << 40) ^ ((long) c[1] << 20) ^ c[2]);
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            java.util.Set<Long> reached = new java.util.HashSet<>();
            int[] first = w.taken.get(0);
            queue.add(first);
            reached.add(((long) first[0] << 40) ^ ((long) first[1] << 20) ^ first[2]);
            boolean open = false;
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                for (int k = 0; k < 6; k++) {
                    int x = c[0] + (k == 0 ? 1 : k == 1 ? -1 : 0), y = c[1] + (k == 2 ? 1 : k == 3 ? -1 : 0), z = c[2] + (k == 4 ? 1 : k == 5 ? -1 : 0);
                    long key = ((long) x << 40) ^ ((long) y << 20) ^ z;
                    if (set.contains(key)) {
                        if (reached.add(key)) queue.add(new int[]{x, y, z});
                    } else if (!w.filled(x, y, z)) open = true;
                }
            }
            if (reached.size() != set.size()) loose++;
            if (!open) sealedOff++;
            // its shape: spread across the blow and along it
            double mx = 0, my = 0, mz = 0;
            for (int[] c : w.taken) {
                mx += c[0];
                my += c[1];
                mz += c[2];
            }
            mx /= got;
            my /= got;
            mz /= got;
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz), va = 0, vt = 0;
            for (int[] c : w.taken) {
                double vx = c[0] - mx, vy = c[1] - my, vz = c[2] - mz, a = (vx * dx + vy * dy + vz * dz) / l;
                va += a * a;
                vt += vx * vx + vy * vy + vz * vz - a * a;
            }
            along += Math.sqrt(va / got);
            across += Math.sqrt(vt / got / 2);
            // blocks with nothing left are air
            for (var it = carved.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                if (com.antaurora.apofirstlight.earthwork.CarveChunk.count(en.getValue()) >= com.antaurora.apofirstlight.earthwork.CarveChunk.CELLS) {
                    kind[en.getKey()] = 0;
                    it.remove();
                    emptied++;
                }
            }
        }
        if (shortPieces > 0) bad.add("rock carve: " + shortPieces + " strikes took less than their piece");
        if (loose > 0) bad.add("rock carve: " + loose + " pieces not in one piece");
        if (sealedOff > 0) bad.add("rock carve: " + sealedOff + " pieces not touching open space");
        // the surface: every carved block's net, every uncarved full block's vanilla faces toward an air cell
        java.util.Map<String, Integer> edges = new java.util.HashMap<>();
        int quads = 0;
        int L = com.antaurora.apofirstlight.earthwork.CarveNet.L, LO = com.antaurora.apofirstlight.earthwork.CarveNet.LO;
        boolean[] solid = new boolean[L * L * L], isCarved = new boolean[27], cube = new boolean[27];
        float[][] vert = new float[com.antaurora.apofirstlight.earthwork.CarveNet.VERTS][];
        long meshNanos = 0;
        for (var en : carved.entrySet()) {
            int bx = en.getKey() % W, by = en.getKey() / W % W, bz = en.getKey() / (W * W);
            long t = System.nanoTime();
            for (int n = 0; n < 27; n++) {
                int nx = bx + n % 3 - 1, ny = by + n / 3 % 3 - 1, nz = bz + n / 9 - 1;
                int k = w.kindOf(nx, ny, nz);
                isCarved[n] = k == 1 && carved.containsKey(nx + W * (ny + W * nz));
                cube[n] = !isCarved[n] && k != 0;
            }
            for (int k = 0; k < L; k++) for (int j = 0; j < L; j++) for (int i = 0; i < L; i++)
                solid[com.antaurora.apofirstlight.earthwork.CarveNet.sample(i, j, k)] = w.filled(bx * 8 + i - LO, by * 8 + j - LO, bz * 8 + k - LO);
            final int ox = bx, oy = by, oz = bz;
            int[] count = {0};
            com.antaurora.apofirstlight.earthwork.CarveNet.build(solid, isCarved, cube, vert, (pts, axis, lowSolid, solidN, emptyN, emptySample) -> {
                float[][] p = new float[4][];
                for (int c = 0; c < 4; c++) p[c] = new float[]{pts[c][0] + ox, pts[c][1] + oy, pts[c][2] + oz};
                edgeLoop(edges, p);
                count[0]++;
            });
            meshNanos += System.nanoTime() - t;
            quads += count[0];
        }
        for (int bz = 0; bz < W; bz++) for (int by = 0; by < W; by++) for (int bx = 0; bx < W; bx++) {
            if (w.kindOf(bx, by, bz) == 0 || carved.containsKey(bx + W * (by + W * bz))) continue;
            for (int d = 0; d < 6; d++) {
                int axis = d / 2, sgn = d % 2 == 0 ? 1 : -1;
                int nx = bx + (axis == 0 ? sgn : 0), ny = by + (axis == 1 ? sgn : 0), nz = bz + (axis == 2 ? sgn : 0);
                if (w.kindOf(nx, ny, nz) != 0) continue;   // a full block or a carving (still a full block): culled
                float plane = (axis == 0 ? bx : axis == 1 ? by : bz) + (sgn > 0 ? 1 : 0);
                for (int u = 0; u < 8; u++) for (int v = 0; v < 8; v++) {
                    float a0 = u / 8F, a1 = (u + 1) / 8F, b0 = v / 8F, b1 = (v + 1) / 8F;
                    float[][] p;
                    // counter-clockwise seen from the + side (CarveNet's order), reversed for the - side
                    if (axis == 0) p = new float[][]{{plane, by + a0, bz + b0}, {plane, by + a1, bz + b0}, {plane, by + a1, bz + b1}, {plane, by + a0, bz + b1}};
                    else if (axis == 1) p = new float[][]{{bx + a0, plane, bz + b0}, {bx + a0, plane, bz + b1}, {bx + a1, plane, bz + b1}, {bx + a1, plane, bz + b0}};
                    else p = new float[][]{{bx + a0, by + b0, plane}, {bx + a1, by + b0, plane}, {bx + a1, by + b1, plane}, {bx + a0, by + b1, plane}};
                    if (sgn < 0) {
                        float[] t = p[1];
                        p[1] = p[3];
                        p[3] = t;
                    }
                    edgeLoop(edges, p);
                }
            }
        }
        int open = 0;
        for (var en : edges.entrySet()) {
            String[] ab = en.getKey().split(">");
            Integer back = edges.get(ab[1] + ">" + ab[0]);
            if (back == null || !back.equals(en.getValue())) open++;
        }
        if (open > 0) bad.add("rock carve: the surface has " + open + " unmatched edges (a gap or a flipped face)");
        int shapesOff = 0;
        for (long[] b : carved.values()) {
            double vol = 0;
            for (var box : com.antaurora.apofirstlight.earthwork.RockCarve.shapeOf(b).toAabbs()) vol += box.getXsize() * box.getYsize() * box.getZsize();
            if (Math.abs(vol - (512 - com.antaurora.apofirstlight.earthwork.CarveChunk.count(b)) / 512.0) > 1e-6) shapesOff++;
        }
        if (shapesOff > 0) bad.add("rock carve: " + shapesOff + " collision shapes not the sub-cells left");
        out("  rock carve: %d pieces, %d blocks carved, %d emptied; spread across %.2f, along %.2f sub-cells; %d net quads (%.0f a block, %.2f ms a block); %d unmatched edges",
                pieces, carved.size(), emptied, across / Math.max(1, pieces), along / Math.max(1, pieces), quads,
                carved.isEmpty() ? 0.0 : (double) quads / carved.size(), carved.isEmpty() ? 0.0 : meshNanos / 1e6 / carved.size(), open);
    }

    /**
     * U6 (docs/worldgen/underground_u6_rock_faces_design.md): a cave corridor in a rock mass, 5 wide, 4 high, 32 long, with
     * a side alcove behind a one-block wall, a one-block pillar and a non-rock cube (an ore) in a wall, for each rock look:
     * every worn block's net (RockRelief#bits, CarveNet) and the vanilla faces of every unworn block toward air must close
     * (every directed edge has its reverse as often); every top (cave floors, and since V3 the mass's tops under the open
     * sky) wears at most 2 sub-cells deep with no step over one sub-cell, rims (a top with an open side: rounded, its wall
     * wearing in under it) apart; no block wears through; the collision boxes hold exactly
     * the sub-cells left. Reports the quads and the time a worn block costs.
     */
    static void relief(List<String> bad) {
        final int X = 12, Y = 11, Z = 34;
        String[] names = {"granite", "sandstone", "limestone", "shale"};   // soil is no longer worn (dig R1)
        for (int look = 0; look < names.length; look++) {
            int[] kind = new int[X * Y * Z];   // 0 air, 1 rock, 2 another full cube (an ore)
            for (int z = 0; z < Z; z++) for (int y = 0; y < Y; y++) for (int x = 0; x < X; x++) {
                boolean corridor = x >= 3 && x <= 7 && y >= 3 && y <= 6 && z >= 1 && z <= 32;
                boolean alcove = x >= 9 && x <= 10 && y >= 3 && y <= 4 && z >= 10 && z <= 13;
                boolean door = x == 8 && y >= 3 && y <= 4 && z >= 11 && z <= 12;
                boolean pillar = x == 5 && z == 20 && y >= 3 && y <= 6;
                kind[x + X * (y + Y * z)] = (corridor || alcove || door) && !pillar ? 0 : 1;
            }
            kind[2 + X * (4 + Y * 15)] = 2;
            class Mass {
                final java.util.Map<Integer, long[]> occ = new java.util.HashMap<>();

                int kindOf(int bx, int by, int bz) {
                    return bx < 0 || by < 0 || bz < 0 || bx >= X || by >= Y || bz >= Z ? 0 : kind[bx + X * (by + Y * bz)];
                }

                int open(int bx, int by, int bz) {
                    int o = 0;
                    for (int d = 0; d < 6; d++)
                        if (kindOf(bx + (d == 4 ? -1 : d == 5 ? 1 : 0), by + (d == 0 ? -1 : d == 1 ? 1 : 0), bz + (d == 2 ? -1 : d == 3 ? 1 : 0)) == 0) o |= 1 << d;
                    return o;
                }

                long[] occ(int bx, int by, int bz) { return kindOf(bx, by, bz) == 1 ? occ.get(bx + X * (by + Y * bz)) : null; }

                /** something above the air over the block (RockRelief#roofed: the world-surface heightmap above y + 1) */
                boolean roofed(int bx, int by, int bz) {
                    for (int y = by + 2; y < Y; y++) if (kindOf(bx, y, bz) != 0) return true;
                    return false;
                }

                boolean filled(int x, int y, int z) {
                    int bx = Math.floorDiv(x, 8), by = Math.floorDiv(y, 8), bz = Math.floorDiv(z, 8), k = kindOf(bx, by, bz);
                    if (k != 1) return k == 2;
                    long[] b = occ(bx, by, bz);
                    return b == null || !com.antaurora.apofirstlight.earthwork.CarveChunk.gone(b, x & 7, y & 7, z & 7);
                }
            }
            Mass m = new Mass();
            long reliefNanos = 0;
            int throughs = 0, skyTops = 0, floors = 0, steps = 0, deepFloors = 0, rims = 0;
            for (int z = 0; z < Z; z++) for (int y = 0; y < Y; y++) for (int x = 0; x < X; x++) {
                if (m.kindOf(x, y, z) != 1) continue;
                int open = m.open(x, y, z);
                long t = System.nanoTime();
                long[] b = com.antaurora.apofirstlight.earthwork.RockRelief.bits(x, y, z, open, look);
                reliefNanos += System.nanoTime() - t;
                if (b == null) continue;
                if ((open & 2) != 0) {   // a top: how deep it goes and the steps across it (walked over), its rims apart
                    if (m.roofed(x, y, z)) floors++;
                    else skyTops++;
                    if ((open & 0b111100) != 0) rims++;
                    int[] top = new int[64];
                    boolean[] inner = new boolean[64];
                    for (int c = 0; c < 64; c++) {
                        int u = c & 7, v = c >> 3;
                        // a rim (a top with an open side: its wall wears in under it, its edge is rounded) is not walked across
                        inner[c] = (open & 0b111100) == 0;
                        int j = 7;
                        while (j >= 0 && com.antaurora.apofirstlight.earthwork.CarveChunk.gone(b, u, j, v)) j--;
                        top[c] = j;
                        if (inner[c] && 7 - j > 2) deepFloors++;
                    }
                    for (int c = 0; c < 64; c++) {
                        if ((c & 7) < 7 && inner[c] && inner[c + 1] && Math.abs(top[c] - top[c + 1]) > 1) steps++;
                        if ((c >> 3) < 7 && inner[c] && inner[c + 8] && Math.abs(top[c] - top[c + 8]) > 1) steps++;
                    }
                }
                if (com.antaurora.apofirstlight.earthwork.CarveChunk.count(b) >= com.antaurora.apofirstlight.earthwork.CarveChunk.CELLS) throughs++;
                m.occ.put(x + X * (y + Y * z), b);
            }
            if (throughs > 0) bad.add("rock relief " + names[look] + ": " + throughs + " blocks worn through");
            if (deepFloors > 0) bad.add("rock relief " + names[look] + ": " + deepFloors + " top columns away from the rims worn deeper than 2 sub-cells");
            if (steps > 0) bad.add("rock relief " + names[look] + ": " + steps + " top steps higher than a sub-cell away from the rims");
            if (skyTops == 0) bad.add("rock relief " + names[look] + ": no top under the open sky worn (V3 wears them)");
            java.util.Map<String, Integer> edges = new java.util.HashMap<>();
            int L = com.antaurora.apofirstlight.earthwork.CarveNet.L, LO = com.antaurora.apofirstlight.earthwork.CarveNet.LO;
            boolean[] solid = new boolean[L * L * L], isCarved = new boolean[27], cube = new boolean[27];
            float[][] vert = new float[com.antaurora.apofirstlight.earthwork.CarveNet.VERTS][];
            long meshNanos = 0;
            int quads = 0, vanillaFaces = 0, exposedFaces = 0;
            for (var en : m.occ.entrySet()) {
                int bx = en.getKey() % X, by = en.getKey() / X % Y, bz = en.getKey() / (X * Y);
                long t = System.nanoTime();
                for (int n = 0; n < 27; n++) {
                    int nx = bx + n % 3 - 1, ny = by + n / 3 % 3 - 1, nz = bz + n / 9 - 1;
                    isCarved[n] = m.occ(nx, ny, nz) != null;
                    cube[n] = !isCarved[n] && m.kindOf(nx, ny, nz) != 0;
                }
                for (int k = 0; k < L; k++) for (int j = 0; j < L; j++) for (int i = 0; i < L; i++)
                    solid[com.antaurora.apofirstlight.earthwork.CarveNet.sample(i, j, k)] = m.filled(bx * 8 + i - LO, by * 8 + j - LO, bz * 8 + k - LO);
                final int ox = bx, oy = by, oz = bz;
                int[] count = {0};
                com.antaurora.apofirstlight.earthwork.CarveNet.build(solid, isCarved, cube, vert, (pts, axis, lowSolid, solidN, emptyN, emptySample) -> {
                    float[][] p = new float[4][];
                    for (int c = 0; c < 4; c++) p[c] = new float[]{pts[c][0] + ox, pts[c][1] + oy, pts[c][2] + oz};
                    edgeLoop(edges, p);
                    count[0]++;
                });
                meshNanos += System.nanoTime() - t;
                quads += count[0];
            }
            for (int bz = 0; bz < Z; bz++) for (int by = 0; by < Y; by++) for (int bx = 0; bx < X; bx++) {
                if (m.kindOf(bx, by, bz) == 0) continue;
                boolean worn = m.occ(bx, by, bz) != null;
                for (int d = 0; d < 6; d++) {
                    int axis = d / 2, sgn = d % 2 == 0 ? 1 : -1;
                    int nx = bx + (axis == 0 ? sgn : 0), ny = by + (axis == 1 ? sgn : 0), nz = bz + (axis == 2 ? sgn : 0);
                    if (m.kindOf(nx, ny, nz) != 0) continue;
                    exposedFaces++;
                    if (worn) continue;   // its net draws it
                    vanillaFaces++;
                    float plane = (axis == 0 ? bx : axis == 1 ? by : bz) + (sgn > 0 ? 1 : 0);
                    for (int u = 0; u < 8; u++) for (int v = 0; v < 8; v++) {
                        float a0 = u / 8F, a1 = (u + 1) / 8F, b0 = v / 8F, b1 = (v + 1) / 8F;
                        float[][] p;
                        if (axis == 0) p = new float[][]{{plane, by + a0, bz + b0}, {plane, by + a1, bz + b0}, {plane, by + a1, bz + b1}, {plane, by + a0, bz + b1}};
                        else if (axis == 1) p = new float[][]{{bx + a0, plane, bz + b0}, {bx + a0, plane, bz + b1}, {bx + a1, plane, bz + b1}, {bx + a1, plane, bz + b0}};
                        else p = new float[][]{{bx + a0, by + b0, plane}, {bx + a1, by + b0, plane}, {bx + a1, by + b1, plane}, {bx + a0, by + b1, plane}};
                        if (sgn < 0) {
                            float[] t = p[1];
                            p[1] = p[3];
                            p[3] = t;
                        }
                        edgeLoop(edges, p);
                    }
                }
            }
            int open = 0;
            for (var en : edges.entrySet()) {
                String[] ab = en.getKey().split(">");
                Integer back = edges.get(ab[1] + ">" + ab[0]);
                if (back == null || !back.equals(en.getValue())) open++;
            }
            if (open > 0) bad.add("rock relief " + names[look] + ": the surface has " + open + " unmatched edges (a gap or a flipped face)");
            int shapesOff = 0;
            double worn = 0;
            for (long[] b : m.occ.values()) {
                double vol = 0;
                for (var box : com.antaurora.apofirstlight.earthwork.RockCarve.shapeOf(b).toAabbs()) vol += box.getXsize() * box.getYsize() * box.getZsize();
                if (Math.abs(vol - (512 - com.antaurora.apofirstlight.earthwork.CarveChunk.count(b)) / 512.0) > 1e-6) shapesOff++;
                worn += com.antaurora.apofirstlight.earthwork.CarveChunk.count(b);
            }
            if (shapesOff > 0) bad.add("rock relief " + names[look] + ": " + shapesOff + " collision shapes not the sub-cells left");
            int blocks = m.occ.size();
            out("  rock relief %s: %d worn blocks (%d cave floors, %d tops under the sky, %d of the tops rims), %d faces toward air (%d drawn the vanilla way); worn %.1f%% of their volume; %d net quads (%.0f a worn block); relief %.3f ms, net %.3f ms a block; %d unmatched edges",
                    names[look], blocks, floors, skyTops, rims, exposedFaces, vanillaFaces, blocks == 0 ? 0.0 : 100 * worn / (blocks * 512.0), quads, blocks == 0 ? 0.0 : (double) quads / blocks,
                    blocks == 0 ? 0.0 : reliefNanos / 1e6 / blocks, blocks == 0 ? 0.0 : meshNanos / 1e6 / blocks, open);
        }
    }

    /**
     * U6 V3: bare rock is never sloped (SmoothRules.ROCK), so soil beside it must never bare the rock's side, which the
     * rock leaves to the soil block (culled against it): random hills of soil with bare rock columns among them, every
     * soil top beside rock checked at the two corners of that side. Rock as high as the soil or higher: the corners stay
     * at the soil's top; rock lower: the corners stay over the rock's top (the soil slopes down onto it).
     */
    static void smoothRock(List<String> bad) {
        final int W = 40;
        java.util.Random rnd = new java.util.Random(24);
        int bared = 0, sloped = 0, onto = 0, besideRock = 0;
        for (int round = 0; round < 40; round++) {
            int[] h = new int[W * W];
            boolean[] rock = new boolean[W * W];
            for (int z = 0; z < W; z++) for (int x = 0; x < W; x++) {
                double v = 64 + 2.5 * Math.sin(x * 0.31 + round) + 2.5 * Math.cos(z * 0.27 - round * 0.7) + rnd.nextDouble() * 1.2;
                h[x + W * z] = (int) Math.floor(v);
                rock[x + W * z] = rnd.nextDouble() < 0.3;
            }
            com.antaurora.apofirstlight.terrainsmooth.SmoothRules.Cells cells = new com.antaurora.apofirstlight.terrainsmooth.SmoothRules.Cells() {
                @Override
                public int cell(int x, int y, int z) {
                    if (x < 0 || z < 0 || x >= W || z >= W) return com.antaurora.apofirstlight.terrainsmooth.SmoothRules.UNKNOWN;
                    int t = h[x + W * z];
                    if (y > t) return com.antaurora.apofirstlight.terrainsmooth.SmoothRules.AIR;
                    return rock[x + W * z] ? com.antaurora.apofirstlight.terrainsmooth.SmoothRules.ROCK : com.antaurora.apofirstlight.terrainsmooth.SmoothRules.NATURAL;
                }

                @Override
                public int surface(int x, int z) {
                    return x < 0 || z < 0 || x >= W || z >= W ? com.antaurora.apofirstlight.terrainsmooth.SmoothRules.NO_SURFACE : h[x + W * z];
                }
            };
            // sides N, S, W, E: neighbour offset and the corners (nw 0, ne 1, sw 2, se 3) along it
            int[][] side = {{0, -1, 0, 1}, {0, 1, 2, 3}, {-1, 0, 0, 2}, {1, 0, 1, 3}};
            for (int z = 1; z < W - 1; z++) for (int x = 1; x < W - 1; x++) {
                int y = h[x + W * z];
                // a soil wall (a rim over a drop of two or more) is worn like rock: it is checked as the rock beside, not as soil
                if (rock[x + W * z] || com.antaurora.apofirstlight.ground.GroundClass.soilWall(cells, x, y, z)) continue;
                long g = com.antaurora.apofirstlight.terrainsmooth.SmoothRules.geometry(cells, com.antaurora.apofirstlight.terrainsmooth.SmoothRules.MODE_B, x, y, z);
                if (g >= 0) sloped++;
                float[] c = g < 0 ? new float[]{1, 1, 1, 1} : com.antaurora.apofirstlight.terrainsmooth.SmoothRules.corners(g);
                for (int[] sd : side) {
                    int n = x + sd[0] + W * (z + sd[1]);
                    if (!rock[n] && !com.antaurora.apofirstlight.ground.GroundClass.soilWall(cells, x + sd[0], h[n], z + sd[1])) continue;
                    besideRock++;
                    int hr = h[n];
                    for (int k = 2; k < 4; k++) {
                        float top = y + c[sd[k]];
                        if (hr >= y ? top < y + 1 - 1e-4F : top < hr + 1 - 1e-4F) bared++;
                        else if (hr < y && top < y + 1 - 1e-4F) onto++;
                    }
                }
            }
        }
        if (bared > 0) bad.add("smooth rock: " + bared + " soil corners beside bare rock bare its side");
        if (onto == 0) bad.add("smooth rock: soil never slopes down onto a lower rock");
        out("  smooth rock: %d soil tops sloped, %d beside bare rock or a soil wall, %d corners sloping down onto lower rock, %d baring a rock side",
                sloped, besideRock, onto, bared);
    }

    /** The four directed edges of a quad (zero-length ones skipped), quantized to 1/4096. */
    static void edgeLoop(java.util.Map<String, Integer> edges, float[][] p) {
        for (int c = 0; c < 4; c++) {
            String a = q(p[c]), b = q(p[(c + 1) % 4]);
            if (!a.equals(b)) edges.merge(a + ">" + b, 1, Integer::sum);
        }
    }

    static String q(float[] v) {
        return Math.round(v[0] * 4096) + "," + Math.round(v[1] * 4096) + "," + Math.round(v[2] * 4096);
    }

    /** The largest shares of a per-RockType tally. */
    static String shares(long[] v, int top) {
        long n = 0;
        for (long k : v) n += k;
        final long all = Math.max(1, n);
        StringBuilder sb = new StringBuilder();
        java.util.stream.IntStream.range(0, v.length).boxed().filter(i -> v[i] > 0).sorted((a, b) -> Long.compare(v[b], v[a])).limit(top)
                .forEach(i -> sb.append(String.format(Locale.ROOT, " %s %.1f%%", RockType.values()[i].id, 100.0 * v[i] / all)));
        return sb.toString();
    }

    static Result check(String tag, long seed, TerrainPlanSurface s, FederalMap map, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) throws Exception {
        List<String> bad = new ArrayList<>();
        long t0 = System.nanoTime();
        GeologyModel g = new GeologyModel(seed, s, map, -256);
        long t1 = System.nanoTime();
        GeoProtect p = new GeoProtect(s, roads, towns, h);
        long t2 = System.nanoTime();
        UndergroundPlan u = UndergroundPlanner.build(g, p);
        long t3 = System.nanoTime();
        long geoMs = (t1 - t0) / 1_000_000, protMs = (t2 - t1) / 1_000_000, planMs = (t3 - t2) / 1_000_000;
        out("  times: geology %d ms, protection %d ms, plan %d ms %s", geoMs, protMs, planMs, u.times);
        out("  planner notes: %s", u.notes);
        if (geoMs + planMs > 3000) out("  NOTE planning over the 3 s target (geology + plan %d ms)", geoMs + planMs);
        {   // terrain rework R1: GroundClass answers as the frozen rules on this seed's planned ground
            double span = s.gridN * TerrainPlanSurface.CELL;
            out("  %s", com.antaurora.apofirstlight.ground.GroundClassCheck.seedPatches(bad, seed, g, u, (x, z) -> s.waterAt(x, z),
                    new double[]{s.gridOrigin + span * 0.1, s.gridOrigin + span * 0.9}));
        }

        // ---- geology by state (10, 4): bedrock shares on a 256 m grid
        Map<FederalStateId, EnumMap<RockType, Integer>> byState = new TreeMap<>();
        Map<FederalStateId, Integer> stateN = new TreeMap<>();
        double size = s.gridN * TerrainPlanSurface.CELL;
        for (double z = s.gridOrigin + 128; z < s.gridOrigin + size; z += 256) for (double x = s.gridOrigin + 128; x < s.gridOrigin + size; x += 256) {
            if (s.waterAt(x, z) || !map.isLand(x, z)) continue;
            FederalStateId st = map.stateAt(x, z);
            if (st == null) continue;
            RockType b = g.bedrock(g.column(x, z));
            byState.computeIfAbsent(st, q -> new EnumMap<>(RockType.class)).merge(b, 1, Integer::sum);
            stateN.merge(st, 1, Integer::sum);
        }
        out("  bedrock by state (share of land columns, 256 m grid):");
        for (var e : byState.entrySet()) {
            StringBuilder sb = new StringBuilder();
            int n = stateN.get(e.getKey());
            e.getValue().entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                    .forEach(q -> sb.append(String.format(Locale.ROOT, " %s %.0f%%", q.getKey().id, 100.0 * q.getValue() / n)));
            out("    %-14s n=%4d%s", e.getKey(), n, sb);
        }
        out("  faults: %d (%s)", g.faultList().size(), faultText(g));
        {
            // the shield edge north of the lake basin (U3b design 14.9): its land, its bedrock, its basement depth
            int n = 0, full = 0;
            double depth = 0;
            EnumMap<RockType, Integer> bed = new EnumMap<>(RockType.class);
            for (double z = s.gridOrigin + 128; z < s.gridOrigin + size; z += 256) for (double x = s.gridOrigin + 128; x < s.gridOrigin + size; x += 256) {
                if (s.waterAt(x, z) || !map.isLand(x, z)) continue;
                Column c = g.column(x, z);
                if (c.shield < 0.5) continue;
                n++;
                if (c.shield > 0.95) full++;
                depth += c.hs - c.tops[0];
                bed.merge(g.bedrock(c), 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder();
            int nn = n;
            bed.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4)
                    .forEach(q -> sb.append(String.format(Locale.ROOT, " %s %.0f%%", q.getKey().id, 100.0 * q.getValue() / nn)));
            out("  shield edge: %d land cells over half (%.1f km2), %d full; basement %.0f m down on average; bedrock%s",
                    n, n * 0.065536, full, n == 0 ? 0 : depth / n, sb);
            if (n == 0) bad.add("no shield edge north of the lake basin");
        }
        geologyPass(bad, seed, s, map, g);
        oreRocks(bad, seed, u);
        {
            int landN = 0, road = 0, town = 0, home = 0, wet = 0, river = 0, ok = 0;
            for (double z = s.gridOrigin + 128; z < s.gridOrigin + size; z += 256) for (double x = s.gridOrigin + 128; x < s.gridOrigin + size; x += 256) {
                if (s.waterAt(x, z) || !map.isLand(x, z)) continue;
                landN++;
                if (p.roadDistance(x, z) < GeoProtect.ENTRANCE_ROAD) road++;
                if (p.townDistance(x, z) < GeoProtect.ENTRANCE_TOWN) town++;
                if (p.homeDistance(x, z) < GeoProtect.ENTRANCE_HOME) home++;
                if (p.wet(x, z)) wet++;
                if (s.rivers != null && s.rivers.nearestEdge(x, z, 31, new double[1]) < GeoProtect.ENTRANCE_RIVER) river++;
                if (p.entranceAllowed(EntranceKind.SHAFT, x, z)) ok++;
            }
            out("  entrance rules on %d land cells: road %d, town %d, home %d, wet %d, river %d; allowed %d", landN, road, town, home, wet, river, ok);
        }

        // ---- counts
        EnumMap<CaveKind, Integer> kinds = new EnumMap<>(CaveKind.class);
        double passKarst = 0;
        for (Cave c : u.caves) if (!c.label.equals("removed")) kinds.merge(c.kind, 1, Integer::sum);
        for (Cave c : u.caves) if (c.kind == CaveKind.KARST_SYSTEM) passKarst += c.passageLength;
        int karst = kinds.getOrDefault(CaveKind.KARST_SYSTEM, 0), frac = kinds.getOrDefault(CaveKind.FRACTURE_SYSTEM, 0);
        int giants = kinds.getOrDefault(CaveKind.GIANT, 0), canyons = kinds.getOrDefault(CaveKind.CANYON, 0);
        out("  caves: %s; karst passage %.1f km (%.0f m a system)", kinds, passKarst / 1000, karst == 0 ? 0 : passKarst / karst);
        EnumMap<EntranceKind, int[]> ents = new EnumMap<>(EntranceKind.class);
        for (Entrance e : u.entrances) {
            int[] c = ents.computeIfAbsent(e.kind(), q -> new int[2]);
            c[e.connected() ? 0 : 1]++;
        }
        StringBuilder es = new StringBuilder();
        for (var e : ents.entrySet()) es.append(String.format(Locale.ROOT, " %s %d+%d", e.getKey().name().toLowerCase(Locale.ROOT), e.getValue()[0], e.getValue()[1]));
        out("  entrances (connected+dead):%s; total %d", es, u.entrances.size());
        EnumMap<UndergroundPlan.OreKind, int[]> ores = new EnumMap<>(UndergroundPlan.OreKind.class);
        for (UndergroundPlan.District d : u.districts) ores.computeIfAbsent(d.kind(), q -> new int[2])[0]++;
        for (OreBody o : u.ores) ores.computeIfAbsent(o.kind(), q -> new int[2])[1]++;
        StringBuilder os = new StringBuilder();
        for (var e : ores.entrySet()) os.append(String.format(Locale.ROOT, " %s %d/%d", e.getKey().name().toLowerCase(Locale.ROOT), e.getValue()[0], e.getValue()[1]));
        out("  ore districts/bodies:%s; mines %d", os, u.mines.size());
        range(bad, "karst systems", karst, 50, 120);
        range(bad, "fracture systems", frac, 20, 40);
        range(bad, "giant caves", giants, 3, 6);
        range(bad, "canyons", canyons, 1, 2);
        rangeNote("collapse pits", count(ents, EntranceKind.COLLAPSE_PIT), 300, 800);
        rangeNote("cover sinkholes", count(ents, EntranceKind.COVER_SINKHOLE), 40, 100);
        range(bad, "great sinkholes", count(ents, EntranceKind.GREAT_SINKHOLE), 4, 8);
        rangeNote("hillside mouths", count(ents, EntranceKind.HILLSIDE_MOUTH), 30, 80);
        rangeNote("fissures + shafts", count(ents, EntranceKind.FISSURE) + count(ents, EntranceKind.SHAFT), 50, 150);
        // the states' own ways in (21.10)
        out("  state entrances: spring mouths %d, karst windows %d, cliff mouths %d, wave-cut caves %d",
                count(ents, EntranceKind.SPRING_MOUTH), count(ents, EntranceKind.KARST_WINDOW), count(ents, EntranceKind.CLIFF_MOUTH), count(ents, EntranceKind.WAVE_CUT));
        if (u.facility == null) bad.add("no research facility candidate");

        // ---- layer air by Monte Carlo (7.2), depth histogram
        double[][] mc = monteCarlo(g, u, 400_000, seed);
        String[] ln = {"", "L1", "L2", "L3", "L4"};
        double[] target = {0, 0, 0.01, 0.04, 0};
        for (int L = 1; L <= 4; L++) {
            out("  %s: rock %.2f km3, air %.4f%% (karst %.3f%%, non-karst %.3f%%), water share of air %.0f%%", ln[L], mc[L][0] / 1e9,
                    100 * mc[L][1] / Math.max(1, mc[L][0]), 100 * mc[L][3] / Math.max(1, mc[L][2]), 100 * (mc[L][1] - mc[L][3]) / Math.max(1, mc[L][0] - mc[L][2]),
                    100 * mc[L][4] / Math.max(1, mc[L][1]));
        }
        if (mc[2][3] / Math.max(1, mc[2][2]) > target[2] * 1.5) bad.add("layer 2 karst air over 1.5 % of the 1 % target");
        int l1 = layer1Breaks(g, u);
        out("  layer 1 (no caves): %d non-throat shapes reach it", l1);
        if (l1 > 0) bad.add(l1 + " shapes in layer 1");
        depthPng(new File(OUT, tag + "_depth.png"), mc[5], mc[6]);

        // ---- protection (12.2): every shape against a fresh protection (roads, towns, home), the facility separately
        GeoProtect p0 = new GeoProtect(s, roads, towns, h);
        int viol = 0;
        int facOwner = u.facility == null ? -1 : u.facility.id(), facLink = u.facility == null ? -1 : u.facility.link();
        Map<String, Integer> why = new TreeMap<>();
        for (int i = 0; i < u.shapes.size(); i++) {
            Shape sh = u.shapes.get(i);
            if (sh.bounds()[1] < -1e5) continue;
            Cave c = u.caves.get(sh.owner());
            if (sh.solid()) continue;
            String v = p0.violation(sh, c.layer);
            if (v == null && sh.owner() != facOwner && sh.owner() != facLink && p.facilityHit(sh)) v = "facility";
            if (v != null && c.kind == CaveKind.BUILT && c.label.startsWith("mine")) v = p0.violation(sh, 3);
            if (v != null) { viol++; why.merge(v.split(" ")[0] + " " + c.kind, 1, Integer::sum); if (viol <= 5) out("    violation: cave %d %s %s", c.id, c.kind, v); }
        }
        int entBad = 0;
        double nearRoad = 1e9, nearTown = 1e9, nearHome = 1e9;
        for (Entrance e : u.entrances) {
            if (e.kind() == EntranceKind.FACILITY) continue;
            double rd = p0.roadDistance(e.x(), e.z()), td = p0.townDistance(e.x(), e.z()), hd = p0.homeDistance(e.x(), e.z());
            nearRoad = Math.min(nearRoad, rd);
            nearTown = Math.min(nearTown, td);
            nearHome = Math.min(nearHome, hd);
            if (!p0.entranceAllowed(e.kind(), e.x(), e.z())) entBad++;
        }
        out("  protection: %d shape violations %s, %d entrances break a rule; nearest entrance to a road %.0f m, a town %.0f m, the home %.0f m",
                viol, why, entBad, nearRoad, nearTown, nearHome);
        if (viol > 0) bad.add(viol + " shapes break protection");
        if (entBad > 0) bad.add(entBad + " entrances break protection");

        // ---- facility (19.5)
        if (u.facility != null) {
            var fc = u.facility;
            StringBuilder fs = new StringBuilder();
            for (FacilityPart fp : fc.parts()) fs.append(' ').append(fp.role());
            Place lab = null, portal = null;
            for (FacilityPart fp : fc.parts()) {
                if (fp.role().equals("laboratory")) lab = u.places.get(fp.place());
                if (fp.role().equals("hidden_entrance")) portal = u.places.get(fp.place());
            }
            double cover = lab == null ? 0 : s.heightAt(lab.x(), lab.z()) - (lab.y() + 12);
            double sr = fc.serviceRoad().length < 6 ? 0 : Math.hypot(fc.serviceRoad()[3] - fc.serviceRoad()[0], fc.serviceRoad()[5] - fc.serviceRoad()[2]);
            out("  facility at (%.0f, %.0f) in %s: parts%s; lab cover %.0f m; portal to road %.0f m, town %.0f m, home %.0f m; service road %.0f m at %.1f%%; rock passage %s",
                    fc.x(), fc.z(), fc.host() == null ? "-" : fc.host().id, fs, cover,
                    portal == null ? -1 : p0.roadDistance(portal.x(), portal.z()), lab == null ? -1 : p0.townDistance(lab.x(), lab.z()), lab == null ? -1 : p0.homeDistance(lab.x(), lab.z()),
                    sr, 100 * fc.serviceGrade(), fc.link() >= 0 ? "yes" : "no");
            if (cover < 60) bad.add("facility lab cover " + Math.round(cover) + " m");
            if (lab != null && p0.townDistance(lab.x(), lab.z()) < 3000) bad.add("facility within 3 km of a town");
        }

        // ---- connectivity (7.4)
        int[] comp = components(u);
        Map<Integer, Integer> compSize = new HashMap<>();
        for (Cave c : u.caves) if (!c.label.equals("removed")) compSize.merge(comp[c.id], 1, Integer::sum);
        int sysCon = 0, sysLinked = 0, sysEnt = 0, gLinked = 0, karstN = 0, fracN = 0, karstCon = 0, fracCon = 0, karstEnt = 0, fracEnt = 0;
        BitSet hasEnt = new BitSet();
        for (Entrance e : u.entrances) if (e.connected()) hasEnt.set(e.target());
        BitSet compEnt = new BitSet();
        for (Cave c : u.caves) if (hasEnt.get(c.id)) compEnt.set(comp[c.id]);
        for (Cave c : u.caves) {
            if (c.system()) {
                boolean con = false, lk = false;
                for (CaveLink l : u.links) {
                    if (l.a() == c.id && l.kind().equals("connector")) con = true;
                    if ((l.a() == c.id || l.b() == c.id) && l.kind().equals("karst_link")) lk = true;
                }
                boolean en = compEnt.get(comp[c.id]);
                if (c.kind == CaveKind.KARST_SYSTEM) { karstN++; if (con) karstCon++; if (en) karstEnt++; } else { fracN++; if (con) fracCon++; if (en) fracEnt++; }
                if (lk) sysLinked++;
            }
            if (c.kind == CaveKind.GIANT) for (CaveLink l : u.links) if (l.b() == c.id) { gLinked++; break; }
        }
        int largest = compSize.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        out("  connectivity: karst with connector %d/%d (%.0f%%, target 60), fracture %d/%d (%.0f%%, target 20); karst linked to a neighbour %d; giants linked %d/%d (target 50%%);",
                karstCon, karstN, pct(karstCon, karstN), fracCon, fracN, pct(fracCon, fracN), sysLinked, gLinked, giants);
        out("    reachable from the surface: karst %d/%d (%.0f%%, target 40), fracture %d/%d (%.0f%%, target 15); %d links; largest network %d caves",
                karstEnt, karstN, pct(karstEnt, karstN), fracEnt, fracN, pct(fracEnt, fracN), u.links.size(), largest);

        // ---- routes (19.3)
        List<BitSet> in = new ArrayList<>();
        StringBuilder rs = new StringBuilder();
        int trapsAll = 0;
        for (Kit k : UndergroundPlan.KITS) {
            BitSet ri = UndergroundRoutes.reachIn(u, k);
            in.add(ri);
            BitSet tr = UndergroundRoutes.traps(u, k);
            trapsAll += tr.cardinality();
            rs.append(String.format(Locale.ROOT, " %s in %d traps %d;", k.name(), ri.cardinality(), tr.cardinality()));
            if (tr.cardinality() > 0) {
                int t = tr.nextSetBit(0);
                Place q = u.places.get(t);
                out("    trap with %s: place %d %s at (%.0f, %.0f, %.0f) cave %d", k.name(), t, q.kind(), q.x(), q.y(), q.z(), q.cave());
            }
        }
        out("  routes:%s", rs);
        {
            // where the widest kit stops: steps leaving its reach towards places the step graph alone would reach
            BitSet full = in.get(in.size() - 1);
            Kit top = UndergroundPlan.KITS.get(UndergroundPlan.KITS.size() - 1);
            Map<String, Integer> stop = new TreeMap<>();
            for (Step st : u.steps) for (int side = 0; side < 2; side++) {
                int from = side == 0 ? st.a() : st.b(), to = side == 0 ? st.b() : st.a();
                if (!full.get(from) || full.get(to)) continue;
                Need nd = side == 0 ? st.ab() : st.ba();
                String stopWhy = nd == null ? "none" : nd.dive() ? "dive" : nd.rope() > top.rope() ? "rope>" + top.rope() : nd.anchors() > top.anchors() ? "anchors" : "other";
                stop.merge(st.kind().name().toLowerCase(Locale.ROOT) + ":" + stopWhy, 1, Integer::sum);
            }
            out("    widest kit stops at: %s", stop);
        }
        if (trapsAll > 0) bad.add(trapsAll + " trap places (in, not out)");
        String cut = cutCheck(g, u, seed);
        if (cut != null) bad.add(cut);
        StringBuilder gs = new StringBuilder();
        int giantReach = 0;
        for (Cave c : u.caves) if (c.kind == CaveKind.GIANT || c.kind == CaveKind.CANYON) {
            int k = UndergroundRoutes.leastKitIn(u, c.places.get(0), in);
            if (c.kind == CaveKind.GIANT && k >= 0) giantReach++;
            gs.append(String.format(Locale.ROOT, " %s %s depth %.0f m kit %s;", c.kind == CaveKind.GIANT ? c.label : "canyon", c.host == null ? "-" : c.host.id,
                    g.hs(c.x, c.z) - u.places.get(c.places.get(0)).y(), k < 0 ? "none" : UndergroundPlan.KITS.get(k).name()));
        }
        out("  deep targets:%s", gs);
        if (giantReach < 1) bad.add("no giant cave reachable from the surface");
        int[] kitHist = new int[UndergroundPlan.KITS.size() + 1];
        for (Cave c : u.caves) if (c.system()) {
            int best = -1;
            for (int pl : c.places) { int k = UndergroundRoutes.leastKitIn(u, pl, in); if (k >= 0 && (best < 0 || k < best)) best = k; }
            kitHist[best < 0 ? kitHist.length - 1 : best]++;
        }
        out("  systems by least kit to reach any place: %s (last = not reachable)", Arrays.toString(kitHist));
        int walkSys = 0, sysN = 0;
        for (int i = 0; i + 1 < kitHist.length; i++) walkSys += kitHist[i];
        for (int v : kitHist) sysN += v;
        // linked to an entrance is not walkable: a sump on the way needs diving (V3+); the walkable share is the honest figure
        out("  systems walkable in from the surface (no diving): %d/%d (%.0f%%)", walkSys, sysN, pct(walkSys, sysN));
        rangeNote("percent of systems walkable in", (int) Math.round(pct(walkSys, sysN)), 15, 60);
        // systems by state (C3 per-state caves): depth below the ground, water table below the ground, dry places
        {
            Map<FederalStateId, double[]> st = new TreeMap<>();
            double nearDry = 1e9, nearAny = 1e9;
            for (Cave c : u.caves) if (c.system() || c.kind == CaveKind.GIANT) {
                FederalStateId sid = map.stateAt(c.x, c.z);
                if (sid == null) continue;
                GeologyModel.Column col = g.column(c.x, c.z);
                int dry = 0;
                for (int pl : c.places) if (u.places.get(pl).y() + 1 >= c.waterY) dry++;
                double[] a = st.computeIfAbsent(sid, q -> new double[6]);
                a[0]++; a[1] += col.h - c.y; a[2] += col.h - c.waterY; a[3] += c.places.isEmpty() ? 0 : (double) dry / c.places.size();
                if (dry * 2 >= Math.max(1, c.places.size())) a[4]++;
                if (c.label.contains("vadose")) a[5]++;
                double hd = p.homeDistance(c.x, c.z);
                nearAny = Math.min(nearAny, hd);
                if (dry * 2 >= Math.max(1, c.places.size())) nearDry = Math.min(nearDry, hd);
            }
            out("  systems by state (n, mean depth below ground, water table below ground, dry share of places, mostly dry, vadose):");
            for (var e : st.entrySet()) {
                double[] a = e.getValue();
                out("    %-14s n=%3.0f depth %4.0f m, water table %4.0f m, dry places %3.0f%%, mostly dry %2.0f, vadose %2.0f", e.getKey(), a[0], a[1] / a[0], a[2] / a[0], 100 * a[3] / a[0], a[4], a[5]);
            }
            out("  nearest system to the home %.0f m, nearest mostly dry one %.0f m", nearAny, nearDry);
            // the plan forms by state (21.10): strike S1, branchwork S2, maze S3, small S4, conduit S5
            Map<FederalStateId, Map<String, Integer>> fm = new TreeMap<>();
            for (Cave c : u.caves) if (c.system()) {
                FederalStateId sid = map.stateAt(c.x, c.z);
                if (sid == null) continue;
                String f = "plain";
                for (String q : new String[]{"strike", "branchwork", "maze", "small", "conduit"}) if (c.label.endsWith(q) || c.label.contains(q + " ")) f = q;
                fm.computeIfAbsent(sid, q -> new TreeMap<>()).merge(f, 1, Integer::sum);
            }
            out("  plan forms by state: %s", fm);
        }
        // the start dry cave (21.6): a mostly dry natural system within 3 km of the home that a light reaches from the surface
        {
            BitSet lit = UndergroundRoutes.reachIn(u, UndergroundPlan.KITS.get(1));
            double best = 1e9;
            int bestId = -1, bestIn = 0;
            for (Cave c : u.caves) {
                if (!c.system() && c.kind != CaveKind.GIANT) continue;
                int dry = 0, inLit = 0;
                for (int pl : c.places) {
                    if (u.places.get(pl).y() + 1 >= c.waterY) dry++;
                    if (lit.get(pl)) inLit++;
                }
                if (inLit == 0 || dry * 2 < Math.max(1, c.places.size())) continue;
                double hd = p.homeDistance(c.x, c.z);
                if (hd < best) { best = hd; bestId = c.id; bestIn = inLit; }
            }
            if (bestId < 0) out("  start dry cave: none reached with a light");
            else {
                Cave c = u.caves.get(bestId);
                out("  start dry cave: cave %d %s (%s) %.0f m from the home, %d of %d places reached with a light, %.0f m of passage%s",
                        bestId, c.kind, c.label, best, bestIn, c.places.size(), c.passageLength, u.startCave == bestId ? "" : " (planner's start cave " + u.startCave + ")");
            }
            if (best > 3000) bad.add("no light-only dry cave within 3 km of the home");
        }
        // the start cave as a destination (U5 sample, design 24): named, walked in with a light, every route point open
        {
            UndergroundPlan.Destination d = null;
            for (UndergroundPlan.Destination q : u.destinations) if (q.kind() == UndergroundPlan.DestinationKind.START_CAVE) d = q;
            if (d == null) {
                out("  start destination: none");
                if (u.startCave >= 0) bad.add("the start cave has no destination");
            } else {
                int shut = 0;
                for (int i = 1; i < d.route().size(); i++) {
                    double[] q = d.route().get(i);
                    if (!u.airAt(q[0], q[1] + 1, q[2])) shut++;
                }
                UndergroundPlan.Entrance e = d.entrance() >= 0 ? u.entrances.get(d.entrance()) : null;
                out("  start destination: %s, cave %d, entrance %s, %.0f m from the home (bearing %.0f), road %.0f m; way in kit %s, %.0f m, down %.0f m, %d places, %d not open",
                        d.name(), d.cave(), e == null ? "none" : e.id() + " " + e.kind(), d.homeDistance(), d.homeBearing(), d.roadWalk(),
                        UndergroundPlan.KITS.get(d.kit()).name(), d.length(), d.drop(), d.route().size(), shut);
                if (d.cave() != u.startCave) bad.add("the start destination is not the start cave");
                if (d.kit() > 1) bad.add("the start destination needs more than a light");
                if (e == null) bad.add("the start destination has no entrance");
                if (shut > 0) bad.add(shut + " start destination route points not open");
            }
        }
        // the trail to the start destination's mouth (U5b, design 24.5): from a two-lane or county road's shoulder to the
        // rim, a metre apart, never on another cave's cut, a town or the home's ground; the sign and the markers each in one chunk
        {
            String trailLine = "trail: none";
            for (UndergroundPlan.Trail t : u.trails) {
                UndergroundPlan.Destination d = u.destinations.get(t.destination());
                double[] l = t.line(), rim = d.route().get(0);
                int n = t.points();
                RoadDesign road = null;
                for (RoadDesign r : roads) if (r.id.equals(t.road())) road = r;
                double head = road == null ? 0 : Double.MAX_VALUE;
                if (road != null) for (double q = 0; q <= road.length; q += 0.5) {
                    double[] c = road.point(q, 0);
                    head = Math.min(head, Math.hypot(c[0] - l[0], c[1] - l[1]));
                }
                double gap = 0, cutM = 0, homeMin = Double.MAX_VALUE, townMin = Double.MAX_VALUE, rise = 0, fall = 0;
                for (int i = 0; i < n; i++) {
                    double x = l[i * 3], z = l[i * 3 + 1];
                    if (i > 0) {
                        gap = Math.max(gap, Math.hypot(x - l[i * 3 - 3], z - l[i * 3 - 2]));
                        double dy = l[i * 3 + 2] - l[i * 3 - 1];
                        if (dy > 0) rise += dy; else fall -= dy;
                    }
                    homeMin = Math.min(homeMin, p.homeDistance(x, z));
                    townMin = Math.min(townMin, p.townDistance(x, z));
                    double plan = s.heightAt(x, z);
                    for (int id : u.shapesNear(x, z)) {
                        Shape sh = u.shapes.get(id);
                        double[] b = sh.bounds();
                        if (x < b[0] || x > b[3] || z < b[2] || z > b[5]) continue;
                        if (rim[0] >= b[0] - 4 && rim[0] <= b[3] + 4 && rim[2] >= b[2] - 4 && rim[2] <= b[5] + 4) continue;
                        if ((sh instanceof UndergroundPlan.Open o && o.floorAt(x, z) < plan - 0.5) || (sh instanceof Talus tl && tl.surfaceAt(x, z) > plan + 0.3)) { cutM++; break; }
                    }
                }
                double markGap = 0, prev = 0;
                java.util.List<double[]> along = new java.util.ArrayList<>();
                for (var m : t.markers()) {
                    double best = Double.MAX_VALUE, at = 0;
                    for (int i = 0; i < n; i++) {
                        double dd = Math.hypot(l[i * 3] - m.x(), l[i * 3 + 1] - m.z());
                        if (dd < best) { best = dd; at = i; }
                    }
                    along.add(new double[]{at, best});
                }
                along.sort((a, b) -> Double.compare(a[0], b[0]));
                double off = 0;
                for (double[] a : along) { markGap = Math.max(markGap, a[0] - prev); prev = a[0]; off = Math.max(off, a[1]); }
                markGap = Math.max(markGap, t.length() - prev);
                int split = 0;
                for (var m : t.markers()) if (!CaveTrail.oneChunk(m)) split++;
                if (t.sign() != null && !CaveTrail.oneChunk(t.sign())) split++;
                if (t.caution() != null && !CaveTrail.oneChunk(t.caution())) split++;
                double cautionBack = -1;
                if (t.caution() != null) {
                    double cb = Double.MAX_VALUE;
                    int at = 0;
                    for (int i = 0; i < n; i++) {
                        double dd = Math.hypot(l[i * 3] - t.caution().x(), l[i * 3 + 1] - t.caution().z());
                        if (dd < cb) { cb = dd; at = i; }
                    }
                    cautionBack = n - 1 - at;
                }
                double straight = Math.hypot(rim[0] - l[0], rim[2] - l[1]);
                int steepAt = 0;
                double steep = -1, over20 = 0;
                for (int i = 0; i + 10 < n; i++) {
                    double gr = Math.abs(l[(i + 10) * 3 + 2] - l[i * 3 + 2]) / 10;
                    if (gr > steep) { steep = gr; steepAt = i; }
                    if (gr > 0.2) over20++;
                }
                trailLine = String.format(Locale.ROOT, "trail: road %s (%s), trailhead %.1f m from its centre (paved half width %.2f); %.0f m walked (%.1f mi, sign %s), straight %.0f m (x%.2f); climbs %.0f m, descends %.0f m, steepest 10 m %.0f%% at %d m (%.0f m of 10 m stretches over 20%%); %d fords; %d markers (largest gap %.0f m, %.1f m off the line at most), caution sign %s; %d fixtures across chunks; nearest home %.0f m, town %.0f m; %.0f m on another cut; spacing %.2f m",
                        t.road(), road == null ? "?" : road.section.name, head, road == null ? 0 : road.section.edge, t.length(), t.length() / CaveTrail.MILE,
                        t.sign() == null ? "none" : t.sign().bone(), straight, t.length() / Math.max(1, straight), rise, fall, 100 * t.grade(), steepAt, over20, t.fords(), t.markers().size(), markGap, off,
                        cautionBack < 0 ? "none" : String.format(Locale.ROOT, "%.0f m before the rim", cautionBack), split, homeMin, townMin, cutM, gap);
                boolean fromTown = t.road().startsWith("town:");
                if (fromTown) {
                    double td = p.townDistance(l[0], l[1]);
                    trailLine += String.format(Locale.ROOT, "; from the town's edge (%.0f m out)", td);
                    if (td < CaveTrail.TOWN_CLEAR - 1 || td > CaveTrail.TOWN_CLEAR + 8) bad.add(String.format(Locale.ROOT, "the town trailhead is %.0f m from the town", td));
                } else if (road == null || !CaveTrail.trailhead(road)) bad.add("the trail does not leave a two-lane or county road");
                else if (head > road.section.edge + 1.5) bad.add(String.format(Locale.ROOT, "the trailhead is %.1f m from its road's centre", head));
                if (Math.hypot(l[(n - 1) * 3] - rim[0], l[(n - 1) * 3 + 1] - rim[2]) > 0.5) bad.add("the trail does not end at the rim");
                if (gap > 1.2) bad.add(String.format(Locale.ROOT, "trail points %.2f m apart", gap));
                if (t.grade() > 0.6) bad.add(String.format(Locale.ROOT, "the trail climbs %.0f%% over 10 m", 100 * t.grade()));
                if (cutM > 0) bad.add(String.format(Locale.ROOT, "%.0f m of the trail on another cave's cut", cutM));
                if (homeMin < CaveTrail.HOME_CLEAR - 5 || townMin < CaveTrail.TOWN_CLEAR - 5) bad.add("the trail runs through the home's ground or a town");
                if (t.sign() == null) bad.add("the trail has no trailhead sign");
                if (t.caution() == null) bad.add("the trail has no caution sign before the mouth");
                if (split > 0) bad.add(split + " trail fixtures across chunks");
                if (markGap > 2.5 * CaveTrail.MARKER_EVERY) bad.add(String.format(Locale.ROOT, "trail markers %.0f m apart", markGap));
            }
            boolean any = false;
            for (UndergroundPlan.Destination d : u.destinations) any |= d.kind() == UndergroundPlan.DestinationKind.START_CAVE;
            if (any && u.trails.isEmpty()) bad.add("the start destination has no trail (" + u.notes.keySet().stream().filter(k -> k.startsWith("trail")).toList() + ")");
            out("  " + trailLine);
        }
        int entUnreach = 0;
        for (Entrance e : u.entrances) if (e.connected() && e.place() >= 0 && UndergroundRoutes.leastKitIn(u, e.place(), in) < 0) entUnreach++;
        if (entUnreach > 0) bad.add(entUnreach + " connected entrances whose rim is not a surface place");

        // ---- pitches, platforms and anchors (19.2)
        int pitchN = 0, platBad = 0, anchorBad = 0, anchorWeak = 0, noAnchor = 0;
        double maxH = 0;
        Map<String, Integer> fx = new TreeMap<>();
        for (Pitch pc : u.pitches) {
            pitchN++;
            maxH = Math.max(maxH, pc.height());
            fx.merge(pc.fixture().name().toLowerCase(Locale.ROOT) + (pc.slopeAlt() >= 0 ? "+slope" : ""), 1, Integer::sum);
            for (int i = 0; i < pc.rigs().size(); i++) {
                Place q = u.places.get(pc.rigs().get(i));
                String why2 = standable(u, s, q, pc);
                if (why2 != null) {
                    platBad++;
                    if (platBad <= 6) {
                        out("    platform: pitch %d rig %d %s %s at (%.1f, %.1f, %.1f) cave %d %s", pc.id(), i, q.kind(), why2, q.x(), q.y(), q.z(), pc.owner(), u.caves.get(pc.owner()).kind);
                        // the shape under the platform, to tell a later shape from the pitch's own
                        for (int si : u.shapesNear(q.x(), q.z())) if (u.shapes.get(si).inside(q.x(), q.y() - 0.6, q.z()))
                            out("      under it: shape %d %s of cave %d, y %.0f..%.0f, x %.0f..%.0f", si, u.shapes.get(si).getClass().getSimpleName(), u.shapes.get(si).owner(), u.shapes.get(si).bounds()[1], u.shapes.get(si).bounds()[4], u.shapes.get(si).bounds()[0], u.shapes.get(si).bounds()[3]);
                    }
                }
                if (pc.rigs().get(i) == pc.bottom()) continue;
                int[] a = i < pc.anchorsAt().size() ? pc.anchorsAt().get(i) : new int[0];
                if (a.length < 2) { noAnchor++; continue; }
                for (int ai : a) {
                    Anchor an = u.anchors.get(ai);
                    if (u.openAt(an.x(), an.y(), an.z()) || an.rock() == null || an.rock().soil) {
                        anchorBad++;
                        if (anchorBad <= 4) out("    anchor: pitch %d rig %d %s at (%.1f, %.1f, %.1f) %s, place y %.1f ground %.1f, pit r %.1f", pc.id(), i, q.kind(), an.x(), an.y(), an.z(),
                                u.openAt(an.x(), an.y(), an.z()) ? "open" : an.rock() == null ? "no rock" : an.rock().id, q.y(), s.heightAt(an.x(), an.z()), pc.rh());
                    }
                    else if (!an.good()) anchorWeak++;
                }
            }
        }
        out("  pitches %d (max %.0f m) %s; anchors %d: %d not in rock, %d in weak rock, %d rigs without; %d rig platforms not standable",
                pitchN, maxH, fx, u.anchors.size(), anchorBad, anchorWeak, noAnchor, platBad);
        if (platBad > pitchN / 50 + 2) bad.add(platBad + " rope platforms not standable");
        if (anchorBad > 0 || noAnchor > 0) bad.add((anchorBad + noAnchor) + " rope anchors missing or not in rock");

        entranceShapes(u, s, bad);
        entranceRocks(u, s, p0, bad);

        // ---- successions (19.7)
        int succ = 0, first = 0, noExit = 0;
        EnumMap<UndergroundPlan.Env, Integer> envs = new EnumMap<>(UndergroundPlan.Env.class);
        for (Succession c : u.successions) {
            envs.merge(c.env(), 1, Integer::sum);
            if (c.succession()) succ++;
            if (c.firstStart()) first++;
            boolean rel = false;
            for (Exit e : c.exits()) if (e.reliable()) rel = true;
            if (c.succession() && !rel) noExit++;
        }
        out("  succession candidates %d %s: %d suit succession (reliable exit, hazard <= 3), %d a first start; %d marked without a reliable exit",
                u.successions.size(), envs, succ, first, noExit);
        for (int i = 0; i < Math.min(6, u.successions.size()); i++) {
            Succession c = u.successions.get(i);
            Exit e = c.exits().isEmpty() ? null : c.exits().get(0);
            out("    #%d %s at (%.0f, %.0f, %.0f) %s hazard %d kit %s exit %s bg %s", c.id(), c.env(), c.x(), c.y(), c.z(), c.host(), c.hazard(),
                    c.kit() == null ? "none" : c.kit().name(), e == null ? "none" : String.format(Locale.ROOT, "%.0f m, up %.0f m, %d pitches, needs %s", e.length(), e.climbUp(), e.pitches(), e.need().text()), c.backgrounds());
        }
        if (noExit > 0) bad.add(noExit + " succession candidates without a reliable exit");
        Map<String, Integer> docs = new TreeMap<>();
        for (UndergroundPlan.DocumentSite d : u.documents) docs.merge(d.kind().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        out("  document points %d %s", u.documents.size(), docs);

        // ---- determinism: a second build, and queries in two orders
        GeologyModel g2 = new GeologyModel(seed, s, map, -256);
        UndergroundPlan u2 = UndergroundPlanner.build(g2, new GeoProtect(s, roads, towns, h));
        String d1 = digest(u), d2 = digest(u2);
        boolean qOk = queryOrder(g, g2, u, u2, seed);
        out("  determinism: digest %s / %s %s; queries in shuffled order %s", d1.substring(0, 12), d2.substring(0, 12), d1.equals(d2) ? "same" : "DIFFERENT", qOk ? "same" : "DIFFERENT");
        if (!d1.equals(d2)) bad.add("two builds differ");
        if (!qOk) bad.add("query order changes answers");

        // ---- sampling cost a chunk (U2 input) and the other heights
        double chunkMs = chunkCost(g, u, seed);
        out("  sampling a full 16x16 chunk column (rock + cave at every y): %.1f ms", chunkMs);
        for (int minY : new int[]{-192, -64}) {
            long a = System.nanoTime();
            GeologyModel gm = new GeologyModel(seed, s, map, minY);
            UndergroundPlan um = UndergroundPlanner.build(gm, new GeoProtect(s, roads, towns, h));
            EnumMap<CaveKind, Integer> km = new EnumMap<>(CaveKind.class);
            for (Cave c : um.caves) if (!c.label.equals("removed")) km.merge(c.kind, 1, Integer::sum);
            out("  minY %d: %s, entrances %d, %d ms", minY, km, um.entrances.size(), (System.nanoTime() - a) / 1_000_000);
        }

        // ---- pictures
        geologyPng(new File(OUT, tag + "_geology.png"), g, s, map, roads, towns, h);
        mapPng(new File(OUT, tag + "_map.png"), g, s, u, roads, towns, h);
        graphPng(new File(OUT, tag + "_graph.png"), s, u, comp, in);
        sections(tag, g, s, u, p0);

        String line = String.format(Locale.ROOT, "systems %d+%d, giants %d, canyons %d, entrances %d (%d connected), mines %d, facility %s, successions %d, plan %d ms",
                karst, frac, giants, canyons, u.entrances.size(), u.entrances.stream().filter(Entrance::connected).count(), u.mines.size(), u.facility != null ? "yes" : "no",
                u.successions.size(), geoMs + planMs);
        out("  " + line);
        return new Result(line, bad);
    }

    // ------------------------------------------------------------------ helpers

    static double pct(int a, int b) { return b == 0 ? 0 : 100.0 * a / b; }

    static int count(EnumMap<EntranceKind, int[]> m, EntranceKind k) { int[] c = m.get(k); return c == null ? 0 : c[0] + c[1]; }

    static void range(List<String> bad, String what, int n, int lo, int hi) {
        if (n < lo || n > hi) bad.add(what + " " + n + " outside " + lo + "-" + hi);
    }

    static void rangeNote(String what, int n, int lo, int hi) {
        if (n < lo || n > hi) out("  NOTE %s %d outside the design's %d-%d", what, n, lo, hi);
    }

    static String faultText(GeologyModel g) {
        StringBuilder sb = new StringBuilder();
        for (GeologyModel.Fault f : g.faultList()) sb.append(String.format(Locale.ROOT, "%s%.1f km/%.0f m", sb.length() == 0 ? "" : ", ", f.length() / 1000, f.throwM()));
        return sb.toString();
    }

    /**
     * Monte Carlo over land columns: per layer {rock m3, air m3, karst rock m3, karst air m3, water m3}, then rows 5/6:
     * the air sample counts by depth below Hs (5 m bins) and by layer (row 6 holds the layer of each bin's majority).
     */
    static double[][] monteCarlo(GeologyModel g, UndergroundPlan u, int n, long seed) {
        TerrainPlanSurface s = g.surface;
        double size = s.gridN * TerrainPlanSurface.CELL;
        SplittableRandom rnd = new SplittableRandom(seed ^ 0x5EED);
        double[][] r = new double[7][];
        for (int i = 0; i < 5; i++) r[i] = new double[5];
        r[5] = new double[4 * 120];
        r[6] = new double[0];
        int land = 0, tries = 0;
        // the volume one sample stands for: land area x column height / samples (each sample a random land column, random y)
        double area = 0;
        List<double[]> samples = new ArrayList<>();
        while (land < n && tries < n * 4) {
            tries++;
            double x = s.gridOrigin + rnd.nextDouble() * size, z = s.gridOrigin + rnd.nextDouble() * size;
            if (s.waterAt(x, z) || !g.map.isLand(x, z)) continue;
            land++;
            samples.add(new double[]{x, z, rnd.nextDouble()});
        }
        area = size * size * land / tries;
        for (double[] sm : samples) {
            Column c = g.column(sm[0], sm[1]);
            double y = g.minY + sm[2] * (c.h - g.minY);
            double vol = area * (c.h - g.minY) / land;
            int L = Math.max(1, Math.min(4, g.layer(c, y)));
            if (g.layer(c, y) == 5) L = 4;
            boolean kar = g.karst(c, y) >= 0.5;
            r[L][0] += vol;
            if (kar) r[L][2] += vol;
            int cave = u.caveAt(sm[0], y, sm[1]);
            if (cave >= 0) {
                r[L][1] += vol;
                if (kar) r[L][3] += vol;
                if (u.waterAt(sm[0], y, sm[1])) r[L][4] += vol;
                int bin = (int) Math.min(119, Math.max(0, (c.hs - y) / 5));
                r[5][(L - 1) * 120 + bin] += vol;
            }
        }
        return r;
    }

    /** Non-throat shapes whose roof comes above the bottom of layer 1 (Hs - T1) anywhere on a 3x3 footprint. */
    static int layer1Breaks(GeologyModel g, UndergroundPlan u) {
        int n = 0;
        for (int i = 0; i < u.shapes.size(); i++) {
            if (u.throats.get(i)) continue;
            Shape sh = u.shapes.get(i);
            if (sh.solid()) continue;
            double[] b = sh.bounds();
            if (b[1] < -1e5) continue;
            boolean br = false;
            for (int a = 0; a <= 2 && !br; a++) for (int c = 0; c <= 2 && !br; c++) {
                double x = b[0] + (b[3] - b[0]) * a / 2, z = b[2] + (b[5] - b[2]) * c / 2;
                double top = b[4];
                if (sh instanceof Ellipsoid e) { top = e.roofAt(x, z); if (Double.isNaN(top)) continue; }
                if (sh instanceof Capsule cp && !(a == 1 && c == 1) && !cp.vertical()) continue;
                Column col = g.column(x, z);
                if (top > col.hs - col.t1 + 0.5) br = true;
            }
            if (br) { n++; if (n <= 3) out("    layer 1: shape %d %s of cave %d %s", i, sh.getClass().getSimpleName(), sh.owner(), u.caves.get(sh.owner()).kind); }
        }
        return n;
    }

    /** Why a rigging place is not standable, or null: open at +0.5 and +1.7, solid at -0.6, not over the shaft. */
    static String standable(UndergroundPlan u, TerrainPlanSurface s, Place q, Pitch pc) {
        Platform pf = q.platform();
        if (pf == null) return "no platform";
        if (q.kind() == PlaceKind.SURFACE || Math.abs(q.y() - s.heightAt(q.x(), q.z())) < 1.5 && !u.openAt(q.x(), q.y() + 0.5, q.z())) {
            double gy = u.groundAt(q.x(), q.z(), s.heightAt(q.x(), q.z()));
            if (Math.abs(gy - q.y()) > 2) return String.format(Locale.ROOT, "%.1f m off the ground", q.y() - gy);
            if (u.openAt(q.x(), gy - 0.6, q.z()) && Math.hypot(q.x() - pc.ax(), q.z() - pc.az()) <= pc.rh() + 0.2) return "over the shaft";
            return null;
        }
        if (!u.openAt(q.x(), q.y() + 0.5, q.z())) return "solid at +0.5";
        if (!u.openAt(q.x(), q.y() + 1.7, q.z())) return "solid at +1.7";
        if (u.openAt(q.x(), q.y() - 0.6, q.z())) {
            if (q.id() != pc.bottom() || u.caveAt(q.x(), q.y() - 3, q.z()) >= 0) return "open below";
        }
        return null;
    }

    /**
     * U4 (design 22): the entrance bowls, porches and talus. Along 24 rays of every bowl the ground after the cut has no
     * step over 1.2 m in 1 m and no slope steeper than its kind allows (+10 %); no water stands open to air beside or
     * under it (a bowl's pond, or the cave's water in the throat under it); no talus buries a place; every surface place
     * stands on the ground after the cut. Prints the counts by entrance kind and the sizes.
     */
    static void entranceShapes(UndergroundPlan u, TerrainPlanSurface s, List<String> bad) {
        Map<String, EntranceKind> at = new HashMap<>();
        for (Entrance e : u.entrances) at.put(Math.round(e.x()) + "," + Math.round(e.z()), e.kind());
        Map<String, int[]> count = new TreeMap<>();
        int bowls = 0, ponds = 0, steps = 0, steep = 0, hang = 0, notches = 0, taluses = 0, buried = 0, offGround = 0;
        int pits = 0, portals = 0, noPortal = 0, hanging = 0;
        double sumR = 0, maxR = 0, sumD = 0, maxD = 0, worstStep = 0, worstSlope = 0, sumLen = 0, sumIrr = 0, maxIrr = 0;
        String worstSlopeAt = "";
        for (int i = 0; i < u.shapes.size(); i++) {
            Shape sh = u.shapes.get(i);
            if (sh instanceof Notch n) { notches++; sumLen += n.len(); continue; }
            if (sh instanceof Pit pt) {
                pits++;
                // the outline: its longest and shortest radius at the floor, 72 directions
                double lo = Double.MAX_VALUE, hi = 0;
                for (int a = 0; a < 72; a++) {
                    double cx = Math.cos(a * Math.PI / 36), cz = Math.sin(a * Math.PI / 36), rr = 0;
                    while (rr < pt.cliffReach() && pt.floorAt(pt.x() + cx * rr, pt.z() + cz * rr) <= pt.floorY() + 0.01) rr += 0.25;
                    lo = Math.min(lo, rr);
                    hi = Math.max(hi, rr);
                }
                sumIrr += hi / Math.max(lo, 1);
                maxIrr = Math.max(maxIrr, hi / Math.max(lo, 1));
                // portals: the cave's passages that cross the floor's outline within 8 m over the floor
                int po = 0;
                for (int j : u.caves.get(pt.owner()).shapes) {
                    if (!(u.shapes.get(j) instanceof Capsule c) || c.vertical()) continue;
                    boolean in = false, out = false;
                    for (int q = 0; q <= 40; q++) {
                        double f = q / 40.0, x = c.ax() + (c.bx() - c.ax()) * f, y = c.ay() + (c.by() - c.ay()) * f, z = c.az() + (c.bz() - c.az()) * f;
                        if (y < pt.floorY() - 2 || y > pt.floorY() + 8) continue;
                        if (pt.floorAt(x, z) <= pt.floorY() + 0.01) in = true; else out = true;
                    }
                    if (in && out) po++;
                }
                portals += po;
                if (po == 0) noPortal++;
                // no place hangs in its air: every place over its floor stands on its ground, on a talus or is a rope's ledge
                for (Place q : u.places) {
                    if (q.y() < -1e5 || q.kind() == PlaceKind.LEDGE || Math.abs(q.x() - pt.x()) > pt.outer() || Math.abs(q.z() - pt.z()) > pt.outer()) continue;
                    if (q.y() <= pt.floorY() + 0.5 || q.y() >= pt.top() || pt.floorAt(q.x(), q.z()) >= q.y() - 0.5) continue;
                    boolean onTalus = false;
                    for (Shape o : u.shapes) if (o instanceof Talus t && t.host() == i && t.surfaceAt(q.x(), q.z()) >= q.y() - 0.7) onTalus = true;
                    if (onTalus) continue;
                    hanging++;
                    if (hanging <= 3) out("    place %d %s hangs in pit %d at (%.1f, %.1f, %.1f), floor %.1f", q.id(), q.kind(), i, q.x(), q.y(), q.z(), pt.floorAt(q.x(), q.z()));
                }
                continue;
            }
            if (sh instanceof Talus t) {
                taluses++;
                Shape host = u.shapes.get(t.host());
                for (Place q : u.places) {
                    if (q.y() < -1e5 || q.platform() == null || Math.abs(q.x() - t.x()) > 80 || Math.abs(q.z() - t.z()) > 80) continue;
                    if (t.inside(q.x(), q.y() + 0.3, q.z()) && host.inside(q.x(), q.y() + 0.3, q.z())) {
                        buried++;
                        if (buried <= 3) out("    talus %d buries place %d %s at (%.1f, %.1f, %.1f)", i, q.id(), q.kind(), q.x(), q.y(), q.z());
                    }
                }
                continue;
            }
            if (!(sh instanceof Bowl b)) continue;
            bowls++;
            EntranceKind k = at.get(Math.round(b.x()) + "," + Math.round(b.z()));
            String kn = (k == null ? "?" : k.name().toLowerCase(Locale.ROOT)) + (u.caves.get(b.owner()).kind == CaveKind.SINKHOLE ? " dead" : "");
            count.computeIfAbsent(kn, q -> new int[1])[0]++;
            if (b.water() > Double.NEGATIVE_INFINITY) ponds++;
            double depth = s.heightAt(b.x(), b.z()) - b.floorY();
            sumR += b.R();
            maxR = Math.max(maxR, b.R());
            sumD += depth;
            maxD = Math.max(maxD, depth);
            double limit = (k == EntranceKind.COLLAPSE_PIT ? 1.0 : k == EntranceKind.KARST_WINDOW ? 0.8 : k == EntranceKind.GREAT_SINKHOLE ? 0.7 : 0.65) * 1.1;
            double reach = b.R() * (1 + UndergroundPlan.BOWL_WOB) + 3;
            for (int a = 0; a < 24; a++) {
                double cx = Math.cos(a * Math.PI / 12), cz = Math.sin(a * Math.PI / 12);
                double pg = Double.NaN, pp = Double.NaN, ppg = Double.NaN, ppp = Double.NaN;
                for (double r = 0; r <= reach; r += 0.5) {
                    double x = b.x() + cx * r, z = b.z() + cz * r, plan = s.heightAt(x, z), gy = u.groundAt(x, z, plan);
                    boolean cut = gy < plan - 0.1;
                    if (!Double.isNaN(pg) && cut && pp - 0.1 > pg) {
                        double sl = Math.abs(gy - pg) / 0.5;
                        if (sl > worstSlope) { worstSlope = sl; worstSlopeAt = String.format(Locale.ROOT, "%s at (%.0f, %.0f)", kn, x, z); }
                        if (sl > limit && ++steep <= 3) out("    bowl %d %s wall %.2f over its %.2f at (%.1f, %.1f)", i, kn, sl, limit, x, z);
                    }
                    if (!Double.isNaN(ppg) && (cut || ppp - 0.1 > ppg)) {
                        double st = Math.abs(gy - ppg) - Math.abs(plan - ppp);
                        worstStep = Math.max(worstStep, st);
                        if (st > 1.2) steps++;
                    }
                    ppg = pg;
                    ppp = pp;
                    pg = gy;
                    pp = plan;
                }
            }
            // water open to air: every wet point of the footprint against its four sides and the point under it
            double top = Math.max(b.water(), b.floorY()) + 1;
            for (double x = b.x() - reach; x <= b.x() + reach; x += 1) for (double z = b.z() - reach; z <= b.z() + reach; z += 1)
                for (double y = Math.floor(b.floorY()) - 2.5; y <= top; y += 1) {
                    if (!u.waterAt(x, y, z)) continue;
                    double[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
                    for (double[] o : nb) {
                        double nx = x + o[0], ny = y + o[1], nz = z + o[2];
                        boolean air = u.airAt(nx, ny, nz) || ny > s.heightAt(nx, nz) && !u.waterAt(nx, ny, nz);
                        if (!air) continue;
                        hang++;
                        if (hang <= 3) out("    water open to air at (%.1f, %.1f, %.1f) by bowl %d %s", x, y, z, i, kn);
                        break;
                    }
                }
        }
        for (Place q : u.places) {
            if (q.kind() != PlaceKind.SURFACE || q.y() < -1e5 || u.caves.get(q.cave()).kind == CaveKind.BUILT) continue;   // built heads stand at their own level
            double gy = u.groundAt(q.x(), q.z(), s.heightAt(q.x(), q.z()));
            if (Math.abs(gy - q.y()) > 2) {
                offGround++;
                if (offGround <= 3) out("    surface place %d %.1f m off the ground after the cut", q.id(), q.y() - gy);
            }
        }
        StringBuilder cs = new StringBuilder();
        for (var e : count.entrySet()) cs.append(' ').append(e.getKey()).append(' ').append(e.getValue()[0]);
        out("  U4 entrance shapes: bowls %d (%s ), %d with a pond; radius %.1f m mean / %.1f max, depth %.1f / %.1f; porches %d (%.1f m long); talus %d; notes %s",
                bowls, cs, ponds, bowls == 0 ? 0 : sumR / bowls, maxR, bowls == 0 ? 0 : sumD / bowls, maxD, notches, notches == 0 ? 0 : sumLen / notches, taluses, u4Notes(u));
        out("    steepest cut %.2f (%s), worst step over the ground's own %.2f m; %d steep samples, %d steps, %d wet points open to air, %d places buried, %d surface places off the ground",
                worstSlope, worstSlopeAt, worstStep, steep, steps, hang, buried, offGround);
        out("    great sinkhole pits %d: outline longest / shortest radius %.2f mean / %.2f max; %d portals (%d pits with none); %d places hanging",
                pits, pits == 0 ? 0 : sumIrr / pits, maxIrr, portals, noPortal, hanging);
        if (bowls == 0) bad.add("no entrance bowls");
        if (pits == 0) bad.add("no great sinkhole pits");
        if (hanging > 0) bad.add(hanging + " places hanging in great sinkholes");
        if (steep > 0) bad.add(steep + " bowl wall samples steeper than allowed");
        if (steps > 0) bad.add(steps + " steps over 1.2 m at bowl rims");
        if (hang > 0) bad.add(hang + " wet points open to air at entrances");
        if (buried > 0) bad.add(buried + " places buried by talus");
        if (offGround > 0) bad.add(offGround + " surface places off the ground");
    }

    /**
     * U4b (design 22.7): the entrance rocks. Every rock lies dry on solid ground with open room over it, outside the
     * protected zones, one to a column (a boulder's footprint holds no stones); a boulder has no hollow a metre under it,
     * leaves a metre's walk to every place's platform, step line and pitch shaft, a metre to the next boulder, and the
     * boulders of a bowl cover at most a quarter of its floor. Prints the counts by setting and the forms.
     */
    static void entranceRocks(UndergroundPlan u, TerrainPlanSurface s, GeoProtect p, List<String> bad) {
        Map<String, EntranceKind> at = new HashMap<>();
        for (Entrance e : u.entrances) at.put(Math.round(e.x()) + "," + Math.round(e.z()), e.kind());
        Map<String, int[]> count = new TreeMap<>(), byHost = new TreeMap<>();
        int[] forms = new int[UndergroundPlan.ROCK_FORMS.length], looks = new int[UndergroundPlan.ROCK_LOOKS.length];
        int erratic = 0;
        int boulders = 0, patches = 0, cobble = 0, flint = 0, wet = 0, air = 0, hollow = 0, zone = 0, twice = 0, route = 0, close = 0, crowded = 0;
        Map<Long, UndergroundPlan.Rock> cols = new HashMap<>();
        List<UndergroundPlan.Rock> bl = new ArrayList<>();
        Map<Integer, Double> cover = new HashMap<>();
        for (UndergroundPlan.Rock r : u.rocks) {
            double x = r.x() + 0.5, z = r.z() + 0.5, y = r.y();
            Shape sh = u.shapes.get(r.shape());
            String set = sh instanceof Bowl b ? String.valueOf(at.get(Math.round(b.x()) + "," + Math.round(b.z()))).toLowerCase(Locale.ROOT)
                    : sh instanceof Pit ? "great_sinkhole pit" : sh instanceof Talus ? "talus" : "porch";
            count.computeIfAbsent(set, q -> new int[2])[r.boulder() ? 0 : 1]++;
            byHost.computeIfAbsent(set + ":" + UndergroundPlan.ROCK_LOOKS[r.look()], q -> new int[2])[r.boulder() ? 0 : 1]++;
            looks[r.look()]++;
            if (set.equals("cover_sinkhole") && r.look() != 0) erratic++;
            if (s.waterAt(x, z) || u.waterAt(x, y + 0.2, z)) wet++;
            if (!u.openAt(x, y + 0.3, z) || u.openAt(x, y - 0.3, z) || !u.openAt(x, y + 1.5, z) && y + 1.5 <= s.heightAt(x, z)) {
                air++;
                if (air <= 3) out("    rock %s at (%.1f, %.2f, %.1f) not on the ground", set, x, y, z);
            }
            GeoProtect.Zone zn = p.zone(x, z);
            if (zn != GeoProtect.Zone.NONE && zn != GeoProtect.Zone.START_AREA) zone++;
            if (cols.put((long) r.x() << 32 ^ (r.z() & 0xFFFFFFFFL), r) != null) twice++;
            if (!r.boulder()) {
                patches++;
                if (r.cobble()) cobble++;
                if (r.flint()) flint++;
                continue;
            }
            boulders++;
            forms[r.form()]++;
            bl.add(r);
            double rad = UndergroundPlan.ROCK_LENGTH[r.form()] / 2;
            if (u.openAt(x, y - 1, z)) hollow++;
            if (sh instanceof Bowl) cover.merge(r.shape(), Math.PI * rad * rad, Double::sum);
            for (Place q : u.places) {
                if (q.y() < -1e5 || q.platform() == null || Math.abs(q.y() - y) >= 4) continue;
                if (Math.abs(x - q.x()) < q.platform().hx() + rad + 1 && Math.abs(z - q.z()) < q.platform().hz() + rad + 1) { route++; break; }
            }
            for (UndergroundPlan.Step st : u.steps) {
                Place a = u.places.get(st.a()), b = u.places.get(st.b());
                if (a.y() < -1e5 || b.y() < -1e5) continue;
                double ux = b.x() - a.x(), uz = b.z() - a.z(), l2 = ux * ux + uz * uz;
                double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a.x()) * ux + (z - a.z()) * uz) / l2));
                if (Math.hypot(x - a.x() - ux * t, z - a.z() - uz * t) < rad + 1 && Math.abs(a.y() + (b.y() - a.y()) * t - y) < 3) { route++; break; }
            }
            for (UndergroundPlan.Pitch pc : u.pitches) {
                Place pt = u.places.get(pc.top()), pb = u.places.get(pc.bottom());
                if (y <= pb.y() - 2 || y >= pt.y() + 4) continue;
                // a great sinkhole's rope hangs down its wall from the rim to the floor place
                double d;
                if (pc.rh() <= 12) d = Math.hypot(x - pc.ax(), z - pc.az()) - pc.rh();
                else {
                    double ux = pb.x() - pt.x(), uz = pb.z() - pt.z(), l2 = ux * ux + uz * uz;
                    double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - pt.x()) * ux + (z - pt.z()) * uz) / l2));
                    d = Math.hypot(x - pt.x() - ux * t, z - pt.z() - uz * t);
                }
                if (d < rad + 1) { route++; break; }
            }
        }
        // a boulder's footprint holds no other rock, and a metre between boulders
        for (UndergroundPlan.Rock r : bl) {
            double rad = UndergroundPlan.ROCK_LENGTH[r.form()] / 2;
            int e = (int) Math.ceil(rad);
            for (int dz = -e; dz <= e; dz++) for (int dx = -e; dx <= e; dx++) {
                UndergroundPlan.Rock o = cols.get((long) (r.x() + dx) << 32 ^ ((r.z() + dz) & 0xFFFFFFFFL));
                if (o != null && o != r && !o.boulder()) twice++;
            }
            for (UndergroundPlan.Rock o : bl)
                if (o != r && Math.abs(o.x() - r.x()) < 8 && Math.abs(o.z() - r.z()) < 8
                        && Math.hypot(o.x() - r.x(), o.z() - r.z()) < rad + UndergroundPlan.ROCK_LENGTH[o.form()] / 2 + 1 - 1e-9) close++;
        }
        for (var e : cover.entrySet()) {
            Bowl b = (Bowl) u.shapes.get(e.getKey());
            double reach = b.rf() + 1.5;
            if (e.getValue() > 0.25 * Math.PI * reach * reach + 1e-9) crowded++;
        }
        StringBuilder cs = new StringBuilder(), fs = new StringBuilder();
        for (var e : count.entrySet()) cs.append(' ').append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
        for (int i = 0; i < forms.length; i++) if (forms[i] > 0) fs.append(' ').append(UndergroundPlan.ROCK_FORMS[i]).append(' ').append(forms[i]);
        out("  U4b entrance rocks: %d boulders, %d stone patches (%d with a field stone, %d with a flint); boulders/patches by setting:%s; forms:%s",
                boulders, patches, cobble, flint, cs, fs);
        StringBuilder hs = new StringBuilder();
        for (var e : byHost.entrySet()) hs.append(' ').append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
        StringBuilder ls = new StringBuilder();
        for (int i = 0; i < looks.length; i++) ls.append(' ').append(UndergroundPlan.ROCK_LOOKS[i]).append(' ').append(looks[i]);
        out("    R3 rocks by look:%s; by setting and look (boulders/patches):%s; %d cover sinkhole rocks not granite", ls, hs, erratic);
        if (erratic > 0) bad.add(erratic + " cover sinkhole rocks not granite");
        out("    %d wet, %d off the ground, %d boulders over a hollow, %d in protected zones, %d columns twice, %d boulders on routes, %d boulder pairs too close, %d bowls over a quarter covered",
                wet, air, hollow, zone, twice, route, close / 2, crowded);
        if (boulders == 0 || patches == 0) bad.add("no entrance rocks");
        if (wet + air + hollow + zone + twice + route + close + crowded > 0)
            bad.add((wet + air + hollow + zone + twice + route + close / 2 + crowded) + " entrance rocks misplaced");
    }

    static String u4Notes(UndergroundPlan u) {
        StringBuilder sb = new StringBuilder("{");
        for (var e : u.notes.entrySet())
            if (e.getKey().startsWith("bowl") || e.getKey().startsWith("dead bowls") || e.getKey().startsWith("porch") || e.getKey().startsWith("talus") || e.getKey().startsWith("sinkhole too") || e.getKey().startsWith("pit"))
                sb.append(sb.length() > 1 ? ", " : "").append(e.getKey()).append('=').append(e.getValue());
        return sb.append('}').toString();
    }

    static int[] components(UndergroundPlan u) {
        int[] p = new int[u.caves.size()];
        for (int i = 0; i < p.length; i++) p[i] = i;
        for (CaveLink l : u.links) union(p, l.a(), l.b());
        for (int i = 0; i < p.length; i++) p[i] = find(p, i);
        return p;
    }

    static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }

    static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

    static String digest(UndergroundPlan u) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (Shape sh : u.shapes) sb.append(sh).append('\n');
        for (Entrance e : u.entrances) sb.append(e).append('\n');
        for (Place q : u.places) sb.append(q).append('\n');
        for (UndergroundPlan.Step st : u.steps) sb.append(st).append('\n');
        for (OreBody o : u.ores) sb.append(o).append('\n');
        for (Anchor a : u.anchors) sb.append(a).append('\n');
        for (UndergroundPlan.Rock r : u.rocks) sb.append(r).append('\n');
        for (Succession c : u.successions) sb.append(c.id()).append(c.env()).append(c.place()).append(c.hazard()).append(c.exits().size()).append('\n');
        sb.append(u.facility == null ? "-" : u.facility.x() + "," + u.facility.z());
        byte[] d = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hx = new StringBuilder();
        for (byte b : d) hx.append(String.format("%02x", b));
        return hx.toString();
    }

    /** The same points asked of plan 1 in order and of plan 2 shuffled (a query never depends on what was asked before). */
    static boolean queryOrder(GeologyModel g1, GeologyModel g2, UndergroundPlan u1, UndergroundPlan u2, long seed) {
        TerrainPlanSurface s = g1.surface;
        double size = s.gridN * TerrainPlanSurface.CELL;
        SplittableRandom rnd = new SplittableRandom(seed ^ 0xC0FFEE);
        int n = 20000;
        double[][] pts = new double[n][];
        for (int i = 0; i < n; i++) {
            double x = s.gridOrigin + rnd.nextDouble() * size, z = s.gridOrigin + rnd.nextDouble() * size;
            pts[i] = new double[]{x, g1.minY + rnd.nextDouble() * (s.heightAt(x, z) - g1.minY), z};
        }
        // bias half of them to cave shapes so the cave answers are exercised
        for (int i = 0; i < n / 2 && !u1.shapes.isEmpty(); i++) {
            double[] b = u1.shapes.get(rnd.nextInt(u1.shapes.size())).bounds();
            if (b[1] < -1e5) continue;
            pts[i] = new double[]{b[0] + rnd.nextDouble() * (b[3] - b[0]), b[1] + rnd.nextDouble() * (b[4] - b[1]), b[2] + rnd.nextDouble() * (b[5] - b[2])};
        }
        String[] a1 = new String[n], a2 = new String[n];
        for (int i = 0; i < n; i++) a1[i] = g1.rockAt(pts[i][0], pts[i][1], pts[i][2]) + ":" + u1.caveAt(pts[i][0], pts[i][1], pts[i][2]);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = n - 1 - i;
        for (int i = n - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = order[i]; order[i] = order[j]; order[j] = t; }
        for (int i : order) a2[i] = g2.rockAt(pts[i][0], pts[i][1], pts[i][2]) + ":" + u2.caveAt(pts[i][0], pts[i][1], pts[i][2]);
        return Arrays.equals(a1, a2);
    }

    /** ms to answer rock and cave at every block of a 16x16 column from minY to the ground (average of 12 land chunks). */
    /**
     * U2: the chunk cut (NoiseChunkUndergroundMixin: one shapesInBox per chunk, caveAmong per block) answers exactly
     * caveAt, block for block, in 24 chunks through cave shapes (every 2nd block of the column, the cave's height range);
     * prints the cut's cost per chunk. Null = same everywhere.
     */
    static String cutCheck(GeologyModel g, UndergroundPlan u, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed ^ 0x52C);
        long diff = 0, cells = 0, inCave = 0, ns = 0;
        int chunks = 0;
        for (int tries = 0; chunks < 24 && tries < 2000 && !u.shapes.isEmpty(); tries++) {
            UndergroundPlan.Shape sh = u.shapes.get(rnd.nextInt(u.shapes.size()));
            double[] b = sh.bounds();
            double cx = b[0] + rnd.nextDouble() * (b[3] - b[0]), cz = b[2] + rnd.nextDouble() * (b[5] - b[2]);
            double x0 = Math.floor(cx / 16) * 16, z0 = Math.floor(cz / 16) * 16;
            int y0 = (int) Math.floor(b[1]) - 2, y1 = (int) Math.ceil(b[4]) + 2;
            long a = System.nanoTime();
            int[] ids = u.shapesInBox(x0, z0, x0 + 16, z0 + 16);
            int[] got = new int[16 * 16 * ((y1 - y0) / 2 + 1)];
            int k = 0;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++)
                for (int y = y0; y <= y1; y += 2) got[k++] = u.caveAmong(ids, x0 + dx + 0.5, y + 0.5, z0 + dz + 0.5);
            ns += System.nanoTime() - a;
            k = 0;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++)
                for (int y = y0; y <= y1; y += 2) {
                    int want = u.caveAt(x0 + dx + 0.5, y + 0.5, z0 + dz + 0.5);
                    if (want != got[k++]) diff++;
                    if (want >= 0) inCave++;
                    cells++;
                }
            chunks++;
        }
        out("  U2 cut: %d chunks, %d cells (%d in caves), %d differ from caveAt; cut %.2f ms per chunk column range",
                chunks, cells, inCave, diff, chunks == 0 ? 0 : ns / 1e6 / chunks);
        return diff == 0 ? null : diff + " cut cells differ from caveAt";
    }

    static double chunkCost(GeologyModel g, UndergroundPlan u, long seed) {
        TerrainPlanSurface s = g.surface;
        SplittableRandom rnd = new SplittableRandom(seed ^ 0xC4);
        double size = s.gridN * TerrainPlanSurface.CELL;
        int done = 0;
        long sink = 0, ns = 0;
        while (done < 12) {
            double x0 = Math.floor((s.gridOrigin + rnd.nextDouble() * size) / 16) * 16, z0 = Math.floor((s.gridOrigin + rnd.nextDouble() * size) / 16) * 16;
            if (s.waterAt(x0, z0) || !g.map.isLand(x0, z0)) continue;
            long a = System.nanoTime();
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                Column c = g.column(x0 + dx + 0.5, z0 + dz + 0.5);
                for (int y = g.minY; y < c.h; y++) {
                    RockType r = g.rockAt(c, y + 0.5);
                    sink += r == null ? 0 : r.ordinal();
                    sink += u.caveAt(x0 + dx + 0.5, y + 0.5, z0 + dz + 0.5);
                }
            }
            ns += System.nanoTime() - a;
            done++;
        }
        if (sink == Long.MIN_VALUE) System.out.println();
        return ns / 1e6 / done;
    }

    // ------------------------------------------------------------------ pictures

    static final double MPP = 32;

    static int px(TerrainPlanSurface s, double x) { return (int) ((x - s.gridOrigin) / MPP); }

    static Color rgb(int c) { return new Color(c); }

    static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f)), g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f)), b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static BufferedImage base(GeologyModel g, TerrainPlanSurface s, FederalMap map, double dim) {
        int w = (int) (s.gridN * TerrainPlanSurface.CELL / MPP);
        BufferedImage im = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        FederalStateId[] prev = new FederalStateId[w];
        for (int j = 0; j < w; j++) {
            FederalStateId left = null;
            for (int i = 0; i < w; i++) {
                double x = s.gridOrigin + (i + 0.5) * MPP, z = s.gridOrigin + (j + 0.5) * MPP;
                int c;
                FederalStateId st = map.stateAt(x, z);
                if (s.waterAt(x, z) || !map.isLand(x, z)) c = 0x3A5F8A;
                else {
                    RockType b = g.bedrock(g.column(x, z));
                    double hs = g.hs(x + MPP, z + MPP) - g.hs(x - MPP, z - MPP);
                    c = shade(b == null ? 0x808080 : b.rgb, (0.85 + Math.max(-0.25, Math.min(0.25, hs / 60))) * dim);
                }
                if (st != null && ((left != null && left != st) || (prev[i] != null && prev[i] != st))) c = 0x101010;
                im.setRGB(i, j, c);
                left = st;
                prev[i] = st;
            }
        }
        return im;
    }

    static void works(Graphics2D gr, TerrainPlanSurface s, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) {
        gr.setColor(new Color(250, 250, 250, 200));
        gr.setStroke(new BasicStroke(1.2f));
        for (RoadDesign r : roads) {
            double[] prev = null;
            for (double d = 0; d <= r.length; d += 64) {
                double[] q = r.point(Math.min(d, r.length), 0);
                if (prev != null) gr.draw(new Line2D.Double(px(s, prev[0]), px(s, prev[1]), px(s, q[0]), px(s, q[1])));
                prev = q;
            }
        }
        gr.setColor(new Color(255, 230, 60));
        for (TownPlan t : towns) {
            double[][] cs = {{t.limit[0], t.limit[2]}, {t.limit[1], t.limit[2]}, {t.limit[1], t.limit[3]}, {t.limit[0], t.limit[3]}};
            for (int i = 0; i < 4; i++) {
                double[] a = t.world(cs[i][0], cs[i][1]), b = t.world(cs[(i + 1) % 4][0], cs[(i + 1) % 4][1]);
                gr.draw(new Line2D.Double(px(s, a[0]), px(s, a[1]), px(s, b[0]), px(s, b[1])));
            }
        }
        if (h != null) {
            gr.setColor(Color.RED);
            gr.fill(new Ellipse2D.Double(px(s, h.x()) - 4, px(s, h.z()) - 4, 8, 8));
            gr.draw(new Ellipse2D.Double(px(s, h.x()) - 300 / MPP, px(s, h.z()) - 300 / MPP, 600 / MPP, 600 / MPP));
        }
    }

    static void legend(Graphics2D gr, int x, int y, String[] names, int[] colors) {
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < names.length; i++) {
            gr.setColor(new Color(colors[i]));
            gr.fillRect(x, y + i * 16, 12, 12);
            gr.setColor(Color.WHITE);
            gr.drawString(names[i], x + 16, y + i * 16 + 11);
        }
    }

    static void geologyPng(File f, GeologyModel g, TerrainPlanSurface s, FederalMap map, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) throws Exception {
        BufferedImage im = base(g, s, map, 1);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setColor(new Color(200, 20, 20));
        gr.setStroke(new BasicStroke(2f));
        for (GeologyModel.Fault fl : g.faultList()) gr.draw(new Line2D.Double(px(s, fl.ax()), px(s, fl.az()), px(s, fl.bx()), px(s, fl.bz())));
        works(gr, s, roads, towns, h);
        RockType[] rs = RockType.values();
        String[] names = new String[rs.length];
        int[] cols = new int[rs.length];
        for (int i = 0; i < rs.length; i++) { names[i] = rs[i].id; cols[i] = rs[i].rgb; }
        gr.setColor(new Color(0, 0, 0, 160));
        gr.fillRect(6, 6, 110, rs.length * 16 + 8);
        legend(gr, 10, 10, names, cols);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    static final Map<CaveKind, Integer> CAVE_RGB = Map.of(CaveKind.SMALL_KARST, 0x9AD0FF, CaveKind.FISSURE, 0xC0C0C0, CaveKind.KARST_SYSTEM, 0x2EE6FF,
            CaveKind.FRACTURE_SYSTEM, 0xFFB347, CaveKind.CONNECTOR, 0x7CFF7C, CaveKind.GIANT, 0xFF40FF, CaveKind.CANYON, 0xFF4040,
            CaveKind.SINKHOLE, 0xFFFFFF, CaveKind.BUILT, 0xFFE040);

    static void mapPng(File f, GeologyModel g, TerrainPlanSurface s, UndergroundPlan u, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) throws Exception {
        BufferedImage im = base(g, s, g.map, 0.45);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        works(gr, s, roads, towns, h);
        for (int i = 0; i < u.shapes.size(); i++) {
            Shape sh = u.shapes.get(i);
            double[] b = sh.bounds();
            if (b[1] < -1e5 || sh.solid()) continue;
            Cave c = u.caves.get(sh.owner());
            gr.setColor(rgb(CAVE_RGB.get(c.kind)));
            if (sh instanceof Capsule cp) {
                gr.setStroke(new BasicStroke((float) Math.max(1, 2 * cp.rh() / MPP)));
                gr.draw(new Line2D.Double(px(s, cp.ax()), px(s, cp.az()), px(s, cp.bx()), px(s, cp.bz())));
            } else {
                double w = Math.max(2, (b[3] - b[0]) / MPP), hh = Math.max(2, (b[5] - b[2]) / MPP);
                gr.fill(new Ellipse2D.Double(px(s, (b[0] + b[3]) / 2) - w / 2, px(s, (b[2] + b[5]) / 2) - hh / 2, w, hh));
            }
        }
        for (OreBody o : u.ores) {
            gr.setColor(new Color(255, 120, 200, 140));
            double w = Math.max(3, 2 * o.rx() / MPP);
            gr.fill(new Ellipse2D.Double(px(s, o.x()) - w / 2, px(s, o.z()) - w / 2, w, w));
        }
        for (Entrance e : u.entrances) {
            int col = switch (e.kind()) {
                case COLLAPSE_PIT -> 0xB0B0B0;
                case COVER_SINKHOLE -> 0x80E0A0;
                case GREAT_SINKHOLE -> 0xFFFFFF;
                case FISSURE -> 0xE0A060;
                case SHAFT -> 0x60A0FF;
                case HILLSIDE_MOUTH -> 0x00FFC0;
                case DRY_SPRING -> 0x4080FF;
                case MINE_ADIT, MINE_SHAFT -> 0xFFE040;
                case FACILITY -> 0xFF2020;
                case SPRING_MOUTH -> 0x40C0FF;
                case KARST_WINDOW -> 0xA0FFFF;
                case CLIFF_MOUTH -> 0xFF80C0;
                case WAVE_CUT -> 0xC0A0FF;
            };
            gr.setColor(e.connected() ? rgb(col) : new Color(col & 0x7F7F7F));
            double r = e.kind() == EntranceKind.GREAT_SINKHOLE ? Math.max(5, 2 * e.radius() / MPP) : e.connected() ? 4 : 2;
            gr.fill(new Ellipse2D.Double(px(s, e.x()) - r / 2, px(s, e.z()) - r / 2, r, r));
        }
        gr.setColor(Color.WHITE);
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (Cave c : u.caves) if (c.kind == CaveKind.GIANT) gr.drawString(c.label, px(s, c.x) + 8, px(s, c.z));
        for (Succession c : u.successions) {
            gr.setColor(c.firstStart() ? Color.GREEN : c.succession() ? Color.ORANGE : Color.GRAY);
            gr.draw(new Ellipse2D.Double(px(s, c.x()) - 5, px(s, c.z()) - 5, 10, 10));
        }
        if (u.facility != null) {
            gr.setColor(Color.RED);
            gr.setStroke(new BasicStroke(2));
            gr.draw(new Ellipse2D.Double(px(s, u.facility.x()) - 12, px(s, u.facility.z()) - 12, 24, 24));
            gr.drawString("facility", px(s, u.facility.x()) + 14, px(s, u.facility.z()) + 4);
        }
        String[] names = {"karst system", "fracture system", "connector (L2)", "small cave / fissure", "giant cave", "canyon", "mine / facility", "ore body", "succession: first / succ / no"};
        int[] cols = {0x2EE6FF, 0xFFB347, 0x7CFF7C, 0x9AD0FF, 0xFF40FF, 0xFF4040, 0xFFE040, 0xFF78C8, 0x00FF00};
        gr.setColor(new Color(0, 0, 0, 170));
        gr.fillRect(6, 6, 200, names.length * 16 + 8);
        legend(gr, 10, 10, names, cols);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    static void graphPng(File f, TerrainPlanSurface s, UndergroundPlan u, int[] comp, List<BitSet> in) throws Exception {
        int w = (int) (s.gridN * TerrainPlanSurface.CELL / MPP);
        BufferedImage im = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setColor(new Color(16, 18, 22));
        gr.fillRect(0, 0, w, w);
        BitSet any = in.get(in.size() - 1);
        gr.setStroke(new BasicStroke(1.5f));
        for (CaveLink l : u.links) {
            Cave a = u.caves.get(l.a()), b = u.caves.get(l.b());
            gr.setColor(switch (l.kind()) {
                case "connector" -> new Color(120, 255, 120);
                case "karst_link" -> new Color(46, 230, 255);
                case "system_giant" -> new Color(255, 64, 255);
                case "system_canyon" -> new Color(255, 64, 64);
                default -> new Color(255, 224, 64);
            });
            gr.draw(new Line2D.Double(px(s, a.x), px(s, a.z), px(s, b.x), px(s, b.z)));
        }
        gr.setColor(new Color(255, 255, 255, 110));
        for (Entrance e : u.entrances) if (e.connected()) {
            Cave c = u.caves.get(e.target());
            gr.draw(new Line2D.Double(px(s, e.x()), px(s, e.z()), px(s, c.x), px(s, c.z)));
        }
        for (Cave c : u.caves) {
            if (c.label.equals("removed") || c.places.isEmpty()) continue;
            if (c.kind == CaveKind.SMALL_KARST || c.kind == CaveKind.FISSURE) {
                boolean reach = false;
                for (int pl : c.places) if (any.get(pl)) reach = true;
                if (!reach) continue;
            }
            boolean reach = false;
            for (int pl : c.places) if (any.get(pl)) reach = true;
            double r = switch (c.kind) { case GIANT -> 14; case CANYON -> 11; case KARST_SYSTEM, FRACTURE_SYSTEM -> 7; case BUILT -> 8; default -> 4; };
            gr.setColor(rgb(CAVE_RGB.get(c.kind)));
            Ellipse2D.Double el = new Ellipse2D.Double(px(s, c.x) - r / 2, px(s, c.z) - r / 2, r, r);
            if (reach) gr.fill(el); else gr.draw(el);
        }
        String[] names = {"filled: reachable from the surface", "ring: closed (no route)", "connector link", "karst link", "system - giant", "system - canyon", "entrance (white line)"};
        int[] cols = {0xFFFFFF, 0x888888, 0x78FF78, 0x2EE6FF, 0xFF40FF, 0xFF4040, 0xFFFFFF};
        legend(gr, 10, 10, names, cols);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    /** Three E-W and three N-S sections (8 m a pixel across, 1 m a pixel up) and two 1 m detail sections. */
    static void sections(String tag, GeologyModel g, TerrainPlanSurface s, UndergroundPlan u, GeoProtect p) throws Exception {
        List<Cave> gi = new ArrayList<>();
        for (Cave c : u.caves) if (c.kind == CaveKind.GIANT) gi.add(c);
        double size = s.gridN * TerrainPlanSurface.CELL;
        double[] at = {-size / 5, 0, size / 5};
        for (int i = 0; i < 3; i++) {
            double z = i < gi.size() ? gi.get(i).z : at[i];
            section(new File(OUT, tag + "_section_ew_" + (i + 1) + ".png"), g, s, u, s.gridOrigin, z, s.gridOrigin + size, z, 8, String.format(Locale.ROOT, "E-W at z %.0f", z));
            double x = i < gi.size() ? gi.get(gi.size() - 1 - i).x : at[i];
            if (i == 0 && u.facility != null) x = u.facility.x();
            section(new File(OUT, tag + "_section_ns_" + (i + 1) + ".png"), g, s, u, x, s.gridOrigin, x, s.gridOrigin + size, 8, String.format(Locale.ROOT, "N-S at x %.0f", x));
        }
        if (!gi.isEmpty()) {
            Cave c = gi.get(0);
            section(new File(OUT, tag + "_detail_giant.png"), g, s, u, c.x - 600, c.z, c.x + 600, c.z, 1, "detail E-W through " + c.label);
        }
        if (u.facility != null) {
            var fc = u.facility;
            double ux = Math.cos(fc.yaw()), uz = Math.sin(fc.yaw());
            section(new File(OUT, tag + "_detail_facility.png"), g, s, u, fc.x() - ux * 700, fc.z() - uz * 700, fc.x() + ux * 300, fc.z() + uz * 300, 1, "detail along the facility's haul tunnel");
        }
        // the deepest pitch of a karst system (vertical exploration)
        Pitch deep = null;
        for (Pitch pc : u.pitches) if (u.caves.get(pc.owner()).kind != CaveKind.BUILT && (deep == null || pc.height() > deep.height())) deep = pc;
        if (deep != null) section(new File(OUT, tag + "_detail_pitch.png"), g, s, u, deep.ax() - 150, deep.az(), deep.ax() + 150, deep.az(), 0.5,
                String.format(Locale.ROOT, "detail through the deepest natural pitch (%.0f m, %s)", deep.height(), deep.fixture()));
    }

    static void section(File f, GeologyModel g, TerrainPlanSurface s, UndergroundPlan u, double ax, double az, double bx, double bz, double mpp, String title) throws Exception {
        double len = Math.hypot(bx - ax, bz - az);
        int w = (int) Math.ceil(len / mpp);
        double yTop = 320, yBot = g.minY;
        double ypp = mpp >= 8 ? 1 : mpp;
        int hgt = (int) ((yTop - yBot) / ypp);
        BufferedImage im = new BufferedImage(w, hgt + 20, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w; i++) {
            double t = (i + 0.5) / w;
            double x = ax + (bx - ax) * t, z = az + (bz - az) * t;
            boolean land = !s.waterAt(x, z) && g.map.isLand(x, z);
            Column c = g.column(x, z);
            double[] bands = g.bands(c);
            double wl = s.waterAt(x, z) ? Math.max(c.h, 64) : c.h;
            for (int j = 0; j < hgt; j++) {
                double y = yTop - (j + 0.5) * ypp;
                int col;
                if (y > c.h) col = y <= wl ? 0x3A5F8A : 0xE8EEF4;
                else {
                    int cave = u.caveAt(x, y, z);
                    if (cave >= 0) {
                        Cave cv = u.caves.get(cave);
                        col = u.waterAt(x, y, z) ? 0x2050C0 : cv.kind == CaveKind.BUILT ? 0xFFB020 : 0x000000;
                    } else {
                        RockType r = g.rockAt(c, y);
                        col = r == null ? 0x808080 : r.rgb;
                        for (OreBody o : u.ores) {
                            if (Math.abs(o.x() - x) > o.rx() + o.rz() || Math.abs(o.z() - z) > o.rx() + o.rz()) continue;
                            double dx = x - o.x(), dz = z - o.z(), cy = Math.cos(o.yaw()), sy = Math.sin(o.yaw());
                            double lx = dx * cy + dz * sy, lz = -dx * sy + dz * cy, ly = y - o.y();
                            if (lx * lx / (o.rx() * o.rx()) + ly * ly / (o.ry() * o.ry()) + lz * lz / (o.rz() * o.rz()) <= 1) { col = 0xFF60C0; break; }
                        }
                    }
                }
                if (land) for (int b = 1; b <= 4; b++) if (Math.abs(y - bands[b]) < ypp * 0.5 && y < c.h) col = b == 1 ? 0xFFFF00 : b == 2 ? 0x00FF00 : b == 3 ? 0x00C0FF : 0xFF00FF;
                im.setRGB(i, j, col);
            }
        }
        Graphics2D gr = im.createGraphics();
        gr.setColor(Color.BLACK);
        gr.fillRect(0, hgt, w, 20);
        gr.setColor(Color.WHITE);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gr.drawString(String.format(Locale.ROOT, "%s, %.0f m wide, Y %d..%d; lines: yellow L1 bottom, green L2 bottom, cyan L3 bottom, magenta floor keep; black cave air, blue water, orange built, pink ore", title, len, (int) yBot, (int) yTop), 4, hgt + 14);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    static void depthPng(File f, double[] bins, double[] unused) throws Exception {
        int bw = 4, nb = 120, w = nb * bw + 80, hgt = 4 * 90 + 40;
        BufferedImage im = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = im.createGraphics();
        gr.setColor(new Color(20, 22, 26));
        gr.fillRect(0, 0, w, hgt);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int[] cols = {0xFFFF00, 0x00FF00, 0x00C0FF, 0xFF40FF};
        String[] names = {"layer 1", "layer 2", "layer 3", "layer 4"};
        for (int L = 0; L < 4; L++) {
            double max = 1;
            for (int b = 0; b < nb; b++) max = Math.max(max, bins[L * nb + b]);
            int y0 = 30 + L * 90 + 80;
            gr.setColor(new Color(cols[L]));
            for (int b = 0; b < nb; b++) {
                int hh = (int) Math.round(75 * bins[L * nb + b] / max);
                gr.fillRect(60 + b * bw, y0 - hh, bw - 1, hh);
            }
            gr.drawString(names[L], 4, y0 - 30);
        }
        gr.setColor(Color.WHITE);
        gr.drawString("cave air volume by depth below Hs (5 m bins, 0-600 m), each layer scaled to its own peak", 60, 16);
        for (int d = 0; d <= 600; d += 100) gr.drawString(d + " m", 60 + d / 5 * bw - 8, hgt - 4);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }
}
