package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.lock.WorldgenLockEvents;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World generation version lock (C2, 2026-10-14; docs/worldgen/worldgen_version_lock_v1.md): a server whose world the
 * lock refused saves nothing while it stops. Vanilla's stop after the refusal used to rewrite level.dat (LastPlayed)
 * and rotate level.dat_old; measured in the C2 test client, every other file of the world was already untouched.
 * (The singleplayer client's own rewrite of level.dat when it opens a world comes before the lock and stays.)
 */
@Mixin(MinecraftServer.class)
public abstract class WorldgenLockSaveMixin {
    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void apocalypse$noSaveWhenBlocked(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        if (WorldgenLockEvents.blocked((MinecraftServer) (Object) this)) cir.setReturnValue(false);
    }
}
