package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.InfectedEntityRules;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.survivor.SurvivorStats;
import com.antaurora.apofirstlight.temperature.PlayerTemperature;
import com.antaurora.apofirstlight.thirst.PlayerThirst;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Survival and health V1a (docs/gameplay/survival_health_v1a.md): players at 100 HP.
 * <ul>
 * <li><b>Maximum.</b> The max_health base value is {@code max_health} (100) for every player; survivor body levels, growth and
 * traits do not change it. A player whose base was lower (an old 20 HP save, or a fresh player object after a death)
 * has the base raised and the current health scaled by the same ratio, so a save at 18 / 20 comes back at 90 / 100.
 * Returning from the End (vanilla copies health onto a fresh 20 HP player) keeps the health.</li>
 * <li><b>Damage.</b> Scaled in {@link LivingDamageEvent}, which Player#actuallyHurt posts after armour, armour toughness,
 * enchantment / resistance reduction and absorption: those all still work on the vanilla-scale amount (one absorption
 * point stops about 5 HP), and only what actually comes off health is scaled, once, by the first matching rule in
 * health_v1.json (infected melee ×4, bullets by a curve, falls ×5) or the default ×5. Players only: mobs and NPCs keep
 * vanilla numbers, so guns do what they always did to the infected. Damage types in never_below_one (vanilla starvation,
 * which on Normal stops at 1 HP) keep the player at 1 HP at least. Lightning is ×1: LightningInjury hurts in AFL HP.</li>
 * <li><b>Natural regeneration.</b> Vanilla's food regeneration is off (mixin/FoodDataThirstMixin); this class heals instead,
 * once a second: none for {@code delay_seconds} after the last damage; then awake_per_minute, or rest_per_minute while
 * sleeping or sitting on a seat the player does not drive (furniture chair, office chair, a vehicle's passenger seat),
 * × natural_regen (survivor attribute) × the wounds' multiplier, up to {@link HealthCap#cap}. Needs food ≥ min_food, the
 * naturalRegeneration rule, and neither dehydration (PlayerThirst) nor the hypothermia / heat exhaustion stages
 * (PlayerTemperature): one decision here, so the old mixins no longer each block on their own. Radiation sickness still
 * scales every heal (RadiationSicknessEvents).</li>
 * <li><b>Sleep.</b> A night skipped by sleeping heals every sleeping player once: sleep_per_minute per real minute of
 * skipped game time, at most sleep_max, under the same conditions; the skip's target time is remembered so the same
 * skip never heals twice.</li>
 * </ul>
 * Each second the regeneration state, the wounds and the fractures go to the client (HealthPackets) when they changed (and
 * at least every 5 s while there are wounds or fractures, for their recovery bars). Fractures (Fractures) heal here too, a
 * second at a time and over a night skipped asleep, and lower the regeneration ceiling (HealthCap).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PlayerHealth {
    /** What natural regeneration does now (the client's badge). */
    public enum Regen { NONE, AWAKE, REST, WAITING, BLOCKED }

    static final String KEY = "afl_health";

    private static final class State {
        long lastDamageTick = Long.MIN_VALUE / 2;
        Regen regen = Regen.NONE;
        HealthPackets.State sent;
        int sinceSync;
        boolean forceSync = true;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static {
        HealthCap.register(Fractures::cap);
    }

    private PlayerHealth() {
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    // ---- maximum ----

    /** Raises the max_health base to the configured maximum, scaling the current health by the same ratio. */
    static void ensureMaximum(ServerPlayer player) {
        var instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;
        HealthConfig c = HealthConfig.get();
        double base = instance.getBaseValue();
        if (Math.abs(base - c.maxHealth) < 1e-6) return;
        float health = player.getHealth();
        double ratio = base > 0 ? c.maxHealth / base : c.legacyScale;
        instance.setBaseValue(c.maxHealth);
        if (player.isAlive()) player.setHealth((float) Math.min(player.getMaxHealth(), Math.max(1, health * ratio)));
        ApocalypseFirstLight.LOGGER.debug("[AFL HEALTH] {}: max_health base {} -> {}, health {} -> {}",
                player.getGameProfile().getName(), base, c.maxHealth, health, player.getHealth());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensureMaximum(player);
            state(player).forceSync = true;
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Wounds.forget(player);
            Fractures.forget(player);
            STATES.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isWasDeath()) {
            Wounds.clear(player);
            Fractures.clear(player);
        } else {
            // vanilla put the old health on a fresh 20 HP player (clamped): raise the base first, then take the health back
            var instance = player.getAttribute(Attributes.MAX_HEALTH);
            if (instance != null) instance.setBaseValue(HealthConfig.get().maxHealth);
            player.setHealth(Math.min(player.getMaxHealth(), original.getHealth()));
            Wounds.save(player, Wounds.state(player));
            Fractures.save(player, Fractures.state(player));
            CompoundTag health = original.getPersistentData().getCompound(KEY);
            if (!health.isEmpty()) player.getPersistentData().put(KEY, health.copy());
        }
        STATES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensureMaximum(player);
            state(player).forceSync = true;
        }
    }

    // ---- damage ----

    /** Health lost for a vanilla-scale amount that got past armour and absorption. */
    static double scaled(DamageSource source, double amount) {
        HealthConfig c = HealthConfig.get();
        for (HealthConfig.Rule rule : c.damage.rules) {
            if (!matches(rule.type, source)) continue;
            if (rule.infectedAttacker && !(source.getEntity() instanceof LivingEntity attacker && InfectedEntityRules.isInfected(attacker))) continue;
            return Math.max(0, rule.apply(amount));
        }
        return amount * c.damage.defaultScale;
    }

    private static boolean matches(String type, DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> k.location().toString().equals(type)).orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float amount = event.getAmount();
        if (!(amount > 0)) return;
        DamageSource source = event.getSource();
        double health = amount >= 3.0E37F ? amount : scaled(source, amount);
        for (String type : HealthConfig.get().damage.neverBelowOne)
            if (matches(type, source)) health = Math.min(health, Math.max(0, player.getHealth() - 1));
        event.setAmount((float) health);
        if (health > 0) {
            state(player).lastDamageTick = player.server.getTickCount();
            Wounds.onDamage(player, source, (float) health);
            Fractures.onDamage(player, source, health);
            LightningInjury.onDamage(player, source, health);
            Knockdown.onDamage(player, source, health);
        }
    }

    // ---- natural regeneration ----

    /** Whether the player rests: asleep, or sitting on something it does not drive (not a ridden animal). */
    static boolean resting(ServerPlayer player) {
        if (player.isSleeping()) return true;
        Entity vehicle = player.getVehicle();
        return vehicle != null && !(vehicle instanceof LivingEntity) && vehicle.getControllingPassenger() != player;
    }

    /** The conditions natural regeneration needs (the delay and the ceiling aside). */
    static boolean canRegenerate(ServerPlayer player) {
        return player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                && player.getFoodData().getFoodLevel() >= HealthConfig.get().regen.minFood
                && !PlayerThirst.blocksNaturalRegen(player) && !PlayerTemperature.blocksNaturalRegen(player);
    }

    /** The natural healing multiplier: the survivor's natural_regen x the wounds' x a lightning aftermath's (SurvivorStatsGameTests reads it). */
    public static double regenMultiplier(ServerPlayer player) {
        return SurvivorStats.multiplier(player, AflAttributes.NATURAL_REGEN, 0) * Wounds.healthRegenMultiplier(player)
                * LightningInjury.healthRegenMultiplier(player);
    }

    /** Heals by this much toward the ceiling (never above it, never taking anything away). */
    private static void heal(ServerPlayer player, double amount) {
        double room = HealthCap.cap(player) - player.getHealth();
        if (room <= 0 || !(amount > 0)) return;
        player.heal((float) Math.min(room, amount));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if ((player.tickCount + player.getId()) % 20 != 0) return;
        State s = state(player);
        ensureMaximum(player);
        Wounds.step(player, 20);
        Fractures.step(player, 20, resting(player));
        Fractures.apply(player);
        HealthConfig c = HealthConfig.get();
        Regen regen;
        if (!player.isAlive() || player.isSpectator()) regen = Regen.NONE;
        else if (player.getHealth() >= Math.min(player.getMaxHealth(), HealthCap.cap(player)) - 1e-4) regen = Regen.NONE;
        else if (!canRegenerate(player)) regen = Regen.BLOCKED;
        else if (player.server.getTickCount() - s.lastDamageTick < c.regen.delaySeconds * 20) regen = Regen.WAITING;
        else regen = resting(player) ? Regen.REST : Regen.AWAKE;
        if (regen == Regen.AWAKE || regen == Regen.REST) {
            double perMinute = regen == Regen.REST ? c.regen.restPerMinute : c.regen.awakePerMinute;
            heal(player, perMinute / 60.0 * regenMultiplier(player));
        }
        s.regen = regen;
        sync(player, s);
    }

    private static void sync(ServerPlayer player, State s) {
        var wounds = Wounds.state(player);
        if (wounds.dirty) {
            Wounds.save(player, wounds);
            wounds.dirty = false;
            s.forceSync = true;
        }
        var bones = Fractures.state(player);
        if (bones.dirty) {
            Fractures.save(player, bones);
            bones.dirty = false;
            s.forceSync = true;
        }
        List<Wounds.View> views = Wounds.views(player);
        List<Fractures.View> fractures = Fractures.views(player);
        BodyMarkSync.update(player, views, fractures);   // the dressings and splints everyone who draws this player sees
        var packet = new HealthPackets.State((byte) s.regen.ordinal(), (float) Wounds.visual(player), views, fractures);
        s.sinceSync++;
        boolean changed = s.sent == null || s.sent.regen() != packet.regen() || !sameShape(s.sent.wounds(), views)
                || !sameFractures(s.sent.fractures(), fractures)
                || Math.abs(s.sent.visual() - packet.visual()) > 0.02
                || s.sinceSync >= 5 && (!views.isEmpty() && !Objects.equals(s.sent.wounds(), views)
                        || !fractures.isEmpty() && !Objects.equals(s.sent.fractures(), fractures));
        if (!s.forceSync && !changed) return;
        s.forceSync = false;
        s.sinceSync = 0;
        s.sent = packet;
        AflNetwork.healthState(player, packet);
    }

    /** Same wounds in the same stages and treatment (only the recovery bars differ). */
    private static boolean sameShape(List<Wounds.View> a, List<Wounds.View> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            var x = a.get(i);
            var y = b.get(i);
            if (x.stage() != y.stage() || x.cleaned() != y.cleaned() || x.bandaged() != y.bandaged() || x.part() != y.part() || x.kind() != y.kind()) return false;
        }
        return true;
    }

    /** Same broken limbs, splinted alike (only the healing differs). */
    private static boolean sameFractures(List<Fractures.View> a, List<Fractures.View> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).part() != b.get(i).part() || a.get(i).splinted() != b.get(i).splinted()) return false;
        return true;
    }

    @SubscribeEvent
    public static void sleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        HealthConfig c = HealthConfig.get();
        long target = event.getNewTime();
        long skipped = Math.max(0, target - level.getDayTime());
        if (skipped <= 0) return;
        for (ServerPlayer player : level.players()) {
            if (!player.isSleeping() || !player.isAlive()) continue;
            CompoundTag tag = player.getPersistentData().getCompound(KEY);
            if (tag.contains("last_sleep") && tag.getLong("last_sleep") == target) continue;
            tag.putLong("last_sleep", target);
            player.getPersistentData().put(KEY, tag);
            Fractures.step(player, skipped, true);   // bones heal over the skipped night, asleep
            state(player).forceSync = true;
            if (!canRegenerate(player)) continue;
            double amount = Math.min(c.regen.sleepMax, skipped / 1200.0 * c.regen.sleepPerMinute * regenMultiplier(player));
            heal(player, amount);
            state(player).forceSync = true;
        }
    }

    // ---- dev (/afl health) ----

    static String describe(ServerPlayer player) {
        State s = state(player);
        long since = player.server.getTickCount() - s.lastDamageTick;
        return String.format(java.util.Locale.ROOT,
                "health %.1f / %.0f (cap %.0f, absorption %.1f) | regen %s, last damage %s s ago, x%.2f | wounds %s | fractures %s | stamina regen x%.2f, visual %.2f",
                player.getHealth(), player.getMaxHealth(), HealthCap.cap(player), player.getAbsorptionAmount(), s.regen,
                since > 1_000_000 ? "-" : String.valueOf(since / 20), regenMultiplier(player), Wounds.views(player), Fractures.views(player),
                Wounds.staminaRegenMultiplier(player), Wounds.visual(player));
    }

    static void markSync(ServerPlayer player) {
        state(player).forceSync = true;
    }
}
