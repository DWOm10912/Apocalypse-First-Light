package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import com.antaurora.apofirstlight.noise.ExplosionNoiseProfile;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;


/** Isolated server checks; this class is excluded from the published JAR. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoiseSystemV2GameTests {

    private NoiseSystemV2GameTests() {
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void internalGunshotHearingRadius(GameTestHelper helper) {
        Vec3 source = helper.absoluteVec(new Vec3(2.0, 3.0, 2.0));
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Could not create listener");
        zombie.setInvulnerable(true);
        zombie.setNoAi(true);
        zombie.moveTo(source.x + 12.0, source.y, source.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(zombie);
        NoiseSystem.emit(new NoiseEvent(null, source, NoiseType.GUNSHOT,
                helper.getLevel().getGameTime(), null, 10.0), helper.getLevel());
        helper.assertTrue(!InfectedHearingState.isValid(zombie), "Out-of-range listener heard event");
        NoiseSystem.emit(new NoiseEvent(null, source, NoiseType.GUNSHOT,
                helper.getLevel().getGameTime(), null, 96.0), helper.getLevel());
        helper.assertTrue(InfectedHearingState.isValid(zombie), "Internal noise seam did not reach listener");
        helper.succeed();
    }




    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void explosionRadiusFormula(GameTestHelper helper) {
        assertExplosionRadius(helper, 2.0F, 144.0);
        assertExplosionRadius(helper, 2.5F, 160.0);
        assertExplosionRadius(helper, 3.0F, 168.0);
        assertExplosionRadius(helper, 4.0F, 192.0);
        assertExplosionRadius(helper, 5.0F, 216.0);
        assertExplosionRadius(helper, 6.0F, 240.0);
        assertExplosionRadius(helper, 100.0F, 256.0);
        assertExplosionRadius(helper, -100.0F, 96.0);
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void explosionUsesCenterAndSingleDetonate(GameTestHelper helper) {
        Vec3 firstCenter = helper.absoluteVec(new Vec3(3.0, 3.0, 3.0));
        Vec3 secondCenter = helper.absoluteVec(new Vec3(5.0, 3.0, 3.0));
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Could not create listener zombie");
        zombie.setInvulnerable(true);
        zombie.setNoAi(true);
        zombie.moveTo(firstCenter.x + 2.0, firstCenter.y, firstCenter.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(zombie);

        DetonateProbe probe = new DetonateProbe();
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            helper.getLevel().explode(null, firstCenter.x, firstCenter.y, firstCenter.z, 0.25F,
                    Level.ExplosionInteraction.NONE);
            helper.assertTrue(probe.detonations == 1, "One explosion did not produce exactly one Detonate event");
            helper.assertTrue(firstCenter.equals(InfectedHearingState.lastHeardPosition(zombie)),
                    "Infected did not investigate the first explosion center");
            helper.assertTrue("EXPLOSION".equals(zombie.getPersistentData()
                            .getCompound("apocalypse_firstlight_hearing").getString("type")),
                    "Hearing state was not tagged as EXPLOSION");

            helper.getLevel().explode(null, secondCenter.x, secondCenter.y, secondCenter.z, 0.25F,
                    Level.ExplosionInteraction.NONE);
            helper.assertTrue(probe.detonations == 2, "Two explosions did not produce two Detonate events");
            helper.assertTrue(secondCenter.equals(InfectedHearingState.lastHeardPosition(zombie)),
                    "Infected target did not move to the second explosion center");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void vanillaTntUsesLiveStrength(GameTestHelper helper) {
        Vec3 center = helper.absoluteVec(new Vec3(3.0, 3.0, 3.0));
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Could not create TNT listener zombie");
        zombie.setInvulnerable(true);
        zombie.setNoAi(true);
        zombie.moveTo(center.x + 8.0, center.y, center.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(zombie);

        DetonateProbe probe = new DetonateProbe();
        MinecraftForge.EVENT_BUS.register(probe);
        PrimedTnt tnt = new PrimedTnt(helper.getLevel(), center.x, center.y, center.z, null);
        tnt.setFuse(1);
        helper.getLevel().addFreshEntity(tnt);

        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(probe.detonations == 1,
                        "Vanilla TNT did not produce exactly one Detonate event");
                helper.assertTrue(probe.lastStrength == 4.0F,
                        "Vanilla TNT live strength was " + probe.lastStrength + " instead of 4.0");
                helper.assertTrue(ExplosionNoiseProfile.radius(probe.lastStrength) == 192.0,
                        "Vanilla TNT did not resolve to a 192-block noise radius");
                helper.assertTrue(probe.lastCenter != null && probe.lastCenter.distanceTo(center) < 1.0,
                        "Vanilla TNT explosion center moved unexpectedly far from its spawn position");
                helper.assertTrue(probe.lastCenter.equals(InfectedHearingState.lastHeardPosition(zombie)),
                        "Infected did not investigate the vanilla TNT explosion center");
                helper.succeed();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(probe);
            }
        });
    }





    private static void assertExplosionRadius(GameTestHelper helper, float strength, double expected) {
        helper.assertTrue(ExplosionNoiseProfile.radius(strength) == expected,
                "Explosion strength " + strength + " expected " + expected
                        + " but got " + ExplosionNoiseProfile.radius(strength));
    }



    public static final class DetonateProbe {
        private int detonations;
        private float lastStrength;
        private Vec3 lastCenter;

        @SubscribeEvent
        public void onDetonate(ExplosionEvent.Detonate event) {
            detonations++;
            lastStrength = ((ExplosionAccessor) event.getExplosion()).afl$getRadius();
            lastCenter = event.getExplosion().getPosition();
        }
    }
}
