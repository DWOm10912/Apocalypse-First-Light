package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeHeadshotGameTests {
    @GameTest(template="network_empty")
    public static void headGeometryAndAllowList(GameTestHelper h) {
        AABB b=new AABB(-.3,0,-.3,.3,1.95,.3);
        h.assertTrue(NativeHeadshots.intersect(b,true,new Vec3(0,1.7,-3),new Vec3(0,1.7,3)).orElseThrow().head(),"Central head");
        h.assertTrue(!NativeHeadshots.intersect(b,true,new Vec3(0,1,-3),new Vec3(0,1,3)).orElseThrow().head(),"Torso body");
        h.assertTrue(!NativeHeadshots.intersect(b,true,new Vec3(0,0,-3),new Vec3(0,2.8,3)).orElseThrow().head(),"Body before head remains body");
        h.assertTrue(NativeHeadshots.intersect(b,true,new Vec3(.28,1.7,-3),new Vec3(.28,1.7,3)).isEmpty(),"Outside narrow head is empty space");
        h.assertTrue(!NativeHeadshots.intersect(b,false,new Vec3(0,1.7,-3),new Vec3(0,1.7,3)).orElseThrow().head(),"Unlisted full AABB is body");
        var z=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,2));
        h.assertTrue(NativeHeadshots.enabled(z),"Zombie enabled");
        for(var type:new EntityType<?>[]{EntityType.COW,EntityType.PIG,EntityType.SHEEP,EntityType.HUSK,EntityType.DROWNED,EntityType.ZOMBIE_VILLAGER}) {
            var e=type.create(h.getLevel());h.assertTrue(!NativeHeadshots.enabled(e),"Exact allowlist excludes "+type);
        }
        h.assertTrue(Math.abs(NativeGunShot.damageAt(NativeGunDefinition.SERVICE_PISTOL,64)*NativeHeadshots.MULTIPLIER.get()-6.825)<1e-8,"Falloff before multiplier");
        h.succeed();
    }
    @GameTest(template="network_empty")
    public static void realServerDamage(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"headshot_check"));
        var z=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,7));z.setNoAi(true);z.setBaby(false);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        var box=z.getBoundingBox();
        p.setPos(z.getX(),box.maxY-.25-p.getEyeHeight(),z.getZ()-3);p.setYRot(0);p.setXRot(0);
        float before=z.getHealth();var hit=NativeGunShot.execute(p,NativeGunDefinition.SERVICE_PISTOL);
        h.assertTrue(hit.entity()==z&&hit.head(),"Real spread ray classifies zombie head");
        h.assertTrue(Math.abs(before-z.getHealth()-10.5)<.001,"Near head damage10.5");
        z.setHealth(20);p.setPos(z.getX(),box.minY+.8-p.getEyeHeight(),z.getZ()-3);
        hit=NativeGunShot.execute(p,NativeGunDefinition.SERVICE_PISTOL);
        h.assertTrue(hit.entity()==z&&!hit.head()&&Math.abs(z.getHealth()-13)<.001,"Body damage7");
        z.setInvulnerable(true);before=z.getHealth();NativeGunShot.execute(p,NativeGunDefinition.SERVICE_PISTOL);
        h.assertTrue(z.getHealth()==before,"Invulnerability remains effective");
        z.discard();h.succeed();
    }
}
