package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mesh Trees P1 (docs/rendering/mesh_trees_p1_forest.md, B): whether this server's world grows the natural mesh forest,
 * and the development-only registrations that make it grow.
 * <ul>
 * <li>Gate, decided on the server once per world start, from the server's own world folder (never a client or display
 * name): development run, a folder whose name starts with {@code AFL_TREE_}, and the marker file {@value #MARKER} in it.
 * The marker is written only when the world has no region file yet (a brand-new world); an {@code AFL_TREE_*} world that
 * already has chunks (AFL_TREE_01 from P0) stays off, so no existing save gets a forest seam. Only the overworld.</li>
 * <li>{@code apocalypse_firstlight:dev_mesh_forest}: the feature (ForestFeature), and a biome modifier serializer of the
 * same id. The modifier (dev data, dropped from the jar) takes the covered biomes' vanilla tree features out of
 * VEGETAL_DECORATION and appends the mesh forest feature, last, so no other feature order changes. When the gate is off
 * it changes nothing.</li>
 * <li>With the gate on, the 128 layouts are built on a background thread at server start, so worldgen threads find
 * them ready.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForestGate {
    static final Logger LOG = LogUtils.getLogger();
    public static final ResourceLocation ID = new ResourceLocation("apocalypse_firstlight", "dev_mesh_forest");
    static final String MARKER = "afl_dev_mesh_forest.txt";

    private static volatile MinecraftServer decidedFor;
    private static volatile boolean on;
    private static volatile String why = "no server yet";
    private static volatile ForestPlan plan;
    static volatile String prewarm = "not started";

    private ForestGate() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (FMLEnvironment.production) return;
        event.register(ForgeRegistries.Keys.FEATURES, helper -> helper.register(ID, new ForestFeature()));
        event.register(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, helper -> helper.register(ID, Modifier.CODEC));
    }

    /** Removes the vanilla tree features of the covered biomes and appends the mesh forest, when the gate is on. */
    public record Modifier(HolderSet<Biome> biomes, HolderSet<PlacedFeature> remove, Holder<PlacedFeature> feature) implements BiomeModifier {
        static final Codec<Modifier> CODEC = RecordCodecBuilder.create(b -> b.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(Modifier::biomes),
                PlacedFeature.LIST_CODEC.fieldOf("remove").forGetter(Modifier::remove),
                PlacedFeature.CODEC.fieldOf("feature").forGetter(Modifier::feature)).apply(b, Modifier::new));

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if ((phase != Phase.ADD && phase != Phase.REMOVE) || !biomes.contains(biome) || !enabled()) return;
            var settings = builder.getGenerationSettings();
            if (phase == Phase.REMOVE) settings.getFeatures(GenerationStep.Decoration.VEGETAL_DECORATION).removeIf(remove::contains);
            else settings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, feature);
        }

        @Override
        public Codec<? extends BiomeModifier> codec() { return CODEC; }
    }

    /** The gate for the running server (decided at its start; the biome modifiers run first). */
    public static boolean enabled() {
        if (FMLEnvironment.production || !TreeBlocks.ready()) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        if (decidedFor != server) decide(server);
        return on;
    }

    public static String reason() { return why; }

    private static synchronized void decide(MinecraftServer server) {
        if (decidedFor == server) return;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String name = root.getFileName() == null ? "" : root.getFileName().toString();
        Path marker = root.resolve(MARKER);
        boolean result;
        String reason;
        if (!name.startsWith(TreeCommands.PREFIX)) {
            result = false;
            reason = "world folder \"" + name + "\" does not start with " + TreeCommands.PREFIX;
        } else if (Files.isRegularFile(marker)) {
            result = true;
            reason = "forest test world (" + MARKER + ")";
        } else if (generated(root)) {
            result = false;
            reason = name + " was generated before the forest test; create a new " + TreeCommands.PREFIX + "* world";
        } else {
            try {
                Files.writeString(marker, "AFL Mesh Trees P1: natural mesh forest test world (docs/rendering/mesh_trees_p1_forest.md).\n"
                        + "Written when the world was created; delete the world to remove the test, not only this file.\n");
                result = true;
                reason = "new forest test world (" + MARKER + " written)";
            } catch (IOException e) {
                result = false;
                reason = "could not write " + MARKER + ": " + e;
            }
        }
        on = result;
        why = reason;
        plan = null;
        ForestFeature.reset();
        decidedFor = server;
        LOG.info("[AFL mesh forest] natural mesh forest {} ({})", on ? "ON" : "off", why);
        if (on) prewarm();
    }

    /** A region file exists: the world has generated chunks. */
    private static boolean generated(Path root) {
        Path region = root.resolve("region");
        if (!Files.isDirectory(region)) return false;
        try (var files = Files.list(region)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".mca"));
        } catch (IOException e) {
            return true;
        }
    }

    /** Forgets the stopped server. */
    static synchronized void stopped() {
        decidedFor = null;
        on = false;
        why = "no server";
        plan = null;
    }

    /** The plan for a worldgen level when the forest grows there (the overworld of a gated world with a Terrain V2 plan). */
    static ForestPlan plan(WorldGenLevel level) {
        if (!enabled() || !level.getLevel().dimension().equals(Level.OVERWORLD)) return null;
        long seed = level.getSeed();
        ForestPlan p = plan;
        if (p != null && p.seed == seed) return p;
        var surface = TerrainPlanStore.peek(seed);
        if (surface == null || surface.ecology == null) { ForestFeature.NO_PLAN.increment(); return null; }
        p = new ForestPlan(surface, seed);
        plan = p;
        return p;
    }

    /** The plan of a running server's overworld (commands), or null. */
    static ForestPlan plan(MinecraftServer server) {
        long seed = server.getWorldData().worldGenOptions().seed();
        ForestPlan p = plan;
        if (p != null && p.seed == seed) return p;
        var surface = TerrainPlanStore.peek(seed);
        return surface == null || surface.ecology == null ? null : new ForestPlan(surface, seed);
    }

    private static void prewarm() {
        prewarm = "building";
        var thread = new Thread(() -> {
            long t0 = System.nanoTime();
            for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) for (int t = 0; t < 4; t++)
                ForestPlan.woodFloor(TreeShape.layout(sp, v, t));
            ForestPlan.crown(Species.WHITE_OAK, 0);
            prewarm = String.format("128 layouts in %.0f ms", (System.nanoTime() - t0) / 1e6);
            LOG.info("[AFL mesh forest] {}", prewarm);
        }, "AFL mesh forest layouts");
        thread.setDaemon(true);
        thread.start();
    }
}
