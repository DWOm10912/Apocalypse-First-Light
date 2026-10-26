package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.time.WorldTimeClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * World Time V1 (time/WorldTimeClientState): the client moves its day time on between the server's packets at the server's
 * day length, so the sun does not jump back each second. Game time is untouched.
 */
@Mixin(ClientLevel.class)
public abstract class WorldTimeClientLevelMixin {
    @ModifyArg(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;setDayTime(J)V"))
    private long afl$slowerDay(long next) {
        return WorldTimeClientState.advance(next);
    }
}
