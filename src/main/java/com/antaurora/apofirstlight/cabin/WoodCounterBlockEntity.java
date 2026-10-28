package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md): the storage of a {@link WoodFurniture.Counter}, 18 slots (the
 * kitchen counter, a 9 x 2 chest screen) or 9 (under the sink, 9 x 1); since V2 (docs/models/wood_furniture_v2.md,
 * 2026-10-15) of any {@link WoodFurniture.Storage} (the wardrobe 27, the nightstand 9; the vintage chest freezer's 27 in
 * its subclass), its slots, sounds and open look from the block.
 * <p>Since 2026-10-15 (user: the same steps as every searchable container) the doors / drawer / lid are opened and
 * closed by the player ({@link FurnitureOpening}: aim at them), with their sounds at the doors (3 blocks, with their
 * noise), and the screen opens only from the open interior; closing them closes every screen on it. A world copy with a
 * loot table is searched slot by slot (Progressive Container Search, docs/gameplay/progressive_container_search_v1.md);
 * one placed by a player is plain storage. The forest start home's own cabinets are to be let through unsearched (plan
 * P5, user 2026-10-12: by a data list of loot table prefixes, not built yet).
 * <p>Furniture Mesh V1 (2026-10-15, tools/furniture/furniture-mesh.mjs): the piece is an AFL Animated Block Mesh
 * (docs/rendering/animated_block_mesh_runtime_v1.md), its profile block_mesh_profiles/&lt;the block's id&gt;.json, its
 * channel 'open' the block's {@code open}: the doors / drawer / lid swing, the resting piece drawn by the chunk.
 */
public class WoodCounterBlockEntity extends RandomizableContainerBlockEntity implements AflSearchableContainer, AflAnimatedMeshHost {
    /** The locker's pace: 40 ticks (2 s) a slot, +-15 % seed jitter. */
    public static final AflContainerSearchSettings SEARCH_SETTINGS = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);
    private static final String SYNC_SEARCH_COMPLETE = "AflSearchComplete";
    private static final String SYNC_HUNG = "AflHung";
    /**
     * The wardrobe's clothes (hungClothes): in the hanging space a shirt, jacket and jeans on the rod, shoes on the floor
     * board; ON_SHELF added: folded on the top shelf.
     */
    public static final byte SHIRT = 1, JACKET = 2, JEANS = 3, SHOES = 4, ON_SHELF = 0x10;

    private NonNullList<ItemStack> items;
    private final AflContainerSearchState search = new AflContainerSearchState();
    private boolean contentsDropped;
    /** Transient: true only while markPlacedByPlayer() commits the one-time search initialization. */
    private boolean placedByPlayer;
    private boolean clientSearchComplete = true;
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private ResourceLocation meshProfile;
    /** The wardrobe's revealed clothes in slot order (SHIRT..SHOES): the server's last sent, the client's last received. */
    private byte[] hung = new byte[0];
    private boolean hungSync;

    public WoodCounterBlockEntity(BlockPos pos, BlockState state) { this(WoodFurniture.COUNTER, pos, state); }

    protected WoodCounterBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        items = NonNullList.withSize(slots(state), ItemStack.EMPTY);
    }

    private static int slots(BlockState state) { return state.getBlock() instanceof WoodFurniture.Storage s ? s.slots() : 18; }

    // ------------------------------------------------------------------------------------- the animated mesh

    @Override
    public ResourceLocation meshProfile() {
        if (meshProfile == null) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock());
            meshProfile = new ResourceLocation(id.getNamespace(), "block_mesh_profiles/" + id.getPath() + ".json");
        }
        return meshProfile;
    }

    @Override
    public AflBlockMeshAnimationState meshAnimation() { return meshAnimation; }

    @Override
    public Direction meshFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    @Override
    public void refreshMeshAnimationTargets() { AflAnimatedMeshHost.refreshTargets(level, meshProfile(), meshAnimation, this::meshChannelTarget); }

    @Override
    public boolean meshChannelTarget(String channel) {
        BlockState state = getBlockState();
        return "open".equals(channel) && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        refreshMeshAnimationTargets();
    }

    @Override
    public AABB getRenderBoundingBox() { return AflAnimatedMeshHost.renderBounds(worldPosition, meshProfile(), meshFacing()); }

    @Override
    public int getContainerSize() { return items.size(); }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return getBlockState().getBlock().getName(); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return AflContainerSearch.createMenu(id, inventory, this); }

    /** Only while the doors are open: closing them closes every screen on it (and pauses a running search). */
    @Override
    public boolean stillValid(Player player) {
        BlockState state = getBlockState();
        return super.stillValid(player) && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    /** The player opens or closes the doors / drawer / lid: the look on every cell, the sound and its noise, the game event. */
    public void setDoors(boolean open, Player player) {
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof WoodFurniture.Storage s) || !state.hasProperty(BlockStateProperties.OPEN)
                || state.getValue(BlockStateProperties.OPEN) == open) return;
        s.setOpen(level, worldPosition, state, open);
        sound(state, open ? s.openSound() : s.closeSound(), s);
        level.gameEvent(player, open ? GameEvent.CONTAINER_OPEN : GameEvent.CONTAINER_CLOSE, worldPosition);
    }

    /** Every removal path ends here: revealed slots drop, never-seen world loot is lost with the furniture. */
    public void dropContentsOnce() {
        if (contentsDropped || level == null) return;
        contentsDropped = true;
        AflContainerSearch.dropContentsOnBreak(level, worldPosition, this);
        clearContent();
    }

    // ---- Progressive Container Search ----

    @Override
    public AflContainerSearchState aflSearchState() { return search; }

    @Override
    public AflContainerSearchSettings aflSearchSettings() { return SEARCH_SETTINGS; }

    @Override
    public AflContainerSearchLayout aflSearchLayout() {
        return getBlockState().getBlock() instanceof WoodFurniture.Storage s ? s.searchLayout() : AflContainerSearchLayout.GRID_6X3;
    }

    @Override
    public boolean aflSearchRequiredOnInit() { return !placedByPlayer && lootTable != null; }

    public void markPlacedByPlayer() {
        placedByPlayer = true;
        AflContainerSearch.isComplete(this);
        placedByPlayer = false;
    }

    @Override
    public void unpackLootTable(@Nullable Player player) {
        AflContainerSearch.beforeLootUnpack(this);
        boolean rolled = lootTable != null;
        super.unpackLootTable(player);
        if (rolled) AflContainerSearch.afterLootUnpack(this);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return AflContainerSearch.canPlaceItem(this, slot, stack) && super.canPlaceItem(slot, stack); }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) { return AflContainerSearch.canTakeItem(this, slot) && super.canTakeItem(target, slot, stack); }

    @Override
    protected IItemHandler createUnSidedHandler() { return AflContainerSearch.itemHandler(this); }

    @Override
    public void onAflSearchCompleted(ServerLevel level) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }

    @Override
    public void onAflSearchSlotRevealed(ServerLevel level, int slot) { syncHung(); }

    @Override
    public void setChanged() {
        super.setChanged();
        syncHung();
    }

    /** Search or view (the prompt): clients know only whether the search is done, never the items. */
    public boolean isSearchCompleteForPrompt() { return level != null && level.isClientSide ? clientSearchComplete : AflContainerSearch.isComplete(this); }

    // ---- the wardrobe's clothes (Main's clothing_*_hung meshes, 2026-10-27) ----

    /** What hangs in the wardrobe (client; WoodFurnitureRenderer): its revealed clothes only, never unsearched loot. */
    public byte[] hungClothes() { return hung; }

    /** The revealed clothes now; a world copy nobody opened yet shows none (its search is not set up here). */
    private byte[] hungNow() {
        if (!(getBlockState().getBlock() instanceof WoodFurnitureV2.Wardrobe) || !search.isInitialized()) return new byte[0];
        var out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            byte code = stack.isEmpty() ? 0 : hungCode(stack);
            if (i < WoodFurnitureV2.Wardrobe.SHELF_SLOTS) code |= ON_SHELF;
            if ((code & ~ON_SHELF) != 0 && AflContainerSearch.isRevealed(this, i)) for (int n = Math.min(stack.getCount(), 8); n > 0; n--) out.write(code);
        }
        return out.toByteArray();
    }

    private static byte hungCode(ItemStack stack) {
        if (stack.is(com.antaurora.apofirstlight.registry.AflItems.CLOTHING_SHIRT.get())) return SHIRT;
        if (stack.is(com.antaurora.apofirstlight.registry.AflItems.CLOTHING_JACKET.get())) return JACKET;
        if (stack.is(com.antaurora.apofirstlight.registry.AflItems.CLOTHING_JEANS.get())) return JEANS;
        return stack.is(com.antaurora.apofirstlight.registry.AflItems.CLOTHING_SHOES.get()) ? SHOES : 0;
    }

    /** A slot set or revealed: the clothes changed, tell the clients tracking the wardrobe (the search's own setChanged re-enters). */
    private void syncHung() {
        if (hungSync || !(level instanceof ServerLevel server)) return;
        hungSync = true;
        try {
            byte[] now = hungNow();
            if (!java.util.Arrays.equals(now, hung)) {
                hung = now;
                server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        } finally {
            hungSync = false;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_SEARCH_COMPLETE, AflContainerSearch.isComplete(this));
        if (!hungSync) {
            hungSync = true;
            try {
                hung = hungNow();
            } finally {
                hungSync = false;
            }
        }
        if (hung.length > 0) tag.putByteArray(SYNC_HUNG, hung);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains(SYNC_SEARCH_COMPLETE)) clientSearchComplete = tag.getBoolean(SYNC_SEARCH_COMPLETE);
        hung = tag.getByteArray(SYNC_HUNG);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        search.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(slots(getBlockState()), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
    }

    /** Heard as far as the infected hear the doors (blocks; forest home sounds V1, 2026-10-14). */
    public static final double DOOR_RADIUS = 3;

    /** At the doors: the cell's front at the furniture's door height; the sound and its noise reach DOOR_RADIUS. */
    private void sound(BlockState state, SoundEvent sound, WoodFurniture.Storage storage) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3i n = state.getValue(WoodFurniture.Counter.FACING).getNormal();
        com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(server, null,
                new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + n.getX() * 0.4, worldPosition.getY() + storage.soundHeight(), worldPosition.getZ() + 0.5 + n.getZ() * 0.4),
                sound, DOOR_RADIUS, storage.soundPitch() * (0.97F + level.random.nextFloat() * 0.06F), com.antaurora.apofirstlight.noise.NoiseType.INTERACTION,
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }
}
