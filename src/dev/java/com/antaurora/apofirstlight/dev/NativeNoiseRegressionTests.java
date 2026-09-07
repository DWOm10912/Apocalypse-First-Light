package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.infected.ai.InvestigateNoiseGoal;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import com.antaurora.apofirstlight.noise.AcousticOcclusionResolver;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeNoiseRegressionTests {
    @GameTest(template="network_empty",timeoutTicks=100,batch="native_noise_matrix")
    public static void successfulShotNoiseMatrix(GameTestHelper h) {
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"noise_matrix"));
        p.setPos(h.absoluteVec(new Vec3(3.5,2,3.5)));p.getInventory().selected=0;
        var gun=new net.minecraft.world.item.ItemStack(com.antaurora.apofirstlight.registry.AflItems.P9_01.get());p.getInventory().setItem(0,gun);
        var listener=h.spawn(EntityType.ZOMBIE,new BlockPos(10,2,3));listener.setNoAi(true);
        String[] cases={"MISS","BLOCK","BODY","HEAD","ANIMAL","LAST"};
        for(int index=0;index<cases.length;index++) {final int n=index;
            h.runAfterDelay(2+index*5,()->{
                P901Actions.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                p.setXRot(-90);p.setYRot(0);net.minecraft.world.entity.LivingEntity target=null;
                if(n==1)h.setBlock(new BlockPos(3,5,3),Blocks.STONE);
                if(n>=2&&n<=4){target=n==4?h.spawn(EntityType.COW,new BlockPos(3,2,7)):h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,7));((net.minecraft.world.entity.Mob)target).setNoAi(true);
                    Vec3 aim=new Vec3(target.getX(),n==3?target.getBoundingBox().maxY-.25:target.getY()+.8,target.getZ()).subtract(p.getEyePosition());
                    p.setYRot((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z))));}
                NativeGunAmmo.set(gun,NativeGunDefinition.P9_01,n==5?1:17);InfectedHearingState.clear(listener);
                P901Actions.request(p,false,0);
                h.assertTrue(InfectedHearingState.isValid(listener),cases[n]+" emits noise");
                InfectedHearingState.clear(listener);P901Actions.request(p,false,0);
                h.assertTrue(!InfectedHearingState.isValid(listener),"Cooldown rejection silent");
                if(target!=null)target.discard();h.setBlock(new BlockPos(3,5,3),Blocks.AIR);
                ApocalypseFirstLight.LOGGER.info("[AFL NOISE MATRIX] {} PASS",cases[n]);
            });
        }
        h.runAfterDelay(34,()->{
            P901Actions.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            NativeGunAmmo.set(gun,NativeGunDefinition.P9_01,0);InfectedHearingState.clear(listener);P901Actions.request(p,false,0);
            h.assertTrue(!InfectedHearingState.isValid(listener),"Dry silent");listener.discard();h.succeed();
        });
    }
    @GameTest(template="network_empty",timeoutTicks=220,batch="native_noise_distances")
    public static void nativeNoiseFlatDistances(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(0,1,1000));
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        for(int x=(base.getX()-2)>>4;x<=(base.getX()+72)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+3)>>4;z++)level.setChunkForced(x,z,true);
        for(int x=-2;x<=72;x++)for(int z=-3;z<=3;z++) {
            level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<4;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"native_noise"));
        p.setPos(Vec3.atBottomCenterOf(base));p.setXRot(-90);p.setYRot(0);
        int[] distances={20,40,60,68};List<Zombie> listeners=new ArrayList<>();
        for(int d:distances){var z=EntityType.ZOMBIE.create(level);z.setPos(p.getX()+d,p.getY(),p.getZ());z.setYRot(-90);z.setNoAi(true);z.setPersistenceRequired();level.addFreshEntity(z);listeners.add(z);}
        h.runAfterDelay(50,()->{
            listeners.forEach(InfectedHearingState::clear);
            listeners.forEach(z->z.setNoAi(false));
            NativeGunShot.execute(p,NativeGunDefinition.P9_01);
            for(int i=0;i<listeners.size();i++) {
                var z=listeners.get(i);var a=AcousticOcclusionResolver.resolve(level,p.getEyePosition(),z.getEyePosition(),64);
                boolean heard=InfectedHearingState.isValid(z);
                var path=z.getNavigation().createPath(BlockPos.containing(p.getEyePosition()),0);
                var ground=z.getNavigation().createPath(p.blockPosition(),0);
                ApocalypseFirstLight.LOGGER.info("[AFL NOISE REGRESSION] distance={} radius=64 transmission={} heard={} eyePathReach={} groundPathReach={} source={} listener={} alive={} registered={}",distances[i],a.effectiveRadius()/64,heard,path!=null&&path.canReach(),ground!=null&&ground.canReach(),p.position(),z.position(),z.isAlive(),level.getEntity(z.getId())==z);
                h.assertTrue(heard==(distances[i]<64),"Hearing boundary "+distances[i]);
            }
        });
        h.runAfterDelay(150,()->{
            boolean passed=true;
            for(int i=0;i<listeners.size();i++){
                var z=listeners.get(i);double moved=p.getX()+distances[i]-z.getX();
                var goal=z.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(g->g instanceof InvestigateNoiseGoal).map(g->(InvestigateNoiseGoal)g).findFirst().orElseThrow();
                ApocalypseFirstLight.LOGGER.info("[AFL NOISE REGRESSION] distance={} movedToward={} attempts={} failed={} target={}",distances[i],moved,goal.diagnosticPathAttemptCount(),goal.diagnosticFailedPathCount(),z.getTarget());
                if(i<3)passed&=moved>1&&goal.diagnosticPathAttemptCount()>0;
                else passed&=!InfectedHearingState.isValid(z)&&goal.diagnosticPathAttemptCount()==0;
                z.discard();
            }
            h.assertTrue(passed,"Heard listeners must walk toward source; outside listener must not investigate");h.succeed();
        });
    }
}
