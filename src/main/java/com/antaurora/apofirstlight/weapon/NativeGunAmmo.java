package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Stack persistence. Mutators are called by server gameplay (and on throwaway client preview stacks); reads never create
 * NBT. A gun without ammo data is empty: every new gun (creative tab, /give, crafting) starts with no rounds (user
 * 2026-10-19, docs/native_guns/p9_first_draw_chamber_v1.md 1). A gun meant to come loaded sets its count explicitly.
 */
public final class NativeGunAmmo {
    private static final String ROOT = "AflGunAmmo";
    private static final String COUNT = "ammoInMagazine";
    /** A round in the chamber (docs/native_guns/p9_first_draw_chamber_v1.md); not a round of its own, the count holds it. */
    private static final String CHAMBERED = "chambered";
    private NativeGunAmmo() {}

    /**
     * Whether a round is chambered. Missing means no. A new gun is empty and unchambered; its first empty reload chambers
     * a round. A gun with rounds but none chambered (an empty reload cut short after the magazine went in, a gun from
     * before this flag, one spawned with a loaded magazine) is chambered on its next draw (first_draw). The last shot
     * empties the chamber, a completed empty reload fills it.
     */
    public static boolean chambered(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        return tag != null && tag.getCompound(ROOT).getBoolean(CHAMBERED);
    }

    /**
     * Whether the gun was ever loaded: the chamber flag is written by the last shot (false) and by a completed empty
     * reload or first draw (true). A new gun has neither, so at 0 rounds its chambers hold nothing rather than spent
     * cases (NativeBreakActionChambers).
     */
    public static boolean everLoaded(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        return tag != null && tag.getCompound(ROOT).contains(CHAMBERED);
    }

    public static void setChambered(ItemStack gun, NativeGunDefinition definition, boolean chambered) {
        initialize(gun, definition);   // the count first, so the ammo data always holds it
        gun.getOrCreateTag().getCompound(ROOT).putBoolean(CHAMBERED, chambered);
    }
    /**
     * The most rounds the gun holds. A gun that loads from magazine items (NativeMagazines): the fitted magazine and the
     * chamber (P9-01 standard: 17 + 1), the chamber alone without a magazine.
     */
    public static int capacity(ItemStack gun,NativeGunDefinition definition){
        if(NativeMagazines.system(definition))return NativeMagazines.capacity(gun,definition);
        var attachment=NativeAttachments.active(gun,NativeAttachment.Slot.MAGAZINE);
        return attachment.getItem() instanceof NativeMagazineItem m?m.capacity():definition.magazineCapacity();
    }

    static CompoundTag tagWithoutAmmo(ItemStack stack) {
        CompoundTag copy = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        copy.remove(ROOT);
        copy.remove(NativeFireModes.TAG);
        // the fitted magazine of a gun that loads from magazine items changes with every reload
        if (NativeMagazines.system(stack) && copy.contains("AflAttachments", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            copy.getCompound("AflAttachments").remove(NativeAttachment.Slot.MAGAZINE.name());
            if (copy.getCompound("AflAttachments").isEmpty()) copy.remove("AflAttachments");
        }
        return copy;
    }

    /** Rounds in the magazine; 0 for a gun without ammo data (a new gun is empty). */
    public static int read(ItemStack gun, NativeGunDefinition definition) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(ROOT)) return 0;
        return Math.max(0, Math.min(capacity(gun,definition), tag.getCompound(ROOT).getInt(COUNT)));
    }

    public static void initialize(ItemStack gun, NativeGunDefinition definition) {
        NativeFireModes.sanitize(gun,definition);
        NativeMagazines.normalize(gun, definition);
        // A new gun is written as empty (read gives 0 without data). Preserve zero and existing states; sanitize
        // corrupt/out-of-range data rather than refill it.
        int count = read(gun, definition);
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)
                || !tag.getCompound(ROOT).contains(COUNT, net.minecraft.nbt.Tag.TAG_INT)
                || tag.getCompound(ROOT).getInt(COUNT) != count) set(gun, definition, count);
    }

    public static void set(ItemStack gun, NativeGunDefinition definition, int count) {
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag ammo = tag.getCompound(ROOT);
        ammo.putInt(COUNT, Math.max(0, Math.min(capacity(gun,definition), count)));
        tag.put(ROOT, ammo);
    }

    public static boolean consumeOne(ItemStack gun, NativeGunDefinition definition) {
        int count = read(gun, definition);
        initialize(gun, definition);
        if (count == 0) return false;
        set(gun, definition, count - 1);
        return true;
    }

    private static boolean matches(ItemStack stack, NativeGunDefinition definition) {
        return !stack.isEmpty() && definition.ammoType().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    public static boolean infiniteReserve(Inventory inventory) {
        return inventory.player.isCreative();
    }

    public static int reserve(Inventory inventory, NativeGunDefinition definition) {
        if (infiniteReserve(inventory)) return Integer.MAX_VALUE;
        int result = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(stack, definition)) result += stack.getCount();
        }
        // Grid Inventory V0: then the pockets, the rig and the backpack (the client reads its synced copies)
        for (ItemStack stack : PlayerGrids.stacks(inventory.player)) {
            if (matches(stack, definition)) result += stack.getCount();
        }
        return result;
    }

    /**
     * Recompute at mag-in on the server thread; no ammo is reserved or removed at reload start. Loose rounds into the
     * gun: only for guns that do not load from magazine items (NativeMagazines#swap does theirs).
     */
    public static int transfer(Inventory inventory, ItemStack gun, NativeGunDefinition definition) {
        int current = read(gun, definition), remaining = capacity(gun,definition) - current;
        if (infiniteReserve(inventory)) {
            set(gun, definition, capacity(gun,definition));
            inventory.setChanged();
            return remaining;
        }
        int loaded = 0;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!matches(stack, definition)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            loaded += take;
        }
        if (remaining > 0 && !inventory.player.level().isClientSide) {
            var grids = PlayerGrids.view(inventory.player);
            int fromGrids = 0;
            for (ItemStack stack : grids.stacks()) {
                if (remaining <= 0) break;
                if (!matches(stack, definition)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                fromGrids += take;
            }
            if (fromGrids > 0) grids.commit();
            loaded += fromGrids;
        }
        set(gun, definition, current + loaded);
        inventory.setChanged();
        return loaded;
    }
}
