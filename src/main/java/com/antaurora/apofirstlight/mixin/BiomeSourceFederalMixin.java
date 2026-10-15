package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.federal.FederalBiomeSourceAccess;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * C2 (2026-10-14, docs/worldgen/federal_six_state_c2.md): the federal experimental biomes join the possible biomes of
 * the one biome source they were given to (the overworld of a world made with the federal preset, FederalPreset), so
 * their features are sorted and decorated there. Every other source has none and returns its own set untouched: r1
 * worlds keep their possible biomes and with them their feature order.
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourceFederalMixin implements FederalBiomeSourceAccess {
    @Unique private volatile List<Holder<Biome>> apocalypse$federalBiomes;

    @Override
    public void apocalypse$setFederalBiomes(List<Holder<Biome>> biomes) {
        apocalypse$federalBiomes = List.copyOf(biomes);
    }

    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    private void apocalypse$federalPossibleBiomes(CallbackInfoReturnable<Set<Holder<Biome>>> ci) {
        List<Holder<Biome>> extra = apocalypse$federalBiomes;
        if (extra == null) return;
        Set<Holder<Biome>> base = ci.getReturnValue();
        if (base.containsAll(extra)) return;
        Set<Holder<Biome>> out = new ObjectLinkedOpenHashSet<>(base);
        out.addAll(extra);
        ci.setReturnValue(out);
    }
}
