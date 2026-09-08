package com.antaurora.apofirstlight.weapon;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import static com.antaurora.apofirstlight.weapon.NativeAttachment.Slot;

/** Stack-local slots, transacted only on the server through the existing V packet. */
public final class NativeAttachments {
    private static final String ROOT="AflAttachments";
    /** Serialization primitive; callers must provide server authority and compatibility validation. */
    static void writeStored(ItemStack gun,Slot slot,ItemStack attachment){
        var root=gun.getOrCreateTag().getCompound(ROOT);
        if(attachment.isEmpty())root.remove(slot.name());
        else root.put(slot.name(),attachment.save(new net.minecraft.nbt.CompoundTag()));
        if(root.isEmpty())gun.getOrCreateTag().remove(ROOT);else gun.getOrCreateTag().put(ROOT,root);
    }
    public static ItemStack stored(ItemStack gun,Slot slot){
        var tag=gun.getTag();
        return tag==null||!tag.contains(ROOT,Tag.TAG_COMPOUND)?ItemStack.EMPTY:
                ItemStack.of(tag.getCompound(ROOT).getCompound(slot.name()));
    }
    public static ItemStack storedSight(ItemStack gun){return stored(gun,Slot.SIGHT);}
    public static boolean compatible(ItemStack gun,ItemStack attachment){
        if(!(gun.getItem() instanceof NativeGunItem g)||!(attachment.getItem() instanceof NativeAttachment a))return false;
        var id=ForgeRegistries.ITEMS.getKey(attachment.getItem());var d=g.definition();
        return switch(a.slot()){
            case SIGHT -> d.sightMount()!=null&&d.sightMount().accepts().contains(id);
            case MUZZLE -> d.muzzleMount()!=null&&d.muzzleMount().accepts().contains(id);
        };
    }
    public static ItemStack active(ItemStack gun,Slot slot){
        var s=stored(gun,slot);
        return s.getItem() instanceof NativeAttachment a&&a.slot()==slot&&compatible(gun,s)?s:ItemStack.EMPTY;
    }
    public static ItemStack activeSight(ItemStack gun){return active(gun,Slot.SIGHT);}
    private static Slot detachSlot(ItemStack gun){return !storedSight(gun).isEmpty()?Slot.SIGHT:Slot.MUZZLE;}
    public static boolean gesture(net.minecraft.world.entity.player.Player p){
        var gun=p.getMainHandItem();
        return gun.getItem() instanceof NativeGunItem
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
    private NativeAttachments(){}
}
