package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One grid storage: its layout and the stacks lying in it, each at a cell with its rotation
 * (docs/ui/afl_inventory_grid_v0_plan.md 1). Sizes come from ItemSizes on the side given at creation.
 * <p>
 * Saved as a vanilla-style item list (each entry an ItemStack plus "Slot", so the weight system's generic container
 * reading counts it) with the grid position in "GridSec", "GridX", "GridY", "GridRot". On load an entry that no longer
 * fits where it was (other sizes after a data change) moves to the first free place; one that fits nowhere stays as
 * overflow: kept and shown, it can only be taken out.
 */
public final class GridStorage {
    /**
     * One stack in the grid. The stack is live: callers that change it call {@link #prune()} afterwards. A locked entry
     * (a searchable container's item not searched yet, AflContainerGrid) only takes its cells: nothing merges onto it, it
     * is never pruned, and the grid actions leave it alone; its footprint may be given outright (the client knows the
     * size of an unsearched item, not the item).
     */
    public static final class Entry {
        private ItemStack stack;
        private int section, x, y;
        private boolean rotated;
        private boolean locked;
        @Nullable private ItemSizes.Size fixed;

        Entry(ItemStack stack, int section, int x, int y, boolean rotated) {
            this.stack = stack;
            this.section = section;
            this.x = x;
            this.y = y;
            this.rotated = rotated;
        }

        public ItemStack stack() { return stack; }
        public int section() { return section; }
        public int x() { return x; }
        public int y() { return y; }
        public boolean rotated() { return rotated; }
        public boolean locked() { return locked; }
        void setStack(ItemStack stack) { this.stack = stack; }
    }

    /** A free place for a stack. */
    public record Spot(int section, int x, int y, boolean rotated) {}

    /** Cells kept free for the stack in the hand (Loadout): nothing is placed over them until it comes back. */
    public record Reserved(int section, int x, int y, int w, int h) {}

    private final GridLayout layout;
    private final boolean client;
    private final List<Entry> entries = new ArrayList<>();
    private final List<ItemStack> overflow = new ArrayList<>();
    @Nullable private Reserved reserved;

    public GridStorage(GridLayout layout, boolean client) {
        this.layout = layout;
        this.client = client;
    }

    public GridLayout layout() { return layout; }

    public List<Entry> entries() { return Collections.unmodifiableList(entries); }

    /** Stacks that fit nowhere after a layout or size change: shown, take-only. */
    public List<ItemStack> overflow() { return Collections.unmodifiableList(overflow); }

    public boolean isEmpty() { return entries.isEmpty() && overflow.isEmpty(); }

    /** The cells kept for the hand's stack, or null. Not saved: the owner's Loadout sets it on every view. */
    @Nullable
    public Reserved reserved() { return reserved; }

    public void reserve(@Nullable Reserved reserved) { this.reserved = reserved; }

    public ItemSizes.Size footprint(ItemStack stack, boolean rotated) {
        return ItemSizes.of(stack, client).turned(rotated);
    }

    public ItemSizes.Size footprint(Entry entry) { return entry.fixed != null ? entry.fixed : footprint(entry.stack, entry.rotated); }

    /** The entry covering this cell, if any. */
    @Nullable
    public Entry at(int section, int x, int y) {
        for (Entry e : entries) {
            if (e.section != section) continue;
            var s = footprint(e);
            if (x >= e.x && x < e.x + s.w() && y >= e.y && y < e.y + s.h()) return e;
        }
        return null;
    }

    /**
     * Whether the stack can lie with its top-left cell here: inside the section, over no entry except {@code ignore}, and
     * not over the reserved cells.
     */
    public boolean fits(ItemStack stack, int section, int x, int y, boolean rotated, @Nullable Entry ignore) {
        if (stack.isEmpty() || section < 0 || section >= layout.size()) return false;
        var sec = layout.section(section);
        var s = footprint(stack, rotated);
        if (x < 0 || y < 0 || x + s.w() > sec.w() || y + s.h() > sec.h()) return false;
        var r = reserved;
        if (r != null && r.section() == section && x < r.x() + r.w() && r.x() < x + s.w() && y < r.y() + r.h() && r.y() < y + s.h())
            return false;
        for (Entry e : entries) {
            if (e == ignore || e.section != section) continue;
            var o = footprint(e);
            if (x < e.x + o.w() && e.x < x + s.w() && y < e.y + o.h() && e.y < y + s.h()) return false;
        }
        return true;
    }

    /** First free place: sections in order, row by row, unrotated before rotated. */
    @Nullable
    public Spot findSpot(ItemStack stack) {
        var size = ItemSizes.of(stack, client);
        for (int section = 0; section < layout.size(); section++) {
            var sec = layout.section(section);
            for (int y = 0; y < sec.h(); y++)
                for (int x = 0; x < sec.w(); x++) {
                    if (fits(stack, section, x, y, false, null)) return new Spot(section, x, y, false);
                    if (size.w() != size.h() && fits(stack, section, x, y, true, null)) return new Spot(section, x, y, true);
                }
        }
        return null;
    }

    /** Puts the stack (owned by the grid from now on) at a place the caller checked with {@link #fits}. */
    public Entry place(ItemStack stack, int section, int x, int y, boolean rotated) {
        var e = new Entry(stack, section, x, y, rotated);
        entries.add(e);
        return e;
    }

    /**
     * Puts an entry whose cells are known without asking ItemSizes ({@code size} as it lies, turned already), locked or
     * not: a searchable container's contents as its menu sent them.
     */
    public Entry place(ItemStack stack, int section, int x, int y, boolean rotated, ItemSizes.Size size, boolean locked) {
        var e = place(stack, section, x, y, rotated);
        e.fixed = size;
        e.locked = locked;
        return e;
    }

    /** Locks an entry (an item not searched yet): see {@link Entry}. */
    public void lock(Entry entry) { entry.locked = true; }

    public void remove(Entry entry) { entries.remove(entry); }

    /** Takes an overflow stack out of the grid. */
    public ItemStack takeOverflow(int index) {
        return index >= 0 && index < overflow.size() ? overflow.remove(index) : ItemStack.EMPTY;
    }

    /**
     * As much of the stack as fits: first onto stacks of the same item, then into free places. The argument shrinks by
     * what went in; returns whether anything did.
     */
    public boolean insert(ItemStack stack) {
        int before = stack.getCount();
        for (Entry e : entries) {
            if (stack.isEmpty()) break;
            if (e.locked || !ItemStack.isSameItemSameTags(e.stack, stack)) continue;
            int room = Math.min(e.stack.getMaxStackSize(), 64) - e.stack.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, stack.getCount());
            e.stack.grow(moved);
            stack.shrink(moved);
        }
        while (!stack.isEmpty()) {
            var spot = findSpot(stack);
            if (spot == null) break;
            int moved = Math.min(stack.getMaxStackSize(), stack.getCount());
            place(stack.split(moved), spot.section(), spot.x(), spot.y(), spot.rotated());
        }
        return stack.getCount() < before;
    }

    /** Drops entries whose stacks went empty (someone used them up). */
    public void prune() {
        entries.removeIf(e -> !e.locked && e.stack.isEmpty());
        overflow.removeIf(ItemStack::isEmpty);
    }

    /** Every stack, entries then overflow, live. */
    public List<ItemStack> stacks() {
        List<ItemStack> out = new ArrayList<>(entries.size() + overflow.size());
        for (Entry e : entries) out.add(e.stack);
        out.addAll(overflow);
        return out;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        int slot = 0;
        for (Entry e : entries) {
            if (e.stack.isEmpty()) continue;
            CompoundTag tag = e.stack.save(new CompoundTag());
            tag.putByte("Slot", (byte) slot++);
            tag.putByte("GridSec", (byte) e.section);
            tag.putByte("GridX", (byte) e.x);
            tag.putByte("GridY", (byte) e.y);
            if (e.rotated) tag.putBoolean("GridRot", true);
            list.add(tag);
        }
        for (ItemStack stack : overflow) {
            if (stack.isEmpty()) continue;
            CompoundTag tag = stack.save(new CompoundTag());
            tag.putByte("Slot", (byte) slot++);
            tag.putBoolean("GridOverflow", true);
            list.add(tag);
        }
        return list;
    }

    public static GridStorage load(GridLayout layout, @Nullable ListTag list, boolean client) {
        GridStorage storage = new GridStorage(layout, client);
        if (list == null) return storage;
        List<ItemStack> homeless = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty()) continue;
            if (tag.getBoolean("GridOverflow") || !tag.contains("GridX", Tag.TAG_ANY_NUMERIC)) { homeless.add(stack); continue; }
            int section = tag.getByte("GridSec"), x = tag.getByte("GridX"), y = tag.getByte("GridY");
            boolean rotated = tag.getBoolean("GridRot");
            if (storage.fits(stack, section, x, y, rotated, null)) storage.place(stack, section, x, y, rotated);
            else homeless.add(stack);
        }
        for (ItemStack stack : homeless) {
            var spot = storage.findSpot(stack);
            if (spot != null) storage.place(stack, spot.section(), spot.x(), spot.y(), spot.rotated());
            else storage.overflow.add(stack);
        }
        return storage;
    }
}
