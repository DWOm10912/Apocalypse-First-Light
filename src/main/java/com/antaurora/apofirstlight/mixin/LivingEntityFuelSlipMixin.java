package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.antaurora.apofirstlight.fluid.FuelSpills;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fuel stains are slippery (2026-10-05, fluid/FuelStainIndex): the ground friction LivingEntity#travel reads under the feet
 * (its only getFriction call; both the grip and the glide follow it, as on ice) is at least the stain's: gasoline 0.85,
 * diesel 0.92 (ice 0.98, ordinary blocks 0.6). Server side from the level's FuelSpills (mobs), client side from the synced
 * copy (the local player moves itself). getFriction is Forge's (IForgeBlockState), unmapped: the @At does not remap.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFuelSlipMixin {
    @Redirect(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float apocalypse$fuelSlip(BlockState state, LevelReader level, BlockPos position, Entity entity) {
        float friction = state.getFriction(level, position, entity);
        LivingEntity self = (LivingEntity) (Object) this;
        FuelStainIndex stains = self.level() instanceof ServerLevel server ? FuelSpills.get(server).index() : FuelStainIndex.CLIENT;
        FuelStainIndex.Stain stain = stains.under(self);
        return stain == null ? friction : Math.max(friction, stain.diesel ? FuelStainIndex.DIESEL_FRICTION : FuelStainIndex.GASOLINE_FRICTION);
    }
}
