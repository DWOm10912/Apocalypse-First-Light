package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No Container/capability: hoppers cannot bypass intact glass. */
public final class VendingMachineBlockEntity extends BlockEntity {
    public static final int ROWS = 4;
    public static final int COLUMNS = 3;
    public static final int SIZE = ROWS * COLUMNS;
    private static final int LAYOUT_VERSION = 2;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE,ItemStack.EMPTY);
    public VendingMachineBlockEntity(BlockPos p, BlockState s) { super(AflBlockEntities.VENDING_MACHINE.get(),p,s); }
    public ItemStack getItem(int slot) { return items.get(slot); }
    public static double displayX(int slot) { return (5.63 + slot%COLUMNS*3.6)/16; }
    public static double displayY(int slot) { return (9.35 + slot/COLUMNS*4.7)/16; }
    public static final double DISPLAY_Z = 4.4/16;
    public boolean put(int slot, ItemStack stack) {
        if (!getBlockState().getValue(VendingMachineBlock.BROKEN) || stack.isEmpty() || !items.get(slot).isEmpty()) return false;
        items.set(slot,stack.copyWithCount(1)); sync(); return true;
    }
    public ItemStack take(int slot) {
        if (!getBlockState().getValue(VendingMachineBlock.BROKEN)) return ItemStack.EMPTY;
        ItemStack out=items.set(slot,ItemStack.EMPTY); sync(); return out;
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i=0;i<SIZE;i++) {
            ItemStack out=items.set(i,ItemStack.EMPTY);
            if (!out.isEmpty()) Block.popResource(level,worldPosition,out);
        }
        setChanged();
    }
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items=NonNullList.withSize(SIZE,ItemStack.EMPTY);
        if (tag.getInt("LayoutVersion") >= LAYOUT_VERSION) {
            ContainerHelper.loadAllItems(tag,items);
        } else {
            // V1 stored the left and right lanes as 4x2; keep both in their visual lanes.
            NonNullList<ItemStack> previous=NonNullList.withSize(8,ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag,previous);
            for (int row=0;row<ROWS;row++) {
                items.set(row*COLUMNS,previous.get(row*2));
                items.set(row*COLUMNS+2,previous.get(row*2+1));
            }
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("LayoutVersion",LAYOUT_VERSION);
        ContainerHelper.saveAllItems(tag,items);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0,1,0); }
}
