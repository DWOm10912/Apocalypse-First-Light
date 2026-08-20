package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import com.antaurora.apofirstlight.world.bunker.BunkerPlacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class RadiationManager {
    private static final double SAFE_THRESHOLD = 0.08;
    private static final double HEAVY_THRESHOLD = 0.62;
    private static final double SCORCHED_THRESHOLD = 0.84;
    private static final double FULL_SAFE_RADIUS = 40.0;
    private static final double FALLOFF_RADIUS = 96.0;
    public static final BlockPos BUNKER_RADIATION_SAFE_LOCAL = new BlockPos(16, 1, 9);
    private static final ResourceLocation BUNKER_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "bunker");
    private static final java.util.Map<ServerLevel, RadiationField> FIELDS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private RadiationManager() {}

    public static RadiationSample getRadiationSample(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return RadiationSample.safe(0, 0);
        }
        RadiationWorldData data = RadiationWorldData.get(level);
        long chunkX = pos.getX() >> 4;
        long chunkZ = pos.getZ() >> 4;
        double distance = distanceFromAnchor(pos, data);
        boolean core = distance <= FULL_SAFE_RADIUS;
        double base = field(level).sample(pos.getX(), pos.getZ());
        double suppression = distance <= FULL_SAFE_RADIUS ? 0.0
                : smoothstep(Math.min(1.0, (distance - FULL_SAFE_RADIUS) / (FALLOFF_RADIUS - FULL_SAFE_RADIUS)));
        double effectiveField = base * suppression;
        double ambient = rateFor(effectiveField);
        double local = getLocalRadiation(level, pos);
        RadiationZone zone = zoneFor(effectiveField);
        if (core && local <= 0.0) {
            ambient = 0.0;
            zone = RadiationZone.SAFE;
        }
        return new RadiationSample(base, zone, ambient, local, ambient + local, core, suppression,
                data.safeAnchorX(), data.safeAnchorZ(), data.anchorSource());
    }

    public static double getAmbientRadiation(ServerLevel level, BlockPos pos) {
        return getRadiationSample(level, pos).worldAmbientRadiation();
    }

    public static double getLocalRadiation(ServerLevel level, BlockPos pos) { return 0.0; }
    public static double getFinalRadiation(ServerLevel level, BlockPos pos) { return getRadiationSample(level, pos).finalRadiation(); }
    public static RadiationZone getRadiationZone(ServerLevel level, BlockPos pos) { return getRadiationSample(level, pos).zone(); }

    public static boolean isNaturalSafe(ServerLevel level, BlockPos pos) {
        return isNaturalZone(level, pos, RadiationZone.SAFE);
    }

    public static boolean isNaturalZone(ServerLevel level, BlockPos pos, RadiationZone target) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return false;
        }
        RadiationSample sample = getRadiationSample(level, pos);
        return zoneFor(sample.baseField()) == target
                && !sample.spawnSafeCore()
                && sample.spawnSuppression() >= 1.0;
    }

    public static void setSpawnSafeChunk(ServerLevel level, long chunkX, long chunkZ) {
        // Kept for compatibility with existing callers; new bunker integration does not use it.
        RadiationWorldData data = RadiationWorldData.get(level);
        data.setBunkerAnchor(new BlockPos((int) (chunkX * 16L + 8L), 0, (int) (chunkZ * 16L + 8L)));
    }

    public static boolean ensureBunkerAnchor(ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return false;
        BunkerSavedData bunker = level.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (!bunker.isGenerated()) return false;
        Optional<StructureTemplate> template = level.getServer().getStructureManager().get(BUNKER_ID);
        if (template.isEmpty()) return false;
        BlockPos anchor = BunkerPlacementManager.localToWorld(template.get(), bunker.getOrigin(),
                BunkerPlacementManager.parseRotation(bunker.getRotation()), BUNKER_RADIATION_SAFE_LOCAL);
        RadiationWorldData.get(level).setBunkerAnchor(anchor);
        return true;
    }

    public static BlockPos safeAnchor(ServerLevel level) {
        RadiationWorldData data = RadiationWorldData.get(level);
        return new BlockPos(data.safeAnchorX(), 0, data.safeAnchorZ());
    }

    private static RadiationField field(ServerLevel level) {
        return FIELDS.computeIfAbsent(level, ignored -> new RadiationField(level.getSeed()));
    }

    private static double distanceFromAnchor(BlockPos pos, RadiationWorldData data) {
        double dx = pos.getX() - data.safeAnchorX();
        double dz = pos.getZ() - data.safeAnchorZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static RadiationZone zoneFor(double field) {
        if (field < SAFE_THRESHOLD) return RadiationZone.SAFE;
        if (field < HEAVY_THRESHOLD) return RadiationZone.IRRADIATED;
        if (field < SCORCHED_THRESHOLD) return RadiationZone.HEAVY_FALLOUT;
        return RadiationZone.SCORCHED;
    }

    private static double rateFor(double field) {
        if (field < SAFE_THRESHOLD) return 0.0;
        if (field < HEAVY_THRESHOLD) return lerp(0.10, 1.50, (field - SAFE_THRESHOLD) / (HEAVY_THRESHOLD - SAFE_THRESHOLD));
        if (field < SCORCHED_THRESHOLD) return lerp(1.50, 6.00, (field - HEAVY_THRESHOLD) / (SCORCHED_THRESHOLD - HEAVY_THRESHOLD));
        return lerp(6.00, 20.00, (field - SCORCHED_THRESHOLD) / (1.00 - SCORCHED_THRESHOLD));
    }

    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
