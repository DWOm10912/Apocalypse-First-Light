package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.AlloyFurnaceBlockEntity;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import com.antaurora.apofirstlight.recipe.AlloyingRecipe;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflMenus;
import com.antaurora.apofirstlight.registry.AflRecipes;
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

public final class AlloyFurnaceMenu extends AbstractContainerMenu {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.alloyFurnace();
    private static final int MACHINE_SLOT_COUNT = AlloyFurnaceBlockEntity.CONTAINER_SIZE;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;
    private final Inventory playerInventory;

    public AlloyFurnaceMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, requireBlockEntity(inventory, buffer),
                new SimpleContainerData(AlloyFurnaceBlockEntity.DATA_COUNT));
    }

    public AlloyFurnaceMenu(int containerId, Inventory inventory, AlloyFurnaceBlockEntity furnace,
                            ContainerData data) {
        super(AflMenus.ALLOY_FURNACE.get(), containerId);
        checkContainerSize(furnace, AlloyFurnaceBlockEntity.CONTAINER_SIZE);
        checkContainerDataCount(data, AlloyFurnaceBlockEntity.DATA_COUNT);
        this.container = furnace;
        this.data = data;
        this.playerInventory = inventory;

        MachineGuiLayout.Element inputA = LAYOUT.element("input_slot_a");
        addSlot(new Slot(furnace, AlloyFurnaceBlockEntity.INPUT_A_SLOT, inputA.x(), inputA.y()));
        MachineGuiLayout.Element inputB = LAYOUT.element("input_slot_b");
        addSlot(new Slot(furnace, AlloyFurnaceBlockEntity.INPUT_B_SLOT, inputB.x(), inputB.y()));
        MachineGuiLayout.Element output = LAYOUT.outputSlots().get(0);
        addSlot(new Slot(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT, output.x(), output.y()) {
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
        container.startOpen(inventory.player);
    }

    private static AlloyFurnaceBlockEntity requireBlockEntity(Inventory inventory, FriendlyByteBuf buffer) {
        if (inventory.player.level().getBlockEntity(buffer.readBlockPos())
                instanceof AlloyFurnaceBlockEntity furnace) {
            return furnace;
        }
        throw new IllegalStateException("Alloy Furnace block entity is missing");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), ((AlloyFurnaceBlockEntity) container).getBlockPos()),
                player, AflBlocks.ALLOY_FURNACE.get());
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
        } else if (isAlloyingIngredient(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, AlloyFurnaceBlockEntity.INPUT_A_SLOT,
                    AlloyFurnaceBlockEntity.OUTPUT_SLOT, false)) {
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

    private boolean isAlloyingIngredient(ItemStack stack) {
        for (AlloyingRecipe recipe : playerInventory.player.level().getRecipeManager()
                .getAllRecipesFor(AflRecipes.ALLOYING_TYPE.get())) {
            for (AlloyingRecipe.CountedIngredient ingredient : recipe.countedIngredients()) {
                if (ingredient.ingredient().test(stack)) {
                    return true;
                }
            }
        }
        return false;
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
