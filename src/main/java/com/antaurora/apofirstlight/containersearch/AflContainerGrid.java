package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A searchable container as a grid (docs/ui/afl_container_search_page_v1.md 12, user 2026-10-27: "容器分区改成网格，未搜出的
 * 东西显示暗色轮廓"). The container stays a plain Container of one slot per cell of its {@link AflContainerSearchLayout}
 * (so saving, loot tables, hoppers, comparators and the goods keep working): an item's stack sits on the slot of its
 * top-left cell, the cells it covers stay empty, and the search state keeps which items lie turned. Everything that
 * moves items as a grid goes through {@link #view} and {@link #commit}: the grid actions of the search page
 * (GridActions.CONTAINER_TARGET), shift-clicks into it, the packing after the loot is rolled.
 * <p>An item not searched yet is a locked entry: it takes its cells and nothing touches it.
 */
public final class AflContainerGrid {
    /** Footprint byte of a slot in the menu data: w - 1 (bits 0-2), h - 1 (3-5) as it lies, turned (6), an item here (7). */
    public static final int PRESENT = 0x80, TURNED = 0x40;

    /**
     * While {@link #commit} writes the slots: a block entity's setItem unpacks its loot table first, which ends in
     * {@link #pack}; that must not run on a half-written grid.
     */
    private static final ThreadLocal<Boolean> WRITING = ThreadLocal.withInitial(() -> false);

    private AflContainerGrid() {
    }

    /** Whether the container is laid out as a grid of its own size (the menu refuses to open otherwise). */
    static boolean gridded(AflSearchableContainer container) {
        var layout = container.aflSearchLayout();
        return layout != null && layout.size() == container.getContainerSize();
    }

    /**
     * Server: the container's items as a grid storage, live (the entries hold the container's own stacks): each at its
     * anchor cell, turned as the state says, locked while not searched. Change it, then {@link #commit} it.
     */
    public static GridStorage view(AflSearchableContainer container) {
        var layout = container.aflSearchLayout();
        var state = container.aflSearchState();
        var storage = new GridStorage(layout.grid(), false);
        for (int slot = 0; slot < layout.size(); slot++) {
            var stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            var entry = storage.place(stack, layout.sectionOf(slot), layout.cellX(slot), layout.cellY(slot), state.isTurned(slot));
            if (!state.isRevealed(container, slot)) storage.lock(entry);
        }
        return storage;
    }

    /**
     * Server: writes a changed {@link #view} back: every entry's stack onto its anchor slot with its turn, the rest of the
     * slots empty. A locked entry keeps its slot hidden; every other slot is open (an item put or moved there was seen).
     */
    public static void commit(AflSearchableContainer container, GridStorage storage) {
        var layout = container.aflSearchLayout();
        var state = container.aflSearchState();
        int size = layout.size();
        ItemStack[] next = new ItemStack[size];
        Arrays.fill(next, ItemStack.EMPTY);
        boolean[] turned = new boolean[size], locked = new boolean[size];
        for (var entry : storage.entries()) {
            if (entry.stack().isEmpty()) continue;
            int slot = layout.slot(entry.section(), entry.x(), entry.y());
            next[slot] = entry.stack();
            turned[slot] = entry.rotated();
            locked[slot] = entry.locked();
        }
        boolean outer = !WRITING.get();
        WRITING.set(true);
        try {
            for (int slot = 0; slot < size; slot++) {
                if (container.getItem(slot) != next[slot]) container.setItem(slot, next[slot]);
                state.setTurned(slot, turned[slot]);
                state.setRevealed(container, slot, !locked[slot]);
            }
        } finally {
            if (outer) WRITING.set(false);
        }
        container.setChanged();
    }

    /**
     * Server: as much of the stack as fits goes in (onto searched stacks of the same item, then into free places, turned
     * if it must); the argument shrinks by what went in. Returns whether anything did.
     */
    public static boolean insert(AflSearchableContainer container, ItemStack stack) {
        if (stack.isEmpty() || !gridded(container)) return false;
        var storage = view(container);
        if (!storage.insert(stack)) return false;
        commit(container, storage);
        return true;
    }

    /**
     * For the container's {@code canPlaceItem} (hoppers, vanilla slot clicks): a stack goes onto a slot only as the grid
     * allows, onto the same item there, or into an empty anchor whose cells it fits, turned as the slot last was.
     */
    public static boolean canPlace(AflSearchableContainer container, int slot, ItemStack stack) {
        if (!gridded(container) || slot < 0 || slot >= container.getContainerSize()) return true;
        var there = container.getItem(slot);
        if (!there.isEmpty()) return ItemStack.isSameItemSameTags(there, stack);
        var layout = container.aflSearchLayout();
        return view(container).fits(stack, layout.sectionOf(slot), layout.cellX(slot), layout.cellY(slot),
                container.aflSearchState().isTurned(slot), null);
    }

    /** The search time of the item on a slot, in base slot times: 0.75 + 0.25 per cell (AflContainerSearchSpeed). */
    static double searchWeight(AflSearchableContainer container, int slot) {
        var stack = container.getItem(slot);
        if (stack.isEmpty()) return 1;
        var size = ItemSizes.of(stack, false);
        return 0.75 + 0.25 * size.w() * size.h();
    }

    /** The menu's footprint byte of a slot (see {@link #PRESENT}); 0 for no item. */
    static int footprint(AflSearchableContainer container, int slot) {
        var stack = container.getItem(slot);
        if (stack.isEmpty()) return 0;
        boolean turned = container.aflSearchState().isTurned(slot);
        var size = ItemSizes.of(stack, false).turned(turned);
        return PRESENT | (turned ? TURNED : 0) | (size.h() - 1) << 3 | (size.w() - 1);
    }

    /** Cells of a footprint byte, as the item lies. */
    public static ItemSizes.Size size(int footprint) {
        return new ItemSizes.Size((footprint & 7) + 1, (footprint >> 3 & 7) + 1);
    }

    /**
     * Server: makes the container a grid it can show. Items not searched yet merge into one stack per item where they
     * fit together (a loot table spreads a stack over several slots); every item that lies where its cells fit stays;
     * the others move to the first free place, the larger first, turned if they must. What fits nowhere: unsearched loot
     * is gone, a searched item goes to {@link AflSearchableContainer#aflGridOverflow}. Run after the loot is rolled and
     * before every menu opens (old worlds, other item sizes after a data change). Nothing happens to a grid that is
     * fine as it is.
     */
    public static void pack(AflSearchableContainer container) {
        if (WRITING.get() || !gridded(container)) return;
        var state = container.aflSearchState();
        if (!state.ensureInitialized(container)) return;
        var layout = container.aflSearchLayout();
        var storage = new GridStorage(layout.grid(), false);
        record Loose(ItemStack stack, boolean hidden, boolean turned) {}
        List<Loose> loose = new ArrayList<>();
        boolean moved = false;
        for (int slot = 0; slot < layout.size(); slot++) {
            var stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            boolean hidden = !state.isRevealed(container, slot), turned = state.isTurned(slot);
            if (hidden && mergeHidden(storage, stack)) {
                moved = true;
                if (stack.isEmpty()) continue;
            }
            int section = layout.sectionOf(slot), x = layout.cellX(slot), y = layout.cellY(slot);
            if (storage.fits(stack, section, x, y, turned, null)) {
                var entry = storage.place(stack, section, x, y, turned);
                if (hidden) storage.lock(entry);
            } else {
                loose.add(new Loose(stack, hidden, turned));
                moved = true;
            }
        }
        if (!moved) return;
        loose.sort(Comparator.comparingInt((Loose l) -> {
            var size = ItemSizes.of(l.stack(), false);
            return size.w() * size.h();
        }).reversed());
        List<ItemStack> spill = new ArrayList<>();
        for (Loose l : loose) {
            var stack = l.stack();
            if (l.hidden() && mergeHidden(storage, stack) && stack.isEmpty()) continue;
            var spot = storage.findSpot(stack);
            if (spot == null) {
                if (!l.hidden()) spill.add(stack);
                continue;
            }
            var entry = storage.place(stack, spot.section(), spot.x(), spot.y(), spot.rotated());
            if (l.hidden()) storage.lock(entry);
        }
        commit(container, storage);
        if (!spill.isEmpty()) container.aflGridOverflow(spill);
    }

    /**
     * Server: lays stacks in as unsearched items (a body filled at death): the larger first, each in the first free
     * place, turned if it must; returns the stacks with no place (whole). Nothing merges.
     */
    public static List<ItemStack> fillHidden(AflSearchableContainer container, List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        if (!gridded(container)) {
            left.addAll(stacks);
            return left;
        }
        var storage = view(container);
        for (var stack : largestFirst(stacks)) {
            var spot = storage.findSpot(stack);
            if (spot == null) {
                left.add(stack);
                continue;
            }
            storage.lock(storage.place(stack, spot.section(), spot.x(), spot.y(), spot.rotated()));
        }
        commit(container, storage);
        return left;
    }

    /** Whether {@link #fillHidden} would find a place for every one of the stacks (nothing changes). */
    public static boolean fitsAll(AflSearchableContainer container, List<ItemStack> stacks) {
        if (!gridded(container)) return false;
        var storage = view(container);
        for (var stack : largestFirst(stacks)) {
            var spot = storage.findSpot(stack);
            if (spot == null) return false;
            storage.place(stack.copy(), spot.section(), spot.x(), spot.y(), spot.rotated());
        }
        return true;
    }

    private static List<ItemStack> largestFirst(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (var stack : stacks) if (!stack.isEmpty()) out.add(stack);
        out.sort(Comparator.comparingInt((ItemStack s) -> {
            var size = ItemSizes.of(s, false);
            return size.w() * size.h();
        }).reversed());
        return out;
    }

    /** A stack not searched yet onto the unsearched stacks of the same item already laid; returns whether any went. */
    private static boolean mergeHidden(GridStorage storage, ItemStack stack) {
        int before = stack.getCount();
        for (var entry : storage.entries()) {
            if (stack.isEmpty()) break;
            if (!entry.locked() || !ItemStack.isSameItemSameTags(entry.stack(), stack)) continue;
            int room = Math.min(entry.stack().getMaxStackSize(), 64) - entry.stack().getCount();
            int moved = Math.min(room, stack.getCount());
            if (moved <= 0) continue;
            entry.stack().grow(moved);
            stack.shrink(moved);
        }
        return stack.getCount() < before;
    }
}
