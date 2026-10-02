package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Entry points an asset's container block entity forwards to. See
 * {@code docs/gameplay/progressive_container_search_v1.md} for the full integration contract.
 */
public final class AflContainerSearch {
    private static final List<MenuType<ChestMenu>> VANILLA_GRIDS = List.of(MenuType.GENERIC_9x1,
            MenuType.GENERIC_9x2, MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);

    private AflContainerSearch() {
    }

    /**
     * First line of the asset's {@code unpackLootTable(Player)} override. Every vanilla path that unpacks loot
     * passes through there, so the "search required" decision is made while the loot table is still pending.
     */
    public static void beforeLootUnpack(AflSearchableContainer container) {
        container.aflSearchState().ensureInitialized(container);
    }

    public static boolean isRevealed(AflSearchableContainer container, int slot) {
        return container.aflSearchState().isRevealed(container, slot);
    }

    public static boolean isComplete(AflSearchableContainer container) {
        return container.aflSearchState().isComplete(container);
    }

    /** For the asset's {@code canPlaceItem} override: vanilla hopper fallback insertion checks it first. */
    public static boolean canPlaceItem(AflSearchableContainer container, int slot) {
        return isRevealed(container, slot);
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
     * For the asset's {@code createMenu(int, Inventory)}: a search menu while anything is hidden, afterwards the
     * ordinary vanilla menu of the same layout (chest grid 9 x 1..6, or the 3 x 3 dispenser grid; the 6 x 3 grid has no
     * vanilla menu and keeps the search menu, every slot revealed). The layout comes
     * from {@link AflSearchableContainer#aflSearchLayout()}; returns null (menu not opened) when it does not match the
     * container size.
     */
    @Nullable
    public static AbstractContainerMenu createMenu(int containerId, Inventory inventory,
                                                   AflSearchableContainer container) {
        int size = container.getContainerSize();
        AflContainerSearchLayout layout = container.aflSearchLayout();
        if (layout == null || layout.size() != size) {
            ApocalypseFirstLight.LOGGER.error("Searchable container at {} has {} slots and layout {}; a search menu "
                    + "needs a 9 x 1..6 chest grid, the 3 x 3 or the 6 x 3 grid of the same size", container.getBlockPos(), size, layout);
            return null;
        }
        if (isComplete(container) && layout.isChest()) {
            return new ChestMenu(VANILLA_GRIDS.get(layout.rows() - 1), containerId, inventory, container, layout.rows());
        }
        if (isComplete(container) && layout == AflContainerSearchLayout.GRID_3X3) {
            return new DispenserMenu(containerId, inventory, container);
        }
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
