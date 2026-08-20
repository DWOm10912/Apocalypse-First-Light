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
    /** Local foot-position outside the west-facing entrance door. */
    public static final BlockPos ENTRANCE_SURFACE_LOCAL = new BlockPos(30, 5, 8);
    private static final Logger LOGGER = ApocalypseFirstLight.LOGGER;
    private static final ResourceLocation BUNKER_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "bunker");
    private static final int SEARCH_MIN_RADIUS = 32;
    private static final int SEARCH_MAX_RADIUS = 160;
    private static final int FALLBACK_MAX_RADIUS = 256;
    private static final int EMERGENCY_MIN_RADIUS = 288;
    private static final int EMERGENCY_MAX_RADIUS = 768;
    private static final int PRIMARY_MAX_SURFACE_DELTA = 4;
    private static final int FALLBACK_MAX_SURFACE_DELTA = 7;
    private static final int EMERGENCY_MAX_SURFACE_DELTA = 9;
    private static final double MAX_UNDERGROUND_CAVITY_RATIO = 0.20D;
    private static final int ENTRANCE_SURFACE_Y_OFFSET = 0;
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
        int rejectedUndergroundFluid = 0;
        int rejectedCavity = 0;
        int rejectedEntranceSupport = 0;
        int placementFailures = 0;
        int emergencyAttempts = 0;
        HeightRejectDiagnostics heightDiagnostics = new HeightRejectDiagnostics();
        for (int radius = SEARCH_MIN_RADIUS; radius <= FALLBACK_MAX_RADIUS; radius += 16) {
            for (BlockPos candidate : candidates(spawn, radius)) {
                attempts++;
                Rotation rotation = randomRotation(random);
                FitResult result = findFit(overworld, template, candidate, rotation,
                        radius <= SEARCH_MAX_RADIUS ? PRIMARY_MAX_SURFACE_DELTA : FALLBACK_MAX_SURFACE_DELTA,
                        heightDiagnostics);
                switch (result.reason) {
                    case WATER -> { rejectedWater++; continue; }
                    case SLOPE -> { rejectedSlope++; continue; }
                    case HEIGHT -> { rejectedHeight++; continue; }
                    case UNDERGROUND_FLUID -> { rejectedUndergroundFluid++; continue; }
                    case MAJOR_CAVITY -> { rejectedCavity++; continue; }
                    case ENTRANCE_SUPPORT -> { rejectedEntranceSupport++; continue; }
                    case OK -> { }
                }
                Candidate fit = result.candidate;
                BunkerPlacementHygiene.Stats hygiene = preparePlacement(overworld, fit);
                if (!place(overworld, template, fit, rotation)) {
                    placementFailures++;
                    continue;
                }
                BunkerSurfaceIntegration.IntegrationStats integration = BunkerSurfaceIntegration.apply(
                        overworld, template, fit.origin, rotation, overworld.getSeed());
                data.markGenerated(fit.origin, rotation.name(), fit.surfaceY, PLACEMENT_VERSION);
                LOGGER.info("[AFL Bunker] Generated bunker at {}, {}, {}, rotation={}, surfaceY={}, tier={}, attempts={}, chunks={}, undergroundSamples={}, cavityRatio={}, trees={}, supportFill={}, logsCleared={}, leavesCleared={}, otherVegetationCleared={}, burialBlocks={}",
                        fit.origin.getX(), fit.origin.getY(), fit.origin.getZ(), rotation, fit.surfaceY,
                        radius <= SEARCH_MAX_RADIUS ? "PRIMARY" : "FALLBACK", attempts, fit.chunkCount,
                        result.undergroundSamples, result.cavityRatio, integration.conflictingTrees(), integration.supportFilled(),
                        integration.logsCleared(), integration.leavesCleared(),
                        integration.otherVegetationCleared(), integration.burialPlaced());
                LOGGER.info("[AFL Bunker] Placement hygiene: vegetationCleared={}, entitiesFound={}, entitiesMoved={}, entitiesMoveFailed={}",
                        hygiene.vegetationCleared(), hygiene.livingEntitiesFound(), hygiene.livingEntitiesMoved(), hygiene.livingEntitiesMoveFailed());
                return;
            }
        }
        for (int radius = EMERGENCY_MIN_RADIUS; radius <= EMERGENCY_MAX_RADIUS; radius += 32) {
            for (BlockPos candidate : candidates(spawn, radius)) {
                attempts++;
                emergencyAttempts++;
                Rotation rotation = randomRotation(random);
                FitResult result = findFit(overworld, template, candidate, rotation,
                        EMERGENCY_MAX_SURFACE_DELTA, heightDiagnostics);
                switch (result.reason) {
                    case WATER -> { rejectedWater++; continue; }
                    case SLOPE -> { rejectedSlope++; continue; }
                    case HEIGHT -> { rejectedHeight++; continue; }
                    case UNDERGROUND_FLUID -> { rejectedUndergroundFluid++; continue; }
                    case MAJOR_CAVITY -> { rejectedCavity++; continue; }
                    case ENTRANCE_SUPPORT -> { rejectedEntranceSupport++; continue; }
                    case OK -> { }
                }
                Candidate fit = result.candidate;
                BunkerPlacementHygiene.Stats hygiene = preparePlacement(overworld, fit);
                if (!place(overworld, template, fit, rotation)) {
                    placementFailures++;
                    continue;
                }
                BunkerSurfaceIntegration.IntegrationStats integration = BunkerSurfaceIntegration.apply(
                        overworld, template, fit.origin, rotation, overworld.getSeed());
                data.markGenerated(fit.origin, rotation.name(), fit.surfaceY, PLACEMENT_VERSION);
                LOGGER.info("[AFL Bunker] Generated bunker at {}, {}, {}, rotation={}, surfaceY={}, tier=EMERGENCY, attempts={}, emergencyAttempts={}, radius={}, chunks={}, undergroundSamples={}, cavityRatio={}, trees={}, supportFill={}, logsCleared={}, leavesCleared={}, otherVegetationCleared={}, burialBlocks={}",
                        fit.origin.getX(), fit.origin.getY(), fit.origin.getZ(), rotation, fit.surfaceY,
                        attempts, emergencyAttempts, radius, fit.chunkCount, result.undergroundSamples,
                        result.cavityRatio, integration.conflictingTrees(), integration.supportFilled(),
                        integration.logsCleared(), integration.leavesCleared(),
                        integration.otherVegetationCleared(), integration.burialPlaced());
                LOGGER.info("[AFL Bunker] Placement hygiene: vegetationCleared={}, entitiesFound={}, entitiesMoved={}, entitiesMoveFailed={}",
                        hygiene.vegetationCleared(), hygiene.livingEntitiesFound(), hygiene.livingEntitiesMoved(), hygiene.livingEntitiesMoveFailed());
                return;
            }
        }
        LOGGER.error("[AFL Bunker] Failed to find/place startup bunker after {} attempts; surfaceWater={}, undergroundFluid={}, undergroundCavity={}, entranceSupport={}, slope={}, height={}, placementFailures={}",
                attempts, rejectedWater, rejectedUndergroundFluid, rejectedCavity, rejectedEntranceSupport,
                rejectedSlope, rejectedHeight, placementFailures);
        LOGGER.error("[AFL Bunker] Emergency search exhausted: emergencyAttempts={}, radiusRange={}..{}, heightDiagnostics={}",
                emergencyAttempts, EMERGENCY_MIN_RADIUS, EMERGENCY_MAX_RADIUS, heightDiagnostics.summary(overworld));
        heightDiagnostics.logSamples();
    }

    private static FitResult findFit(ServerLevel level, StructureTemplate template, BlockPos candidate, Rotation rotation,
                                     int allowedSlopeDelta, HeightRejectDiagnostics heightDiagnostics) {
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
        BlockPos entranceAtZero = localToWorld(template, BlockPos.ZERO, rotation, ENTRANCE_SURFACE_LOCAL);
        int referenceSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                candidate.getX() + entranceAtZero.getX(), candidate.getZ() + entranceAtZero.getZ());
        BlockPos entranceSurface = new BlockPos(candidate.getX() + entranceAtZero.getX(),
                referenceSurface - 1, candidate.getZ() + entranceAtZero.getZ());
        if (isIceOrWaterSurface(level, entranceSurface)
                || hasFluidBelowSurface(level, entranceSurface, 4)) return FitResult.water();
        int originY = referenceSurface + ENTRANCE_SURFACE_Y_OFFSET - ENTRANCE_SURFACE_LOCAL.getY();
        BlockPos origin = new BlockPos(candidate.getX(), originY, candidate.getZ());
        BunkerSurfaceIntegration.SupportCheck support = BunkerSurfaceIntegration.checkEntranceSupport(
                level, template, origin, rotation);
        if (!support.accepted()) return FitResult.entranceSupport();
        BoundingBox box = template.getBoundingBox(settings, origin);
        if (box.minY() < level.getMinBuildHeight() + 3 || box.maxY() >= level.getMaxBuildHeight()) {
            heightDiagnostics.record(candidate, rotation, referenceSurface, originY, box);
            return FitResult.height();
        }
        FitResult underground = checkUnderground(level, box);
        if (underground.reason != RejectReason.OK) return underground;
        int minChunkX = box.minX() >> 4, maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4, maxChunkZ = box.maxZ() >> 4;
        int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        return FitResult.ok(new Candidate(origin, referenceSurface, box, minChunkX, maxChunkX,
                minChunkZ, maxChunkZ, chunkCount), underground.undergroundSamples, underground.cavityRatio);
    }

    private static boolean hasFluidBelowSurface(ServerLevel level, BlockPos surface, int depth) {
        for (int offset = 0; offset < depth; offset++) {
            if (!level.getFluidState(surface.below(offset)).isEmpty()) return true;
        }
        return false;
    }

    private static FitResult checkUnderground(ServerLevel level, BoundingBox box) {
        int undergroundSamples = 0;
        int undergroundAirSamples = 0;
        int undergroundFluidSamples = 0;
        for (int xi = 0; xi < 5; xi++) {
            int x = sampleCoordinate(box.minX(), box.maxX(), xi, 5);
            for (int zi = 0; zi < 5; zi++) {
                int z = sampleCoordinate(box.minZ(), box.maxZ(), zi, 5);
                int terrainSurfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int yi = 0; yi < 5; yi++) {
                    int y = sampleCoordinate(box.minY(), box.maxY(), yi, 5);
                    if (y >= terrainSurfaceY - 2) continue;
                    BlockPos sample = new BlockPos(x, y, z);
                    undergroundSamples++;
                    if (!level.getFluidState(sample).isEmpty()) undergroundFluidSamples++;
                    if (level.getBlockState(sample).isAir()) undergroundAirSamples++;
                }
            }
        }
        if (undergroundFluidSamples > 0) {
            return FitResult.undergroundFluid(undergroundSamples, ratio(undergroundAirSamples, undergroundSamples));
        }
        double cavityRatio = ratio(undergroundAirSamples, undergroundSamples);
        if (cavityRatio > MAX_UNDERGROUND_CAVITY_RATIO) {
            return FitResult.majorCavity(undergroundSamples, cavityRatio);
        }
        return FitResult.undergroundOk(undergroundSamples, cavityRatio);
    }

    private static int sampleCoordinate(int min, int max, int index, int count) {
        if (count <= 1 || min == max) return min;
        return min + (int) Math.round((max - min) * (index / (double) (count - 1)));
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0D : numerator / (double) denominator;
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

    private static BunkerPlacementHygiene.Stats preparePlacement(ServerLevel level, Candidate fit) {
        return BunkerPlacementHygiene.prepareForPlacement(level, fit.box, fit.surfaceY);
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
        // origin is the exact first BlockPos passed to StructureTemplate#placeInWorld.
        // Vanilla calculates each block as origin + calculateRelativePosition(settings, localPos).
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(net.minecraft.world.level.block.Mirror.NONE)
                .setRotation(rotation);
        BlockPos transformed = StructureTemplate.transform(localPosition, settings.getMirror(),
                settings.getRotation(), settings.getRotationPivot());
        return origin.offset(transformed.getX(), transformed.getY(), transformed.getZ());
    }

    public static Rotation parseRotation(String value) {
        try {
            return Rotation.valueOf(value);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("[AFL Bunker] Unknown saved rotation {}; falling back to NONE", value);
            return Rotation.NONE;
        }
    }

    private enum RejectReason { OK, WATER, SLOPE, HEIGHT, UNDERGROUND_FLUID, MAJOR_CAVITY, ENTRANCE_SUPPORT }

    private record FitResult(Candidate candidate, RejectReason reason, int undergroundSamples, double cavityRatio) {
        private static FitResult ok(Candidate candidate) { return new FitResult(candidate, RejectReason.OK, 0, 0.0D); }
        private static FitResult ok(Candidate candidate, int samples, double ratio) {
            return new FitResult(candidate, RejectReason.OK, samples, ratio);
        }
        private static FitResult water() { return new FitResult(null, RejectReason.WATER, 0, 0.0D); }
        private static FitResult slope() { return new FitResult(null, RejectReason.SLOPE, 0, 0.0D); }
        private static FitResult height() { return new FitResult(null, RejectReason.HEIGHT, 0, 0.0D); }
        private static FitResult undergroundFluid(int samples, double ratio) {
            return new FitResult(null, RejectReason.UNDERGROUND_FLUID, samples, ratio);
        }
        private static FitResult majorCavity(int samples, double ratio) {
            return new FitResult(null, RejectReason.MAJOR_CAVITY, samples, ratio);
        }
        private static FitResult entranceSupport() { return new FitResult(null, RejectReason.ENTRANCE_SUPPORT, 0, 0.0D); }
        private static FitResult undergroundOk(int samples, double ratio) {
            return new FitResult(null, RejectReason.OK, samples, ratio);
        }
    }

    private record Candidate(BlockPos origin, int surfaceY, BoundingBox box, int minChunkX, int maxChunkX,
                             int minChunkZ, int maxChunkZ, int chunkCount) {}

    private static final class HeightRejectDiagnostics {
        private int count;
        private int minOriginY = Integer.MAX_VALUE, maxOriginY = Integer.MIN_VALUE;
        private int minBoxMinY = Integer.MAX_VALUE, maxBoxMinY = Integer.MIN_VALUE;
        private int minBoxMaxY = Integer.MAX_VALUE, maxBoxMaxY = Integer.MIN_VALUE;
        private int minReferenceY = Integer.MAX_VALUE, maxReferenceY = Integer.MIN_VALUE;
        private final List<String> samples = new java.util.ArrayList<>();

        private void record(BlockPos candidate, Rotation rotation, int referenceY, int originY, BoundingBox box) {
            count++;
            minOriginY = Math.min(minOriginY, originY); maxOriginY = Math.max(maxOriginY, originY);
            minBoxMinY = Math.min(minBoxMinY, box.minY()); maxBoxMinY = Math.max(maxBoxMinY, box.minY());
            minBoxMaxY = Math.min(minBoxMaxY, box.maxY()); maxBoxMaxY = Math.max(maxBoxMaxY, box.maxY());
            minReferenceY = Math.min(minReferenceY, referenceY); maxReferenceY = Math.max(maxReferenceY, referenceY);
            if (samples.size() < 5) samples.add("candidate=" + candidate.getX() + "," + candidate.getZ()
                    + ", rotation=" + rotation + ", referenceSurfaceY=" + referenceY + ", originY=" + originY
                    + ", boxY=[" + box.minY() + "," + box.maxY() + "]");
        }

        private String summary(ServerLevel level) {
            return "count=" + count + ", originYRange=[" + minOriginY + "," + maxOriginY
                    + "], boxMinYRange=[" + minBoxMinY + "," + maxBoxMinY + "], boxMaxYRange=["
                    + minBoxMaxY + "," + maxBoxMaxY + "], referenceSurfaceYRange=[" + minReferenceY + ","
                    + maxReferenceY + "], worldY=[" + level.getMinBuildHeight() + "," + level.getMaxBuildHeight() + ")";
        }

        private void logSamples() {
            for (String sample : samples) LOGGER.error("[AFL Bunker] HEIGHT reject sample: {}", sample);
        }
    }
}
