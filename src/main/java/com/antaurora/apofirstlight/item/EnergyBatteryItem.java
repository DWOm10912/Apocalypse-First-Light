package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Energy Battery: a small rechargeable cell, made empty, holding {@link MachineBalanceManager#energyBattery()} FE (50,000
 * by default). It exposes a standard Forge Energy capability, so anything that charges or drains FE items works with it;
 * the charge lives in the stack ({@code Energy}), the capacity it was last written with in {@code Capacity} (clients
 * without the server's balance data still draw the right bar). See {@code energy/EnergyBatteries} for drawing from a
 * player's inventory.
 */
public final class EnergyBatteryItem extends Item {
    private static final String ENERGY_KEY = "Energy";
    private static final String CAPACITY_KEY = "Capacity";

    public EnergyBatteryItem(Properties properties) {
        super(properties);
    }

    public static int capacity(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int current = MachineBalanceManager.energyBattery().capacityFe();
        return tag != null && tag.contains(CAPACITY_KEY) ? Math.max(1, tag.getInt(CAPACITY_KEY)) : current;
    }

    public static int energy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(ENERGY_KEY), 0, capacity(stack));
    }

    /** Writes the charge (clamped) together with the capacity currently in force. */
    public static void setEnergy(ItemStack stack, int energy) {
        int capacity = MachineBalanceManager.energyBattery().capacityFe();
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(CAPACITY_KEY, capacity);
        tag.putInt(ENERGY_KEY, Mth.clamp(energy, 0, capacity));
    }

    /** A fully charged battery (creative tab, tests). */
    public static ItemStack charged(Item item) {
        ItemStack stack = new ItemStack(item);
        setEnergy(stack, MachineBalanceManager.energyBattery().capacityFe());
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack) / capacity(stack));
    }

    /** Green when full, toward red as it drains (the vanilla durability hue range). */
    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0F, (float) energy(stack) / capacity(stack)) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.stored_energy_capacity",
                String.format(Locale.ROOT, "%,d", energy(stack)), String.format(Locale.ROOT, "%,d", capacity(stack))));
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new Provider(stack);
    }

    private static final class Provider implements ICapabilityProvider {
        private final LazyOptional<IEnergyStorage> storage;

        private Provider(ItemStack stack) {
            this.storage = LazyOptional.of(() -> new Storage(stack));
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return ForgeCapabilities.ENERGY.orEmpty(capability, storage);
        }
    }

    private record Storage(ItemStack stack) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int stored = energy(stack);
            int accepted = Math.min(Math.max(0, maxReceive), Math.min(MachineBalanceManager.energyBattery().maxReceiveFePerTick(),
                    MachineBalanceManager.energyBattery().capacityFe() - stored));
            if (accepted <= 0) return 0;
            if (!simulate) setEnergy(stack, stored + accepted);
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int stored = energy(stack);
            int extracted = Math.min(Math.max(0, maxExtract), Math.min(MachineBalanceManager.energyBattery().maxExtractFePerTick(), stored));
            if (extracted <= 0) return 0;
            if (!simulate) setEnergy(stack, stored - extracted);
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return energy(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity(stack);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
