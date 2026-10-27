package com.antaurora.apofirstlight.creature.infected;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An infected's body as a Progressive Container Search host (the entity host, as survivor/SurvivorCorpseContainer): the
 * body's nine slots and its search state live in the corpse entity and are saved with it. Every slot starts hidden.
 */
final class InfectedCorpseContainer implements AflSearchableContainer {
    /** 0.8 s per slot, +-15 %, silent: a survivor's body's settings. */
    static final AflContainerSearchSettings SEARCH = new AflContainerSearchSettings(16, 0.15F, 0.0F, 0);
    private static final double REACH_SQR = 8.0 * 8.0;

    private final InfectedCorpse corpse;

    InfectedCorpseContainer(InfectedCorpse corpse) {
        this.corpse = corpse;
    }

    @Override
    public AflContainerSearchState aflSearchState() {
        return corpse.search;
    }

    @Override
    public AflContainerSearchSettings aflSearchSettings() {
        return SEARCH;
    }

    /** Shirt pockets 2, pants pockets 4, the belt 3: one row each (docs/ui/afl_container_search_page_v1.md 12). */
    static final AflContainerSearchLayout LAYOUT = AflContainerSearchLayout.of(new AflContainerSearchLayout.Section("shirt_pockets", 2, 1),
            new AflContainerSearchLayout.Section("pants_pockets", 4, 1), new AflContainerSearchLayout.Section("belt", 3, 1));

    @Override
    public AflContainerSearchLayout aflSearchLayout() {
        return LAYOUT;
    }

    @Override
    public boolean aflSearchRequiredOnInit() {
        return true;
    }

    @Override
    @Nullable
    public Level getLevel() {
        return corpse.level();
    }

    @Override
    public BlockPos getBlockPos() {
        return corpse.blockPosition();
    }

    @Override
    public BlockState getBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isRemoved() {
        return corpse.isRemoved();
    }

    @Override
    public int getContainerSize() {
        return corpse.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : corpse.items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < corpse.items.size() ? corpse.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(corpse.items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(corpse.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= corpse.items.size()) return;
        corpse.items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
    }

    @Override
    public void setChanged() {
    }

    /** The body still there, the viewer alive, in its level and within 8 blocks. */
    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.isAlive() && player.level() == corpse.level() && player.distanceToSqr(corpse) <= REACH_SQR;
    }

    @Override
    public void clearContent() {
        corpse.items.clear();
    }
}
