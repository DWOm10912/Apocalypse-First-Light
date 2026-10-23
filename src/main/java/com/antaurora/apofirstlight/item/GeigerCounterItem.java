package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Geiger counter with a 9 V battery (item inspect V1, docs/gameplay/item_inspect_v1_design.md 5): right click in the hand
 * switches it on and off; on, it draws 1 FE per 16 ticks from the battery in its bay wherever it is (hand, clip, grid,
 * chest, ground). Nothing ticks: the tag keeps the game time the charge was last settled ({@code Since}), and whoever
 * needs the charge (the reading's sync, the LCD, the inspect screen, the charging station) works it out from the time
 * since. Switching on runs a 1 s self-test ({@code OnAt}). With a NiMH battery fitted the counter takes charge on the
 * charging station (its energy capability passes it to the battery); an alkaline one only comes out and is replaced.
 * <p>Tag: {@code Battery} (the fitted battery's whole stack), {@code On}, {@code Since}, {@code OnAt}, {@code Cover}
 * (the bay cover is open).
 */
public final class GeigerCounterItem extends Item {
    public static final String BATTERY = "Battery", ON = "On", SINCE = "Since", ON_AT = "OnAt", COVER = "Cover";
    /** Game ticks per FE while on: 4,500 FE is 72,000 ticks (3 game days), 3,000 FE 48,000 (2). */
    public static final int TICKS_PER_FE = 16;
    /** The switch-on self-test: every segment and the whole battery icon (ticks). */
    public static final int SELF_TEST_TICKS = 20;
    /** The client's game time (set by the client setup; the server's is read from the overworld). */
    public static LongSupplier clientClock = () -> 0L;

    public GeigerCounterItem(Properties properties) {
        super(properties);
    }

    /** The overworld game time on the side this runs on (every dimension shares it). */
    public static long now() {
        if (EffectiveSide.get().isServer()) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) return server.overworld().getGameTime();
        }
        return clientClock.getAsLong();
    }

    // ---------------------------------------------------------------- state

    public static ItemStack battery(ItemStack geiger) {
        CompoundTag tag = geiger.getTag();
        return tag == null || !tag.contains(BATTERY) ? ItemStack.EMPTY : ItemStack.of(tag.getCompound(BATTERY));
    }

    @Nullable
    public static Battery9vItem.Kind batteryKind(ItemStack geiger) {
        return Battery9vItem.kind(battery(geiger));
    }

    public static boolean on(ItemStack geiger) {
        return geiger.getTag() != null && geiger.getTag().getBoolean(ON);
    }

    public static boolean coverOpen(ItemStack geiger) {
        return geiger.getTag() != null && geiger.getTag().getBoolean(COVER);
    }

    /** The battery's charge now (0 without one). */
    public static int energy(ItemStack geiger, long now) {
        ItemStack battery = battery(geiger);
        if (battery.isEmpty()) return 0;
        int stored = Battery9vItem.energy(battery);
        if (!on(geiger)) return stored;
        long used = Math.max(0, now - geiger.getTag().getLong(SINCE)) / TICKS_PER_FE;
        return (int) Math.max(0, stored - used);
    }

    /** The fitted battery's capacity (1 without one, so shares stay finite). */
    public static int capacity(ItemStack geiger) {
        Battery9vItem.Kind kind = batteryKind(geiger);
        return kind == null ? 1 : kind.capacity;
    }

    /** On, with charge left: reads, lights the LCD, clicks. */
    public static boolean powered(ItemStack geiger, long now) {
        return geiger.getItem() instanceof GeigerCounterItem && on(geiger) && energy(geiger, now) > 0;
    }

    /** In the switch-on self-test (every segment lit, no reading yet). */
    public static boolean selfTest(ItemStack geiger, long now) {
        if (!powered(geiger, now)) return false;
        long since = now - geiger.getTag().getLong(ON_AT);
        return since >= 0 && since < SELF_TEST_TICKS;
    }

    /** The counter at work for this entity (hand or clip), or empty. */
    public static ItemStack inUse(LivingEntity entity, Item item) {
        return AflEquipmentSlots.inUseStack(entity, item);
    }

    /** The entity's counter at work and powered: what the reading's sync, the HUD and the clicks need. */
    public static boolean working(LivingEntity entity, Item item) {
        ItemStack stack = inUse(entity, item);
        return !stack.isEmpty() && powered(stack, now());
    }

    /**
     * Writes the charge drawn since {@code Since} into the battery and moves {@code Since} on by the ticks it paid for
     * (the rest carries to the next settle); off, without a battery or flat, {@code Since} is simply now.
     */
    public static void settle(ItemStack geiger, long now) {
        CompoundTag tag = geiger.getOrCreateTag();
        ItemStack battery = battery(geiger);
        long since = tag.getLong(SINCE);
        if (battery.isEmpty() || !tag.getBoolean(ON) || now < since) { tag.putLong(SINCE, now); return; }
        int stored = Battery9vItem.energy(battery);
        long used = Math.min(stored, (now - since) / TICKS_PER_FE);
        if (used > 0) {
            Battery9vItem.setEnergy(battery, (int) (stored - used));
            tag.put(BATTERY, battery.save(new CompoundTag()));
        }
        tag.putLong(SINCE, stored - used > 0 ? since + used * TICKS_PER_FE : now);
    }

    /** Fits a battery into the empty bay (one item; the caller has taken it from where it was). */
    public static void fit(ItemStack geiger, ItemStack battery, long now) {
        settle(geiger, now);
        CompoundTag tag = geiger.getOrCreateTag();
        tag.put(BATTERY, battery.copyWithCount(1).save(new CompoundTag()));
        tag.putLong(SINCE, now);
    }

    /** Takes the fitted battery out with its charge settled (empty if there is none). */
    public static ItemStack remove(ItemStack geiger, long now) {
        settle(geiger, now);
        ItemStack battery = battery(geiger);
        if (geiger.getTag() != null) geiger.getTag().remove(BATTERY);
        return battery;
    }

    public static void setCover(ItemStack geiger, boolean open) {
        if (open) geiger.getOrCreateTag().putBoolean(COVER, true);
        else if (geiger.getTag() != null) geiger.getTag().remove(COVER);
    }

    /**
     * Server: flips the switch; switching on starts the self-test. Returns the action-bar note for a switch-on that
     * lights nothing (no battery, flat), or null.
     */
    @Nullable
    public static Component toggle(ItemStack geiger, long now) {
        settle(geiger, now);
        CompoundTag tag = geiger.getOrCreateTag();
        boolean on = !tag.getBoolean(ON);
        tag.putBoolean(ON, on);
        tag.putLong(SINCE, now);
        if (!on) { tag.remove(ON_AT); return null; }
        tag.putLong(ON_AT, now);
        if (battery(geiger).isEmpty()) return Component.translatable("message.apocalypse_firstlight.geiger.no_battery");
        if (energy(geiger, now) <= 0) return Component.translatable("message.apocalypse_firstlight.geiger.flat");
        return null;
    }

    /** The slide switch's click, at the holder (a placeholder until the Firefly foley, design doc 8). */
    public static void switchSound(Player player) {
        player.level().playSound(null, player.getX(), player.getEyeY() - 0.4, player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.25F, 1.9F);
    }

    /** PASS on both sides (a consuming result would replay the equip bob); the server alone flips the switch. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide) return InteractionResultHolder.pass(stack);
        Component note = toggle(stack, level.getGameTime());
        switchSound(player);
        if (note != null) player.displayClientMessage(note, true);
        return InteractionResultHolder.pass(stack);
    }

    /** The settle and the charging change the tag: never bob the hand for it. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    // ---------------------------------------------------------------- tooltip and the inspect screen's status line

    /** Battery (kind and charge, or none), switch, and the cover when open. */
    public static List<Component> status(ItemStack geiger, long now) {
        List<Component> out = new ArrayList<>();
        ItemStack battery = battery(geiger);
        out.add(battery.isEmpty() ? Component.translatable("tooltip.apocalypse_firstlight.geiger.no_battery")
                : Component.translatable("tooltip.apocalypse_firstlight.geiger.battery", battery.getHoverName(),
                Battery9vItem.percent(energy(geiger, now), capacity(geiger))));
        out.add(Component.translatable(on(geiger) ? "tooltip.apocalypse_firstlight.geiger.on" : "tooltip.apocalypse_firstlight.geiger.off"));
        if (coverOpen(geiger)) out.add(Component.translatable("tooltip.apocalypse_firstlight.geiger.cover_open"));
        return out;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        for (Component line : status(stack, level == null ? now() : level.getGameTime())) lines.add(line.copy().withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- charging station

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

    /** The fitted battery behind the counter: only a NiMH one takes charge (at its own 1 FE a tick); nothing comes out. */
    private record Storage(ItemStack geiger) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            Battery9vItem.Kind kind = batteryKind(geiger);
            if (kind == null || !kind.rechargeable()) return 0;
            long now = now();
            int stored = energy(geiger, now), accepted = Math.min(Math.max(0, maxReceive), Math.min(kind.maxReceive, kind.capacity - stored));
            if (accepted <= 0 || simulate) return Math.max(0, accepted);
            settle(geiger, now);
            ItemStack battery = battery(geiger);
            Battery9vItem.setEnergy(battery, Battery9vItem.energy(battery) + accepted);
            geiger.getOrCreateTag().put(BATTERY, battery.save(new CompoundTag()));
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy(geiger, now()); }
        @Override public int getMaxEnergyStored() { return capacity(geiger); }
        @Override public boolean canExtract() { return false; }

        @Override
        public boolean canReceive() {
            Battery9vItem.Kind kind = batteryKind(geiger);
            return kind != null && kind.rechargeable();
        }
    }
}
