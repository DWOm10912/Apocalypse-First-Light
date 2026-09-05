package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import com.antaurora.apofirstlight.noise.ExplosionNoiseProfile;
import com.antaurora.apofirstlight.noise.GunshotNoiseResolver;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/** Isolated server checks; this class is excluded from the published JAR. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoiseSystemV2GameTests {
    private static final Set<ResourceLocation> EXCLUDED = Set.of(
            id("db_long"), id("db_short"), id("lonetrail"), id("spr15hb"), id("timeless50"),
            id("deagle_golden"), id("qbz_95"), id("taurus943"), id("minigun")
    );

    private NoiseSystemV2GameTests() {
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void explicitGunRadiiAndFallback(GameTestHelper helper) throws Exception {
        Map<ResourceLocation, Double> explicit = explicitRadii();
        helper.assertTrue(explicit.size() == 45, "Expected 45 explicit gun radii, got " + explicit.size());
        helper.assertTrue(TimelessAPI.getAllCommonGunIndex().size() == 54, "Expected 54 runtime TaCZ guns");
        for (ResourceLocation gunId : explicit.keySet()) {
            helper.assertTrue(TimelessAPI.getCommonGunIndex(gunId).isPresent(), "Missing runtime gun " + gunId);
        }

        assertExplicit(helper, explicit, "m1911", 64.0);
        assertExplicit(helper, explicit, "ump45", 72.0);
        assertExplicit(helper, explicit, "p90", 80.0);
        assertExplicit(helper, explicit, "deagle", 88.0);
        assertExplicit(helper, explicit, "ak47", 96.0);
        assertExplicit(helper, explicit, "spas_12", 104.0);
        assertExplicit(helper, explicit, "kar98", 112.0);
        assertExplicit(helper, explicit, "ai_awp", 128.0);
        assertExplicit(helper, explicit, "m95", 160.0);
        assertExplicit(helper, explicit, "m107", 160.0);

        for (ResourceLocation gunId : EXCLUDED) {
            helper.assertTrue(!explicit.containsKey(gunId), "Excluded gun has explicit radius: " + gunId);
            ItemStack bareGun = GunItemBuilder.create().setId(gunId).build();
            double fallback = GunshotNoiseResolver.resolveRadius(bareGun, gunId);
            helper.assertTrue(Double.isFinite(fallback) && fallback > 0, "Invalid fallback for " + gunId);
        }
        helper.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void suppressorKeepsExplicitBase(GameTestHelper helper) {
        assertSuppressorReduction(helper, "m1911", 64.0);
        assertSuppressorReduction(helper, "ak47", 96.0);
        assertSuppressorReduction(helper, "m107", 160.0);
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

    private static void assertExplicit(GameTestHelper helper, Map<ResourceLocation, Double> explicit,
                                       String gunPath, double expected) {
        ResourceLocation gunId = id(gunPath);
        helper.assertTrue(Double.valueOf(expected).equals(explicit.get(gunId)),
                gunId + " explicit radius mismatch");
        ItemStack bareGun = GunItemBuilder.create().setId(gunId).build();
        helper.assertTrue(GunshotNoiseResolver.resolveRadius(bareGun, gunId) == expected,
                gunId + " did not resolve through its explicit radius");
    }

    private static void assertSuppressorReduction(GameTestHelper helper, String gunPath, double expectedBase) {
        ResourceLocation gunId = id(gunPath);
        ItemStack gunStack = GunItemBuilder.create().setId(gunId).build();
        IGun gun = IGun.getIGunOrNull(gunStack);
        helper.assertTrue(gun != null, "Missing IGun for " + gunId);
        helper.assertTrue(GunshotNoiseResolver.resolveRadius(gunStack, gunId) == expectedBase,
                gunId + " bare radius changed");

        for (var entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            if (entry.getValue().getType() != AttachmentType.MUZZLE
                    || entry.getValue().getData() == null
                    || !entry.getValue().getData().getModifier().containsKey("silence")) {
                continue;
            }
            ItemStack attachment = AttachmentItemBuilder.create().setId(entry.getKey()).build();
            if (!gun.allowAttachment(gunStack, attachment)) {
                continue;
            }
            gun.installAttachment(gunStack, attachment);
            double suppressed = GunshotNoiseResolver.resolveRadius(gunStack, gunId);
            helper.assertTrue(suppressed < expectedBase,
                    gunId + " compatible suppressor did not reduce radius: " + entry.getKey());
            return;
        }
        helper.fail("No compatible suppressor found for " + gunId);
    }

    private static void assertExplosionRadius(GameTestHelper helper, float strength, double expected) {
        helper.assertTrue(ExplosionNoiseProfile.radius(strength) == expected,
                "Explosion strength " + strength + " expected " + expected
                        + " but got " + ExplosionNoiseProfile.radius(strength));
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, Double> explicitRadii() throws Exception {
        Field field = GunshotNoiseResolver.class.getDeclaredField("REFERENCE_RADII");
        field.setAccessible(true);
        return (Map<ResourceLocation, Double>) field.get(null);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("tacz", path);
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
