package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.NativeAdsProgress;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
public class NativeAdsGameTests {
    @GameTest(template="network_empty")
    public static void adsTickAndPartialRecovery(GameTestHelper h) {
        var p=new NativeAdsProgress();
        p.tick(true,4,4);
        h.assertTrue(p.sample(0)==0 && p.sample(.5F)==.125F && p.sample(1)==.25F,"Partial interpolation");
        // Render sampling never advances the state, independent of FPS.
        for(int i=0;i<200;i++)p.sample(i/200F);
        h.assertTrue(p.sample(1)==.25F,"Rendering does not advance ADS");
        for(int i=0;i<3;i++)p.tick(true,4,4);
        h.assertTrue(p.sample(1)==1,"Rifle four ticks");
        p.tick(false,4,4);h.assertTrue(p.sample(.5F)==.875F,"Smooth exit");
        p.tick(true,4,4);h.assertTrue(p.sample(1)==1,"Reversal preserves progress");
        p.reset();h.assertTrue(p.sample(0)==0 && p.sample(1)==0,"Switch/death reset both endpoints");
        for(int i=0;i<3;i++)p.tick(true,3,3);
        h.assertTrue(p.sample(1)==1,"Pistol three ticks");
        for(int i=0;i<5;i++)p.tick(false,3,3);
        h.assertTrue(p.sample(1)==0,"Exit clamps to HIP");
        h.succeed();
    }
}
