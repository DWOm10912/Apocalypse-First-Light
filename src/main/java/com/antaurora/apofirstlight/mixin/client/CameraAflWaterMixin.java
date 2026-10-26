package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.liquid.WaterQuery;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Physical water V1 package 4 (docs/gameplay/water_v1_design.md 17): a camera under AFL's water (as drawn on the client)
 * sees through water, vanilla's water fog and colour, as under a water block.
 */
@Mixin(Camera.class)
public abstract class CameraAflWaterMixin {
    @Shadow private boolean initialized;
    @Shadow private BlockGetter level;
    @Shadow private Vec3 position;

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void apocalypse$aflWater(CallbackInfoReturnable<FogType> callback) {
        if (initialized && level instanceof Level l && !Double.isNaN(WaterQuery.surfaceAt(l, position.x, position.y, position.z)))
            callback.setReturnValue(FogType.WATER);
    }
}
