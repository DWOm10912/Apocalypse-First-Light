package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Chest-grid menu (9 x 1..6) over a searchable container. Server side it wraps the masked
 * {@link AflContainerSearchView}; client side it mirrors only what the server sent.
 *
 * <p>Search presentation travels through ordinary menu data slots, in this order: the reveal mask in 16-bit words,
 * the current slot (-1 = none), its duration in ticks, the low 16 bits of the game tick it started, and a flags word.
 * Only changed values are sent, so a reveal costs one slot packet plus a few data packets and nothing is sent while
 * a slot is in progress; clients interpolate the progress from their own synchronized game time. The flags word is
 * always sent last, which tells the client when the initial state is complete.
 */
public final class AflContainerSearchMenu extends ChestMenu {
    private static final int EXTRA_DATA = 4;
    private static final int FLAG_SEARCHING = 1;
    private static final int FLAG_COMPLETE = 2;
    private static final int FLAG_SYNCED = 0x40;

    @Nullable
    private final AflContainerSearchView server;
    @Nullable
    private final ClientMirror client;
    private final Player player;
    private final int slotCount;
    private final int maskWords;
    private final int[] synced;
    private final long[] revealedAt;
    private boolean clientSynced;
    private boolean installed;
    private boolean closed;

    private AflContainerSearchMenu(MenuType<?> type, int id, Inventory inventory, Container container, int rows,
                                   @Nullable AflContainerSearchView server, @Nullable ClientMirror client) {
        super(type, id, inventory, container, rows);
        this.server = server;
        this.client = client;
        this.player = inventory.player;
        this.slotCount = rows * 9;
        this.maskWords = (slotCount + 15) / 16;
        this.synced = new int[maskWords + EXTRA_DATA];
        this.synced[maskWords] = -1;
        this.revealedAt = new long[slotCount];
        Arrays.fill(revealedAt, Long.MIN_VALUE);
        for (int index = 0; index < synced.length; index++) {
            addDataSlot(new SearchData(index));
        }
        if (server != null) {
            server.owner().aflSearchState().attach(this);
        }
    }

    static AflContainerSearchMenu server(int id, Inventory inventory, AflSearchableContainer owner, int rows) {
        AflContainerSearchView view = new AflContainerSearchView(owner);
        return new AflContainerSearchMenu(AflMenus.searchableContainer(rows), id, inventory, view, rows, view, null);
    }

    /** Client factory used by the registered menu types; the server sends no extra open data. */
    public static AflContainerSearchMenu client(int rows, int id, Inventory inventory) {
        ClientMirror mirror = new ClientMirror(rows * 9);
        return new AflContainerSearchMenu(AflMenus.searchableContainer(rows), id, inventory, mirror, rows, null, mirror);
    }

    /** ChestMenu adds plain container slots from its constructor; replace them with masked ones. */
    @Override
    protected Slot addSlot(Slot slot) {
        if (slot.getClass() == Slot.class && slot.container instanceof AflContainerSearchSlot.RevealMask) {
            slot = new AflContainerSearchSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y);
        }
        return super.addSlot(slot);
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

    /** Slot currently being searched, or -1. */
    public int currentSearchSlot() {
        return value(maskWords);
    }

    public boolean isSearching() {
        return (value(maskWords + 3) & FLAG_SEARCHING) != 0 && currentSearchSlot() >= 0;
    }

    public boolean isSearchComplete() {
        return (value(maskWords + 3) & FLAG_COMPLETE) != 0;
    }

    /** 0..1 progress of the current slot, interpolated from the synchronized game time. Presentation only. */
    public float currentSlotProgress(float partialTick) {
        int duration = value(maskWords + 1);
        if (!isSearching() || duration <= 0) {
            return 0.0F;
        }
        int elapsed = (short) (((int) player.level().getGameTime() & 0xFFFF) - value(maskWords + 2));
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
        return switch (index - maskWords) {
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
        } else if (index == maskWords) {
            synced[index] = (short) value;
        } else {
            synced[index] = value & 0xFFFF;
            if (index == maskWords + 3 && (value & FLAG_SYNCED) != 0) {
                clientSynced = true;
            }
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
