package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * AFL Workbench V1 (docs/gameplay/afl_workbench_v1_design.md 14): what a {@link Workbench} keeps, in its master cell.
 * Two drawers of 9 slots each (0..8 the left drawer, 9..17 the right, as seen from the front), each a plain 9 x 1 chest
 * screen while it is pulled out (pushing it in closes the screen); five pegs (the tools, sent to clients, which draw
 * them). The drawers are its animated mesh's channels {@code drawer_left} / {@code drawer_right} (Furniture Mesh V1:
 * they slide smoothly, the resting bench drawn by the chunk). Sounds: the wood cabinet's for now (user 2026-10-21: the
 * workbench's own later), at the drawer, with its noise (3 blocks, as every furniture door).
 * <p>The hung tools are drawn by the chunk with the bench (client WoodGunRackChunking, the tool rack's path, through
 * {@link CabinMeshModel#EXTRA} in the model data): recorded once when they change, never re-drawn every frame.
 * <p>A furniture piece being made (FurnitureWork.Job, docs/gameplay/furniture_bench_work_v1.md 3.4) stays on the top
 * when the work stops: saved and sent to the clients, drawn by the chunk with the tools while nobody works on it.
 */
public class WorkbenchBlockEntity extends BlockEntity implements AflAnimatedMeshHost {
    public static final int DRAWER_SLOTS = 9;
    private static final String[] CHANNELS = {"drawer_left", "drawer_right"};
    private final NonNullList<ItemStack> drawers = NonNullList.withSize(2 * DRAWER_SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> tools = NonNullList.withSize(Workbench.PEGS, ItemStack.EMPTY);
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private ResourceLocation meshProfile;
    private boolean dropped;
    /** The furniture piece on the top, or null; {@code canOpen}: its stain can stands opened (pried open this batch). */
    @Nullable private FurnitureWork.Job job;
    private boolean canOpen;
    /** A furniture piece made here, standing on the top until someone takes it (doc 12.3), or empty. */
    private ItemStack finished = ItemStack.EMPTY;
    /** The chunk's share of the tools, installed by the client (WoodGunRackChunking); none on a server. */
    public static volatile Function<WorkbenchBlockEntity, ModelData> toolsData = bench -> ModelData.EMPTY;

    public WorkbenchBlockEntity(BlockPos pos, BlockState state) { super(Workbench.TYPE, pos, state); }

    // ------------------------------------------------------------------------------------------- the drawers

    /** The drawer's screen: its 9 slots, valid while it is pulled out. */
    public MenuProvider drawerMenu(int side) {
        Container drawer = new Drawer(side);
        return new SimpleMenuProvider((id, inventory, player) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, drawer, 1),
                Component.translatable("container.apocalypse_firstlight.workbench_drawer"));
    }

    /** The player pulls a drawer out or pushes it in: the look on the left cells, the sound and its noise, the game event. */
    public void setDrawer(int side, boolean open, Player player) {
        if (level == null || Workbench.drawerOpen(level, worldPosition, side) == open) return;
        Workbench.setDrawerState(level, worldPosition, side, open);
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            RangedSound.playWithNoise(server, null, Workbench.drawerFront(worldPosition, meshFacing(), side, open),
                    open ? AflSounds.WOOD_CABINET_OPEN.get() : AflSounds.WOOD_CABINET_CLOSE.get(), WoodCounterBlockEntity.DOOR_RADIUS,
                    1.05F + level.random.nextFloat() * 0.06F, NoiseType.INTERACTION, ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        }
        level.gameEvent(player, open ? GameEvent.CONTAINER_OPEN : GameEvent.CONTAINER_CLOSE, worldPosition);
    }

    /** One drawer's 9 slots over the shared list. */
    private final class Drawer implements Container {
        private final int side, base;

        Drawer(int side) {
            this.side = side;
            this.base = side * DRAWER_SLOTS;
        }

        @Override public int getContainerSize() { return DRAWER_SLOTS; }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < DRAWER_SLOTS; i++) if (!drawers.get(base + i).isEmpty()) return false;
            return true;
        }

        @Override public ItemStack getItem(int slot) { return drawers.get(base + slot); }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack out = ContainerHelper.removeItem(drawers, base + slot, amount);
            if (!out.isEmpty()) setChanged();
            return out;
        }

        @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(drawers, base + slot); }

        @Override
        public void setItem(int slot, ItemStack stack) {
            drawers.set(base + slot, stack);
            if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
            setChanged();
        }

        @Override public void setChanged() { WorkbenchBlockEntity.this.setChanged(); }

        /** Only while this drawer is out, within reach of the bench. */
        @Override
        public boolean stillValid(Player player) {
            return level != null && !isRemoved() && level.getBlockEntity(worldPosition) == WorkbenchBlockEntity.this
                    && Workbench.drawerOpen(level, worldPosition, side) && Container.stillValidBlockEntity(WorkbenchBlockEntity.this, player);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < DRAWER_SLOTS; i++) drawers.set(base + i, ItemStack.EMPTY);
            setChanged();
        }
    }

    // ---------------------------------------------------------------------------------------------- the pegs

    public ItemStack tool(int peg) { return tools.get(peg); }

    public boolean noTools() { return tools.stream().allMatch(ItemStack::isEmpty); }

    public ItemStack takeTool(int peg) {
        ItemStack out = tools.get(peg);
        tools.set(peg, ItemStack.EMPTY);
        toolsChanged();
        return out;
    }

    public void putTool(int peg, ItemStack stack) {
        tools.set(peg, stack);
        toolsChanged();
    }

    private void toolsChanged() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Every removal path ends here: the drawers' things and the tools drop once. */
    public void dropContents() {
        if (dropped || level == null) return;
        dropped = true;
        Containers.dropContents(level, worldPosition, drawers);
        if (!finished.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, finished);
        Containers.dropContents(level, worldPosition.above(), tools);
    }

    // ------------------------------------------------------------------------------------- the furniture piece

    @Nullable
    public FurnitureWork.Job job() { return job; }

    public boolean canOpen() { return canOpen; }

    /** Server: the piece's new state (null: none on the top) and the can's; sent to the clients when either changed. */
    public void setJob(@Nullable FurnitureWork.Job job, boolean canOpen) {
        boolean seen = !java.util.Objects.equals(this.job, job) || this.canOpen != canOpen;
        this.job = job;
        this.canOpen = canOpen;
        setChanged();
        if (seen && level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** The piece made here standing on the top (doc 12.3), or empty. */
    public ItemStack finished() { return finished; }

    /** Server: the finished piece set on the top (or cleared: empty), sent to the clients. */
    public void setFinished(ItemStack stack) {
        finished = stack.copy();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Server: the finished piece taken off the top (empty: none). */
    public ItemStack takeFinished() {
        ItemStack out = finished;
        if (!out.isEmpty()) setFinished(ItemStack.EMPTY);
        return out;
    }

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
        return state.hasProperty(Workbench.FACING) ? state.getValue(Workbench.FACING) : Direction.NORTH;
    }

    @Override
    public void refreshMeshAnimationTargets() { AflAnimatedMeshHost.refreshTargets(level, meshProfile(), meshAnimation, this::meshChannelTarget); }

    @Override
    public boolean meshChannelTarget(String channel) {
        BlockState state = getBlockState();
        if (!state.hasProperty(Workbench.DRAWER_LEFT)) return false;
        return CHANNELS[0].equals(channel) ? state.getValue(Workbench.DRAWER_LEFT) : CHANNELS[1].equals(channel) && state.getValue(Workbench.DRAWER_RIGHT);
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

    /** The bench's resting parts (the animated mesh's chunk data) and the tools on its pegs. May run on a chunk builder thread. */
    @Override
    public @NotNull ModelData getModelData() {
        ModelData bench = AflAnimatedMeshHost.super.getModelData();
        CabinMeshModel.Extra tools = toolsData.apply(this).get(CabinMeshModel.EXTRA);
        return tools == null ? bench : bench.derive().with(CabinMeshModel.EXTRA, tools).build();
    }

    @Override
    public AABB getRenderBoundingBox() { return AflAnimatedMeshHost.renderBounds(worldPosition, meshProfile(), meshFacing()); }

    // ---------------------------------------------------------------------------------- sync and persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Drawers", ContainerHelper.saveAllItems(new CompoundTag(), drawers, true));
        tag.put("Tools", ContainerHelper.saveAllItems(new CompoundTag(), tools, true));
        saveJob(tag);
    }

    private void saveJob(CompoundTag tag) {
        if (job != null) tag.put("Furniture", job.save());
        if (canOpen) tag.putBoolean("CanOpen", true);
        if (!finished.isEmpty()) tag.put("Finished", finished.save(new CompoundTag()));
    }

    private void loadJob(CompoundTag tag) {
        job = tag.contains("Furniture") ? FurnitureWork.Job.load(tag.getCompound("Furniture")) : null;
        canOpen = tag.getBoolean("CanOpen");
        finished = tag.contains("Finished") ? ItemStack.of(tag.getCompound("Finished")) : ItemStack.EMPTY;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        drawers.clear();
        tools.clear();
        ContainerHelper.loadAllItems(tag.getCompound("Drawers"), drawers);
        ContainerHelper.loadAllItems(tag.getCompound("Tools"), tools);
        loadJob(tag);
    }

    /** Clients get the tools and the furniture piece (they draw them), never the drawers' things. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Tools", ContainerHelper.saveAllItems(new CompoundTag(), tools, true));
        saveJob(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        tools.clear();
        ContainerHelper.loadAllItems(tag.getCompound("Tools"), tools);
        loadJob(tag);
        // the chunk draws the new tools (non-urgent rebuild, off the main thread), as the tool rack's
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
}
