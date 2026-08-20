package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.Set;

public final class WildlifeSpawnPolicy {
    private static final Set<MobCategory> TARGET_CATEGORIES = Set.of(
            MobCategory.CREATURE, MobCategory.AMBIENT, MobCategory.WATER_CREATURE,
            MobCategory.WATER_AMBIENT, MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS);

    private WildlifeSpawnPolicy() {}

    public static boolean shouldDenyNaturalSpawn(ServerLevel level, EntityType<?> type,
                                                   BlockPos pos, MobSpawnType reason) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || !isTargetVanillaWildlife(type) || !isNaturalSpawnReason(reason)) return false;
        return !RadiationManager.isNaturalZone(level, pos, RadiationZone.SAFE);
    }

    public static boolean isTargetVanillaWildlife(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return "minecraft".equals(id.getNamespace()) && TARGET_CATEGORIES.contains(type.getCategory());
    }

    public static boolean isNaturalSpawnReason(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    public static Set<MobCategory> targetCategories() { return TARGET_CATEGORIES; }
}
