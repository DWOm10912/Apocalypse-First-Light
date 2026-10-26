package com.antaurora.apofirstlight.earthwork;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The bulk materials (docs/gameplay/sandbox_v1_design.md 4.1, docs/worldgen/earthwork_a2.md 1), data-driven from
 * data/apocalypse_firstlight/bulk_materials/&lt;id&gt;.json, in id order (their index in earthwork data, saved by id). The
 * built-in table below is the same as the shipped files and stands in until they load (and for a file that fails).
 * {@code backfill} must exist: mixed earth becomes it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class BulkMaterials {
    private static final Logger LOG = LoggerFactory.getLogger("AFL bulk materials");

    /**
     * One material: loose density (kg/m3), angle of repose of loose earth and excavation angle of the natural ground
     * (degrees), the block it is once put down, its grip for vehicles (A3), the natural blocks it comes from, and the
     * shade its loose earth is drawn with (backfill: the dirt texture a little darker), and the block-atlas sprite it is
     * drawn with instead of its block's top (null: the block's; Rock R2's rubble, which is gravel once put down).
     */
    public record Material(String id, double density, double repose, double excavation, BlockState block, double grip,
                           List<Block> sources, float shade, ResourceLocation texture) {
        /** grams of n quanta (1 quantum = 0.244 L) */
        public long grams(long n) { return Math.round(n * EarthGrid.LITRES_PER_QUANTUM * density); }
    }

    private static volatile Table table = new Table(builtIn());

    private BulkMaterials() {}

    /** The current table: materials by index, the source blocks, the backfill index. */
    public static final class Table {
        public final List<Material> all;
        public final int backfill;
        private final Map<String, Integer> byId = new HashMap<>();
        private final Map<Block, Integer> bySource = new HashMap<>();
        /** step per vertex (quanta) of each material's loose earth (repose) and natural ground (excavation) */
        public final int[] pile, cut;

        Table(List<Material> list) {
            all = List.copyOf(list);
            pile = new int[all.size()];
            cut = new int[all.size()];
            for (int k = 0; k < all.size(); k++) {
                Material m = all.get(k);
                byId.put(m.id(), k);
                for (Block b : m.sources()) bySource.putIfAbsent(b, k);
                pile[k] = EarthGrid.step(m.repose());
                cut[k] = EarthGrid.step(m.excavation());
            }
            Integer b = byId.get("backfill");
            if (b == null) throw new IllegalArgumentException("bulk materials need backfill");
            backfill = b;
        }

        public Material get(int index) { return all.get(index < 0 || index >= all.size() ? backfill : index); }

        /** Index of a material id, backfill for an unknown one (an old save, a removed file). */
        public int index(String id) { return byId.getOrDefault(id, backfill); }

        /**
         * The material natural block b is, -1 when it is none (not shovelable: a hard floor). AFL's own soils (natural ground
         * N1) name theirs (NaturalGroundBlock#bulk), so the files list vanilla's blocks only.
         */
        public int of(Block b) {
            Integer k = bySource.get(b);
            if (k != null) return k;
            return b instanceof com.antaurora.apofirstlight.ground.NaturalGroundBlock g && g.loose() ? byId.getOrDefault(g.bulk, -1) : -1;
        }

        public int size() { return all.size(); }
    }

    public static Table table() { return table; }

    private static List<Material> builtIn() {
        List<Material> out = new ArrayList<>();
        out.add(new Material("backfill", 1500, 36, 36, Blocks.DIRT.defaultBlockState(), 0.60, List.of(), 0.82F, null));
        out.add(new Material("clay", 1700, 40, 70, Blocks.CLAY.defaultBlockState(), 0.50, List.of(Blocks.CLAY), 1, null));
        out.add(new Material("gravel", 1700, 38, 45, Blocks.GRAVEL.defaultBlockState(), 0.60, List.of(Blocks.GRAVEL), 1, null));
        out.add(new Material("mud", 1900, 20, 30, Blocks.MUD.defaultBlockState(), 0.35, List.of(Blocks.MUD), 1, null));
        // Rock R2: broken rock off a pick; its excavation angle is the rock's (natural rock reads as it, Earthwork#natural)
        out.add(new Material("rubble", 1600, 40, 85, Blocks.GRAVEL.defaultBlockState(), 0.60, List.of(), 1,
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/rock/rubble")));
        out.add(new Material("sand", 1600, 34, 34, Blocks.SAND.defaultBlockState(), 0.45, List.of(Blocks.SAND), 1, null));
        // natural ground N1b (2026-10-25): red sand its own material, so dug red sand stays red (the data files' blocks are AFL's)
        out.add(new Material("red_sand", 1600, 34, 34, Blocks.RED_SAND.defaultBlockState(), 0.45, List.of(Blocks.RED_SAND), 1, null));
        out.add(new Material("topsoil", 1300, 35, 63, Blocks.DIRT.defaultBlockState(), 0.60,
                List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL), 1, null));
        return out;
    }

    private static double number(JsonObject o, String key) {
        double d = o.get(key).getAsDouble();
        if (!Double.isFinite(d) || d < 0) throw new IllegalArgumentException(key + " must be a finite number >= 0");
        return d;
    }

    /** A block, or a block state written as a block id with properties ("apocalypse_firstlight:topsoil[cover=bare]"). */
    private static BlockState state(String s) {
        if (s.indexOf('[') < 0) return block(s).defaultBlockState();
        try {
            return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException("bad block state " + s + ": " + e.getMessage());
        }
    }

    private static Block block(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        if (!BuiltInRegistries.BLOCK.containsKey(rl)) throw new IllegalArgumentException("unknown block " + id);
        return BuiltInRegistries.BLOCK.get(rl);
    }

    static Material parse(String id, JsonObject o) {
        double repose = number(o, "repose_deg");
        double excavation = o.has("excavation_deg") ? number(o, "excavation_deg") : repose;
        if (repose <= 0 || repose >= 90 || excavation < repose || excavation >= 90)
            throw new IllegalArgumentException("angles must be 0 < repose <= excavation < 90");
        List<Block> sources = new ArrayList<>();
        if (o.has("sources")) for (JsonElement e : o.getAsJsonArray("sources")) sources.add(block(e.getAsString()));
        float shade = o.has("shade") ? (float) number(o, "shade") : 1;
        ResourceLocation texture = o.has("texture") ? new ResourceLocation(o.get("texture").getAsString()) : null;
        return new Material(id, number(o, "density_kg_m3"), repose, excavation, state(o.get("block").getAsString()),
                number(o, "grip"), List.copyOf(sources), Math.min(1, shade), texture);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "bulk_materials") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<String, Material> read = new TreeMap<>();
                for (Material m : builtIn()) read.put(m.id(), m);
                for (var en : input.entrySet()) {
                    if (!en.getKey().getNamespace().equals(ApocalypseFirstLight.MOD_ID)) continue;
                    try {
                        read.put(en.getKey().getPath(), parse(en.getKey().getPath(), en.getValue().getAsJsonObject()));
                    } catch (RuntimeException ex) {
                        LOG.error("bulk material {} skipped: {}", en.getKey(), ex.getMessage());
                    }
                }
                try {
                    table = new Table(new ArrayList<>(read.values()));
                } catch (RuntimeException ex) {
                    LOG.error("bulk materials kept as they were: {}", ex.getMessage());
                }
            }
        });
    }
}
