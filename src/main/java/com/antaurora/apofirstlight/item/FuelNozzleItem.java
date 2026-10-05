package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Fuel Dispenser V1 nozzle in the hand (gasoline / diesel, docs/models/fuel_dispenser_v1.md). Only ever made by
 * FuelDispenserBlockEntity#take and tethered to it: it records the dispenser, the nozzle and a session number, and only
 * exists while its holder keeps it in the main hand. Anywhere else (another slot, the off hand, another inventory once a
 * player holds it there, the ground) it removes itself; the dispenser then hangs the nozzle back in its holster. V1 holds
 * no fuel.
 * <p>
 * Spraying (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"): holding right click (use) runs the nozzle; every use tick
 * the dispenser draws fuel from that grade's line onto the ground in view (FuelDispenserBlockEntity#spray). No containers yet.
 */
public final class FuelNozzleItem extends Item {
    private static final String TAG = "FuelDispenser";

    public FuelNozzleItem() {
        super(new Properties().stacksTo(1));
    }

    public static ItemStack create(Item item, BlockPos dispenser, ResourceKey<Level> dimension, int nozzle, int session) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putLong("Pos", dispenser.asLong());
        tag.putString("Dimension", dimension.location().toString());
        tag.putInt("Nozzle", nozzle);
        tag.putInt("Session", session);
        return stack;
    }

    @Nullable
    private static CompoundTag binding(ItemStack stack, @Nullable Level level, BlockPos dispenser) {
        if (level == null || !(stack.getItem() instanceof FuelNozzleItem)) return null;
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.getLong("Pos") == dispenser.asLong() && level.dimension().location().toString().equals(tag.getString("Dimension"))
                ? tag : null;
    }

    /** The nozzle of this dispenser the stack belongs to (the client cannot check the session), or null. */
    @Nullable
    public static Nozzle nozzleOf(ItemStack stack, Level level, BlockPos dispenser) {
        CompoundTag tag = binding(stack, level, dispenser);
        int index = tag == null ? -1 : tag.getInt("Nozzle");
        return index >= 0 && index < Nozzle.values().length ? Nozzle.values()[index] : null;
    }

    /** The dispenser a nozzle stack belongs to (any side, no session check), or null. */
    @Nullable
    public static BlockPos dispenserOf(ItemStack stack) {
        CompoundTag tag = stack.getItem() instanceof FuelNozzleItem ? stack.getTagElement(TAG) : null;
        return tag == null ? null : BlockPos.of(tag.getLong("Pos"));
    }

    /** The nozzle index (Nozzle ordinal) a nozzle stack is, or -1. */
    public static int nozzleIndexOf(ItemStack stack) {
        CompoundTag tag = stack.getItem() instanceof FuelNozzleItem ? stack.getTagElement(TAG) : null;
        return tag == null ? -1 : tag.getInt("Nozzle");
    }

    /** True for this dispenser's nozzle under this session. */
    public static boolean matches(ItemStack stack, @Nullable Level level, BlockPos dispenser, int nozzle, int session) {
        CompoundTag tag = binding(stack, level, dispenser);
        return tag != null && tag.getInt("Nozzle") == nozzle && tag.getInt("Session") == session && session != 0;
    }

    /**
     * Hold right click in the main hand: the nozzle runs while it is held (onUseTick). Returns PASS although it starts
     * using: any consuming result makes the client replay the held item's equip bob (ItemInHandRenderer#itemUsed), which
     * dropped the nozzle away from its hose for a moment (user, 2026-10-05).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND) player.startUsingItem(hand);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !tethered(stack, level, player)) {
            player.stopUsingItem();
            return;
        }
        if (level.getBlockEntity(BlockPos.of(tag.getLong("Pos"))) instanceof FuelDispenserBlockEntity dispenser) {
            dispenser.spray(player, Nozzle.values()[tag.getInt("Nozzle")]);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!(entity instanceof ServerPlayer player) || player.getMainHandItem() != stack || !tethered(stack, level, player)) stack.setCount(0);
    }

    private static boolean tethered(ItemStack stack, Level level, ServerPlayer player) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !level.dimension().location().toString().equals(tag.getString("Dimension"))) return false;
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FuelDispenserBlockEntity dispenser
                && dispenser.isHeldBy(tag.getInt("Nozzle"), player.getUUID(), tag.getInt("Session"));
    }

    /** Never lies on the ground: dropped, tossed or spilled with a player's inventory, it is gone (the dispenser takes it back). */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide) entity.discard();
        return true;
    }
}
