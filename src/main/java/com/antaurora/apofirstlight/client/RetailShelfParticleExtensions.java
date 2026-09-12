package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

/** Bounded breaking debris for the detailed two-block shelf shape. */
public final class RetailShelfParticleExtensions implements IClientBlockExtensions {
    private static final int PARTICLES_PER_HALF = 16;

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        if (!(level instanceof ClientLevel clientLevel)) return true;

        // Vanilla subdivides every VoxelShape box, which overproduces debris for this fixture.
        // Each half gets a fixed budget and uses the production block-model particle sprite.
        AABB bounds = state.getShape(level, pos).bounds();
        for (int i = 0; i < PARTICLES_PER_HALF; i++) {
            double x = pos.getX() + bounds.minX + clientLevel.random.nextDouble() * (bounds.maxX - bounds.minX);
            double y = pos.getY() + bounds.minY + clientLevel.random.nextDouble() * (bounds.maxY - bounds.minY);
            double z = pos.getZ() + bounds.minZ + clientLevel.random.nextDouble() * (bounds.maxZ - bounds.minZ);
            double vx = (clientLevel.random.nextDouble() - 0.5D) * 0.16D;
            double vy = clientLevel.random.nextDouble() * 0.12D;
            double vz = (clientLevel.random.nextDouble() - 0.5D) * 0.16D;
            manager.add(new TerrainParticle(clientLevel, x, y, z, vx, vy, vz, state, pos)
                    .updateSprite(state, pos));
        }
        return true;
    }
}
