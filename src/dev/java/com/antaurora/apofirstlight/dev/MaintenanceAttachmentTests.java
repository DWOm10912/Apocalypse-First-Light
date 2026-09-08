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
