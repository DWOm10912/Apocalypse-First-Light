package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CommercialDumpsterBlock;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Commercial Dumpster V2, on the MASTER cell: an {@link #SIZE}-slot container with Progressive Container Search (6 x 3
 * menu, 40 ticks a slot, the framework's shared search sound) and the whole dumpster's mesh (AFL Animated Block Mesh
 * Runtime; the profile follows the block's colour; channels {@code left_open} / {@code right_open} driven by the block
 * state). World loot is rolled when a lid first opens; searching needs a lid open.
 * <p>Goods (docs/gameplay/container_goods_v1.md): per half two big bags, a flattened carton and a bag on top (bones
 * {@code goods_<side>_<k>}, tools/build-commercial-dumpster-v2.mjs), filled in turn on both halves (the first half from the
 * position), as many as AflContainerGoods#shown gives for the occupied slots; a half's goods are drawn only while its lid
 * is open or moving. Clients get that count, never the items.
 */
public final class CommercialDumpsterBlockEntity extends RandomizableContainerBlockEntity implements AflSearchableContainer, AflAnimatedMeshHost {
    public static final int SIZE = 18;
    /** 40 ticks (2 s) a slot, +-15 % seed jitter, no rummaging noise for the infected. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);
    public static final int GOODS = 8;
    /** All eight goods from this many occupied slots on (about one per 1.75 slots). */
    public static final int GOODS_FULL_AT = 14;
    /** Within a half: the two floor bags, the carton, the bag on top. */
    private static final int[] SPOT_ORDER = {0, 1, 3, 2};
    private static final ResourceLocation FALLBACK_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/commercial_dumpster_green.json");
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";
    private static final String SYNC_GOODS = "Goods";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;
    /** Server: the goods count last sent; client: the synced count. */
    private int goods;
    private List<String> goodsOrder;

    public CommercialDumpsterBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.COMMERCIAL_DUMPSTER.get(), position, state);
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
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    /** Shutting the last open lid closes every open inventory screen (and so pauses a running search). */
    @Override
    public boolean stillValid(Player player) {
        BlockState state = getBlockState();
        return super.stillValid(player) && state.getBlock() instanceof CommercialDumpsterBlock
                && (state.getValue(CommercialDumpsterBlock.LEFT_OPEN) || state.getValue(CommercialDumpsterBlock.RIGHT_OPEN));
    }

    /** Every removal path ends here (CommercialDumpsterBlock#onRemove): revealed slots drop, unseen loot is lost. */
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
        return AflContainerSearchLayout.GRID_6X3;
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

    // ---- goods ----

    private int goodsNow() {
        return AflContainerGoods.shown(AflContainerGoods.occupied(items), GOODS, GOODS_FULL_AT);
    }

    /** Both halves in turn, the first half from the position (the same on every client and after reloading). */
    private List<String> goodsOrder() {
        if (goodsOrder == null) {
            boolean leftFirst = (worldPosition.asLong() * 0x9E3779B97F4A7C15L >>> 63) == 0;
            String first = leftFirst ? "left" : "right", second = leftFirst ? "right" : "left";
            List<String> order = new ArrayList<>(GOODS);
            for (int k : SPOT_ORDER) {
                order.add("goods_" + first + "_" + k);
                order.add("goods_" + second + "_" + k);
            }
            goodsOrder = List.copyOf(order);
        }
        return goodsOrder;
    }

    /** Server: any change of the contents (slots, menus, hoppers, loot) resends the goods count when it moves. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && lootTable == null && goodsNow() != goods) {
            goods = goodsNow();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- client sync: the completion flag and the goods count; never items ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        tag.putInt(SYNC_GOODS, lootTable == null ? goodsNow() : 0);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
        goods = Math.max(0, Math.min(GOODS, tag.getInt(SYNC_GOODS)));
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    public ResourceLocation meshProfile() {
        return getBlockState().getBlock() instanceof CommercialDumpsterBlock block ? block.meshProfile() : FALLBACK_PROFILE;
    }

    @Override
    public AflBlockMeshAnimationState meshAnimation() {
        return meshAnimation;
    }

    @Override
    public Direction meshFacing() {
        return getBlockState().getValue(CommercialDumpsterBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        if (!(getBlockState().getBlock() instanceof CommercialDumpsterBlock)) return;
        AflAnimatedMeshHost.refreshTargets(level, meshProfile(), meshAnimation, this::meshChannelTarget);
    }

    /** Channel targets from the block state (both sides: also the hit mesh's pose, meshhit/AnimatedMeshHits). */
    @Override
    public boolean meshChannelTarget(String channel) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CommercialDumpsterBlock)) return false;
        return "left_open".equals(channel) ? state.getValue(CommercialDumpsterBlock.LEFT_OPEN)
                : "right_open".equals(channel) && state.getValue(CommercialDumpsterBlock.RIGHT_OPEN);
    }

    /** Goods: the first {@link #goods} of the fill order, each only while its half's lid is open or still moving. */
    @Override
    public boolean meshPartVisible(String part) {
        if (!part.startsWith("goods_")) return true;
        if (level == null || !goodsOrder().subList(0, goods).contains(part)) return false;
        boolean left = part.startsWith("goods_left_");
        BlockState state = getBlockState();
        return state.getBlock() instanceof CommercialDumpsterBlock
                && (state.getValue(left ? CommercialDumpsterBlock.LEFT_OPEN : CommercialDumpsterBlock.RIGHT_OPEN)
                || meshAnimation.sample(left ? "left_open" : "right_open", level.getGameTime()) > 0);
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
        return AflAnimatedMeshHost.renderBounds(worldPosition, meshProfile(), meshFacing());
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
        goods = goodsNow();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        search.save(tag);
    }
}
