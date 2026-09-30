package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.IndustrialLockerBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Industrial Locker V2: 27-slot container with Progressive Container Search (world loot) and an animated Pure Mesh door
 * (AFL Animated Block Mesh Runtime, channel {@code open} driven by the block state's OPEN). Both systems are composed
 * here through their interfaces; neither knows about the other.
 */
public class IndustrialLockerBlockEntity extends RandomizableContainerBlockEntity
        implements AflSearchableContainer, AflAnimatedMeshHost {
    public static final int SIZE = 27;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/industrial_locker.json");
    /** Formal locker pace: 40 ticks (2 s) per slot, +-15 % seed-derived jitter, no rummaging noise configured yet. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    /** Client copy of "search complete" for the world interaction prompt; the server state is authoritative. */
    private boolean clientSearchComplete = true;

    public IndustrialLockerBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.INDUSTRIAL_LOCKER.get(), position, state);
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
        return Component.translatable("block.apocalypse_firstlight.industrial_locker");
    }

    /** Three-row grid: the search menu while anything is hidden, afterwards the ordinary chest menu. */
    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    public boolean canOpen(Player player) {
        return !isRemoved() && level != null && level.getBlockState(worldPosition).getBlock() == AflBlocks.INDUSTRIAL_LOCKER.get();
    }

    /** Closing the door closes every open inventory screen (and so pauses a running search). */
    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && getBlockState().getValue(IndustrialLockerBlock.OPEN);
    }

    /** Every removal path (player, support loss, explosion) ends here: revealed slots drop, unseen loot is lost. */
    public void dropContentsOnce() {
        if (contentsDropped || level == null) {
            return;
        }
        contentsDropped = true;
        AflContainerSearch.dropContentsOnBreak(level, worldPosition, this);
        clearContent();
    }

    // ---- Progressive Container Search contract ----

    @Override
    public AflContainerSearchState aflSearchState() {
        return search;
    }

    @Override
    public AflContainerSearchSettings aflSearchSettings() {
        return SEARCH_SETTINGS;
    }

    /** World loot (a pending loot table) is searched; anything a player placed is plain storage. */
    @Override
    public boolean aflSearchRequiredOnInit() {
        return !placedByPlayer && lootTable != null;
    }

    /** Called from the block's setPlacedBy: commits "fully revealed" persistently at placement time. */
    public void markPlacedByPlayer() {
        placedByPlayer = true;
        AflContainerSearch.isComplete(this);
        placedByPlayer = false;   // committed; later re-initializations (e.g. /data reset) decide from the NBT alone
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
    public void onAflSearchCompleted(ServerLevel level) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Prompt state: server authoritative, client from the synchronized flag. */
    public boolean isSearchCompleteForPrompt() {
        return level != null && level.isClientSide ? clientSearchComplete : AflContainerSearch.isComplete(this);
    }

    // ---- client sync: only the completion flag, never items ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    public ResourceLocation meshProfile() {
        return MESH_PROFILE;
    }

    @Override
    public AflBlockMeshAnimationState meshAnimation() {
        return meshAnimation;
    }

    @Override
    public Direction meshFacing() {
        return getBlockState().getValue(IndustrialLockerBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation,
                channel -> "open".equals(channel) && getBlockState().getValue(IndustrialLockerBlock.OPEN));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        refreshMeshAnimationTargets();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return AflAnimatedMeshHost.renderBounds(worldPosition, MESH_PROFILE, meshFacing());
    }

    // ---- persistence ----

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
