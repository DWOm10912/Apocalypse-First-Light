package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public class BulletGlassGameTests {
    @GameTestGenerator
    public static java.util.Collection<TestFunction> cases() {
        return java.util.List.of(new TestFunction("glass", "afl_glass_tests:continuation", "afl_glass_tests:empty", 100, 0L, true, BulletGlassGameTests::glassContinuation));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void load(net.minecraftforge.event.level.LevelEvent.Load event) {
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_glass_tests","empty"))
                .fillFromWorld(level,new BlockPos(0,300,0),new net.minecraft.core.Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
    }
    public static void glassContinuation(GameTestHelper h) {
        var level=h.getLevel();
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"glass_test"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.antaurora.apofirstlight.registry.AflItems.P9_01.get()));
        var origin=h.absolutePos(new BlockPos(2,4,2));
        level.getEntities((net.minecraft.world.entity.Entity)null,
                new net.minecraft.world.phys.AABB(origin,origin.offset(1,1,26)).inflate(3),
                e->!(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
        // Explicit fixture loading only; production tracer never loads chunks.
        for(int z=0;z<26;z++)for(int x=-1;x<=1;x++) {
            var pos=origin.offset(x,0,z);level.getChunkAt(pos);level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        }
        Vec3 start=Vec3.atCenterOf(origin),dir=new Vec3(0,0,1);
        p.setPos(start.x,start.y-p.getEyeHeight(),start.z);p.setYRot(0);p.setXRot(0);
        int count=0;
        for(var block:net.minecraftforge.registries.ForgeRegistries.BLOCKS) {
            if(!block.defaultBlockState().is(BulletBlockInteraction.GLASS))continue;
            count++;
            var pos=origin.offset(0,0,3);level.setBlockAndUpdate(pos,block.defaultBlockState());
            var hit=NativeGunShot.trace(p,start,dir,12);
            h.assertTrue(level.isEmptyBlock(pos)&&hit.point().distanceTo(start.add(0,0,12))<.001,"Glass variant continuation: "+block);
        }
        h.assertTrue(count==35,"35 vanilla tag entries loaded");
        for(int z=1;z<=17;z++)level.setBlockAndUpdate(origin.offset(0,0,z),Blocks.GLASS.defaultBlockState());
        var hit=NativeGunShot.trace(p,start,dir,24);
        for(int z=1;z<=16;z++)h.assertTrue(level.isEmptyBlock(origin.offset(0,0,z)),"Layer "+z);
        h.assertTrue(!level.isEmptyBlock(origin.offset(0,0,17)),"Safety cap preserves layer17");
        level.setBlockAndUpdate(origin.offset(0,0,17),Blocks.AIR.defaultBlockState());
        var glass=origin.offset(0,0,3);var wall=origin.offset(0,0,7);
        level.setBlockAndUpdate(glass,Blocks.GLASS_PANE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CrossCollisionBlock.EAST,true)
                .setValue(net.minecraft.world.level.block.CrossCollisionBlock.WEST,true));
        level.setBlockAndUpdate(glass.east(),Blocks.GLASS_PANE.defaultBlockState());
        var angledStart=start.add(-.5,0,0);
        NativeGunShot.trace(p,angledStart,Vec3.atCenterOf(glass).subtract(angledStart),8);
        h.assertTrue(level.isEmptyBlock(glass),"Connected pane oblique hit breaks");
        level.setBlockAndUpdate(glass.east(),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(glass,Blocks.GLASS.defaultBlockState());level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        hit=NativeGunShot.trace(p,start,dir,12);
        h.assertTrue(level.isEmptyBlock(glass)&&Math.abs(hit.point().z-wall.getZ())<.001,"Glass then solid wall");
        level.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(glass,Blocks.GLASS.defaultBlockState());
        hit=NativeGunShot.trace(p,start,dir,2);
        h.assertTrue(!level.isEmptyBlock(glass)&&hit.point().distanceTo(start.add(0,0,2))<.001,"Range not extended");
        var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        zombie.setPos(start.x,start.y-.8,start.z+6);zombie.setNoAi(true);zombie.setBaby(false);level.addFreshEntity(zombie);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        for(var d:new NativeGunDefinition[]{NativeGunDefinition.P9_01,NativeGunDefinition.BR51_01}) {
            zombie.setHealth(20);zombie.invulnerableTime=0;
            level.setBlockAndUpdate(glass,Blocks.GLASS_PANE.defaultBlockState());
            // A connected window spans the sampled spread; an isolated pane post can
            // legitimately be missed even though the wider zombie behind is hit.
            level.setBlockAndUpdate(glass.east(),Blocks.GLASS_PANE.defaultBlockState());
            level.setBlockAndUpdate(glass.west(),Blocks.GLASS_PANE.defaultBlockState());
            level.setBlockAndUpdate(glass.south(),Blocks.GLASS.defaultBlockState());
            hit=NativeGunShot.execute(p,d);
            h.assertTrue(hit.entity()==zombie&&level.isEmptyBlock(glass)&&level.isEmptyBlock(glass.south())&&zombie.getHealth()<20,"Same shot two layers + damage "+d.id()+" target="+hit.entity()+" point="+hit.point()+" glass="+level.getBlockState(glass)+" hp="+zombie.getHealth());
            h.assertTrue(Math.abs(20-zombie.getHealth()-NativeGunShot.damageAt(d,start.distanceTo(hit.point())))<.001,"No extra glass damage loss");
            level.setBlockAndUpdate(glass.east(),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(glass.west(),Blocks.AIR.defaultBlockState());
        }
        zombie.setPos(start.x,start.y-.8,start.z+1);
        level.setBlockAndUpdate(glass,Blocks.GLASS.defaultBlockState());
        hit=NativeGunShot.trace(p,start,dir,12);
        h.assertTrue(hit.entity()==zombie&&!level.isEmptyBlock(glass),"Entity before glass stops");zombie.discard();
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> cancel=e->{if(e.getPos().equals(glass))e.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.event.level.BlockEvent.BreakEvent.class,cancel);
        try {
            hit=NativeGunShot.trace(p,start,dir,12);
            h.assertTrue(!level.isEmptyBlock(glass)&&Math.abs(hit.point().z-glass.getZ())<.001,"Canceled break stops shot");
        } finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);}
        p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        NativeGunShot.trace(p,start,dir,12);
        h.assertTrue(!level.isEmptyBlock(glass),"Adventure cannot break glass");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        level.setBlockAndUpdate(glass,Blocks.GLASS_PANE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CrossCollisionBlock.WATERLOGGED,true));
        hit=NativeGunShot.trace(p,start,dir,12);
        h.assertTrue(level.getBlockState(glass).is(Blocks.WATER)&&hit.point().distanceTo(start.add(0,0,12))<.001,"Waterlogged pane leaves water and continues");
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(origin,origin.offset(1,1,24))).isEmpty(),"No glass drops");
        var high=new BlockPos(origin.getX(),300,origin.getZ());
        for(int x=1;x<=1024;x++)if(!level.hasChunkAt(high.offset(x,0,0))) {
            var unloaded=high.offset(x,0,0);
            hit=NativeGunShot.trace(p,Vec3.atCenterOf(high),new Vec3(1,0,0),1024);
            h.assertTrue(hit.point().x<=unloaded.getX()+.001&&!level.hasChunkAt(unloaded),"Stops without loading chunk");
            break;
        }
        h.succeed();
    }
}
