package com.antaurora.apofirstlight.vegetation;

import com.antaurora.apofirstlight.tree.ForestGate;
import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * AFL vegetation P2a (docs/gameplay/vegetation_v1_design.md 14.5): the registrations that make the mesh plants grow, and
 * the plan of a world.
 * <ul>
 * <li>{@code apocalypse_firstlight:vegetation}: the feature (VegetationFeature) and a biome modifier serializer of the same
 * id. The modifier appends the feature to VEGETAL_DECORATION in Phase.AFTER_EVERYTHING: the mesh forest goes in at
 * Phase.ADD, and within one phase the modifiers' order is not kept (C, 2026-10-24), so the plants come after the trees and
 * find their trunks and roots.</li>
 * <li>The plan: the overworld's Terrain V2 plan (the one its noise router was bound to, as ForestGate), the mesh forest's
 * plan when it grows, the world's road works when it has roads. None of it on another dimension or a world without a plan.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VegetationGate {
    public static final ResourceLocation ID = new ResourceLocation("apocalypse_firstlight", "vegetation");

    private static volatile VegetationPlan plan;

    private VegetationGate() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.FEATURES, helper -> helper.register(ID, new VegetationFeature()));
        event.register(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, helper -> helper.register(ID, Modifier.CODEC));
    }

    /** Appends the vegetation feature to the listed biomes' VEGETAL_DECORATION, after every other modifier. */
    public record Modifier(HolderSet<Biome> biomes, Holder<PlacedFeature> feature) implements BiomeModifier {
        static final Codec<Modifier> CODEC = RecordCodecBuilder.create(b -> b.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(Modifier::biomes),
                PlacedFeature.CODEC.fieldOf("feature").forGetter(Modifier::feature)).apply(b, Modifier::new));

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.AFTER_EVERYTHING || !biomes.contains(biome)) return;
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, feature);
        }

        @Override
        public Codec<? extends BiomeModifier> codec() { return CODEC; }
    }

    /** What the feature has done since the game started (dev statistics). */
    public static String status() { return VegetationFeature.status(); }

    /** The block a plan's plant grows as (P2a: its stand-in). */
    public static net.minecraft.world.level.block.state.BlockState block(VegetationPlan.Plant p) { return VegetationFeature.block(p); }

    /** The plan of a server level when plants grow there (the overworld with a Terrain V2 plan), or null. */
    public static VegetationPlan plan(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) return null;
        long seed = level.getSeed();
        var access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        TerrainPlanSurface surface = access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
        VegetationPlan p = plan;
        if (p != null && p.seed == seed && p.surface == surface) return p;
        if (surface == null || surface.ecology == null) return null;
        var network = RoadNetworkRuntime.bound(level);
        p = new VegetationPlan(surface, seed, ForestGate.enabled() ? new ForestPlan(surface, seed) : null, network == null ? null : network.works());
        plan = p;
        return p;
    }
}
