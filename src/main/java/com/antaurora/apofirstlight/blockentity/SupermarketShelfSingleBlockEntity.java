package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SupermarketShelfSingleBlockEntity extends BlockEntity {
    public static final int SIZE = 12;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;

    public SupermarketShelfSingleBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.SUPERMARKET_SHELF_SINGLE.get(), position, state);
    }

    public boolean isEmpty(int slot) {
        return items.get(slot).isEmpty();
    }

    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    public void insertOne(int slot, ItemStack source) {
        items.set(slot, source.copyWithCount(1));
        sync();
    }

    public ItemStack removeOne(int slot) {
        ItemStack removed = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
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
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
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
