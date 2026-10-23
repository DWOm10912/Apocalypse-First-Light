package com.antaurora.apofirstlight.item;

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

/**
 * 9 V battery (item inspect V1, docs/gameplay/item_inspect_v1_design.md 6): the real 48.5 x 26.5 x 17.5 mm block with a
 * snap terminal, for portable instruments (the Geiger counter's bay). Two kinds: alkaline (primary, 4,500 FE, never takes
 * charge) and NiMH (rechargeable, 3,000 FE, at most 1 FE a tick). Both drain at the counter's 1 FE per 16 ticks, so a
 * fresh alkaline runs it 3 game days and a NiMH 2 (user 2026-10-22). The charge lives in {@code Energy}; a missing tag
 * is a full battery and a full one carries no tag, so fresh batteries stack and part-used ones do not.
 */
public final class Battery9vItem extends Item {
    public static final String ENERGY_KEY = "Energy";
    /** Charge of a fresh battery (FE) and the NiMH's charging limit (FE a tick); the wiki reads these. */
    public static final int ALKALINE_CAPACITY = 4_500, NIMH_CAPACITY = 3_000, NIMH_MAX_RECEIVE = 1;

    public enum Kind {
        ALKALINE(ALKALINE_CAPACITY, 0), NIMH(NIMH_CAPACITY, NIMH_MAX_RECEIVE);

        public final int capacity, maxReceive;

        Kind(int capacity, int maxReceive) {
            this.capacity = capacity;
            this.maxReceive = maxReceive;
        }

        public boolean rechargeable() { return maxReceive > 0; }
    }

    private final Kind kind;

    public Battery9vItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    /** The kind of a 9 V battery stack, or null for anything else. */
    @Nullable
    public static Kind kind(ItemStack stack) {
        return stack.getItem() instanceof Battery9vItem battery ? battery.kind : null;
    }

    public static int capacity(ItemStack stack) {
        Kind kind = kind(stack);
        return kind == null ? 1 : kind.capacity;
    }

    /** The stored charge: a stack without the tag is full. */
    public static int energy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int capacity = capacity(stack);
        return tag == null || !tag.contains(ENERGY_KEY) ? capacity : Mth.clamp(tag.getInt(ENERGY_KEY), 0, capacity);
    }

    /** Writes the charge (clamped); a full battery drops the tag (and an empty tag with it) so it stacks with new ones. */
    public static void setEnergy(ItemStack stack, int energy) {
        int capacity = capacity(stack), value = Mth.clamp(energy, 0, capacity);
        if (value >= capacity) {
            stack.removeTagKey(ENERGY_KEY);
            if (stack.getTag() != null && stack.getTag().isEmpty()) stack.setTag(null);
        } else stack.getOrCreateTag().putInt(ENERGY_KEY, value);
    }

    /** Charge in whole percent, rounded down except that any charge left reads at least 1%. */
    public static int percent(int energy, int capacity) {
        if (energy <= 0 || capacity <= 0) return 0;
        return Math.max(1, energy * 100 / capacity);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energy(stack) < capacity(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack) / capacity(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0F, (float) energy(stack) / capacity(stack)) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.battery_9v.charge", percent(energy(stack), capacity(stack))));
        if (!kind.rechargeable()) lines.add(Component.translatable("tooltip.apocalypse_firstlight.battery_9v.primary"));
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

    /** Takes charge only if rechargeable, never gives it out (the instrument it sits in draws by game time). */
    private record Storage(ItemStack stack) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            Kind kind = kind(stack);
            if (kind == null || !kind.rechargeable() || stack.getCount() != 1) return 0;
            int stored = energy(stack), accepted = Math.min(Math.max(0, maxReceive), Math.min(kind.maxReceive, kind.capacity - stored));
            if (accepted <= 0) return 0;
            if (!simulate) setEnergy(stack, stored + accepted);
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy(stack); }
        @Override public int getMaxEnergyStored() { return capacity(stack); }
        @Override public boolean canExtract() { return false; }

        @Override
        public boolean canReceive() {
            Kind kind = kind(stack);
            return kind != null && kind.rechargeable();
        }
    }
}
