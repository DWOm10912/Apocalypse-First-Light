package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A kitchen stove's cooktop (Cooking V1 K1c, {@link KitchenStoves}): the pots standing on it, one a spot, each on the
 * burner it was put on (the range's front or back burner of its pair). Sent to clients for client/CooktopRenderer.
 */
public class CooktopBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final int[] burner = new int[2];

    public CooktopBlockEntity(BlockPos pos, BlockState state) { super(KitchenStoves.COOKTOP, pos, state); }

    public ItemStack item(int spot) { return items.get(spot); }

    public int burner(int spot) { return burner[spot]; }

    public void put(int spot, int on, ItemStack stack) {
        items.set(spot, stack);
        burner[spot] = on;
        changed();
    }

    public ItemStack take(int spot) {
        ItemStack out = items.get(spot);
        items.set(spot, ItemStack.EMPTY);
        changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** The cell and the pots standing over its top. */
    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0, 0.5, 0).inflate(0.3); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
        tag.putIntArray("Burner", burner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        int[] b = tag.getIntArray("Burner");
        for (int k = 0; k < burner.length; k++) burner[k] = k < b.length ? b[k] : 0;
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
