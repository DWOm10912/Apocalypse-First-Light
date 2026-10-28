package com.antaurora.apofirstlight.cooking;

import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.WaterQuery;
import com.antaurora.apofirstlight.liquid.world.LiquidWorld;
import com.antaurora.apofirstlight.radiation.RadiationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Enamel water pail (Cooking V1 K1a, docs/gameplay/cooking_v1_plan.md 7): carries up to 12 L of the AFL water, its amount
 * in the stack's tag ({@link #WATER_KEY}, mL) with whether it was boiled ({@link #BOILED_KEY}: the box stove, K1b) and the
 * contamination of where it was taken (contamination/ItemContamination), and its heat ({@link PailHeat}: hot off the box
 * stove, cooling in the hand). Never stacks.
 * <ul>
 *   <li>use at water in reach ({@link #scoopAt}): dip it, filled to 12 L where the water is deep enough (a pail's depth),
 *   less where it is shallower; taken out of the engine's cell there, or free from a river (its cells are boundaries: the
 *   river keeps its level) or a vanilla water block;</li>
 *   <li>hold use elsewhere: tip it out ahead, {@link #POUR_ML} a tick after a short tip ({@link #POUR_TIP}), into the
 *   engine as parcels; let go to stop. No drawing water from it (user 2026-10-27: "后面我们做水壶，先不做取水").</li>
 * </ul>
 * Carried, the water sloshes and spills over the rim ({@link PailSlosh}); the client draws it (client/cooking/WaterPailRenderer).
 */
public final class WaterPailItem extends Item {
    /** mL to the fill line, and a pail's depth of water that fills it when dipped (m). */
    public static final long CAPACITY = 12_000;
    static final double FULL_DIP = 0.20;
    public static final String WATER_KEY = "AflWaterMl", BOILED_KEY = "AflBoiled";
    /** How far from the eyes water can be scooped (m). */
    public static final double REACH = 2.5;
    /** Ticks the pail takes to tip before water runs (item/water_pail_pour: tipped at 0.45 s), and mL a tick then (2.4 L a second: 12 L in 5 s). */
    public static final int POUR_TIP = 9;
    public static final long POUR_ML = 120;

    public WaterPailItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** mL of water in this pail. */
    public static long water(ItemStack stack) {
        return stack.getTag() == null ? 0 : Math.max(0, stack.getTag().getLong(WATER_KEY));
    }

    public static boolean boiled(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(BOILED_KEY);
    }

    /** Sets the water (mL); empty, the tag's water, boiled flag and contamination go. */
    public static void setWater(ItemStack stack, long ml) {
        if (ml > 0) {
            stack.getOrCreateTag().putLong(WATER_KEY, Math.min(ml, CAPACITY));
            return;
        }
        if (stack.getTag() == null) return;
        stack.getTag().remove(WATER_KEY);
        stack.getTag().remove(BOILED_KEY);
        PailHeat.clear(stack.getTag());
        ItemContamination.clear(stack);   // may drop the whole tag already
        if (stack.getTag() != null && stack.getTag().isEmpty()) stack.setTag(null);
    }

    /**
     * The water surface a pail dipped from here would reach (world: the point on the surface under the view), or null:
     * the first point along the view inside the AFL water (WaterQuery, either side) or a vanilla water block, short of the
     * first block the view hits and within {@link #REACH}.
     */
    @Nullable
    public static Vec3 scoopAt(Level level, Player player) {
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1);
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double max = hit.getType() == HitResult.Type.MISS ? REACH : hit.getLocation().distanceTo(eye) + 0.05;
        for (double d = 0.3; d <= max; d += 0.05) {
            Vec3 q = eye.add(look.scale(d));
            double s = WaterQuery.surfaceAt(level, q.x, q.y, q.z);
            if (!Double.isNaN(s)) return new Vec3(q.x, s, q.z);
            BlockPos pos = BlockPos.containing(q);
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && q.y < pos.getY() + fluid.getHeight(level, pos)) return new Vec3(q.x, pos.getY() + fluid.getHeight(level, pos), q.z);
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 at = scoopAt(level, player);
        if (at != null && water(stack) < CAPACITY) {
            if (level instanceof ServerLevel server) scoop(server, player, stack, at);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (water(stack) <= 0) return InteractionResultHolder.pass(stack);   // a full pail at water pours, as anywhere
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Server: the pail dipped at a water surface; filled as deep as the water there allows. */
    private static void scoop(ServerLevel level, Player player, ItemStack stack, Vec3 at) {
        long have = water(stack);
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        double y = at.y - 0.02;
        LiquidEngine engine = LiquidWorld.engine(level);
        LiquidCell cell = engine == null ? null : engine.cellAt(x, y, z);
        double depth = cell == null ? 1 : at.y - cell.floor();   // a vanilla water block: deep enough
        long fillTo = Math.min(CAPACITY, (long) (CAPACITY * Math.min(1, depth / FULL_DIP)));
        long want = fillTo - have;
        if (want <= 0) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.water_pail.shallow"), true);
            return;
        }
        long got = cell == null || cell.boundary() ? want : engine.remove(x, y, z, want);
        if (got <= 0) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.water_pail.shallow"), true);
            return;
        }
        BlockPos pos = BlockPos.containing(at.x, y, at.z);
        ItemContamination.applyMinimumLevel(stack, ItemContamination.getTargetLevel(RadiationManager.getAmbientRadiationForContamination(level, pos)));
        stack.getOrCreateTag().remove(BOILED_KEY);   // raw water in it now
        PailHeat.mixIn(stack, have, got, level.getGameTime());
        setWater(stack, have + got);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8F, 0.9F + level.random.nextFloat() * 0.15F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, at);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /**
     * Ticks the pail in this entity's main hand has been tipped (pouring), or -1. Not by the stack's identity: the server
     * changes the water every tick, the client's stack is replaced on each sync and the one being used catches up a tick
     * later, so an identity test flickered between pouring and hanging (in game 2026-10-27).
     */
    public static int pourTicks(LivingEntity entity, ItemStack stack) {
        return stack.getItem() instanceof WaterPailItem && entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                && entity.getUseItem().getItem() instanceof WaterPailItem ? entity.getTicksUsingItem() : -1;
    }

    /**
     * Where the poured water leaves the lip (world), as item/water_pail_pour holds it: its lip key 5 px in from the main
     * shoulder (22 px over the feet, 5 px out), 9 px down, 11 px ahead (model px of 0.0586 m), turning with the body.
     */
    public static Vec3 lip(LivingEntity entity) {
        double yaw = Math.toRadians(entity.yBodyRot), px = 0.9375 / 16;
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double side = (5 - 5.0) * px * (entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1);
        return entity.position().add(ahead.scale(11.0 * px)).add(right.scale(side)).add(0, (22 - 9) * px, 0);
    }

    /** Server: the pour, a parcel every tick once it is tipped. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int ticks = getUseDuration(stack) - remaining;
        long have = water(stack);
        if (have <= 0) {
            entity.releaseUsingItem();
            return;
        }
        if (ticks == 1) level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.7F, 1.0F);
        if (ticks < POUR_TIP) return;
        long out = Math.min(have, POUR_ML);
        double yaw = Math.toRadians(entity.yBodyRot);
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), v = entity.getDeltaMovement().scale(20);
        PailSlosh.launch(server, lip(entity), new Vec3(v.x + ahead.x * 0.9, -0.2, v.z + ahead.z * 0.9), out, entity.getId());
        setWater(stack, have - out);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        long ml = water(stack);
        String cap = litres(CAPACITY);
        lines.add((ml <= 0 ? Component.translatable("tooltip.apocalypse_firstlight.water_pail.empty", cap)
                : Component.translatable("tooltip.apocalypse_firstlight.water_pail." + (boiled(stack) ? "boiled" : "raw"), litres(ml), cap))
                .withStyle(ChatFormatting.GRAY));
        double t = level == null ? PailHeat.AMBIENT : PailHeat.temp(stack, level.getGameTime());
        if (t >= PailHeat.AMBIENT + 5)
            lines.add(Component.translatable("tooltip.apocalypse_firstlight.water_pail.temp", Math.round(t)).withStyle(ChatFormatting.GRAY));
    }

    /** mL as litres with one decimal (no decimal when whole). */
    static String litres(long ml) {
        return ml % 1000 == 0 ? Long.toString(ml / 1000) : String.format(Locale.ROOT, "%.1f", ml / 1000.0);
    }

    /** The water changes as it spills and pours; still the same pail in hand, so no re-equip (as FuelCanItem). */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /** A server player's pail (the weight's water, weight/AflCarriedContents). */
    public static List<net.minecraftforge.fluids.FluidStack> contents(ItemStack stack) {
        long ml = water(stack);
        return ml < 500 ? List.of() : List.of(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, (int) Math.round(ml / 1000.0)));
    }
}
