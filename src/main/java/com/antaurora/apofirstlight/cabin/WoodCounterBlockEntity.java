package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md): the storage of a {@link WoodFurniture.Counter}, 18 slots (the
 * kitchen counter, a 9 x 2 chest screen) or 9 (under the sink, 9 x 1). Like a vanilla barrel: {@code open} while anyone has
 * it open (the doors swing out), its own open / close sounds at the doors (3 blocks, with their noise), a loot table rolled on first opening. Not a
 * Progressive Container Search container: the start home's own cabinets open as plain storage (plan P5, user 2026-10-12);
 * one placed in a searched world structure would need that integration first.
 */
public class WoodCounterBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items;
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(state, com.antaurora.apofirstlight.registry.AflSounds.WOOD_CABINET_OPEN.get());
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(state, com.antaurora.apofirstlight.registry.AflSounds.WOOD_CABINET_CLOSE.get());
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int now) {}

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == WoodCounterBlockEntity.this;
        }
    };

    public WoodCounterBlockEntity(BlockPos pos, BlockState state) {
        super(WoodFurniture.COUNTER, pos, state);
        items = NonNullList.withSize(slots(state), ItemStack.EMPTY);
    }

    private static int slots(BlockState state) { return state.getBlock() instanceof WoodFurniture.Counter c ? c.slots() : 18; }

    @Override
    public int getContainerSize() { return items.size(); }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return getBlockState().getBlock().getName(); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        int rows = getContainerSize() / 9;
        return new ChestMenu(rows == 1 ? MenuType.GENERIC_9x1 : MenuType.GENERIC_9x2, id, inventory, this, rows);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(slots(getBlockState()), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator()) openers.incrementOpeners(player, level, worldPosition, getBlockState());
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator()) openers.decrementOpeners(player, level, worldPosition, getBlockState());
    }

    /** From the block's scheduled tick: closes the doors when nobody has the cabinet open any more. */
    public void recheckOpen() {
        if (!remove) openers.recheckOpeners(level, worldPosition, getBlockState());
    }

    private void setOpen(BlockState state, boolean open) {
        if (level != null && state.hasProperty(WoodFurniture.Counter.OPEN)) level.setBlock(worldPosition, state.setValue(WoodFurniture.Counter.OPEN, open), 3);
    }

    /** Heard as far as the infected hear the doors (blocks; forest home sounds V1, 2026-10-14). */
    public static final double DOOR_RADIUS = 3;

    /** At the doors: the cell's front, low (they sit under the worktop); the sound and its noise reach DOOR_RADIUS. */
    private void sound(BlockState state, SoundEvent sound) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        Vec3i n = state.getValue(WoodFurniture.Counter.FACING).getNormal();
        com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(server, null,
                new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + n.getX() * 0.4, worldPosition.getY() + 0.4, worldPosition.getZ() + 0.5 + n.getZ() * 0.4),
                sound, DOOR_RADIUS, 0.97F + level.random.nextFloat() * 0.06F, com.antaurora.apofirstlight.noise.NoiseType.INTERACTION,
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }
}
