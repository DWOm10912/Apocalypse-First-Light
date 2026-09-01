package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import com.antaurora.apofirstlight.world.bunker.BunkerPlacementManager;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class RadiationManager {
    private static final double SAFE_THRESHOLD = 0.08;
    private static final double HEAVY_THRESHOLD = 0.62;
    private static final double EXTREME_THRESHOLD = 0.84;
    private static final double FULL_SAFE_RADIUS = 40.0;
    private static final double FALLOFF_RADIUS = 96.0;
    public static final int STARTUP_RADIATION_HANDOFF_WIDTH = 48;
    public static final double STARTUP_WOODLAND_MIN = 0.10D;
    public static final double STARTUP_WOODLAND_MAX = 0.42D;
    public static final BlockPos BUNKER_RADIATION_SAFE_LOCAL = new BlockPos(16, 1, 9);
    private static final ResourceLocation BUNKER_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "bunker");
    private static final java.util.Map<ServerLevel, RadiationField> FIELDS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<ServerLevel, java.util.Map<BlockPos, CachedShielding>> SHIELDING_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private RadiationManager() {}

    public static RadiationSample getRadiationSample(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return RadiationSample.safe(0, 0);
        }
        RadiationWorldData data = RadiationWorldData.get(level);
        double distance = distanceFromAnchor(pos, data);
        boolean core = distance <= FULL_SAFE_RADIUS;
        EnvironmentalField environmental = computeEnvironmentalField(level, pos.getX(), pos.getZ(), data);
        double rawWorldField = environmental.rawWorldField();
        double base = environmental.biomeConstrainedField();
        double suppression = environmental.safeAnchorSuppression();
        double effectiveField = effectiveEnvironmentalField(level, pos.getX(), pos.getZ(), environmental);
        double ambient = rateFor(effectiveField);
        RadiationShielding.Sample shielding = shielding(level, pos);
        double shieldedAmbient = ambient * shielding.transmission();
        double local = getLocalRadiation(level, pos);
        RadiationZone zone = zoneFor(effectiveField);
        if (core && local <= 0.0) {
            shieldedAmbient = 0.0;
            zone = RadiationZone.SAFE;
        }
        return new RadiationSample(rawWorldField, base, zone, shieldedAmbient, local, shieldedAmbient + local,
                shielding.transmission(), shielding.shieldingRaysHit(), shielding.shieldingBlocksCounted(), core, suppression,
                data.safeAnchorX(), data.safeAnchorZ(), data.anchorSource());
    }

    public static double getAmbientRadiation(ServerLevel level, BlockPos pos) {
        return getRadiationSample(level, pos).worldAmbientRadiation();
    }

    /**
     * Ambient world exposure used when newly generated ItemStacks acquire contamination.
     * Includes biome/startup/safe-anchor constraints, but excludes building shielding and local sources.
     */
    public static double getAmbientRadiationForContamination(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return 0.0D;
        EnvironmentalField environmental = computeEnvironmentalField(level, pos.getX(), pos.getZ(),
                RadiationWorldData.get(level));
        return rateFor(effectiveEnvironmentalField(level, pos.getX(), pos.getZ(), environmental));
    }

    /** Natural, unsuppressed field for chunk-independent ecology/search consumers; not player radiation. */
    public static double getNaturalBaseField(ServerLevel level, int x, int z) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return 0.0;
        return field(level).sample(x, z);
    }

    public static double getNaturalRawField(ServerLevel level, int x, int z) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return 0.0;
        return field(level).sample(x, z);
    }

    public static RadiationZone getNaturalZone(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return RadiationZone.SAFE;
        return zoneFor(getNaturalBaseField(level, pos.getX(), pos.getZ()));
    }

    public static double getLocalRadiation(ServerLevel level, BlockPos pos) { return 0.0; }

    /** Player-specific exposure. The world sample remains position-only; carried items are added exactly once here. */
    public static PlayerRadiation getPlayerRadiation(ServerPlayer player) {
        RadiationSample worldSample = getRadiationSample(player.serverLevel(), player.blockPosition());
        double carriedItemRadiation = ItemContamination.getPlayerCarriedSourceRate(player);
        return new PlayerRadiation(worldSample, carriedItemRadiation,
                worldSample.finalRadiation() + carriedItemRadiation);
    }

    /** Shielded ambient component only; player final radiation is {@link #getFinalRadiation}. */
    public static double getFinalRadiation(ServerLevel level, BlockPos pos) { return getRadiationSample(level, pos).finalRadiation(); }
    public static RadiationZone getRadiationZone(ServerLevel level, BlockPos pos) { return getRadiationSample(level, pos).zone(); }

    /** Read-only, chunk-independent startup radiation diagnostic. */
    public static StartupRadiationDebug startupRadiationDebug(ServerLevel level, int x, int z) {
        RadiationWorldData data = RadiationWorldData.get(level);
        EnvironmentalField environmental = computeEnvironmentalField(level, x, z, data);
        double cap = startupRadiationCap(level.getSeed(), x, z, environmental.preStartupEffectiveField());
        double finalField = Double.isNaN(cap) ? environmental.preStartupEffectiveField()
                : Math.min(environmental.preStartupEffectiveField(), cap);
        StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(x, z, level.getSeed());
        return new StartupRadiationDebug(x, z, Math.sqrt((double) x * x + (double) z * z), zone,
                StartupPlainsEnclave.plainsBoundary(x, z, level.getSeed()),
                StartupPlainsEnclave.woodlandOuterBoundary(x, z, level.getSeed()), environmental.rawWorldField(),
                environmental.biomeResolution().biomeId(), environmental.biomeResolution().profile(),
                environmental.biomeConstrainedField(), environmental.safeAnchorDistance(),
                environmental.safeAnchorSuppression(), environmental.preStartupEffectiveField(),
                Double.isNaN(cap) ? null : cap, finalField, zoneFor(finalField));
    }

    public static boolean isNaturalSafe(ServerLevel level, BlockPos pos) {
        return isNaturalZone(level, pos, RadiationZone.SAFE);
    }

    public static boolean isNaturalZone(ServerLevel level, BlockPos pos, RadiationZone target) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return false;
        }
        RadiationWorldData data = RadiationWorldData.get(level);
        double dx = pos.getX() - data.safeAnchorX();
        double dz = pos.getZ() - data.safeAnchorZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance >= FALLOFF_RADIUS && zoneFor(getNaturalBaseField(level, pos.getX(), pos.getZ())) == target;
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

    private static RadiationShielding.Sample shielding(ServerLevel level, BlockPos pos) {
        long tick = level.getGameTime();
        java.util.Map<BlockPos, CachedShielding> cache = SHIELDING_CACHE.computeIfAbsent(level,
                ignored -> new java.util.HashMap<>());
        CachedShielding cached = cache.get(pos);
        if (cached != null && tick - cached.tick < 10) return cached.sample;
        RadiationShielding.Sample sample = RadiationShielding.sample(level, pos);
        cache.put(pos.immutable(), new CachedShielding(tick, sample));
        return sample;
    }

    private record CachedShielding(long tick, RadiationShielding.Sample sample) {}

    private static double distanceFromAnchor(BlockPos pos, RadiationWorldData data) {
        double dx = pos.getX() - data.safeAnchorX();
        double dz = pos.getZ() - data.safeAnchorZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static RadiationZone zoneFor(double field) {
        if (field < SAFE_THRESHOLD) return RadiationZone.SAFE;
        if (field < HEAVY_THRESHOLD) return RadiationZone.IRRADIATED;
        if (field < EXTREME_THRESHOLD) return RadiationZone.HEAVY_FALLOUT;
        return RadiationZone.EXTREME;
    }

    private static double rateFor(double field) {
        double clampedField = clamp01(field);
        if (clampedField < SAFE_THRESHOLD) return 0.0;
        if (clampedField < HEAVY_THRESHOLD) {
            return lerp(1.0, 10.0,
                    (clampedField - SAFE_THRESHOLD) / (HEAVY_THRESHOLD - SAFE_THRESHOLD));
        }
        if (clampedField < EXTREME_THRESHOLD) {
            return lerp(10.0, 60.0,
                    (clampedField - HEAVY_THRESHOLD) / (EXTREME_THRESHOLD - HEAVY_THRESHOLD));
        }
        return lerp(60.0, 240.0,
                (clampedField - EXTREME_THRESHOLD) / (1.0 - EXTREME_THRESHOLD));
    }

    private static double startupRadiationCap(long seed, int x, int z, double originalField) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        int plainsBoundary = StartupPlainsEnclave.plainsBoundary(x, z, seed);
        int woodlandBoundary = StartupPlainsEnclave.woodlandOuterBoundary(x, z, seed);
        if (distance <= StartupPlainsEnclave.CORE_RADIUS_BLOCKS) {
            return SAFE_THRESHOLD - 0.001D;
        }
        if (distance <= plainsBoundary) {
            double t = smoothstep(clamp01((distance - StartupPlainsEnclave.CORE_RADIUS_BLOCKS)
                    / Math.max(1.0D, plainsBoundary - StartupPlainsEnclave.CORE_RADIUS_BLOCKS)));
            return lerp(0.0D, SAFE_THRESHOLD - 0.001D, t);
        }
        if (distance <= woodlandBoundary) {
            double t = smoothstep(clamp01((distance - plainsBoundary)
                    / Math.max(1.0D, woodlandBoundary - plainsBoundary)));
            return lerp(STARTUP_WOODLAND_MIN, STARTUP_WOODLAND_MAX, t);
        }
        double handoff = woodlandBoundary + STARTUP_RADIATION_HANDOFF_WIDTH;
        if (distance <= handoff) {
            double t = smoothstep((distance - woodlandBoundary) / STARTUP_RADIATION_HANDOFF_WIDTH);
            return lerp(STARTUP_WOODLAND_MAX, originalField, t);
        }
        return Double.NaN;
    }

    private static double effectiveEnvironmentalField(ServerLevel level, int x, int z,
                                                      EnvironmentalField environmental) {
        double effectiveField = environmental.preStartupEffectiveField();
        double startupCap = startupRadiationCap(level.getSeed(), x, z, effectiveField);
        return Double.isNaN(startupCap) ? effectiveField : Math.min(effectiveField, startupCap);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static EnvironmentalField computeEnvironmentalField(ServerLevel level, int x, int z,
                                                                 RadiationWorldData data) {
        double raw = field(level).sample(x, z);
        BiomeRadiationResolver.Resolution resolution = BiomeRadiationResolver.resolve(level, x, z);
        double constrained = resolution.profile().constrain(raw);
        double anchorDistance = Math.sqrt((double) (x - data.safeAnchorX()) * (x - data.safeAnchorX())
                + (double) (z - data.safeAnchorZ()) * (z - data.safeAnchorZ()));
        double suppression = anchorDistance <= FULL_SAFE_RADIUS ? 0.0
                : smoothstep(Math.min(1.0D, (anchorDistance - FULL_SAFE_RADIUS)
                / (FALLOFF_RADIUS - FULL_SAFE_RADIUS)));
        return new EnvironmentalField(raw, resolution, constrained, anchorDistance, suppression,
                constrained * suppression);
    }

    private record EnvironmentalField(double rawWorldField, BiomeRadiationResolver.Resolution biomeResolution,
                                      double biomeConstrainedField, double safeAnchorDistance,
                                      double safeAnchorSuppression, double preStartupEffectiveField) {
    }

    public record PlayerRadiation(RadiationSample worldSample, double carriedItemRadiation,
                                  double effectiveRadiation) {
    }

    public record StartupRadiationDebug(int x, int z, double distanceFromStartupCenter, StartupPlainsEnclave.Zone startupZone,
                                        int plainsBoundary, int woodlandBoundary, double rawWorldField,
                                        ResourceLocation biomeId, BiomeRadiationProfile biomeProfile,
                                        double biomeConstrainedField, double safeAnchorDistance,
                                        double safeAnchorSuppression, double preStartupEffectiveField,
                                        Double startupCap, double finalEffectiveField, RadiationZone finalZone) {
    }
}
