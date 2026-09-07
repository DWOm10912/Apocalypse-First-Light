package com.antaurora.apofirstlight.weapon;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** One stack-local slot, transacted only on the server through a dedicated key packet. */
public final class NativeAttachments {
    private static final String ROOT="AflAttachments",SIGHT="SIGHT";
    public static ItemStack storedSight(ItemStack gun){
        var tag=gun.getTag();
        return tag==null||!tag.contains(ROOT,Tag.TAG_COMPOUND)?ItemStack.EMPTY:
                ItemStack.of(tag.getCompound(ROOT).getCompound(SIGHT));
    }
    public static boolean compatible(ItemStack gun,ItemStack sight){
        if(!(gun.getItem() instanceof NativeGunItem nativeGun)||!(sight.getItem() instanceof NativeSightItem))return false;
        var mount=nativeGun.definition().sightMount();
        return mount!=null&&mount.accepts().contains(ForgeRegistries.ITEMS.getKey(sight.getItem()));
    }
    public static ItemStack activeSight(ItemStack gun){var s=storedSight(gun);return compatible(gun,s)?s:ItemStack.EMPTY;}
    public static boolean gesture(net.minecraft.world.entity.player.Player p){
        var gun=p.getMainHandItem();
        return gun.getItem() instanceof NativeGunItem
                &&(compatible(gun,p.getOffhandItem())||p.getOffhandItem().isEmpty()&&!storedSight(gun).isEmpty());
    }
    public static boolean exchange(ServerPlayer p){
        if(!p.isAlive()||p.isSpectator()||P901Actions.busy(p)||!gesture(p))return false;
        var gun=p.getMainHandItem();var old=storedSight(gun);var off=p.getOffhandItem();
        if(off.isEmpty()&&!old.isEmpty()){
            var root=gun.getOrCreateTag().getCompound(ROOT);root.remove(SIGHT);
            if(root.isEmpty())gun.getOrCreateTag().remove(ROOT);else gun.getOrCreateTag().put(ROOT,root);
            old.setCount(1);p.setItemInHand(InteractionHand.OFF_HAND,old);
        }else if(old.isEmpty()&&compatible(gun,off)){
            var copy=off.copy();copy.setCount(1);
            var root=gun.getOrCreateTag().getCompound(ROOT);root.put(SIGHT,copy.save(new net.minecraft.nbt.CompoundTag()));
            gun.getOrCreateTag().put(ROOT,root);
            if(!p.isCreative())off.shrink(1);
        }else return false; // No silent replacement or item loss.
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();p.containerMenu.broadcastChanges();return true;
    }
    private NativeAttachments(){}
}
