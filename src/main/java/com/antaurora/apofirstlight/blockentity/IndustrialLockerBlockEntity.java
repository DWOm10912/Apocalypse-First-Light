package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.IndustrialLockerBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflGoodsThemes;
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
import net.minecraft.util.RandomSource;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Industrial Locker V2: 27-slot container with Progressive Container Search (world loot) and an animated Pure Mesh door
 * (AFL Animated Block Mesh Runtime, channel {@code open} driven by the block state's OPEN). Both systems are composed
 * here through their interfaces; neither knows about the other.
 * <p>Goods (2026-10-01, docs/gameplay/container_goods_v1.md): anonymous props at seven spots (tools/build-industrial-locker-v2.mjs
 * GOODS, bones {@code goods_<spot>_<prop>}). The theme follows the loot table (AflGoodsThemes, recorded when the loot is
 * rolled, which now happens when the door is first opened); it decides which props each kind of spot may hold. Spots
 * fill in an order and with props drawn from the position and theme, as many as AflContainerGoods#shown gives for the
 * occupied slots. Clients get the theme name and that count, never the items; nothing is drawn behind a shut door.
 */
public class IndustrialLockerBlockEntity extends RandomizableContainerBlockEntity
        implements AflSearchableContainer, AflAnimatedMeshHost, AflContainerGoods.Themed {
    public static final int SIZE = 27;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/industrial_locker.json");
    /** Formal locker pace: 40 ticks (2 s) per slot, +-15 % seed-derived jitter, no rummaging noise configured yet. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";
    private static final String GOODS_THEME_KEY = "GoodsTheme";
    private static final String SYNC_GOODS = "Goods";
    /** The goods spots (the generator's; _a on the viewer's left) and, per theme, the props each kind of spot may hold. */
    private static final String[] GOODS_SPOTS = {"floor_a", "floor_b", "shelf_a", "shelf_b", "rod", "hook_a", "hook_b"};
    private static final Map<String, Map<String, List<String>>> GOODS_THEMES = Map.of(
            AflGoodsThemes.GENERIC, Map.of("floor", List.of("carton", "holdall"), "shelf", List.of("carton", "thermos"),
                    "rod", List.of("jacket"), "hook", List.of("tote")),
            "industrial", Map.of("floor", List.of("carton", "holdall", "toolbox", "boots"), "shelf", List.of("carton", "thermos", "hard_hat"),
                    "rod", List.of("jacket", "vest"), "hook", List.of("tote", "gloves", "hard_hat")),
            "school", Map.of("floor", List.of("carton", "backpack", "sneakers", "sports_bag", "basketball"), "shelf", List.of("carton", "thermos", "books"),
                    "rod", List.of("jacket"), "hook", List.of("tote", "backpack")));

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    /** Client copy of "search complete" for the world interaction prompt; the server state is authoritative. */
    private boolean clientSearchComplete = true;
    /** Server: recorded when the loot is rolled (null: none yet, generic); client: synced. */
    @Nullable
    private String goodsTheme;
    /** Server: the goods count last sent; client: the synced count. */
    private int goods;
    private List<String> goodsOrder;
    private Set<String> shownGoods = Set.of();

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

    /** Rolling the loot also fixes the goods theme, from the loot table. */
    @Override
    public void unpackLootTable(@Nullable Player player) {
        if (lootTable != null && goodsTheme == null) goodsTheme = AflGoodsThemes.themeFor(lootTable);
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

    // ---- goods ----

    private String goodsThemeName() {
        return goodsTheme != null ? goodsTheme : AflGoodsThemes.GENERIC;
    }

    @Override
    public void setGoodsTheme(String theme) {
        goodsTheme = theme;
        goodsOrder = null;
        showGoods(goods);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private int goodsNow() {
        return AflContainerGoods.shown(AflContainerGoods.occupied(items), GOODS_SPOTS.length);
    }

    private void showGoods(int count) {
        goods = Math.max(0, Math.min(GOODS_SPOTS.length, count));
        if (goodsOrder == null) goodsOrder = goodsOrder(worldPosition, goodsThemeName());
        shownGoods = Set.copyOf(goodsOrder.subList(0, goods));
    }

    /** Spots shuffled, each with one prop its kind may hold in the theme (the two spots of a kind differ when they can). */
    private static List<String> goodsOrder(BlockPos pos, String theme) {
        Map<String, List<String>> props = GOODS_THEMES.getOrDefault(theme, GOODS_THEMES.get(AflGoodsThemes.GENERIC));
        RandomSource random = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L + theme.hashCode());
        String[] spots = GOODS_SPOTS.clone();
        for (int i = spots.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String swap = spots[i];
            spots[i] = spots[j];
            spots[j] = swap;
        }
        Map<String, String> taken = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (String spot : spots) {
            String kind = spot.endsWith("_a") || spot.endsWith("_b") ? spot.substring(0, spot.length() - 2) : spot;
            List<String> options = new ArrayList<>(props.get(kind));
            if (options.size() > 1) options.remove(taken.get(kind));
            String prop = options.get(random.nextInt(options.size()));
            taken.put(kind, prop);
            order.add("goods_" + spot + "_" + prop);
        }
        return List.copyOf(order);
    }

    /** Server: any change of the contents (slots, menus, hoppers, loot) resends the goods count when it moves. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && lootTable == null && goodsNow() != goods) {
            showGoods(goodsNow());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- client sync: the completion flag, the goods theme and count; never items ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        tag.putString(GOODS_THEME_KEY, goodsThemeName());
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
        String theme = tag.getString(GOODS_THEME_KEY);
        if (!theme.equals(goodsThemeName())) {
            goodsTheme = theme;
            goodsOrder = null;
        }
        showGoods(tag.getInt(SYNC_GOODS));
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

    /** Goods: only the shown ones, and nothing behind a shut door (drawn while it is open or still swinging). */
    @Override
    public boolean meshPartVisible(String part) {
        if (!part.startsWith("goods_")) return true;
        if (level == null || !shownGoods.contains(part)) return false;
        return getBlockState().getValue(IndustrialLockerBlock.OPEN) || meshAnimation.sample("open", level.getGameTime()) > 0;
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
        goodsTheme = tag.contains(GOODS_THEME_KEY) ? tag.getString(GOODS_THEME_KEY) : null;
        goodsOrder = null;
        showGoods(goodsNow());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
        search.save(tag);
        if (goodsTheme != null) tag.putString(GOODS_THEME_KEY, goodsTheme);
    }
}
