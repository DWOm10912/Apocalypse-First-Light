package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md) on the server: a bolt kills what it strikes. Vanilla's strike (5 damage and
 * fire, again every tick the bolt flashes) is replaced for living things by one hit of {@link #LETHAL_DAMAGE} per bolt,
 * set alight; other entities (items, vehicles) keep vanilla's. Where it strikes is still vanilla's (the highest block, or a
 * living thing under the open sky near it, or a lightning rod). Creative and spectator players are not hurt (vanilla).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightningStrikes {
    /** A direct strike's damage: past any armour and protection. */
    public static final float LETHAL_DAMAGE = 1000;
    /** The server keeps a bolt this long (ticks), so the client's flash plays out (mixin LightningBoltLifeMixin). */
    public static final int MIN_TICKS = 20;
    /** A struck thing burns this long (s; vanilla's). */
    private static final int BURN_SECONDS = 8;

    private LightningStrikes() {
    }

    @SubscribeEvent
    public static void struck(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        event.setCanceled(true);
        LightningBolt bolt = event.getLightning();
        // the same bolt strikes again each tick it flashes: once is enough
        if (bolt.getHitEntities().anyMatch(e -> e == living)) return;
        living.setSecondsOnFire(BURN_SECONDS);
        living.hurt(living.damageSources().lightningBolt(), LETHAL_DAMAGE);
    }
}
