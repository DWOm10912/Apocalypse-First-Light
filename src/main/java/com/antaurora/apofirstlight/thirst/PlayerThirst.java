package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.radiation.RadiationExposureProvider;
import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thirst V1, server side (docs/项目内容/01 - 设计/生存/口渴.md; numbers in ThirstConfig). Thirst falls by a base rate and
 * with the stamina spent (PlayerStamina#consumeSpent); drinks raise it. Raw water may give the stomach bug
 * (GastroenteritisEffect), radioactive water adds cumulative dose. Low thirst slows stamina regeneration and, dehydrated,
 * stops natural healing (FoodDataThirstMixin); at 0 it hurts like starvation. Glass bottles filled from a water source
 * become dirty water bottles carrying the place's contamination level. Creative / Spectator: frozen, no effects;
 * Peaceful: does not fall and does not hurt. The value is kept across logins; a death starts full.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PlayerThirst {
    private static final String KEY = "afl_thirst";
    public static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dehydration"));
    /** The client's last synced own thirst (ClientThirst), for the client side of canDrink. */
    static volatile float clientValue = Float.NaN, clientMax = 100;
    private static final Map<ServerPlayer, State> STATES = new HashMap<>();

    private static final class State {
        double value = Double.NaN;
        int damageTimer, symptomCooldown, lastSyncTick = Integer.MIN_VALUE;
        long nextSip;
        boolean forceSync = true;
        float sent = Float.NaN;
    }
    private PlayerThirst() {}

    private static State state(ServerPlayer player) {
        var s = STATES.computeIfAbsent(player, p -> new State());
        if (Double.isNaN(s.value)) {
            double max = ThirstConfig.get().max;
            var data = player.getPersistentData();
            s.value = data.contains(KEY) ? Math.max(0, Math.min(max, data.getDouble(KEY))) : max;
        }
        return s;
    }
    private static boolean enabled(Player player) { return !player.isCreative() && !player.isSpectator(); }

    public static double value(ServerPlayer player) { return state(player).value; }
    public static boolean sick(Player player) { return player.hasEffect(AflMobEffects.GASTROENTERITIS.get()); }

    /** Survival: only while not full. Creative / Spectator always (no effect). */
    public static boolean canDrink(Player player) {
        if (!enabled(player)) return true;
        if (player.level().isClientSide) return Float.isNaN(clientValue) || clientValue < clientMax - 0.01f;
        return player instanceof ServerPlayer serverPlayer && state(serverPlayer).value < ThirstConfig.get().max - 0.01;
    }
    /** PlayerStamina: stamina regeneration × this (thirsty, dehydrated, sick). */
    public static double staminaRegenMultiplier(ServerPlayer player) {
        if (!enabled(player)) return 1;
        var c = ThirstConfig.get();
        double value = state(player).value;
        double m = value < c.dehydratedBelow ? c.staminaRegenDehydrated : value < c.thirstyBelow ? c.staminaRegenThirsty : 1;
        return sick(player) ? m * c.sickness.staminaRegen : m;
    }
    /** FoodDataThirstMixin: no natural regeneration while dehydrated. */
    public static boolean blocksNaturalRegen(Player player) {
        return player instanceof ServerPlayer serverPlayer && enabled(serverPlayer) && state(serverPlayer).value < ThirstConfig.get().dehydratedBelow;
    }

    /** One drink: thirst, then the stomach-bug roll for raw water, then the dose for radioactive water. */
    public static void drink(ServerPlayer player, double amount, boolean raw, int contamination, boolean sip) {
        if (!enabled(player)) return;
        var c = ThirstConfig.get();
        var s = state(player);
        s.value = Math.min(c.max, s.value + amount);
        if (raw && player.getRandom().nextDouble() < (sip ? c.sickness.chanceSip : c.sickness.chanceBottle)) infect(player, s, c);
        double dose = c.dose(contamination, sip);
        if (dose > 0) player.getCapability(RadiationExposureProvider.CAPABILITY).ifPresent(exposure -> exposure.addDose(dose));
        s.forceSync = true;
    }
    /** Infected again while sick: the duration starts over, the symptom spacing does not (its frequency cap). */
    private static void infect(ServerPlayer player, State s, ThirstConfig c) {
        boolean wasSick = sick(player);
        player.addEffect(new MobEffectInstance(AflMobEffects.GASTROENTERITIS.get(), (int)Math.round(c.sickness.durationSeconds * 20),
                0, false, true, true));
        if (!wasSick) s.symptomCooldown = symptomInterval(player, c);
    }
    private static int symptomInterval(ServerPlayer player, ThirstConfig c) {
        double seconds = c.sickness.symptomMinSeconds + player.getRandom().nextDouble() * (c.sickness.symptomMaxSeconds - c.sickness.symptomMinSeconds);
        return (int)Math.round(seconds * 20);
    }

    /** The vanilla water source block the player looks at within reach (fluids: sources only), or null. */
    @Nullable
    public static BlockPos waterSourceInView(Player player) {
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1.0F).scale(player.getBlockReach()));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        var fluid = player.level().getFluidState(hit.getBlockPos());
        return fluid.getType() == Fluids.WATER && fluid.isSource() ? hit.getBlockPos() : null;
    }
    /** Contamination of water taken here: the environment-to-level table used for block drops (辐射系统.md §11). */
    private static int contaminationAt(ServerLevel level, BlockPos pos) {
        return ItemContamination.getTargetLevel(RadiationManager.getAmbientRadiationForContamination(level, pos)).value();
    }

    /** ThirstPackets.Sip: sneak + empty hand at a water source; the server checks it all again. */
    static void sip(ServerPlayer player) {
        var c = ThirstConfig.get();
        var s = state(player);
        long now = player.level().getGameTime();
        if (now < s.nextSip || !player.getMainHandItem().isEmpty() || !player.isShiftKeyDown() || !canDrink(player)) return;
        BlockPos pos = waterSourceInView(player);
        if (pos == null || !player.level().mayInteract(player, pos)) return;
        s.nextSip = now + c.sipCooldownTicks;
        drink(player, c.sip, true, contaminationAt(player.serverLevel(), pos), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS,
                0.5F, player.level().random.nextFloat() * 0.1F + 0.9F);
        player.level().gameEvent(player, GameEvent.DRINK, player.position());
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /** A glass bottle used at a water source: a dirty water bottle with the place's contamination (instead of vanilla's). */
    @SubscribeEvent public static void fillBottle(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.GLASS_BOTTLE)) return;
        Player player = event.getEntity();
        BlockPos pos = waterSourceInView(player);
        if (pos == null || !event.getLevel().mayInteract(player, pos)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.getLevel().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            event.getLevel().gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            ItemStack water = new ItemStack(AflItems.DIRTY_WATER_BOTTLE.get());
            ItemContamination.setLevel(water, contaminationAt(serverPlayer.serverLevel(), pos));
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(stack, player, water));
            player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
        }
    }
    /** Foods and drinks with water in them (thirst config "foods"). */
    @SubscribeEvent public static void eat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Double amount = ThirstConfig.get().foods.get(String.valueOf(ForgeRegistries.ITEMS.getKey(event.getItem().getItem())));
        if (amount != null && amount > 0) drink(player, amount, false, 0, false);
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        var c = ThirstConfig.get();
        var s = state(player);
        double spent = PlayerStamina.consumeSpent(player);
        if (enabled(player)) {
            boolean peaceful = player.level().getDifficulty() == Difficulty.PEACEFUL;
            boolean sick = sick(player);
            if (!peaceful) {
                double drain = c.basePerHour / 72000.0 + spent * c.exertionPer100Stamina / 100.0;
                if (sick) drain *= c.sickness.thirstMultiplier;
                drain *= com.antaurora.apofirstlight.temperature.PlayerTemperature.thirstMultiplier(player); // heat stages
                s.value = Math.max(0, s.value - drain);
            }
            if (sick) {
                player.causeFoodExhaustion((float)(c.sickness.extraExhaustionPerSecond / 20));
                if (s.symptomCooldown > 0) s.symptomCooldown--;
                else { symptom(player, s, c); s.symptomCooldown = symptomInterval(player, c); }
            }
            if (s.value <= 0 && !peaceful) {
                if (++s.damageTimer >= c.damageIntervalSeconds * 20) { s.damageTimer = 0; dehydrationDamage(player); }
            } else s.damageTimer = 0;
        }
        player.getPersistentData().putDouble(KEY, s.value);
        sync(player, s, c);
    }
    private static void symptom(ServerPlayer player, State s, ThirstConfig c) {
        s.value = Math.max(0, s.value - c.sickness.symptomThirst);
        var food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - c.sickness.symptomFood));
        if (c.sickness.symptomNauseaSeconds > 0) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                (int)Math.round(c.sickness.symptomNauseaSeconds * 20), 0, false, false, false));
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.thirst.gastro_symptom"), true);
        s.forceSync = true;
    }
    /** Same limits as vanilla starvation: Easy stops at 10 health, Normal at 1, Hard can kill. */
    private static void dehydrationDamage(ServerPlayer player) {
        var difficulty = player.level().getDifficulty();
        if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
            var type = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEHYDRATION);
            player.hurt(new DamageSource(type), 1.0F);
        }
    }
    private static void sync(ServerPlayer player, State s, ThirstConfig c) {
        int now = player.server.getTickCount();
        float value = (float)s.value;
        if (s.forceSync || (value != s.sent && now - s.lastSyncTick >= 10)) {
            AflNetwork.thirstState(player, new ThirstPackets.State(value, (float)c.max));
            s.sent = value; s.lastSyncTick = now; s.forceSync = false;
        }
    }

    // ---- lifecycle

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) state(player).forceSync = true;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var s = STATES.remove(player);
            if (s != null) player.getPersistentData().putDouble(KEY, s.value);
        }
    }
    /** A death starts full; returning from the End keeps the value. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer player)) return;
        var s = STATES.remove(original);
        if (event.isWasDeath()) player.getPersistentData().remove(KEY);
        else if (s != null) player.getPersistentData().putDouble(KEY, s.value);
        state(player).forceSync = true;
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) state(player).forceSync = true;
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) state(player).forceSync = true;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }

    /** /aflthirst */
    static String describe(ServerPlayer player) {
        var s = state(player);
        var effect = player.getEffect(AflMobEffects.GASTROENTERITIS.get());
        return String.format(Locale.ROOT, "%s: thirst %.1f / %.0f | sick=%s%s | next symptom %d ticks | stamina regen x%.2f | natural regen %s | enabled=%s",
                player.getGameProfile().getName(), s.value, ThirstConfig.get().max, effect != null,
                effect != null ? " (" + effect.getDuration() / 20 + " s left)" : "", s.symptomCooldown,
                staminaRegenMultiplier(player), blocksNaturalRegen(player) ? "blocked" : "normal", enabled(player));
    }
    static void set(ServerPlayer player, double value) {
        var s = state(player);
        s.value = Math.max(0, Math.min(ThirstConfig.get().max, value));
        s.forceSync = true;
    }
    static void infect(ServerPlayer player) { infect(player, state(player), ThirstConfig.get()); }
}
