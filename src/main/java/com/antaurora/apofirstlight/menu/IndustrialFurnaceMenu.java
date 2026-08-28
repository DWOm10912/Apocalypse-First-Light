package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class IndustrialFurnaceMenu extends AbstractContainerMenu {
    public static final int AUTO_BALANCE_BUTTON_ID = 0;

    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.industrialFurnace();
    private static final int MACHINE_SLOT_COUNT = IndustrialFurnaceBlockEntity.CONTAINER_SIZE;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;
    private final IndustrialFurnaceBlockEntity furnace;

    public IndustrialFurnaceMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, requireBlockEntity(inventory, buffer),
                new SimpleContainerData(IndustrialFurnaceBlockEntity.DATA_COUNT));
    }

    public IndustrialFurnaceMenu(int containerId, Inventory inventory,
                                 IndustrialFurnaceBlockEntity furnace, ContainerData data) {
        super(AflMenus.INDUSTRIAL_FURNACE.get(), containerId);
        checkContainerSize(furnace, IndustrialFurnaceBlockEntity.CONTAINER_SIZE);
        checkContainerDataCount(data, IndustrialFurnaceBlockEntity.DATA_COUNT);
        this.container = furnace;
        this.data = data;
        this.furnace = furnace;

        for (int lane = 0; lane < IndustrialFurnaceBlockEntity.LANE_COUNT; lane++) {
            MachineGuiLayout.Element inputSlot = LAYOUT.element("input_slot_" + lane);
            addSlot(new Slot(furnace, IndustrialFurnaceBlockEntity.inputSlot(lane),
                    inputSlot.x(), inputSlot.y()) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    furnace.rebalanceInputsIfNeeded();
                }
            });
        }
        for (int lane = 0; lane < IndustrialFurnaceBlockEntity.LANE_COUNT; lane++) {
            MachineGuiLayout.Element outputSlot = LAYOUT.outputSlots().get(lane);
            addSlot(new Slot(furnace, IndustrialFurnaceBlockEntity.outputSlot(lane),
                    outputSlot.x(), outputSlot.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

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
        container.startOpen(inventory.player);
    }

    private static IndustrialFurnaceBlockEntity requireBlockEntity(Inventory inventory,
                                                                     FriendlyByteBuf buffer) {
        if (inventory.player.level().getBlockEntity(buffer.readBlockPos())
                instanceof IndustrialFurnaceBlockEntity furnace) {
            return furnace;
        }
        throw new IllegalStateException("Smelting Factory block entity is missing");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(
                        player.level(), ((IndustrialFurnaceBlockEntity) container).getBlockPos()),
                player, AflBlocks.INDUSTRIAL_FURNACE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();
        if (slotIndex < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoInputLanes(stackInSlot)) {
            if (slotIndex < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(stackInSlot, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START,
                    PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
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

    private boolean moveIntoInputLanes(ItemStack stack) {
        boolean moved = false;
        for (int lane = 0; lane < IndustrialFurnaceBlockEntity.LANE_COUNT && !stack.isEmpty(); lane++) {
            moved |= moveItemStackTo(stack,
                    IndustrialFurnaceBlockEntity.inputSlot(lane),
                    IndustrialFurnaceBlockEntity.inputSlot(lane) + 1,
                    false);
        }
        if (moved) {
            furnace.rebalanceInputsIfNeeded();
        }
        return moved;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != AUTO_BALANCE_BUTTON_ID || !stillValid(player)) {
            return false;
        }
        furnace.toggleAutoBalance();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public int getStoredEnergy() {
        return readInt(0);
    }

    public int getEnergyCapacity() {
        return readInt(2);
    }

    public boolean isAutoBalanceEnabled() {
        return data.get(IndustrialFurnaceBlockEntity.AUTO_BALANCE_DATA_INDEX) != 0;
    }

    public int getArrowProgress(int lane) {
        if (lane < 0 || lane >= IndustrialFurnaceBlockEntity.LANE_COUNT) {
            return 0;
        }
        int dataStart = 4 + lane * 4;
        int requiredTicks = readInt(dataStart + 2);
        return requiredTicks <= 0
                ? 0
                : Mth.clamp((int) ((long) readInt(dataStart) * 24 / requiredTicks), 0, 24);
    }

    private int readInt(int lowWordIndex) {
        return data.get(lowWordIndex) & 0xFFFF
                | (data.get(lowWordIndex + 1) & 0xFFFF) << 16;
    }
}
