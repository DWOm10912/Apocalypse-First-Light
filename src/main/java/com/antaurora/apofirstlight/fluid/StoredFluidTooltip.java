package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.energy.MachineStoredEnergy;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Locale;

/** Read-only presentation of the existing BlockEntityTag; never writes hover state. */
public final class StoredFluidTooltip {
    private StoredFluidTooltip() {}

    public static void append(ItemStack stack, List<Component> lines) {
        if (stack.is(AflItems.FLUID_TANK.get())) {
            addFluid(lines, "tooltip.apocalypse_firstlight.stored_fluid",
                    FluidTankStoredFluid.read(stack), FluidTankBlockEntity.CAPACITY_MB);
        } else if (stack.is(AflItems.CHEMICAL_REACTOR.get())) {
            int capacity = MachineBalanceManager.chemicalReactor().capacityFe();
            int energy = Math.min(capacity, MachineStoredEnergy.read(stack));
            if (energy > 0) {
                lines.add(Component.translatable("tooltip.apocalypse_firstlight.stored_energy_capacity",
                        number(energy), number(capacity)).withStyle(ChatFormatting.GRAY));
            }
            CompoundTag data = BlockItem.getBlockEntityData(stack);
            int fluidCapacity = ChemicalReactorBlockEntity.TANK_CAPACITY_MB;
            addFluid(lines, "tooltip.apocalypse_firstlight.input_fluid",
                    read(data, ChemicalReactorBlockEntity.INPUT_TANK_KEY, fluidCapacity), fluidCapacity);
            addFluid(lines, "tooltip.apocalypse_firstlight.waste_fluid",
                    read(data, ChemicalReactorBlockEntity.WASTE_TANK_KEY, fluidCapacity), fluidCapacity);
        }
    }

    public static FluidStack read(CompoundTag data, String key, int capacity) {
        if (data == null || !data.contains(key, Tag.TAG_COMPOUND) || capacity <= 0) {
            return FluidStack.EMPTY;
        }
        try {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(data.getCompound(key));
            if (fluid.isEmpty() || fluid.getAmount() <= 0) return FluidStack.EMPTY;
            fluid.setAmount(Math.min(capacity, fluid.getAmount()));
            return fluid;
        } catch (IllegalArgumentException | net.minecraft.ResourceLocationException exception) {
            return FluidStack.EMPTY;
        }
    }

    private static void addFluid(List<Component> lines, String label, FluidStack fluid, int capacity) {
        if (fluid.isEmpty()) return;
        lines.add(Component.translatable(label, fluid.getDisplayName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.stored_fluid_amount",
                number(fluid.getAmount()), number(capacity)).withStyle(ChatFormatting.GRAY));
    }

    private static String number(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
