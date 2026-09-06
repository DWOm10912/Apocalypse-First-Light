package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InvestigateNoiseGoal;
import com.antaurora.apofirstlight.infected.breach.InfectedBreachAuthorization;
import com.antaurora.apofirstlight.infected.breach.InfectedBreachGoal;
import com.antaurora.apofirstlight.infected.breach.InfectedBreakerClaims;
import com.antaurora.apofirstlight.infected.breach.InfectedEntrySeekingGoal;
import com.antaurora.apofirstlight.infected.breach.InfectedEntrySeekingSystem;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/** Isolated P0 regression and load-shape checks; excluded from the published JAR. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfectedAiPerformanceGameTests {
    private static final int STRESS_WINDOW_TICKS = 100;

    private InfectedAiPerformanceGameTests() {
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void hardHurtDoesNotSpawnReinforcements(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty oldDifficulty = level.getServer().getWorldData().getDifficulty();
        boolean oldMobSpawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        level.getServer().getWorldData().setDifficulty(Difficulty.HARD);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        SummonAidProbe probe = new SummonAidProbe();
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            List<Zombie> zombies = spawnZombies(helper, 10, true);
            zombies.forEach(zombie -> zombie.setInvulnerable(false));
            int before = countAflInfected(level);
            Player attacker = helper.makeMockPlayer();
            for (int pass = 0; pass < 4; pass++) {
                for (Zombie zombie : zombies) {
                    AttributeInstance chance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                    helper.assertTrue(chance != null, "Zombie reinforcement attribute missing");
                    chance.setBaseValue(1.0D);
                    zombie.invulnerableTime = 0;
                    zombie.hurt(level.damageSources().playerAttack(attacker), 0.01F);
                    helper.assertTrue(chance.getValue() == 0.0D,
                            "Hurt path left non-zero reinforcement chance: " + chance.getValue());
                }
            }
            int after = countAflInfected(level);
            helper.assertTrue(after == before,
                    "HARD hurt changed infected count " + before + " -> " + after);
            helper.assertTrue(probe.events == 40 && probe.denied == 40,
                    "Expected 40 denied SummonAid events, got events=" + probe.events + " denied=" + probe.denied);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(oldMobSpawning, level.getServer());
            level.getServer().getWorldData().setDifficulty(oldDifficulty);
        }
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void allZombieSubtypesHaveZeroEffectiveChance(GameTestHelper helper) {
        List<EntityType<? extends Zombie>> types = List.of(EntityType.ZOMBIE, EntityType.HUSK,
                EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN);
        for (EntityType<? extends Zombie> type : types) {
            Zombie zombie = type.create(helper.getLevel());
            helper.assertTrue(zombie != null, "Could not create " + type);
            zombie.moveTo(helper.absoluteVec(new Vec3(2.0, 3.0, 2.0)));
            helper.getLevel().addFreshEntity(zombie);
            AttributeInstance chance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
            helper.assertTrue(chance != null && chance.getValue() == 0.0D,
                    type + " effective reinforcement chance was " + (chance == null ? "missing" : chance.getValue()));
            helper.assertTrue(chance.getModifiers().isEmpty(), type + " retained reinforcement modifiers");
        }
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void sameBlockAllowsOneBreakerAndReleases(GameTestHelper helper) {
        BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(obstacle, Blocks.GLASS.defaultBlockState(), 3);
        List<Zombie> zombies = spawnZombies(helper, 8, true);
        int acquired = 0;
        for (Zombie zombie : zombies) {
            if (InfectedBreakerClaims.tryClaim(zombie, obstacle)) {
                acquired++;
            }
        }
        helper.assertTrue(acquired == 1, "Same obstacle acquired by " + acquired + " breakers");
        helper.assertTrue(InfectedBreakerClaims.claimCountAt(helper.getLevel(), obstacle) == 1,
                "Active claim count was not one");
        zombies.get(0).discard();
        helper.assertTrue(InfectedBreakerClaims.claimCountAt(helper.getLevel(), obstacle) == 0,
                "Discarded owner did not release through stale-owner cleanup");
        helper.assertTrue(InfectedBreakerClaims.tryClaim(zombies.get(1), obstacle),
                "Another zombie could not acquire the released obstacle");
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 140)
    public static void twentyInfectedStress(GameTestHelper helper) {
        runStaggeredScanStress(helper, 20);
    }

    @GameTest(template = "network_empty", timeoutTicks = 140)
    public static void fortyInfectedStress(GameTestHelper helper) {
        runStaggeredScanStress(helper, 40);
    }

    @GameTest(template = "network_empty", timeoutTicks = 140)
    public static void eightyInfectedStress(GameTestHelper helper) {
        runStaggeredScanStress(helper, 80);
    }

    @GameTest(template = "network_empty", timeoutTicks = 120)
    public static void breakableObstacleStillBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(obstacle, Blocks.GLASS.defaultBlockState(), 3);
        Zombie zombie = spawnZombies(helper, 1, true).get(0);
        Vec3 zombiePosition = helper.absoluteVec(new Vec3(1.5, 2.0, 3.5));
        Vec3 noisePosition = helper.absoluteVec(new Vec3(5.5, 2.0, 3.5));
        zombie.moveTo(zombiePosition);
        authorizeNoise(level, zombie, noisePosition);
        InfectedBreachGoal goal = new InfectedBreachGoal(zombie);
        boolean[] started = {false};
        for (int tick = 1; tick <= 80; tick++) {
            helper.runAtTickTime(tick, () -> {
                authorizeNoise(level, zombie, noisePosition);
                if (!started[0] && goal.canUse()) {
                    started[0] = true;
                    goal.start();
                }
                if (started[0] && goal.canContinueToUse()) {
                    goal.tick();
                }
            });
        }
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(started[0], "Breach goal never acquired the approved glass obstacle; scans="
                    + goal.diagnosticScanCount());
            helper.assertTrue(level.getBlockState(obstacle).isAir(), "Approved glass obstacle was not broken");
            helper.succeed();
        });
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void duplicateNoiseRefreshDoesNotBuildPaths(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = spawnZombies(helper, 1, true).get(0);
        Vec3 source = helper.absoluteVec(new Vec3(5.0, 3.0, 5.0));
        zombie.moveTo(helper.absoluteVec(new Vec3(2.0, 3.0, 5.0)));
        long firstTime = level.getGameTime();
        int replaced = 0;
        int duplicate = 0;
        for (int shot = 0; shot < 20; shot++) {
            InfectedHearingState.HearResult result = InfectedHearingState.hear(
                    zombie, source, firstTime + shot, NoiseType.GUNSHOT.name());
            if (result.targetReplaced()) replaced++;
            if (result.duplicateRefreshSuppressed()) duplicate++;
        }
        helper.assertTrue(InfectedHearingState.isValid(zombie), "Zombie did not retain the noise target");
        helper.assertTrue(InfectedHearingState.heardGameTime(zombie) == firstTime,
                "Duplicate noise replaced the stable target revision");
        helper.assertTrue(InfectedHearingState.lastEventGameTime(zombie) == firstTime + 19,
                "Duplicate noise did not refresh the event timestamp");
        helper.assertTrue(replaced == 1 && duplicate == 19,
                "Duplicate noise results were replaced=" + replaced + " duplicate=" + duplicate);
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 140)
    public static void unreachableNoiseUsesFailedPathCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = spawnZombies(helper, 1, true).get(0);
        Vec3 start = helper.absoluteVec(new Vec3(2.0, 3.0, 2.0));
        zombie.moveTo(start);
        Vec3 unreachable = start.add(200.0, 0.0, 0.0);
        InfectedHearingState.hear(zombie, unreachable, level.getGameTime(), "EXPLOSION");
        InvestigateNoiseGoal goal = new InvestigateNoiseGoal(zombie);
        goal.start();
        for (int tick = 1; tick <= STRESS_WINDOW_TICKS; tick++) {
            helper.runAtTickTime(tick, goal::tick);
        }
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(goal.diagnosticPathAttemptCount() > 0L
                            && goal.diagnosticPathAttemptCount() <= 4L,
                    "Unreachable target retried too often: " + goal.diagnosticPathAttemptCount());
            helper.assertTrue(goal.diagnosticFailedPathCount() == goal.diagnosticPathAttemptCount(),
                    "Unreachable attempts were not recorded as failed retries: attempts="
                            + goal.diagnosticPathAttemptCount() + " failed=" + goal.diagnosticFailedPathCount());
            helper.succeed();
        });
    }

    private static void runStaggeredScanStress(GameTestHelper helper, int count) {
        ServerLevel level = helper.getLevel();
        BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(obstacle, Blocks.GLASS.defaultBlockState(), 3);
        Vec3 zombiePosition = helper.absoluteVec(new Vec3(1.5, 2.0, 3.5));
        Vec3 noisePosition = helper.absoluteVec(new Vec3(5.5, 2.0, 3.5));
        List<InfectedBreachGoal> goals = new ArrayList<>();
        List<Zombie> zombies = spawnZombies(helper, count, true);
        for (Zombie zombie : zombies) {
            zombie.moveTo(zombiePosition);
            authorizeNoise(level, zombie, noisePosition);
            goals.add(new InfectedBreachGoal(zombie));
        }
        for (int tick = 1; tick <= STRESS_WINDOW_TICKS; tick++) {
            helper.runAtTickTime(tick, () -> {
                for (int index = 0; index < goals.size(); index++) {
                    InfectedBreachGoal goal = goals.get(index);
                    authorizeNoise(level, zombies.get(index), noisePosition);
                    goal.canUse();
                }
            });
        }
        helper.runAtTickTime(110, () -> {
            long scanCount = goals.stream().mapToLong(InfectedBreachGoal::diagnosticScanCount).sum();
            long upperBound = (long) count * 11L;
            helper.assertTrue(scanCount > 0L && scanCount <= upperBound,
                    count + " infected scans exceeded cooldown bound: " + scanCount);
            helper.assertTrue(InfectedBreakerClaims.claimCountAt(level, obstacle) <= 1,
                    count + " infected created multiple same-block claims");
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL AI STRESS] infected={} ticks={} scans={} pathAttempts={} activeClaims={} failedRetries={} reinforcements={}",
                    count, STRESS_WINDOW_TICKS, scanCount, 0,
                    InfectedBreakerClaims.claimCountAt(level, obstacle), 0, 0);
            helper.succeed();
        });
    }

    private static List<Zombie> spawnZombies(GameTestHelper helper, int count, boolean noAi) {
        List<Zombie> zombies = new ArrayList<>();
        Vec3 position = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        for (int index = 0; index < count; index++) {
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(zombie != null, "Could not create zombie " + index);
            zombie.moveTo(position);
            zombie.setNoAi(noAi);
            zombie.setInvulnerable(true);
            zombie.setPersistenceRequired();
            helper.getLevel().addFreshEntity(zombie);
            zombies.add(zombie);
        }
        return zombies;
    }

    private static void authorizeNoise(ServerLevel level, Zombie zombie, Vec3 position) {
        long now = level.getGameTime();
        InfectedHearingState.hear(zombie, position, now, NoiseType.EXPLOSION.name());
        InfectedBreachAuthorization.updateFromHeardNoise(zombie,
                new NoiseEvent(null, position, NoiseType.EXPLOSION, now, null, 12.0),
                12.0, InfectedHearingState.heardGameTime(zombie));
    }

    private static int countAflInfected(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getType() == EntityType.ZOMBIE) {
                count++;
            }
        }
        return count;
    }

    public static final class SummonAidProbe {
        private int events;
        private int denied;

        @SubscribeEvent(priority = EventPriority.MONITOR)
        public void onSummonAid(ZombieEvent.SummonAidEvent event) {
            events++;
            if (event.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY) {
                denied++;
            }
        }
    }
}
