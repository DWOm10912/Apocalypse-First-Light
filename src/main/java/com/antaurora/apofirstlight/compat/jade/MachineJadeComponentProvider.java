package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.compat.jade.client.AflJadeEnergyFillElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.IProgressStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum MachineJadeComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final int BAR_OUTER_WIDTH = 98;
    private static final int BAR_OUTER_HEIGHT = 12;
    private static final int PROCESSING_BAR_WIDTH = 76;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(MachineJadeServerDataProvider.MACHINE_TYPE, Tag.TAG_STRING)) {
            return;
        }

        tooltip.remove(Identifiers.UNIVERSAL_ENERGY_STORAGE);
        tooltip.remove(Identifiers.UNIVERSAL_ITEM_STORAGE);

        addEnergy(tooltip, data);
        switch (data.getString(MachineJadeServerDataProvider.MACHINE_TYPE)) {
            case MachineJadeServerDataProvider.THERMAL_GENERATOR -> addThermalGenerator(tooltip, data);
            case MachineJadeServerDataProvider.CRUSHER -> addCrusher(tooltip, data);
            case MachineJadeServerDataProvider.INDUSTRIAL_FURNACE -> addIndustrialFurnace(tooltip, data);
            case MachineJadeServerDataProvider.COMPRESSOR -> addCrusher(tooltip, data);
            case MachineJadeServerDataProvider.ALLOY_FURNACE -> addAlloyFurnace(tooltip, data);
            default -> {
            }
        }
    }

    private static void addEnergy(ITooltip tooltip, CompoundTag data) {
        int stored = Math.max(0, data.getInt(MachineJadeServerDataProvider.ENERGY_STORED));
        int capacity = Math.max(0, data.getInt(MachineJadeServerDataProvider.ENERGY_CAPACITY));
        float ratio = capacity <= 0 ? 0.0F : Math.min(1.0F, (float) stored / capacity);
        Component value = Component.translatable("jade.apocalypse_firstlight.energy_value",
                formatNumber(stored), formatNumber(capacity));

        IElementHelper helper = tooltip.getElementHelper();
        tooltip.add(List.of(
                helper.text(Component.translatable("jade.apocalypse_firstlight.energy")),
                helper.spacer(4, 0),
                helper.text(value)));
        IProgressStyle style = helper.progressStyle().overlay(new AflJadeEnergyFillElement());
        tooltip.add(helper.progress(ratio, null, style, BoxStyle.DEFAULT, true)
                .size(new Vec2(BAR_OUTER_WIDTH, BAR_OUTER_HEIGHT)));
    }

    private static void addThermalGenerator(ITooltip tooltip, CompoundTag data) {
        if (data.contains(MachineJadeServerDataProvider.FUEL, Tag.TAG_COMPOUND)) {
            addLabeledItem(tooltip, "jade.apocalypse_firstlight.fuel",
                    ItemStack.of(data.getCompound(MachineJadeServerDataProvider.FUEL)));
        }
    }

    private static void addCrusher(ITooltip tooltip, CompoundTag data) {
        ItemStack input = ItemStack.EMPTY;
        if (data.contains(MachineJadeServerDataProvider.INPUT, Tag.TAG_COMPOUND)) {
            input = ItemStack.of(data.getCompound(MachineJadeServerDataProvider.INPUT));
        }

        int progress = Math.max(0, data.getInt(MachineJadeServerDataProvider.PROCESSING_PROGRESS));
        int processingTime = Math.max(0, data.getInt(MachineJadeServerDataProvider.PROCESSING_TIME));
        if (progress > 0 && !input.isEmpty()) {
            addLabeledItem(tooltip, "jade.apocalypse_firstlight.processing", input);
        }

        if (progress > 0 && processingTime > 0) {
            float ratio = Math.min(1.0F, (float) progress / processingTime);
            IElementHelper helper = tooltip.getElementHelper();
            tooltip.add(helper.progress(ratio, null,
                            helper.progressStyle(), BoxStyle.DEFAULT, false)
                    .size(new Vec2(BAR_OUTER_WIDTH, BAR_OUTER_HEIGHT)));
        }

        addOutputs(tooltip, data);
    }

    private static void addIndustrialFurnace(ITooltip tooltip, CompoundTag data) {
        if (data.contains(MachineJadeServerDataProvider.PROCESSING_LANES, Tag.TAG_LIST)) {
            ListTag laneTags = data.getList(
                    MachineJadeServerDataProvider.PROCESSING_LANES, Tag.TAG_COMPOUND);
            IElementHelper helper = tooltip.getElementHelper();
            boolean processingLabelAdded = false;
            for (Tag laneTag : laneTags) {
                if (!(laneTag instanceof CompoundTag laneData)) {
                    continue;
                }
                int progress = Math.max(0,
                        laneData.getInt(MachineJadeServerDataProvider.PROCESSING_PROGRESS));
                if (progress <= 0 || !laneData.contains(
                        MachineJadeServerDataProvider.INPUT, Tag.TAG_COMPOUND)) {
                    continue;
                }
                ItemStack input = ItemStack.of(
                        laneData.getCompound(MachineJadeServerDataProvider.INPUT));
                if (input.isEmpty()) {
                    continue;
                }

                if (!processingLabelAdded) {
                    tooltip.add(Component.translatable("jade.apocalypse_firstlight.processing"));
                    processingLabelAdded = true;
                }
                int requiredTicks = Math.max(0,
                        laneData.getInt(MachineJadeServerDataProvider.PROCESSING_TIME));
                float ratio = requiredTicks <= 0
                        ? 0.0F
                        : Math.min(1.0F, (float) progress / requiredTicks);
                List<IElement> row = new ArrayList<>();
                row.add(helper.item(input));
                row.add(helper.spacer(4, 0));
                row.add(helper.progress(ratio, null,
                                helper.progressStyle(), BoxStyle.DEFAULT, false)
                        .size(new Vec2(PROCESSING_BAR_WIDTH, BAR_OUTER_HEIGHT)));
                tooltip.add(row);
            }
        }

        addOutputs(tooltip, data);
    }

    private static void addAlloyFurnace(ITooltip tooltip, CompoundTag data) {
        int progress = Math.max(0, data.getInt(MachineJadeServerDataProvider.PROCESSING_PROGRESS));
        int processingTime = Math.max(0, data.getInt(MachineJadeServerDataProvider.PROCESSING_TIME));
        if (progress > 0 && data.contains(MachineJadeServerDataProvider.PROCESSING_INPUTS, Tag.TAG_LIST)) {
            ListTag inputTags = data.getList(
                    MachineJadeServerDataProvider.PROCESSING_INPUTS, Tag.TAG_COMPOUND);
            List<IElement> inputRow = new ArrayList<>();
            IElementHelper helper = tooltip.getElementHelper();
            for (Tag inputTag : inputTags) {
                if (inputTag instanceof CompoundTag stackTag) {
                    ItemStack stack = ItemStack.of(stackTag);
                    if (!stack.isEmpty()) {
                        inputRow.add(helper.item(stack));
                    }
                }
            }
            if (!inputRow.isEmpty()) {
                tooltip.add(Component.translatable("jade.apocalypse_firstlight.processing"));
                tooltip.add(inputRow);
                if (processingTime > 0) {
                    float ratio = Math.min(1.0F, (float) progress / processingTime);
                    tooltip.add(helper.progress(ratio, null,
                                    helper.progressStyle(), BoxStyle.DEFAULT, false)
                            .size(new Vec2(BAR_OUTER_WIDTH, BAR_OUTER_HEIGHT)));
                }
            }
        }

        addOutputs(tooltip, data);
    }

    private static void addLabeledItem(ITooltip tooltip, String translationKey, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        IElementHelper helper = tooltip.getElementHelper();
        tooltip.add(Component.translatable(translationKey));
        tooltip.add(helper.item(stack.copy()));
    }

    private static void addOutputs(ITooltip tooltip, CompoundTag data) {
        if (!data.contains(MachineJadeServerDataProvider.OUTPUTS, Tag.TAG_LIST)) {
            return;
        }

        ListTag outputTags = data.getList(MachineJadeServerDataProvider.OUTPUTS, Tag.TAG_COMPOUND);
        List<IElement> row = new ArrayList<>();
        IElementHelper helper = tooltip.getElementHelper();
        for (Tag outputTag : outputTags) {
            if (outputTag instanceof CompoundTag stackTag) {
                ItemStack stack = ItemStack.of(stackTag);
                if (!stack.isEmpty()) {
                    row.add(helper.item(stack));
                }
            }
        }
        if (!row.isEmpty()) {
            tooltip.add(Component.translatable("jade.apocalypse_firstlight.output"));
            tooltip.add(row);
        }
    }

    private static String formatNumber(int value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, value));
    }

    @Override
    public ResourceLocation getUid() {
        return MachineJadeServerDataProvider.UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2000;
    }
}
