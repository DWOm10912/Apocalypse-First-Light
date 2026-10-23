package com.antaurora.apofirstlight.inspect;

import com.antaurora.apofirstlight.inventory.CarriedItems;
import com.antaurora.apofirstlight.inventory.grid.GridSource;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.item.Battery9vItem;
import com.antaurora.apofirstlight.item.GeigerCounterItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item inspect V1 (docs/gameplay/item_inspect_v1_design.md 4): what a hotspot click does, decided by the server. The
 * item is named by where it is ({@link #HAND}, a grid entry as {@link GridSource}, or an inventory-menu slot index), and
 * so is the battery a fit takes. Nothing is made or lost: a fit takes one battery from where it was into the counter's
 * tag in the same step, a removal puts the battery from the tag back into the grids, the inventory or at the feet.
 */
public final class InspectActions {
    /** The item in the main hand. */
    public static final int HAND = -1;

    public enum Action {
        SWITCH(0.2), OPEN_COVER(0.5), CLOSE_COVER(0.5), REMOVE_BATTERY(0.8), FIT_BATTERY(0.8);

        /** How long the inspect screen plays it (s). */
        public final double seconds;

        Action(double seconds) { this.seconds = seconds; }

        @Nullable
        public static Action of(int ordinal) {
            var all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    private InspectActions() {}

    /** The stack at a place, live (read only on the client; null when the place is unknown or empty there). */
    @Nullable
    public static ItemStack at(Player player, int source) {
        if (source == HAND) return player.getMainHandItem();
        if (GridSource.is(source)) {
            var cell = GridSource.cell(source);
            if (cell == null) return null;
            var storage = PlayerGrids.view(player).get(cell.target());
            var entry = storage == null ? null : entry(storage, cell);
            return entry == null ? null : entry.stack();
        }
        return source > 0 && source < player.inventoryMenu.slots.size() ? player.inventoryMenu.getSlot(source).getItem() : null;
    }

    @Nullable
    private static GridStorage.Entry entry(GridStorage storage, GridSource.Cell cell) {
        if (cell.section() >= storage.layout().size()) return null;
        var entry = storage.at(cell.section(), cell.x(), cell.y());
        return entry == null || entry.x() != cell.x() || entry.y() != cell.y() ? null : entry;
    }

    /**
     * One transaction over the player's places: reads hand out copies, writes put them back (a grid entry's stack is
     * changed in place in one shared view, committed once).
     */
    private static final class Places {
        private final ServerPlayer player;
        private PlayerGrids.View view;
        private boolean gridChanged;

        Places(ServerPlayer player) { this.player = player; }

        @Nullable
        ItemStack read(int source) {
            if (source == HAND) return player.getMainHandItem().copy();
            if (GridSource.is(source)) {
                var entry = gridEntry(source);
                return entry == null ? null : entry.stack().copy();
            }
            // menu slot 0 is the crafting result
            return source > 0 && source < player.inventoryMenu.slots.size() ? player.inventoryMenu.getSlot(source).getItem().copy() : null;
        }

        void write(int source, ItemStack stack) {
            if (source == HAND) { player.setItemInHand(InteractionHand.MAIN_HAND, stack); return; }
            if (GridSource.is(source)) {
                var entry = gridEntry(source);
                if (entry == null) return;
                entry.stack().setTag(stack.getTag() == null ? null : stack.getTag().copy());
                entry.stack().setCount(stack.getCount());
                gridChanged = true;
                return;
            }
            var slot = player.inventoryMenu.getSlot(source);
            slot.set(stack);
            slot.setChanged();
        }

        @Nullable
        private GridStorage.Entry gridEntry(int source) {
            var cell = GridSource.cell(source);
            if (cell == null) return null;
            if (view == null) view = PlayerGrids.view(player);
            var storage = view.get(cell.target());
            return storage == null ? null : entry(storage, cell);
        }

        void commit() {
            if (gridChanged) view.commit();
        }
    }

    /** Applies one click; false when the item or the battery is no longer what the screen saw (nothing changes then). */
    public static boolean apply(ServerPlayer player, int source, Action action, int candidate) {
        if (!player.isAlive() || player.isSpectator()) return false;
        var places = new Places(player);
        ItemStack geiger = places.read(source);
        if (geiger == null || !(geiger.getItem() instanceof GeigerCounterItem) || geiger.getCount() != 1) return false;
        long now = player.level().getGameTime();
        boolean open = GeigerCounterItem.coverOpen(geiger);
        ItemStack removed = ItemStack.EMPTY;
        switch (action) {
            case SWITCH -> {
                var note = GeigerCounterItem.toggle(geiger, now);
                GeigerCounterItem.switchSound(player);
                if (note != null) player.displayClientMessage(note, true);
            }
            case OPEN_COVER, CLOSE_COVER -> {
                if (open == (action == Action.OPEN_COVER)) return false;
                GeigerCounterItem.settle(geiger, now);
                GeigerCounterItem.setCover(geiger, action == Action.OPEN_COVER);
            }
            case REMOVE_BATTERY -> {
                if (!open || GeigerCounterItem.battery(geiger).isEmpty()) return false;
                removed = GeigerCounterItem.remove(geiger, now);
            }
            case FIT_BATTERY -> {
                if (!open || !GeigerCounterItem.battery(geiger).isEmpty() || candidate == source) return false;
                ItemStack battery = places.read(candidate);
                if (battery == null || Battery9vItem.kind(battery) == null || battery.isEmpty()) return false;
                GeigerCounterItem.fit(geiger, battery.copyWithCount(1), now);
                battery.shrink(1);
                places.write(candidate, battery);
            }
        }
        places.write(source, geiger);
        places.commit();
        if (!removed.isEmpty()) CarriedItems.give(player, removed);
        return true;
    }
}
