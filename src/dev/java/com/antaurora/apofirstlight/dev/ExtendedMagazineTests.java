package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.*;
import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("afl_workbench_tests") @PrefixGameTestTemplate(false)
public final class ExtendedMagazineTests {
    @GameTestGenerator public static java.util.Collection<TestFunction> cases(){
        return java.util.List.of(new TestFunction("extended_magazine","afl_workbench_tests:extended_magazine","afl_workbench_tests:empty",200,0L,true,ExtendedMagazineTests::run));
    }
    private static MaintenanceActionRequest request(ServerPlayer p,GunMaintenanceBenchBlockEntity b,int source){
        return new MaintenanceActionRequest(p.containerMenu.containerId,b.getBlockPos(),b.attachmentRevision(),b.getItem(0).copy(),NativeAttachment.Slot.MAGAZINE,source,source<0?ItemStack.EMPTY:p.getInventory().getItem(source).copy());
    }
    public static void run(GameTestHelper h){
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(4,2,4));
        for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(root,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
        var bench=(GunMaintenanceBenchBlockEntity)level.getBlockEntity(root);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"magazine_A"));
        var q=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"magazine_B"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);q.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setPos(root.getCenter());q.setPos(root.getCenter());
        var menu=new GunMaintenanceMenu(61,p.getInventory(),bench);p.containerMenu=menu;q.containerMenu=new GunMaintenanceMenu(62,q.getInventory(),bench);
        var d=NativeGunDefinition.P9_01;
        var magItem=AflItems.P9_01_EXTENDED_MAGAZINE.get();
        var ammo=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(d.ammoType());
        h.assertTrue(!NativeAttachments.compatible(new ItemStack(AflItems.BR51_01.get()),new ItemStack(magItem)),"BR51 incompatible");
        for(int count:new int[]{0,5,17,19,24}){
            bench.clearContent();p.getInventory().clearContent();q.getInventory().clearContent();
            p.getInventory().setItem(4,new ItemStack(AflItems.P9_01.get()));
            h.assertTrue(menu.clickMenuButton(p,4),"Place P9");
            h.assertTrue(NativeGunAmmo.capacity(bench.getItem(0),d)==17,"Standard17");
            p.getInventory().setItem(12,new ItemStack(magItem));q.getInventory().setItem(12,new ItemStack(magItem));
            var first=request(p,bench,12);var stale=request(q,bench,12);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(p,first),"Install");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(q,stale),"Concurrent stale install rejected");
            var gun=bench.getItem(0);
            h.assertTrue(NativeGunAmmo.capacity(gun,d)==24&&NativeGunAmmo.read(gun,d)==17,"Install raises limit without free rounds");
            NativeGunAmmo.set(gun,d,count);
            var saved=ItemStack.of(gun.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(NativeGunAmmo.capacity(saved,d)==24&&NativeGunAmmo.read(saved,d)==count,"Stack persistence");
            var remove=request(p,bench,-1);
            h.assertTrue(MaintenanceAttachmentTransaction.commit(p,remove),"Remove");
            h.assertTrue(!MaintenanceAttachmentTransaction.commit(p,remove),"Repeated remove rejected");
            h.assertTrue(NativeGunAmmo.capacity(bench.getItem(0),d)==17&&NativeGunAmmo.read(bench.getItem(0),d)==Math.min(17,count),"Truncated limit");
            h.assertTrue(count(p,ammo)==Math.max(0,count-17)&&count(p,magItem)==1&&count(q,magItem)==1,"Rounds and accessories conserved");
        }
        p.getInventory().setItem(12,new ItemStack(magItem));
        h.assertTrue(MaintenanceAttachmentTransaction.commit(p,request(p,bench,12)),"Reinstall");
        NativeGunAmmo.set(bench.getItem(0),d,17);
        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(ammo))p.getInventory().setItem(i,ItemStack.EMPTY);
        p.getInventory().setItem(13,new ItemStack(ammo,10));
        h.assertTrue(NativeGunAmmo.transfer(p.getInventory(),bench.getItem(0),d)==7&&NativeGunAmmo.read(bench.getItem(0),d)==24&&p.getInventory().getItem(13).getCount()==3,"Reload fills24 consumes7");
        for(int i=0;i<24;i++)h.assertTrue(NativeGunAmmo.consumeOne(bench.getItem(0),d),"24 rounds can fire");
        h.assertTrue(!NativeGunAmmo.consumeOne(bench.getItem(0),d),"25th cannot fire");
        NativeGunAmmo.set(bench.getItem(0),d,24);
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(root).inflate(5)).forEach(net.minecraft.world.entity.Entity::discard);
        h.assertTrue(MaintenanceAttachmentTransaction.commit(p,request(p,bench,-1)),"Full inventory detach");
        var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(root).inflate(5));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(ammo)).mapToInt(e->e.getItem().getCount()).sum()==7,"Seven overflow rounds dropped");
        h.assertTrue(drops.stream().filter(e->e.getItem().is(magItem)).mapToInt(e->e.getItem().getCount()).sum()==1,"One magazine dropped");
        drops.forEach(net.minecraft.world.entity.Entity::discard);bench.clearContent();p.containerMenu=p.inventoryMenu;q.containerMenu=q.inventoryMenu;
        h.succeed();
    }
    private static int count(ServerPlayer p,Item item){return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
}
