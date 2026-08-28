package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
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

public final class CrusherMenu extends AbstractContainerMenu {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.crusher();
    private static final int MACHINE_SLOT_COUNT = CrusherBlockEntity.CONTAINER_SIZE;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;

    public CrusherMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, requireBlockEntity(inventory, buffer),
                new SimpleContainerData(CrusherBlockEntity.DATA_COUNT));
    }

    public CrusherMenu(int containerId, Inventory inventory, CrusherBlockEntity crusher, ContainerData data) {
        super(AflMenus.CRUSHER.get(), containerId);
        checkContainerSize(crusher, CrusherBlockEntity.CONTAINER_SIZE);
        checkContainerDataCount(data, CrusherBlockEntity.DATA_COUNT);
        this.container = crusher;
        this.data = data;

        MachineGuiLayout.Element inputSlot = LAYOUT.element("input_slot");
        addSlot(new Slot(crusher, CrusherBlockEntity.INPUT_SLOT, inputSlot.x(), inputSlot.y()));
        for (int index = 0; index < CrusherBlockEntity.OUTPUT_SLOT_COUNT; index++) {
            MachineGuiLayout.Element outputSlot = LAYOUT.outputSlots().get(index);
            addSlot(new Slot(crusher, CrusherBlockEntity.FIRST_OUTPUT_SLOT + index,
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

    private static CrusherBlockEntity requireBlockEntity(Inventory inventory, FriendlyByteBuf buffer) {
        if (inventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof CrusherBlockEntity crusher) {
            return crusher;
        }
        throw new IllegalStateException("Crusher block entity is missing");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), ((CrusherBlockEntity) container).getBlockPos()),
                player, AflBlocks.CRUSHER.get());
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
        } else if (!moveItemStackTo(stackInSlot, CrusherBlockEntity.INPUT_SLOT,
                CrusherBlockEntity.INPUT_SLOT + 1, false)) {
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

    public int getArrowProgress() {
        int total = readInt(6);
        return total <= 0 ? 0 : Mth.clamp((int) ((long) readInt(4) * 24 / total), 0, 24);
    }

    private int readInt(int lowWordIndex) {
        return data.get(lowWordIndex) & 0xFFFF
                | (data.get(lowWordIndex + 1) & 0xFFFF) << 16;
    }
}
