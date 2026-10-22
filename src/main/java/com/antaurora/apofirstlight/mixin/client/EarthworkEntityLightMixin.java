package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Light for an entity whose eyes are in an edited column's skin cell (Earthwork A3, user 2026-10-21: in a deep pit the
 * player and the T15 went black). The cell is a full block in the data, so it stores no light, though its space above
 * the skin is open; the light is read from the first cell above that is not a skin cell (Earthwork#clientLightCell).
 * Players, mobs, vehicles, dropped items and the first person hand (EntityRenderer#getPackedLightCoords); particles in
 * EarthworkParticleLightMixin.
 */
@Mixin(EntityRenderer.class)
public abstract class EarthworkEntityLightMixin {
    @ModifyVariable(method = "getPackedLightCoords", at = @At("STORE"))
    private BlockPos afl$probe(BlockPos pos, Entity entity, float partialTicks) {
        return Earthwork.clientLightCell(entity.level(), pos);
    }
}
