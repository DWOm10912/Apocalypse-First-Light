package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.survivor.LiveRagdolls;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.VehicleImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Live ragdoll probe, the server half (dev only; src/dev/live-ragdoll-probe-server.init.gradle, 2026-10-22): with
 * {@code -Dafl.liveragdollserver=true} a dedicated server (or the client's own with {@code -Dafl.liveragdollprobe=sp}), once a player joined, stands it (survival, full health) on a stone
 * platform at y 200 looking south and has a T15 just behind it hit it through VehicleImpact's own hit at {@link #CLOSING} m/s, at
 * the {@link #HITS} ticks after it joined (the client half, LiveRagdollProbe in mp mode, takes its screenshots then); the last
 * hit at {@link #LOW_HEALTH} health, so it stays down collapsed. A dedicated server stops once the player left.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LiveRagdollServerProbe {
    private static final boolean ON = Boolean.getBoolean("afl.liveragdollserver") || "sp".equals(System.getProperty("afl.liveragdollprobe"));
    static final double CLOSING = 8.5;
    /** Ticks after the join: the platform, each hit (first person, third person, low health in first person). */
    static final int SETUP = 100;
    static final int[] HITS = {300, 640, 980};
    private static final float LOW_HEALTH = 50;
    private static ServerPlayer player;
    private static int ticks;
    private static BlockPos base;

    private LiveRagdollServerProbe() {
    }

    private static void log(String s) {
        ApocalypseFirstLight.LOGGER.info("[AFL LIVE RAGDOLL SERVER PROBE] {}", s);
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (ON && player == null && event.getEntity() instanceof ServerPlayer p) {
            player = p;
            ticks = 0;
            log("joined " + p.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END || player == null) return;
        ticks++;
        ServerLevel l = player.serverLevel();
        if (ticks == SETUP) {
            base = BlockPos.containing(player.getX(), 200, player.getZ());
            l.setDayTime(6000);
            l.setWeatherParameters(6000, 0, false, false);
            l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, l.getServer());
            for (int x = -20; x <= 20; x++)
                for (int z = -12; z <= 28; z++) {
                    l.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 4; y++) l.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            player.setGameMode(GameType.SURVIVAL);
            log("platform at " + base);
        }
        for (int i = 0; i < HITS.length; i++) {
            if (ticks == HITS[i] - 40) {
                player.teleportTo(l, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), 0, 10);
                player.setHealth(player.getMaxHealth());
            }
            if (ticks == HITS[i]) {
                if (i == HITS.length - 1) player.setHealth(LOW_HEALTH);
                hit(l, i);
            }
        }
        if (ticks % 20 == 0 && ticks > HITS[0])
            log(String.format("t%d: ragdoll %s, health %.1f, pose %s, at %.2f %.2f %.2f", ticks, LiveRagdolls.active(player), player.getHealth(),
                    player.getPose(), player.getX(), player.getY(), player.getZ()));
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ON || event.getEntity() != player) return;
        player = null;
        if (event.getEntity().getServer() != null && event.getEntity().getServer().isDedicatedServer()) {
            log("done");
            event.getEntity().getServer().halt(false);
        }
    }

    private static void hit(ServerLevel l, int i) {
        PickupTruckEntity truck = AflEntities.PICKUP_T15.get().create(l);
        if (truck == null) { log("no truck"); return; }
        truck.moveTo(player.getX(), player.getY(), player.getZ() - 4, 0, 0);
        l.addFreshEntity(truck);
        try {
            Method hit = VehicleImpact.class.getDeclaredMethod("hit", ServerLevel.class, PickupTruckEntity.class, LivingEntity.class,
                    double.class, double.class, double.class);
            hit.setAccessible(true);
            hit.invoke(null, l, truck, player, CLOSING, 0.0, 1.0);
            log(String.format("hit %d at %.1f m/s: health %.1f, ragdoll %s", i, CLOSING, player.getHealth(), LiveRagdolls.active(player)));
        } catch (Exception e) {
            log("hit failed " + e);
        }
        truck.discard();
    }
}
