package com.antaurora.apofirstlight.world.bunker;

import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BunkerWorldEvents {
    private BunkerWorldEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ENCLAVE] center=({}, {}), coreRadius={}, outerRadius={}, coordinateSpace=BLOCK",
                StartupPlainsEnclave.CENTER_X, StartupPlainsEnclave.CENTER_Z,
                StartupPlainsEnclave.CORE_RADIUS_BLOCKS, StartupPlainsEnclave.OUTER_RADIUS_BLOCKS);
        probeLiveBiomeSource(overworld);
        auditStartupSurface(overworld);
        BunkerPlacementManager.ensureGenerated(overworld);
        RadiationManager.ensureBunkerAnchor(overworld);
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
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample[0], sample[1]);
            BlockPos surface = new BlockPos(sample[0], surfaceY - 1, sample[1]);
            ResourceLocation biomeId = level.getBiome(surface).unwrapKey()
                    .map(key -> key.location()).orElse(new ResourceLocation("minecraft", "unknown"));
            boolean plains = "minecraft".equals(biomeId.getNamespace()) && "plains".equals(biomeId.getPath());
            ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ENCLAVE AUDIT] pos=({}, {}) surfaceY={} biome={} expected=plains result={}",
                    sample[0], sample[1], surfaceY, biomeId, plains ? "PASS" : "FAIL");
        }
        int[][] boundarySamples = {{208, 0}, {-208, 0}, {0, 208}, {0, -208}};
        for (int[] sample : boundarySamples) {
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample[0], sample[1]);
            BlockPos surface = new BlockPos(sample[0], surfaceY - 1, sample[1]);
            ResourceLocation biomeId = level.getBiome(surface).unwrapKey()
                    .map(key -> key.location()).orElse(new ResourceLocation("minecraft", "unknown"));
            ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ENCLAVE BOUNDARY AUDIT] pos=({}, {}) surfaceY={} biome={} insideCore={}",
                    sample[0], sample[1], surfaceY, biomeId,
                    StartupPlainsEnclave.containsBlock(sample[0], sample[1]));
        }
    }
}
