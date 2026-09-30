package com.antaurora.apofirstlight.dev.containersearch;

import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * DEVELOPMENT ONLY. Reference integration of {@link AflSearchableContainer} on top of an existing
 * RandomizableContainerBlockEntity; production containers add the same forwarding overrides.
 */
public final class DevSearchCrateBlockEntity extends RandomizableContainerBlockEntity implements AflSearchableContainer {
    private static final int SIZE = 18;
    /** Framework default timing plus an audible noise hook; not a balance proposal for any real asset. */
    private static final AflContainerSearchSettings SETTINGS = new AflContainerSearchSettings(40, 0.2F, 8.0F, 40);

    private final AflContainerSearchState search = new AflContainerSearchState();
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    DevSearchCrateBlockEntity(BlockPos pos, BlockState state) {
        super(DevSearchCrateRegistration.blockEntityType(), pos, state);
    }

    @Override
    public AflContainerSearchState aflSearchState() {
        return search;
    }

    @Override
    public AflContainerSearchSettings aflSearchSettings() {
        return SETTINGS;
    }

    @Override
    public boolean aflSearchRequiredOnInit() {
        return lootTable != null;
    }

    @Override
    public void unpackLootTable(@Nullable Player player) {
        AflContainerSearch.beforeLootUnpack(this);
        super.unpackLootTable(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return AflContainerSearch.canPlaceItem(this, slot) && super.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return AflContainerSearch.canTakeItem(this, slot) && super.canTakeItem(target, slot, stack);
    }

    @Override
    protected IItemHandler createUnSidedHandler() {
        return AflContainerSearch.itemHandler(this);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return AflContainerSearch.createMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Dev Search Crate");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
        search.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
        search.save(tag);
    }
}
