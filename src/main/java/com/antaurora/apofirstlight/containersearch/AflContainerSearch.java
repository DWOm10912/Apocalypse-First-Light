package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
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
     * ordinary vanilla chest menu. Supports 9 x 1..6 grids; returns null (menu not opened) for other sizes.
     */
    @Nullable
    public static AbstractContainerMenu createMenu(int containerId, Inventory inventory,
                                                   AflSearchableContainer container) {
        int size = container.getContainerSize();
        int rows = size / 9;
        if (size % 9 != 0 || rows < 1 || rows > 6) {
            ApocalypseFirstLight.LOGGER.error("Searchable container at {} has {} slots; the chest-grid search menu "
                    + "needs 9, 18, 27, 36, 45 or 54", container.getBlockPos(), size);
            return null;
        }
        if (isComplete(container)) {
            return new ChestMenu(VANILLA_GRIDS.get(rows - 1), containerId, inventory, container, rows);
        }
        return AflContainerSearchMenu.server(containerId, inventory, container, rows);
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
