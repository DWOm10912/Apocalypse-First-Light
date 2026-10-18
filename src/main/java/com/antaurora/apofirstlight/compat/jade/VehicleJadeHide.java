package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;

/**
 * No Jade for vehicles (user 2026-10-16: it broke the scene; docs/vehicles/first_pickup_v0_design.md 8.2): looking at a
 * T15 (the cab or one of its picking parts) shows no tooltip at all, not its fuel nor the block behind it; riding one
 * shows none either (the rider looks out through the glass at the road; the dash HUD has the readings).
 */
public final class VehicleJadeHide {
    private VehicleJadeHide() {}

    public static void register(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (accessor == null) return null;
            if (accessor.getPlayer() != null && accessor.getPlayer().getRootVehicle() instanceof PickupTruckEntity) return null;
            if (accessor instanceof EntityAccessor entity
                    && (entity.getEntity() instanceof PickupTruckEntity || entity.getEntity() instanceof PickupTruckEntity.Part)) return null;
            return accessor;
        });
    }
}
