package com.antaurora.apofirstlight.world.bunker;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.biome.StartupBiomeEligibility;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
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
    /** Moderate natural relief is accepted; only severe local cliffs are rejected. */
    private static final int SOFT_SURFACE_RELIEF = 8;
    private static final int MAX_SURFACE_RELIEF = 12;
    private static final double MAX_UNDERGROUND_CAVITY_RATIO = 0.20D;
    private static final int ENTRANCE_SURFACE_Y_OFFSET = 0;
    private static final int PLACEMENT_VERSION = 1;
    private static final long BUNKER_SALT = 0x0A1B00B5EEDL;

    private BunkerPlacementManager() {}

    /** Only reports readiness; it never loads or generates a chunk. */
    public static boolean isStartupAreaReady(ServerLevel level) {
        return readChunkReadiness(level, 0, 0).available();
    }

    public static boolean ensureGenerated(ServerLevel overworld) {
        if (overworld.dimension() != Level.OVERWORLD) return false;
        BunkerSavedData data = overworld.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (data.isGenerated()) return true;

        Optional<StructureTemplate> templateOptional = overworld.getServer().getStructureManager().get(BUNKER_ID);
        if (templateOptional.isEmpty()) {
            LOGGER.error("[AFL Bunker] Missing structure template {}", BUNKER_ID);
            return false;
        }
        StructureTemplate template = templateOptional.get();
        BlockPos spawn = StartupPlainsEnclave.referenceCenter(overworld.getSharedSpawnPos().getY());
        RandomSource random = RandomSource.create(overworld.getSeed() ^ BUNKER_SALT);
        int attempts = 0;
        PlacementDiagnostics diagnostics = new PlacementDiagnostics();
        for (int radius = SEARCH_MIN_RADIUS; radius <= SEARCH_MAX_RADIUS; radius += 16) {
            for (BlockPos candidate : candidates(spawn, radius)) {
                attempts++;
                Rotation rotation = randomRotation(random);
                FitResult result = findFit(overworld, template, candidate, rotation,
                        diagnostics);
                diagnostics.record(result);
                if (result.reason != RejectReason.OK) continue;
                Candidate fit = result.candidate;
                BunkerPlacementHygiene.Stats hygiene = preparePlacement(overworld, fit);
                if (!place(overworld, template, fit, rotation)) {
                    diagnostics.placementFailures++;
                    continue;
                }
                BunkerSurfaceIntegration.IntegrationStats integration = BunkerSurfaceIntegration.apply(
                        overworld, template, fit.origin, rotation, overworld.getSeed());
                data.markGenerated(fit.origin, rotation.name(), fit.surfaceY, PLACEMENT_VERSION);
                boolean startupEnclave = isInsideStartupEnclave(fit);
                LOGGER.info("[AFL Bunker] Generated bunker at {}, {}, {}, rotation={}, surfaceY={}, startupBiome={}, startupEligible=true, startupEnclave={}, rejectedStartupBiome={}, tier={}, attempts={}, chunks={}, undergroundSamples={}, cavityRatio={}, trees={}, supportFill={}, logsCleared={}, leavesCleared={}, otherVegetationCleared={}, burialBlocks={}",
                        fit.origin.getX(), fit.origin.getY(), fit.origin.getZ(), rotation, fit.surfaceY,
                        fit.surfaceBiome, startupEnclave, diagnostics.startupBiomeRejected, "STARTUP", attempts, fit.chunkCount,
                        result.undergroundSamples, result.cavityRatio, integration.conflictingTrees(), integration.supportFilled(),
                        integration.logsCleared(), integration.leavesCleared(),
                        integration.otherVegetationCleared(), integration.burialPlaced());
                logStartupEnclaveMiss(fit, startupEnclave);
                LOGGER.info("[AFL Bunker] Placement hygiene: vegetationCleared={}, entitiesFound={}, entitiesMoved={}, entitiesMoveFailed={}",
                        hygiene.vegetationCleared(), hygiene.livingEntitiesFound(), hygiene.livingEntitiesMoved(), hygiene.livingEntitiesMoveFailed());
                LOGGER.info("[AFL BUNKER NATURAL TERRAIN] attempts={} validSurfaceCandidates={} unavailableSurfaceCandidates={} aquaticRejected={} hardSlopeRejected={} softSlopeAccepted={} heightRejected={} selectedCandidate=({}, {}) selectedSurfaceY={} localRelief={} entranceCutBlocks=0 entranceFillBlocks={} placed=true",
                        attempts, diagnostics.validSurfaceCandidates, diagnostics.unavailableSurfaceCandidates,
                        diagnostics.aquaticRejected, diagnostics.hardSlopeRejected, diagnostics.softSlopeAccepted,
                        diagnostics.heightRejected, fit.entranceSurface.getX(), fit.entranceSurface.getZ(),
                        fit.surfaceY, fit.localRelief, integration.supportFilled());
                return true;
            }
        }
        LOGGER.error("[AFL Bunker] Failed to find/place startup bunker in reliable startup area after {} attempts; aquaticRejected={}, unavailableSurfaceCandidates={}, undergroundFluid={}, undergroundCavity={}, entranceSupport={}, startupBiomeRejected={}, hardSlopeRejected={}, height={}, placementFailures={}",
                attempts, diagnostics.aquaticRejected, diagnostics.unavailableSurfaceCandidates,
                diagnostics.undergroundFluid, diagnostics.undergroundCavity, diagnostics.entranceSupport,
                diagnostics.startupBiomeRejected, diagnostics.hardSlopeRejected, diagnostics.heightRejected,
                diagnostics.placementFailures);
        LOGGER.error("[AFL Bunker] Startup enclave exhausted: center=({}, {}), coreRadius={}, attempts={}, startupBiomeRejected={}",
                StartupPlainsEnclave.CENTER_X, StartupPlainsEnclave.CENTER_Z,
                StartupPlainsEnclave.CORE_RADIUS_BLOCKS, attempts, diagnostics.startupBiomeRejected);
        LOGGER.error("[AFL BUNKER NATURAL TERRAIN] attempts={} validSurfaceCandidates={} unavailableSurfaceCandidates={} aquaticRejected={} hardSlopeRejected={} softSlopeAccepted={} heightRejected={} selectedCandidate=NONE selectedSurfaceY=NONE localRelief=NONE entranceCutBlocks=0 entranceFillBlocks=0 placed=false",
                attempts, diagnostics.validSurfaceCandidates, diagnostics.unavailableSurfaceCandidates,
                diagnostics.aquaticRejected, diagnostics.hardSlopeRejected, diagnostics.softSlopeAccepted,
                diagnostics.heightRejected);
        diagnostics.logSurfaceSamples();
        return false;
    }

    private static FitResult findFit(ServerLevel level, StructureTemplate template, BlockPos candidate, Rotation rotation,
                                     PlacementDiagnostics diagnostics) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(net.minecraft.world.level.block.Mirror.NONE);
        BoundingBox atZero = template.getBoundingBox(settings, BlockPos.ZERO);
        int minX = atZero.minX() + candidate.getX();
        int maxX = atZero.maxX() + candidate.getX();
        int minZ = atZero.minZ() + candidate.getZ();
        int maxZ = atZero.maxZ() + candidate.getZ();
        if (!isFootprintReady(level, minX, maxX, minZ, maxZ, candidate, rotation, diagnostics)) {
            return FitResult.unavailableSurface();
        }
        int[] samplesX = {minX, (minX + maxX) / 2, maxX};
        int[] samplesZ = {minZ, (minZ + maxZ) / 2, maxZ};
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int waterSamples = 0;
        for (int x : samplesX) for (int z : samplesZ) {
            SurfaceColumn column = readSurfaceColumn(level, x, z);
            if (!column.available()) {
                diagnostics.recordSurfaceSample(candidate, rotation, x, z, column);
                return FitResult.unavailableSurface();
            }
            int y = column.surfaceY();
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
        int localRelief = maxSurface - minSurface;
        diagnostics.validSurfaceCandidates++;
        if (localRelief > MAX_SURFACE_RELIEF) return FitResult.slope();
        if (localRelief > SOFT_SURFACE_RELIEF) diagnostics.softSlopeAccepted++;
        BlockPos entranceAtZero = localToWorld(template, BlockPos.ZERO, rotation, ENTRANCE_SURFACE_LOCAL);
        int entranceX = candidate.getX() + entranceAtZero.getX();
        int entranceZ = candidate.getZ() + entranceAtZero.getZ();
        SurfaceColumn entranceColumn = readSurfaceColumn(level, entranceX, entranceZ);
        if (!entranceColumn.available()) {
            diagnostics.recordSurfaceSample(candidate, rotation, entranceX, entranceZ, entranceColumn);
            return FitResult.unavailableSurface();
        }
        int referenceSurface = entranceColumn.surfaceY();
        BlockPos entranceSurface = new BlockPos(candidate.getX() + entranceAtZero.getX(),
                referenceSurface - 1, candidate.getZ() + entranceAtZero.getZ());
        var surfaceBiome = level.getBiome(entranceSurface);
        if (!StartupBiomeEligibility.isStartupEligible(surfaceBiome)) {
            return FitResult.startupBiomeIneligible();
        }
        if (isIceOrWaterSurface(level, entranceSurface)
                || hasFluidBelowSurface(level, entranceSurface, 4)) return FitResult.water();
        int originY = referenceSurface + ENTRANCE_SURFACE_Y_OFFSET - ENTRANCE_SURFACE_LOCAL.getY();
        BlockPos origin = new BlockPos(candidate.getX(), originY, candidate.getZ());
        BunkerSurfaceIntegration.SupportCheck support = BunkerSurfaceIntegration.checkEntranceSupport(
                level, template, origin, rotation);
        if (!support.accepted()) return FitResult.entranceSupport();
        BoundingBox box = template.getBoundingBox(settings, origin);
        if (box.minY() < level.getMinBuildHeight() + 3 || box.maxY() >= level.getMaxBuildHeight()) {
            return FitResult.height();
        }
        FitResult underground = checkUnderground(level, box);
        if (underground.reason != RejectReason.OK) return underground;
        int minChunkX = box.minX() >> 4, maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4, maxChunkZ = box.maxZ() >> 4;
        int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        ResourceLocation surfaceBiomeId = surfaceBiome.unwrapKey().map(key -> key.location())
                .orElse(new ResourceLocation("minecraft", "unknown"));
        return FitResult.ok(new Candidate(origin, entranceSurface, referenceSurface, box, minChunkX, maxChunkX,
                minChunkZ, maxChunkZ, chunkCount, surfaceBiomeId, localRelief), underground.undergroundSamples, underground.cavityRatio);
    }

    private static boolean isFootprintReady(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                             BlockPos candidate, Rotation rotation, PlacementDiagnostics diagnostics) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                SurfaceColumn column = readChunkReadiness(level, chunkX, chunkZ);
                if (!column.available()) {
                    diagnostics.recordSurfaceSample(candidate, rotation, chunkX << 4, chunkZ << 4, column);
                    return false;
                }
            }
        }
        return true;
    }

    private static SurfaceColumn readSurfaceColumn(ServerLevel level, int x, int z) {
        SurfaceColumn readiness = readChunkReadiness(level, x >> 4, z >> 4);
        if (!readiness.available()) return readiness;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new SurfaceColumn(surfaceY, surfaceY > level.getMinBuildHeight(), true,
                readiness.chunkStatus(), readiness.heightmapPrimed());
    }

    private static SurfaceColumn readChunkReadiness(ServerLevel level, int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return SurfaceColumn.unavailable(false, "UNLOADED", false, level.getMinBuildHeight());
        }
        String status = String.valueOf(chunk.getHighestGeneratedStatus());
        boolean heightmapPrimed = chunk.hasPrimedHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
        boolean full = chunk.getHighestGeneratedStatus().isOrAfter(ChunkStatus.FULL);
        return new SurfaceColumn(level.getMinBuildHeight(), full && heightmapPrimed, true, status, heightmapPrimed);
    }

    private static boolean isInsideStartupEnclave(Candidate fit) {
        return fit.surfaceBiome.equals(Biomes.PLAINS.location())
                && StartupPlainsEnclave.containsBlock(fit.entranceSurface.getX(), fit.entranceSurface.getZ());
    }

    private static void logStartupEnclaveMiss(Candidate fit, boolean startupEnclave) {
        if (!startupEnclave) {
            LOGGER.warn("[AFL Bunker] STARTUP_ENCLAVE_MISS entrance=({}, {}) startupBiome={}",
                    fit.entranceSurface.getX(), fit.entranceSurface.getZ(), fit.surfaceBiome);
        }
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

    private enum RejectReason { OK, UNAVAILABLE_SURFACE, WATER, SLOPE, HEIGHT, UNDERGROUND_FLUID, MAJOR_CAVITY, ENTRANCE_SUPPORT, STARTUP_BIOME_INELIGIBLE }

    private record FitResult(Candidate candidate, RejectReason reason, int undergroundSamples, double cavityRatio) {
        private static FitResult ok(Candidate candidate) { return new FitResult(candidate, RejectReason.OK, 0, 0.0D); }
        private static FitResult ok(Candidate candidate, int samples, double ratio) {
            return new FitResult(candidate, RejectReason.OK, samples, ratio);
        }
        private static FitResult unavailableSurface() { return new FitResult(null, RejectReason.UNAVAILABLE_SURFACE, 0, 0.0D); }
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
        private static FitResult startupBiomeIneligible() { return new FitResult(null, RejectReason.STARTUP_BIOME_INELIGIBLE, 0, 0.0D); }
        private static FitResult undergroundOk(int samples, double ratio) {
            return new FitResult(null, RejectReason.OK, samples, ratio);
        }
    }

    private record Candidate(BlockPos origin, BlockPos entranceSurface, int surfaceY, BoundingBox box, int minChunkX, int maxChunkX,
                             int minChunkZ, int maxChunkZ, int chunkCount, ResourceLocation surfaceBiome,
                             int localRelief) {}

    private record SurfaceColumn(int surfaceY, boolean available, boolean chunkLoaded, String chunkStatus,
                                 boolean heightmapPrimed) {
        private static SurfaceColumn unavailable(boolean chunkLoaded, String status, boolean heightmapPrimed, int minBuildHeight) {
            return new SurfaceColumn(minBuildHeight, false, chunkLoaded, status, heightmapPrimed);
        }
    }

    private static final class PlacementDiagnostics {
        private int validSurfaceCandidates;
        private int unavailableSurfaceCandidates;
        private int aquaticRejected;
        private int hardSlopeRejected;
        private int softSlopeAccepted;
        private int heightRejected;
        private int undergroundFluid;
        private int undergroundCavity;
        private int entranceSupport;
        private int startupBiomeRejected;
        private int placementFailures;
        private final List<String> surfaceSamples = new java.util.ArrayList<>();

        private void record(FitResult result) {
            switch (result.reason) {
                case UNAVAILABLE_SURFACE -> unavailableSurfaceCandidates++;
                case WATER -> aquaticRejected++;
                case SLOPE -> hardSlopeRejected++;
                case HEIGHT -> heightRejected++;
                case UNDERGROUND_FLUID -> undergroundFluid++;
                case MAJOR_CAVITY -> undergroundCavity++;
                case ENTRANCE_SUPPORT -> entranceSupport++;
                case STARTUP_BIOME_INELIGIBLE -> startupBiomeRejected++;
                case OK -> { }
            }
        }

        private void recordSurfaceSample(BlockPos candidate, Rotation rotation, int x, int z, SurfaceColumn column) {
            if (surfaceSamples.size() >= 6) return;
            surfaceSamples.add("candidate=(" + candidate.getX() + "," + candidate.getZ() + ")"
                    + " rotation=" + rotation + " sample=(" + x + "," + z + ")"
                    + " chunkLoaded=" + column.chunkLoaded() + " chunkStatus=" + column.chunkStatus()
                    + " heightmapType=MOTION_BLOCKING_NO_LEAVES surfaceY=" + column.surfaceY()
                    + " surfaceAvailable=" + column.available() + " rejectReason=UNAVAILABLE_SURFACE");
        }

        private void logSurfaceSamples() {
            for (String sample : surfaceSamples) LOGGER.error("[AFL BUNKER SURFACE SAMPLE] {}", sample);
        }
    }
}
