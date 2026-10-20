package com.antaurora.apofirstlight.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/**
 * Shared server-thread inventory exchange; target adapters validate identity before calling. The source is a vanilla
 * inventory index (0..35) or a grid entry (GridSource, docs/ui/afl_inventory_grid_v0_plan.md 13); what comes off the
 * gun goes in by the normal pickup order (the grids in Survival / Adventure), or drops when nothing takes it.
 */
public final class AttachmentInteractionCore {
    /** The live source stack: a vanilla slot's, or a grid entry's in {@code grid}'s view; empty when there is none. */
    private static ItemStack source(ServerPlayer player, int sourceSlot, com.antaurora.apofirstlight.inventory.grid.GridSource.Found[] grid) {
        if (com.antaurora.apofirstlight.inventory.grid.GridSource.is(sourceSlot)) {
            var found=com.antaurora.apofirstlight.inventory.grid.GridSource.find(player,sourceSlot);
            if (grid != null) grid[0]=found;
            return found == null ? ItemStack.EMPTY : found.entry().stack();
        }
        if (sourceSlot < 0 || sourceSlot >= 36) return ItemStack.EMPTY;
        return player.getInventory().getItem(sourceSlot);
    }
    public static boolean valid(ServerPlayer player, ItemStack gun, NativeAttachment.Slot target,
            int sourceSlot, ItemStack expectedSource) {
        if (!AttachmentModificationPolicy.allowed(gun) || !NativeAttachments.supportsSlot(gun,target)) return false;
        if (sourceSlot == -1) return !NativeAttachments.stored(gun,target).isEmpty();
        var source=source(player,sourceSlot,null);
        return !source.isEmpty() && ItemStack.matches(source,expectedSource)
                && source.getItem() instanceof NativeAttachment a && a.slot()==target
                && NativeAttachments.compatible(gun,source);
    }
    public static boolean commit(ServerPlayer player, ItemStack gun, NativeAttachment.Slot target,
            int sourceSlot, ItemStack expectedSource, Consumer<ItemStack> publish) {
        if (!valid(player,gun,target,sourceSlot,expectedSource)) return false;
        var old=NativeAttachments.stored(gun,target);
        boolean remove=sourceSlot==-1;
        var grid=new com.antaurora.apofirstlight.inventory.grid.GridSource.Found[1];
        var source=remove?ItemStack.EMPTY:source(player,sourceSlot,grid);
        var updated=gun.copy();
        var definition=((NativeGunItem)gun.getItem()).definition();
        int loaded=NativeGunAmmo.read(gun,definition);
        var next=source.copy();next.setCount(remove?0:1);
        NativeAttachments.writeStored(updated,target,next);
        var returns=new java.util.ArrayList<ItemStack>();
        if(!old.isEmpty())returns.add(old.copy());
        if(target==NativeAttachment.Slot.MAGAZINE&&NativeMagazines.system(definition)){
            // magazines keep their rounds (NativeMagazines): the fitted one comes off with what is left in it, the new one
            // goes in with its own; the chambered round stays
            returns.clear();
            var out=NativeMagazines.takenOut(gun,definition);
            if(!out.isEmpty())returns.add(out);
            NativeMagazines.fit(updated,definition,remove?ItemStack.EMPTY:next,remove?0:NativeMagazines.rounds(source));
        }else if(target==NativeAttachment.Slot.MAGAZINE){
            int excess=Math.max(0,loaded-NativeGunAmmo.capacity(updated,definition));
            NativeGunAmmo.set(updated,definition,loaded);
            if(excess>0)returns.add(new ItemStack(java.util.Objects.requireNonNull(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(definition.ammoType())),excess));
        }
        // Preserve a full inventory and grid snapshot; the target is published only after all returns succeed.
        var inv=player.getInventory();var before=new java.util.ArrayList<ItemStack>();
        for(int i=0;i<36;i++)before.add(inv.getItem(i).copy());
        var gridsBefore=com.antaurora.apofirstlight.inventory.grid.PlayerGrids.snapshot(player);
        if(!remove)source.shrink(1); // Both entry points conserve real items, including creative players.
        if(grid[0]!=null)grid[0].view().commit();
        var dropped=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        for(var returned:returns){
            inv.add(returned); // the normal pickup order: weapon positions by role, then pockets, rig, backpack
            if(!returned.isEmpty()){
                var drop=player.drop(returned,false);
                if(drop==null){
                    dropped.forEach(net.minecraft.world.entity.Entity::discard);
                    for(int i=0;i<36;i++)inv.setItem(i,before.get(i));gridsBefore.run();return false;
                }
                dropped.add(drop);
            }
        }
        publish.accept(updated);
        inv.setChanged();player.inventoryMenu.broadcastChanges();
        com.antaurora.apofirstlight.weight.PlayerWeightRuntime.dirty(player);
        return true;
    }
    private AttachmentInteractionCore() {}
}
