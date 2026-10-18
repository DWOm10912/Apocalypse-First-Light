package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Survivor attributes read on vanilla events (the rest are read inside their own systems). */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class SurvivorStatEvents {
    private SurvivorStatEvents() {}

    /** fall_damage: the fall's damage × the player's value (the distance, and the landing noise, are unchanged). */
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player)
            event.setDamageMultiplier(event.getDamageMultiplier() * (float)SurvivorStats.multiplier(player, AflAttributes.FALL_DAMAGE, 0));
    }
}
