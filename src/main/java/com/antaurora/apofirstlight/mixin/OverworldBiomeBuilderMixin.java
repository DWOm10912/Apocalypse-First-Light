package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(net.minecraft.world.level.biome.OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {
    @ModifyArg(method = "addBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addOffCoastBiomes(Ljava/util/function/Consumer;)V"), index = 0)
    private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> apocalypse$filterOffCoast(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        return apocalypse$filtered(consumer);
    }

    @ModifyArg(method = "addBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addInlandBiomes(Ljava/util/function/Consumer;)V"), index = 0)
    private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> apocalypse$filterInland(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        return apocalypse$filtered(consumer);
    }

    @ModifyArg(method = "addBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addUndergroundBiomes(Ljava/util/function/Consumer;)V"), index = 0)
    private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> apocalypse$filterUnderground(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        return apocalypse$filtered(consumer);
    }

    private static Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> apocalypse$filtered(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        return mapping -> {
            if (!AflVanillaBiomePolicy.isDisabled(mapping.getSecond())) consumer.accept(mapping);
        };
    }
}
