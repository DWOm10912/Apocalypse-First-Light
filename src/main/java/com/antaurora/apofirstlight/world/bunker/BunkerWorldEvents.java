package com.antaurora.apofirstlight.world.bunker;

import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.biome.StartupSettlementProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BunkerWorldEvents {
    private static final Map<MinecraftServer, Integer> PENDING_PLACEMENT_TICKS = new WeakHashMap<>();

    private BunkerWorldEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ENCLAVE] center=({}, {}) coreRadius={} plainsBaseRadius={} plainsNoiseAmplitude={} plainsNoiseScale={} woodlandBaseOuterRadius={} woodlandNoiseAmplitude={} woodlandNoiseScale={} minWoodlandBuffer={} maxWoodlandBuffer={} settlementProtectionDepth={} coordinateSpace=BLOCK",
                StartupPlainsEnclave.CENTER_X, StartupPlainsEnclave.CENTER_Z,
                StartupPlainsEnclave.CORE_RADIUS_BLOCKS, StartupPlainsEnclave.PLAINS_BASE_RADIUS,
                StartupPlainsEnclave.PLAINS_NOISE_AMPLITUDE, 128,
                StartupPlainsEnclave.WOODLAND_BASE_OUTER_RADIUS, StartupPlainsEnclave.WOODLAND_NOISE_AMPLITUDE,
                StartupPlainsEnclave.WOODLAND_NOISE_SCALE, StartupPlainsEnclave.MIN_WOODLAND_BUFFER,
                StartupPlainsEnclave.MAX_WOODLAND_BUFFER,
                StartupSettlementProtection.STARTUP_SETTLEMENT_WOODLAND_PROTECTION_DEPTH);
        long seed = overworld.getSeed();
        ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ECOLOGY SHAPE] seed={} center=({}, {}) baseRingOuter={} primaryAngleDeg={} primaryExtraLength={} primaryHalfWidth={} secondaryCount={} secondary0AngleDeg={} secondary0ExtraLength={} secondary0HalfWidth={} secondary1AngleDeg={} secondary1ExtraLength={} secondary1HalfWidth={} lobeStartOverlap={} shapeSource=BASE_PRIMARY_SECONDARY_ORIGINAL_OUTSIDE",
                seed, StartupPlainsEnclave.CENTER_X, StartupPlainsEnclave.CENTER_Z,
                StartupPlainsEnclave.WOODLAND_BASE_OUTER_RADIUS,
                StartupPlainsEnclave.primaryLobeAngleDegrees(seed),
                StartupPlainsEnclave.primaryLobeExtraLength(seed),
                StartupPlainsEnclave.primaryLobeHalfWidth(seed),
                StartupPlainsEnclave.secondaryLobeCount(seed),
                StartupPlainsEnclave.secondaryLobeAngleDegrees(seed, 0),
                StartupPlainsEnclave.secondaryLobeExtraLength(seed, 0),
                StartupPlainsEnclave.secondaryLobeHalfWidth(seed, 0),
                StartupPlainsEnclave.secondaryLobeAngleDegrees(seed, 1),
                StartupPlainsEnclave.secondaryLobeExtraLength(seed, 1),
                StartupPlainsEnclave.secondaryLobeHalfWidth(seed, 1),
                128);
        probeLiveBiomeSource(overworld);
        auditStartupSurface(overworld);
        PENDING_PLACEMENT_TICKS.put(event.getServer(), 0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Integer pending = PENDING_PLACEMENT_TICKS.get(server);
        if (pending == null) return;

        int ticks = pending + 1;
        if (ticks < 20) {
            PENDING_PLACEMENT_TICKS.put(server, ticks);
            return;
        }

        ServerLevel overworld = server.overworld();
        if (!BunkerPlacementManager.isStartupAreaReady(overworld)) {
            PENDING_PLACEMENT_TICKS.put(server, ticks);
            if (ticks % 20 == 0) {
                ApocalypseFirstLight.LOGGER.info("[AFL Bunker] Waiting for reliable startup chunks before placement; ticks={} loadedChunks={}",
                        ticks, overworld.getChunkSource().getLoadedChunksCount());
            }
            return;
        }

        BunkerPlacementManager.ensureGenerated(overworld);
        BunkerSavedData data = overworld.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (!data.isGenerated()) {
            PENDING_PLACEMENT_TICKS.remove(server);
            return;
        }
        RadiationManager.ensureBunkerAnchor(overworld);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BunkerPlayerSpawnEvents.tryInitialSpawn(player);
        }
        PENDING_PLACEMENT_TICKS.remove(server);
    }

    private static void probeLiveBiomeSource(ServerLevel overworld) {
        var chunkSource = overworld.getChunkSource();
        ChunkGenerator generator = chunkSource.getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        String sourceIdentity = Integer.toHexString(System.identityHashCode(biomeSource));
        ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] chunkSourceClass={} chunkGeneratorClass={}",
                chunkSource.getClass().getName(), generator.getClass().getName());
        ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] biomeSourceClass={} biomeSourceIdentity={}",
                biomeSource.getClass().getName(), sourceIdentity);
        boolean multiNoise = biomeSource instanceof MultiNoiseBiomeSource;
        ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] isMultiNoiseBiomeSource={} multiNoiseClassExact={}",
                multiNoise, MultiNoiseBiomeSource.class.getName());
        if (!multiNoise) {
            Class<?> sourceClass = biomeSource.getClass();
            ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] superclass={} interfaces={}",
                    sourceClass.getSuperclass() == null ? "NONE" : sourceClass.getSuperclass().getName(),
                    java.util.Arrays.toString(sourceClass.getInterfaces()));
        }
        try {
            var method = biomeSource.getClass().getDeclaredMethod("getNoiseBiome", int.class, int.class,
                    int.class, net.minecraft.world.level.biome.Climate.Sampler.class);
            ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] getNoiseBiomeDeclaringClass={}",
                    method.getDeclaringClass().getName());
        } catch (NoSuchMethodException exception) {
            ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] getNoiseBiomeDeclaringClass={} (inherited or unavailable)",
                    BiomeSource.class.getName());
        }
        ApocalypseFirstLight.LOGGER.info("[AFL BIOME SOURCE PROBE] DIRECT_CALL_TRACE_EXPECTED={}", multiNoise);

        probeBiomeAt(overworld, biomeSource, 0, 0, sourceIdentity);
        probeBiomeAt(overworld, biomeSource, 64, 0, sourceIdentity);
    }

    private static void probeBiomeAt(ServerLevel level, BiomeSource biomeSource, int blockX, int blockZ,
                                      String sourceIdentity) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
        int quartX = QuartPos.fromBlock(blockX);
        int quartY = QuartPos.fromBlock(surfaceY);
        int quartZ = QuartPos.fromBlock(blockZ);
        Holder<Biome> direct = biomeSource.getNoiseBiome(quartX, quartY, quartZ,
                level.getChunkSource().randomState().sampler());
        BlockPos position = new BlockPos(blockX, surfaceY, blockZ);
        Holder<Biome> levelResult = level.getBiome(position);
        boolean sameKey = direct.unwrapKey().isPresent() && levelResult.unwrapKey().isPresent()
                && direct.unwrapKey().get().equals(levelResult.unwrapKey().get());
        ApocalypseFirstLight.LOGGER.info(
                "[AFL BIOME SOURCE PROBE] pos=({}, {}, {}) quart=({}, {}, {}) directBiomeSourceResult={} levelGetBiomeResult={} sameHolder={} sameBiomeKey={} sourceIdentity={}",
                blockX, surfaceY, blockZ, quartX, quartY, quartZ, biomeId(direct), biomeId(levelResult),
                direct == levelResult, sameKey, sourceIdentity);
    }

    private static ResourceLocation biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location()).orElse(new ResourceLocation("minecraft", "unknown"));
    }

    private static void auditStartupSurface(ServerLevel level) {
        int[][] samples = {{0, 0}, {64, 0}, {-64, 0}, {0, 64}, {0, -64}, {160, 0}};
        for (int[] sample : samples) {
            logSurfaceAudit(level, sample[0], sample[1], true);
        }
        int[][] boundarySamples = {{208, 0}, {-208, 0}, {0, 208}, {0, -208}};
        for (int[] sample : boundarySamples) {
            logSurfaceAudit(level, sample[0], sample[1], false);
        }
    }

    private static void logSurfaceAudit(ServerLevel level, int x, int z, boolean startupCoreSample) {
        ChunkAccess chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        boolean loaded = chunk != null;
        String status = loaded ? String.valueOf(chunk.getHighestGeneratedStatus()) : "UNLOADED";
        boolean primed = loaded && chunk.hasPrimedHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
        boolean ready = loaded && chunk.getHighestGeneratedStatus().isOrAfter(ChunkStatus.FULL) && primed;
        if (!ready) {
            ApocalypseFirstLight.LOGGER.info("[AFL BUNKER SURFACE SAMPLE] candidate=({}, {}) chunkLoaded={} chunkStatus={} heightmapType=MOTION_BLOCKING_NO_LEAVES surfaceY=UNAVAILABLE surfaceAvailable=false rejectReason=UNAVAILABLE_SURFACE startupAudit=true",
                    x, z, loaded, status);
            return;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos surface = new BlockPos(x, surfaceY - 1, z);
        ResourceLocation biomeId = level.getBiome(surface).unwrapKey()
                .map(key -> key.location()).orElse(new ResourceLocation("minecraft", "unknown"));
        if (startupCoreSample) {
            boolean plains = "minecraft".equals(biomeId.getNamespace()) && "plains".equals(biomeId.getPath());
            ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ENCLAVE AUDIT] pos=({}, {}) surfaceY={} biome={} expected=plains result={}",
                    x, z, surfaceY, biomeId, plains ? "PASS" : "FAIL");
        } else {
            ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ENCLAVE BOUNDARY AUDIT] pos=({}, {}) surfaceY={} biome={} insideCore={}",
                    x, z, surfaceY, biomeId, StartupPlainsEnclave.containsBlock(x, z));
        }
    }
}
