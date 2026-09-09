package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import com.antaurora.apofirstlight.registry.*;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;

public final class MaintenanceAttachmentTests {
    public static void rifle(net.minecraft.gametest.framework.GameTestHelper h){
        var root=h.absolutePos(new BlockPos(4,2,4));var level=h.getLevel();
        for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(root,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
        var bench=(GunMaintenanceBenchBlockEntity)level.getBlockEntity(root);
        var a=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"rifle_A"));
        var b=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"rifle_B"));
        a.setPos(root.getCenter());b.setPos(root.getCenter());
        var am=new GunMaintenanceMenu(61,a.getInventory(),bench);var bm=new GunMaintenanceMenu(62,b.getInventory(),bench);a.containerMenu=am;b.containerMenu=bm;
        var slot=NativeAttachment.Slot.MUZZLE;var accessory=AflItems.RIFLE_SUPPRESSOR_01.get();
        for(int trial=0;trial<20;trial++){
            bench.clearContent();a.getInventory().clearContent();b.getInventory().clearContent();
            a.getInventory().setItem(4,new ItemStack(AflItems.BR51_01.get()));h.assertTrue(am.clickMenuButton(a,4),"rifle bench fixture");
            a.getInventory().setItem(23,new ItemStack(accessory));b.getInventory().setItem(23,new ItemStack(accessory));
            var ar=request(a,bench,slot,23);var br=request(b,bench,slot,23);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,ar),"rifle bench install");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(b,br)&&count(b,accessory)==1,"rifle stale no consumption");
            a.getInventory().setItem(24,new ItemStack(accessory));
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,slot,24))&&count(a,accessory)==1,"rifle replace conserves");
            var sight=AflItems.RIFLE_RED_DOT_01.get();
            a.getInventory().setItem(25,new ItemStack(sight));b.getInventory().setItem(25,new ItemStack(sight));
            var staleSight=request(b,bench,NativeAttachment.Slot.SIGHT,25);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,NativeAttachment.Slot.SIGHT,25)),"rifle sight install");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(b,staleSight)&&count(b,sight)==1,"rifle sight stale safe");
            a.getInventory().setItem(26,new ItemStack(sight));
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,NativeAttachment.Slot.SIGHT,26))&&count(a,sight)==1,"rifle sight replace conserves");
            h.assertTrue(NativeAttachments.activeSight(bench.getItem(0)).is(sight)&&NativeAttachments.active(bench.getItem(0),slot).is(accessory),"rifle simultaneous sight muzzle");
            var magazine=AflItems.BR51_EXTENDED_MAGAZINE_35.get();var ms=NativeAttachment.Slot.MAGAZINE;
            var definition=((NativeGunItem)bench.getItem(0).getItem()).definition();
            h.assertTrue(NativeGunAmmo.capacity(bench.getItem(0),definition)==20,"standard 20");
            h.assertTrue(!NativeAttachments.compatible(new ItemStack(AflItems.P9_01.get()),new ItemStack(magazine))
                    &&!NativeAttachments.compatible(bench.getItem(0),new ItemStack(AflItems.P9_01_EXTENDED_MAGAZINE.get())),"magazines not cross-compatible");
            NativeGunAmmo.set(bench.getItem(0),definition,12);
            a.getInventory().setItem(27,new ItemStack(magazine));b.getInventory().setItem(27,new ItemStack(magazine));
            var staleMag=request(b,bench,ms,27);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,ms,27)),"35R install");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(b,staleMag)&&count(b,magazine)==1,"35R stale install preserves item");
            h.assertTrue(NativeGunAmmo.capacity(bench.getItem(0),definition)==35&&NativeGunAmmo.read(bench.getItem(0),definition)==12,"12/20 to 12/35 no free ammo");
            a.getInventory().setItem(28,new ItemStack(magazine));
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,ms,28))&&count(a,magazine)==1,"35R replacement returns old");
            NativeGunAmmo.set(bench.getItem(0),definition,27);
            var magSaved=bench.saveWithoutMetadata();bench.load(magSaved);
            var magRestored=ItemStack.of(bench.getItem(0).save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(NativeGunAmmo.read(magRestored,definition)==27&&NativeGunAmmo.capacity(magRestored,definition)==35
                    &&NativeAttachments.activeSight(magRestored).is(sight)&&NativeAttachments.active(magRestored,slot).is(accessory),"three slots and ammo persist");
            int ammoBefore=count(a,AflItems.ROUND_762MM.get());
            var staleRemoval=request(a,bench,ms,-1);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,staleRemoval),"35R remove");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(a,staleRemoval),"no duplicate overflow return");
            h.assertTrue(NativeGunAmmo.read(bench.getItem(0),definition)==20&&NativeGunAmmo.capacity(bench.getItem(0),definition)==20
                    &&count(a,AflItems.ROUND_762MM.get())==ammoBefore+7&&count(a,magazine)==2,"27/35 to 20/20 plus 7 real reserve, two magazines conserved");
            h.assertTrue(NativeAttachments.activeSight(bench.getItem(0)).is(sight)&&NativeAttachments.active(bench.getItem(0),slot).is(accessory),"magazine removal keeps sight and muzzle");
            var saved=bench.saveWithoutMetadata();bench.load(saved);
            h.assertTrue(NativeAttachments.active(bench.getItem(0),slot).is(accessory),"rifle bench save/load");
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,slot,-1))&&count(a,accessory)==2,"rifle remove conserves");
            h.assertTrue(NativeAttachments.activeSight(bench.getItem(0)).is(sight),"muzzle removal keeps sight");
            var restored=ItemStack.of(bench.getItem(0).save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(NativeAttachments.activeSight(restored).is(sight),"sight stack persistence");
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,NativeAttachment.Slot.SIGHT,-1))&&count(a,sight)==2,"rifle sight remove conserves");
        }
        bench.clearContent();a.containerMenu=a.inventoryMenu;b.containerMenu=b.inventoryMenu;h.succeed();
    }
    private static MaintenanceActionRequest request(ServerPlayer p,GunMaintenanceBenchBlockEntity b,NativeAttachment.Slot slot,int source){
        return new MaintenanceActionRequest(p.containerMenu.containerId,b.getBlockPos(),b.attachmentRevision(),b.getItem(0).copy(),slot,source,
                source<0?ItemStack.EMPTY:p.getInventory().getItem(source).copy());
    }
    public static void run(GameTestHelper h){
        var root=h.absolutePos(new BlockPos(4,2,4));var level=h.getLevel();
        for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(root,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
        var bench=(GunMaintenanceBenchBlockEntity)level.getBlockEntity(root);
        var a=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"attach_A"));
        var b=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"attach_B"));
        a.setPos(root.getX(),root.getY(),root.getZ());b.setPos(root.getX(),root.getY(),root.getZ());
        var am=new GunMaintenanceMenu(51,a.getInventory(),bench);var bm=new GunMaintenanceMenu(52,b.getInventory(),bench);a.containerMenu=am;b.containerMenu=bm;
        for(int trial=0;trial<20;trial++){
            bench.clearContent();a.getInventory().clearContent();b.getInventory().clearContent();
            a.getInventory().setItem(4,new ItemStack(AflItems.P9_01.get()));h.assertTrue(am.clickMenuButton(a,4),"attachment fixture");
            a.getInventory().setItem(23,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));b.getInventory().setItem(23,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));
            var ar=request(a,bench,NativeAttachment.Slot.MUZZLE,23);var br=request(b,bench,NativeAttachment.Slot.MUZZLE,23);
            var winner=trial%2==0?a:b;var loser=trial%2==0?b:a;
            h.assertTrue(MaintenanceAttachmentTransaction.commit(winner,trial%2==0?ar:br),"first install");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(loser,trial%2==0?br:ar),"stale install rejected");
            h.assertTrue(winner.getInventory().getItem(23).isEmpty()&&loser.getInventory().getItem(23).getCount()==1,"exactly one consumed");
            h.assertTrue(am.originSlotFor(a.getUUID())==4&&bm.canShowTakeButton(b.getUUID()),"attachment preserves return metadata");
            a.getInventory().setItem(24,new ItemStack(AflItems.PISTOL_RED_DOT.get()));
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,NativeAttachment.Slot.SIGHT,24)),"sight installed");
            h.assertTrue(!NativeAttachments.activeSight(bench.getItem(0)).isEmpty()&&!NativeAttachments.active(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"both slots");
            var stale=request(loser,bench,NativeAttachment.Slot.MUZZLE,23);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(winner,request(winner,bench,NativeAttachment.Slot.MUZZLE,-1)),"remove");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(loser,stale),"remove vs replace stale");
            h.assertTrue(!NativeAttachments.activeSight(bench.getItem(0)).isEmpty(),"muzzle removal preserves sight");
            a.getInventory().setItem(25,new ItemStack(AflItems.PISTOL_RED_DOT.get()));
            int before=count(a,AflItems.PISTOL_RED_DOT.get());
            h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,NativeAttachment.Slot.SIGHT,25)),"same type replacement");
            h.assertTrue(count(a,AflItems.PISTOL_RED_DOT.get())==before,"replacement conserves old attachment");
            var saved=bench.saveWithoutMetadata();bench.load(saved);
            h.assertTrue(!NativeAttachments.activeSight(bench.getItem(0)).isEmpty(),"BE persistence");
            var invalid=request(a,bench,NativeAttachment.Slot.SIGHT,-1);a.containerMenu=a.inventoryMenu;
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(a,invalid),"closed menu rejected");a.containerMenu=am;
        }
        for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        h.assertTrue(MaintenanceAttachmentTransaction.commit(a,request(a,bench,NativeAttachment.Slot.SIGHT,-1)),"full inventory detach");
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(5),e->e.getItem().is(AflItems.PISTOL_RED_DOT.get())).stream().mapToInt(e->e.getItem().getCount()).sum()==1,"exactly one drop");
        for(var e:level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(5),e->e.getItem().is(AflItems.PISTOL_RED_DOT.get())))e.discard();
        bench.clearContent();a.containerMenu=a.inventoryMenu;b.containerMenu=b.inventoryMenu;
        com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE ATTACHMENTS] 20 alternating races, replace/remove, origin, persistence, full drop PASS");
    }
    private static int count(ServerPlayer p,Item item){int n=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}
}
