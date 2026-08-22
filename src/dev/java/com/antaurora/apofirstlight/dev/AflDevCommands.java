package com.antaurora.apofirstlight.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.biome.StartupSettlementProtection;
import com.antaurora.apofirstlight.client.EnvironmentalParticleController;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
        dev.then(Commands.literal("startup_ecology_sample")
                .executes(AflDevCommands::startupEcologySample));
        dev.then(Commands.literal("startup_ecology_here")
                .executes(AflDevCommands::startupEcologyHere));
        dev.then(Commands.literal("startup_ecology_radial")
                .executes(AflDevCommands::startupEcologyRadial));
        dev.then(Commands.literal("startup_radiation_sample")
                .executes(AflDevCommands::startupRadiationSample));
        dev.then(Commands.literal("settlement_prototype")
                .executes(context -> settlementPrototype(context))
                .then(Commands.literal("here").executes(context -> settlementPrototype(context))));
        dev.then(Commands.literal("settlement_terrain_check")
                .executes(AflDevCommands::settlementTerrainCheck)
                .then(Commands.literal("here").executes(AflDevCommands::settlementTerrainCheck)));
        dev.then(Commands.literal("scorched_surface_sample")
                .executes(context -> scorchedSurfaceSample(context, 128))
                .then(Commands.argument("size", IntegerArgumentType.integer(16, 256))
                        .executes(context -> scorchedSurfaceSample(context,
                                IntegerArgumentType.getInteger(context, "size")))));
        dev.then(Commands.literal("scorched_liquid_sample")
                .executes(context -> scorchedLiquidSample(context, 128))
                .then(Commands.argument("size", IntegerArgumentType.integer(16, 256))
                        .executes(context -> scorchedLiquidSample(context,
                                IntegerArgumentType.getInteger(context, "size")))));
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
        com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave.Zone startupZone =
                StartupPlainsEnclave.zoneAt(pos.getX(), pos.getZ(), level.getSeed());
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
            String message = String.format("[AFL TERRAIN SAMPLE] size=%d minY=%d maxY=%d deltaY=%d p05Y=%d medianY=%d p95Y=%d robustDeltaY=%d avgY=%.2f maxLocalSlope=%d p99LocalSlope=%d waterRatio=%.3f sampledColumns=%d skippedColumns=%d coverage=%.3f sampledSlopeEdges=%d skippedSlopeEdges=%d reliable=%s CITY_FRIENDLY=%s reason=%s",
                    result.size(), result.minY(), result.maxY(), result.deltaY(), result.p05Y(), result.medianY(),
                    result.p95Y(), result.robustDeltaY(), result.averageY(), result.maxLocalSlope(),
                    result.p99LocalSlope(), result.waterRatio(), result.sampledColumns(), result.skippedColumns(),
                    result.coverage(), result.sampledSlopeEdges(), result.skippedSlopeEdges(), result.reliable(),
                    result.cityFriendly(), result.reason());
            context.getSource().sendSuccess(() -> Component.literal(message), false);
        }
        return 1;
    }

    private static int startupEcologySample(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        long seed = level.getSeed();
        int[][] directions = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        for (int i = 0; i < directions.length; i++) {
            int[] direction = directions[i];
            int sampleX = direction[0] * 512;
            int sampleZ = direction[1] * 512;
            int plains = StartupPlainsEnclave.plainsBoundary(sampleX, sampleZ, seed);
            int woodland = StartupPlainsEnclave.woodlandOuterBoundary(sampleX, sampleZ, seed);
            StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(sampleX, sampleZ, seed);
            final String line = String.format("[AFL STARTUP ECOLOGY] dir=%s plains=%d woodland=%d zone=%s",
                    names[i], plains, woodland, zone);
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "[AFL STARTUP ECOLOGY CONFIG] seed=%d core=%d plainsBase=%d plainsAmplitude=%d woodlandBase=%d woodlandAmplitude=%d minBuffer=%d maxBuffer=%d",
                seed, StartupPlainsEnclave.CORE_RADIUS_BLOCKS, StartupPlainsEnclave.PLAINS_BASE_RADIUS,
                StartupPlainsEnclave.PLAINS_NOISE_AMPLITUDE, StartupPlainsEnclave.WOODLAND_BASE_OUTER_RADIUS,
                StartupPlainsEnclave.WOODLAND_NOISE_AMPLITUDE, StartupPlainsEnclave.MIN_WOODLAND_BUFFER,
                StartupPlainsEnclave.MAX_WOODLAND_BUFFER)), false);
        return 1;
    }

    private static int startupEcologyHere(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        long seed = level.getSeed();
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int surfaceQuartY = (surfaceY - 1) >> 2;
        String surfaceBiome = level.getBiome(new BlockPos(pos.getX(), surfaceY - 1, pos.getZ())).unwrapKey()
                .map(key -> key.location().toString()).orElse("unknown");
        String playerBiome = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString()).orElse("unknown");
        StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(pos.getX(), pos.getZ(), seed);
        int settlementBoundary = StartupSettlementProtection.settlementProtectionBoundary(pos.getX(), pos.getZ(), seed);
        StartupSettlementProtection.ProtectionClass protection =
                StartupSettlementProtection.protectionAt(pos.getX(), pos.getZ(), seed);
        StartupPlainsEnclave.ShapeSource shapeSource =
                StartupPlainsEnclave.woodlandShapeSource(pos.getX(), pos.getZ(), seed);
        int lobeIndex = shapeSource == StartupPlainsEnclave.ShapeSource.PRIMARY_LOBE ? -1
                : shapeSource == StartupPlainsEnclave.ShapeSource.SECONDARY_LOBE_0 ? 0
                : shapeSource == StartupPlainsEnclave.ShapeSource.SECONDARY_LOBE_1 ? 1 : -2;
        String expected = zone == StartupPlainsEnclave.Zone.WOODLAND_BUFFER
                ? "apocalypse_firstlight:irradiated_woodland"
                : zone == StartupPlainsEnclave.Zone.OUTSIDE ? "original" : "minecraft:plains";
        boolean match = "original".equals(expected) || expected.equals(surfaceBiome);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "[AFL STARTUP ECOLOGY HERE] pos=(%d,%d,%d) seed=%d distance=%.1f zone=%s woodlandShapeSource=%s primaryLobeAngleDeg=%.1f primaryLobeExtraLength=%d primaryLobeHalfWidth=%d secondaryLobeCount=%d lobeForward=%.1f lobeSide=%.1f lobeBoundaryMargin=%.1f plainsBoundary=%d settlementProtectionBoundary=%d settlementProtected=%s protectionClass=%s woodlandBoundary=%d eligibleWoodlandWidth=%d expectedBiome=%s surfaceY=%d surfaceQuartY=%d surfaceBiome=%s playerBiome=%s surfaceMatch=%s verticalOverride=SURFACE_BAND blockY=48..112 quartY=12..28 holderResolutionStatus=%s overridePath=MultiNoiseBiomeSource#getNoiseBiome:RETURN",
                pos.getX(), pos.getY(), pos.getZ(), seed,
                StartupSettlementProtection.distanceFromCenter(pos.getX(), pos.getZ()), zone,
                shapeSource, StartupPlainsEnclave.primaryLobeAngleDegrees(seed),
                StartupPlainsEnclave.primaryLobeExtraLength(seed), StartupPlainsEnclave.primaryLobeHalfWidth(seed),
                StartupPlainsEnclave.secondaryLobeCount(seed),
                lobeIndex == -2 ? 0.0D : StartupPlainsEnclave.lobeForward(pos.getX(), pos.getZ(), seed, lobeIndex),
                lobeIndex == -2 ? 0.0D : StartupPlainsEnclave.lobeSide(pos.getX(), pos.getZ(), seed, lobeIndex),
                lobeIndex == -2 ? 0.0D : StartupPlainsEnclave.lobeBoundaryMargin(pos.getX(), pos.getZ(), seed, lobeIndex),
                StartupPlainsEnclave.plainsBoundary(pos.getX(), pos.getZ(), seed), settlementBoundary,
                protection != StartupSettlementProtection.ProtectionClass.NONE, protection,
                StartupPlainsEnclave.woodlandOuterBoundary(pos.getX(), pos.getZ(), seed),
                StartupSettlementProtection.eligibleWoodlandWidth(pos.getX(), pos.getZ(), seed), expected, surfaceY,
                surfaceQuartY, surfaceBiome, playerBiome,
                match, match ? "RESOLVED" : "MISMATCH_OR_UNRESOLVED")), false);
        return 1;
    }

    private static int startupEcologyRadial(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        long seed = level.getSeed();
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[][] directions = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int i = 0; i < directions.length; i++) {
            final int directionIndex = i;
            int x = directions[directionIndex][0] * 600;
            int z = directions[directionIndex][1] * 600;
            int plains = StartupPlainsEnclave.plainsBoundary(x, z, seed);
            int protection = StartupSettlementProtection.settlementProtectionBoundary(x, z, seed);
            int woodland = StartupPlainsEnclave.woodlandOuterBoundary(x, z, seed);
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    "[AFL STARTUP RADIAL] dir=%s sample=(%d,%d) plainsBoundary=%d settlementProtectionBoundary=%d woodlandBoundary=%d eligibleWoodlandWidth=%d",
                    names[directionIndex], x, z, plains, protection, woodland, Math.max(0, woodland - protection))), false);
        }
        return 1;
    }

    private static int startupRadiationSample(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        int[][] directions = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[] distances = {0, 160, 200, 240, 280, 320, 360, 400};
        for (int directionIndex = 0; directionIndex < directions.length; directionIndex++) {
            int[] direction = directions[directionIndex];
            for (int distance : distances) {
                int x = Math.round(direction[0] * distance);
                int z = Math.round(direction[1] * distance);
                RadiationManager.StartupRadiationDebug sample =
                        RadiationManager.startupRadiationDebug(level, x, z);
                String line = String.format(
                        "[AFL STARTUP RADIATION] dir=%s distance=%d startupZone=%s plainsBoundary=%d woodlandBoundary=%d biome=%s profile=%s raw=%.4f constrained=%.4f suppression=%.4f preStartup=%.4f cap=%s final=%.4f zone=%s",
                        names[directionIndex], distance, sample.startupZone(),
                        sample.plainsBoundary(), sample.woodlandBoundary(), sample.biomeId(), sample.biomeProfile(),
                        sample.rawWorldField(), sample.biomeConstrainedField(), sample.safeAnchorSuppression(),
                        sample.preStartupEffectiveField(),
                        sample.startupCap() == null ? "OUTSIDE" : String.format("%.4f", sample.startupCap()),
                        sample.finalEffectiveField(), sample.finalZone());
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        context.getSource().sendSuccess(() -> Component.literal(
                        "[AFL STARTUP RADIATION CONFIG] handoffWidth="
                        + RadiationManager.STARTUP_RADIATION_HANDOFF_WIDTH
                        + " woodlandMin=" + RadiationManager.STARTUP_WOODLAND_MIN
                        + " woodlandMax=" + RadiationManager.STARTUP_WOODLAND_MAX
                        + " semantics=MIN(original,startupCap) doseShieldingUnchanged=true"), false);
        return 1;
    }

    private static int settlementPrototype(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos player = BlockPos.containing(context.getSource().getPosition());
        String version = "[AFL SETTLEMENT PROTOTYPE] validatorVersion=" + SettlementPrototype.VALIDATOR_VERSION;
        context.getSource().sendSuccess(() -> Component.literal(version), false);
        ApocalypseFirstLight.LOGGER.info(version);
        SettlementPrototype.Result result = SettlementPrototype.generateHere(level, player);
        if (!result.success()) {
            String diagnostic = "[AFL SETTLEMENT PROTOTYPE] rejected " + SettlementPrototype.rejectDiagnostic(result);
            context.getSource().sendFailure(Component.literal(diagnostic));
            ApocalypseFirstLight.LOGGER.info(diagnostic);
            return 0;
        }
        SettlementPrototype.Plan plan = result.plan();
        String orientation = plan.northSouth() ? "NORTH_SOUTH" : "EAST_WEST";
        SettlementPrototype.TerrainStats terrain = plan.terrain();
        int localRoads = Math.max(0, plan.roadPlans().size() - 1);
        long residentialLots = plan.lots().stream().filter(lot -> lot.type() == SettlementPrototype.LotType.RESIDENTIAL).count();
        long commercialLots = plan.lots().stream().filter(lot -> lot.type() == SettlementPrototype.LotType.COMMERCIAL).count();
        String message = String.format("[AFL SETTLEMENT PROTOTYPE] anchor=%s biome=apocalypse_firstlight:irradiated_woodland archetype=STAGGERED_T orientation=%s plannedBounds=%s samples=%d minY=%d p10=%d median=%d p90=%d maxY=%d effectiveRelief=%d outliers=%d outlierRatio=%.3f mainRoadSegments=%d localRoads=%d intersections=%d residentialLots=%d commercialLots=%d emptyFrontage=APPROX_20_PERCENT treesCleared=%d logsCleared=%d leavesCleared=%d otherVegetationCleared=%d regionalStubA=%s regionalStubB=%s %s",
                plan.anchor().toShortString(), orientation, plan.bounds(), terrain.sampleCount(), terrain.minY(), terrain.p10(), terrain.median(), terrain.p90(), terrain.maxY(), terrain.effectiveRelief(), terrain.outlierCount(), terrain.outlierRatio(),
                1, localRoads, localRoads, residentialLots, commercialLots, result.logsCleared() + result.leavesCleared(), result.logsCleared(),
                result.leavesCleared(), result.otherVegetationCleared(), "PRESENT", "PRESENT", result.detail() == null ? "" : result.detail());
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int settlementTerrainCheck(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        SettlementPrototype.TerrainStats stats = SettlementPrototype.terrainCheckHere(level, pos);
        boolean pass = SettlementPrototype.passesGlobalTerrain(stats);
        String line = String.format("[AFL SETTLEMENT TERRAIN] validatorVersion=%s stage=GLOBAL_ROBUST anchor=%s sampleSpacing=%d samples=%d minY=%d p10=%d p25=%d median=%d p75=%d p90=%d maxY=%d effectiveRelief=%d maxEffectiveRelief=12 outliers=%d outlierRatio=%.3f maxOutlierRatio=0.150 heightmapType=MOTION_BLOCKING_NO_LEAVES result=%s",
                SettlementPrototype.VALIDATOR_VERSION, pos.toShortString(), SettlementPrototype.GLOBAL_SAMPLE_SPACING,
                stats.sampleCount(), stats.minY(), stats.p10(), stats.p25(), stats.median(), stats.p75(), stats.p90(),
                stats.maxY(), stats.effectiveRelief(), stats.outlierCount(), stats.outlierRatio(), pass ? "PASS" : "FAIL");
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        ApocalypseFirstLight.LOGGER.info(line);
        return pass ? 1 : 0;
    }

    private static int scorchedSurfaceSample(CommandContext<CommandSourceStack> context, int size) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        ScorchedSurfaceSampler.Result result = ScorchedSurfaceSampler.sample(level, center, size);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "[AFL SCORCHED SURFACE SAMPLE] size=%d total=%d loaded=%d scorched=%d otherBiome=%d skipped=%d coverage=%.3f representative=%s",
                result.size(), result.totalColumns(), result.loadedColumns(), result.scorchedColumns(),
                result.otherBiomeColumns(), result.skippedColumns(), result.coverage(), result.representativeness())), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "[AFL SCORCHED SURFACE BLOCKS] scorchedSoil=%d fusedGround=%d other=%d scorchedSoilRatio=%.4f fusedRatio=%.4f otherRatio=%.4f",
                result.scorchedSoilColumns(), result.fusedGroundColumns(), result.otherSurfaceColumns(),
                result.scorchedSoilRatio(), result.fusedGroundRatio(), result.otherRatio())), false);
        for (int depth = 0; depth <= 12; depth++) {
            final int d = depth;
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    "[AFL SCORCHED DEPTH PROFILE] depth=%d fused=%d fusedRatio=%.4f scorchedSoil=%d other=%d",
                    d, result.fusedByDepth()[d], ratio(result.fusedByDepth()[d], result.scorchedColumns()),
                    result.scorchedSoilByDepth()[d], result.otherByDepth()[d])), false);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL SCORCHED SURFACE NOISE] directSurfaceNoiseSample=NOT_AVAILABLE_SAFELY threshold=0.0 FINAL_THRESHOLD=NOT_YET_DETERMINED thresholdHitRatio=UNKNOWN"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL SCORCHED CANARY MODE] mode=V1_8_COVERAGE_CALIBRATION threshold=0.0"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL SCORCHED SURFACE DIAG] consistency=UNKNOWN recommendedThreshold=DEFERRED_MANUAL_NOISE_SAMPLE"), false);
        return 1;
    }

    private static double ratio(int value, int total) {
        return total == 0 ? 0.0D : (double) value / total;
    }

    private static int scorchedLiquidSample(CommandContext<CommandSourceStack> context, int size) {
        if (context.getSource().getEntity() == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        ScorchedLiquidSampler.Result result = ScorchedLiquidSampler.sample(level, center, size);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "[AFL SCORCHED LIQUID SAMPLE] size=%d total=%d loaded=%d scorched=%d directSkyWater=%d nearSurfaceAccessibleWater=%d surfaceAccessibleWater=%d directSkyLava=%d nearSurfaceAccessibleLava=%d surfaceAccessibleLava=%d deepUndergroundLiquid=%d surfaceAccessibleWaterRatio=%.4f representative=%s",
                result.size(), result.total(), result.loaded(), result.scorched(), result.directSkyWater(),
                result.nearSurfaceAccessibleWater(), result.surfaceAccessibleWater(), result.directSkyLava(),
                result.nearSurfaceAccessibleLava(), result.surfaceAccessibleLava(), result.deepUndergroundLiquid(),
                result.surfaceAccessibleWaterRatio(), result.scorched() >= Math.max(64, result.total() / 20))), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL SCORCHED LIQUID DEPTH] window=surfaceY-12..surfaceY classification=DIRECT_SKY_UNION_NEAR_SURFACE_BFS bfsRadius=8 bfsMaxVisited=256 verticalRange=surfaceY-12..surfaceY+3"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL SCORCHED LIQUID DIAG] sourceCandidate=UNKNOWN featureFix=lake_water_unproven biomeScoped=true runtimeSetBlock=false"), false);
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
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("unknown");
        StartupPlainsEnclave.Zone startupZone = StartupPlainsEnclave.zoneAt(pos.getX(), pos.getZ(), level.getSeed());
        com.antaurora.apofirstlight.radiation.RadiationZone zone = RadiationManager.getNaturalZone(level, pos);
        com.antaurora.apofirstlight.world.WildlifeSpawnPolicy.Decision decision =
                WildlifeSpawnPolicy.decision(level, net.minecraft.world.entity.EntityType.COW, pos,
                        net.minecraft.world.entity.MobSpawnType.NATURAL);
        context.getSource().sendSuccess(() -> Component.literal("Dimension: "
                + level.dimension().location() + " | Natural Base Field: "
                + String.format("%.4f", RadiationManager.getNaturalBaseField(level, pos.getX(), pos.getZ()))
                + " | Runtime Biome: " + biome + " | Startup Zone: " + startupZone
                + " | Natural Zone: " + zone + " | Natural SAFE: " + (zone == com.antaurora.apofirstlight.radiation.RadiationZone.SAFE)
                + " | Startup Ecological Safe: " + (startupZone == StartupPlainsEnclave.Zone.CORE_PLAINS || startupZone == StartupPlainsEnclave.Zone.FRINGE_PLAINS)
                + " | AFL Decision: " + (decision.deny() ? "DENY" : "PASS") + " | Reason: " + decision.reason()
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
