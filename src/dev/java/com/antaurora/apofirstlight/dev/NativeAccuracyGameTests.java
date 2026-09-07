package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
public class NativeAccuracyGameTests {
    @GameTest(template="network_empty",timeoutTicks=40)
    public static void serverDisplacementAndStop(GameTestHelper h) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"accuracy_motion"));
        p.setOnGround(true);p.setPose(net.minecraft.world.entity.Pose.CROUCHING);p.setDeltaMovement(Vec3.ZERO);
        var d=NativeGunDefinition.BR51_01;
        h.assertTrue(NativeStanceAccuracy.evaluate(p,d).stance()==NativeStanceAccuracy.Stance.CROUCH_STILL,"Initial crouch");
        h.runAfterDelay(1,()->{
            p.setPos(p.getX()+.08,p.getY(),p.getZ());
            h.assertTrue(NativeStanceAccuracy.evaluate(p,d).stance()==NativeStanceAccuracy.Stance.CROUCH_MOVING,"Server displacement despite zero velocity");
        });
        h.runAfterDelay(2,()->{
            var r=NativeStanceAccuracy.evaluate(p,d);
            h.assertTrue(r.stance()==NativeStanceAccuracy.Stance.CROUCH_STILL&&r.recoveryPenalty()>0,"Stopped but still recovering");
        });
        h.runAfterDelay(5,()->{
            var r=NativeStanceAccuracy.evaluate(p,d);
            h.assertTrue(r.recoveryPenalty()==0&&Math.abs(r.finalDegrees()-.135)<1e-10,"Fully recovered");h.succeed();
        });
    }
    @GameTest(template="network_empty")
    public static void stancePriorityAndRecovery(GameTestHelper h) {
        var c=NativeStanceAccuracy.Stance.CROUCH_STILL;
        h.assertTrue(NativeStanceAccuracy.classify(true,false,true,.001)==c,"Crouch jitter is still");
        h.assertTrue(NativeStanceAccuracy.classify(true,false,true,.02)==NativeStanceAccuracy.Stance.CROUCH_MOVING,"Slow crouch moves");
        h.assertTrue(NativeStanceAccuracy.classify(true,false,false,0)==NativeStanceAccuracy.Stance.STAND_STILL,"Stand still");
        h.assertTrue(NativeStanceAccuracy.classify(true,false,false,.1)==NativeStanceAccuracy.Stance.WALKING,"Walk");
        h.assertTrue(NativeStanceAccuracy.classify(true,true,true,0)==NativeStanceAccuracy.Stance.SPRINTING,"Sprint priority");
        h.assertTrue(NativeStanceAccuracy.classify(false,true,true,0)==NativeStanceAccuracy.Stance.AIRBORNE,"Air priority");
        h.assertTrue(NativeStanceAccuracy.recovery(4.5,.45,0,3)>0,"No instant recovery");
        h.assertTrue(NativeStanceAccuracy.recovery(4.5,.45,3,3)==0,"Crouch recovered at 3 ticks");
        h.assertTrue(NativeStanceAccuracy.recovery(4.5,1,5,5)==0,"Stand recovered at 5 ticks");
        for(var d:new NativeGunDefinition[]{NativeGunDefinition.P9_01,NativeGunDefinition.BR51_01})
            for(var s:NativeStanceAccuracy.Stance.values()) {
                double a=d.spreadDegrees()*NativeStanceAccuracy.multiplier(d.accuracy(),s);
                h.assertTrue(Double.isFinite(a)&&a>0&&a<10,"Finite bounded spread");
            }
        h.succeed();
    }
    @GameTest(template="network_empty",timeoutTicks=100)
    public static void distanceDistribution(GameTestHelper h) {
        // Direction statistics against a 0.6x1.8 target plane, not graphical/AI hit testing.
        for(var d:new NativeGunDefinition[]{NativeGunDefinition.P9_01,NativeGunDefinition.BR51_01})
            for(int distance:new int[]{20,40,60}) {
                int[] hits=new int[6];
                for(var stance:NativeStanceAccuracy.Stance.values()) {
                    var random=RandomSource.create(5101);double sumX=0,sumY=0;
                    double angle=d.spreadDegrees()*NativeStanceAccuracy.multiplier(d.accuracy(),stance);
                    for(int i=0;i<10000;i++) {
                        Vec3 dir=NativeGunShot.spread(new Vec3(0,0,1),angle,random);
                        h.assertTrue(Double.isFinite(dir.x)&&Math.abs(dir.length()-1)<1e-8,"Unit finite ray");
                        double x=dir.x/dir.z*distance,y=dir.y/dir.z*distance;
                        sumX+=x;sumY+=y;
                        if(Math.abs(x)<=.3&&Math.abs(y)<=.9)hits[stance.ordinal()]++;
                    }
                    double radius=Math.tan(Math.toRadians(angle))*distance;
                    h.assertTrue(Math.abs(sumX/10000)<radius*.03&&Math.abs(sumY/10000)<radius*.03,"No sampling bias");
                }
                h.assertTrue(hits[0]>=hits[2]&&hits[2]>=hits[3]&&hits[3]>=hits[4]&&hits[4]>=hits[5],"Stance ordering");
                if(d==NativeGunDefinition.BR51_01)h.assertTrue(hits[0]>=9900,"Rifle crouch reliable");
                com.mojang.logging.LogUtils.getLogger().info("[AFL ACCURACY TEST] gun={} distance={} hits/10000 CS,CM,SS,W,S,A={}",d.id(),distance,java.util.Arrays.toString(hits));
            }
        h.succeed();
    }
}
