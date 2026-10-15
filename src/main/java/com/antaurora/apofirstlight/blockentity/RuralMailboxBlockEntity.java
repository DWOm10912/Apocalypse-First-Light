package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RuralMailboxBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Rural Driveway V1 mailbox storage: {@link #SIZE} slots in a hopper screen, like a barrel otherwise (OPEN while anyone has
 * it open, a loot table rolled on first opening). Drawn by the AFL Animated Block Mesh Runtime from the master cell
 * (tools/build-rural-driveway-v1.mjs): 'body' still, 'door' on channel 'open' (drops 95 degrees, 0.4 s) following OPEN,
 * 'flag' on channel 'flag' (stands up 90 degrees, 0.5 s) following FLAG.
 */
public class RuralMailboxBlockEntity extends RandomizableContainerBlockEntity implements AflAnimatedMeshHost {
    public static final int SIZE = 5;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/rural_mailbox.json");

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.IRON_TRAPDOOR_OPEN);
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.IRON_TRAPDOOR_CLOSE);
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int now) {}

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof HopperMenu menu && !menu.slots.isEmpty() && menu.slots.get(0).container == RuralMailboxBlockEntity.this;
        }
    };

    public RuralMailboxBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.RURAL_MAILBOX.get(), pos, state);
    }

    @Override
    public int getContainerSize() { return SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return getBlockState().getBlock().getName(); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new HopperMenu(id, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator()) openers.incrementOpeners(player, level, worldPosition, getBlockState());
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator()) openers.decrementOpeners(player, level, worldPosition, getBlockState());
    }

    /** From the block's scheduled tick: shuts the door when nobody has the mailbox open any more. */
    public void recheckOpen() {
        if (!remove) openers.recheckOpeners(level, worldPosition, getBlockState());
    }

    private void setOpen(BlockState state, boolean open) {
        if (level != null && state.hasProperty(RuralMailboxBlock.OPEN)) level.setBlock(worldPosition, state.setValue(RuralMailboxBlock.OPEN, open), 3);
    }

    /** At the door: the box's front, 1.1 m up. */
    private void sound(SoundEvent sound) {
        if (level == null) return;
        Direction facing = meshFacing();
        level.playSound(null, worldPosition.getX() + 0.5 + facing.getStepX() * 0.25, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.25,
                sound, SoundSource.BLOCKS, 0.4F, level.random.nextFloat() * 0.1F + 1.35F);
    }

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    public ResourceLocation meshProfile() { return MESH_PROFILE; }

    @Override
    public AflBlockMeshAnimationState meshAnimation() { return meshAnimation; }

    @Override
    public Direction meshFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(RuralMailboxBlock.FACING) ? state.getValue(RuralMailboxBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation, this::meshChannelTarget);
    }

    @Override
    public boolean meshChannelTarget(String channel) {
        BlockState state = getBlockState();
        return switch (channel) {
            case "open" -> state.hasProperty(RuralMailboxBlock.OPEN) && state.getValue(RuralMailboxBlock.OPEN);
            case "flag" -> state.hasProperty(RuralMailboxBlock.FLAG) && state.getValue(RuralMailboxBlock.FLAG);
            default -> false;
        };
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }
}
