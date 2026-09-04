package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.energy.EnergyCellMode;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EnergyCellMenu extends AbstractContainerMenu {
    public static final int MODE_BUTTON_ID = 0;

    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.energyCell();

    private static final int PLAYER_INVENTORY_END = 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final EnergyCellBlockEntity cell;

    public EnergyCellMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, requireBlockEntity(inventory, buffer),
                new SimpleContainerData(EnergyCellBlockEntity.DATA_COUNT));
    }

    public EnergyCellMenu(int containerId, Inventory inventory, EnergyCellBlockEntity cell, ContainerData data) {
        super(AflMenus.ENERGY_CELL.get(), containerId);
        checkContainerDataCount(data, EnergyCellBlockEntity.DATA_COUNT);
        this.data = data;
        this.cell = cell;
        this.access = ContainerLevelAccess.create(inventory.player.level(), cell.getBlockPos());

        MachineGuiLayout.Grid playerInventory = LAYOUT.playerInventory();
        for (int row = 0; row < playerInventory.rows(); row++) {
            for (int column = 0; column < playerInventory.columns(); column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        playerInventory.x() + column * playerInventory.spacing(),
                        playerInventory.y() + row * playerInventory.spacing()));
            }
        }
        MachineGuiLayout.Grid hotbar = LAYOUT.hotbar();
        for (int column = 0; column < hotbar.columns(); column++) {
            addSlot(new Slot(inventory, column,
                    hotbar.x() + column * hotbar.spacing(), hotbar.y()));
        }

        addDataSlots(data);
    }

    private static EnergyCellBlockEntity requireBlockEntity(Inventory inventory, FriendlyByteBuf buffer) {
        if (inventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof EnergyCellBlockEntity cell) {
            return cell;
        }
        throw new IllegalStateException("Energy Cell block entity is missing");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AflBlocks.ENERGY_CELL.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();
        if (slotIndex < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(stackInSlot, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, 0, PLAYER_INVENTORY_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stackInSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return original;
    }

    public int getStoredEnergy() {
        return readInt(0);
    }

    public int getEnergyCapacity() {
        return readInt(2);
    }

    public EnergyCellMode getMode() {
        return EnergyCellMode.fromSerialized(data.get(EnergyCellBlockEntity.MODE_DATA_INDEX));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != MODE_BUTTON_ID || !stillValid(player)) {
            return false;
        }
        return access.evaluate((level, position) -> {
            if (level.getBlockEntity(position) != cell) {
                return false;
            }
            cell.toggleMode();
            return true;
        }, false);
    }

    private int readInt(int lowWordIndex) {
        return data.get(lowWordIndex) & 0xFFFF
                | (data.get(lowWordIndex + 1) & 0xFFFF) << 16;
    }
}
