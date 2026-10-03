package com.antaurora.apofirstlight.stamina;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/**
 * Stamina V1, server side (docs/项目内容/01 - 设计/生存/耐力.md; numbers in StaminaConfig). Every player tick: movement
 * costs from what the player did since the last tick (walk, sneak, sprint, swim, tread water, climb, dig), then
 * regeneration once the delay after the last effort ran out. Jumps, melee swings, shots and reloads spend on their own
 * events. Costs scale with the carried load (PlayerWeightRuntime): movement by "move", the rest by "action";
 * regeneration by "regen". At 0 the player is winded until stamina is back at winded_resume: no sprint, weaker melee,
 * slower digging, more weapon sway and spread, heavy breathing. Creative / Spectator: always full, no effects.
 * The value is kept in the player's persistent data across logins; a death starts full.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PlayerStamina {
    private static final String KEY = "afl_stamina";
    private static final int BREATH_KEEP_ALIVE = 20;
    private static final Map<ServerPlayer, State> STATES = new HashMap<>();

    private static final class State {
        double value = Double.NaN, lastX, lastY, lastZ;
        boolean winded, mined, hasLast, forceSync = true;
        int delay, lastSyncTick = Integer.MIN_VALUE, breathTick = Integer.MIN_VALUE;
        float sentValue = Float.NaN, sentSway = Float.NaN;
        boolean sentWinded;
        byte breath, sentBreath, sentOwnBreath;
    }
    private PlayerStamina() {}

    private static State state(ServerPlayer player) {
        var s = STATES.computeIfAbsent(player, p -> new State());
        if (Double.isNaN(s.value)) {
            double max = StaminaConfig.get().max;
            var data = player.getPersistentData();
            s.value = data.contains(KEY) ? Math.max(0, Math.min(max, data.getDouble(KEY))) : max;
        }
        return s;
    }
    private static boolean enabled(ServerPlayer player) { return !player.isCreative() && !player.isSpectator(); }

    public static double value(ServerPlayer player) { return state(player).value; }
    public static boolean winded(ServerPlayer player) { return enabled(player) && state(player).winded; }
    /** 0 at fatigue.start stamina and above, 1 at 0. */
    public static double fatigue(ServerPlayer player) {
        if (!enabled(player)) return 0;
        double start = StaminaConfig.get().fatigue.start;
        return start <= 0 ? 0 : Math.max(0, Math.min(1, (start - state(player).value) / start));
    }
    /** NativeGunShot: the shot cone × this. */
    public static double spreadMultiplier(ServerPlayer player) { return 1 + StaminaConfig.get().fatigue.spreadExtra * fatigue(player); }

    /** The load's multipliers {move, action, regen}. */
    private static double[] weight(ServerPlayer player) {
        var load = PlayerWeightRuntime.state(player);
        return StaminaConfig.get().weightMultipliers(load == null ? 0 : load.encumbranceRatio());
    }

    /** One effort: spends amount × the load's move or action multiplier and restarts the regeneration delay. */
    public static void spend(ServerPlayer player, double amount, boolean movement) {
        if (!enabled(player) || amount <= 0) return;
        var s = state(player);
        s.value -= amount * weight(player)[movement ? 0 : 1];
        s.delay = Math.max(s.delay, (int)Math.round(StaminaConfig.get().delaySeconds * 20));
        settle(s);
    }
    /** NativeGunActions: an accepted shot. */
    public static void shot(ServerPlayer player, ResourceLocation ammo) {
        var costs = StaminaConfig.get().costs;
        spend(player, costs.shotsByAmmo.getOrDefault(ammo.toString(), costs.shot), false);
    }
    /** NativeGunActions: an accepted reload. */
    public static void reload(ServerPlayer player, ResourceLocation gun) {
        var costs = StaminaConfig.get().costs;
        spend(player, costs.reloadsByGun.getOrDefault(gun.toString(), costs.reload), false);
    }

    /** Clamp; entering 0 makes the player winded (longer delay); back at winded_resume clears it. */
    private static void settle(State s) {
        var c = StaminaConfig.get();
        if (s.value <= 0) {
            s.value = 0;
            if (!s.winded) { s.winded = true; s.delay = Math.max(s.delay, (int)Math.round(c.exhaustedDelaySeconds * 20)); }
        } else if (s.value > c.max) s.value = c.max;
        if (s.winded && s.value >= c.windedResume) s.winded = false;
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        var c = StaminaConfig.get();
        var s = state(player);
        double dx = s.hasLast ? player.getX() - s.lastX : 0, dy = s.hasLast ? player.getY() - s.lastY : 0, dz = s.hasLast ? player.getZ() - s.lastZ : 0;
        s.lastX = player.getX(); s.lastY = player.getY(); s.lastZ = player.getZ(); s.hasLast = true;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 4) horizontal = 0; // a teleport, not movement
        if (!enabled(player)) {
            s.value = c.max; s.winded = false; s.delay = 0; s.mined = false;
        } else {
            double[] w = weight(player);
            double drain = 0;
            boolean effort = false;
            if (!player.isPassenger()) {
                if (player.isSwimming()) { drain = c.costs.swim; effort = true; }
                else if (player.isInWater() && !player.onGround()) drain = c.costs.water; // treading water: no delay
                else if (player.onClimbable() && (dy > 0.01 || horizontal > 0.01)) { drain = c.costs.climb; effort = true; }
                else if (horizontal > 0.01) {
                    if (player.isSprinting()) { drain = c.costs.sprint; effort = true; }
                    else drain = player.isCrouching() ? c.costs.sneak : c.costs.walk;
                }
            }
            drain *= w[0];
            if (s.mined) { drain += c.costs.mining * w[1]; effort = true; s.mined = false; }
            s.value -= drain / 20;
            if (effort) s.delay = Math.max(s.delay, (int)Math.round(c.delaySeconds * 20));
            if (s.delay > 0) s.delay--;
            else {
                double regen = c.regen * w[2];
                if (player.isInWater()) regen *= c.regenInWater;
                if (player.getFoodData().getFoodLevel() <= c.lowFoodLevel) regen *= c.regenLowFood;
                s.value += regen / 20;
            }
            settle(s);
            if (s.winded && player.isSprinting() && !player.isPassenger()) player.setSprinting(false);
        }
        player.getPersistentData().putDouble(KEY, s.value);
        sync(player, s, c);
    }

    private static void sync(ServerPlayer player, State s, StaminaConfig c) {
        int now = player.server.getTickCount();
        double fatigue = fatigue(player);
        float sway = (float)(1 + c.fatigue.swayExtra * fatigue);
        float value = (float)s.value;
        s.breath = !enabled(player) ? 0 : s.winded ? (byte)2 : s.value < c.breath.lightBelow ? (byte)1 : 0;
        boolean changed = s.forceSync || s.winded != s.sentWinded || s.breath != s.sentOwnBreath || Math.abs(sway - s.sentSway) > 0.02f
                || (value != s.sentValue && (Math.abs(value - s.sentValue) >= 0.25f || value == (float)c.max || value == 0));
        if (changed && (s.forceSync || s.winded != s.sentWinded || s.breath != s.sentOwnBreath || now - s.lastSyncTick >= 2)) {
            AflNetwork.staminaState(player, new StaminaPackets.State(value, (float)c.max, s.winded && enabled(player), sway,
                    s.winded && enabled(player) ? (float)c.winded.digSpeed : 1f, s.breath));
            s.sentValue = value; s.sentWinded = s.winded; s.sentSway = sway; s.sentOwnBreath = s.breath; s.lastSyncTick = now; s.forceSync = false;
        }
        if (s.breath != s.sentBreath || (s.breath > 0 && now - s.breathTick >= BREATH_KEEP_ALIVE)) {
            AflNetwork.staminaBreath(player, new StaminaPackets.Breath(player.getId(), s.breath));
            s.sentBreath = s.breath; s.breathTick = now;
        }
    }

    // ---- efforts on their own events

    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isPassenger()) {
            var costs = StaminaConfig.get().costs;
            spend(player, player.isSprinting() ? costs.sprintJump : costs.jump, true);
        }
    }
    /** After the handlers that cancel a swing (a held gun, NativeGunActions#preventMelee). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void melee(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var costs = StaminaConfig.get().costs;
        var held = player.getMainHandItem();
        double cost = held.isEmpty() ? costs.meleeEmptyHand
                : costs.meleeItems.getOrDefault(String.valueOf(ForgeRegistries.ITEMS.getKey(held.getItem())), costs.melee);
        spend(player, cost, false);
    }
    /** Winded melee hits are weaker. */
    @SubscribeEvent public static void meleeDamage(LivingHurtEvent event) {
        if (event.getSource().is(DamageTypes.PLAYER_ATTACK) && event.getSource().getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() == player && winded(player))
            event.setAmount(event.getAmount() * (float)StaminaConfig.get().winded.meleeDamage);
    }
    /** The server asks for the dig speed every tick a block is being broken: that tick costs stamina; winded digs slower. */
    @SubscribeEvent public static void dig(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !enabled(player)) return;
        state(player).mined = true;
        if (winded(player)) event.setNewSpeed(event.getNewSpeed() * (float)StaminaConfig.get().winded.digSpeed);
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
    /** A death starts full (the new player has no saved value); returning from the End keeps the value. */
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
        if (event.getEntity() instanceof ServerPlayer player) { var s = state(player); s.forceSync = true; s.hasLast = false; }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }

    /** /aflstamina */
    static String describe(ServerPlayer player) {
        var s = state(player);
        double[] w = weight(player);
        return String.format(Locale.ROOT, "%s: stamina %.1f / %.0f | winded=%s | delay %d ticks | fatigue %.2f | load x move %.2f action %.2f regen %.2f | enabled=%s",
                player.getGameProfile().getName(), s.value, StaminaConfig.get().max, s.winded, s.delay, fatigue(player), w[0], w[1], w[2], enabled(player));
    }
    static void set(ServerPlayer player, double value) {
        var s = state(player);
        s.value = value;
        s.winded = false;
        settle(s);
        s.forceSync = true;
    }
}
