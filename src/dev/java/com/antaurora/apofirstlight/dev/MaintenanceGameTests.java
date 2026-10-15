package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import com.antaurora.apofirstlight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;

public final class MaintenanceGameTests {
    public static void transactions(GameTestHelper h){
        MaintenanceAttachmentTests.run(h);
        var root=h.absolutePos(new BlockPos(4,2,4));var level=h.getLevel();var block=AflBlocks.GUN_MAINTENANCE_BENCH.get();
        for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(root,Direction.NORTH,part),block.stateFor(Direction.NORTH,part),3);
        level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(root.east().below(),Blocks.STONE.defaultBlockState(),3);
        var bench=(GunMaintenanceBenchBlockEntity)level.getBlockEntity(root);
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"maintenance_test"));p.setPos(root.getX(),root.getY(),root.getZ());
        var other=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"maintenance_other"));other.setPos(root.getX(),root.getY(),root.getZ());
        var menu=new GunMaintenanceMenu(1,p.getInventory(),bench);p.containerMenu=menu;
        var second=new GunMaintenanceMenu(2,other.getInventory(),bench);other.containerMenu=second;
        var pending=new GunMaintenanceBenchBlockEntity(root,bench.getBlockState());
        var pendingMenu=new GunMaintenanceMenu(3,p.getInventory(),pending);
        var pendingGun=new ItemStack(AflItems.P9_01.get());
        pending.setItem(0,pendingGun.copy());
        h.assertTrue(!pendingMenu.canShowTakeButton(p.getUUID())&&pendingMenu.originFor(p.getUUID())==-1,"slot before metadata hides return controls");
        var snapshot=pending.saveWithoutMetadata();snapshot.putUUID("OriginPlayerUUID",p.getUUID());snapshot.remove("OriginSource");snapshot.remove("OriginRotated");snapshot.putInt("OriginHotbarSlot",4);
        pending.load(snapshot);
        // OriginHotbarSlot: a save from before the gun picker still names its origin
        h.assertTrue(!pendingMenu.canShowTakeButton(p.getUUID())&&pendingMenu.originFor(p.getUUID())==4,"owner snapshot restores placeholder only");
        pending.setItem(0,pendingGun.copy());
        h.assertTrue(pendingMenu.originFor(p.getUUID())==4,"identical slot after metadata preserves readiness");
        snapshot.putUUID("OriginPlayerUUID",other.getUUID());pending.load(snapshot);
        h.assertTrue(pendingMenu.canShowTakeButton(p.getUUID()),"confirmed other owner shows take");
        snapshot.remove("OriginPlayerUUID");pending.load(snapshot);
        h.assertTrue(pendingMenu.canShowTakeButton(p.getUUID()),"confirmed legacy missing owner remains retrievable");
        pending.setItem(0,ItemStack.EMPTY);
        h.assertTrue(!pendingMenu.canShowTakeButton(p.getUUID()),"empty slot hides take");
        h.assertTrue(!menu.clickMenuButton(p,-1)&&!menu.clickMenuButton(p,90),"bad indices");
        p.getInventory().setItem(0,new ItemStack(Items.STONE));h.assertTrue(!menu.clickMenuButton(p,0),"reject normal item");
        for(var item:new Item[]{AflItems.P9_01.get(),AflItems.BR51_01.get()}){
            var gun=new ItemStack(item);gun.getOrCreateTag().putString("MaintenanceTestNBT","preserved");
            var attachment=new net.minecraft.nbt.CompoundTag();attachment.put("SIGHT",new ItemStack(AflItems.PISTOL_RED_DOT.get()).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",attachment);
            int hand=com.antaurora.apofirstlight.inventory.HotbarRoles.HAND_SLOT;
            p.getInventory().setItem(hand,gun);h.assertTrue(menu.clickMenuButton(p,hand)&&p.getInventory().getItem(hand).isEmpty(),"true transfer");
            h.assertTrue(menu.originFor(p.getUUID())==hand&&!menu.canShowTakeButton(p.getUUID()),"owner placeholder metadata");
            h.assertTrue(second.originFor(other.getUUID())==-1&&second.canShowTakeButton(other.getUUID()),"non owner button metadata");
            h.assertTrue(!second.clickMenuButton(other,GunMaintenanceMenu.RETURN_GUN),"forged owner return rejected");
            h.assertTrue(!menu.clickMenuButton(p,hand),"replay insertion rejected");menu.removed(p);h.assertTrue(!bench.isEmpty(),"close persists");
            var saved=bench.saveWithoutMetadata();bench.load(saved);h.assertTrue(ItemStack.matches(gun,bench.getItem(0)),"save load full NBT");
            h.assertTrue(menu.clickMenuButton(p,9)&&ItemStack.matches(gun,p.getInventory().getItem(hand)),"origin return");
            h.assertTrue(!second.clickMenuButton(other,9),"second taker cannot duplicate");
            h.assertTrue(!second.clickMenuButton(other,GunMaintenanceMenu.TAKE_GUN)&&!second.canShowTakeButton(other.getUUID())&&menu.originFor(p.getUUID())==-1,"owner wins race and clears both entrypoints");
        }
        // origin taken: the normal pickup order (no sling, no backpack: nothing takes the rifle, it lands at the feet)
        int hand=com.antaurora.apofirstlight.inventory.HotbarRoles.HAND_SLOT;
        h.assertTrue(menu.clickMenuButton(p,hand),"insert for occupied origin");p.getInventory().setItem(hand,new ItemStack(Items.DIRT));
        var near=new AABB(root).inflate(4);
        h.assertTrue(menu.clickMenuButton(p,9)&&bench.isEmpty()&&level.getEntitiesOfClass(ItemEntity.class,near,e->e.getItem().is(AflItems.BR51_01.get())).size()==1,
                "origin taken, nothing takes it: at the feet");
        var lying=level.getEntitiesOfClass(ItemEntity.class,near,e->e.getItem().is(AflItems.BR51_01.get())).get(0);
        var rifle=lying.getItem().copy();lying.discard();
        // with a sling it goes to the primary position by the pickup order
        com.antaurora.apofirstlight.inventory.grid.GunSling.fit(rifle);
        p.getInventory().setItem(com.antaurora.apofirstlight.inventory.HotbarRoles.PRIMARY_SLOT,ItemStack.EMPTY); // the stone from "reject normal item"
        p.getInventory().setItem(hand,rifle);
        h.assertTrue(menu.clickMenuButton(p,hand),"insert for primary fallback");p.getInventory().setItem(hand,new ItemStack(Items.DIRT));
        h.assertTrue(menu.clickMenuButton(p,9)&&p.getInventory().getItem(com.antaurora.apofirstlight.inventory.HotbarRoles.PRIMARY_SLOT).is(AflItems.BR51_01.get()),
                "origin taken: a slung rifle goes to the primary position");
        h.assertTrue(menu.clickMenuButton(p,com.antaurora.apofirstlight.inventory.HotbarRoles.PRIMARY_SLOT),"insert for other player");
        h.assertTrue(second.clickMenuButton(other,GunMaintenanceMenu.TAKE_GUN)&&other.getInventory().getItem(0).getItem()==AflItems.BR51_01.get(),"other player's inventory only");
        h.assertTrue(!menu.clickMenuButton(p,GunMaintenanceMenu.RETURN_GUN)&&menu.originFor(p.getUUID())==-1,"non owner wins race and owner stale request rejected");
        h.assertTrue(second.clickMenuButton(other,0),"insert for full inventory");
        for(int i=0;i<36;i++)other.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        h.assertTrue(second.clickMenuButton(other,9)&&bench.isEmpty(),"full inventory safe drop");
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4),e->e.getItem().getItem()==AflItems.BR51_01.get()).size()==1,"one fallback gun");
        p.getInventory().setItem(3,new ItemStack(AflItems.P9_01.get()));h.assertTrue(menu.clickMenuButton(p,3),"insert for break");
        level.removeBlock(root.east().above(),false);
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4),e->e.getItem().getItem()==AflItems.P9_01.get()).size()==1,"part teardown one gun");
        h.assertTrue(!menu.clickMenuButton(p,9),"stale menu invalid");h.succeed();
    }
}
