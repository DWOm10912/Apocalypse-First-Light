package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Runs exclusively on the server thread. Validate everything before touching either inventory. */
public final class MaintenanceAttachmentTransaction {
    public static boolean valid(ServerPlayer player, MaintenanceActionRequest r){
        if(player.isSpectator()||!player.isAlive()||!(player.containerMenu instanceof GunMaintenanceMenu menu)
                ||menu.containerId!=r.containerId()||!menu.stillValid(player)
                ||!menu.bench.getBlockPos().equals(r.bench())||menu.bench.attachmentRevision()!=r.revision())return false;
        var gun=menu.bench.getItem(0);
        if(!(gun.getItem() instanceof NativeGunItem g)||!g.definition().id().toString().equals("apocalypse_firstlight:p9_01")
                ||!ItemStack.matches(gun,r.expectedGun()))return false;
        var old=NativeAttachments.stored(gun,r.target());
        boolean remove=r.sourceSlot()==-1;
        if(remove&&old.isEmpty())return false;
        ItemStack source=ItemStack.EMPTY;
        if(!remove){
            if(r.sourceSlot()<0||r.sourceSlot()>=36)return false;
            source=player.getInventory().getItem(r.sourceSlot());
            if(source.isEmpty()||!ItemStack.matches(source,r.expectedSource())
                    ||!(source.getItem() instanceof NativeAttachment a)||a.slot()!=r.target()
                    ||!NativeAttachments.compatible(gun,source))return false;
        }
        return true;
    }
    public static boolean commit(ServerPlayer player,MaintenanceActionRequest r){
        if(!valid(player,r))return false;
        var menu=(GunMaintenanceMenu)player.containerMenu;
        var gun=menu.bench.getItem(0);
        var old=NativeAttachments.stored(gun,r.target());
        boolean remove=r.sourceSlot()==-1;
        var source=remove?ItemStack.EMPTY:player.getInventory().getItem(r.sourceSlot());
        var updated=gun.copy();
        var definition=((NativeGunItem)gun.getItem()).definition();
        int loaded=NativeGunAmmo.read(gun,definition);
        var next=source.copy();next.setCount(remove?0:1);
        NativeAttachments.writeStored(updated,r.target(),next);
        var returns=new java.util.ArrayList<ItemStack>();
        if(!old.isEmpty())returns.add(old.copy());
        if(r.target()==NativeAttachment.Slot.MAGAZINE){
            int excess=Math.max(0,loaded-NativeGunAmmo.capacity(updated,definition));
            NativeGunAmmo.set(updated,definition,loaded);
            if(excess>0)returns.add(new ItemStack(java.util.Objects.requireNonNull(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(definition.ammoType())),excess));
        }
        // Ordinary inventory insertion works on copies first; failed drop can roll back completely.
        var inv=player.getInventory();var before=new java.util.ArrayList<ItemStack>();
        for(int i=0;i<36;i++)before.add(inv.getItem(i).copy());
        if(!remove)source.shrink(1); // Bench transactions conserve real items, including creative players.
        var dropped=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        for(var returned:returns){
            for(int i=0;i<36&&!returned.isEmpty();i++){
                var slot=inv.getItem(i);
                if(!slot.isEmpty()&&ItemStack.isSameItemSameTags(slot,returned)){
                    int amount=Math.min(returned.getCount(),Math.max(0,slot.getMaxStackSize()-slot.getCount()));
                    slot.grow(amount);returned.shrink(amount);
                }
            }
            for(int i=0;i<36&&!returned.isEmpty();i++)if(inv.getItem(i).isEmpty()){
                int amount=Math.min(returned.getCount(),returned.getMaxStackSize());
                var part=returned.copy();part.setCount(amount);inv.setItem(i,part);returned.shrink(amount);
            }
            if(!returned.isEmpty()){
                var drop=player.drop(returned,false);
                if(drop==null){
                    dropped.forEach(net.minecraft.world.entity.Entity::discard);
                    for(int i=0;i<36;i++)inv.setItem(i,before.get(i));return false;
                }
                dropped.add(drop);
            }
        }
        menu.bench.commitAttachments(updated);inv.setChanged();menu.broadcastChanges();player.inventoryMenu.broadcastChanges();
        return true;
    }
    private MaintenanceAttachmentTransaction(){}
}
