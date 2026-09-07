package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ExplosionTinnitusEnvelope;
import com.antaurora.apofirstlight.explosion.ExplosionTinnitusProfile;
import com.antaurora.apofirstlight.noise.ExplosionNoiseProfile;
import com.antaurora.apofirstlight.tinnitus.GunshotExposureAccumulator;
import com.antaurora.apofirstlight.tinnitus.GunshotExposureTracker;
import com.antaurora.apofirstlight.tinnitus.GunshotTinnitusProfile;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Headless deterministic checks; these DEV-only tests do not claim audible or visual acceptance. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GunshotTinnitusGameTests {
    private static final double EPSILON = 1.0E-9;

    private GunshotTinnitusGameTests() {
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void profileAccumulationAndDecay(GameTestHelper helper) {
        assertClose(helper, GunshotTinnitusProfile.loudness(64.0), 0.0, "M1911 loudness");
        assertClose(helper, GunshotTinnitusProfile.loudness(96.0), 1.0 / 3.0, "AKM loudness");
        assertClose(helper, GunshotTinnitusProfile.loudness(160.0), 1.0, "M107 loudness");
        helper.assertTrue(GunshotTinnitusProfile.shotExposure(64.0, 0.0) == 0.0,
                "M1911 single shot must not add exposure");

        GunshotExposureAccumulator umpBurst = new GunshotExposureAccumulator();
        for (int shot = 0; shot < 10; shot++) {
            helper.assertTrue(umpBurst.recordShot(GunshotTinnitusProfile.shotExposure(72.0, 0.0), shot * 2L)
                            == 0.0F,
                    "UMP45 triggered too easily within ten close shots");
        }

        double akShot = GunshotTinnitusProfile.shotExposure(96.0, 0.0);
        helper.assertTrue(akShot < GunshotTinnitusProfile.LIGHT_THRESHOLD,
                "AKM single shot crossed the light threshold");
        GunshotExposureAccumulator akBurst = new GunshotExposureAccumulator();
        helper.assertTrue(akBurst.recordShot(akShot, 0L) == 0.0F, "AKM first shot triggered");
        float akImpulse = akBurst.recordShot(akShot, 2L);
        helper.assertTrue(akImpulse >= GunshotTinnitusProfile.LIGHT_THRESHOLD,
                "AKM close burst did not accumulate into tinnitus");

        GunshotExposureAccumulator spasBurst = new GunshotExposureAccumulator();
        double spasShot = GunshotTinnitusProfile.shotExposure(104.0, 0.0);
        helper.assertTrue(spasBurst.recordShot(spasShot, 0L) == 0.0F
                        && spasBurst.recordShot(spasShot, 2L) > 0.0F,
                "SPAS-12 close multi-shot accumulation failed");

        GunshotExposureAccumulator karBurst = new GunshotExposureAccumulator();
        double karShot = GunshotTinnitusProfile.shotExposure(112.0, 0.0);
        helper.assertTrue(karBurst.recordShot(karShot, 0L) == 0.0F
                        && karBurst.recordShot(karShot, 4L) > 0.0F,
                "Kar98 close two-shot accumulation failed");

        GunshotExposureAccumulator scarBurst = new GunshotExposureAccumulator();
        helper.assertTrue(scarBurst.recordShot(karShot, 0L) == 0.0F
                        && scarBurst.recordShot(karShot, 2L) > 0.0F,
                "SCAR-H close burst no longer accumulates into tinnitus");

        GunshotExposureAccumulator deagleRepeat = new GunshotExposureAccumulator();
        double deagleShot = GunshotTinnitusProfile.shotExposure(88.0, 0.0);
        helper.assertTrue(deagleRepeat.recordShot(deagleShot, 0L) == 0.0F
                        && deagleRepeat.recordShot(deagleShot, 2L) == 0.0F
                        && deagleRepeat.recordShot(deagleShot, 4L) >= GunshotTinnitusProfile.LIGHT_THRESHOLD,
                "Deagle rapid repeat no longer accumulates into tinnitus");

        GunshotExposureAccumulator awmShots = new GunshotExposureAccumulator();
        double awmShot = GunshotTinnitusProfile.shotExposure(128.0, 0.0);
        float awmSingle = awmShots.recordShot(awmShot, 0L);
        float awmSecond = awmShots.recordShot(awmShot, 6L);
        helper.assertTrue(awmSingle >= GunshotTinnitusProfile.LIGHT_THRESHOLD,
                "AWM single shot did not trigger light tinnitus");
        helper.assertTrue(awmSecond >= GunshotTinnitusProfile.MEDIUM_THRESHOLD && awmSecond > awmSingle,
                "AWM second shot did not strengthen tinnitus");

        GunshotExposureAccumulator cooldown = new GunshotExposureAccumulator();
        double m107Shot = GunshotTinnitusProfile.shotExposure(160.0, 0.0);
        helper.assertTrue(cooldown.recordShot(m107Shot, 0L) == 0.75F,
                "M107 single-shot severity changed");
        helper.assertTrue(cooldown.recordShot(m107Shot, 1L) == 0.0F,
                "Repeat impulse bypassed the six-tick cooldown");
        helper.assertTrue(cooldown.recordShot(m107Shot, 6L) == 1.0F,
                "Accumulated M107 exposure did not strengthen after cooldown");
        helper.assertTrue(cooldown.recordShot(m107Shot, 12L) == 0.0F,
                "Unchanged severity sent another packet before the extension interval");
        helper.assertTrue(cooldown.recordShot(m107Shot, 26L) == 1.0F,
                "Sustained fire did not send the twenty-tick extension update");

        GunshotExposureAccumulator decay = new GunshotExposureAccumulator();
        decay.recordShot(0.30, 0L);
        assertClose(helper, decay.exposureAt(125L), 0.0, "125-tick exposure decay");
        helper.assertTrue(decay.recordShot(akShot, 126L) == 0.0F,
                "A new AKM single shot inherited stale exposure");
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void distanceAndNearbyListeners(GameTestHelper helper) throws Exception {
        double previous = Double.POSITIVE_INFINITY;
        for (int distance = 0; distance <= 16; distance++) {
            double current = GunshotTinnitusProfile.shotExposure(160.0, distance);
            helper.assertTrue(current <= previous, "M107 exposure increased at distance " + distance);
            previous = current;
        }
        helper.assertTrue(GunshotTinnitusProfile.shotExposure(160.0, 0.0) == 0.75,
                "M107 shooter single shot must be obvious");
        helper.assertTrue(GunshotTinnitusProfile.shotExposure(160.0, 3.0)
                        >= GunshotTinnitusProfile.MEDIUM_THRESHOLD,
                "M107 three-block listener did not receive a clear impulse");
        helper.assertTrue(GunshotTinnitusProfile.shotExposure(160.0, 12.0)
                        < GunshotTinnitusProfile.shotExposure(160.0, 3.0),
                "M107 twelve-block falloff is not weaker");
        helper.assertTrue(GunshotTinnitusProfile.shotExposure(160.0, 16.0) == 0.0
                        && GunshotTinnitusProfile.shotExposure(160.0, 20.0) == 0.0,
                "Gunshot exposure escaped the sixteen-block hard cap");

        ServerLevel level = helper.getLevel();
        Vec3 source = helper.absoluteVec(new Vec3(3.0, 3.0, 3.0));
        List<ServerPlayer> players = new ArrayList<>();
        ServerPlayer shooter = addPlayer(players, level, source, 0.0);
        ServerPlayer atThree = addPlayer(players, level, source, 3.0);
        ServerPlayer atEight = addPlayer(players, level, source, 8.0);
        ServerPlayer atFifteen = addPlayer(players, level, source, 15.0);
        ServerPlayer atTwenty = addPlayer(players, level, source, 20.0);
        ServerPlayer spectator = addSpectator(players, level, source, 1.0);

        int impulses = accumulateWithoutNetwork(level, shooter, source, 160.0, false, players);
        helper.assertTrue(impulses == 2,
                "M107 should impulse only shooter and near three-block listener, got " + impulses);
        Map<UUID, GunshotExposureAccumulator> exposure = exposureForLevel(level);
        long tick = level.getServer().getTickCount();
        double shooterExposure = exposure.get(shooter.getUUID()).exposureAt(tick);
        double threeExposure = exposure.get(atThree.getUUID()).exposureAt(tick);
        double eightExposure = exposure.get(atEight.getUUID()).exposureAt(tick);
        double fifteenExposure = exposure.get(atFifteen.getUUID()).exposureAt(tick);
        helper.assertTrue(shooterExposure > threeExposure && threeExposure > eightExposure
                        && eightExposure > fifteenExposure && fifteenExposure > 0.0,
                "Nearby stored exposure is not monotonic by distance");
        helper.assertTrue(!exposure.containsKey(atTwenty.getUUID()),
                "Twenty-block player was included in gunshot exposure");
        helper.assertTrue(!exposure.containsKey(spectator.getUUID()),
                "Spectator was included in gunshot exposure");
        helper.succeed();
    }


    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void sharedTimelineAndExplosionRegression(GameTestHelper helper) {
        helper.assertTrue(ExplosionNoiseProfile.radius(4.0F) == 192.0,
                "Vanilla TNT explosion noise no longer resolves to 192 blocks");
        helper.assertTrue(ExplosionTinnitusProfile.RANGE_MULTIPLIER == 3.0
                        && ExplosionTinnitusProfile.MIN_AUDIO_SEVERITY == 0.15F
                        && ExplosionTinnitusProfile.MIN_OVERLAY_SEVERITY == 0.08F
                        && ExplosionTinnitusProfile.MAX_PLAYBACK_TICKS == 200
                        && ExplosionTinnitusProfile.WEAK_EXTENSION_TICKS == 20,
                "Explosion tinnitus constants changed");
        assertClose(helper, ExplosionTinnitusProfile.severity(4.0F, 0.0), 1.0,
                "Explosion epicenter severity");
        helper.assertTrue(ExplosionTinnitusProfile.durationTicks(0.75F) == 130,
                "Shared tinnitus duration mapping changed");
        assertClose(helper, ExplosionTinnitusProfile.initialVolume(0.75F), 0.5625,
                "Shared tinnitus volume mapping");

        ExplosionTinnitusEnvelope timeline = new ExplosionTinnitusEnvelope();
        helper.assertTrue(timeline.trigger(0.75F) == ExplosionTinnitusEnvelope.TriggerResult.START,
                "First tinnitus impulse did not start the shared timeline");
        helper.assertTrue(timeline.trigger(0.40F) == ExplosionTinnitusEnvelope.TriggerResult.EXTEND
                        && timeline.severity() == 0.75F,
                "Weaker repeated impulse restarted or reduced the shared timeline");
        helper.assertTrue(timeline.trigger(0.90F) == ExplosionTinnitusEnvelope.TriggerResult.STRENGTHEN
                        && timeline.severity() == 0.90F,
                "Stronger impulse restarted instead of strengthening the shared episode");
        int starts = 1;
        for (float impulse : new float[]{0.30F, 1.0F, 0.50F, 0.95F}) {
            if (timeline.trigger(impulse) == ExplosionTinnitusEnvelope.TriggerResult.START) starts++;
        }
        helper.assertTrue(starts == 1, "Gunshot/explosion episode produced multiple sound starts");
        while (timeline.active()) timeline.tick();
        helper.assertTrue(timeline.trigger(0.75F) == ExplosionTinnitusEnvelope.TriggerResult.START,
                "Finished tinnitus episode did not re-arm a new sound start");
        helper.succeed();
    }

    private static ServerPlayer addPlayer(List<ServerPlayer> players, ServerLevel level,
                                          Vec3 source, double xOffset) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(id, "afl-tinnitus-" + players.size()));
        player.moveTo(source.x + xOffset, source.y, source.z, 0.0F, 0.0F);
        players.add(player);
        return player;
    }

    private static ServerPlayer addSpectator(List<ServerPlayer> players, ServerLevel level,
                                             Vec3 source, double xOffset) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(id, "afl-tinnitus-spectator")) {
            @Override
            public boolean isSpectator() {
                return true;
            }
        };
        player.moveTo(source.x + xOffset, source.y, source.z, 0.0F, 0.0F);
        players.add(player);
        return player;
    }





    @SuppressWarnings("unchecked")
    private static Map<UUID, GunshotExposureAccumulator> exposureForLevel(ServerLevel level)
            throws Exception {
        Field field = GunshotExposureTracker.class.getDeclaredField("EXPOSURE_BY_LEVEL");
        field.setAccessible(true);
        Map<ServerLevel, Map<UUID, GunshotExposureAccumulator>> allExposure =
                (Map<ServerLevel, Map<UUID, GunshotExposureAccumulator>>) field.get(null);
        Map<UUID, GunshotExposureAccumulator> exposure = allExposure.get(level);
        if (exposure == null) {
            throw new IllegalStateException("No gunshot exposure was recorded for the GameTest level");
        }
        return exposure;
    }

    private static int accumulateWithoutNetwork(ServerLevel level, ServerPlayer shooter,
                                                Vec3 source, double effectiveRadius,
                                                boolean trueSuppressor,
                                                Iterable<ServerPlayer> listeners) throws Exception {
        Method method = GunshotExposureTracker.class.getDeclaredMethod("accumulateListeners",
                ServerLevel.class, ServerPlayer.class, Vec3.class, double.class,
                boolean.class, Iterable.class, BiConsumer.class);
        method.setAccessible(true);
        BiConsumer<ServerPlayer, Float> noNetwork = (player, severity) -> { };
        return (int) method.invoke(null, level, shooter, source, effectiveRadius,
                trueSuppressor, listeners, noNetwork);
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON,
                label + " expected " + expected + " but got " + actual);
    }
}
