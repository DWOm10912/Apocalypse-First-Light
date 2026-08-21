package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.debug.BiomeTraceContext;
import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Final guard at the point where the overworld parameter list becomes the
 * runtime MultiNoiseBiomeSource.  The builder hook remains useful for normal
 * vanilla construction; this hook prevents a preset/codec path from
 * reintroducing the disabled entries into the actual candidate list.
 */
@Mixin(net.minecraft.world.level.biome.MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Unique
    private boolean apocalypse$sourceCensusLogged;
    @Unique
    private final Map<Long, Integer> apocalypse$traceCounts = new HashMap<>();

    @Shadow
    private Climate.ParameterList<Holder<Biome>> parameters() {
        throw new AssertionError();
    }

    @ModifyArg(method = "createFromList", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/mojang/datafixers/util/Either;left(Ljava/lang/Object;)Lcom/mojang/datafixers/util/Either;"),
            index = 0)
    private static Object apocalypse$filterRuntimeParameters(Object parameters) {
        if (!(parameters instanceof Climate.ParameterList<?> rawList)) {
            return parameters;
        }

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> filtered = new ArrayList<>();
        for (Pair<Climate.ParameterPoint, ?> rawPair : rawList.values()) {
            if (!(rawPair.getSecond() instanceof Holder<?> rawHolder)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Holder<Biome> holder = (Holder<Biome>) rawHolder;
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            if (!AflVanillaBiomePolicy.isDisabled(key)) {
                filtered.add(Pair.of(rawPair.getFirst(), holder));
            }
        }
        return new Climate.ParameterList<>(filtered);
    }

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"))
    private void apocalypse$traceEnter(int quartX, int quartY, int quartZ, Climate.Sampler sampler,
                                       CallbackInfoReturnable<Holder<Biome>> callback) {
        BiomeTraceContext.CURRENT.set(new BiomeTraceContext.Context(
                quartX, quartY, quartZ, Integer.toHexString(System.identityHashCode(this))));
        if (apocalypse$isDiagnosticQuart(quartX, quartZ)) {
            apocalypse$logSourceCensus();
            long xz = (((long) quartX) << 32) ^ (quartZ & 0xffffffffL);
            int count = apocalypse$traceCounts.getOrDefault(xz, 0);
            if (count < 3) {
                apocalypse$traceCounts.put(xz, count + 1);
                ApocalypseFirstLight.LOGGER.info(
                        "[AFL BIOME TRACE][MultiNoiseBiomeSource#getNoiseBiome] phase=HEAD thread={} sourceIdentity={} quart=({}, {}, {}) insideEnclave={} resolved=PENDING overrideAttempted=PENDING overrideApplied=PENDING",
                        Thread.currentThread().getName(), Integer.toHexString(System.identityHashCode(this)),
                        quartX, quartY, quartZ, StartupPlainsEnclave.containsQuart(quartX, quartZ));
            }
        }
    }

    @Unique
    private boolean apocalypse$isDiagnosticQuart(int quartX, int quartZ) {
        return (quartX == 0 && quartZ == 0)
                || (quartX == 16 && quartZ == 0)
                || (quartX == -16 && quartZ == 0)
                || (quartX == 0 && quartZ == 16)
                || (quartX == 0 && quartZ == -16)
                || (quartX == 40 && quartZ == 0);
    }

    @Unique
    private void apocalypse$logSourceCensus() {
        if (apocalypse$sourceCensusLogged) {
            return;
        }
        apocalypse$sourceCensusLogged = true;
        int parameterCount = 0;
        boolean hasPlains = false;
        boolean hasWoodland = false;
        boolean hasBarrens = false;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : parameters().values()) {
            parameterCount++;
            hasPlains |= entry.getSecond().is(Biomes.PLAINS);
            hasWoodland |= entry.getSecond().is(AflBiomes.IRRADIATED_WOODLAND);
            hasBarrens |= entry.getSecond().is(AflBiomes.FALLOUT_BARRENS);
        }
        ApocalypseFirstLight.LOGGER.info(
                "[AFL BIOME TRACE][SOURCE] identity={} thread={} parameterCount={} hasPlains={} hasIrradiatedWoodland={} hasFalloutBarrens={}",
                Integer.toHexString(System.identityHashCode(this)), Thread.currentThread().getName(), parameterCount,
                hasPlains, hasWoodland, hasBarrens);
    }

}
