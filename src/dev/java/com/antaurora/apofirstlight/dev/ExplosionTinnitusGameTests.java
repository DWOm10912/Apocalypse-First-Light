package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.explosion.ExplosionTinnitusProfile;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.tacz.guns.util.block.ProjectileExplosion;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Headless checks only: do not treat these as audible/visual client acceptance. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExplosionTinnitusGameTests {
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void tinnitusProfileAndPacket(GameTestHelper h) {
        h.assertTrue(ExplosionTinnitusProfile.severity(4, 0) == 1, "Epicenter severity");
        h.assertTrue(ExplosionTinnitusProfile.severity(4, 12) == 0, "Outside radius");
        h.assertTrue(!ExplosionTinnitusProfile.shouldTrigger(ExplosionTinnitusProfile.severity(4, 8)),
                "Below-threshold explosion triggered");
        h.assertTrue(ExplosionTinnitusProfile.shouldTriggerOverlay(ExplosionTinnitusProfile.severity(4, 8)),
                "Eight-block visual-only feedback missing");
        h.assertTrue(!ExplosionTinnitusProfile.shouldTriggerOverlay(ExplosionTinnitusProfile.severity(4, 11.05)),
                "Outside visual threshold triggered");
        float previous = 2;
        for (int i = 0; i <= 120; i++) {
            float severity = ExplosionTinnitusProfile.severity(4, i / 10.0);
            h.assertTrue(severity <= previous && severity >= 0, "Distance is not monotonic");
            previous = severity;
        }
        h.assertTrue(!ExplosionTinnitusProfile.shouldTrigger(Float.NaN)
                && !ExplosionTinnitusProfile.shouldTrigger(Float.POSITIVE_INFINITY)
                && ExplosionTinnitusProfile.severity(0, 0) == 0, "Invalid numeric input");
        h.assertTrue(AflSounds.EXPLOSION_TINNITUS.get().getLocation().getPath().equals("explosion_tinnitus"),
                "Missing sound registry");
        var packet = new AflNetwork.ExplosionTinnitusS2CPacket(0.75F, 42, h.getLevel().dimension().location());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AflNetwork.ExplosionTinnitusS2CPacket.encode(packet, buffer);
            h.assertTrue(packet.equals(AflNetwork.ExplosionTinnitusS2CPacket.decode(buffer)), "S2C roundtrip");
            h.assertTrue(!buffer.isReadable(), "Unread packet payload");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void tinnitusVanillaAndCancelledHook(GameTestHelper h) {
        Vec3 center = h.absoluteVec(new Vec3(3, 3, 3));
        HookProbe probe = new HookProbe(center);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            Explosion explosion = h.getLevel().explode(null, center.x, center.y, center.z, 0.25F,
                    Level.ExplosionInteraction.NONE);
            h.assertTrue(probe.detonations == 1, "Vanilla Detonate not fired exactly once");
            h.assertTrue(((ExplosionAccessor) explosion).afl$getRadius() == 0.25F, "Live radius accessor");
            probe.cancel = true;
            h.getLevel().explode(null, center.x, center.y, center.z, 0.25F, Level.ExplosionInteraction.NONE);
            h.assertTrue(probe.detonations == 1, "Cancelled explosion reached Detonate");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        h.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void tinnitusTaczHook(GameTestHelper h) {
        Vec3 center = h.absoluteVec(new Vec3(3, 3, 3));
        ProjectileExplosion explosion = new ProjectileExplosion(h.getLevel(), null, null, null, null,
                center.x, center.y, center.z, 0, 0.25F, false, Explosion.BlockInteraction.KEEP);
        HookProbe probe = new HookProbe(center);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            h.assertTrue(((ExplosionAccessor) explosion).afl$getRadius() == 0.25F,
                    "TaCZ radius is not propagated to vanilla base");
            explosion.explode();
            h.assertTrue(probe.detonations == 1, "TaCZ did not use shared Forge Detonate exactly once");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        h.succeed();
    }

    public static final class HookProbe {
        private final Vec3 center;
        private int detonations;
        private boolean cancel;

        HookProbe(Vec3 center) { this.center = center; }

        @SubscribeEvent
        public void start(ExplosionEvent.Start event) {
            if (cancel && event.getExplosion().getPosition().equals(center)) event.setCanceled(true);
        }

        @SubscribeEvent
        public void detonate(ExplosionEvent.Detonate event) {
            if (event.getExplosion().getPosition().equals(center)) detonations++;
        }
    }
}
