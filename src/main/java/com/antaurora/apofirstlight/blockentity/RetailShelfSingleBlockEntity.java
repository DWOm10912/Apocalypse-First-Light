package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.RetailShelfLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflGoodsState;
import com.antaurora.apofirstlight.containersearch.AflGoodsThemes;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

import java.util.List;
import java.util.Map;

/**
 * Retail shelf (lower half owns it): since 2026-10-01 a {@link #SIZE}-slot searchable container (3 x 3 menu, quick
 * search: the goods stand in the open), no longer a display of real items. What shows on the decks is the shared goods
 * library (docs/gameplay/container_goods_v1.md), one product per deck cell (RetailShelfLayout.CELLS), as many cells as the
 * contents call for, products by the goods theme (from the loot table); world loot is rolled on the first server tick, so
 * the decks show it from the start. Clients get the theme and that count, never the items.
 */
public class RetailShelfSingleBlockEntity extends RandomizableContainerBlockEntity implements AflSearchableContainer, AflContainerGoods.Themed {
    public static final int SIZE = 9;
    /** Goods stand in the open: 20 ticks (1 s) a slot, +-15 % seed jitter, no rummaging noise. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(20, 0.15F, 0.0F, 0);
    /** Every deck cell shows from this many occupied slots on. */
    public static final int GOODS_FULL_AT = 6;
    /** Library products per theme (tools/build-goods-library-v1.mjs). */
    public static final Map<String, List<String>> GOODS_PRODUCTS = Map.of(
            AflGoodsThemes.GENERIC, List.of("boxes", "cans", "bottles", "jars", "bags"),
            "grocery", List.of("boxes", "cans", "bottles", "jars", "bags", "cartons"),
            "pharmacy", List.of("pill_boxes", "med_bottles", "tubes", "boxes"),
            "hardware", List.of("paint_cans", "spray_cans", "part_boxes", "boxes"),
            "industrial", List.of("paint_cans", "spray_cans", "part_boxes", "boxes"));
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflGoodsState goods = new AflGoodsState(RetailShelfLayout.CELLS, GOODS_FULL_AT, GOODS_PRODUCTS);
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;

    public RetailShelfSingleBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.RETAIL_SHELF_SINGLE.get(), position, state);
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
        return Component.translatable("block.apocalypse_firstlight.retail_shelf_single");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    /** Every removal path (player, support loss, explosion) ends here: revealed slots drop, unseen loot is lost. */
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

    @Override
    public boolean aflSearchRequiredOnInit() {
        return !placedByPlayer && lootTable != null;
    }

    public void markPlacedByPlayer() {
        placedByPlayer = true;
        AflContainerSearch.isComplete(this);
        placedByPlayer = false;
    }

    /** Rolling the loot also fixes the goods theme, from the loot table. */
    @Override
    public void unpackLootTable(@Nullable Player player) {
        goods.recordTheme(lootTable);
        AflContainerSearch.beforeLootUnpack(this);
        super.unpackLootTable(player);
    }

    /** Server, every tick: world loot is rolled at once (not on first opening), so the goods seen from outside show how much it holds. */
    public void serverTick() {
        if (lootTable != null) unpackLootTable(null);
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

    /** Client: the deck cells that show, with their products. */
    public List<AflGoodsState.Spot> shownGoods() {
        return goods.shown(worldPosition);
    }

    @Override
    public void setGoodsTheme(String theme) {
        goods.setTheme(theme);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Server: any change of the contents resends the goods count when it moves. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && lootTable == null && goods.refresh(items))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- client sync: search completion, goods theme and count; never items ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        goods.writeSync(tag, items, lootTable != null);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
        goods.readSync(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 1, 0);
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
        goods.load(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        search.save(tag);
        goods.save(tag);
    }
}
