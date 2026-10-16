package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Parked vehicles collide (vehicle/VehicleColliders, docs/vehicles/first_pickup_v0_design.md section 8.2): the entity
 * collision list a move works with (Entity#collide, also for stepping up) gets the vehicle boxes near the move.
 */
@Mixin(Entity.class)
public abstract class EntityVehicleCollisionMixin {
    @Unique private Vec3 apocalypse$move = Vec3.ZERO;

    @Inject(method = "collide", at = @At("HEAD"))
    private void apocalypse$keepMove(Vec3 move, CallbackInfoReturnable<Vec3> cir) {
        apocalypse$move = move;
    }

    @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> apocalypse$addVehicles(List<VoxelShape> list) {
        Entity self = (Entity) (Object) this;
        return VehicleColliders.withVehicles(self, self.getBoundingBox().expandTowards(apocalypse$move), list);
    }
}
