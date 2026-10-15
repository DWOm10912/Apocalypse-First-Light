package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.federal.FederalPreset;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/**
 * Federal default on a dedicated server (2026-10-14, docs/worldgen/federal_six_state_c2.md section 12): when
 * server.properties has no level-type, a new world is made with the AFL Federation preset instead of minecraft:normal,
 * and that is what the server writes into the file. Only the fallback is replaced: a level-type written in the file
 * (minecraft:normal, minecraft:flat, another mod's type) is used as it is. An existing world ignores level-type anyway;
 * its generator is in its level.dat.
 */
@Mixin(DedicatedServerProperties.class)
public abstract class DedicatedServerFederalDefaultMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/dedicated/DedicatedServerProperties;get(Ljava/lang/String;Ljava/util/function/Function;Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 2)
    private Object apocalypse$federalLevelType(String key, Function<String, ?> parse, Object fallback) {
        return "level-type".equals(key) ? FederalPreset.defaultLevelType(fallback) : fallback;
    }
}
