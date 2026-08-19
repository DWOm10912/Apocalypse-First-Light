package com.antaurora.apofirstlight.world.bunker;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BunkerPlayerSpawnEvents {
    private static final Logger LOGGER = ApocalypseFirstLight.LOGGER;
    private static final String INITIAL_SPAWN_KEY = ApocalypseFirstLight.MOD_ID + ":bunker_initial_spawned";
    private static final ResourceLocation BUNKER_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "bunker");

    private BunkerPlayerSpawnEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag persistent = player.getPersistentData();
        if (persistent.getBoolean(INITIAL_SPAWN_KEY)) return;

        ServerLevel overworld = player.server.overworld();
        BunkerSavedData data = overworld.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (!data.isGenerated()) {
            LOGGER.warn("[AFL Bunker] Initial player spawn deferred for {}; bunker is not generated", player.getGameProfile().getName());
            return;
        }
        Optional<StructureTemplate> templateOptional = player.server.getStructureManager().get(BUNKER_ID);
        if (templateOptional.isEmpty()) {
            LOGGER.error("[AFL Bunker] Initial player spawn failed: missing structure template {}", BUNKER_ID);
            return;
        }

        StructureTemplate template = templateOptional.get();
        BlockPos preferred = preferredSpawn(overworld, data);
        BlockPos safe = findSafePosition(overworld, preferred);
        if (safe == null) {
            LOGGER.warn("[AFL Bunker] No safe initial player spawn found near {} for {}", preferred, player.getGameProfile().getName());
            return;
        }
        if (!safe.equals(preferred)) {
            LOGGER.warn("[AFL Bunker] Preferred spawn anchor unsafe; using fallback {} for {}", safe,
                    player.getGameProfile().getName());
        }
        overworld.getChunk(safe.getX() >> 4, safe.getZ() >> 4);
        player.teleportTo(overworld, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.setRespawnPosition(Level.OVERWORLD, safe, player.getYRot(), true, false);
        persistent.putBoolean(INITIAL_SPAWN_KEY, true);
        LOGGER.info("[AFL Bunker] Initial player spawn: {} -> {}", player.getGameProfile().getName(), safe);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        if (original.getBoolean(INITIAL_SPAWN_KEY)) {
            event.getEntity().getPersistentData().putBoolean(INITIAL_SPAWN_KEY, true);
        }
    }

    public static BlockPos preferredSpawn(ServerLevel level, BunkerSavedData data) {
        Optional<StructureTemplate> templateOptional = level.getServer().getStructureManager().get(BUNKER_ID);
        if (templateOptional.isEmpty()) return null;
        return BunkerPlacementManager.localToWorld(templateOptional.get(), data.getOrigin(),
                BunkerPlacementManager.parseRotation(data.getRotation()), BunkerPlacementManager.PLAYER_SPAWN_LOCAL);
    }

    public static BlockPos findSafePosition(ServerLevel level, BlockPos preferred) {
        if (preferred == null) return null;
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos candidate = preferred.offset(dx, dy, dz);
                        if (isSafe(level, candidate)) return candidate;
                    }
                }
            }
        }
        return isSafe(level, preferred) ? preferred : null;
    }

    public static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos support = feet.below();
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState supportState = level.getBlockState(support);
        return level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty()
                && feetState.getCollisionShape(level, feet).isEmpty()
                && headState.getCollisionShape(level, head).isEmpty()
                && !supportState.getCollisionShape(level, support).isEmpty();
    }
}
