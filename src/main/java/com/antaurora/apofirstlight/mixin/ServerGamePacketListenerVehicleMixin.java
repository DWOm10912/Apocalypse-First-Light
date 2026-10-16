package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Standing on a parked vehicle (its roof, hood, bed: vehicle/VehicleColliders) has no block underneath, which the server
 * would take for floating and kick for flying after 4 seconds where flight is off (dedicated servers).
 * <p>Vehicle physics V0-b: the vanilla vehicle move of a driven truck is ignored; the truck's own state packet moves it
 * (vehicle/VehiclePackets). Vanilla would test the move against the truck's square entity box with block collisions and
 * snap it back on a smoothed slope or a tilted body.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerVehicleMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void apocalypse$onVehicle(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PickupTruckEntity || VehicleColliders.standsOn(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void apocalypse$truckMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        // on the server thread first, as vanilla (off it, this throws and the packet is handled again there)
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if (player.getRootVehicle() instanceof PickupTruckEntity) ci.cancel();
    }
}
