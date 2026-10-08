package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflGoodsState;
import com.antaurora.apofirstlight.containersearch.AflGoodsThemes;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.antaurora.apofirstlight.energy.CompressorAppliance;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Vending machine (lower half owns it): since 2026-10-01 a {@link #SIZE}-slot searchable container (3 x 3 menu, quick
 * search), reachable only once the glass is broken (VendingMachineBlock#use; the menu closes when it is not). Behind the
 * glass: the shared goods library (docs/gameplay/container_goods_v1.md), drinks and snacks, one product per lane
 * ({@link #ROWS} x {@link #COLUMNS}), as many lanes as the contents call for; world loot is rolled on the first server
 * tick so they show through intact glass. Hoppers and item handlers reach it only once the glass is broken, then under
 * the search framework's rules. Clients get the theme and that count, never the items.
 *
 * <p>V2 (2026-10-01): drawn by the AFL Animated Block Mesh Runtime (block_mesh_profiles/vending_machine.json, no
 * animation): the 'glass' part hides once the glass is broken (an empty frame, no shards), 'lights' / 'lights_lit' follow
 * LIT. Power (machine_balance/vending_machine.json): {@link CompressorAppliance} in lights-only mode, fed only through the
 * power port on the lower half's back.
 */
public final class VendingMachineBlockEntity extends RandomizableContainerBlockEntity
        implements com.antaurora.apofirstlight.energy.PlugCord.Owner, AflSearchableContainer, AflContainerGoods.Themed, AflAnimatedMeshHost, CompressorAppliance.Host {
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/vending_machine.json");
    public static final int ROWS = 4;
    public static final int COLUMNS = 3;
    public static final int LANES = ROWS * COLUMNS;
    public static final int SIZE = 9;
    /** 20 ticks (1 s) a slot, +-15 % seed jitter, no rummaging noise. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(20, 0.15F, 0.0F, 0);
    /** Every lane shows from this many occupied slots on. */
    public static final int GOODS_FULL_AT = 6;
    public static final Map<String, List<String>> GOODS_PRODUCTS = Map.of(AflGoodsThemes.GENERIC, List.of("cans", "bottles", "bags"));
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflGoodsState goods = new AflGoodsState(LANES, GOODS_FULL_AT, GOODS_PRODUCTS);
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;
    /** Mesh parts 'coil_<lane>' (tools/build-vending-machine-v2.mjs COIL_BONES) to their lanes. */
    private static final Map<String, Integer> COIL_LANES = java.util.stream.IntStream.range(0, LANES).boxed()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(lane -> "coil_" + lane, lane -> lane));
    /** Client, render only: the lanes the last {@link #shownGoods()} filled. */
    private int shownLanes;
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private final CompressorAppliance power = new CompressorAppliance(this, MachineBalanceManager::vendingMachine).cord(this::cordGeometry);

    public VendingMachineBlockEntity(BlockPos p, BlockState s) {
        super(AflBlockEntities.VENDING_MACHINE.get(), p, s);
    }

    /**
     * A lane (tools/build-vending-machine-v2.mjs LANE_X / TRAY_TOPS / GOODS_FRONT_Z), source px: the block's bottom centre
     * at the origin, +x the viewer's left, front toward -z. Lane = row * COLUMNS + column; row 0 at the bottom, column 0
     * on the viewer's left.
     */
    public static double laneX(int lane) {
        return 5.2 - lane % COLUMNS * 3.2;
    }

    /** The lane's tray top. */
    public static double laneY(int lane) {
        return 7.9 + lane / COLUMNS * 4.75;
    }

    /** The products' front edge. */
    public static final double LANE_FRONT_Z = -6.4;

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
        return Component.translatable("block.apocalypse_firstlight.vending_machine");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    /** The glass has to be broken. */
    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && getBlockState().getValue(VendingMachineBlock.BROKEN);
    }

    /** Every removal path ends here (VendingMachineBlock#onRemove): revealed slots drop, unseen loot is lost. */
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

    /**
     * Server, every tick: world loot is rolled at once (not on first opening), so the goods seen from outside show how
     * much it holds; then the lights' power.
     */
    public void serverTick() {
        if (lootTable != null) unpackLootTable(null);
        power.serverTick();
    }

    // ---- power: lights only ----

    @Override
    public boolean lit() {
        return getBlockState().getValue(VendingMachineBlock.LIT);
    }

    @Override
    public void setLit(boolean lit) {
        if (level != null && getBlockState().getBlock() instanceof VendingMachineBlock block) block.setLit(level, worldPosition, lit);
    }

    /** No compressor: never used. */
    @Override
    public Vec3 compressorPosition() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public void syncAppliance() {
        setChanged();
    }

    /** Hoppers (and every other automation) only through broken glass. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return getBlockState().getValue(VendingMachineBlock.BROKEN) && AflContainerSearch.canPlaceItem(this, slot) && super.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return getBlockState().getValue(VendingMachineBlock.BROKEN) && AflContainerSearch.canTakeItem(this, slot) && super.canTakeItem(target, slot, stack);
    }

    /** Energy only through the power port; items only through broken glass. */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && !getBlockState().getValue(VendingMachineBlock.BROKEN))
            return LazyOptional.empty();
        return super.getCapability(capability, side);
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

    /** Client: the lanes that show, with their products; also notes them for the coil fronts (VendingMachineRenderer calls it every frame, before the mesh). */
    public List<AflGoodsState.Spot> shownGoods() {
        List<AflGoodsState.Spot> spots = goods.shown(worldPosition);
        int lanes = 0;
        for (AflGoodsState.Spot spot : spots) lanes |= 1 << spot.cell();
        shownLanes = lanes;
        return spots;
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

    // ---- persistence and client sync (search completion, goods theme and count; never items) ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
        goods.load(tag, items);
        power.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        search.save(tag);
        goods.save(tag);
        power.save(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        goods.writeSync(tag, items, lootTable != null);
        power.plugCord().writeSync(tag);
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
        power.plugCord().load(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    @Override
    public AABB getRenderBoundingBox() {
        return power.plugCord().renderBounds(AflAnimatedMeshHost.renderBounds(worldPosition, MESH_PROFILE, meshFacing()));
    }

    /** Power cord (tools/build-vending-machine-v2.mjs CORD): the C14 inlet on the back panel, low at the viewer's left corner. */
    private PlugCord.Geometry cordGeometry() {
        return PlugCord.appliance(worldPosition, meshFacing(), 6.0, 2.4, 7.7, -7.8, 7.8, 0, 31.8);
    }

    @Override public PlugCord plugCord() { return power.plugCord(); }

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
        return getBlockState().getValue(VendingMachineBlock.FACING);
    }

    /** No animation channels. */
    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation, this::meshChannelTarget);
    }

    @Override
    public boolean meshChannelTarget(String channel) {
        return false;
    }

    /**
     * No glass once broken; the lit light set (LabPBR emissive, full brightness) while LIT, else the unlit one; a lane's
     * coil front ('coil_<lane>', where the products stand) only while the lane shows no goods: the products are wider
     * than the coil and would cut through it.
     */
    @Override
    public boolean meshPartVisible(String part) {
        Integer lane = COIL_LANES.get(part);
        if (lane != null) return (shownLanes & 1 << lane) == 0;
        return switch (part) {
            case "glass" -> !getBlockState().getValue(VendingMachineBlock.BROKEN);
            case "lights" -> !lit();
            case "lights_lit" -> lit();
            default -> true;
        };
    }

    @Override
    public boolean meshPartEmissive(String part) {
        return "lights_lit".equals(part);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }
}
