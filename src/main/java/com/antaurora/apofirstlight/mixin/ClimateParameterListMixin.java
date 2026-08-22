package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.debug.BiomeTraceContext;
import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrablender.api.RegionType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(value = Climate.ParameterList.class, priority = 900)
public abstract class ClimateParameterListMixin {
    private static final int APOCALYPSE_LOG_LIMIT_PER_TARGET = 1;
    private static final int APOCALYPSE_REASON_LOG_LIMIT = 2;
    private static final ConcurrentHashMap<String, AtomicInteger> APOCALYPSE_LOG_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> APOCALYPSE_REASON_COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong SURFACE_UNDERGROUND_BIOME_REJECTS = new AtomicLong();
    private static final AtomicLong SURFACE_LUSH_CAVES_REJECTS = new AtomicLong();
    private static final AtomicLong SURFACE_DRIPSTONE_CAVES_REJECTS = new AtomicLong();
    private static final AtomicLong SURFACE_DEEP_DARK_REJECTS = new AtomicLong();
    private static final AtomicLong SURFACE_UNDERGROUND_BIOME_REPLACED_BY_AFL_BIOME = new AtomicLong();
    private static final AtomicLong SURFACE_UNDERGROUND_BIOME_FALLBACK_TO_PLAINS = new AtomicLong();
    @Unique
    private volatile Holder<Biome> apocalypse$plainsHolder;
    @Unique
    private volatile Holder<Biome> apocalypse$woodlandHolder;

    /**
     * TerraBlender resolves Region biome keys into registry-backed holders while
     * building its generation-time R-trees.  Capture the same holders here once;
     * worker threads then only read these immutable references.
     */
    @Dynamic("Added by TerraBlender's MixinParameterList")
    @Inject(method = "initializeForTerraBlender", at = @At("RETURN"), remap = false)
    private void apocalypse$captureGenerationHolders(RegistryAccess registryAccess, RegionType regionType,
                                                       long seed, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
        apocalypse$plainsHolder = biomeRegistry.getHolder(Biomes.PLAINS).orElse(null);
        apocalypse$woodlandHolder = biomeRegistry.getHolder(AflBiomes.IRRADIATED_WOODLAND).orElse(null);
    }

    /**
     * TerraBlender adds this method to ParameterList and uses its result in the
     * cancellable HEAD path of MultiNoiseBiomeSource#getNoiseBiome.
     */
    @Dynamic("Added by TerraBlender's MixinParameterList")
    @Inject(method = "findValuePositional",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void apocalypse$applyStartupPlainsEnclave(Climate.TargetPoint target, int quartX, int quartY, int quartZ,
                                                       CallbackInfoReturnable<Object> callback) {
        BiomeTraceContext.Context context = BiomeTraceContext.CURRENT.get();
        boolean diagnostic = apocalypse$isDiagnosticQuart(quartX, quartZ);
        String originalBiome = apocalypse$returnBiome(callback);
        if (diagnostic) {
            apocalypse$logReason("CALL_ENTER", quartX, quartY, quartZ, context,
                    originalBiome, false, false);
        }
        try {
            if (context == null) {
                if (diagnostic) apocalypse$logReason("CONTEXT_MISSING", quartX, quartY, quartZ,
                        context, originalBiome, false, false);
                return;
            }
            if (context.seed() == null) {
                if (diagnostic) apocalypse$logReason("SEED_CONTEXT_MISSING", quartX, quartY, quartZ,
                        context, originalBiome, true, false);
                return;
            }
            if (!(callback.getReturnValue() instanceof Holder<?> rawHolder)) {
                if (diagnostic) apocalypse$logReason("RETURN_NOT_HOLDER", quartX, quartY, quartZ,
                        context, originalBiome, true, true);
                return;
            }
            @SuppressWarnings("unchecked")
            Holder<Biome> original = (Holder<Biome>) rawHolder;
            ResourceKey<Biome> originalKey = original.unwrapKey().orElse(null);
            boolean undergroundBiome = AflVanillaBiomePolicy.isAllowedUndergroundBiome(originalKey);
            boolean surfaceContext = StartupPlainsEnclave.isSurfaceQuartY(quartY);
            if (undergroundBiome && !surfaceContext) {
                if (diagnostic) apocalypse$logReason("UNDERGROUND_BYPASS", quartX, quartY, quartZ,
                        context, BiomeTraceContext.biomeId(original), true, true);
                return;
            }
            boolean surfaceUndergroundReject = undergroundBiome && surfaceContext;
            if (surfaceUndergroundReject) {
                SURFACE_UNDERGROUND_BIOME_REJECTS.incrementAndGet();
                if (Biomes.LUSH_CAVES.equals(originalKey)) {
                    SURFACE_LUSH_CAVES_REJECTS.incrementAndGet();
                } else if (Biomes.DRIPSTONE_CAVES.equals(originalKey)) {
                    SURFACE_DRIPSTONE_CAVES_REJECTS.incrementAndGet();
                } else if (Biomes.DEEP_DARK.equals(originalKey)) {
                    SURFACE_DEEP_DARK_REJECTS.incrementAndGet();
                }
                if (diagnostic) apocalypse$logReason("SURFACE_UNDERGROUND_REJECT", quartX, quartY, quartZ,
                        context, BiomeTraceContext.biomeId(original), true, true);
            }
            StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(
                    quartX << 2, quartZ << 2, context.seed());
            if (zone == StartupPlainsEnclave.Zone.OUTSIDE && !surfaceUndergroundReject) {
                if (diagnostic) apocalypse$logReason("ZONE_OUTSIDE", quartX, quartY, quartZ,
                        context, BiomeTraceContext.biomeId(original), true, true);
                return;
            }
            ResourceKey<Biome> targetKey = zone == StartupPlainsEnclave.Zone.WOODLAND_BUFFER
                    ? AflBiomes.IRRADIATED_WOODLAND : Biomes.PLAINS;
            if (surfaceUndergroundReject) {
                if (zone == StartupPlainsEnclave.Zone.OUTSIDE) {
                    SURFACE_UNDERGROUND_BIOME_FALLBACK_TO_PLAINS.incrementAndGet();
                    targetKey = Biomes.PLAINS;
                } else if (zone == StartupPlainsEnclave.Zone.WOODLAND_BUFFER) {
                    SURFACE_UNDERGROUND_BIOME_REPLACED_BY_AFL_BIOME.incrementAndGet();
                }
            }
            Holder<Biome> targetHolder = apocalypse$resolveHolder(targetKey);
            if (targetHolder == null) {
                AtomicInteger count = APOCALYPSE_LOG_COUNTS.computeIfAbsent(
                        context.sourceIdentity() + '|' + targetKey.location(), ignored -> new AtomicInteger());
                if (count.getAndIncrement() < APOCALYPSE_LOG_LIMIT_PER_TARGET) {
                    ApocalypseFirstLight.LOGGER.error(
                            "[AFL STARTUP ENCLAVE] HOLDER_RESOLUTION_FAILED target={} thread={} sourceIdentity={} quart=({}, {}, {}) zone={} originalBiome={}",
                            targetKey.location(),
                            Thread.currentThread().getName(), context.sourceIdentity(), quartX, quartY, quartZ,
                            zone, BiomeTraceContext.biomeId(original));
                }
                if (diagnostic) apocalypse$logReason("TARGET_HOLDER_MISSING", quartX, quartY, quartZ,
                        context, BiomeTraceContext.biomeId(original), true, true);
                return;
            }
            callback.setReturnValue(targetHolder);
            if (diagnostic) apocalypse$logReason("OVERRIDE_APPLIED", quartX, quartY, quartZ,
                    context, BiomeTraceContext.biomeId(original), true, true,
                    targetKey.location().toString());
        } finally {
            if (diagnostic) apocalypse$logReason("TRACE_CLEAR", quartX, quartY, quartZ,
                    context, originalBiome, context != null, context != null && context.seed() != null);
            BiomeTraceContext.CURRENT.remove();
        }
    }

    @Unique
    private static String apocalypse$returnBiome(CallbackInfoReturnable<Object> callback) {
        Object value = callback.getReturnValue();
        if (!(value instanceof Holder<?> holder)) return "<non-holder>";
        if (!(holder.value() instanceof Biome)) return "<holder-non-biome>";
        @SuppressWarnings("unchecked")
        Holder<Biome> biome = (Holder<Biome>) holder;
        return BiomeTraceContext.biomeId(biome);
    }

    @Unique
    private static boolean apocalypse$isDiagnosticQuart(int quartX, int quartZ) {
        int[][] points = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {40, 0}, {0, 52}};
        for (int[] point : points) {
            if (Math.abs(quartX - point[0]) <= 1 && Math.abs(quartZ - point[1]) <= 1) return true;
        }
        return false;
    }

    @Unique
    private static void apocalypse$logReason(String reason, int quartX, int quartY, int quartZ,
                                              BiomeTraceContext.Context context,
                                              String originalBiome, boolean tracePresent,
                                              boolean seedPresent) {
        apocalypse$logReason(reason, quartX, quartY, quartZ, context, originalBiome,
                tracePresent, seedPresent, "<none>");
    }

    @Unique
    private static void apocalypse$logReason(String reason, int quartX, int quartY, int quartZ,
                                              BiomeTraceContext.Context context,
                                              String originalBiome, boolean tracePresent,
                                              boolean seedPresent, String targetBiome) {
        String key = reason + '|' + quartX + '|' + quartZ;
        AtomicInteger count = APOCALYPSE_REASON_COUNTS.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (count.getAndIncrement() >= APOCALYPSE_REASON_LOG_LIMIT) return;
        String zone = context == null || context.seed() == null ? "<unknown>"
                : StartupPlainsEnclave.zoneAt(quartX << 2, quartZ << 2, context.seed()).name();
        ApocalypseFirstLight.LOGGER.info(
                "[AFL STARTUP DIAG] reason={} thread={} quart=({}, {}, {}) blockApprox=({}, {}, {}) sourceIdentity={} originalBiome={} zone={} biomeTraceContext={} startupSeedContext={} targetBiome={} overrideApplied={}",
                reason, Thread.currentThread().getName(), quartX, quartY, quartZ,
                quartX << 2, quartY << 2, quartZ << 2,
                context == null ? "<none>" : context.sourceIdentity(), originalBiome, zone,
                tracePresent ? "YES" : "NO", seedPresent ? "YES" : "NO", targetBiome,
                "OVERRIDE_APPLIED".equals(reason) ? "YES" : "NO");
    }

    @Unique
    private Holder<Biome> apocalypse$resolveHolder(ResourceKey<Biome> key) {
        Holder<Biome> cached = key.equals(Biomes.PLAINS) ? apocalypse$plainsHolder : apocalypse$woodlandHolder;
        if (cached != null) return cached;
        for (Pair<Climate.ParameterPoint, ?> entry : ((Climate.ParameterList<?>) (Object) this).values()) {
            if (!(entry.getSecond() instanceof Holder<?> rawHolder)) continue;
            @SuppressWarnings("unchecked")
            Holder<Biome> holder = (Holder<Biome>) rawHolder;
            if (key.equals(holder.unwrapKey().orElse(null)) && holder.kind() == Holder.Kind.REFERENCE) {
                if (key.equals(Biomes.PLAINS)) apocalypse$plainsHolder = holder;
                else apocalypse$woodlandHolder = holder;
                return holder;
            }
        }
        return null;
    }
}
