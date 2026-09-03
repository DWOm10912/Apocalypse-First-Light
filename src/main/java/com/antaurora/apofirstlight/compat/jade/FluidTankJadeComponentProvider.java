package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.compat.jade.client.AflJadeFluidFillElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.IProgressStyle;

import java.util.List;
import java.util.Locale;

public enum FluidTankJadeComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final int BAR_OUTER_WIDTH = 98;
    private static final int BAR_OUTER_HEIGHT = 12;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(FluidTankJadeServerDataProvider.FLUID_CAPACITY, Tag.TAG_INT)) {
            return;
        }

        tooltip.remove(Identifiers.UNIVERSAL_FLUID_STORAGE);
        tooltip.remove(Identifiers.UNIVERSAL_FLUID_STORAGE_DETAILED);

        int stored = Math.max(0, data.getInt(FluidTankJadeServerDataProvider.FLUID_AMOUNT));
        int capacity = Math.max(0, data.getInt(FluidTankJadeServerDataProvider.FLUID_CAPACITY));
        FluidStack fluid = readFluid(data, stored);
        Component fluidName = fluid.isEmpty()
                ? Component.translatable("jade.apocalypse_firstlight.empty_fluid")
                : fluid.getDisplayName();
        Component value = Component.translatable("jade.apocalypse_firstlight.fluid_value",
                formatNumber(stored), formatNumber(capacity));

        IElementHelper helper = tooltip.getElementHelper();
        tooltip.add(List.of(
                helper.text(fluidName),
                helper.spacer(4, 0),
                helper.text(value)));

        float ratio = capacity <= 0 ? 0.0F : Math.min(1.0F, (float) stored / capacity);
        IProgressStyle style = helper.progressStyle().overlay(new AflJadeFluidFillElement(fluid));
        tooltip.add(helper.progress(ratio, null, style, BoxStyle.DEFAULT, true)
                .size(new Vec2(BAR_OUTER_WIDTH, BAR_OUTER_HEIGHT)));
    }

    private static FluidStack readFluid(CompoundTag data, int stored) {
        if (stored <= 0 || !data.contains(FluidTankJadeServerDataProvider.FLUID, Tag.TAG_COMPOUND)) {
            return FluidStack.EMPTY;
        }
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(
                data.getCompound(FluidTankJadeServerDataProvider.FLUID));
        if (!fluid.isEmpty()) {
            fluid.setAmount(stored);
        }
        return fluid;
    }

    private static String formatNumber(int value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, value));
    }

    @Override
    public ResourceLocation getUid() {
        return FluidTankJadeServerDataProvider.UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2000;
    }
}
