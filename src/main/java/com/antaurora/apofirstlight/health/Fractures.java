package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.noise.movement.MovementNoiseEvents;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fractures and splints V1 (docs/gameplay/fracture_splint_v1_plan.md): at most one fracture per limb (right / left arm,
 * right / left leg; user 2026-10-20, the recommended answers). A fall breaks bones by the health it took (after armour and
 * the ×5 fall rule): a leg with chance clamp((d - 10) / 40, 0, 0.85), a random side; an arm with clamp((d - 25) / 60, 0, 0.5)
 * only when the player falls over, the arm it lands on (FallLanding, docs/gameplay/fall_landing_v1_plan.md). A limb broken
 * again loses half its healing and its splint. Later sources (crashes, blasts) call {@link #roll}.
 * <p>
 * Healing: a splinted leg in 5 game days, an arm in 4, unsplinted × 0.25, resting (asleep, a passenger) × 1.5; like the
 * wounds only while the player is online, but a night skipped asleep counts (PlayerHealth#sleepFinished). A death clears
 * them; returning from the End keeps them.
 * <p>
 * What a fracture does, [not splinted, splinted], faded out linearly over the last 30 % of the healing (HealthConfig.Fracture):
 * <ul>
 * <li>a leg: movement speed (an attribute modifier, so the client moves the same), no sprinting until sprint_from of the healing (splinted far sooner; the client's sprint check,
 * LocalPlayerWeightSprintMixin), a lower jump (never below 0.82 × vanilla height: still up one block), 1 vanilla point more
 * from a landing of 2 blocks or more (5 HP), walking stamina costs, the natural-regeneration ceiling;</li>
 * <li>an arm, by which one: the main arm (gun and tool hand) steadies the aim less, brings it back from recoil slower,
 * digs and hits slower; the other (support) arm mostly the aim; both slow climbing; the ceiling. Reloading is not slowed
 * in V1 (the reload clips have fixed timing on the server, the gun model and the body; plan 11).</li>
 * </ul>
 * The splint (SplintItem) goes on the first unsplinted fracture in the order right leg, left leg, right arm, left arm
 * ({@link #splintTarget}: the same rule on both sides, from the marks every client gets).
 * <p>
 * Saved in the player's persistent data ({@code afl_fractures}: {@code v}, {@code list} of {part, splinted, progress}).
 * The penalties are computed from {@link View}s, so the client (its own player, ClientHealth) uses the same functions
 * with the numbers it has (the defaults, or the integrated server's).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class Fractures {
    /** A fracture as the client sees it: the limb, splinted or not, the healing done (0..1). */
    public record View(BodyPart part, boolean splinted, float progress) {}

    static final String KEY = "afl_fractures";
    private static final int VERSION = 1;
    private static final UUID MOVE_ID = UUID.fromString("8f3a2c61-5b0e-4f7a-9d1c-3e6b7a0f4c21");
    /** The order a splint goes on: legs first (not walking is what hurts most), the right before the left. */
    private static final BodyPart[] SPLINT_ORDER = {BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM};
    /** How far the bone's crack carries (blocks): the heaviest landing's noise it comes with. */
    private static final double CRACK_RADIUS = MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS;

    static final class Fracture {
        final BodyPart part;
        boolean splinted;
        double progress;

        Fracture(BodyPart part) {
            this.part = part;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("part", part.key());
            t.putBoolean("splinted", splinted);
            t.putDouble("progress", progress);
            return t;
        }
    }

    static final class State {
        final Map<BodyPart, Fracture> limbs = new EnumMap<>(BodyPart.class);
        boolean dirty;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Fractures() {
    }

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> load(player));
    }

    private static State load(ServerPlayer player) {
        State s = new State();
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        for (var t : root.getList("list", CompoundTag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BodyPart part = BodyPart.parse(c.getString("part"));
            if (part == null || !part.limb()) continue;
            Fracture f = new Fracture(part);
            f.splinted = c.getBoolean("splinted");
            f.progress = Mth.clamp(c.getDouble("progress"), 0, 1);
            s.limbs.put(part, f);
        }
        return s;
    }

    static void save(ServerPlayer player, State s) {
        if (s.limbs.isEmpty()) {
            player.getPersistentData().remove(KEY);
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("v", VERSION);
        var list = new net.minecraft.nbt.ListTag();
        for (Fracture f : s.limbs.values()) list.add(f.save());
        root.put("list", list);
        player.getPersistentData().put(KEY, root);
    }

    /** Logout: the persistent data takes the in-memory copy. */
    static void forget(ServerPlayer player) {
        State s = STATES.remove(player.getUUID());
        if (s != null) save(player, s);
    }

    /** Death: whole bones on the next life. */
    static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
        player.getPersistentData().remove(KEY);
    }

    static List<View> views(ServerPlayer player) {
        List<View> out = new ArrayList<>();
        for (Fracture f : state(player).limbs.values()) out.add(new View(f.part, f.splinted, (float) f.progress));
        return out;
    }

    /** The fractures this side knows of a player: the server's, or the client's own (other players: none). */
    public static List<View> of(Player player) {
        if (player instanceof ServerPlayer server) return views(server);
        return player.isLocalPlayer() ? ClientHealth.fractures() : List.of();
    }

    // ---- sources ----

    /** After a player's damage was scaled (PlayerHealth): a fall lands hard or falls over (FallLanding), breaking bones. */
    static void onDamage(ServerPlayer player, DamageSource source, double healthLost) {
        if (!(healthLost > 0) || !source.is(DamageTypes.FALL)) return;
        FallLanding.land(player, healthLost);
    }

    public static double chance(double[] curve, double d) {
        return Mth.clamp((d - curve[0]) / curve[1], 0, curve[2]);
    }

    /** Breaks this limb with this chance (the API for later sources: crashes, blasts, falling trees). */
    public static boolean roll(ServerPlayer player, BodyPart part, double chance) {
        if (!part.limb() || !(chance > 0) || player.getRandom().nextDouble() >= chance) return false;
        breakLimb(player, part);
        return true;
    }

    /** A new fracture, or the same limb broken again: half its healing back and its splint off. */
    static void breakLimb(ServerPlayer player, BodyPart part) {
        State s = state(player);
        Fracture f = s.limbs.get(part);
        if (f == null) {
            s.limbs.put(part, new Fracture(part));
            message(player, "broken." + part.key());
        } else {
            f.progress *= 1 - HealthConfig.get().fracture.rebreakSetback;
            f.splinted = false;
            message(player, "rebroken." + part.key());
        }
        // the bone giving (docs/gameplay/fall_landing_v1_plan.md 5), heard as far as the hard landing it comes with
        RangedSound.play(player.serverLevel(), player.position(), AflSounds.FRACTURE_CRACK.get(), SoundSource.PLAYERS,
                CRACK_RADIUS, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
        s.dirty = true;
    }

    static void breakDev(ServerPlayer player, BodyPart part) {
        breakLimb(player, part);
    }

    static void splintDev(ServerPlayer player, BodyPart part) {
        Fracture f = state(player).limbs.get(part);
        if (f == null || f.splinted) return;
        f.splinted = true;
        state(player).dirty = true;
    }

    /** Dev: heals this limb (null: every one). */
    static void healDev(ServerPlayer player, BodyPart part) {
        State s = state(player);
        if (part == null) s.limbs.clear();
        else s.limbs.remove(part);
        s.dirty = true;
    }

    // ---- time ----

    private static double healTicks(BodyPart part) {
        var c = HealthConfig.get().fracture;
        return (part.leg() ? c.legHealDays : c.armHealDays) * WoundConfig.get().dayTicks;
    }

    /** {@code ticks} of healing for every fracture (PlayerHealth once a second; a skipped night; the dev advance). */
    static void step(ServerPlayer player, long ticks, boolean resting) {
        State s = state(player);
        if (s.limbs.isEmpty()) return;
        var c = HealthConfig.get().fracture;
        for (var it = s.limbs.values().iterator(); it.hasNext(); ) {
            Fracture f = it.next();
            double rate = (f.splinted ? 1 : c.unsplinted) * (resting ? c.rest : 1);
            f.progress = Math.min(1, f.progress + ticks / healTicks(f.part) * rate);
            if (f.progress >= 1) {
                it.remove();
                s.dirty = true;
                message(player, "healed." + f.part.key());
            }
        }
    }

    /** Dev: this many game days, a second at a time, awake. */
    static void advance(ServerPlayer player, double days) {
        long steps = Math.max(1, Math.round(days * WoundConfig.get().dayTicks / 20.0));
        for (long i = 0; i < steps && !state(player).limbs.isEmpty(); i++) step(player, 20, false);
    }

    /** Once a second with the rest of the health tick: the movement modifier and no sprinting. */
    static void apply(ServerPlayer player) {
        List<View> list = views(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double m = moveMultiplier(list);
            AttributeModifier old = speed.getModifier(MOVE_ID);
            if (m >= 0.9999) {
                if (old != null) speed.removeModifier(MOVE_ID);
            } else if (old == null || Math.abs(old.getAmount() - (m - 1)) > 1e-4) {
                if (old != null) speed.removeModifier(MOVE_ID);
                speed.addTransientModifier(new AttributeModifier(MOVE_ID, "AFL fracture", m - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        if (sprintBlocked(list) && player.isSprinting() && !player.isPassenger()) player.setSprinting(false);
    }

    // ---- treatment ----

    /** The limb a splint goes on, or null: the first unsplinted fracture in {@link #SPLINT_ORDER} (masks by BodyPart#bit). */
    public static BodyPart splintTarget(int broken, int splinted) {
        for (BodyPart p : SPLINT_ORDER) if ((broken & p.bit()) != 0 && (splinted & p.bit()) == 0) return p;
        return null;
    }

    public static BodyPart splintTarget(List<View> list) {
        return splintTarget(brokenMask(list), splintedMask(list));
    }

    public static int brokenMask(List<View> list) {
        int m = 0;
        for (View v : list) m |= v.part().bit();
        return m;
    }

    public static int splintedMask(List<View> list) {
        int m = 0;
        for (View v : list) if (v.splinted()) m |= v.part().bit();
        return m;
    }

    /** Puts a splint on (SplintItem, on finishing); false when nothing takes one. */
    static boolean splint(ServerPlayer player) {
        BodyPart target = splintTarget(views(player));
        if (target == null) return false;
        state(player).limbs.get(target).splinted = true;
        state(player).dirty = true;
        message(player, "splinted." + target.key());
        return true;
    }

    // ---- what they do ([not splinted, splinted], faded over the last part of the healing) ----

    /** How far the penalties have faded (0 full, 1 gone). */
    public static double fade(View v) {
        double from = HealthConfig.get().fracture.fadeFrom;
        return Mth.clamp((v.progress() - from) / (1 - from), 0, 1);
    }

    private static double pick(double[] pair, View v) {
        return Mth.lerp(fade(v), v.splinted() ? pair[1] : pair[0], 1);
    }

    public static double moveMultiplier(List<View> list) {
        double m = 1;
        for (View v : list) if (v.part().leg()) m *= pick(HealthConfig.get().fracture.leg.move, v);
        return m;
    }

    /** Jump height × this (1: no broken leg). */
    public static double jumpMultiplier(List<View> list) {
        var leg = HealthConfig.get().fracture.leg;
        double m = 1;
        boolean any = false;
        for (View v : list) if (v.part().leg()) { m *= pick(leg.jump, v); any = true; }
        return any ? Math.max(leg.jumpFloor, m) : 1;
    }

    public static double staminaCostMultiplier(List<View> list) {
        double m = 1;
        for (View v : list) if (v.part().leg()) m *= pick(HealthConfig.get().fracture.leg.stamina, v);
        return m;
    }

    public static boolean sprintBlocked(List<View> list) {
        double[] from = HealthConfig.get().fracture.sprintFrom;
        for (View v : list) if (v.part().leg() && v.progress() < (v.splinted() ? from[1] : from[0])) return true;
        return false;
    }

    /** A leg still hurting (not yet faded out): the hard landing. */
    public static boolean legHurts(List<View> list) {
        for (View v : list) if (v.part().leg() && fade(v) < 1) return true;
        return false;
    }

    /** One arm number (sway, recoil_recovery, dig, melee, climb) multiplied over the broken arms. */
    public enum ArmEffect { SWAY, RECOIL_RECOVERY, DIG, MELEE, CLIMB }

    public static double arm(Player player, List<View> list, ArmEffect effect) {
        var c = HealthConfig.get().fracture;
        double m = 1;
        for (View v : list) {
            if (!v.part().arm()) continue;
            HealthConfig.Arm a = v.part().mainArm(player) ? c.mainArm : c.supportArm;
            double[] pair = switch (effect) {
                case SWAY -> a.sway;
                case RECOIL_RECOVERY -> a.recoilRecovery;
                case DIG -> a.dig;
                case MELEE -> a.melee;
                case CLIMB -> a.climb;
            };
            m *= pick(pair, v);
        }
        return m;
    }

    public static double arm(Player player, ArmEffect effect) {
        return arm(player, of(player), effect);
    }

    /** The natural-regeneration ceiling (HealthCap): the lowest of the fractures', faded up to the maximum. */
    static double cap(ServerPlayer player) {
        var c = HealthConfig.get().fracture;
        double max = player.getMaxHealth(), cap = max;
        for (View v : views(player)) {
            double[] pair = v.part().leg() ? c.leg.cap : (v.part().mainArm(player) ? c.mainArm.cap : c.supportArm.cap);
            cap = Math.min(cap, Mth.lerp(fade(v), v.splinted() ? pair[1] : pair[0], max));
        }
        return cap;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fracture." + key), true);
    }

    // ---- the penalties on their events ----

    /** A lower jump (height ∝ the take-off speed squared). Both sides: the client moves its own player. */
    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isPassenger()) return;
        double m = jumpMultiplier(of(player));
        if (m >= 0.9999) return;
        Vec3 v = player.getDeltaMovement();
        if (v.y > 0) player.setDeltaMovement(v.x, v.y * Math.sqrt(m), v.z);
    }

    /** A hard landing on a broken leg: from landing_from blocks the fall counts landing_extra blocks more (vanilla starts at 3). */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var leg = HealthConfig.get().fracture.leg;
        if (event.getDistance() < leg.landingFrom || !legHurts(views(player))) return;
        event.setDistance((float) (Math.max(event.getDistance(), 3) + leg.landingExtra));
    }

    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        double m = arm(event.getEntity(), ArmEffect.DIG);
        if (m < 0.9999) event.setNewSpeed((float) (event.getNewSpeed() * m));
    }

    /** A weaker melee hit from a broken arm. */
    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof ServerPlayer attacker)) return;
        double m = arm(attacker, ArmEffect.MELEE);
        if (m < 0.9999) event.setAmount((float) (event.getAmount() * m));
    }

    /** Slower climbing (ladders, vines): the own player's upward speed, after its move (it goes into the next one). */
    @SubscribeEvent
    public static void climb(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || !player.level().isClientSide || !player.isLocalPlayer() || !player.onClimbable()) return;
        Vec3 v = player.getDeltaMovement();
        if (v.y <= 0) return;
        double m = arm(player, ArmEffect.CLIMB);
        if (m < 0.9999) player.setDeltaMovement(v.x, v.y * m, v.z);
    }
}
