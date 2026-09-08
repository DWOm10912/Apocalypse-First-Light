package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** One final native shot state for hearing, tinnitus and sound selection. */
public record NativeGunNoise(double radius,boolean suppressed) {
    public static NativeGunNoise resolve(ItemStack stack,NativeGunDefinition gun){
        var muzzle=NativeAttachments.active(stack,NativeAttachment.Slot.MUZZLE);
        if(!(muzzle.getItem() instanceof NativeAttachment a))return new NativeGunNoise(gun.noiseRadius(),false);
        return new NativeGunNoise(Math.max(1,Math.round(gun.noiseRadius()*a.noiseRadiusMultiplier())),a.suppressesFireSound());
    }
    public SoundEvent fireSound(NativeGunItem gun){
        var id=gun.definition().suppressedFireSound();
        var sound=suppressed&&id!=null?ForgeRegistries.SOUND_EVENTS.getValue(id):null;
        return sound!=null?sound:gun.fireSound();
    }
}
