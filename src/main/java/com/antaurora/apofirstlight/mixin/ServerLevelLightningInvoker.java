package com.antaurora.apofirstlight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Regional weather V1 (weather/AflWeather): a storm's strike picks its target the way vanilla's does (and lightning V2's rolling sphere). */
@Mixin(ServerLevel.class)
public interface ServerLevelLightningInvoker {
    @Invoker("findLightningTargetAround")
    BlockPos afl$findLightningTargetAround(BlockPos pos);
}
