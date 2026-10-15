package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;
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
 * Mesh Trees P1 (docs/rendering/mesh_trees_p1_forest.md, B; formal since P4, mesh_trees_p4_formal.md F/O): whether this
 * server's world grows the natural mesh forest, and the registrations that make it grow.
 * <ul>
 * <li>Gate, decided on the server once per world start: {@link MeshForestConfig#ENABLED} (a server config, loaded before
 * the biome modifiers run). Every AFL world, new or old: an old world grows mesh trees in the chunks it generates from
 * then on; chunks it already has keep their trees. Only the overworld, and only with a Terrain V2 plan.</li>
 * <li>{@code apocalypse_firstlight:mesh_forest}: the feature (ForestFeature), and a biome modifier serializer of the same
 * id. The modifier takes the covered biomes' vanilla tree features out of VEGETAL_DECORATION and appends the mesh forest
 * feature, last, so no other feature order changes. When the gate is off it changes nothing.</li>
 * <li>With the gate on, every layout is built on a background thread at server start, so worldgen threads find them
 * ready.</li>
 * </ul>
 * P0-P3 gated the forest to {@code AFL_TREE_*} test worlds; that gate is gone (the test worlds grow it like any other).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForestGate {
    static final Logger LOG = LogUtils.getLogger();
    public static final ResourceLocation ID = new ResourceLocation("apocalypse_firstlight", "mesh_forest");

    private static volatile MinecraftServer decidedFor;
    private static volatile boolean on;
    private static volatile String why = "no server yet";
    private static volatile ForestPlan plan;
    static volatile String prewarm = "not started";

    private ForestGate() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
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
        if (!TreeBlocks.ready()) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        if (decidedFor != server) decide(server);
        return on;
    }

    public static String reason() { return why; }

    private static synchronized void decide(MinecraftServer server) {
        if (decidedFor == server) return;
        boolean result;
        String reason;
        try {
            result = MeshForestConfig.ENABLED.get();
            reason = result ? "meshForestEnabled = true (serverconfig/apocalypse_firstlight-trees.toml)"
                    : "meshForestEnabled = false: new chunks get the biomes' vanilla trees";
        } catch (IllegalStateException e) {
            // the server config is not loaded (a server without its config phase): the default
            result = true;
            reason = "server config not loaded, default on";
        }
        on = result;
        why = reason;
        plan = null;
        ForestFeature.reset();
        decidedFor = server;
        LOG.info("[AFL mesh forest] natural mesh forest {} ({})", on ? "ON" : "off", why);
        if (on) prewarm();
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
        // the plan the overworld's noise router was bound to (C2: r1 or the federal experimental plan, never by seed)
        var surface = boundSurface(level.getLevel());
        ForestPlan p = plan;
        if (p != null && p.seed == seed && p.surface == surface) return p;
        if (surface == null || surface.ecology == null) { ForestFeature.NO_PLAN.increment(); return null; }
        p = new ForestPlan(surface, seed);
        plan = p;
        return p;
    }

    /** The plan of a running server's overworld (commands), or null. */
    static ForestPlan plan(MinecraftServer server) {
        long seed = server.getWorldData().worldGenOptions().seed();
        var surface = server.overworld() == null ? null : boundSurface(server.overworld());
        ForestPlan p = plan;
        if (p != null && p.seed == seed && p.surface == surface) return p;
        return surface == null || surface.ecology == null ? null : new ForestPlan(surface, seed);
    }

    private static com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface boundSurface(net.minecraft.server.level.ServerLevel level) {
        var access = (com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        return access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
    }

    private static void prewarm() {
        prewarm = "building";
        var thread = new Thread(() -> {
            long t0 = System.nanoTime();
            for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) for (int t = 0; t < 4; t++)
                ForestPlan.woodFloor(TreeShape.layout(sp, v, t));
            ForestPlan.crown(Species.WHITE_OAK, 0);
            prewarm = String.format("%d layouts in %.0f ms", Species.values().length * TreeShape.VARIANTS * 4, (System.nanoTime() - t0) / 1e6);
            LOG.info("[AFL mesh forest] {}", prewarm);
        }, "AFL mesh forest layouts");
        thread.setDaemon(true);
        thread.start();
    }
}
