package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
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

public final class ThermalGeneratorMenu extends AbstractContainerMenu {
    public static final int FUEL_SLOT_X = 44;
    public static final int FUEL_SLOT_Y = 34;
    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    private static final int MACHINE_SLOT_COUNT = 1;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;
    private final boolean clientSide;

    public ThermalGeneratorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory,
                requireBlockEntity(inventory, buffer),
                new SimpleContainerData(ThermalGeneratorBlockEntity.DATA_COUNT));
    }

    public ThermalGeneratorMenu(int containerId, Inventory inventory, ThermalGeneratorBlockEntity generator,
                                ContainerData data) {
        super(AflMenus.THERMAL_GENERATOR.get(), containerId);
        checkContainerSize(generator, ThermalGeneratorBlockEntity.CONTAINER_SIZE);
        checkContainerDataCount(data, ThermalGeneratorBlockEntity.DATA_COUNT);
        this.container = generator;
        this.data = data;
        this.clientSide = inventory.player.level().isClientSide();

        addSlot(new Slot(generator, ThermalGeneratorBlockEntity.FUEL_SLOT, FUEL_SLOT_X, FUEL_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isFuel(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INVENTORY_X + column * 18, HOTBAR_Y));
        }

        addDataSlots(data);
        container.startOpen(inventory.player);
    }

    private static ThermalGeneratorBlockEntity requireBlockEntity(Inventory inventory, FriendlyByteBuf buffer) {
        if (inventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof ThermalGeneratorBlockEntity generator) {
            return generator;
        }
        throw new IllegalStateException("Thermal Generator block entity is missing");
    }

    private boolean isFuel(ItemStack stack) {
        return clientSide
                ? ClientThermalGeneratorFuelData.isThermalGeneratorFuel(stack)
                : MachineBalanceManager.isThermalGeneratorFuel(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), ((ThermalGeneratorBlockEntity) container).getBlockPos()),
                player, AflBlocks.THERMAL_GENERATOR.get());
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
        } else if (isFuel(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, 0, MACHINE_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(stackInSlot, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public int getFireProgress() {
        int total = getFuelEnergyTotal();
        return total <= 0 ? 0 : Mth.clamp((int) ((long) getFuelEnergyRemaining() * 13 / total), 0, 13);
    }

    public int getArrowProgress() {
        int total = getFuelEnergyTotal();
        if (total <= 0) {
            return 0;
        }
        int consumed = Math.max(0, total - getFuelEnergyRemaining());
        return Mth.clamp((int) ((long) consumed * 24 / total), 0, 24);
    }

    public int getStoredEnergy() {
        return readInt(0);
    }

    public int getEnergyCapacity() {
        return readInt(2);
    }

    public int getFuelEnergyRemaining() {
        return readInt(4);
    }

    public int getFuelEnergyTotal() {
        return readInt(6);
    }

    private int readInt(int lowWordIndex) {
        return data.get(lowWordIndex) & 0xFFFF
                | (data.get(lowWordIndex + 1) & 0xFFFF) << 16;
    }
}
