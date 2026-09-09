package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import static com.antaurora.apofirstlight.weapon.NativeAttachment.Slot;
import static com.antaurora.apofirstlight.weapon.NativeAttachments.*;

/** Historical exchange fixture for stack/compatibility regression only. Never shipped as a player entry point. */
final class LegacyAttachmentFixture {
    private static final String ROOT="AflAttachments";
    private static Slot detachSlot(ItemStack gun){return !storedSight(gun).isEmpty()?Slot.SIGHT:Slot.MUZZLE;}
    public static boolean gesture(net.minecraft.world.entity.player.Player p){
        var gun=p.getMainHandItem();
        return gun.getItem() instanceof NativeGunItem
                &&(!(p.getOffhandItem().getItem() instanceof NativeMagazineItem))
                &&(compatible(gun,p.getOffhandItem())||p.getOffhandItem().isEmpty()&&!stored(gun,detachSlot(gun)).isEmpty());
    }
    public static boolean requestMatches(ServerPlayer p,int slot,ItemStack gun,ItemStack offhand){
        return slot>=0&&slot<9&&p.getInventory().selected==slot&&p.containerMenu==p.inventoryMenu
                &&ItemStack.matches(p.getMainHandItem(),gun)&&ItemStack.matches(p.getOffhandItem(),offhand);
    }
    public static boolean exchange(ServerPlayer p){
        if(!p.isAlive()||p.isSpectator()||P901Actions.busy(p)||p.containerMenu!=p.inventoryMenu||!gesture(p))return false;
        var gun=p.getMainHandItem();var off=p.getOffhandItem();
        Slot slot=off.isEmpty()?detachSlot(gun):((NativeAttachment)off.getItem()).slot();
        var old=stored(gun,slot);
        if(off.isEmpty()&&!old.isEmpty()){
            var root=gun.getOrCreateTag().getCompound(ROOT);root.remove(slot.name());
            if(root.isEmpty())gun.getOrCreateTag().remove(ROOT);else gun.getOrCreateTag().put(ROOT,root);
            old.setCount(1);p.setItemInHand(InteractionHand.OFF_HAND,old);
        }else if(old.isEmpty()&&compatible(gun,off)){
            var copy=off.copy();copy.setCount(1);
            var root=gun.getOrCreateTag().getCompound(ROOT);root.put(slot.name(),copy.save(new net.minecraft.nbt.CompoundTag()));
            gun.getOrCreateTag().put(ROOT,root);
            if(!p.isCreative())off.shrink(1);
        }else return false; // No silent replacement or item loss.
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();p.containerMenu.broadcastChanges();return true;
    }
}
