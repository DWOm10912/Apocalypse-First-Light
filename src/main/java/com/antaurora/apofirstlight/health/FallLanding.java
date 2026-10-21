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
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class FallLanding {
    public static final byte HARD = 0, FALL_OVER = 1;
    /** The actions' lengths and the hand meeting the ground (ticks; tools/medical-actions-v1a/author_fall_v1.cjs). */
    public static final int HARD_TICKS = 12, FALL_TICKS = 56, HAND_HITS = 9;
    /** How far the body's thump carries (blocks): a heavy landing's noise. */
    private static final double THUMP_RADIUS = MovementNoiseEvents.HEAVY_LANDING_RADIUS;
    /** How far in from the feet's corners the support is looked for, and how deep under them (blocks). */
    private static final double CORNER_IN = 0.05, CORNER_DEPTH = 0.1;

    private static final class Falling {
        final int start;
        final boolean mirror;
        final double healthLost;
        boolean handDown;

        Falling(int start, boolean mirror, double healthLost) {
            this.start = start;
            this.mirror = mirror;
            this.healthLost = healthLost;
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
        var c = HealthConfig.get().fracture;
        var random = player.getRandom();
        boolean legBefore = false;
        for (Fractures.View v : Fractures.views(player)) legBefore |= v.part().leg();
        boolean legBroke = Fractures.roll(player, BodyPart.randomLeg(random), Fractures.chance(c.legFall, healthLost));
        double chance = legBroke ? 1 : fallOverChance(player, healthLost, legBefore);
        boolean over = random.nextDouble() < chance;
        boolean mirror = random.nextBoolean();
        if (over) {
            FALLING.put(player.getUUID(), new Falling(player.server.getTickCount(), mirror, healthLost));
            player.stopUsingItem();
            player.setSprinting(false);
        }
        var packet = new HealthPackets.Landing(player.getId(), over ? FALL_OVER : HARD, mirror);
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
        if (!player.isAlive() || t >= FALL_TICKS) {
            FALLING.remove(id);
            return;
        }
        if (t >= HAND_HITS && !f.handDown) {
            f.handDown = true;
            handHits(player, f);
        }
        if (player.isUsingItem()) player.stopUsingItem();
        if (player.isSprinting()) player.setSprinting(false);
    }

    /** The hand meets the ground: the body's thump, and the arm under it may break (arm_fall). */
    private static void handHits(ServerPlayer player, Falling f) {
        RangedSound.play(player.serverLevel(), player.position(), AflSounds.BODY_FALL.get(), SoundSource.PLAYERS, THUMP_RADIUS,
                1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
        Fractures.roll(player, f.mirror ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM,
                Fractures.chance(HealthConfig.get().fracture.armFall, f.healthLost));
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
