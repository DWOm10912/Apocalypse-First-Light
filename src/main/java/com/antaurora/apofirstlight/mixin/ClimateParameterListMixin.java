package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.debug.BiomeTraceContext;
import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.worldgen.aquifer.ScorchedAquiferContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = Climate.ParameterList.class, priority = 900)
public abstract class ClimateParameterListMixin {
    private static final int APOCALYPSE_LOG_LIMIT_PER_TARGET = 1;
    /** Flat-Country surface ecology band: block Y 48..112; lower cave biomes remain untouched. */
    private static final int APOCALYPSE_SURFACE_BAND_MIN_QUART_Y = 12;
    private static final int APOCALYPSE_SURFACE_BAND_MAX_QUART_Y = 28;
    private static final ConcurrentHashMap<String, AtomicInteger> APOCALYPSE_LOG_COUNTS = new ConcurrentHashMap<>();
    @Unique
    private volatile Holder<Biome> apocalypse$plainsHolder;
    @Unique
    private volatile Holder<Biome> apocalypse$woodlandHolder;

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
        try {
            ScorchedAquiferContext.Context ecology = ScorchedAquiferContext.current();
            if (context == null || ecology == null
                    || !(callback.getReturnValue() instanceof Holder<?> rawHolder)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Holder<Biome> original = (Holder<Biome>) rawHolder;
            if (quartY < APOCALYPSE_SURFACE_BAND_MIN_QUART_Y
                    || quartY > APOCALYPSE_SURFACE_BAND_MAX_QUART_Y) {
                return;
            }
            StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(
                    quartX << 2, quartZ << 2, ecology.seed());
            if (zone == StartupPlainsEnclave.Zone.OUTSIDE) {
                return;
            }
            ResourceKey<Biome> targetKey = zone == StartupPlainsEnclave.Zone.WOODLAND_BUFFER
                    ? AflBiomes.IRRADIATED_WOODLAND : Biomes.PLAINS;
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
                return;
            }
            callback.setReturnValue(targetHolder);
        } finally {
            BiomeTraceContext.CURRENT.remove();
        }
    }

    @Unique
    private Holder<Biome> apocalypse$resolveHolder(ResourceKey<Biome> key) {
        Holder<Biome> cached = key.equals(Biomes.PLAINS) ? apocalypse$plainsHolder : apocalypse$woodlandHolder;
        if (cached != null) return cached;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> resolved = biomes.getHolder(key).orElse(null);
        if (resolved != null) {
            if (key.equals(Biomes.PLAINS)) apocalypse$plainsHolder = resolved;
            else apocalypse$woodlandHolder = resolved;
        }
        return resolved;
    }
}
