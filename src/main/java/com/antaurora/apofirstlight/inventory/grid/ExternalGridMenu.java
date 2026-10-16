package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The panel of an {@link ExternalGridHost}'s grid (docs/ui/afl_inventory_grid_v0_plan.md 15). Server side it wraps the
 * host; client side it holds a copy of the grid that {@link ExternalGrids} keeps current. Its slots are the player's
 * weapon positions and hand (hotbar 0..3), so the grids side panel shows beside it (ContainerGridPanel) and the menu's
 * carried stack is the grids' cursor as in any container ({@link GridActions#gridMenu}). The grid itself has no slots:
 * {@link GridActions} does everything on it with target {@link GridActions#EXTERNAL_TARGET}.
 */
public final class ExternalGridMenu extends AbstractContainerMenu {
    /** Layout, GUI pixels (the screen draws to the same numbers): cell, side padding, the title band, the gap to the slots. */
    public static final int CELL = 18, PAD = 8, TOP = 20, SECTION_GAP = 2, SLOTS_GAP = 16, BOTTOM = 8;
    /** The extra gap before the hand's slot (after the three weapon positions). */
    public static final int HAND_GAP = 6;

    @Nullable
    private final ExternalGridHost host;
    private final int hostEntityId;
    private final GridLayout layout;
    /** Client: the grid as last sent. */
    private GridStorage clientGrid;

    /** Server: the host's panel for the player. */
    public ExternalGridMenu(int id, Inventory inventory, ExternalGridHost host, int hostEntityId) {
        this(id, inventory, host, hostEntityId, host.grid().layout(), null);
    }

    private ExternalGridMenu(int id, Inventory inventory, @Nullable ExternalGridHost host, int hostEntityId, GridLayout layout,
                             @Nullable ListTag clientItems) {
        super(AflMenus.EXTERNAL_GRID.get(), id);
        this.host = host;
        this.hostEntityId = hostEntityId;
        this.layout = layout;
        this.clientGrid = host == null ? GridStorage.load(layout, clientItems, true) : null;
        for (int i = 0; i < HotbarRoles.USED; i++) addSlot(new Slot(inventory, i, slotX(i) + 1, slotY(layout) + 1));
    }

    /** Client: from the open data ({@link #writeOpen}). */
    public static ExternalGridMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        int sections = buf.readByte();
        int[] wh = new int[sections * 2];
        for (int i = 0; i < wh.length; i++) wh[i] = buf.readByte();
        CompoundTag tag = buf.readNbt();
        return new ExternalGridMenu(id, inventory, null, entity, GridLayout.of(wh),
                tag == null ? new ListTag() : tag.getList("Items", Tag.TAG_COMPOUND));
    }

    /** Server: the open data the client menu is built from. */
    static void writeOpen(FriendlyByteBuf buf, ExternalGridHost host, int hostEntityId) {
        buf.writeVarInt(hostEntityId);
        var layout = host.grid().layout();
        buf.writeByte(layout.size());
        for (int i = 0; i < layout.size(); i++) {
            buf.writeByte(layout.section(i).w());
            buf.writeByte(layout.section(i).h());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", host.grid().save());
        buf.writeNbt(tag);
    }

    // ---------------------------------------------------------------- layout (shared with the screen)

    /** Left edge of a section's cells. */
    public int sectionX(int section) {
        int x = PAD;
        for (int i = 0; i < section; i++) x += layout.section(i).w() * CELL + SECTION_GAP;
        return x;
    }

    public int gridHeight() {
        int h = 0;
        for (int i = 0; i < layout.size(); i++) h = Math.max(h, layout.section(i).h() * CELL);
        return h;
    }

    public int width() {
        int w = sectionX(layout.size()) - SECTION_GAP + PAD;
        return Math.max(w, slotX(HotbarRoles.USED - 1) + CELL + PAD);
    }

    public int height() {
        return slotY(layout) + CELL + BOTTOM;
    }

    /** Left edge of hotbar slot box {@code i} (0..3). */
    public static int slotX(int i) {
        return PAD + i * CELL + (i >= HotbarRoles.HAND_SLOT ? HAND_GAP : 0);
    }

    private static int slotY(GridLayout layout) {
        int h = 0;
        for (int i = 0; i < layout.size(); i++) h = Math.max(h, layout.section(i).h() * CELL);
        return TOP + h + SLOTS_GAP;
    }

    public int slotY() {
        return slotY(layout);
    }

    // ---------------------------------------------------------------- the grid

    /** The grid: the host's live storage on the server, the last sent copy on the client. */
    public GridStorage grid() {
        return host != null ? host.grid() : clientGrid;
    }

    public GridLayout layout() {
        return layout;
    }

    @Nullable
    public ExternalGridHost host() {
        return host;
    }

    public int hostEntityId() {
        return hostEntityId;
    }

    /** Client: the grid as the server sent it. */
    public void receive(ListTag items) {
        clientGrid = GridStorage.load(layout, items, true);
    }

    /** Server: after the grid changed, the host saves and everyone looking at it gets the new grid. */
    void changed() {
        if (host == null) return;
        host.onGridChanged();
        ExternalGrids.contentsChanged(host);
    }

    @Override
    public boolean stillValid(Player player) {
        return host == null || host.stillValid(player);
    }

    /** Shift-click on a weapon position or the hand: as much as fits goes into the grid (server). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (host == null || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        var stack = slot.getItem();
        if (!PlayerGrids.allowedInGrid(stack) || !host.stillValid(player)) return ItemStack.EMPTY;
        var moving = stack.copy();
        if (host.grid().insert(moving)) {
            slot.set(moving.isEmpty() ? ItemStack.EMPTY : moving);
            changed();
        }
        return ItemStack.EMPTY;
    }
}
