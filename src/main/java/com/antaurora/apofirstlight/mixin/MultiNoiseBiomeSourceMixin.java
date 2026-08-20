package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

/**
 * Final guard at the point where the overworld parameter list becomes the
 * runtime MultiNoiseBiomeSource.  The builder hook remains useful for normal
 * vanilla construction; this hook prevents a preset/codec path from
 * reintroducing the disabled entries into the actual candidate list.
 */
@Mixin(net.minecraft.world.level.biome.MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
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
}
