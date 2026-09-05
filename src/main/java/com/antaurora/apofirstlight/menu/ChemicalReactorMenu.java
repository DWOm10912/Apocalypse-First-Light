package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class ChemicalReactorMenu extends AbstractContainerMenu {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.chemicalReactor();
    private static final int MACHINE_SLOT_COUNT = ChemicalReactorBlockEntity.CONTAINER_SIZE;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final ChemicalReactorBlockEntity reactor;
    private final ContainerData data;

    public ChemicalReactorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, requireBlockEntity(inventory, buffer),
                new SimpleContainerData(ChemicalReactorBlockEntity.DATA_COUNT));
    }

    public ChemicalReactorMenu(int containerId, Inventory inventory, ChemicalReactorBlockEntity reactor,
                               ContainerData data) {
        super(AflMenus.CHEMICAL_REACTOR.get(), containerId);
        checkContainerSize(reactor, ChemicalReactorBlockEntity.CONTAINER_SIZE);
        checkContainerDataCount(data, ChemicalReactorBlockEntity.DATA_COUNT);
        this.reactor = reactor;
        this.data = data;

        MachineGuiLayout.Element inputSlot = LAYOUT.element("item_input_slot");
        addSlot(new Slot(reactor, ChemicalReactorBlockEntity.INPUT_SLOT,
                inputSlot.x(), inputSlot.y()));
        MachineGuiLayout.Element outputSlot = LAYOUT.outputSlots().get(0);
        addSlot(new Slot(reactor, ChemicalReactorBlockEntity.OUTPUT_SLOT,
                outputSlot.x(), outputSlot.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        MachineGuiLayout.Grid playerGrid = LAYOUT.playerInventory();
        for (int row = 0; row < playerGrid.rows(); row++) {
            for (int column = 0; column < playerGrid.columns(); column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        playerGrid.x() + column * playerGrid.spacing(),
                        playerGrid.y() + row * playerGrid.spacing()));
            }
        }
        MachineGuiLayout.Grid hotbar = LAYOUT.hotbar();
        for (int column = 0; column < hotbar.columns(); column++) {
            addSlot(new Slot(inventory, column,
                    hotbar.x() + column * hotbar.spacing(), hotbar.y()));
        }
        addDataSlots(data);
    }

    private static ChemicalReactorBlockEntity requireBlockEntity(Inventory inventory, FriendlyByteBuf buffer) {
        if (inventory.player.level().getBlockEntity(buffer.readBlockPos())
                instanceof ChemicalReactorBlockEntity reactor) {
            return reactor;
        }
        throw new IllegalStateException("Chemical Reactor block entity is missing");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), reactor.getBlockPos()), player, AflBlocks.CHEMICAL_REACTOR.get());
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
        } else if (!moveItemStackTo(stackInSlot, ChemicalReactorBlockEntity.INPUT_SLOT,
                ChemicalReactorBlockEntity.INPUT_SLOT + 1, false)) {
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

    public int getStoredEnergy() {
        return readInt(0);
    }

    public int getEnergyCapacity() {
        return readInt(2);
    }

    public int getInputFluidAmount() {
        return data.get(4);
    }

    public int getInputFluidCapacity() {
        return data.get(5);
    }

    public int getWasteFluidAmount() {
        return data.get(6);
    }

    public int getWasteFluidCapacity() {
        return data.get(7);
    }

    public int getArrowProgress() {
        int requiredTicks = readInt(10);
        return requiredTicks <= 0
                ? 0
                : Mth.clamp((int) ((long) readInt(8) * 24 / requiredTicks), 0, 24);
    }

    public FluidStack getInputFluid() {
        return withSyncedAmount(reactor.getInputFluid(), getInputFluidAmount());
    }

    public FluidStack getWasteFluid() {
        return withSyncedAmount(reactor.getWasteFluid(), getWasteFluidAmount());
    }

    private int readInt(int lowWordIndex) {
        return data.get(lowWordIndex) & 0xFFFF
                | (data.get(lowWordIndex + 1) & 0xFFFF) << 16;
    }

    private static FluidStack withSyncedAmount(FluidStack fluid, int amount) {
        if (fluid.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        fluid.setAmount(amount);
        return fluid;
    }
}
