package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeGunShotGameTests {
    private static final NativeGunDefinition D = NativeGunDefinition.SERVICE_PISTOL;
    @GameTest(template="network_empty", timeoutTicks=100)
    public static void serverShotOcclusionNoiseAndDry(GameTestHelper h) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "native_shot"));
        var origin = h.absoluteVec(new Vec3(3.5, 2, 3.5));
        p.setPos(origin); p.setYRot(0); p.setXRot(0);
        var gun = new ItemStack(AflItems.SERVICE_PISTOL.get());
        p.getInventory().selected=0; p.getInventory().setItem(0,gun);
        var near=h.spawn(EntityType.IRON_GOLEM, new BlockPos(3,2,8)); near.setNoAi(true);
        var far=h.spawn(EntityType.IRON_GOLEM, new BlockPos(3,2,12)); far.setNoAi(true);
        var listener=h.spawn(EntityType.ZOMBIE,new BlockPos(10,2,3)); listener.setNoAi(true); listener.setBaby(false);
        var hit=NativeGunShot.trace(p,p.getEyePosition(),new Vec3(0,0,1),64);
        h.assertTrue(hit.entity()==near,"Nearest entity wins");
        h.setBlock(new BlockPos(3,3,6),Blocks.STONE);
        h.assertTrue(NativeGunShot.trace(p,p.getEyePosition(),new Vec3(0,0,1),64).entity()==null,"Wall blocks entity");
        h.setBlock(new BlockPos(3,3,6),Blocks.AIR);
        ServicePistolActions.request(p,false,0);
        h.assertTrue(NativeGunAmmo.read(gun,D)==16,"Exactly one ammo debited");
        h.assertTrue(Math.abs(near.getHealth()-93)<.001 && far.getHealth()==100,"Body damage7, no penetration");
        h.assertTrue(InfectedHearingState.lastHeardPosition(listener)!=null,"Native shot reaches hearing");
        for(int i=0;i<20;i++)ServicePistolActions.request(p,false,0);
        h.assertTrue(near.getHealth()==93,"Spam cannot duplicate damage");
        h.runAfterDelay(4,()->{
            ServicePistolActions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
            NativeGunAmmo.set(gun,D,0);
            // Other parallel GameTests can emit noise between ticks. Compare within this server task.
            long heard=InfectedHearingState.heardGameTime(listener);
            for(int i=0;i<20;i++)ServicePistolActions.request(p,false,0);
            h.assertTrue(near.getHealth()==93 && NativeGunAmmo.read(gun,D)==0,"Dry fire no damage/ammo");
            h.assertTrue(InfectedHearingState.heardGameTime(listener)==heard,"Dry fire no noise refresh");
            h.succeed();
        });
    }
    @GameTest(template="network_empty", timeoutTicks=100)
    public static void falloffAndSpread(GameTestHelper h) {
        h.assertTrue(NativeGunShot.damageAt(D,0)==7 && NativeGunShot.damageAt(D,24)==7,"Near damage");
        h.assertTrue(Math.abs(NativeGunShot.damageAt(D,64)-4.55)<.00001,"Far damage");
        h.assertTrue(Math.abs(NativeGunShot.damageAt(D,44)-5.775)<.00001,"Linear midpoint");
        var random=net.minecraft.util.RandomSource.create(123);
        for(var forward : new Vec3[]{new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(0,-1,0)})
            for(int i=0;i<1000;i++){
                var ray=NativeGunShot.spread(forward,D.spreadDegrees(),random);
                h.assertTrue(Math.abs(ray.length()-1)<1e-9 && ray.dot(forward)>=Math.cos(Math.toRadians(1.2))-1e-9,"Spread cone bound");
            }
        h.succeed();
    }
}
