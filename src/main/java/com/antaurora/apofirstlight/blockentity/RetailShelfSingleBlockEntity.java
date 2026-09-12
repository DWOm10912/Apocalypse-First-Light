package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.RetailShelfLayout;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RetailShelfSingleBlockEntity extends BlockEntity implements Container {
    public static final int SIZE = RetailShelfLayout.SLOTS;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    // Old hopper-accessible shelves could contain stacks above one. Keep the excess recoverable
    // without allowing any visible display slot to hold more than one item.
    private NonNullList<ItemStack> legacyOverflow = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;

    public RetailShelfSingleBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.RETAIL_SHELF_SINGLE.get(), position, state);
    }

    public boolean isEmpty(int slot) {
        return items.get(slot).isEmpty();
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public int getMaxStackSize() {
        return RetailShelfLayout.MAX_STACK_PER_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < SIZE && items.get(slot).isEmpty() && !stack.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            refillLegacySlot(slot);
            sync();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) refillLegacySlot(slot);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        sync();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        legacyOverflow.replaceAll(ignored -> ItemStack.EMPTY);
        sync();
    }

    public void insertOne(int slot, ItemStack source) {
        if (canPlaceItem(slot, source)) {
            setItem(slot, source);
        }
    }

    public ItemStack removeOne(int slot) {
        ItemStack removed = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        refillLegacySlot(slot);
        sync();
        return removed;
    }

    public void dropContentsOnce() {
        if (contentsDropped || level == null) {
            return;
        }
        contentsDropped = true;
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                Block.popResource(level, worldPosition, item.copy());
            }
        }
        for (ItemStack item : legacyOverflow) {
            if (!item.isEmpty()) {
                Block.popResource(level, worldPosition, item.copy());
            }
        }
        items.replaceAll(ignored -> ItemStack.EMPTY);
        legacyOverflow.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    private void refillLegacySlot(int slot) {
        ItemStack reserve = legacyOverflow.get(slot);
        if (!items.get(slot).isEmpty() || reserve.isEmpty()) return;
        items.set(slot, reserve.copyWithCount(1));
        reserve.shrink(1);
        if (reserve.isEmpty()) legacyOverflow.set(slot, ItemStack.EMPTY);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        legacyOverflow = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.contains("LegacyOverflow", 10)) {
            ContainerHelper.loadAllItems(tag.getCompound("LegacyOverflow"), legacyOverflow);
        }
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.getCount() > 1) {
                ItemStack reserve = legacyOverflow.get(slot);
                if (reserve.isEmpty()) {
                    legacyOverflow.set(slot, stack.copyWithCount(stack.getCount() - 1));
                } else if (ItemStack.isSameItemSameTags(stack, reserve)) {
                    reserve.grow(stack.getCount() - 1);
                }
                items.set(slot, stack.copyWithCount(1));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (legacyOverflow.stream().anyMatch(stack -> !stack.isEmpty())) {
            CompoundTag overflow = new CompoundTag();
            ContainerHelper.saveAllItems(overflow, legacyOverflow);
            tag.put("LegacyOverflow", overflow);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
