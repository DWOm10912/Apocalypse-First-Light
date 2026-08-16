package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public final class AcousticOcclusionResolver {
    private static final TagKey<net.minecraft.world.level.block.Block> DAMPENING_BLOCKS = TagKey.create(
            Registries.BLOCK, new ResourceLocation(ApocalypseFirstLight.MOD_ID, "noise_dampening_blocks")
    );
    private static final double SAMPLE_STEP = 0.5;

    private AcousticOcclusionResolver() {}

    public static Result resolve(ServerLevel level, Vec3 source, Vec3 listener, double baseRadius) {
        double distance = source.distanceTo(listener);
        int samples = Math.max(1, (int) Math.ceil(distance / SAMPLE_STEP));
        Set<BlockPos> visited = new HashSet<>();
        int woolLayers = 0;
        for (int i = 0; i <= samples; i++) {
            double progress = (double) i / samples;
            BlockPos pos = BlockPos.containing(source.lerp(listener, progress));
            if (!visited.add(pos)) continue;
            if (level.getBlockState(pos).is(DAMPENING_BLOCKS)) woolLayers++;
        }
        return new Result(baseRadius * Math.pow(0.5, woolLayers), woolLayers);
    }

    public record Result(double effectiveRadius, int woolLayers) {}
}
