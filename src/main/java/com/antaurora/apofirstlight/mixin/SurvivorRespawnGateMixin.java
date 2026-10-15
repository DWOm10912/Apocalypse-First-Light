package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.survivor.Survivors;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Survivor Death and Succession V0 (docs/gameplay/survivor_death_succession_v0.md 10): the server refuses the respawn
 * request of a player whose HARD run ended. Injected after the packet was moved onto the server thread (vanilla's
 * {@code ensureRunningOnSameThread}), before anything else of the command runs; the End-credits return is not touched.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SurvivorRespawnGateMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleClientCommand", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"))
    private void apocalypse$survivorRespawnGate(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN && !player.wonGame
                && Survivors.respawnBlocked(player)) {
            ci.cancel();
        }
    }
}
