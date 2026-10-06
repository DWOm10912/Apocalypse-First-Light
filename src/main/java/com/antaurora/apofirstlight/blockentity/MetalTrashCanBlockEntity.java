package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.MetalTrashCanBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
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
 * Metal Trash Can V2: a {@link #SIZE}-slot container with Progressive Container Search (3 x 3 menu, 40 ticks a slot, the
 * framework's shared rummaging sound) and an animated Pure Mesh lid (AFL Animated Block Mesh Runtime, channel {@code open} driven by the block
 * state's OPEN). World loot is rolled when the lid first opens.
 * <p>Goods (docs/gameplay/container_goods_v1.md): up to three closed black bags stacked inside (bones
 * {@code goods_bag_0..2}, bottom first), as many as AflContainerGoods#shown gives for the occupied slots; drawn only while
 * the lid is open or moving. Clients get that count, never the items.
 */
public final class MetalTrashCanBlockEntity extends RandomizableContainerBlockEntity implements AflSearchableContainer, AflAnimatedMeshHost {
    public static final int SIZE = 9;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/metal_trash_can.json");
    /** 40 ticks (2 s) a slot, +-15 % seed jitter, no rummaging noise for the infected (the audible sound is the framework's). */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);
    public static final int BAGS = 3;
    /** All three bags from this many occupied slots on (1-2 slots: one bag, 3-4: two). */
    public static final int BAGS_FULL_AT = 6;
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";
    private static final String SYNC_GOODS = "Goods";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;
    /** Server: the bag count last sent; client: the synced count. */
    private int bags;

    public MetalTrashCanBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.METAL_TRASH_CAN.get(), position, state);
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
        return Component.translatable("block.apocalypse_firstlight.metal_trash_can");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    /** Shutting the lid closes every open inventory screen (and so pauses a running search). */
    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && getBlockState().getValue(MetalTrashCanBlock.OPEN);
    }

    /** Every removal path ends here (MetalTrashCanBlock#onRemove): revealed slots drop, unseen loot is lost. */
    public void dropContentsOnce() {
        if (contentsDropped || level == null) return;
        contentsDropped = true;
        AflContainerSearch.dropContentsOnBreak(level, worldPosition, this);
        clearContent();
    }

    // ---- Progressive Container Search ----

    @Override
    public AflContainerSearchState aflSearchState() {
        return search;
    }

    @Override
    public AflContainerSearchSettings aflSearchSettings() {
        return SEARCH_SETTINGS;
    }

    @Override
    public AflContainerSearchLayout aflSearchLayout() {
        return AflContainerSearchLayout.GRID_3X3;
    }

    /** World loot (a pending loot table) is searched; anything a player placed is plain storage. */
    @Override
    public boolean aflSearchRequiredOnInit() {
        return !placedByPlayer && lootTable != null;
    }

    public void markPlacedByPlayer() {
        placedByPlayer = true;
        AflContainerSearch.isComplete(this);
        placedByPlayer = false;
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

    public boolean isSearchCompleteForPrompt() {
        return level != null && level.isClientSide ? clientSearchComplete : AflContainerSearch.isComplete(this);
    }

    // ---- goods: the bags ----

    private int bagsNow() {
        return AflContainerGoods.shown(AflContainerGoods.occupied(items), BAGS, BAGS_FULL_AT);
    }

    /** Server: any change of the contents (slots, menus, hoppers, loot) resends the bag count when it moves. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && lootTable == null && bagsNow() != bags) {
            bags = bagsNow();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- client sync: the completion flag and the bag count; never items ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        tag.putInt(SYNC_GOODS, lootTable == null ? bagsNow() : 0);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
        bags = Math.max(0, Math.min(BAGS, tag.getInt(SYNC_GOODS)));
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
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
        return getBlockState().getValue(MetalTrashCanBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation, this::meshChannelTarget);
    }

    /** Channel targets from the block state (both sides: also the hit mesh's pose, meshhit/AnimatedMeshHits). */
    @Override
    public boolean meshChannelTarget(String channel) {
        return "open".equals(channel) && getBlockState().getValue(MetalTrashCanBlock.OPEN);
    }

    /** Bags: the first {@link #bags}, bottom up, and none under a shut lid (drawn while it is open or still moving). */
    @Override
    public boolean meshPartVisible(String part) {
        if (!part.startsWith("goods_bag_")) return true;
        if (level == null || part.charAt(part.length() - 1) - '0' >= bags) return false;
        return getBlockState().getValue(MetalTrashCanBlock.OPEN) || meshAnimation.sample("open", level.getGameTime()) > 0;
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
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
        bags = bagsNow();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        search.save(tag);
    }
}
