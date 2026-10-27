package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;


/**
 * Entry points an asset's container block entity forwards to. See
 * {@code docs/gameplay/progressive_container_search_v1.md} for the full integration contract.
 */
public final class AflContainerSearch {
    private AflContainerSearch() {
    }

    /**
     * First line of the asset's {@code unpackLootTable(Player)} override. Every vanilla path that unpacks loot
     * passes through there, so the "search required" decision is made while the loot table is still pending.
     */
    public static void beforeLootUnpack(AflSearchableContainer container) {
        container.aflSearchState().ensureInitialized(container);
    }

    /**
     * Last line of the same override: the rolled loot laid out as the container's grid (AflContainerGrid#pack: a stack
     * the table spread over several slots together again, items that overlap moved, the larger first).
     */
    public static void afterLootUnpack(AflSearchableContainer container) {
        AflContainerGrid.pack(container);
    }

    /**
     * Opens the container's search page for the player (server; nothing on the client). The menu's open data is the
     * container's grid sections. {@code provider} makes the menu (normally {@link #createMenu}) and names it.
     */
    public static void open(Player player, MenuProvider provider, AflSearchableContainer container) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AflContainerSearchLayout layout = container.aflSearchLayout();
        if (layout == null) return;
        NetworkHooks.openScreen(serverPlayer, provider, layout::write);
    }

    /** {@link #open(Player, MenuProvider, AflSearchableContainer)} for a block entity that is both. */
    public static <T extends MenuProvider & AflSearchableContainer> void open(Player player, @Nullable T container) {
        if (container != null) open(player, container, container);
    }

    public static boolean isRevealed(AflSearchableContainer container, int slot) {
        return container.aflSearchState().isRevealed(container, slot);
    }

    public static boolean isComplete(AflSearchableContainer container) {
        return container.aflSearchState().isComplete(container);
    }

    /**
     * For the asset's {@code canPlaceItem} override: vanilla hopper fallback insertion and the Forge item handler check
     * it first. A revealed slot, and room in the grid for the stack there (AflContainerGrid#canPlace).
     */
    public static boolean canPlaceItem(AflSearchableContainer container, int slot, ItemStack stack) {
        return isRevealed(container, slot) && AflContainerGrid.canPlace(container, slot, stack);
    }

    /** For the asset's {@code canTakeItem} override: vanilla fallback extraction checks it first. */
    public static boolean canTakeItem(AflSearchableContainer container, int slot) {
        return isRevealed(container, slot);
    }

    /** For the asset's {@code createUnSidedHandler} override: the Forge capability every automation uses. */
    public static IItemHandler itemHandler(AflSearchableContainer container) {
        return new AflContainerSearchItemHandler(container);
    }

    /**
     * For the asset's {@code createMenu(int, Inventory)}: the search menu, searched or not (once searched every slot is
     * revealed). Until 2026-10-26 a searched chest grid or 3 x 3 grid opened the vanilla menu of the same layout; since the
     * search page (docs/ui/afl_container_search_page_v1.md) these containers keep that page. The grid sections come
     * from {@link AflSearchableContainer#aflSearchLayout()}; returns null (menu not opened) when they do not match the
     * container size. The contents are laid out as the grid first (AflContainerGrid#pack) and the empty cells open.
     * Open it with {@link #open}, which sends the sections along.
     */
    @Nullable
    public static AbstractContainerMenu createMenu(int containerId, Inventory inventory,
                                                   AflSearchableContainer container) {
        int size = container.getContainerSize();
        AflContainerSearchLayout layout = container.aflSearchLayout();
        if (layout == null || layout.size() != size) {
            ApocalypseFirstLight.LOGGER.error("Searchable container at {} has {} slots and grid sections {} of another size",
                    container.getBlockPos(), size, layout);
            return null;
        }
        AflContainerGrid.pack(container);
        container.aflSearchState().revealEmpty(container);
        return AflContainerSearchMenu.server(containerId, inventory, container, layout);
    }

    /**
     * Break / explosion / support-loss handling: revealed slots drop as usual; hidden slots are never-seen world
     * loot and are destroyed with the container, and a still-pending loot table is cancelled instead of rolled.
     * Breaking therefore cannot bypass the search, and re-placing the block cannot re-roll anything.
     */
    public static void dropContentsOnBreak(Level level, BlockPos pos, AflSearchableContainer container) {
        if (level.isClientSide) {
            return;
        }
        AflContainerSearchState state = container.aflSearchState();
        boolean hiddenRemain = !state.isComplete(container);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (state.isRevealed(container, slot)) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), container.removeItemNoUpdate(slot));
            }
        }
        if (hiddenRemain) {
            if (container instanceof RandomizableContainerBlockEntity loot) {
                loot.setLootTable(null, 0L);
            }
            container.clearContent();
        }
    }

    /** Comparator extension point: vanilla fullness formula over revealed slots only. */
    public static int revealedAnalogSignal(AflSearchableContainer container) {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(new AflContainerSearchView(container));
    }

    /** One-line state summary for development commands. */
    public static String debugSummary(AflSearchableContainer container) {
        return container.aflSearchState().describe(container);
    }
}
