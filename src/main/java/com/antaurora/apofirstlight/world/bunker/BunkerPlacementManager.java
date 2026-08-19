package com.antaurora.apofirstlight.world.bunker;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public final class BunkerPlacementManager {
    private static final Logger LOGGER = ApocalypseFirstLight.LOGGER;
    private static final ResourceLocation BUNKER_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "bunker");
    private static final int SEARCH_MIN_RADIUS = 32;
    private static final int SEARCH_MAX_RADIUS = 160;
    private static final int FALLBACK_MAX_RADIUS = 256;
    private static final int MAX_SURFACE_DELTA = 4;
    private static final int TOP_BURIAL_DEPTH = 2;
    private static final int PLACEMENT_VERSION = 1;
    private static final long BUNKER_SALT = 0x0A1B00B5EEDL;

    private BunkerPlacementManager() {}

    public static void ensureGenerated(ServerLevel overworld) {
        if (overworld.dimension() != Level.OVERWORLD) return;
        BunkerSavedData data = overworld.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (data.isGenerated()) return;

        Optional<StructureTemplate> templateOptional = overworld.getServer().getStructureManager().get(BUNKER_ID);
        if (templateOptional.isEmpty()) {
            LOGGER.error("[AFL Bunker] Missing structure template {}", BUNKER_ID);
            return;
        }
        StructureTemplate template = templateOptional.get();
        BlockPos spawn = overworld.getSharedSpawnPos();
        RandomSource random = RandomSource.create(overworld.getSeed() ^ BUNKER_SALT);
        int attempts = 0;
        for (int radius = SEARCH_MIN_RADIUS; radius <= FALLBACK_MAX_RADIUS; radius += 16) {
            for (BlockPos candidate : candidates(spawn, radius)) {
                attempts++;
                Rotation rotation = randomRotation(random);
                Candidate fit = findFit(overworld, template, candidate, rotation);
                if (fit == null) continue;
                if (place(overworld, template, fit, rotation)) {
                    data.markGenerated(fit.origin, rotation.name(), fit.surfaceY, PLACEMENT_VERSION);
                    LOGGER.info("[AFL Bunker] Generated bunker at {} {}, rotation={}, surfaceY={}, attempts={}, chunks={}",
                            fit.origin.getX(), fit.origin.getY(), fit.origin.getZ(), rotation, fit.surfaceY, attempts, fit.chunkCount);
                    return;
                }
            }
        }
        LOGGER.error("[AFL Bunker] Failed to find/place startup bunker after {} attempts", attempts);
    }

    private static Candidate findFit(ServerLevel level, StructureTemplate template, BlockPos candidate, Rotation rotation) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(net.minecraft.world.level.block.Mirror.NONE);
        BoundingBox atZero = template.getBoundingBox(settings, BlockPos.ZERO);
        int minX = atZero.minX() + candidate.getX();
        int maxX = atZero.maxX() + candidate.getX();
        int minZ = atZero.minZ() + candidate.getZ();
        int maxZ = atZero.maxZ() + candidate.getZ();
        int[] samplesX = {minX, (minX + maxX) / 2, maxX};
        int[] samplesZ = {minZ, (minZ + maxZ) / 2, maxZ};
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int waterSamples = 0;
        for (int x : samplesX) for (int z : samplesZ) {
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            boolean fluidDetected = false;
            for (int depth = 1; depth <= 4; depth++) {
                BlockPos sample = new BlockPos(x, y - depth, z);
                if (!level.getFluidState(sample).isEmpty()) {
                    fluidDetected = true;
                    break;
                }
            }
            BlockPos surface = new BlockPos(x, y - 1, z);
            if (fluidDetected || isIceOrWaterSurface(level, surface)) waterSamples++;
            minSurface = Math.min(minSurface, y);
            maxSurface = Math.max(maxSurface, y);
        }
        if (waterSamples > 0) return null;
        if (maxSurface - minSurface > MAX_SURFACE_DELTA) return null;
        int referenceSurface = minSurface;
        int originY = referenceSurface - TOP_BURIAL_DEPTH - atZero.maxY();
        BlockPos origin = new BlockPos(candidate.getX(), originY, candidate.getZ());
        BoundingBox box = template.getBoundingBox(settings, origin);
        if (box.minY() < level.getMinBuildHeight() + 3 || box.maxY() >= level.getMaxBuildHeight()) return null;
        int minChunkX = box.minX() >> 4, maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4, maxChunkZ = box.maxZ() >> 4;
        int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        return new Candidate(origin, referenceSurface, box, minChunkX, maxChunkX, minChunkZ, maxChunkZ, chunkCount);
    }

    private static boolean isIceOrWaterSurface(ServerLevel level, BlockPos surface) {
        return level.getBlockState(surface).is(Blocks.WATER)
                || level.getBlockState(surface).is(Blocks.ICE)
                || level.getBlockState(surface).is(Blocks.FROSTED_ICE)
                || level.getBlockState(surface).is(Blocks.PACKED_ICE)
                || level.getBlockState(surface).is(Blocks.BLUE_ICE);
    }

    private static boolean place(ServerLevel level, StructureTemplate template, Candidate fit, Rotation rotation) {
        for (int x = fit.minChunkX; x <= fit.maxChunkX; x++) {
            for (int z = fit.minChunkZ; z <= fit.maxChunkZ; z++) level.getChunk(x, z);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation)
                .setMirror(net.minecraft.world.level.block.Mirror.NONE);
        return template.placeInWorld(level, fit.origin, fit.origin, settings, RandomSource.create(level.getSeed()), 2);
    }

    private static List<BlockPos> candidates(BlockPos spawn, int radius) {
        return List.of(
                new BlockPos(spawn.getX() + radius, spawn.getY(), spawn.getZ()),
                new BlockPos(spawn.getX() - radius, spawn.getY(), spawn.getZ()),
                new BlockPos(spawn.getX(), spawn.getY(), spawn.getZ() + radius),
                new BlockPos(spawn.getX(), spawn.getY(), spawn.getZ() - radius),
                new BlockPos(spawn.getX() + radius / 2, spawn.getY(), spawn.getZ() + radius / 2),
                new BlockPos(spawn.getX() - radius / 2, spawn.getY(), spawn.getZ() - radius / 2));
    }

    private static Rotation randomRotation(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private record Candidate(BlockPos origin, int surfaceY, BoundingBox box, int minChunkX, int maxChunkX,
                             int minChunkZ, int maxChunkZ, int chunkCount) {}
}
