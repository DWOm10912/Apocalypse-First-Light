package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
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
        return decision(level, type, pos, reason).deny();
    }

    /** Policy decision only; PASS leaves all vanilla/Forge placement checks intact. */
    public static Decision decision(ServerLevel level, EntityType<?> type, BlockPos pos, MobSpawnType reason) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return Decision.pass("NOT_OVERWORLD");
        if (!isTargetVanillaWildlife(type)) return Decision.pass("NOT_TARGET");
        if (!isNaturalSpawnReason(reason)) return Decision.pass("NOT_NATURAL_REASON");
        StartupPlainsEnclave.Zone startup = StartupPlainsEnclave.zoneAt(pos.getX(), pos.getZ(), level.getSeed());
        if (startup == StartupPlainsEnclave.Zone.CORE_PLAINS || startup == StartupPlainsEnclave.Zone.FRINGE_PLAINS)
            return Decision.pass("STARTUP_SAFE");
        if (startup == StartupPlainsEnclave.Zone.WOODLAND_BUFFER) return Decision.deny("STARTUP_WOODLAND");
        return RadiationManager.isNaturalZone(level, pos, RadiationZone.SAFE)
                ? Decision.pass("NATURAL_SAFE") : Decision.deny("NATURAL_IRRADIATED");
    }

    public static boolean isTargetVanillaWildlife(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return "minecraft".equals(id.getNamespace()) && TARGET_CATEGORIES.contains(type.getCategory());
    }

    public static boolean isNaturalSpawnReason(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    public static Set<MobCategory> targetCategories() { return TARGET_CATEGORIES; }

    public record Decision(boolean deny, String reason) {
        private static Decision pass(String reason) { return new Decision(false, reason); }
        private static Decision deny(String reason) { return new Decision(true, reason); }
    }
}
