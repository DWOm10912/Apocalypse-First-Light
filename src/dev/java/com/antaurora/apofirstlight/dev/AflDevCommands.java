package com.antaurora.apofirstlight.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.world.WildlifeSpawnPolicy;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import com.antaurora.apofirstlight.world.bunker.BunkerPlacementManager;
import com.antaurora.apofirstlight.world.bunker.BunkerPlayerSpawnEvents;
import com.antaurora.apofirstlight.client.EnvironmentalParticleController;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AflDevCommands {
    private AflDevCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> mask = Commands.literal("mask_structure_exterior_air");
        mask.then(Commands.argument("input", StringArgumentType.word())
                .then(Commands.argument("output", StringArgumentType.word())
                        .executes(context -> maskExteriorAir(context, false))
                        .then(Commands.literal("--overwrite")
                                .executes(context -> maskExteriorAir(context, true)))));

        LiteralArgumentBuilder<CommandSourceStack> schem = Commands.literal("schem_to_nbt");
        schem.then(Commands.argument("input", StringArgumentType.word())
                .then(Commands.argument("output", StringArgumentType.word())
                        .executes(context -> convert(context, false))
                        .then(Commands.literal("--overwrite")
                                .executes(context -> convert(context, true)))));

        LiteralArgumentBuilder<CommandSourceStack> dev = Commands.literal("dev")
                .requires(source -> source.hasPermission(2));
        dev.then(schem);
        dev.then(mask);
        dev.then(Commands.literal("bunker")
                .then(Commands.literal("status")
                        .executes(AflDevCommands::bunkerStatus)));
        dev.then(Commands.literal("biome_policy")
                .then(Commands.literal("status").executes(AflDevCommands::biomePolicyStatus)));
        dev.then(Commands.literal("inland_terrain")
                .then(Commands.literal("status").executes(AflDevCommands::inlandTerrainStatus)));
        dev.then(Commands.literal("terrain_sample")
                .executes(AflDevCommands::terrainSample));
        dev.then(Commands.literal("wildlife_spawn")
                .then(Commands.literal("status").executes(AflDevCommands::wildlifeSpawnStatus)));
        dev.then(Commands.literal("particle")
                .then(Commands.literal("dead_leaf_debris")
                        .executes(AflDevCommands::forcedDeadLeafDebris)));
        dev.then(Commands.literal("env_particles")
                .then(Commands.literal("status").executes(AflDevCommands::environmentalParticleStatus))
                .then(Commands.literal("reset").executes(AflDevCommands::resetEnvironmentalParticleStatus)));

        CommandNode<CommandSourceStack> afl = event.getDispatcher().getRoot().getChild("afl");
        if (afl != null) {
            afl.addChild(dev.build());
        } else {
            event.getDispatcher().register(Commands.literal("afl").then(dev));
        }
    }

    private static int convert(CommandContext<CommandSourceStack> context, boolean overwrite) {
        String input = StringArgumentType.getString(context, "input");
        String output = StringArgumentType.getString(context, "output");
        try {
            SchemToVanillaStructureConverter.Result result =
                    SchemToVanillaStructureConverter.convert(context.getSource().getServer(), input, output, overwrite);
            context.getSource().sendSuccess(() -> Component.literal(result.summary()), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("AFL schematic conversion failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int maskExteriorAir(CommandContext<CommandSourceStack> context, boolean overwrite) {
        String input = StringArgumentType.getString(context, "input");
        String output = StringArgumentType.getString(context, "output");
        try {
            BunkerExteriorAirMasker.Result result =
                    BunkerExteriorAirMasker.mask(context.getSource().getServer(), input, output, overwrite);
            context.getSource().sendSuccess(() -> Component.literal(result.summary()), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("AFL exterior air mask failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int bunkerStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel overworld = context.getSource().getServer().overworld();
        BunkerSavedData data = overworld.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (!data.isGenerated()) {
            context.getSource().sendSuccess(() -> Component.literal("Generated: false"), false);
            return 1;
        }
        BlockPos preferred = BunkerPlayerSpawnEvents.preferredSpawn(overworld, data);
        BlockPos safe = BunkerPlayerSpawnEvents.findSafePosition(overworld, preferred);
        String playerInfo = " | Player Spawn Local: " + BunkerPlacementManager.PLAYER_SPAWN_LOCAL.toShortString()
                + " | Player Spawn World: " + (preferred == null ? "unavailable" : preferred.toShortString())
                + " | Player Spawn Safe: " + (preferred != null && BunkerPlayerSpawnEvents.isSafe(overworld, preferred));
        if (safe != null && !safe.equals(preferred)) playerInfo += " | Resolved Safe Spawn: " + safe.toShortString();
        String entranceInfo = "";
        ResourceLocation bunkerId = new ResourceLocation("apocalypse_firstlight", "bunker");
        java.util.Optional<StructureTemplate> template = overworld.getServer().getStructureManager().get(bunkerId);
        if (template.isPresent()) {
            BlockPos entrance = BunkerPlacementManager.localToWorld(template.get(), data.getOrigin(),
                    BunkerPlacementManager.parseRotation(data.getRotation()),
                    BunkerPlacementManager.ENTRANCE_SURFACE_LOCAL);
            entranceInfo = " | Entrance Local: " + BunkerPlacementManager.ENTRANCE_SURFACE_LOCAL.toShortString()
                    + " | Entrance World: " + entrance.toShortString()
                    + " | Entrance Surface Delta: " + (entrance.getY() - data.getReferenceSurfaceY());
        }
        final String finalEntranceInfo = entranceInfo;
        final String playerStatus = playerInfo;
        context.getSource().sendSuccess(() -> Component.literal("Generated: true | Origin: "
                + data.getOrigin().toShortString() + " | Rotation: " + data.getRotation()
                + " | Reference Surface Y: " + data.getReferenceSurfaceY()
                + finalEntranceInfo + playerStatus), false);
        return 1;
    }

    private static int biomePolicyStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getServer().overworld();
        java.util.Set<Holder<Biome>> possible = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        long disabledPresent = possible.stream().map(Holder::unwrapKey).flatMap(java.util.Optional::stream)
                .filter(AflVanillaBiomePolicy::isDisabled).count();
        String sourceClass = level.getChunkSource().getGenerator().getBiomeSource().getClass().getName();
        context.getSource().sendSuccess(() -> Component.literal("Policy Enabled: true | Disabled Count: "
                + AflVanillaBiomePolicy.DISABLED_COUNT + " | Allowed Vanilla Base Count: "
                + AflVanillaBiomePolicy.ALLOWED_VANILLA_COUNT + " | BiomeSource class: " + sourceClass
                + " | Possible biome count: " + possible.size() + " | Disabled keys present count: " + disabledPresent), false);
        return 1;
    }

    private static int inlandTerrainStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                pos.getX(), pos.getZ());
        int seaLevel = level.getSeaLevel();
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("unknown");
        context.getSource().sendSuccess(() -> Component.literal("Policy Enabled: true | Overworld Guard: "
                + (level.dimension() == net.minecraft.world.level.Level.OVERWORLD)
                + " | Continents Clamp: [-0.11, 1.0] | Biome: " + biome
                + " | Surface Y: " + surfaceY + " | Sea Level: " + seaLevel), false);
        return 1;
    }

    private static int terrainSample(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        for (int size : new int[]{64, 128, 256}) {
            TerrainSuitabilitySampler.Result result = TerrainSuitabilitySampler.sample(level, center, size);
            String cityFriendly = result.reliable() ? Boolean.toString(result.cityFriendly()) : "UNKNOWN";
            String coverageStatus = result.reliable() ? "" : " LOW_COVERAGE";
            String message = String.format("[AFL TERRAIN SAMPLE] size=%d minY=%d maxY=%d deltaY=%d avgY=%.2f maxLocalSlope=%d waterRatio=%.3f sampledColumns=%d skippedColumns=%d coverage=%.3f sampledSlopeEdges=%d skippedSlopeEdges=%d reliable=%s CITY_FRIENDLY=%s%s",
                    result.size(), result.minY(), result.maxY(), result.deltaY(), result.averageY(),
                    result.maxLocalSlope(), result.waterRatio(), result.sampledColumns(), result.skippedColumns(),
                    result.coverage(), result.sampledSlopeEdges(), result.skippedSlopeEdges(), result.reliable(), cityFriendly, coverageStatus);
            context.getSource().sendSuccess(() -> Component.literal(message), false);
        }
        return 1;
    }

    private static int wildlifeSpawnStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        com.antaurora.apofirstlight.radiation.RadiationZone zone = RadiationManager.getNaturalZone(level, pos);
        boolean allowed = overworld && zone == com.antaurora.apofirstlight.radiation.RadiationZone.SAFE;
        context.getSource().sendSuccess(() -> Component.literal("Dimension: "
                + level.dimension().location() + " | Natural Base Field: "
                + String.format("%.4f", RadiationManager.getNaturalBaseField(level, pos.getX(), pos.getZ()))
                + " | Natural Zone: " + zone + " | Natural SAFE: " + (zone == com.antaurora.apofirstlight.radiation.RadiationZone.SAFE)
                + " | Policy Enabled: true | Vanilla Natural Passive Spawn Allowed Here: " + allowed
                + " | Target Categories: " + WildlifeSpawnPolicy.targetCategories()), false);
        return 1;
    }

    private static int forcedDeadLeafDebris(CommandContext<CommandSourceStack> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                EnvironmentalParticleController.debugSpawnForced());
        context.getSource().sendSuccess(() -> Component.literal("Forced dead_leaf_debris particle test queued."), false);
        return 1;
    }

    private static int environmentalParticleStatus(CommandContext<CommandSourceStack> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            String status = EnvironmentalParticleController.debugStatus();
            net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("[AFL ENV PARTICLES] " + status));
        });
        return 1;
    }

    private static int resetEnvironmentalParticleStatus(CommandContext<CommandSourceStack> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            EnvironmentalParticleController.resetDiagnostics();
            net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("[AFL ENV PARTICLES] diagnostics reset."));
        });
        return 1;
    }
}
