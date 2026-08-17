package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MetalLockerBlockEntity extends RandomizableContainerBlockEntity implements MenuProvider {
    public static final int SIZE = 36;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    public MetalLockerBlockEntity(BlockPos position, BlockState state) { super(AflBlockEntities.METAL_LOCKER.get(), position, state); }
    @Override public int getContainerSize() { return SIZE; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return Component.translatable("block.apocalypse_firstlight.metal_locker"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new ChestMenu(MenuType.GENERIC_9x4, id, inventory, this, 4); }
    public boolean canOpen(Player player) { return !isRemoved() && level != null && level.getBlockState(worldPosition).getBlock() == AflBlocks.METAL_LOCKER.get(); }
    public void dropContentsOnce() { if (contentsDropped || level == null) return; contentsDropped = true; Containers.dropContents(level, worldPosition, this); clearContent(); }
    @Override public void load(CompoundTag tag) { super.load(tag); items = NonNullList.withSize(SIZE, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items); }
}
