package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.survivor.AflDifficulty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AFL fixed difficulty (docs/gameplay/survivor_death_succession_v0.md 2): every vanilla way to change or unlock the
 * difficulty ends here (the /difficulty command, the options button and lock of the single-player host, the dedicated
 * server's server.properties difficulty, vanilla hardcore's forced Hard) and leaves the world on Normal, locked.
 */
@Mixin(MinecraftServer.class)
public abstract class AflDifficultyLockMixin {
    @Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true)
    private void apocalypse$fixedDifficulty(Difficulty difficulty, boolean force, CallbackInfo ci) {
        ci.cancel();
        AflDifficulty.enforce((MinecraftServer) (Object) this);
    }

    @Inject(method = "setDifficultyLocked", at = @At("HEAD"), cancellable = true)
    private void apocalypse$staysLocked(boolean locked, CallbackInfo ci) {
        ci.cancel();
        AflDifficulty.enforce((MinecraftServer) (Object) this);
    }
}
