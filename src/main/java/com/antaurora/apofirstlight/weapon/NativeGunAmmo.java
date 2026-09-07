package com.antaurora.apofirstlight.weapon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Stack persistence. Mutators are called only by server gameplay; reads never create NBT. */
public final class NativeGunAmmo {
    private static final String ROOT = "AflGunAmmo";
    private static final String COUNT = "ammoInMagazine";
    private NativeGunAmmo() {}

    static CompoundTag tagWithoutAmmo(ItemStack stack) {
        CompoundTag copy = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        copy.remove(ROOT);
        return copy;
    }

    public static int read(ItemStack gun, NativeGunDefinition definition) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(ROOT)) return definition.magazineCapacity();
        return Math.max(0, Math.min(definition.magazineCapacity(), tag.getCompound(ROOT).getInt(COUNT)));
    }

    public static void initialize(ItemStack gun, NativeGunDefinition definition) {
        // Preserve zero and existing states; sanitize corrupt/out-of-range data rather than refill it.
        int count = read(gun, definition);
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)
                || !tag.getCompound(ROOT).contains(COUNT, net.minecraft.nbt.Tag.TAG_INT)
                || tag.getCompound(ROOT).getInt(COUNT) != count) set(gun, definition, count);
    }

    public static void set(ItemStack gun, NativeGunDefinition definition, int count) {
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag ammo = tag.getCompound(ROOT);
        ammo.putInt(COUNT, Math.max(0, Math.min(definition.magazineCapacity(), count)));
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
        return result;
    }

    /** Recompute at mag-in on the server thread; no ammo is reserved or removed at reload start. */
    public static int transfer(Inventory inventory, ItemStack gun, NativeGunDefinition definition) {
        int current = read(gun, definition), remaining = definition.magazineCapacity() - current;
        if (infiniteReserve(inventory)) {
            set(gun, definition, definition.magazineCapacity());
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
        set(gun, definition, current + loaded);
        inventory.setChanged();
        return loaded;
    }
}
