package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The natural-land spawn fallback for Terrain V2 worlds (Phase 2, 2026-10-10; docs/worldgen/legacy_worldgen_retirement_v1.md
 * and terrain_v2_phase2_generation_v1.md). The old bunker start is retired and the forest-house start is not built
 * yet, so a new world's spawn is picked from the plan: the plan cell nearest the origin (searched outward on the 16 m
 * grid, within 4 km) that is dry plain or coastal plain (stable depth 12), at least 64 m from water, grade at most
 * 1/32, and not marsh. The spawn Y stands on the planned top block there (ceil(plan height); the Phase 2 save audit
 * found 97.6% of land tops exactly at ceil(h) - 1). Phase 2 read the heightmap of a chunk that was not generated yet and
 * stored Y -64 (players still landed on the surface, vanilla re-solves their Y), fixed 2026-10-10 (Phase 2b).
 * Without a plan (another generator, a datapack world) vanilla's spawn search runs unchanged.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerrainV2SpawnEvents {
    static final int SEARCH_RADIUS = 4096;

    private TerrainV2SpawnEvents() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) return;
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        if (!access.apocalypse$hasTerrainPlan()) return;
        TerrainPlanSurface plan = TerrainPlanStore.peek(access.apocalypse$getSeed());
        if (plan == null) return;
        int[] xz = plan.naturalSpawn(SEARCH_RADIUS);
        if (xz == null) {
            ApocalypseFirstLight.LOGGER.warn("[AFL Terrain V2] no plain spawn cell within {} blocks; vanilla spawn search", SEARCH_RADIUS);
            return;
        }
        int y = (int) Math.ceil(plan.heightAt(xz[0] + 0.5, xz[1] + 0.5));
        ServerLevelData data = event.getSettings();
        data.setSpawn(new BlockPos(xz[0], y, xz[1]), 0.0F);
        event.setCanceled(true);
        ApocalypseFirstLight.LOGGER.info("[AFL Terrain V2] spawn {} {} {} (plan height {})", xz[0], y, xz[1],
                String.format("%.1f", plan.heightAt(xz[0], xz[1])));
    }
}
