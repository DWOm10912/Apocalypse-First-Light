package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md): what hangs on a {@link WoodFurniture.GunRack}. Slots 0..2: the
 * notches, bottom to top (long guns); 3, 4: the shelf, left and right as seen from the front (small items). The stacks are
 * kept whole (a gun keeps its ammunition, attachments and condition) and sent to clients, which draw them
 * (client WoodGunRackRenderer). No screen and no automation: hands only.
 * <p>
 * On a client the guns are drawn by the chunk (client WoodGunRackChunking, through the model data): new contents from the
 * server ask for fresh model data and a rebuild of the section.
 */
public class WoodGunRackBlockEntity extends BlockEntity {
    public static final int SLOTS = 5;
    /** The chunk's share of the drawing, installed by the client (WoodGunRackChunking); none on a server. */
    public static volatile Function<WoodGunRackBlockEntity, ModelData> chunkData = rack -> ModelData.EMPTY;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public WoodGunRackBlockEntity(BlockPos pos, BlockState state) { super(WoodFurniture.GUN_RACK, pos, state); }

    public ItemStack item(int slot) { return items.get(slot); }

    /** Takes the slot's item out (empty if none). */
    public ItemStack take(int slot) {
        ItemStack out = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        changed();
        return out;
    }

    public void put(int slot, ItemStack stack) {
        items.set(slot, stack);
        changed();
    }

    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        redraw();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        redraw();
    }

    /** Client: the chunk draws the new contents (non-urgent rebuild, off the main thread). */
    private void redraw() {
        if (level == null || !level.isClientSide) return;
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** May be called on a chunk builder thread: the client only hands out what it prepared. */
    @Override
    public @NotNull ModelData getModelData() { return chunkData.apply(this); }

    /** Long guns reach past the block's sides (a 1.4 m rifle overhangs 20 cm each way). */
    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(1.0); }
}
