package com.antaurora.apofirstlight.containersearch;

import net.minecraft.network.FriendlyByteBuf;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.AflEquipmentSlot;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Search menu over a searchable container: one slot per cell of its grid sections ({@link AflContainerSearchLayout},
 * AflContainerGrid), the player's inventory and gear after them. Server side it wraps the masked
 * {@link AflContainerSearchView}; client side it mirrors only what the server sent. One menu type for every container:
 * the sections come in the open data (AflContainerSearch#open). The search page moves the container's items as a grid
 * (GridActions.CONTAINER_TARGET); the slots stay for shift-clicks, "take all" and the Creative screen.
 *
 * <p>Search presentation travels through ordinary menu data slots, in this order: the reveal mask in 16-bit words,
 * each slot's footprint byte (AflContainerGrid#footprint, two to a word: what lies there and how big, unsearched items
 * too), the current slot (-1 = none), its duration in ticks, the low 16 bits of the game tick it started, and a flags word.
 * Only changed values are sent, so a reveal costs one slot packet plus a few data packets and nothing is sent while
 * a slot is in progress; clients interpolate the progress from their own synchronized game time. The flags word is
 * always sent last, which tells the client when the initial state is complete.
 */
public final class AflContainerSearchMenu extends AbstractContainerMenu {
    private static final int EXTRA_DATA = 4;
    private static final int FLAG_SEARCHING = 1;
    private static final int FLAG_COMPLETE = 2;
    private static final int FLAG_SYNCED = 0x40;
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] GEAR_ICONS = {"empty_slot_back", "empty_slot_wrist", "empty_slot_ears", "empty_slot_rig", "empty_slot_holster"};
    /** InventoryMenu indices of the gear this menu also holds: armour 5..8, the AFL equipment slots 46..54. */
    private static final int ARMOUR_ROLE = 5, EQUIPMENT_ROLE = 46, EQUIPMENT_ROLES = 9;
    /** The search page's "take all" button (AflSearchScreen). */
    public static final int TAKE_ALL = 0;

    private final Container container;
    private final AflContainerSearchLayout layout;
    @Nullable
    private final AflContainerSearchView server;
    @Nullable
    private final ClientMirror client;
    private final Player player;
    private final int slotCount;
    private final int maskWords;
    /** First footprint word and first extra word in the data. */
    private final int footWords, extra;
    private final int[] synced;
    private final long[] revealedAt;
    private boolean clientSynced;
    private boolean installed;
    private boolean closed;

    private AflContainerSearchMenu(MenuType<?> type, int id, Inventory inventory, Container container,
                                   AflContainerSearchLayout layout,
                                   @Nullable AflContainerSearchView server, @Nullable ClientMirror client) {
        super(type, id);
        checkContainerSize(container, layout.size());
        this.container = container;
        this.layout = layout;
        container.startOpen(inventory.player);
        // container cells: masked slots (the server view and the client mirror are both reveal masks)
        for (int slot = 0; slot < layout.size(); slot++) {
            addSlot(new AflContainerSearchSlot(container, slot, layout.slotX(slot), layout.slotY(slot)));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, layout.inventoryY() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, layout.inventoryY() + 58));
        }
        // the gear on the search page (AflSearchScreen, docs/ui/afl_container_search_page_v1.md 8): armour head to feet,
        // then the AFL equipment slots in InventoryMenu's order (InventoryMenuEquipmentMixin), off screen; a shift-click
        // from the container never lands in them (quickMoveStack), only a click or a drop does
        for (EquipmentSlot armour : ARMOUR) {
            addSlot(new ArmourSlot(inventory, inventory.player, armour));
        }
        for (int i = 0; i < AflEquipmentSlots.INVENTORY_PAGE.size(); i++) {
            addSlot(new AflEquipmentSlot(inventory.player, AflEquipmentSlots.INVENTORY_PAGE.get(i), GEAR_ICONS[i], -2000, -2000));
        }
        for (int i = 0; i < AflEquipmentSlots.CLIPS; i++) {
            addSlot(new AflEquipmentSlot(inventory.player, AflEquipmentSlots.CLIP, i, "empty_slot_clip", -2000, -2000));
        }
        for (String clothes : AflEquipmentSlots.CLOTHES) {
            addSlot(new AflEquipmentSlot(inventory.player, clothes, "empty_slot_" + clothes, -2000, -2000));
        }
        this.server = server;
        this.client = client;
        this.player = inventory.player;
        this.slotCount = layout.size();
        this.maskWords = (slotCount + 15) / 16;
        this.footWords = maskWords;
        this.extra = footWords + (slotCount + 1) / 2;
        this.synced = new int[extra + EXTRA_DATA];
        this.synced[extra] = -1;
        this.revealedAt = new long[slotCount];
        Arrays.fill(revealedAt, Long.MIN_VALUE);
        for (int index = 0; index < synced.length; index++) {
            addDataSlot(new SearchData(index));
        }
        if (server != null) {
            server.owner().aflSearchState().attach(this);
        }
    }

    static AflContainerSearchMenu server(int id, Inventory inventory, AflSearchableContainer owner, AflContainerSearchLayout layout) {
        AflContainerSearchView view = new AflContainerSearchView(owner);
        return new AflContainerSearchMenu(AflMenus.SEARCHABLE_CONTAINER.get(), id, inventory, view, layout, view, null);
    }

    /** Client factory of the registered menu type: the grid sections come in the open data (AflContainerSearch#open). */
    public static AflContainerSearchMenu client(int id, Inventory inventory, FriendlyByteBuf data) {
        AflContainerSearchLayout layout = AflContainerSearchLayout.read(data);
        ClientMirror mirror = new ClientMirror(layout.size());
        return new AflContainerSearchMenu(AflMenus.SEARCHABLE_CONTAINER.get(), id, inventory, mirror, layout, null, mirror);
    }

    public AflContainerSearchLayout layout() {
        return layout;
    }

    /**
     * This menu's index of the gear slot an InventoryMenu index names (armour 5..8, AFL equipment 46..54), or -1. The
     * player's 36 inventory slots follow the container; the gear follows them.
     */
    public int gearSlot(int inventoryMenuIndex) {
        int gear = slotCount + Inventory.INVENTORY_SIZE;
        if (inventoryMenuIndex >= ARMOUR_ROLE && inventoryMenuIndex < ARMOUR_ROLE + ARMOUR.length)
            return gear + inventoryMenuIndex - ARMOUR_ROLE;
        if (inventoryMenuIndex >= EQUIPMENT_ROLE && inventoryMenuIndex < EQUIPMENT_ROLE + EQUIPMENT_ROLES)
            return gear + ARMOUR.length + inventoryMenuIndex - EQUIPMENT_ROLE;
        return -1;
    }

    /** Server: the searched container itself (not the masking view); null on the client. */
    @Nullable
    public AflSearchableContainer owner() {
        return server == null ? null : server.owner();
    }

    @Override
    public boolean stillValid(Player viewer) {
        return container.stillValid(viewer);
    }

    /**
     * Shift-click: container slots go to the player inventory (end first; never into the gear, so "take all" does not
     * dress the player); inventory and gear slots go into the container's grid where they fit (AflContainerGrid#insert,
     * server only: the client waits for the result).
     */
    @Override
    public ItemStack quickMoveStack(Player mover, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < slotCount) {
                if (!moveItemStackTo(stack, slotCount, slotCount + Inventory.INVENTORY_SIZE, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (server == null || !AflContainerGrid.insert(server.owner(), stack)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    /**
     * "Take all": every revealed slot in order, moved the way a shift-click moves it (weapon positions, then whatever is
     * left into the grids, LockedInventoryMenuMixin); what fits nowhere stays. Hidden slots stay hidden and untouched.
     */
    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (id != TAKE_ALL || !getCarried().isEmpty()) return false;
        for (int index = 0; index < slotCount; index++) {
            if (!isSlotRevealed(index) || !slots.get(index).hasItem() || !slots.get(index).mayPickup(clicker)) continue;
            clicked(index, 0, ClickType.QUICK_MOVE, clicker);
        }
        return true;
    }

    /** Hidden container slots ignore every click type, including drag steps and number-key / offhand swaps. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
        if (slotId >= 0 && slotId < slotCount && !isSlotRevealed(slotId)) {
            return;
        }
        super.clicked(slotId, button, clickType, clicker);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isHiddenSlot(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isHiddenSlot(slot) && super.canDragTo(slot);
    }

    @Override
    public void broadcastChanges() {
        if (server != null) {
            if (player.containerMenu == this) {
                installed = true;
            }
            server.owner().aflSearchState().tick(server.owner());
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player leaving) {
        super.removed(leaving);
        container.stopOpen(leaving);
        closed = true;
        if (server != null) {
            server.owner().aflSearchState().detach(server.owner(), this);
        }
    }

    // ---- search presentation API (valid on both sides) ----

    public int searchSlotCount() {
        return slotCount;
    }

    public boolean isSlotRevealed(int slot) {
        if (slot < 0 || slot >= slotCount) {
            return false;
        }
        return server != null ? server.isSlotRevealed(slot) : client != null && client.isSlotRevealed(slot);
    }

    public int revealedCount() {
        int count = 0;
        for (int slot = 0; slot < slotCount; slot++) {
            if (isSlotRevealed(slot)) {
                count++;
            }
        }
        return count;
    }

    /** The footprint byte of a slot (AflContainerGrid#footprint): 0 when nothing lies there. */
    public int footprint(int slot) {
        if (slot < 0 || slot >= slotCount) return 0;
        return value(footWords + slot / 2) >> (slot % 2 * 8) & 0xFF;
    }

    /** Items in the container, searched or not. */
    public int itemCount() {
        int count = 0;
        for (int slot = 0; slot < slotCount; slot++) if (footprint(slot) != 0) count++;
        return count;
    }

    /** Items already searched. */
    public int revealedItemCount() {
        int count = 0;
        for (int slot = 0; slot < slotCount; slot++) if (footprint(slot) != 0 && isSlotRevealed(slot)) count++;
        return count;
    }

    /** Slot currently being searched, or -1. */
    public int currentSearchSlot() {
        return value(extra);
    }

    public boolean isSearching() {
        return (value(extra + 3) & FLAG_SEARCHING) != 0 && currentSearchSlot() >= 0;
    }

    public boolean isSearchComplete() {
        return (value(extra + 3) & FLAG_COMPLETE) != 0;
    }

    /** 0..1 progress of the current slot, interpolated from the synchronized game time. Presentation only. */
    public float currentSlotProgress(float partialTick) {
        int duration = value(extra + 1);
        if (!isSearching() || duration <= 0) {
            return 0.0F;
        }
        int elapsed = (short) (((int) player.level().getGameTime() & 0xFFFF) - value(extra + 2));
        return Mth.clamp((elapsed + partialTick) / duration, 0.0F, 1.0F);
    }

    /** Ticks since this client saw the slot being revealed; infinite for slots already revealed at opening. */
    public float revealAge(int slot, float partialTick) {
        if (slot < 0 || slot >= slotCount || revealedAt[slot] == Long.MIN_VALUE) {
            return Float.POSITIVE_INFINITY;
        }
        return player.level().getGameTime() - revealedAt[slot] + partialTick;
    }

    // ---- session bookkeeping (server) ----

    boolean isStale() {
        return closed || player.isRemoved() || installed && player.containerMenu != this;
    }

    @Nullable
    ServerPlayer searcher() {
        return !isStale() && player.isAlive() && !player.isSpectator() && player instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
    }

    private boolean isHiddenSlot(Slot slot) {
        return slot instanceof AflContainerSearchSlot searchSlot && !searchSlot.isRevealed();
    }

    private int value(int index) {
        return server != null ? serverValue(index) : synced[index];
    }

    private int serverValue(int index) {
        AflSearchableContainer owner = server.owner();
        AflContainerSearchState state = owner.aflSearchState();
        if (index < maskWords) {
            return state.maskWord(owner, index);
        }
        if (index < extra) {
            int slot = (index - footWords) * 2;
            return AflContainerGrid.footprint(owner, slot) | (slot + 1 < slotCount ? AflContainerGrid.footprint(owner, slot + 1) << 8 : 0);
        }
        return switch (index - extra) {
            case 0 -> state.currentSlot();
            case 1 -> state.currentDuration();
            case 2 -> (int) (state.currentStart() & 0xFFFF);
            default -> FLAG_SYNCED
                    | (state.isRunning() && state.currentSlot() >= 0 ? FLAG_SEARCHING : 0)
                    | (state.isComplete(owner) ? FLAG_COMPLETE : 0);
        };
    }

    /** Client: data slot values arrive as signed shorts. */
    private void receive(int index, int value) {
        if (index < maskWords) {
            int bits = value & 0xFFFF;
            int gained = bits & ~synced[index];
            synced[index] = bits;
            for (int bit = 0; bit < 16 && index * 16 + bit < slotCount; bit++) {
                int slot = index * 16 + bit;
                client.setRevealed(slot, (bits & 1 << bit) != 0);
                if (clientSynced && (gained & 1 << bit) != 0) {
                    revealedAt[slot] = player.level().getGameTime();
                }
            }
        } else if (index == extra) {
            synced[index] = (short) value;
        } else {
            synced[index] = value & 0xFFFF;
            if (index == extra + 3 && (value & FLAG_SYNCED) != 0) {
                clientSynced = true;
            }
        }
    }

    /** An armour slot as InventoryMenu makes it: one item that equips there, no taking off a curse of binding. */
    private static final class ArmourSlot extends Slot {
        private final Player owner;
        private final EquipmentSlot armour;

        ArmourSlot(Inventory inventory, Player owner, EquipmentSlot armour) {
            super(inventory, Inventory.INVENTORY_SIZE + armour.getIndex(), -2000, -2000);
            this.owner = owner;
            this.armour = armour;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            owner.onEquipItem(armour, getItem(), stack);
            super.setByPlayer(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(armour, owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            return (stack.isEmpty() || player.isCreative() || !EnchantmentHelper.hasBindingCurse(stack)) && super.mayPickup(player);
        }
    }

    private final class SearchData extends DataSlot {
        private final int index;

        private SearchData(int index) {
            this.index = index;
        }

        @Override
        public int get() {
            return value(index);
        }

        @Override
        public void set(int value) {
            if (client != null) {
                receive(index, value);
            }
        }
    }

    private static final class ClientMirror extends SimpleContainer implements AflContainerSearchSlot.RevealMask {
        private final BitSet revealed = new BitSet();

        private ClientMirror(int size) {
            super(size);
        }

        @Override
        public boolean isSlotRevealed(int slot) {
            return revealed.get(slot);
        }

        private void setRevealed(int slot, boolean on) {
            revealed.set(slot, on);
        }
    }
}
