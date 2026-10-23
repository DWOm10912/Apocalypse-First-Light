package com.antaurora.apofirstlight.weapon;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import static com.antaurora.apofirstlight.weapon.NativeAttachment.Slot;

/** Stack-local slots; live player mutations go through server-authoritative maintenance operations. */
public final class NativeAttachments {
    private static final String ROOT="AflAttachments";
    /** Before the sling became an attachment (2026-10-22) a fitted sling was this flag; it still reads as one. */
    private static final String LEGACY_SLING="AflSling";
    /** Serialization primitive; callers must provide server authority and compatibility validation. */
    static void writeStored(ItemStack gun,Slot slot,ItemStack attachment){
        if(slot==Slot.SLING)gun.getOrCreateTag().remove(LEGACY_SLING);
        var root=gun.getOrCreateTag().getCompound(ROOT);
        if(attachment.isEmpty())root.remove(slot.name());
        else root.put(slot.name(),attachment.save(new net.minecraft.nbt.CompoundTag()));
        if(root.isEmpty())gun.getOrCreateTag().remove(ROOT);else gun.getOrCreateTag().put(ROOT,root);
    }
    public static ItemStack stored(ItemStack gun,Slot slot){
        var tag=gun.getTag();
        if(tag==null)return ItemStack.EMPTY;
        if(slot==Slot.SLING&&tag.getBoolean(LEGACY_SLING)&&!tag.getCompound(ROOT).contains(slot.name()))
            return new ItemStack(com.antaurora.apofirstlight.registry.AflItems.GUN_SLING.get());
        return !tag.contains(ROOT,Tag.TAG_COMPOUND)?ItemStack.EMPTY:
                ItemStack.of(tag.getCompound(ROOT).getCompound(slot.name()));
    }
    /** Setup only (GameTests, dev): a sling at the swivel without the bench transaction. */
    public static void fitSling(ItemStack gun){writeStored(gun,Slot.SLING,new ItemStack(com.antaurora.apofirstlight.registry.AflItems.GUN_SLING.get()));}
    public static ItemStack storedSight(ItemStack gun){return stored(gun,Slot.SIGHT);}
    public static boolean supportsSlot(ItemStack gun,Slot slot){
        if(!(gun.getItem() instanceof NativeGunItem g))return false;
        var d=g.definition();
        return switch(slot){
            case SIGHT -> d.sightMount()!=null&&!d.sightMount().accepts().isEmpty();
            case MUZZLE -> d.muzzleMount()!=null&&!d.muzzleMount().accepts().isEmpty();
            case MAGAZINE -> d.magazineMount()!=null&&!d.magazineMount().accepts().isEmpty();
            case SLING -> d.slingMount()!=null&&!d.slingMount().accepts().isEmpty();
        };
    }
    public static boolean compatible(ItemStack gun,ItemStack attachment){
        if(!(gun.getItem() instanceof NativeGunItem g)||!(attachment.getItem() instanceof NativeAttachment a))return false;
        var id=ForgeRegistries.ITEMS.getKey(attachment.getItem());var d=g.definition();
        return switch(a.slot()){
            case SIGHT -> d.sightMount()!=null&&d.sightMount().accepts().contains(id);
            case MUZZLE -> d.muzzleMount()!=null&&d.muzzleMount().accepts().contains(id);
            case MAGAZINE -> a instanceof NativeMagazineItem m&&m.accepts(d)&&d.magazineMount()!=null&&d.magazineMount().accepts().contains(id);
            case SLING -> d.slingMount()!=null&&d.slingMount().accepts().contains(id);
        };
    }
    public static ItemStack active(ItemStack gun,Slot slot){
        var s=stored(gun,slot);
        return s.getItem() instanceof NativeAttachment a&&a.slot()==slot&&compatible(gun,s)?s:ItemStack.EMPTY;
    }
    public static ItemStack activeSight(ItemStack gun){return active(gun,Slot.SIGHT);}
    private NativeAttachments(){}
}
