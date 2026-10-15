package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A player's grid storages (docs/ui/afl_inventory_grid_v0_plan.md 1): the pockets, and the grids of the worn chest rig
 * and backpack. Every change goes through a {@link View}: load, change the live stacks, {@link View#commit()}. Only the
 * server commits; the client reads its copies (pockets from GridPackets, gear from Curios' own sync) to draw them and
 * to count what is carried.
 */
public final class PlayerGrids {
    /** The storages in pickup order. */
    public enum Target {
        POCKETS(null), RIG(AflEquipmentSlots.RIG), BACKPACK(AflEquipmentSlots.BACK);

        @Nullable final String slot;

        Target(@Nullable String slot) { this.slot = slot; }

        public static Target of(int ordinal) {
            var all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    /** Client: the pockets as last sent by the server. */
    private static volatile ListTag clientPockets = new ListTag();
    /**
     * Client: the last storage read per target and the saved data it came from (compared by identity: a client copy is
     * replaced, never changed, when the server's changes arrive), so drawing and the HUD do not parse every frame.
     */
    private record Cached(Object source, net.minecraft.world.item.Item item, GridStorage storage) {}
    private static final Map<Target, Cached> CLIENT_CACHE = new EnumMap<>(Target.class);

    private PlayerGrids() {}

    static void receivePockets(ListTag items) { clientPockets = items; }

    public static void clearClient() {
        clientPockets = new ListTag();
        invalidateClientCache();
    }

    /** Client: sizes changed, so every cached layout of entries may be stale. */
    static void invalidateClientCache() {
        synchronized (CLIENT_CACHE) { CLIENT_CACHE.clear(); }
    }

    private static GridStorage clientRead(Target target, Object source, net.minecraft.world.item.Item item,
                                          java.util.function.Supplier<GridStorage> load) {
        synchronized (CLIENT_CACHE) {
            var cached = CLIENT_CACHE.get(target);
            if (cached != null && cached.source() == source && cached.item() == item) return cached.storage();
            var storage = load.get();
            CLIENT_CACHE.put(target, new Cached(source, item, storage));
            return storage;
        }
    }

    /** The worn gear stack of a target (the live Curios stack), if it is grid gear worn in its own slot. */
    public static ItemStack gear(Player player, Target target) {
        if (target.slot == null) return ItemStack.EMPTY;
        var stack = AflEquipmentSlots.get(player, target.slot);
        return stack.getItem() instanceof GridContainerItem gear && gear.wornSlot().equals(target.slot) ? stack : ItemStack.EMPTY;
    }

    /** Grid gear never goes into a grid (no containers in containers in V0). */
    public static boolean allowedInGrid(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof GridContainerItem);
    }

    /** The player's storages as they are now. */
    public static View view(Player player) {
        return new View(player);
    }

    /** Every stack in the player's grids (pockets, rig, backpack), live in a fresh view: for reading. */
    public static List<ItemStack> stacks(Player player) {
        return view(player).stacks();
    }

    /**
     * Server: as much of the stack as fits, pockets → rig → backpack, first onto stacks of the same item, then into the
     * first free places. The argument shrinks by what went in; returns whether anything did. Survival / Adventure only.
     */
    public static boolean insert(ServerPlayer player, ItemStack stack) {
        if (!allowedInGrid(stack) || PlayerStorageCapacity.unrestricted(player)) return false;
        var view = view(player);
        if (!view.insert(stack)) return false;
        view.commit();
        return true;
    }

    /**
     * Server: the grids as they are now (the pockets and the worn rig's and backpack's saved contents); running the result
     * puts them back, for transactions that must undo everything when a later step fails.
     */
    public static Runnable snapshot(ServerPlayer player) {
        var pockets = Pockets.of(player);
        var pocketItems = pockets == null ? null : pockets.items().copy();
        var gear = new ArrayList<ItemStack>();
        var tags = new ArrayList<net.minecraft.nbt.CompoundTag>();
        for (Target target : new Target[]{Target.RIG, Target.BACKPACK}) {
            var stack = gear(player, target);
            if (stack.isEmpty()) continue;
            gear.add(stack);
            tags.add(stack.getTag() == null ? null : stack.getTag().copy());
        }
        return () -> {
            if (pockets != null) pockets.set(pocketItems);
            for (int i = 0; i < gear.size(); i++) gear.get(i).setTag(tags.get(i));
            GridPackets.sendPockets(player);
        };
    }

    private static void mergeOnly(GridStorage storage, ItemStack stack) {
        for (var e : storage.entries()) {
            if (stack.isEmpty()) return;
            if (!ItemStack.isSameItemSameTags(e.stack(), stack)) continue;
            int room = Math.min(e.stack().getMaxStackSize(), 64) - e.stack().getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, stack.getCount());
            e.stack().grow(moved);
            stack.shrink(moved);
        }
    }

    /** One load of a player's storages. */
    public static final class View {
        private final Player player;
        private final boolean client;
        private final Map<Target, GridStorage> storages = new EnumMap<>(Target.class);
        private final Map<Target, ItemStack> gear = new EnumMap<>(Target.class);

        private View(Player player) {
            this.player = player;
            this.client = player.level().isClientSide;
            if (client) {
                ListTag pockets = clientPockets;
                storages.put(Target.POCKETS, clientRead(Target.POCKETS, pockets, null,
                        () -> GridStorage.load(Pockets.LAYOUT, pockets, true)));
            } else {
                var data = Pockets.of(player);
                if (data != null) storages.put(Target.POCKETS, GridStorage.load(Pockets.LAYOUT, data.items(), false));
            }
            for (Target target : new Target[]{Target.RIG, Target.BACKPACK}) {
                var stack = PlayerGrids.gear(player, target);
                if (stack.isEmpty()) continue;
                gear.put(target, stack);
                storages.put(target, client
                        ? clientRead(target, stack.getTag() == null ? stack : stack.getTag(), stack.getItem(), () -> GridContainerItem.read(stack, true))
                        : GridContainerItem.read(stack, false));
            }
            Loadout.applyReservation(player, storages);
        }

        /** The storage, or null when the player has none there (no rig / backpack worn). */
        @Nullable
        public GridStorage get(Target target) { return storages.get(target); }

        /**
         * As much of the stack as fits, pockets → rig → backpack: first onto stacks of the same item, then into the first
         * free places. The argument shrinks by what went in; nothing is saved until {@link #commit()}.
         */
        public boolean insert(ItemStack stack) {
            if (!allowedInGrid(stack)) return false;
            int before = stack.getCount();
            for (Target target : Target.values()) {
                var storage = storages.get(target);
                if (storage != null && !stack.isEmpty()) mergeOnly(storage, stack);
            }
            for (Target target : Target.values()) {
                var storage = storages.get(target);
                if (storage != null && !stack.isEmpty()) storage.insert(stack);
            }
            return stack.getCount() < before;
        }

        /** Free 1x1 cells over every storage (the recipe book's room for returned ingredients). */
        public int freeCells() {
            int free = 0;
            var probe = new ItemStack(net.minecraft.world.item.Items.STICK);
            for (var storage : storages.values())
                for (int i = 0; i < storage.layout().size(); i++) {
                    var section = storage.layout().section(i);
                    for (int y = 0; y < section.h(); y++)
                        for (int x = 0; x < section.w(); x++)
                            if (storage.at(i, x, y) == null && storage.fits(probe, i, x, y, false, null)) free++;
                }
            return free;
        }

        /** The worn gear stack behind a target (empty for the pockets). */
        public ItemStack gearOf(Target target) { return gear.getOrDefault(target, ItemStack.EMPTY); }

        /** Every stack, live: change them, then {@link #commit()}. */
        public List<ItemStack> stacks() {
            List<ItemStack> out = new ArrayList<>();
            for (var storage : storages.values()) out.addAll(storage.stacks());
            return out;
        }

        /** Server: writes every storage back (pockets into the capability and to the owner's client, gear into its stack). */
        public void commit() {
            if (client) throw new IllegalStateException("grid changes are the server's");
            storages.forEach((target, storage) -> {
                storage.prune();
                if (target == Target.POCKETS) {
                    var data = Pockets.of(player);
                    if (data != null) data.set(storage.save());
                } else {
                    GridContainerItem.write(gear.get(target), storage);
                }
            });
            if (player instanceof ServerPlayer serverPlayer) GridPackets.sendPockets(serverPlayer);
        }
    }
}
