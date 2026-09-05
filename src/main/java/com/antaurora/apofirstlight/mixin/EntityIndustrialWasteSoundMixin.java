package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.fluid.IndustrialWasteSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityIndustrialWasteSoundMixin {
    @Shadow protected boolean firstTick;
    @Shadow @Final protected RandomSource random;
    @Shadow protected abstract Entity.MovementEmission getMovementEmission();
    @Shadow protected abstract SoundEvent getSwimSound();
    @Shadow protected abstract SoundEvent getSwimSplashSound();
    @Shadow protected abstract SoundEvent getSwimHighSpeedSplashSound();
    @Unique private boolean apocalypse$wasInWaste;
    @Unique private float apocalypse$nextWasteSwim = 1.0F;
    @Unique private float apocalypse$moveDistBefore;

    @Inject(method = "updateInWaterStateAndDoFluidPushing()Z", at = @At("RETURN"))
    private void apocalypse$wasteEntrySound(CallbackInfoReturnable<Boolean> callback) {
        Entity entity = (Entity) (Object) this;
        boolean inWaste = IndustrialWasteSounds.isInWaste(entity);
        if (inWaste && !apocalypse$wasInWaste && !firstTick) {
            IndustrialWasteSounds.splash(entity, random, getSwimSplashSound(), getSwimHighSpeedSplashSound());
        }
        apocalypse$wasInWaste = inWaste;
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void apocalypse$beforeWasteMovement(MoverType type, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        apocalypse$moveDistBefore = entity.moveDist;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void apocalypse$wasteSwimSound(MoverType type, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (!IndustrialWasteSounds.isInWaste(entity)) {
            apocalypse$nextWasteSwim = (int) entity.moveDist + 1.0F;
            return;
        }
        // moveDist is Vanilla's actual movement distance * 0.6 (Y only when climbable).
        // A separate sound cursor preserves Vanilla's nextStep, footsteps and game events verbatim.
        if (entity.moveDist <= apocalypse$moveDistBefore || entity.moveDist <= apocalypse$nextWasteSwim
                || entity.isPassenger() || !getMovementEmission().emitsSounds()
                || entity.level().getBlockState(entity.getOnPos()).isAir()) return;
        apocalypse$nextWasteSwim = (int) entity.moveDist + 1.0F;
        IndustrialWasteSounds.swim(entity, random, getSwimSound());
    }
}
