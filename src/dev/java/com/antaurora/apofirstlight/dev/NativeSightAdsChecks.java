package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.weapon.client.NativeAdsProfile;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class NativeSightAdsChecks {
    @SubscribeEvent public static void login(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e){
        var gun=new net.minecraft.world.item.ItemStack(AflItems.P9_01.get());
        var iron=NativeAdsProfile.forStack(gun);var root=new net.minecraft.nbt.CompoundTag();
        var rifle=NativeAdsProfile.forStack(new net.minecraft.world.item.ItemStack(AflItems.BR51_01.get()));
        if(iron.rootPitch()!=0||iron.adsPitch()!=0||iron.adsYaw()!=0||iron.adsRoll()!=0
                ||rifle.adsPitch()!=0||rifle.adsYaw()!=0||rifle.adsRoll()!=0)
            throw new IllegalStateException("Unexpected native sight-axis calibration");
        var ironPoint=new org.joml.Vector3f(iron.ax()/16,iron.ay()/16,iron.az()/16);
        iron.ads().transformPosition(ironPoint);
        if(Math.abs(ironPoint.x)>1e-5||Math.abs(ironPoint.y)>1e-5||Math.abs(ironPoint.z+iron.eyeRelief())>1e-5)
            throw new IllegalStateException("Iron ADS pivot moved: "+ironPoint);
        root.put("SIGHT",new net.minecraft.world.item.ItemStack(AflItems.PISTOL_RED_DOT.get()).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",root);
        var dot=NativeAdsProfile.forStack(gun);var m=((NativeGunItem)gun.getItem()).definition().sightMount();
        if(dot.adsPitch()!=iron.adsPitch()||dot.adsYaw()!=iron.adsYaw()||dot.adsRoll()!=iron.adsRoll())
            throw new IllegalStateException("Optic lost gun-base ADS rotation");
        var p=new org.joml.Vector3f(m.aimX()/16,m.aimY()/16,m.aimZ()/16);
        dot.correction(true).mul(dot.hip()).transformPosition(p);
        if(dot.equals(iron)||Math.abs(p.x)>1e-5||Math.abs(p.y)>1e-5||Math.abs(p.z+dot.eyeRelief())>1e-5)throw new IllegalStateException("Optic ADS alignment failed: "+p);
        gun.getTag().remove("AflAttachments");if(!iron.equals(NativeAdsProfile.forStack(gun)))throw new IllegalStateException("Iron restoration failed");
        com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[AFL SIGHT] ADS numerical PASS reticle={} iron restoration=PASS; GPU visual=PENDING",p);
    }
}
