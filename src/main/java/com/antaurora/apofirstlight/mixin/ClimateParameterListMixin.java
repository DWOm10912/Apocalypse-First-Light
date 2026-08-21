package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.debug.BiomeTraceContext;
import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
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
    private static final int APOCALYPSE_LOG_LIMIT_PER_SOURCE = 10;
    private static final ConcurrentHashMap<String, AtomicInteger> APOCALYPSE_LOG_COUNTS = new ConcurrentHashMap<>();
    @Unique
    private volatile Holder<Biome> apocalypse$plainsHolder;

    /**
     * TerraBlender adds this method to ParameterList and uses its result in the
     * cancellable HEAD path of MultiNoiseBiomeSource#getNoiseBiome.
     */
    @Dynamic("Added by TerraBlender's MixinParameterList")
    @Inject(method = "findValuePositional(Lnet/minecraft/world/level/biome/Climate$TargetPoint;III)Ljava/lang/Object;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void apocalypse$applyStartupPlainsEnclave(Climate.TargetPoint target, int quartX, int quartY, int quartZ,
                                                       CallbackInfoReturnable<Object> callback) {
        BiomeTraceContext.Context context = BiomeTraceContext.CURRENT.get();
        try {
            if (context == null || !StartupPlainsEnclave.containsQuart(quartX, quartZ)
                    || !(callback.getReturnValue() instanceof Holder<?> rawHolder)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Holder<Biome> original = (Holder<Biome>) rawHolder;
            if (!apocalypse$isSurfaceBiome(original)) {
                return;
            }
            Holder<Biome> plains = apocalypse$findPlainsHolder();
            if (plains == null) {
                AtomicInteger count = APOCALYPSE_LOG_COUNTS.computeIfAbsent(
                        context.sourceIdentity(), ignored -> new AtomicInteger());
                if (count.getAndIncrement() < APOCALYPSE_LOG_LIMIT_PER_SOURCE) {
                    ApocalypseFirstLight.LOGGER.error(
                            "[AFL STARTUP ENCLAVE] PLAINS_HOLDER_RESOLUTION_FAILED thread={} sourceIdentity={} quart=({}, {}, {}) originalBiome={}",
                            Thread.currentThread().getName(), context.sourceIdentity(), quartX, quartY, quartZ,
                            BiomeTraceContext.biomeId(original));
                }
                return;
            }
            callback.setReturnValue(plains);
            AtomicInteger count = APOCALYPSE_LOG_COUNTS.computeIfAbsent(
                    context.sourceIdentity(), ignored -> new AtomicInteger());
            if (count.getAndIncrement() < APOCALYPSE_LOG_LIMIT_PER_SOURCE) {
                ApocalypseFirstLight.LOGGER.info(
                        "[AFL STARTUP ENCLAVE V3.3] thread={} sourceIdentity={} quart=({}, {}, {}) block=({}, {}) originalBiome={} overrideBiome=minecraft:plains hook=Climate.ParameterList#findValuePositional:RETURN",
                        Thread.currentThread().getName(), context.sourceIdentity(), quartX, quartY, quartZ,
                        quartX << 2, quartZ << 2, BiomeTraceContext.biomeId(original));
            }
        } finally {
            BiomeTraceContext.CURRENT.remove();
        }
    }

    private static boolean apocalypse$isSurfaceBiome(Holder<Biome> biome) {
        return biome.is(Biomes.PLAINS)
                || biome.is(AflBiomes.IRRADIATED_WOODLAND)
                || biome.is(AflBiomes.FALLOUT_BARRENS);
    }

    @Unique
    private Holder<Biome> apocalypse$findPlainsHolder() {
        Holder<Biome> cached = apocalypse$plainsHolder;
        if (cached != null) {
            return cached;
        }
        for (Pair<Climate.ParameterPoint, ?> entry : ((Climate.ParameterList<?>) (Object) this).values()) {
            if (entry.getSecond() instanceof Holder<?> rawHolder) {
                @SuppressWarnings("unchecked")
                Holder<Biome> holder = (Holder<Biome>) rawHolder;
                if (holder.is(Biomes.PLAINS) && holder.kind() == Holder.Kind.REFERENCE) {
                    apocalypse$plainsHolder = holder;
                    return holder;
                }
            }
        }
        return null;
    }
}
