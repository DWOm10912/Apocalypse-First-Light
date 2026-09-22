package com.antaurora.apofirstlight.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/** Shared server-thread inventory exchange; target adapters validate identity before calling. */
public final class AttachmentInteractionCore {
    public static boolean valid(ServerPlayer player, ItemStack gun, NativeAttachment.Slot target,
            int sourceSlot, ItemStack expectedSource) {
        if (!AttachmentModificationPolicy.allowed(gun) || !NativeAttachments.supportsSlot(gun,target)) return false;
        if (sourceSlot == -1) return !NativeAttachments.stored(gun,target).isEmpty();
        if (sourceSlot < 0 || sourceSlot >= 36) return false;
        var source=player.getInventory().getItem(sourceSlot);
        return !source.isEmpty() && ItemStack.matches(source,expectedSource)
                && source.getItem() instanceof NativeAttachment a && a.slot()==target
                && NativeAttachments.compatible(gun,source);
    }
    public static boolean commit(ServerPlayer player, ItemStack gun, NativeAttachment.Slot target,
            int sourceSlot, ItemStack expectedSource, Consumer<ItemStack> publish) {
        if (!valid(player,gun,target,sourceSlot,expectedSource)) return false;
        var old=NativeAttachments.stored(gun,target);
        boolean remove=sourceSlot==-1;
        var source=remove?ItemStack.EMPTY:player.getInventory().getItem(sourceSlot);
        var updated=gun.copy();
        var definition=((NativeGunItem)gun.getItem()).definition();
        int loaded=NativeGunAmmo.read(gun,definition);
        var next=source.copy();next.setCount(remove?0:1);
        NativeAttachments.writeStored(updated,target,next);
        var returns=new java.util.ArrayList<ItemStack>();
        if(!old.isEmpty())returns.add(old.copy());
        if(target==NativeAttachment.Slot.MAGAZINE){
            int excess=Math.max(0,loaded-NativeGunAmmo.capacity(updated,definition));
            NativeGunAmmo.set(updated,definition,loaded);
            if(excess>0)returns.add(new ItemStack(java.util.Objects.requireNonNull(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(definition.ammoType())),excess));
        }
        // Preserve a full inventory snapshot; the target is published only after all returns succeed.
        var inv=player.getInventory();var before=new java.util.ArrayList<ItemStack>();
        for(int i=0;i<36;i++)before.add(inv.getItem(i).copy());
        if(!remove)source.shrink(1); // Both entry points conserve real items, including creative players.
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
        publish.accept(updated);
        inv.setChanged();player.inventoryMenu.broadcastChanges();
        return true;
    }
    private AttachmentInteractionCore() {}
}
