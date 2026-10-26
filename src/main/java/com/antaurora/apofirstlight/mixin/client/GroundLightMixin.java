package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.terrainsmooth.client.RockCarveMesh;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The client level's one light read (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md 3.7): ClientLevel gets its
 * own BlockAndTintGetter#getBrightness, which every live read goes through (EntityRenderer's light levels, LevelRenderer's
 * light colour for particles and block entities, the hand), so an edited column's skin cell, a carved or worn block and a
 * pocket beside a carving are lit as the open space they partly are (RockCarveMesh#brightness). Replaces the per-reader
 * hooks of 2026-10-21 / 2026-10-24 (EarthworkEntityLightMixin, the return hook of EarthworkParticleLightMixin), each of
 * which missed the readers it did not sit on. Chunk build regions are not the client level and read as before.
 */
@Mixin(ClientLevel.class)
public abstract class GroundLightMixin implements BlockAndTintGetter {
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        int raw = getLightEngine().getLayerListener(layer).getLightValue(pos);
        return RockCarveMesh.brightness(this, layer, pos, raw);
    }
}
