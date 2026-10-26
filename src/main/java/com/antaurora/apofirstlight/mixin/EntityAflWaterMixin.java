package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.liquid.world.WaterContact;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Physical water V1 package 4 (docs/gameplay/water_v1_design.md 17): AFL's water as water to bodies. After Forge's fluid
 * pass over the box (vanilla blocks) WaterContact adds AFL's water, before vanilla reads the heights for being in water
 * (swimming, splash, fall, fire); after the eye check, eyes under AFL water are under water (drowning, the overlay, the
 * next tick's wasEyeInWater). Once a tick: other code calling the fluid pass again (lava) is not pushed twice.
 */
@Mixin(Entity.class)
public abstract class EntityAflWaterMixin {
    @Shadow(remap = false) protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow(remap = false) private FluidType forgeFluidTypeOnEyes;
    @Shadow public int tickCount;
    @Unique private int apocalypse$aflWaterTick = -1;

    @Inject(method = "updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V", at = @At("RETURN"), remap = false)
    private void apocalypse$aflWater(Predicate<FluidState> shouldUpdate, CallbackInfo callback) {
        if (apocalypse$aflWaterTick == tickCount) return;
        apocalypse$aflWaterTick = tickCount;
        WaterContact.touch((Entity) (Object) this, forgeFluidTypeHeight);
    }

    @Inject(method = "updateFluidOnEyes", at = @At("RETURN"))
    private void apocalypse$aflWaterEyes(CallbackInfo callback) {
        if (WaterContact.eyesUnder((Entity) (Object) this)) forgeFluidTypeOnEyes = ForgeMod.WATER_TYPE.get();
    }
}
