package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server: the grid clicks of the inventory page and of the grid side panel on container screens
 * (docs/ui/afl_inventory_grid_v0_plan.md 5, 10, 13). The client only asks; every action is checked against the storages
 * as the server has them and done here, with the open menu's carried stack as the cursor (the vanilla menu syncs it
 * back). Grid gear never goes into a grid.
 */
public final class GridActions {
    /** What a {@link GridPackets.Action} asks for. */
    public enum Kind {
        /** Take the entry at a cell onto the cursor: button 0 all, 1 half. */
        PICK,
        /** Put the cursor stack with its top-left cell here (or swap it with the one entry it covers): button 0 all, 1 one. */
        PLACE,
        /** Put the cursor stack onto the entry at a cell (same item): button 0 all that fits, 1 one. */
        MERGE,
        /** Shift-click: the weapon at a cell to the empty weapon position that takes it. */
        QUICK,
        /** Shift-click on a player inventory slot ({@code extra}: its inventory index): into the grids. */
        QUICK_FROM_SLOT,
        /** Q on the entry at a cell: {@code extra} 1 the whole stack, 0 one item. */
        DROP,
        /** Keys 1-3 on the entry at a cell ({@code extra}: hotbar index 0..2): swap with that weapon position. */
        HOTBAR_KEY,
        /** Take overflow stack {@code extra} onto the cursor. */
        OVERFLOW_TAKE,
        /** Keys 4-9 on a pockets / rig entry ({@code extra}: 0..5): bind its kind to that key (Loadout). */
        BIND,
        /** Right-click on a binding ({@code extra}: 0..5): unbind it. */
        UNBIND,
        /**
         * Fit the sling on the cursor to the long gun at a cell, or take the gun's sling off onto an empty cursor; target
         * {@link #SLOT_TARGET}: the gun in hotbar index {@code extra} instead.
         */
        SLING,
        /** Ground items (GroundItems): the item entity {@code extra} onto the empty cursor, {@code x} 0 all, 1 half. */
        GROUND_PICK,
        /** The item entity {@code extra} straight in: {@code x} GroundItems.AUTO (normal pickup order) or STOW (grids only). */
        GROUND_QUICK,
        /** The cursor stack onto the ground at the feet: {@code x} 0 all, 1 one. */
        GROUND_DROP,
        /** Shift-click on a grid entry with a container open: into the container, by the menu's own shift-click rules. */
        TO_CONTAINER;

        static Kind of(int ordinal) {
            var all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    /** Action target meaning "a hotbar index" ({@code extra}) rather than a grid storage. */
    public static final int SLOT_TARGET = 15;

    private GridActions() {}

    /** While a grid entry is shift-clicked into a container: the menu's leftover must not go back into the grids on its own. */
    private static final ThreadLocal<Boolean> INTO_CONTAINER = ThreadLocal.withInitial(() -> false);

    /** A grid entry is being moved into a container (LockedInventoryMenuMixin leaves the leftover to {@link #toContainer}). */
    public static boolean movingIntoContainer() { return INTO_CONTAINER.get(); }

    /**
     * The grids work in this menu: the inventory page, or any open container whose menu shows the player's inventory
     * (chests, furnaces, AFL machines, searchable containers...), where the menu's carried stack is the cursor.
     */
    public static boolean gridMenu(Player player, AbstractContainerMenu menu) {
        if (menu == player.inventoryMenu) return true;
        if (!menu.stillValid(player)) return false;
        for (Slot slot : menu.slots)
            if (slot.container == player.getInventory()) return true;
        return false;
    }

    /** Server: one grid action (checked here; GameTests call it directly). */
    public static void handle(ServerPlayer player, GridPackets.Action action) {
        if (!player.isAlive() || PlayerStorageCapacity.unrestricted(player)) return;
        var menu = player.containerMenu;
        if (!gridMenu(player, menu)) return;
        Kind kind = Kind.of(action.kind());
        if (kind == null) return;
        // ground items only from the inventory page; into a container only with one open
        boolean ground = kind == Kind.GROUND_PICK || kind == Kind.GROUND_QUICK || kind == Kind.GROUND_DROP;
        if (ground && menu != player.inventoryMenu) return;
        if (kind == Kind.TO_CONTAINER && menu == player.inventoryMenu) return;
        var loadout = Loadout.of(player);
        if (loadout != null) loadout.reconcileHand(player);
        if (kind == Kind.UNBIND) {
            Loadout.unbind(player, action.extra());
            return;
        }
        var view = PlayerGrids.view(player);
        boolean changed;
        if (ground) {
            changed = switch (kind) {
                case GROUND_PICK -> GroundItems.pick(player, action.extra(), action.x() == 1);
                case GROUND_QUICK -> GroundItems.quick(player, action.extra(), action.x());
                default -> GroundItems.drop(player, action.x() == 1);
            };
        } else if (kind == Kind.TO_CONTAINER) {
            changed = toContainer(player, menu, action);
        } else if (kind == Kind.QUICK_FROM_SLOT) {
            changed = quickFromSlot(player, action.extra());
        } else if (kind == Kind.SLING && action.target() == SLOT_TARGET) {
            int index = action.extra();
            changed = index >= 0 && index < HotbarRoles.USED && sling(player, player.getInventory().items.get(index));
        } else {
            var target = PlayerGrids.Target.of(action.target());
            var storage = target == null ? null : view.get(target);
            if (storage == null) return;
            changed = switch (kind) {
                case PICK -> pick(player, storage, action);
                case PLACE -> place(player, storage, action);
                case MERGE -> merge(player, storage, action);
                case QUICK -> quick(player, storage, action);
                case DROP -> drop(player, storage, action);
                case HOTBAR_KEY -> hotbarKey(player, storage, action);
                case OVERFLOW_TAKE -> overflowTake(player, storage, action);
                case BIND -> {
                    var entry = storage.at(action.section(), action.x(), action.y());
                    if (entry != null && target != PlayerGrids.Target.BACKPACK) Loadout.bind(player, action.extra(), entry.stack());
                    yield false;
                }
                case SLING -> {
                    var entry = storage.at(action.section(), action.x(), action.y());
                    yield entry != null && sling(player, entry.stack());
                }
                default -> false;
            };
            if (changed) view.commit();
        }
        if (changed) {
            player.getInventory().setChanged();
            menu.broadcastChanges();
        }
    }

    private static boolean pick(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        var menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) return false;
        var entry = storage.at(a.section(), a.x(), a.y());
        if (entry == null) return false;
        var target = PlayerGrids.Target.of(a.target());
        if (target != null) Loadout.recordPick(player, target, storage, entry);
        var stack = entry.stack();
        if (a.extra() == 1 && stack.getCount() > 1) {
            menu.setCarried(stack.split((stack.getCount() + 1) / 2));
        } else {
            menu.setCarried(stack.copy());
            stack.setCount(0);
        }
        storage.prune();
        return true;
    }

    private static boolean place(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        var menu = player.containerMenu;
        var carried = menu.getCarried();
        if (!PlayerGrids.allowedInGrid(carried)) return false;
        if (storage.fits(carried, a.section(), a.x(), a.y(), a.rotated(), null)) {
            int moved = a.extra() == 1 ? 1 : Math.min(carried.getCount(), carried.getMaxStackSize());
            storage.place(carried.split(moved), a.section(), a.x(), a.y(), a.rotated());
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }
        if (a.extra() != 0) return false;
        // swap: the cursor stack covers exactly one entry, and fits where it is once that entry is out
        GridStorage.Entry covered = null;
        var size = storage.footprint(carried, a.rotated());
        for (int y = a.y(); y < a.y() + size.h(); y++)
            for (int x = a.x(); x < a.x() + size.w(); x++) {
                var e = storage.at(a.section(), x, y);
                if (e == null || e == covered) continue;
                if (covered != null) return false;
                covered = e;
            }
        if (covered == null || !storage.fits(carried, a.section(), a.x(), a.y(), a.rotated(), covered)) return false;
        if (carried.getCount() > carried.getMaxStackSize()) return false;
        storage.remove(covered);
        storage.place(carried, a.section(), a.x(), a.y(), a.rotated());
        menu.setCarried(covered.stack());
        return true;
    }

    private static boolean merge(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        var menu = player.containerMenu;
        var carried = menu.getCarried();
        var entry = storage.at(a.section(), a.x(), a.y());
        if (entry == null || carried.isEmpty() || !ItemStack.isSameItemSameTags(entry.stack(), carried)) return false;
        int room = Math.min(entry.stack().getMaxStackSize(), 64) - entry.stack().getCount();
        int moved = Math.min(room, a.extra() == 1 ? 1 : carried.getCount());
        if (moved <= 0) return false;
        entry.stack().grow(moved);
        carried.shrink(moved);
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        return true;
    }

    private static boolean quick(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        var entry = storage.at(a.section(), a.x(), a.y());
        if (entry == null) return false;
        var stack = entry.stack();
        int before = stack.getCount();
        toHotbar(player.getInventory(), stack);
        if (stack.getCount() == before) return false;
        storage.prune();
        return true;
    }

    /** A weapon into the empty weapon position that takes it (keys 1-3); anything else stays. */
    static void toHotbar(Inventory inventory, ItemStack stack) {
        for (int i = 0; i < HotbarRoles.HAND_SLOT && !stack.isEmpty(); i++) {
            var there = inventory.items.get(i);
            if (there.isEmpty() || !ItemStack.isSameItemSameTags(there, stack) || PlayerStorageCapacity.rejects(inventory, i, stack)) continue;
            int moved = Math.min(Math.min(there.getMaxStackSize(), inventory.getMaxStackSize()) - there.getCount(), stack.getCount());
            if (moved <= 0) continue;
            there.grow(moved);
            stack.shrink(moved);
        }
        for (int i = 0; i < HotbarRoles.HAND_SLOT && !stack.isEmpty(); i++) {
            if (!inventory.items.get(i).isEmpty() || PlayerStorageCapacity.rejects(inventory, i, stack)) continue;
            int moved = Math.min(stack.getMaxStackSize(), stack.getCount());
            inventory.items.set(i, stack.split(moved));
        }
    }

    /**
     * Shift-click on a grid entry with a container open: the stack goes in by the menu's own shift-click rules (a furnace
     * takes fuel and what smelts, a searchable container only its revealed slots). It is laid in an empty main
     * inventory slot for the menu to move; whatever the menu leaves there or puts elsewhere in the player's inventory
     * comes back to the entry's place.
     */
    private static boolean toContainer(ServerPlayer player, AbstractContainerMenu menu, GridPackets.Action a) {
        var target = PlayerGrids.Target.of(a.target());
        var view = PlayerGrids.view(player);
        var storage = target == null ? null : view.get(target);
        var entry = storage == null ? null : storage.at(a.section(), a.x(), a.y());
        if (entry == null) return false;
        var inventory = player.getInventory();
        Slot carrier = null;
        for (Slot slot : menu.slots) {
            int index = slot.getContainerSlot();
            if (slot.container == inventory && index >= Inventory.getSelectionSize() && index < Inventory.INVENTORY_SIZE
                    && inventory.items.get(index).isEmpty()) {
                carrier = slot;
                break;
            }
        }
        if (carrier == null) return false;
        ItemStack[] before = new ItemStack[inventory.getContainerSize()];
        for (int i = 0; i < before.length; i++) before[i] = inventory.getItem(i).copy();
        int section = entry.section(), x = entry.x(), y = entry.y(), count = entry.stack().getCount();
        boolean turned = entry.rotated();
        var stack = entry.stack().copy();
        storage.remove(entry);
        view.commit();
        int index = carrier.getContainerSlot();
        inventory.items.set(index, stack.copy());   // the menu splits what it moves; stack stays the pattern
        INTO_CONTAINER.set(true);
        try {
            // as vanilla's shift-click: again while the menu moves some and the same item is left
            for (int i = 0; i < 64; i++) {
                var moved = menu.quickMoveStack(player, carrier.index);
                if (moved.isEmpty() || !ItemStack.isSameItem(carrier.getItem(), moved)) break;
            }
        } finally {
            INTO_CONTAINER.set(false);
        }
        var left = inventory.items.get(index);
        int rest = left.isEmpty() ? 0 : left.getCount();
        inventory.items.set(index, ItemStack.EMPTY);
        // nothing stays in the player's other slots: what the menu moved there comes back with the rest
        for (int i = 0; i < before.length; i++) {
            if (i == index) continue;
            var now = inventory.getItem(i);
            if (ItemStack.matches(now, before[i]) || !ItemStack.isSameItemSameTags(now, stack)) continue;
            int grown = now.getCount() - (before[i].isEmpty() ? 0 : before[i].getCount());
            if (grown <= 0) continue;
            now.shrink(grown);
            if (now.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
            rest += grown;
        }
        if (rest > 0) {
            var back = stack.copyWithCount(rest);
            var grids = PlayerGrids.view(player);
            var there = grids.get(target);
            // place keeps the stack it is given without shrinking it: hand over all of back, or the drop below doubles it
            if (there != null && there.fits(back, section, x, y, turned, null)) there.place(back.split(back.getCount()), section, x, y, turned);
            else grids.insert(back);
            grids.commit();
            if (!back.isEmpty()) player.drop(back, false);
        }
        return rest < count;
    }

    private static boolean quickFromSlot(ServerPlayer player, int index) {
        var inventory = player.getInventory();
        if (index < 0 || index >= inventory.getContainerSize()) return false;
        var stack = inventory.getItem(index);
        if (stack.isEmpty()) return false;
        if (!PlayerGrids.insert(player, stack)) return false;
        if (stack.isEmpty()) inventory.setItem(index, ItemStack.EMPTY);
        return true;
    }

    private static boolean drop(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        var entry = storage.at(a.section(), a.x(), a.y());
        if (entry == null) return false;
        var stack = entry.stack();
        var out = a.extra() == 1 ? stack.copy() : stack.copyWithCount(1);
        stack.shrink(out.getCount());
        storage.prune();
        player.drop(out, true);
        return true;
    }

    private static boolean hotbarKey(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        int index = a.extra();
        if (index < 0 || index >= HotbarRoles.HAND_SLOT) return false;   // keys 1-3: the weapon positions
        var inventory = player.getInventory();
        var entry = storage.at(a.section(), a.x(), a.y());
        if (entry == null) return false;
        var moving = entry.stack();
        if (moving.getCount() > moving.getMaxStackSize() || PlayerStorageCapacity.rejects(inventory, index, moving)) return false;
        var there = inventory.items.get(index);
        if (!there.isEmpty()) {
            if (!PlayerGrids.allowedInGrid(there)) return false;
            // the hotbar stack takes the entry's place, either way round, or the first free place
            int section = entry.section(), x = entry.x(), y = entry.y();
            storage.remove(entry);
            if (storage.fits(there, section, x, y, false, null)) storage.place(there, section, x, y, false);
            else if (storage.fits(there, section, x, y, true, null)) storage.place(there, section, x, y, true);
            else {
                var spot = storage.findSpot(there);
                if (spot == null) {
                    storage.place(moving, section, x, y, entry.rotated());
                    return false;
                }
                storage.place(there, spot.section(), spot.x(), spot.y(), spot.rotated());
            }
        } else {
            storage.remove(entry);
        }
        inventory.items.set(index, moving);
        return true;
    }

    /** Sling on the cursor onto a long gun without one, or the gun's sling onto an empty cursor. */
    private static boolean sling(ServerPlayer player, ItemStack gun) {
        var menu = player.containerMenu;
        var carried = menu.getCarried();
        if (GunSling.isSling(carried) && GunSling.canFit(gun)) {
            GunSling.fit(gun);
            carried.shrink(1);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }
        if (carried.isEmpty() && GunSling.has(gun)) {
            menu.setCarried(GunSling.remove(gun));
            return true;
        }
        return false;
    }

    private static boolean overflowTake(ServerPlayer player, GridStorage storage, GridPackets.Action a) {
        var menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) return false;
        var stack = storage.takeOverflow(a.extra());
        if (stack.isEmpty()) return false;
        menu.setCarried(stack);
        return true;
    }
}
