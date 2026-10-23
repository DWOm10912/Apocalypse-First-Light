package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.vehicle.physics.VehiclePhysics;
import com.antaurora.apofirstlight.vehicle.physics.VehicleState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import com.antaurora.apofirstlight.health.HealthConfig;
import com.antaurora.apofirstlight.survivor.LiveRagdolls;
import com.antaurora.apofirstlight.survivor.SurvivorSetup;
import com.antaurora.apofirstlight.survivor.SurvivorSetupFlow;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * T15 impact, forward and backing up (user 2026-10-22 「倒车撞不到人」; docs/gameplay/injury_knockdown_v1_plan.md 9.7): a
 * mock driver in the seat (so the server runs no physics of its own), the truck fed each tick the way the driver's
 * Drive packets feed it (physics.apply + place, PickupTruckEntity#acceptDrive), straight at a standing zombie a metre
 * off the bumper, and a survival player (PvP on, past its setup screen) the same; when (and whether) it is hit, the health
 * it lost, the throw and whether the player was thrown down as a living ragdoll (from vehicle.knock_speed; slower a bump).
 */
@GameTestHolder("afl_truck_impact_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TruckImpactGameTests {
    private static final String NAMESPACE = "afl_truck_impact_tests";
    private static final int SX = 24, SY = 6, SZ = 24;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[" + SX + "," + SY + "," + SZ + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        level.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("truck_impact", NAMESPACE + ":integration", NAMESPACE + ":empty", 1200, 0L, true, TruckImpactGameTests::run));
    }

    /** dir +1 forward, -1 backing up; speed m/s along the truck; yaw of the truck. */
    private record Case(String name, int dir, double speed, float yaw, boolean player) {}

    private static final Case[] CASES = {
            new Case("zombie forward 4 m/s", 1, 4, 0, false), new Case("zombie reverse 4 m/s", -1, 4, 0, false),
            new Case("zombie reverse 2.5 m/s", -1, 2.5, 0, false), new Case("zombie reverse 4 m/s turned", -1, 4, 37, false),
            new Case("zombie reverse 6 m/s", -1, 6, 0, false), new Case("zombie forward 2.5 m/s", 1, 2.5, 0, false),
            new Case("player forward 4 m/s", 1, 4, 0, true), new Case("player reverse 4 m/s", -1, 4, 0, true),
            new Case("player reverse 2.5 m/s", -1, 2.5, 0, true), new Case("player reverse 1.8 m/s", -1, 1.8, 0, true),
            new Case("zombie reverse 1.0 m/s (bump)", -1, 1.0, 0, false), new Case("player reverse 1.0 m/s (bump)", -1, 1.0, 0, true)};

    private static void run(GameTestHelper h) {
        StringBuilder log = new StringBuilder("[AFL TRUCK IMPACT TEST]\n");
        // a player driver hurts a player only with PvP on (ServerPlayer#hurt); the GameTest server leaves it off
        h.getLevel().getServer().setPvpAllowed(true);
        step(h, h.getLevel(), log, new int[]{0}, new boolean[]{true});
    }

    private static void step(GameTestHelper h, ServerLevel level, StringBuilder log, int[] index, boolean[] ok) {
        if (index[0] >= CASES.length) {
            log.append(ok[0] ? "[AFL TRUCK IMPACT TEST] PASS" : "[AFL TRUCK IMPACT TEST] FAIL");
            ApocalypseFirstLight.LOGGER.info(log.toString());
            h.succeed();
            return;
        }
        Case c = CASES[index[0]];
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) {
            for (int y = 2; y < SY; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(x, 1, z), Blocks.STONE.defaultBlockState());
        }
        double top = origin.getY() + 2;
        Vec3 at = new Vec3(origin.getX() + SX / 2.0, top + 0.3, origin.getZ() + SZ / 2.0);
        PickupTruckEntity truck = PickupTruckEntity.create(level, at, c.yaw);
        level.addFreshEntity(truck);
        Player driver = h.makeMockPlayer();
        LivingEntity zombie;
        if (c.player) {
            // a survival player (GameTestHelper#makeMockServerPlayerInLevel's is creative, which vehicles pass by)
            ServerPlayer p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "impact-" + index[0])) {
                @Override
                public boolean isSpectator() { return false; }

                @Override
                public boolean isCreative() { return false; }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, p);
            // past the new survivor's setup screen (SurvivorSetupFlow holds a choosing player out of harm's way)
            try {
                Method living = SurvivorSetupFlow.class.getDeclaredMethod("living", ServerPlayer.class);
                living.setAccessible(true);
                Object record = living.invoke(null, p);
                if (record != null) {
                    Field setup = record.getClass().getDeclaredField("setup");
                    setup.setAccessible(true);
                    setup.set(record, SurvivorSetup.DONE);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            zombie = p;
        } else {
            Zombie z = new Zombie(level);
            z.setNoAi(true);
            zombie = z;
        }
        // a new player is invulnerable for 60 ticks after it joined (ServerPlayer#spawnInvulnerableTime)
        h.runAfterDelay(c.player ? 90 : 30, () -> {
            invoke(truck, "sit", new Class<?>[]{Player.class, int.class}, driver, PickupTruckLayout.DRIVER);
            double f = c.dir > 0 ? 2.40 + 0.3 + 1.0 : -2.37 - 0.3 - 1.0;
            Vec3 spot = PickupTruckLayout.world(truck.position(), truck.getYRot(), f, 0, 0);
            zombie.moveTo(spot.x, top, spot.z, 0, 0);
            if (!c.player) level.addFreshEntity(zombie);
            if (zombie instanceof ServerPlayer sp) {
                float before = sp.getHealth();
                boolean took = sp.hurt(level.damageSources().generic(), 0.2F);
                log.append(String.format("  %s: generic hurt %s (%.2f -> %.2f), invulnerable %s, mode %s, choosing %s, alive %s, removed %s, in level %s%n", c.name, took, before,
                        sp.getHealth(), sp.isInvulnerable(), sp.gameMode.getGameModeForPlayer(), SurvivorSetupFlow.choosing(sp), sp.isAlive(), sp.isRemoved(),
                        level.getEntity(sp.getId()) == sp));
                sp.setHealth(before);
                sp.invulnerableTime = 0;
                sp.hurtTime = 0;
            }
            float health0 = zombie.getHealth();
            Vec3 ahead = PickupTruckLayout.world(truck.position(), truck.getYRot(), 1, 0, 0).subtract(truck.position()).normalize().scale(c.dir);
            int[] t = {0};
            Runnable[] drive = new Runnable[1];
            drive[0] = () -> {
                t[0]++;
                VehiclePhysics physics = (VehiclePhysics) get(truck, "physics");
                VehicleState s = physics.capture();
                double step = c.speed * 0.05;
                VehicleState next = new VehicleState(s.x() + ahead.x * step, s.y(), s.z() + ahead.z * step, s.qx(), s.qy(), s.qz(), s.qw(),
                        (float) (ahead.x * c.speed), 0, (float) (ahead.z * c.speed), 0, 0, 0, 0, s.travel(), s.spinRate(), false, s.drive());
                physics.apply(next);
                invoke(truck, "place", new Class<?>[0]);
                boolean hit = zombie.getHealth() < health0 || zombie.hurtTime > 0;
                if (hit || t[0] >= 50) {
                    double gap = PickupTruckLayout.local(truck.position(), truck.getYRot(), zombie.position())[0];
                    log.append(String.format("%s: driver %s; %s after %d ticks (%.2f m driven), health %.2f -> %.2f, thrown %.2f m/s (y %.2f), at f %+.2f%s%n",
                            c.name, truck.getControllingPassenger() == driver ? "seated" : "NOT seated", hit ? "HIT" : "no hit", t[0], t[0] * step,
                            health0, zombie.getHealth(), zombie.getDeltaMovement().horizontalDistance() * 20, zombie.getDeltaMovement().y * 20, gap,
                            zombie instanceof ServerPlayer p ? ", live ragdoll " + LiveRagdolls.active(p) : ""));
                    // a player is thrown down from vehicle.knock_speed, below it only bumped
                    boolean ragdoll = zombie instanceof ServerPlayer p && LiveRagdolls.active(p);
                    if (!hit || c.player && ragdoll != c.speed >= HealthConfig.get().vehicle.knockSpeed) ok[0] = false;
                    driver.stopRiding();
                    truck.discard();
                    if (zombie instanceof ServerPlayer p) level.getServer().getPlayerList().remove(p);
                    else zombie.discard();
                    index[0]++;
                    h.runAfterDelay(5, () -> step(h, level, log, index, ok));
                } else {
                    h.runAfterDelay(1, () -> drive[0].run());
                }
            };
            h.runAfterDelay(1, () -> drive[0].run());
        });
    }

    private static Object get(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Object o, String name, Class<?>[] types, Object... args) {
        try {
            Method m = o.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(o, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
