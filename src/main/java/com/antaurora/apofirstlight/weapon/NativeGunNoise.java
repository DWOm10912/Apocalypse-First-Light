package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** One final native shot state for hearing, tinnitus and sound selection. */
public record NativeGunNoise(double radius,boolean suppressed) {
    /**
     * How far a suppressed shot is heard: vanilla's 16 blocks, not its 3-6 blocks of noise (the user, 2026-10-09: "消音改回16格").
     * An unsuppressed shot is heard exactly as far as its noise (noise/RangedSound).
     */
    public static final double SUPPRESSED_SOUND_RADIUS=16;
    public double soundRadius(){return suppressed?SUPPRESSED_SOUND_RADIUS:radius;}
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
