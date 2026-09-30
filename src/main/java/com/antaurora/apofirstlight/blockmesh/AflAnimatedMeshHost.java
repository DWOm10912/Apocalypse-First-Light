package com.antaurora.apofirstlight.blockmesh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/**
 * What {@code AflAnimatedBlockMeshRenderer} needs from a block entity. {@link AflAnimatedMeshBlockEntity} implements it
 * for plain block entities; a block entity that must keep another superclass (containers extending
 * RandomizableContainerBlockEntity) implements it directly and forwards to the static helpers below from its own
 * {@code onLoad}, {@code setBlockState} and {@code getRenderBoundingBox}. Same behavior either way.
 */
public interface AflAnimatedMeshHost {
    ResourceLocation meshProfile();

    AflBlockMeshAnimationState meshAnimation();

    Direction meshFacing();

    /** Client only; call after any authoritative state change (block state, update tag). */
    void refreshMeshAnimationTargets();

    /** Shared body of {@link #refreshMeshAnimationTargets()}: channel targets from the concrete block's authority. */
    static void refreshTargets(Level level, ResourceLocation meshProfile, AflBlockMeshAnimationState animation,
                               Predicate<String> target) {
        if (level == null || !level.isClientSide || animation == null || meshProfile == null) return;
        var profile = AflBlockMeshProfiles.get(meshProfile);
        animation.configure(profile);
        if (profile != null) for (var channel : profile.animations().keySet())
            animation.target(channel, target.test(channel), level.getGameTime());
    }

    /** Shared render bounds: the profile's full motion envelope for this facing, else the single block. */
    static AABB renderBounds(BlockPos pos, ResourceLocation meshProfile, Direction facing) {
        var profile = AflBlockMeshProfiles.get(meshProfile);
        return profile == null ? new AABB(pos) : profile.bounds(facing).move(pos);
    }
}
