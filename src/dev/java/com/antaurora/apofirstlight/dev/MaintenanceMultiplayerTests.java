package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import com.antaurora.apofirstlight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;

/** Dev-only, two distinct server identities; not a claim of graphical multiplayer coverage. */
public final class MaintenanceMultiplayerTests {
    public static void run(GameTestHelper h){
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(3,2,3));
        var bench=place(h,root);var another=place(h,root.offset(7,0,0));
        var a=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("8ca95e40-5d79-4000-8000-000000000001"),"AFL_Test_A"));
        var b=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("8ca95e40-5d79-4000-8000-000000000002"),"AFL_Test_B"));
        a.setPos(root.getX(),root.getY(),root.getZ());b.setPos(a.getX(),a.getY(),a.getZ());
        var am=new GunMaintenanceMenu(31,a.getInventory(),bench);var bm=new GunMaintenanceMenu(32,b.getInventory(),bench);
        a.containerMenu=am;b.containerMenu=bm;
        h.assertTrue(a!=b&&!a.getUUID().equals(b.getUUID()),"distinct players");
        log("IDENTITIES A="+a.getUUID()+" B="+b.getUUID()+" BENCH="+root);
        var gun=new ItemStack(AflItems.P9_01.get());gun.getOrCreateTag().putString("MultiplayerMarker","preserve-exact-stack");
        var definition=((com.antaurora.apofirstlight.weapon.NativeGunItem)gun.getItem()).definition();
        com.antaurora.apofirstlight.weapon.NativeGunAmmo.set(gun,definition,7);
        h.assertTrue(com.antaurora.apofirstlight.weapon.NativeGunAmmo.read(gun,definition)==7,"non default ammo fixture");
        var attachments=new net.minecraft.nbt.CompoundTag();attachments.put("SIGHT",new ItemStack(AflItems.PISTOL_RED_DOT.get()).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",attachments);
        for(int i=0;i<50;i++){
            a.getInventory().clearContent();b.getInventory().clearContent();a.getInventory().setItem(4,gun.copy());
            h.assertTrue(am.clickMenuButton(a,4),"place "+i);
            h.assertTrue(a.getInventory().getItem(4).isEmpty()&&bench.originHotbarSlot()==4&&a.getUUID().equals(bench.originPlayerUUID())&&ItemStack.matches(gun,bench.getItem(0)),"owner transfer metadata");
            h.assertTrue(am.originSlotFor(a.getUUID())==4&&!am.canShowTakeButton(a.getUUID())&&bm.originSlotFor(b.getUUID())==-1&&bm.canShowTakeButton(b.getUUID()),"two viewer UI logic");
            boolean first,second; // Both calls execute consecutively on the same server tick.
            if(i%2==0){first=am.clickMenuButton(a,9);second=bm.clickMenuButton(b,10);}
            else{first=bm.clickMenuButton(b,10);second=am.clickMenuButton(a,9);}
            h.assertTrue(first&&!second,"exactly first succeeds "+i);
            h.assertTrue(!am.clickMenuButton(a,9)&&!bm.clickMenuButton(b,10),"stale requests "+i);
            int count=count(h,root,bench,a,b);h.assertTrue(count==1,"race total "+i);
            var received=i%2==0?a.getInventory().getItem(4):b.getInventory().getItem(0);
            h.assertTrue(ItemStack.matches(gun,received)&&bench.isEmpty()&&bench.originHotbarSlot()==-1&&bench.originPlayerUUID()==null,"NBT and origin cleanup "+i);
            log("CASE=RACE_"+i+" ORDER="+(i%2==0?"A_B":"B_A")+" INITIAL_GUN_COUNT=1 FINAL_GUN_COUNT="+count+" SUCCESS="+(i%2==0?"A":"B")+" BENCH=EMPTY");
        }
        for(boolean owner:new boolean[]{true,false})for(int tier=0;tier<3;tier++){
            a.getInventory().clearContent();b.getInventory().clearContent();a.getInventory().setItem(4,gun.copy());h.assertTrue(am.clickMenuButton(a,4),"fallback setup");
            var receiver=owner?a:b;int filled=tier==0?1:tier==1?9:36;
            if(tier==0)receiver.getInventory().setItem(owner?4:0,new ItemStack(Items.STONE,64));
            else for(int s=0;s<filled;s++)receiver.getInventory().setItem(s,new ItemStack(Items.STONE,64));
            h.assertTrue((owner?am:bm).clickMenuButton(receiver,owner?9:10),"fallback action");
            int target=tier==0?(owner?0:1):9;
            if(tier<2)h.assertTrue(ItemStack.matches(gun,receiver.getInventory().getItem(target)),"fallback destination NBT");
            for(int s=0;s<(tier==0?1:filled);s++){int index=tier==0?(owner?4:0):s;h.assertTrue(receiver.getInventory().getItem(index).is(Items.STONE)&&receiver.getInventory().getItem(index).getCount()==64,"no inventory overwrite");}
            h.assertTrue(count(h,root,bench,a,b)==1,"fallback total");
            for(var e:level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(5),e->e.getItem().is(AflItems.P9_01.get()))){h.assertTrue(ItemStack.matches(gun,e.getItem()),"drop exact NBT");e.discard();}
            log("CASE=FALLBACK_"+(owner?"OWNER":"OTHER")+"_"+tier+" INITIAL_GUN_COUNT=1 FINAL_GUN_COUNT=1 BENCH=EMPTY SUCCESS="+(owner?"A":"B"));
        }
        a.getInventory().clearContent();b.getInventory().clearContent();a.getInventory().setItem(4,gun.copy());h.assertTrue(am.clickMenuButton(a,4),"persist setup");
        var saved=bench.saveWithoutMetadata();bench.clearContent();bench.load(saved);
        h.assertTrue(bench.originHotbarSlot()==4&&a.getUUID().equals(bench.originPlayerUUID())&&ItemStack.matches(gun,bench.getItem(0)),"reload identity");
        h.assertTrue(bm.clickMenuButton(b,10)&&count(h,root,bench,a,b)==1,"non owner after reload");log("CASE=BE_RELOAD INITIAL_GUN_COUNT=1 FINAL_GUN_COUNT=1 SUCCESS=B BENCH=EMPTY");
        b.getInventory().clearContent();a.getInventory().setItem(4,gun.copy());h.assertTrue(am.clickMenuButton(a,4),"invalid setup");
        h.assertTrue(!am.clickMenuButton(a,-1)&&!am.clickMenuButton(a,99)&&!bm.clickMenuButton(b,9),"invalid slot and forged owner");
        a.containerMenu=a.inventoryMenu;h.assertTrue(!am.clickMenuButton(a,9),"closed mode");a.containerMenu=am;
        a.setPos(root.getX()+100,root.getY(),root.getZ());h.assertTrue(!am.clickMenuButton(a,9),"distance");a.setPos(root.getX(),root.getY(),root.getZ());
        var wrong=new GunMaintenanceMenu(33,a.getInventory(),another);h.assertTrue(!wrong.clickMenuButton(a,10),"wrong target menu cannot act");
        var otherWorld=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var foreign=new GunMaintenanceBenchBlockEntity(root,bench.getBlockState());foreign.setLevel(otherWorld);
        var foreignMenu=new GunMaintenanceMenu(34,a.getInventory(),foreign);a.containerMenu=foreignMenu;h.assertTrue(!foreignMenu.clickMenuButton(a,10),"wrong dimension");a.containerMenu=am;
        b.setPos(another.getBlockPos().getX(),root.getY(),root.getZ());var isolated=new GunMaintenanceMenu(35,b.getInventory(),another);b.containerMenu=isolated;
        b.getInventory().setItem(2,new ItemStack(AflItems.BR51_01.get()));h.assertTrue(isolated.clickMenuButton(b,2),"second bench place");
        h.assertTrue(am.clickMenuButton(a,9)&&another.getItem(0).is(AflItems.BR51_01.get()),"first return leaves second intact");
        h.assertTrue(isolated.clickMenuButton(b,9)&&b.getInventory().getItem(2).is(AflItems.BR51_01.get()),"second independent return");log("CASE=INVALID_TARGET_AND_TWO_BENCH PASS");
        h.assertTrue(am.clickMenuButton(a,4),"break setup");level.destroyBlock(root.east().above(),true,a);
        level.destroyBlock(root,true,b);
        h.assertTrue(count(h,root,bench,a,b)==1,"break exactly one gun");
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(5),e->e.getItem().is(AflBlocks.GUN_MAINTENANCE_BENCH.get().asItem())).stream().mapToInt(e->e.getItem().getCount()).sum()==1,"bench drop once");
        log("CASE=BREAK INITIAL_GUN_COUNT=1 FINAL_GUN_COUNT=1 SUCCESS=WORLD_DROP BENCH=REMOVED");h.succeed();
    }
    private static GunMaintenanceBenchBlockEntity place(GameTestHelper h,BlockPos root){
        var l=h.getLevel();for(int x=0;x<2;x++)l.setBlock(root.offset(x,-1,0),Blocks.STONE.defaultBlockState(),3);
        for(var part:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(root,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
        return (GunMaintenanceBenchBlockEntity)l.getBlockEntity(root);
    }
    private static int count(GameTestHelper h,BlockPos root,GunMaintenanceBenchBlockEntity bench,ServerPlayer... players){
        int n=bench.getItem(0).is(AflItems.P9_01.get())?bench.getItem(0).getCount():0;
        for(var p:players)for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(AflItems.P9_01.get()))n+=p.getInventory().getItem(i).getCount();
        return n+h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(5),e->e.getItem().is(AflItems.P9_01.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    private static void log(String s){com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE MULTIPLAYER] {}",s);}
}
