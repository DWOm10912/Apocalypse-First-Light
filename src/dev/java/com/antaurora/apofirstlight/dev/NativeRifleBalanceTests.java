package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
public class NativeRifleBalanceTests {
    @GameTest(template="network_empty")
    public static void rifleAndPistolBalance(GameTestHelper h) {
        var r=NativeGunDefinition.BR51_01; var p=NativeGunDefinition.P9_01;
        h.assertTrue(NativeGunShot.damageAt(r,48)==18 && Math.abs(NativeGunShot.damageAt(r,88)-14.4)<1e-8
                && Math.abs(NativeGunShot.damageAt(r,128)-10.8)<1e-8,"Rifle falloff endpoints/midpoint");
        h.assertTrue(r.maxRange()==128 && r.effectiveRange()==96 && r.noiseRadius()==112 && r.gunshotTinnitus(),"Rifle acoustic/range");
        h.assertTrue(r.spreadDegrees()==.30 && r.accuracy().crouchStill()==.45 && r.fireIntervalTicks()==4
                && r.magazineCapacity()==20 && r.reloadDurationTicks()==52,"Frozen rifle data");
        h.assertTrue(r.recoil().verticalMin()==1.35 && r.recoil().verticalMax()==1.75 && r.recoil().maxVertical()==6
                && r.recoil().horizontalLeftMin()==.07 && r.recoil().horizontalRightMin()==.09
                && r.recoil().horizontalMin()==-.18 && r.recoil().horizontalMax()==.24 && r.recoil().maxHorizontal()==1.4,"Rifle recoil");
        h.assertTrue(p.baseDamage()==7 && p.falloffStart()==24 && p.maxRange()==64 && p.noiseRadius()==64
                && !p.gunshotTinnitus() && p.recoil().verticalMin()==.8 && p.recoil().verticalMax()==1.1
                && p.recoil().horizontalLeftMin()==.04 && p.recoil().horizontalRightMin()==.06,"Pistol unchanged");
        var ads=new NativeAdsProgress();
        for(int i=0;i<4;i++)ads.tick(true,4,4);
        ads.tick(false,4,4); h.assertTrue(ads.sample(1)==.75F,"Sprint smooth exit");
        for(int i=0;i<4;i++)ads.tick(false,4,4);
        h.assertTrue(ads.sample(1)==0,"Sprint full exit");
        for(int i=0;i<4;i++)ads.tick(true,4,4);
        h.assertTrue(ads.sample(1)==1,"Resume ADS"); h.succeed();
    }
}
