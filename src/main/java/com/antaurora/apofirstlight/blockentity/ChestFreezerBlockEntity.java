package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.antaurora.apofirstlight.energy.CompressorAppliance;
import com.antaurora.apofirstlight.energy.PlugCord;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * Only the LEFT (master) cell owns a BE. The stable lid state is persisted by BlockState and committed
 * {@link ChestFreezerBlock#ANIMATION_TICKS} after a click; the server announces each started slide with a block event,
 * so clients start it at the click. AFL Animated Block Mesh Runtime host (Chest Freezer V2,
 * tools/build-chest-freezer-v2.mjs; channels {@code left_open} / {@code right_open}).
 * <p>Power (machine_balance/chest_freezer.json): {@link CompressorAppliance}, fed only through the power port on the
 * master's back. Lit = {@link #powered()}, stored here and synced: the status display ("-18°C", ChestFreezerRenderer) and
 * the power LED. The compressor sounds play at pitch {@link #COMPRESSOR_PITCH} (the beverage cooler's sounds, deeper).
 * <p>Contents (2026-10-01): {@link #SIZE} slots with Progressive Container Search (world loot), the 6 x 3 menu, opened
 * from the well of the open half (ChestFreezerBlock). World loot is rolled on the first server tick, not on the first
 * opening, so the goods seen through the glass follow how much it holds from the start. Goods: the mesh's anonymous
 * frozen goods (bones {@code goods_<compartment>_<arrangement>}); the compartments fill in an order and with
 * arrangements drawn from the position, as many as {@link #goodsFor(int)} gives for the occupied slots (hidden ones
 * included: the glass shows how full it is, never what). Clients get that count, never the items.
 */
public final class ChestFreezerBlockEntity extends RandomizableContainerBlockEntity
        implements com.antaurora.apofirstlight.energy.PlugCord.Owner, AflSearchableContainer, AflAnimatedMeshHost, CompressorAppliance.Host {
    public static final int SIZE = 18;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/chest_freezer.json");
    public static final float COMPRESSOR_PITCH = 0.9F;
    /** The lead chest's pace: 40 ticks (2 s) per slot, +-15 % seed jitter, no rummaging noise yet. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);
    /** tools/build-chest-freezer-v2.mjs GOODS: wire compartments, arrangements per compartment. */
    public static final int GOODS_COMPARTMENTS = 8, GOODS_ARRANGEMENTS = 3;
    private static final int EVENT_LID = 1;
    private static final String POWERED_KEY = "Powered";
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";
    private static final String SYNC_GOODS = "Goods";

    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private final CompressorAppliance power = new CompressorAppliance(this, MachineBalanceManager::chestFreezer,
            AflSounds.BEVERAGE_COOLER_COMPRESSOR_START, AflSounds.BEVERAGE_COOLER_COMPRESSOR_STOP, COMPRESSOR_PITCH).cord(this::cordGeometry);
    private final AflContainerSearchState search = new AflContainerSearchState();
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;
    private ChestFreezerBlock.LidState pending;
    private long finishTick;
    /** Client: the lid target announced by the server and not yet committed to the block state. */
    private ChestFreezerBlock.LidState announced;
    private boolean powered;
    /** The goods arrangements in the order the compartments fill, one per compartment, from the position. */
    private final String[] goodsOrder;
    /** Server: the goods count last sent; client: the synced count. */
    private int goods;
    private Set<String> shownGoods = Set.of();

    public ChestFreezerBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.CHEST_FREEZER.get(), pos, state);
        goodsOrder = goodsOrder(pos);
    }

    /** Shuffled compartments, each with one of its arrangements; the same on every side and after reloads. */
    private static String[] goodsOrder(BlockPos pos) {
        RandomSource random = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL);
        int[] compartments = new int[GOODS_COMPARTMENTS];
        for (int i = 0; i < compartments.length; i++) compartments[i] = i;
        for (int i = compartments.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), swap = compartments[i];
            compartments[i] = compartments[j];
            compartments[j] = swap;
        }
        return Arrays.stream(compartments).mapToObj(c -> "goods_" + c + "_" + random.nextInt(GOODS_ARRANGEMENTS)).toArray(String[]::new);
    }

    /** Goods shown for this many occupied slots (the shared container goods rule, AflContainerGoods#shown). */
    public static int goodsFor(int occupied) {
        return AflContainerGoods.shown(occupied, GOODS_COMPARTMENTS);
    }

    private int goodsNow() {
        return goodsFor(AflContainerGoods.occupied(items));
    }

    private void showGoods(int count) {
        goods = Math.max(0, Math.min(GOODS_COMPARTMENTS, count));
        shownGoods = Set.of(Arrays.copyOf(goodsOrder, goods));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return power.plugCord().renderBounds(meshBounds());
    }

    /** Power cord (tools/build-chest-freezer-v2.mjs CORD, x - 16): the C14 inlet on the master's back wall, low at its outer corner. */
    private PlugCord.Geometry cordGeometry() {
        return PlugCord.appliance(worldPosition, meshFacing(), 5.3, 2.2, 7.7, -24, 8, 0);
    }

    @Override public PlugCord plugCord() { return power.plugCord(); }

    private AABB meshBounds() {
        Direction otherDirection = getBlockState().getValue(ChestFreezerBlock.FACING).getCounterClockWise();
        BlockPos other = worldPosition.relative(otherDirection);
        return new AABB(Math.min(worldPosition.getX(), other.getX()) - 0.5, worldPosition.getY() - 0.25,
                Math.min(worldPosition.getZ(), other.getZ()) - 0.5,
                Math.max(worldPosition.getX(), other.getX()) + 1.5, worldPosition.getY() + 1.5,
                Math.max(worldPosition.getZ(), other.getZ()) + 1.5);
    }

    // ---- lids ----

    public boolean startTransition(ChestFreezerBlock.LidState target, long tick) {
        if (pending != null || !(getBlockState().getBlock() instanceof ChestFreezerBlock)) return false;
        ChestFreezerBlock.LidState current = getBlockState().getValue(ChestFreezerBlock.LID);
        if (target == current || (target != ChestFreezerBlock.LidState.CLOSED
                && current != ChestFreezerBlock.LidState.CLOSED)) return false;
        pending = target;
        finishTick = tick + ChestFreezerBlock.ANIMATION_TICKS;
        if (level != null) level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_LID, target.ordinal());
        return true;
    }

    public void completeDue(long tick) {
        if (pending == null || tick < finishTick || level == null
                || !(getBlockState().getBlock() instanceof ChestFreezerBlock block)) return;
        ChestFreezerBlock.LidState target = pending;
        pending = null;
        block.commitLid(level, worldPosition, target);
    }

    public long ticksUntilCompletion(long tick) {
        return pending == null ? 0 : Math.max(1, finishTick - tick);
    }

    /** A slide is under way (server: pending; client: announced and not yet committed). */
    public boolean lidMoving() {
        return pending != null || announced != null;
    }

    /** Server: true broadcasts the lid event to nearby clients; client: the announced slide starts now. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_LID) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            ChestFreezerBlock.LidState[] states = ChestFreezerBlock.LidState.values();
            if (param >= 0 && param < states.length) announced = states[param];
            refreshMeshAnimationTargets();
        }
        return true;
    }

    private ChestFreezerBlock.LidState lidTarget() {
        return announced != null ? announced : getBlockState().getValue(ChestFreezerBlock.LID);
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
        return Component.translatable("block.apocalypse_firstlight.chest_freezer");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return AflContainerSearch.createMenu(id, inventory, this);
    }

    /** A lid closing shut closes every open inventory screen (and so pauses a running search). */
    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && getBlockState().getValue(ChestFreezerBlock.LID) != ChestFreezerBlock.LidState.CLOSED;
    }

    /** Every removal path of the master ends here (ChestFreezerBlock#onRemove): revealed slots drop, hidden loot is lost. */
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

    /** Server: any change of the contents (slots, menus, hoppers, loot) resends the goods count when it moves. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && lootTable == null && goodsNow() != goods) {
            showGoods(goodsNow());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- power: status display, LED and compressor ----

    /** Server, master only (ChestFreezerBlock#getTicker): rolls pending world loot, then runs the power. */
    public void serverTick() {
        if (level == null || !(getBlockState().getBlock() instanceof ChestFreezerBlock)) return;
        if (lootTable != null) unpackLootTable(null);
        power.serverTick();
    }

    /** Synced: the freezer has power (display and LED on). */
    public boolean powered() {
        return powered;
    }

    /** Synced: the compressor is running (clients play its loop). */
    public boolean compressorRunning() {
        return power.compressorRunning();
    }

    @Override
    public boolean lit() {
        return powered;
    }

    @Override
    public void setLit(boolean lit) {
        powered = lit;
        sync();
    }

    @Override
    public Vec3 compressorPosition() {
        return ChestFreezerBlock.compressorPosition(worldPosition, getBlockState().getValue(ChestFreezerBlock.FACING));
    }

    @Override
    public void syncAppliance() {
        sync();
    }


    // ---- persistence and client sync (power, lights, search completion, goods count; never items) ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
        power.load(tag);
        powered = tag.getBoolean(POWERED_KEY);
        showGoods(goodsNow());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        search.save(tag);
        power.save(tag);
        tag.putBoolean(POWERED_KEY, powered);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        power.save(tag);
        power.plugCord().writeSync(tag);
        tag.putBoolean(POWERED_KEY, powered);
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
        power.load(tag);
        powered = tag.getBoolean(POWERED_KEY);
        clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
        showGoods(tag.getInt(SYNC_GOODS));
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
        return getBlockState().getValue(ChestFreezerBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation, this::meshChannelTarget);
    }

    /** Channel targets from the block state (both sides: also the hit mesh's pose, meshhit/AnimatedMeshHits). */
    @Override
    public boolean meshChannelTarget(String channel) {
        return switch (channel) {
            case "left_open" -> lidTarget() == ChestFreezerBlock.LidState.LEFT_OPEN;
            case "right_open" -> lidTarget() == ChestFreezerBlock.LidState.RIGHT_OPEN;
            default -> false;
        };
    }

    /** The lit light set (LabPBR emissive, full brightness) while powered, else the unlit one; the shown goods only. */
    @Override
    public boolean meshPartVisible(String part) {
        if (part.startsWith("goods_")) return shownGoods.contains(part);
        return switch (part) {
            case "lights" -> !powered;
            case "lights_lit" -> powered;
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

    /** The committed state catches up with an announced slide: follow the block state again. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (announced != null && state.getValue(ChestFreezerBlock.LID) == announced) announced = null;
        refreshMeshAnimationTargets();
    }
}
