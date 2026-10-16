package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A pressed flower or dead bush (block/FlattenedPlantBlock, KIND other): the plant it was, put back when it stands up. */
public final class FlattenedPlantBlockEntity extends BlockEntity {
    @Nullable
    private BlockState original;

    public FlattenedPlantBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.FLATTENED_PLANT.get(), pos, state);
    }

    @Nullable
    public BlockState original() { return original; }

    public void setOriginal(BlockState state) {
        original = state;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (original != null) tag.put("Original", NbtUtils.writeBlockState(original));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        original = tag.contains("Original") ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Original")) : null;
    }
}
