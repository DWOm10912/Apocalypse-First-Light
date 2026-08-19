package com.antaurora.apofirstlight.world.bunker;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
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
    public static final BlockPos PLAYER_SPAWN_LOCAL = new BlockPos(5, 1, 9);
    private static final Logger LOGGER = ApocalypseFirstLight.LOGGER;
    private static final ResourceLocation BUNKER_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "bunker");
    private static final int SEARCH_MIN_RADIUS = 32;
    private static final int SEARCH_MAX_RADIUS = 160;
    private static final int FALLBACK_MAX_RADIUS = 256;
    private static final int PRIMARY_MAX_SURFACE_DELTA = 4;
    private static final int FALLBACK_MAX_SURFACE_DELTA = 7;
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
        int rejectedWater = 0;
        int rejectedSlope = 0;
        int rejectedHeight = 0;
        int placementFailures = 0;
        for (int radius = SEARCH_MIN_RADIUS; radius <= FALLBACK_MAX_RADIUS; radius += 16) {
            for (BlockPos candidate : candidates(spawn, radius)) {
                attempts++;
                Rotation rotation = randomRotation(random);
                FitResult result = findFit(overworld, template, candidate, rotation,
                        radius <= SEARCH_MAX_RADIUS ? PRIMARY_MAX_SURFACE_DELTA : FALLBACK_MAX_SURFACE_DELTA);
                switch (result.reason) {
                    case WATER -> { rejectedWater++; continue; }
                    case SLOPE -> { rejectedSlope++; continue; }
                    case HEIGHT -> { rejectedHeight++; continue; }
                    case OK -> { }
                }
                Candidate fit = result.candidate;
                if (!place(overworld, template, fit, rotation)) {
                    placementFailures++;
                    continue;
                }
                data.markGenerated(fit.origin, rotation.name(), fit.surfaceY, PLACEMENT_VERSION);
                LOGGER.info("[AFL Bunker] Generated bunker at {} {}, rotation={}, surfaceY={}, tier={}, attempts={}, chunks={}",
                        fit.origin.getX(), fit.origin.getY(), fit.origin.getZ(), rotation, fit.surfaceY,
                        radius <= SEARCH_MAX_RADIUS ? "PRIMARY" : "FALLBACK", attempts, fit.chunkCount);
                return;
            }
        }
        LOGGER.error("[AFL Bunker] Failed to find/place startup bunker after {} attempts; water={}, slope={}, height={}, placementFailures={}",
                attempts, rejectedWater, rejectedSlope, rejectedHeight, placementFailures);
    }

    private static FitResult findFit(ServerLevel level, StructureTemplate template, BlockPos candidate, Rotation rotation,
                                     int allowedSlopeDelta) {
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
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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
        if (waterSamples > 0) return FitResult.water();
        if (maxSurface - minSurface > allowedSlopeDelta) return FitResult.slope();
        int referenceSurface = minSurface;
        int originY = referenceSurface - TOP_BURIAL_DEPTH - atZero.maxY();
        BlockPos origin = new BlockPos(candidate.getX(), originY, candidate.getZ());
        BoundingBox box = template.getBoundingBox(settings, origin);
        if (box.minY() < level.getMinBuildHeight() + 3 || box.maxY() >= level.getMaxBuildHeight()) {
            return FitResult.height();
        }
        int minChunkX = box.minX() >> 4, maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4, maxChunkZ = box.maxZ() >> 4;
        int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        return FitResult.ok(new Candidate(origin, referenceSurface, box, minChunkX, maxChunkX,
                minChunkZ, maxChunkZ, chunkCount));
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
                new BlockPos(spawn.getX() + diagonal(radius), spawn.getY(), spawn.getZ() + diagonal(radius)),
                new BlockPos(spawn.getX() - diagonal(radius), spawn.getY(), spawn.getZ() + diagonal(radius)),
                new BlockPos(spawn.getX() + diagonal(radius), spawn.getY(), spawn.getZ() - diagonal(radius)),
                new BlockPos(spawn.getX() - diagonal(radius), spawn.getY(), spawn.getZ() - diagonal(radius)));
    }

    private static int diagonal(int radius) {
        return Math.max(1, Math.round((float) (radius / Math.sqrt(2.0))));
    }

    private static Rotation randomRotation(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static BlockPos localToWorld(StructureTemplate template, BlockPos origin, Rotation rotation,
                                        BlockPos localPosition) {
        BlockPos zero = template.getZeroPositionWithTransform(origin, net.minecraft.world.level.block.Mirror.NONE, rotation);
        BlockPos transformed = StructureTemplate.transform(localPosition, net.minecraft.world.level.block.Mirror.NONE,
                rotation, BlockPos.ZERO);
        return zero.offset(transformed.getX(), transformed.getY(), transformed.getZ());
    }

    public static Rotation parseRotation(String value) {
        try {
            return Rotation.valueOf(value);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("[AFL Bunker] Unknown saved rotation {}; falling back to NONE", value);
            return Rotation.NONE;
        }
    }

    private enum RejectReason { OK, WATER, SLOPE, HEIGHT }

    private record FitResult(Candidate candidate, RejectReason reason) {
        private static FitResult ok(Candidate candidate) { return new FitResult(candidate, RejectReason.OK); }
        private static FitResult water() { return new FitResult(null, RejectReason.WATER); }
        private static FitResult slope() { return new FitResult(null, RejectReason.SLOPE); }
        private static FitResult height() { return new FitResult(null, RejectReason.HEIGHT); }
    }

    private record Candidate(BlockPos origin, int surfaceY, BoundingBox box, int minChunkX, int maxChunkX,
                             int minChunkZ, int maxChunkZ, int chunkCount) {}
}
