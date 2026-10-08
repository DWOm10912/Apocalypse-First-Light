package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Distribution Panel screen's menu (Building Power V1): no slots, the panel's state as container data
 * ({@link DistributionPanelBlockEntity} DATA_* layout) and two kinds of button: {@link #MAIN_BUTTON} the main breaker,
 * {@link #BRANCH_BUTTON} + slot a branch breaker. The door opens while a player has it open.
 */
public final class DistributionPanelMenu extends AbstractContainerMenu {
    public static final int MAIN_BUTTON = 0, BRANCH_BUTTON = 1;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final DistributionPanelBlockEntity panel;
    private final BlockPos pos;

    public DistributionPanelMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null, new SimpleContainerData(DistributionPanelBlockEntity.DATA_COUNT));
    }

    public DistributionPanelMenu(int id, Inventory inventory, DistributionPanelBlockEntity panel, ContainerData data) {
        this(id, inventory, panel.getBlockPos(), panel, data);
        panel.startOpen();
    }

    private DistributionPanelMenu(int id, Inventory inventory, BlockPos pos, @Nullable DistributionPanelBlockEntity panel, ContainerData data) {
        super(AflMenus.DISTRIBUTION_PANEL.get(), id);
        checkContainerDataCount(data, DistributionPanelBlockEntity.DATA_COUNT);
        this.data = data;
        this.panel = panel;
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addDataSlots(data);
    }

    public int get(int index) { return data.get(index); }
    public BlockPos pos() { return pos; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (panel == null) return false;
        if (id == MAIN_BUTTON) { panel.toggleMain(player); return true; }
        if (id >= BRANCH_BUTTON && id < BRANCH_BUTTON + DistributionPanelBlockEntity.SLOTS) { panel.toggleBranch(id - BRANCH_BUTTON, player); return true; }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (panel != null) panel.stopOpen();
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AflBlocks.DISTRIBUTION_PANEL.get());
    }
}
