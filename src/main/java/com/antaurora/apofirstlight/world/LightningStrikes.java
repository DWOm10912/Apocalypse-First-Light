package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.LightningPackets;
import com.antaurora.apofirstlight.mixin.LightningBoltAccessor;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md) on the server. What a strike does to living things is lightning injury V1
 * (health/LightningInjury, docs/gameplay/lightning_injury_v1_plan.md): vanilla's strike (5 damage and fire, again every
 * tick the bolt flashes) is replaced for them by one sorting per bolt (direct, side flash, ground current); other entities
 * (items) keep vanilla's, a T15 takes none. Where it strikes and what it does to a tree: lightning V2 (world/LightningAim,
 * the rolling sphere; world/LightningTrees).
 * <p>
 * Thunder V1 (docs/audio/thunder_sounds_v1_plan.md 5): every player within FAR_RANGE hears and sees a strike, also far
 * beyond the entity's tracking range (LightningPackets.FarStrike, client/lightning/FarLightning); the infected hear only
 * strikes within NOISE_RADIUS (user 2026-10-21: farther thunder is everywhere in a storm and would drag them about).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LightningStrikes {
    /** The server keeps a bolt this long (ticks), so the client's flash plays out (mixin LightningBoltLifeMixin). */
    public static final int MIN_TICKS = 20;
    /** Players this far (m, across) are told of a strike. */
    public static final double FAR_RANGE = 15000;
    /** The infected hear a strike this far (m), as an explosion. */
    public static final double NOISE_RADIUS = 80;

    private LightningStrikes() {
    }

    @SubscribeEvent
    public static void struck(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!((LightningBoltAccessor) bolt).afl$visualOnly())
            NoiseSystem.emit(new NoiseEvent(null, bolt.position(), NoiseType.EXPLOSION, level.getGameTime(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.LIGHTNING_BOLT), NOISE_RADIUS), level);
        LightningPackets.FarStrike packet = new LightningPackets.FarStrike(bolt.getUUID(), bolt.getX(), bolt.getY(), bolt.getZ());
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - bolt.getX(), dz = player.getZ() - bolt.getZ();
            if (dx * dx + dz * dz <= FAR_RANGE * FAR_RANGE) AflNetwork.lightning(player, packet);
        }
    }
}
