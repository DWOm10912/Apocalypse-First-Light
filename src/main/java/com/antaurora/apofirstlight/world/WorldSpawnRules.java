package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldSpawnRules {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation ZOMBIE = new ResourceLocation("minecraft", "zombie");

    private WorldSpawnRules() {
    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!event.getLevel().getLevel().dimension().location().equals(OVERWORLD)) {
            return;
        }
        if (WildlifeSpawnPolicy.shouldDenyNaturalSpawn(event.getLevel().getLevel(), event.getEntityType(),
                event.getPos(), event.getSpawnType())) {
            event.setResult(Event.Result.DENY);
            ApocalypseFirstLight.LOGGER.debug("[AFL WILDLIFE] Blocked natural spawn Entity={} Pos={}",
                    EntityType.getKey(event.getEntityType()), event.getPos());
            return;
        }
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ResourceLocation entityId = EntityType.getKey(event.getEntityType());
        if (!ZOMBIE.equals(entityId)) {
            event.setResult(Event.Result.DENY);
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL WORLD] Blocked natural hostile spawn Entity={} Dimension={}", entityId, OVERWORLD
            );
        }
    }
}
