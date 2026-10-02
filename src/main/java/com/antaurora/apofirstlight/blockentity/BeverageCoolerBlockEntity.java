package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerLayout;
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
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The master cell's block entity: door transitions and the AFL Animated Block Mesh Runtime host (Beverage Cooler V2,
 * tools/build-beverage-cooler-v2.mjs; channels {@code left_open} / {@code right_open}). BlockState owns the durable door
 * poses and is committed {@link BeverageCoolerBlock#ANIMATION_TICKS} after a click; the server announces each started
 * transition with a block event, so clients start the swing at the click instead of at the commit.
 * <p>Contents (2026-10-01): a {@link #SIZE}-slot searchable container (6 x 3 menu, quick search), opened through an open
 * door; no longer a display of real items. What shows on the shelves is the shared goods library
 * (docs/gameplay/container_goods_v1.md), drinks, one product per shelf cell (BeverageCoolerLayout.CELLS), as many cells
 * as the contents call for. Clients get the theme and that count, never the items.
 * <p>Power (2026-10-01, machine_balance/beverage_cooler.json): {@link CompressorAppliance} (buffer, lights, compressor
 * cycle), fed only through the power port on the master's back. Lit = the LIT block state (BeverageCoolerBlock#setLit).
 */
public final class BeverageCoolerBlockEntity extends RandomizableContainerBlockEntity
        implements AflSearchableContainer, AflAnimatedMeshHost, CompressorAppliance.Host, AflContainerGoods.Themed {
    public static final int SIZE = 18;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/beverage_cooler.json");
    /** Goods stand in the open behind the glass: 20 ticks (1 s) a slot, +-15 % seed jitter, no rummaging noise. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(20, 0.15F, 0.0F, 0);
    /** Every shelf cell shows from this many occupied slots on. */
    public static final int GOODS_FULL_AT = 12;
    /** Library products (tools/build-goods-library-v1.mjs): drinks, whatever the theme. */
    public static final Map<String, List<String>> GOODS_PRODUCTS = Map.of(AflGoodsThemes.GENERIC, List.of("cans", "bottles", "cartons"));
    private static final int EVENT_LEFT_DOOR = 1;
    private static final int EVENT_RIGHT_DOOR = 2;
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";

    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private final AflGoodsState goods = new AflGoodsState(BeverageCoolerLayout.CELLS, GOODS_FULL_AT, GOODS_PRODUCTS);
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;
    private Boolean pendingLeft;
    private Boolean pendingRight;
    private long leftFinishTick;
    private long rightFinishTick;
    /** Client: door targets announced by the server and not yet committed to the block state. */
    private Boolean announcedLeft;
    private Boolean announcedRight;
    private final CompressorAppliance power = new CompressorAppliance(this, MachineBalanceManager::beverageCooler,
            AflSounds.BEVERAGE_COOLER_COMPRESSOR_START, AflSounds.BEVERAGE_COOLER_COMPRESSOR_STOP, 1.0F);

    public BeverageCoolerBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.BEVERAGE_COOLER.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction right = getBlockState().getValue(BeverageCoolerBlock.FACING).getCounterClockWise();
        BlockPos other = worldPosition.relative(right);
        return new AABB(Math.min(worldPosition.getX(), other.getX()) - 1.5, worldPosition.getY(),
                Math.min(worldPosition.getZ(), other.getZ()) - 1.5,
                Math.max(worldPosition.getX(), other.getX()) + 2.5, worldPosition.getY() + 2.1,
                Math.max(worldPosition.getZ(), other.getZ()) + 2.5);
    }

    public boolean startDoor(boolean left, boolean targetOpen, long tick) {
        if (left ? pendingLeft != null : pendingRight != null) return false;
        if (left) {
            pendingLeft = targetOpen;
            leftFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        } else {
            pendingRight = targetOpen;
            rightFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        }
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), left ? EVENT_LEFT_DOOR : EVENT_RIGHT_DOOR, targetOpen ? 1 : 0);
        }
        return true;
    }

    public void completeDue(long tick) {
        if (!(getBlockState().getBlock() instanceof BeverageCoolerBlock block) || level == null) return;
        if (pendingLeft != null && tick >= leftFinishTick) {
            boolean target = pendingLeft;
            pendingLeft = null;
            block.commitDoor(level, worldPosition, true, target);
        }
        if (pendingRight != null && tick >= rightFinishTick) {
            boolean target = pendingRight;
            pendingRight = null;
            block.commitDoor(level, worldPosition, false, target);
        }
    }

    public long ticksUntilNextCompletion(long tick) {
        long left = pendingLeft == null ? Long.MAX_VALUE : Math.max(1, leftFinishTick - tick);
        long right = pendingRight == null ? Long.MAX_VALUE : Math.max(1, rightFinishTick - tick);
        return Math.min(left, right) == Long.MAX_VALUE ? 0 : Math.min(left, right);
    }

    /** Server: true broadcasts the door event to nearby clients; client: the announced swing starts now. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_LEFT_DOOR && id != EVENT_RIGHT_DOOR) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            if (id == EVENT_LEFT_DOOR) announcedLeft = param != 0;
            else announcedRight = param != 0;
            refreshMeshAnimationTargets();
        }
        return true;
    }

    private boolean doorTarget(boolean left) {
        Boolean announced = left ? announcedLeft : announcedRight;
        return announced != null ? announced
                : getBlockState().getValue(left ? BeverageCoolerBlock.LEFT_OPEN : BeverageCoolerBlock.RIGHT_OPEN);
    }

    // ---- power: lights and compressor ----

    /** Server, master only (BeverageCoolerBlock#getTicker): world loot is rolled at once (it shows through the glass), then the power. */
    public void serverTick() {
        if (level == null || !(getBlockState().getBlock() instanceof BeverageCoolerBlock)) return;
        if (lootTable != null) unpackLootTable(null);
        power.serverTick();
    }

    /** Synced: the compressor is running (clients play its loop). */
    public boolean compressorRunning() {
        return power.compressorRunning();
    }

    @Override
    public boolean lit() {
        return getBlockState().getValue(BeverageCoolerBlock.LIT);
    }

    @Override
    public void setLit(boolean lit) {
        if (level != null && getBlockState().getBlock() instanceof BeverageCoolerBlock block) block.setLit(level, worldPosition, lit);
    }

    @Override
    public Vec3 compressorPosition() {
        return BeverageCoolerBlock.compressorPosition(worldPosition, getBlockState().getValue(BeverageCoolerBlock.FACING));
    }

    @Override
    public void syncAppliance() {
        sync();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null && getBlockState().getBlock() instanceof BeverageCoolerBlock block
                && block.hasPowerPort(getBlockState(), side)) return power.capability().cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        power.invalidateCaps();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        power.reviveCaps();
    }

    // ---- contents: Progressive Container Search ----

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
        return Component.translatable("block.apocalypse_firstlight.beverage_cooler");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    /** Both doors shut closes every open inventory screen (and so pauses a running search). */
    @Override
    public boolean stillValid(Player player) {
        BlockState state = getBlockState();
        return super.stillValid(player) && (state.getValue(BeverageCoolerBlock.LEFT_OPEN) || state.getValue(BeverageCoolerBlock.RIGHT_OPEN));
    }

    /** Every removal path of the master cell ends here (BeverageCoolerBlock#onRemove): revealed slots drop, unseen loot is lost. */
    public void dropContentsOnce() {
        if (contentsDropped || level == null) return;
        contentsDropped = true;
        AflContainerSearch.dropContentsOnBreak(level, worldPosition, this);
        clearContent();
    }

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

    /** Client: the shelf cells that show, with their products. */
    public List<AflGoodsState.Spot> shownGoods() {
        return goods.shown(worldPosition);
    }

    @Override
    public void setGoodsTheme(String theme) {
        goods.setTheme(theme);
        sync();
    }

    /** Server: any change of the contents resends the goods count when it moves. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && lootTable == null && goods.refresh(items))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- persistence and client sync (power, search completion, goods theme and count; never items) ----

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
        power.save(tag);
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
        power.load(tag);
        clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
        goods.readSync(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
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
        return getBlockState().getValue(BeverageCoolerBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation,
                channel -> "left_open".equals(channel) ? doorTarget(true) : "right_open".equals(channel) && doorTarget(false));
    }

    /** The lit light set (LabPBR emissive, full brightness) while LIT, else the unlit one. */
    @Override
    public boolean meshPartVisible(String part) {
        return switch (part) {
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

    /** The committed state catches up with an announced swing: follow the block state again. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (announcedLeft != null && state.getValue(BeverageCoolerBlock.LEFT_OPEN) == announcedLeft) announcedLeft = null;
        if (announcedRight != null && state.getValue(BeverageCoolerBlock.RIGHT_OPEN) == announcedRight) announcedRight = null;
        refreshMeshAnimationTargets();
    }
}
