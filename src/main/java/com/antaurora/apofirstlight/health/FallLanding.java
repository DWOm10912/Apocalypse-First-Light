package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.noise.movement.MovementNoiseEvents;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.weight.EncumbranceState;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Landing and falling over V1 (docs/gameplay/fall_landing_v1_plan.md, user 2026-10-20): a fall that hurts lands hard
 * (body/land_hard, 0.6 s: the legs give and catch it; only a leg may break) or falls over (body/fall_over, 2.8 s: forward
 * onto the left hand, or the right one mirrored, kneeling, then up; no moving, jumping or using things meanwhile). The leg
 * is rolled first (Fractures, leg_fall); the fall-over chance follows section 3 (HealthConfig.FallOver), and a leg that
 * just broke always falls over. The arm is rolled only on falling over, the hand it lands on, when that hand meets the
 * ground ({@link #HAND_HITS} ticks in), with the body's thump. A fall that kills plays nothing.
 * <p>
 * The server decides and tells the player and its trackers (HealthPackets.Landing); the clients play the action
 * (client/skeleton/FallLandingClient), the own one also stops its input and leads its view along the action's
 * first_person_view. The server refuses item use and attacks while the player falls over.
 * <p>
 * Lightning injury V1 (health/LightningInjury) uses the same: a strike's ground current knocks a player down
 * ({@link #knockDown}: body/fall_over, no arm roll), and after lying paralysed it gets up ({@link #getUp}: body/prone_get_up,
 * 2.6 s from lying on the front to standing; tools/medical-actions-v1a/author_lightning_getup_v1.cjs).
 * <p>
 * Injury knockdown V1 (health/Knockdown, docs/gameplay/injury_knockdown_v1_plan.md) too: a heavy hit knocks the player
 * down away from it and it gets up again ({@link #knock}: body/knock_front, knock_back, knock_side), and very low health
 * collapses it onto the front (body/collapse), where it stays crawling (Knockdown).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class FallLanding {
    public static final byte HARD = 0, FALL_OVER = 1, GET_UP = 2, KNOCK_FRONT = 3, KNOCK_BACK = 4, KNOCK_SIDE = 5, COLLAPSE = 6;
    /** The actions' lengths and the hand meeting the ground (ticks; tools/medical-actions-v1a/author_fall_v1.cjs). */
    public static final int HARD_TICKS = 12, FALL_TICKS = 56, HAND_HITS = 9, GET_UP_TICKS = 52;
    /** The knockdown actions' lengths and the body meeting the ground (ticks; tools/medical-actions-v1a/author_knockdown_v1.cjs). */
    public static final int KNOCK_FRONT_TICKS = 90, KNOCK_BACK_TICKS = 76, KNOCK_SIDE_TICKS = 74, COLLAPSE_TICKS = 40;
    private static final int KNOCK_FRONT_THUMP = 16, KNOCK_BACK_THUMP = 12, KNOCK_SIDE_THUMP = 11, COLLAPSE_THUMP = 27;
    /** How far the body's thump carries (blocks): a heavy landing's noise. */
    private static final double THUMP_RADIUS = MovementNoiseEvents.HEAVY_LANDING_RADIUS;
    /** How far in from the feet's corners the support is looked for, and how deep under them (blocks). */
    private static final double CORNER_IN = 0.05, CORNER_DEPTH = 0.1;

    private static final class Falling {
        final int start, length;
        final boolean mirror;
        final double healthLost;
        /** The hand meeting the ground thumps and may break the arm (a fall, not a knock-down or getting up). */
        final boolean handHits;
        /** A knockdown's body meeting the ground (ticks in; -1 none): the thump. */
        final int thumpAt;
        boolean handDown;

        Falling(int start, int length, boolean mirror, double healthLost, boolean handHits) {
            this(start, length, mirror, healthLost, handHits, -1);
        }

        Falling(int start, int length, boolean mirror, double healthLost, boolean handHits, int thumpAt) {
            this.start = start;
            this.length = length;
            this.mirror = mirror;
            this.healthLost = healthLost;
            this.handHits = handHits;
            this.thumpAt = thumpAt;
        }
    }

    private static final Map<UUID, Falling> FALLING = new HashMap<>();
    /** Each player's feet at its last tick end, for the horizontal speed it lands with (the server moves players by packets). */
    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    private FallLanding() {
    }

    /** A fall took this much health (Fractures#onDamage, after scaling). */
    static void land(ServerPlayer player, double healthLost) {
        if (healthLost >= player.getHealth() || player.isPassenger() || player.isInWater()) return;
        // a living ragdoll (survivor/LiveRagdolls) lands as the ragdoll: the damage, no landing of its own
        if (com.antaurora.apofirstlight.survivor.LiveRagdolls.active(player)) return;
        var c = HealthConfig.get().fracture;
        var random = player.getRandom();
        boolean legBefore = false;
        for (Fractures.View v : Fractures.views(player)) legBefore |= v.part().leg();
        boolean legBroke = Fractures.roll(player, BodyPart.randomLeg(random), Fractures.chance(c.legFall, healthLost));
        double chance = legBroke ? 1 : fallOverChance(player, healthLost, legBefore);
        boolean over = random.nextDouble() < chance;
        boolean mirror = random.nextBoolean();
        if (over) {
            FALLING.put(player.getUUID(), new Falling(player.server.getTickCount(), FALL_TICKS, mirror, healthLost, true));
            player.stopUsingItem();
            player.setSprinting(false);
        }
        var packet = new HealthPackets.Landing(player.getId(), over ? FALL_OVER : HARD, mirror);
        AflNetwork.skeletonTo(player, packet);
        AflNetwork.skeletonToTracking(player, packet);
    }

    /** Lightning injury: a strike's current knocks the player over (body/fall_over); the thump, no arm roll. */
    public static void knockDown(ServerPlayer player) {
        if (!player.isAlive() || player.isPassenger()) return;
        boolean mirror = player.getRandom().nextBoolean();
        play(player, new Falling(player.server.getTickCount(), FALL_TICKS, mirror, 0, false), FALL_OVER);
        thump(player);
    }

    /** Lightning injury: up from lying on the front (body/prone_get_up); no moving or using things meanwhile. */
    public static void getUp(ServerPlayer player) {
        if (!player.isAlive() || player.isPassenger()) return;
        play(player, new Falling(player.server.getTickCount(), GET_UP_TICKS, false, 0, false), GET_UP);
    }

    /**
     * Injury knockdown: down away from the hit and up again (KNOCK_FRONT / KNOCK_BACK / KNOCK_SIDE, mirror = onto the
     * right side), or the collapse onto the front (COLLAPSE); the body's thump when it meets the ground.
     */
    public static void knock(ServerPlayer player, byte kind, boolean mirror) {
        if (!player.isAlive() || player.isPassenger()) return;
        int length, thump;
        switch (kind) {
            case KNOCK_FRONT -> { length = KNOCK_FRONT_TICKS; thump = KNOCK_FRONT_THUMP; }
            case KNOCK_BACK -> { length = KNOCK_BACK_TICKS; thump = KNOCK_BACK_THUMP; }
            case KNOCK_SIDE -> { length = KNOCK_SIDE_TICKS; thump = KNOCK_SIDE_THUMP; }
            case COLLAPSE -> { length = COLLAPSE_TICKS; thump = COLLAPSE_THUMP; }
            default -> throw new IllegalArgumentException("not a knockdown: " + kind);
        }
        play(player, new Falling(player.server.getTickCount(), length, mirror, 0, false, thump), kind);
    }

    /**
     * A living ragdoll (survivor/LiveRagdolls): the player counts as falling over (no using, hitting or sprinting) until
     * {@link #ragdollGetUp} or {@link #ragdollEnd}. No packet: the ragdoll's own tell the clients.
     */
    public static void ragdoll(ServerPlayer player) {
        FALLING.put(player.getUUID(), new Falling(player.server.getTickCount(), Integer.MAX_VALUE, false, 0, false));
        player.stopUsingItem();
        player.setSprinting(false);
    }

    /** The ragdoll came to rest: the knockdown {@code kind}'s get-up from {@code fromTick} ticks into it, no thump. */
    public static void ragdollGetUp(ServerPlayer player, byte kind, int fromTick) {
        int length = kind == KNOCK_BACK ? KNOCK_BACK_TICKS : KNOCK_FRONT_TICKS;
        FALLING.put(player.getUUID(), new Falling(player.server.getTickCount() - fromTick, length, false, 0, false));
    }

    /** The ragdoll ended without a get-up (it stays down collapsed, or it was dropped). */
    public static void ragdollEnd(ServerPlayer player) {
        FALLING.remove(player.getUUID());
    }

    private static void play(ServerPlayer player, Falling f, byte kind) {
        FALLING.put(player.getUUID(), f);
        player.stopUsingItem();
        player.setSprinting(false);
        var packet = new HealthPackets.Landing(player.getId(), kind, f.mirror);
        AflNetwork.skeletonTo(player, packet);
        AflNetwork.skeletonToTracking(player, packet);
    }

    /** Section 3: the chance this landing falls over (a leg it broke aside). */
    static double fallOverChance(ServerPlayer player, double healthLost, boolean legBroken) {
        var o = HealthConfig.get().fracture.fallOver;
        double p = Fractures.chance(o.chance, healthLost);
        if (!(p > 0)) return 0;
        if (legBroken) p *= o.brokenLeg;
        if (PlayerWeightRuntime.state(player).tier() != EncumbranceState.Tier.LIGHT) p += o.load;
        Vec3 last = LAST.get(player.getUUID());
        if (last != null && Math.hypot(player.getX() - last.x, player.getZ() - last.z) >= o.speedFrom) p += o.speed;
        if (!footing(player)) p += o.edge;
        if (player.isShiftKeyDown()) p *= o.sneak;
        return Mth.clamp(p, 0, 1);
    }

    /** Whether something stands under each corner of the feet (false on an edge or a slope's low side). */
    private static boolean footing(ServerPlayer player) {
        AABB box = player.getBoundingBox();
        double y = box.minY;
        for (double x : new double[]{box.minX + CORNER_IN, box.maxX - CORNER_IN})
            for (double z : new double[]{box.minZ + CORNER_IN, box.maxZ - CORNER_IN}) {
                AABB under = new AABB(x - 0.01, y - CORNER_DEPTH, z - 0.01, x + 0.01, y - 1.0E-4, z + 0.01);
                if (!player.level().getBlockCollisions(player, under).iterator().hasNext()) return false;
            }
        return true;
    }

    /** Whether this player is falling over now (the server's side; the client's is FallLandingClient). */
    public static boolean fallingOver(Player player) {
        return player instanceof ServerPlayer && FALLING.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        LAST.put(id, player.position());
        Falling f = FALLING.get(id);
        if (f == null) return;
        int t = player.server.getTickCount() - f.start;
        if (!player.isAlive() || t >= f.length) {
            FALLING.remove(id);
            return;
        }
        if (f.handHits && t >= HAND_HITS && !f.handDown) {
            f.handDown = true;
            handHits(player, f);
        }
        if (t == f.thumpAt) thump(player);
        if (player.isUsingItem()) player.stopUsingItem();
        if (player.isSprinting()) player.setSprinting(false);
    }

    /** The hand meets the ground: the body's thump, and the arm under it may break (arm_fall). */
    private static void handHits(ServerPlayer player, Falling f) {
        thump(player);
        Fractures.roll(player, f.mirror ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM,
                Fractures.chance(HealthConfig.get().fracture.armFall, f.healthLost));
    }

    /** The body's thump (a heavy landing's noise). */
    static void thump(ServerPlayer player) {
        RangedSound.play(player.serverLevel(), player.position(), AflSounds.BODY_FALL.get(), SoundSource.PLAYERS, THUMP_RADIUS,
                1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        FALLING.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        FALLING.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    // ---- nothing in the hands while down ----

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (fallingOver(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (fallingOver(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void useEntity(PlayerInteractEvent.EntityInteract event) {
        if (fallingOver(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (fallingOver(event.getEntity())) {
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (fallingOver(event.getEntity())) event.setCanceled(true);
    }
}
