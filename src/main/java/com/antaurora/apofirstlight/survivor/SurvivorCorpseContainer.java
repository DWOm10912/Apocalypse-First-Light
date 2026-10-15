package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchSettings;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The body's items as a Progressive Container Search host (docs/gameplay/progressive_container_search_v1.md, entity
 * host): the search contract asks for a block-entity-like owner, so the body answers with the block it lies in and a
 * stand-in block state (air; only the noise log reads it, and corpses make no rummaging noise in V0). Every slot starts
 * hidden; viewers share the one session, the reveal mask is saved in the corpse record.
 */
final class SurvivorCorpseContainer implements AflSearchableContainer {
    /** 0.8 s per slot, +-15 %; silent like every other V0 container. */
    static final AflContainerSearchSettings SEARCH = new AflContainerSearchSettings(16, 0.15F, 0.0F, 0);
    private static final double REACH_SQR = 8.0 * 8.0;

    private final SurvivorCorpseRecord record;

    SurvivorCorpseContainer(SurvivorCorpseRecord record) {
        this.record = record;
    }

    SurvivorCorpseRecord record() {
        return record;
    }

    @Override
    public AflContainerSearchState aflSearchState() {
        return record.search;
    }

    @Override
    public AflContainerSearchSettings aflSearchSettings() {
        return SEARCH;
    }

    @Override
    public boolean aflSearchRequiredOnInit() {
        return true;
    }

    @Override
    @Nullable
    public Level getLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ResourceLocation id = ResourceLocation.tryParse(record.dimension);
        return server == null || id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    @Override
    public BlockPos getBlockPos() {
        return BlockPos.containing(record.x, record.y, record.z);
    }

    @Override
    public BlockState getBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isRemoved() {
        return record.owner == null || record.owner.corpse(record.id) != record;
    }

    @Override
    public void onAflSearchCompleted(ServerLevel level) {
        SurvivorCorpses.refreshEntity(level, record);
    }

    @Override
    public int getContainerSize() {
        return record.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : record.items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < record.items.size() ? record.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(record.items, slot, amount);
        if (!taken.isEmpty()) setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = ContainerHelper.takeItem(record.items, slot);
        if (!taken.isEmpty()) setChanged();
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= record.items.size()) return;
        record.items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public void setChanged() {
        record.changed();
    }

    /** Server truth for every open menu: the body still exists, same dimension, within 8 blocks, the viewer alive. */
    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.isAlive() && player.level().dimension().location().toString().equals(record.dimension)
                && player.distanceToSqr(record.x, record.y, record.z) <= REACH_SQR;
    }

    @Override
    public void clearContent() {
        record.items.clear();
        setChanged();
    }
}
