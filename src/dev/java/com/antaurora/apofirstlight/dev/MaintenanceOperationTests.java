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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;

public final class MaintenanceOperationTests {
    private static MaintenanceActionRequest request(ServerPlayer p,GunMaintenanceBenchBlockEntity b,int slot){
        return new MaintenanceActionRequest(p.containerMenu.containerId,b.getBlockPos(),b.attachmentRevision(),b.getItem(0).copy(),NativeAttachment.Slot.MUZZLE,slot,
                slot<0?ItemStack.EMPTY:p.getInventory().getItem(slot).copy());
    }
    public static void run(GameTestHelper h){
        var root=h.absolutePos(new BlockPos(4,2,4));var l=h.getLevel();
        l.setBlock(root.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        l.setBlock(root.east().below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        for(var part:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(root,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
        var bench=(GunMaintenanceBenchBlockEntity)l.getBlockEntity(root);
        var a=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"operation_A"));var b=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"operation_B"));
        a.setPos(root.getX(),root.getY(),root.getZ());b.setPos(a.position());
        var am=new GunMaintenanceMenu(61,a.getInventory(),bench);var bm=new GunMaintenanceMenu(62,b.getInventory(),bench);a.containerMenu=am;b.containerMenu=bm;
        a.getInventory().clearContent();b.getInventory().clearContent();a.getInventory().setItem(4,new ItemStack(AflItems.P9_01.get()));
        h.assertTrue(am.clickMenuButton(a,4),"setup");a.getInventory().setItem(23,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));
        h.assertTrue(MaintenanceAttachmentOperation.DURATION_TICKS==51,"real sound ceil plus one");
        h.startSequence().thenExecute(()->{
            var r=request(a,bench,23);MaintenanceAttachmentOperation.begin(a,r);MaintenanceAttachmentOperation.begin(a,r);
            h.assertTrue(MaintenanceAttachmentOperation.pending(a),"begin pending");
        }).thenIdle(45).thenExecute(()->{
            h.assertTrue(NativeAttachments.stored(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty()&&a.getInventory().getItem(23).getCount()==1,"no early install/consume");
        }).thenIdle(10).thenExecute(()->{
            h.assertTrue(!MaintenanceAttachmentOperation.pending(a)&&!NativeAttachments.active(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty()&&a.getInventory().getItem(23).isEmpty(),"install after deadline pending="+MaintenanceAttachmentOperation.pending(a)+" source="+a.getInventory().getItem(23)+" gun="+bench.getItem(0).getTag());
            MaintenanceAttachmentOperation.begin(a,request(a,bench,-1));
        }).thenIdle(45).thenExecute(()->h.assertTrue(!NativeAttachments.active(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"remove waits"))
        .thenIdle(10).thenExecute(()->{
            h.assertTrue(NativeAttachments.stored(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"removed after sound");
            a.getInventory().setItem(23,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));
            MaintenanceAttachmentOperation.begin(a,request(a,bench,23));a.containerMenu=a.inventoryMenu;
        }).thenIdle(55).thenExecute(()->{
            h.assertTrue(!MaintenanceAttachmentOperation.pending(a)&&a.getInventory().getItem(23).getCount()==1&&NativeAttachments.stored(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"close cancels without mutation");
            a.containerMenu=am;MaintenanceAttachmentOperation.begin(a,request(a,bench,23));a.getInventory().setItem(23,ItemStack.EMPTY);
        }).thenIdle(55).thenExecute(()->{
            h.assertTrue(!MaintenanceAttachmentOperation.pending(a)&&NativeAttachments.stored(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"source disappearance cancels");
            a.getInventory().setItem(23,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));b.getInventory().setItem(23,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));
            MaintenanceAttachmentOperation.begin(a,request(a,bench,23));MaintenanceAttachmentOperation.begin(b,request(b,bench,23));
        }).thenIdle(55).thenExecute(()->{
            h.assertTrue(!MaintenanceAttachmentOperation.pending(a)&&!MaintenanceAttachmentOperation.pending(b),"race completed");
            h.assertTrue(a.getInventory().getItem(23).getCount()+b.getInventory().getItem(23).getCount()==1&&!NativeAttachments.active(bench.getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"one delayed winner no dup");
            var source=new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get());source.getOrCreateTag().putString("Replacement","new");a.getInventory().setItem(23,source);
            MaintenanceAttachmentOperation.begin(a,request(a,bench,23));
        }).thenIdle(45).thenExecute(()->h.assertTrue(!NativeAttachments.stored(bench.getItem(0),NativeAttachment.Slot.MUZZLE).hasTag(),"replace waits"))
        .thenIdle(10).thenExecute(()->{
            h.assertTrue(NativeAttachments.stored(bench.getItem(0),NativeAttachment.Slot.MUZZLE).getOrCreateTag().getString("Replacement").equals("new"),"replace committed exact NBT");
            a.containerMenu=a.inventoryMenu;b.containerMenu=b.inventoryMenu;
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE OPERATION] PASS 51 ticks install/remove/replace duplicateBegin close sourceLoss delayedRace");
        }).thenSucceed();
    }
}
