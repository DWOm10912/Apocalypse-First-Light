package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Standing on a parked vehicle (its roof, hood, bed: vehicle/VehicleColliders) has no block underneath, which the server
 * would take for floating and kick for flying after 4 seconds where flight is off (dedicated servers).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerVehicleMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void apocalypse$onVehicle(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (VehicleColliders.standsOn(entity)) cir.setReturnValue(false);
    }
}
