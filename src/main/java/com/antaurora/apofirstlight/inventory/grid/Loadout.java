package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Quick bindings and the hand (docs/ui/afl_inventory_grid_v0_plan.md 10), Survival / Adventure. Keys 4-9 are six
 * bindings to an item kind in the pockets or the chest rig; pressing one takes a stack of that kind into the hand
 * (hotbar index 3, HotbarRoles.HAND_SLOT) and keeps the cells it came from free; switching to anything else puts it
 * back there (or anywhere in the grids). A stack used up in the hand is followed by the next one of its kind. The
 * inventory page's hands box is the same index: what is put there is held, and goes back where it was picked from.
 * <p>
 * Kept with the player (capability {@code apocalypse_firstlight:loadout}): the bindings survive death and succession,
 * the hand state does not. The owner's client gets a copy (GridPackets.LoadoutSync) for the HUD and the inventory page.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class Loadout implements INBTSerializable<CompoundTag> {
    public static final int BINDS = 6;
    /** handBind: nothing in the hand from a binding or the hands box. */
    public static final int NONE = -1;
    /** handBind: the stack in the hand was put there by hand (hands box), or changed into another item while held. */
    public static final int HANDS = -2;
    public static final Capability<Loadout> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "loadout");
    private static final int TIDY_TICKS = 20;

    /** Where the stack in the hand came from: a storage (PlayerGrids.Target ordinal), its cells and rotation. */
    public record Origin(int target, int section, int x, int y, int w, int h, boolean rotated) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putByte("Target", (byte) target);
            tag.putByte("Sec", (byte) section);
            tag.putByte("X", (byte) x);
            tag.putByte("Y", (byte) y);
            tag.putByte("W", (byte) w);
            tag.putByte("H", (byte) h);
            tag.putBoolean("Rot", rotated);
            return tag;
        }

        static Origin load(CompoundTag tag) {
            return new Origin(tag.getByte("Target"), tag.getByte("Sec"), tag.getByte("X"), tag.getByte("Y"),
                    Math.max(1, tag.getByte("W")), Math.max(1, tag.getByte("H")), tag.getBoolean("Rot"));
        }

        static Origin of(PlayerGrids.Target target, GridStorage storage, GridStorage.Entry entry) {
            var size = storage.footprint(entry);
            return new Origin(target.ordinal(), entry.section(), entry.x(), entry.y(), size.w(), size.h(), entry.rotated());
        }
    }

    private final Item[] binds = new Item[BINDS];
    private int handBind = NONE;
    @Nullable private Origin origin;
    @Nullable private Item handItem;
    // not saved
    private int prevSelected = -1;
    private int tidyIn;
    @Nullable private Origin lastPick;
    @Nullable private Item lastPickItem;

    @Nullable
    public static Loadout of(Player player) {
        return player.getCapability(CAPABILITY).resolve().orElse(null);
    }

    // ------------------------------------------------------------------ client copy

    /** Client: the owner's bindings and hand state as last sent. */
    public record ClientState(Item[] binds, int handBind, @Nullable Origin origin) {
        static final ClientState EMPTY = new ClientState(new Item[BINDS], NONE, null);
    }

    private static volatile ClientState client = ClientState.EMPTY;

    public static ClientState client() { return client; }

    static void receive(ClientState state) { client = state; }

    public static void clearClient() { client = ClientState.EMPTY; }

    // ------------------------------------------------------------------ saved state

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Item item : binds) list.add(StringTag.valueOf(item == null ? "" : ForgeRegistries.ITEMS.getKey(item).toString()));
        tag.put("Binds", list);
        tag.putInt("HandBind", handBind);
        if (origin != null) tag.put("Origin", origin.save());
        if (handItem != null) tag.putString("HandItem", ForgeRegistries.ITEMS.getKey(handItem).toString());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        ListTag list = tag.getList("Binds", Tag.TAG_STRING);
        for (int i = 0; i < BINDS; i++) binds[i] = i < list.size() ? item(list.getString(i)) : null;
        handBind = tag.contains("HandBind") ? tag.getInt("HandBind") : NONE;
        origin = tag.contains("Origin", Tag.TAG_COMPOUND) ? Origin.load(tag.getCompound("Origin")) : null;
        handItem = tag.contains("HandItem") ? item(tag.getString("HandItem")) : null;
    }

    @Nullable
    private static Item item(String id) {
        if (id.isEmpty()) return null;
        var key = ResourceLocation.tryParse(id);
        return key == null || !ForgeRegistries.ITEMS.containsKey(key) ? null : ForgeRegistries.ITEMS.getValue(key);
    }

    @Nullable public Item bind(int index) { return index >= 0 && index < BINDS ? binds[index] : null; }
    public int handBind() { return handBind; }
    @Nullable public Origin origin() { return origin; }

    // ------------------------------------------------------------------ reservation

    /** Every view of the player's grids keeps the hand's cells free (server: this state; client: the copy). */
    static void applyReservation(Player player, Map<PlayerGrids.Target, GridStorage> storages) {
        Origin o;
        if (player.level().isClientSide) o = client.origin();
        else {
            var loadout = of(player);
            o = loadout == null ? null : loadout.origin;
        }
        for (var entry : storages.entrySet()) {
            boolean here = o != null && o.target() == entry.getKey().ordinal();
            entry.getValue().reserve(here ? new GridStorage.Reserved(o.section(), o.x(), o.y(), o.w(), o.h()) : null);
        }
    }

    // ------------------------------------------------------------------ server rules

    /** Server: the cell a stack was just picked from on the inventory page (the hands box puts it back there). */
    static void recordPick(ServerPlayer player, PlayerGrids.Target target, GridStorage storage, GridStorage.Entry entry) {
        var loadout = of(player);
        if (loadout == null) return;
        loadout.lastPick = Origin.of(target, storage, entry);
        loadout.lastPickItem = entry.stack().getItem();
    }

    /** Server: binds key 4+index to the kind of a pockets / rig stack (a kind is bound to one key at most). */
    static boolean bind(ServerPlayer player, int index, ItemStack stack) {
        var loadout = of(player);
        if (loadout == null || index < 0 || index >= BINDS || stack.isEmpty()) return false;
        for (int i = 0; i < BINDS; i++) if (loadout.binds[i] == stack.getItem()) loadout.binds[i] = null;
        loadout.binds[index] = stack.getItem();
        sync(player);
        return true;
    }

    static boolean unbind(ServerPlayer player, int index) {
        var loadout = of(player);
        if (loadout == null || index < 0 || index >= BINDS || loadout.binds[index] == null) return false;
        loadout.binds[index] = null;
        sync(player);
        return true;
    }

    /** Server: key 4+index pressed (no screen open). */
    public static void useBind(ServerPlayer player, int index) {
        var loadout = of(player);
        if (loadout == null || index < 0 || index >= BINDS || !player.isAlive() || PlayerStorageCapacity.unrestricted(player)) return;
        loadout.reconcileHand(player);
        Item kind = loadout.binds[index];
        if (kind == null) return;
        var inventory = player.getInventory();
        if (inventory.selected == HotbarRoles.HAND_SLOT && loadout.handBind == index && !inventory.items.get(HotbarRoles.HAND_SLOT).isEmpty())
            return;
        if (find(player, kind) == null) {
            hint(player, "none_left");
            return;
        }
        if (inventory.selected != HotbarRoles.HAND_SLOT && !loadout.leave(player, inventory.selected)) return;
        if (!inventory.items.get(HotbarRoles.HAND_SLOT).isEmpty() && !loadout.stowHand(player)) {
            hint(player, "no_room");
            return;
        }
        loadout.take(player, index, kind);
        select(player, HotbarRoles.HAND_SLOT);
    }

    /**
     * Server: a stack into the hand, held from now on (written for the unload's magazine; since 2026-10-24 that is stowed
     * instead, weapon/NativeMagazines#stow, and this has no caller).
     * The selected position is left as when switching away (a weapon position keeps its weapon), the hand's stack put
     * back. Creative / Spectator: the first empty hotbar slot, selected. False when there is no room (nothing changed
     * but what leaving put away; the caller puts the stack elsewhere).
     */
    public static boolean toHand(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        if (PlayerStorageCapacity.unrestricted(player)) {
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                if (!inventory.items.get(i).isEmpty()) continue;
                inventory.items.set(i, stack);
                inventory.setChanged();
                select(player, i);
                return true;
            }
            return false;
        }
        var loadout = of(player);
        if (loadout == null) return false;
        loadout.reconcileHand(player);
        if (inventory.selected != HotbarRoles.HAND_SLOT && !loadout.leave(player, inventory.selected)) return false;
        if (!inventory.items.get(HotbarRoles.HAND_SLOT).isEmpty() && !loadout.stowHand(player)) {
            hint(player, "no_room");
            return false;
        }
        inventory.items.set(HotbarRoles.HAND_SLOT, stack);
        inventory.setChanged();
        loadout.handBind = HANDS;
        loadout.handItem = stack.getItem();
        loadout.origin = null;
        sync(player);
        select(player, HotbarRoles.HAND_SLOT);
        return true;
    }

    /** Server: one tick of the rules now (GameTests; fake players do not tick). */
    public static void tickNow(ServerPlayer player) {
        var loadout = of(player);
        if (loadout != null) {
            loadout.tidyIn = 0;
            loadout.tick(player);
        }
    }

    /** Server, each tick: the hand, switching away from a position, and what does not belong in the hotbar. */
    private void tick(ServerPlayer player) {
        var inventory = player.getInventory();
        if (!player.isAlive() || PlayerStorageCapacity.unrestricted(player)) {
            prevSelected = inventory.selected;
            return;
        }
        boolean filled = reconcileHand(player);
        int selected = inventory.selected;
        if (prevSelected >= 0 && prevSelected != selected && !leave(player, prevSelected)) {
            select(player, prevSelected);
            selected = prevSelected;
        }
        if (filled && selected != HotbarRoles.HAND_SLOT && leave(player, selected)) {
            select(player, HotbarRoles.HAND_SLOT);   // put into the hands box: held from now on
            selected = HotbarRoles.HAND_SLOT;
        }
        // driving: no weapon in hand (a weapon position, or a weapon in the hands box), put back as when switching away
        if (com.antaurora.apofirstlight.vehicle.PickupTruckEntity.driving(player)
                && HotbarRoles.weaponRole(inventory.items.get(selected)) != null) {
            if (selected == HotbarRoles.HAND_SLOT ? stowHand(player) : leave(player, selected)) {
                select(player, HotbarRoles.HAND_SLOT);
                selected = HotbarRoles.HAND_SLOT;
                hint(player, "driving");
            }
        }
        prevSelected = selected;
        if (--tidyIn <= 0) {
            tidyIn = TIDY_TICKS;
            tidy(player, selected);
        }
    }

    /**
     * Brings the hand state in line with what is in the hand. Returns true when the hand was just filled from outside
     * (the hands box, or an older save).
     */
    boolean reconcileHand(ServerPlayer player) {
        var inventory = player.getInventory();
        var stack = inventory.items.get(HotbarRoles.HAND_SLOT);
        if (stack.isEmpty()) {
            if (handBind == NONE) return false;
            int bound = handBind;
            Item was = handItem;
            clearHand(player);
            var carried = player.containerMenu.getCarried();
            boolean takenByCursor = !carried.isEmpty() && carried.getItem() == was;
            // used up (eaten, placed, thrown): the next stack of the bound kind follows, while the hand is still selected
            if (bound >= 0 && !takenByCursor && inventory.selected == HotbarRoles.HAND_SLOT && binds[bound] != null
                    && find(player, binds[bound]) != null)
                take(player, bound, binds[bound]);
            return false;
        }
        if (handBind == NONE) {
            handBind = HANDS;
            handItem = stack.getItem();
            origin = null;
            if (lastPick != null && lastPickItem == stack.getItem()) {
                var view = PlayerGrids.view(player);
                var target = PlayerGrids.Target.of(lastPick.target());
                var storage = target == null ? null : view.get(target);
                if (storage != null && storage.fits(stack, lastPick.section(), lastPick.x(), lastPick.y(), lastPick.rotated(), null))
                    origin = lastPick;
            }
            lastPick = null;
            sync(player);
            return true;
        }
        if (stack.getItem() != handItem) {
            // changed while held (a bucket filled, a bowl left over, a swap on the inventory page): no longer the binding's
            handItem = stack.getItem();
            handBind = HANDS;
            sync(player);
        }
        return false;
    }

    /** Moves the first stack of the kind in the pockets or rig into the hand, keeping its cells. */
    private void take(ServerPlayer player, int index, Item kind) {
        var found = find(player, kind);
        if (found == null) return;
        var view = found.view();
        var stack = found.entry().stack();
        origin = Origin.of(found.target(), found.storage(), found.entry());
        found.storage().remove(found.entry());
        view.commit();
        player.getInventory().items.set(HotbarRoles.HAND_SLOT, stack);
        handBind = index;
        handItem = stack.getItem();
        player.getInventory().setChanged();
        sync(player);
    }

    private record Found(PlayerGrids.View view, PlayerGrids.Target target, GridStorage storage, GridStorage.Entry entry) {}

    /** The first stack of the kind in the pockets, then the rig (row by row). Bindings never reach into the backpack. */
    @Nullable
    private static Found find(ServerPlayer player, Item kind) {
        var view = PlayerGrids.view(player);
        for (var target : new PlayerGrids.Target[]{PlayerGrids.Target.POCKETS, PlayerGrids.Target.RIG}) {
            var storage = view.get(target);
            if (storage == null) continue;
            GridStorage.Entry best = null;
            for (var e : storage.entries()) {
                if (e.stack().getItem() != kind) continue;
                if (best == null || e.section() < best.section() || e.section() == best.section()
                        && (e.y() < best.y() || e.y() == best.y() && e.x() < best.x())) best = e;
            }
            if (best != null) return new Found(view, target, storage, best);
        }
        return null;
    }

    /** Puts the hand's stack back where it came from, else anywhere in the grids; false (nothing moved) if it fits nowhere. */
    boolean stowHand(ServerPlayer player) {
        var inventory = player.getInventory();
        var stack = inventory.items.get(HotbarRoles.HAND_SLOT);
        if (stack.isEmpty()) {
            clearHand(player);
            return true;
        }
        if (!PlayerGrids.allowedInGrid(stack)) return false;
        var view = PlayerGrids.view(player);
        var work = stack.copy();
        if (origin != null) {
            var target = PlayerGrids.Target.of(origin.target());
            var storage = target == null ? null : view.get(target);
            if (storage != null) {
                storage.reserve(null);
                if (work.getCount() <= work.getMaxStackSize() && storage.fits(work, origin.section(), origin.x(), origin.y(), origin.rotated(), null)) {
                    storage.place(work, origin.section(), origin.x(), origin.y(), origin.rotated());
                    work = ItemStack.EMPTY;
                }
            }
        } else {
            for (var target : PlayerGrids.Target.values()) if (view.get(target) != null) view.get(target).reserve(null);
        }
        if (!work.isEmpty()) view.insert(work);
        if (!work.isEmpty()) return false;
        view.commit();
        inventory.items.set(HotbarRoles.HAND_SLOT, ItemStack.EMPTY);
        inventory.setChanged();
        clearHand(player);
        return true;
    }

    /**
     * Leaving a position: the hand puts its stack back; a weapon position whose weapon may no longer sit there (sling or
     * holster gone, held only) puts it into the grids. False, with a hint, when it fits nowhere.
     */
    boolean leave(ServerPlayer player, int index) {
        if (index == HotbarRoles.HAND_SLOT) {
            if (stowHand(player)) return true;
            hint(player, "no_room");
            return false;
        }
        var inventory = player.getInventory();
        if (index < 0 || index >= HotbarRoles.USED) return true;
        var stack = inventory.items.get(index);
        if (stack.isEmpty() || !PlayerStorageCapacity.isOverflow(inventory, index)) return true;
        if (PlayerGrids.insert(player, stack) && stack.isEmpty()) {
            inventory.items.set(index, ItemStack.EMPTY);
            inventory.setChanged();
            return true;
        }
        hint(player, index == HotbarRoles.PRIMARY_SLOT ? "no_sling" : index == HotbarRoles.PISTOL_SLOT ? "no_holster" : "no_room");
        return false;
    }

    /** What sits in the hotbar where it may not (not in hand): into the grids when they have room, else it stays as overflow. */
    private static void tidy(ServerPlayer player, int selected) {
        var inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (i == selected || i == HotbarRoles.HAND_SLOT || !PlayerStorageCapacity.isOverflow(inventory, i)) continue;
            var stack = inventory.items.get(i);
            if (PlayerGrids.insert(player, stack)) {
                if (stack.isEmpty()) inventory.items.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) inventory.setChanged();
    }

    private void clearHand(ServerPlayer player) {
        if (handBind == NONE && origin == null && handItem == null) return;
        handBind = NONE;
        origin = null;
        handItem = null;
        sync(player);
    }

    private static void select(ServerPlayer player, int index) {
        player.getInventory().selected = index;
        player.connection.send(new ClientboundSetCarriedItemPacket(index));
        var loadout = of(player);
        if (loadout != null) loadout.prevSelected = index;
    }

    private static void hint(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("hint.apocalypse_firstlight.loadout." + key), true);
    }

    static void sync(ServerPlayer player) {
        var loadout = of(player);
        if (loadout != null) GridPackets.sendLoadout(player, new ClientState(loadout.binds.clone(), loadout.handBind, loadout.origin));
    }

    // ------------------------------------------------------------------ events

    private static final class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final Loadout data = new Loadout();
        private final LazyOptional<Loadout> optional = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return capability == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { return data.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag tag) { data.deserializeNBT(tag); }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) event.addCapability(ID, new Provider());
    }

    /** The bindings carry over (a death, succession, the End); the hand state stays with the old body. */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var original = event.getOriginal();
        original.reviveCaps();
        var from = of(original);
        var to = of(event.getEntity());
        if (from != null && to != null) {
            System.arraycopy(from.binds, 0, to.binds, 0, BINDS);
            if (!event.isWasDeath()) {
                to.handBind = from.handBind;
                to.origin = from.origin;
                to.handItem = from.handItem;
            }
        }
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var loadout = of(player);
        if (loadout != null) loadout.tick(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        /** Not named "register": the event bus names its handler class after package, class and method, and Pockets has that one. */
        @SubscribeEvent
        public static void registerLoadout(RegisterCapabilitiesEvent event) {
            event.register(Loadout.class);
        }
    }
}
