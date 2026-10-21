package com.antaurora.apofirstlight.hearing;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tinnitus V2 (docs/gameplay/tinnitus_v2_plan.md 3.2): the loud events and their doses for the players around, into
 * HearingLoad. Gunshots (NativeGunShot, by the gun's ear_db), explosions (every detonation, by its radius) and lightning
 * (a bolt joining the level, as a blast of lightning_radius). Inside (no sky over the head) a dose counts × indoor.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class HearingExposure {
    private HearingExposure() {
    }

    /** A shot fired by shooter at `at`: earDb at the shooter's ear, less suppressor_db when suppressed. */
    public static void gunshot(ServerPlayer shooter, Vec3 at, double earDb, boolean suppressed) {
        var c = TinnitusConfig.get();
        double base = Math.pow(10, (earDb - (suppressed ? c.suppressorDb : 0) - c.referenceDb) / 10);
        if (!(base > 1e-5)) return;
        ServerLevel level = shooter.serverLevel();
        for (ServerPlayer p : level.players()) {
            double d = p == shooter ? 1 : Math.max(1, p.getEyePosition().distanceTo(at));
            if (d > c.range) continue;
            HearingLoad.add(p, base / (d * d) * indoor(level, p));
        }
    }

    /** A blast of this radius at `at` (an explosion; lightning). */
    public static void blast(ServerLevel level, Vec3 at, double radius) {
        var c = TinnitusConfig.get();
        double k = c.blast * (radius / c.blastRadius) * (radius / c.blastRadius);
        if (!(k > 0)) return;
        for (ServerPlayer p : level.players()) {
            double d = Math.max(1, p.getEyePosition().distanceTo(at)), dose = k / (d * d);
            if (dose < c.blastMin) continue;
            HearingLoad.add(p, dose * indoor(level, p));
        }
    }

    private static double indoor(ServerLevel level, ServerPlayer p) {
        return level.canSeeSky(BlockPos.containing(p.getEyePosition())) ? 1 : TinnitusConfig.get().indoor;
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        float radius = ((ExplosionAccessor) event.getExplosion()).afl$getRadius();
        if (Float.isFinite(radius) && radius > 0) blast(level, event.getExplosion().getPosition(), radius);
    }

    @SubscribeEvent
    public static void lightning(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)) return;
        blast(level, bolt.position(), TinnitusConfig.get().lightningRadius);
    }
}
