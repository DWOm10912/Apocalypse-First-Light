package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The clips on a chest rig (docs/gameplay/equipment_slots_v1.md "挂点"; user 2026-10-21: "挂件栏位，应该是需要玩家装备了胸挂之后
 * 才能出现的……如果挂在任意胸挂上，就会好看很多，并且自动贴合"). What hangs there is saved in the rig's own NBT ("Clips", one
 * {Slot, item} per filled clip), so it goes wherever the rig goes: off the body, on the ground, into a corpse, and it
 * reaches the client with Curios' sync of the worn rig. Only items in the apocalypse_firstlight:clippable tag hang there.
 * <p>
 * {@link #get} hands out the same ItemStack object until the rig's "Clips" change: the compass card keeps its swing per
 * stack object, and the Geiger counter knows its own clipped stack by identity.
 */
public final class RigClips {
    private RigClips() {}

    public static final TagKey<Item> CLIPPABLE = ItemTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "clippable"));
    private static final String KEY = "Clips";

    private record Decoded(ListTag snapshot, ItemStack[] stacks) {}
    private static final Map<ItemStack, Decoded> CACHE = new WeakHashMap<>();

    /** How many clips the item has: those of a chest rig, none on anything else. */
    public static int count(ItemStack rig) {
        return rig.getItem() instanceof GridGearItem gear ? gear.clips() : 0;
    }

    /** What hangs on one clip, or empty. */
    public static ItemStack get(ItemStack rig, int index) {
        if (index < 0 || index >= count(rig)) return ItemStack.EMPTY;
        return decoded(rig)[index];
    }

    /** Everything that hangs on the rig. */
    public static List<ItemStack> all(ItemStack rig) {
        List<ItemStack> out = new ArrayList<>();
        int n = count(rig);
        if (n == 0) return out;
        var stacks = decoded(rig);
        for (int i = 0; i < n; i++) if (!stacks[i].isEmpty()) out.add(stacks[i]);
        return out;
    }

    /** Hangs the stack on the clip (empty takes it off). */
    public static void set(ItemStack rig, int index, ItemStack stack) {
        int n = count(rig);
        if (index < 0 || index >= n) return;
        var stacks = decoded(rig).clone();
        stacks[index] = stack;
        var list = new ListTag();
        for (int i = 0; i < n; i++) {
            if (stacks[i].isEmpty()) continue;
            var entry = stacks[i].save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        if (list.isEmpty()) { if (rig.getTag() != null) rig.getTag().remove(KEY); }
        else rig.getOrCreateTag().put(KEY, list);
        synchronized (CACHE) { CACHE.put(rig, new Decoded(list.copy(), stacks)); }
    }

    private static ItemStack[] decoded(ItemStack rig) {
        var tag = rig.getTag();
        ListTag list = tag == null ? new ListTag() : tag.getList(KEY, Tag.TAG_COMPOUND);
        synchronized (CACHE) {
            var cached = CACHE.get(rig);
            if (cached != null && cached.snapshot().equals(list)) return cached.stacks();
            var stacks = new ItemStack[count(rig)];
            java.util.Arrays.fill(stacks, ItemStack.EMPTY);
            for (int i = 0; i < list.size(); i++) {
                var entry = list.getCompound(i);
                int slot = entry.getByte("Slot");
                if (slot >= 0 && slot < stacks.length) stacks[slot] = ItemStack.of(entry);
            }
            CACHE.put(rig, new Decoded(list.copy(), stacks));
            return stacks;
        }
    }
}
