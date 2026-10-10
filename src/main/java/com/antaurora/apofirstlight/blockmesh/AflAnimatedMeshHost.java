package com.antaurora.apofirstlight.blockmesh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.extensions.IForgeBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * What {@code AflAnimatedBlockMeshRenderer} needs from a block entity. {@link AflAnimatedMeshBlockEntity} implements it
 * for plain block entities; a block entity that must keep another superclass (containers extending
 * RandomizableContainerBlockEntity) implements it directly and forwards to the static helpers below from its own
 * {@code onLoad}, {@code setBlockState} and {@code getRenderBoundingBox}. Same behavior either way.
 * <p>
 * It extends Forge's block entity extension only to supply {@link #getModelData}: the parts that rest are drawn by the
 * chunk (docs/dev/render_performance_v1.md), and the model data tells the chunk model which ones and in what pose.
 */
public interface AflAnimatedMeshHost extends IForgeBlockEntity {
    ResourceLocation meshProfile();

    AflBlockMeshAnimationState meshAnimation();

    Direction meshFacing();

    /** Client only; call after any authoritative state change (block state, update tag). */
    void refreshMeshAnimationTargets();

    /**
     * The concrete block's authority for one animation channel: true = the channel's target pose (open door, raised lid).
     * Both sides: refreshMeshAnimationTargets feeds it to the animation on the client, and the hit mesh
     * (meshhit/AnimatedMeshHits, docs/rendering/mesh_hit_runtime_v1.md) poses the model by it on either side.
     */
    boolean meshChannelTarget(String channel);

    /**
     * Value channels (2026-10-09, the diesel generator's gauge needles, key switch and hour meter drums): the channel's
     * target as a value 0..1, the animation's transforms scaled by it, eased from where the channel stands. Hosts that move
     * a channel only between its two ends keep the default, the boolean target. A channel resting between 0 and 1 is
     * drawn by the block entity renderer, not the chunk (client/blockmesh/AflMeshChunking).
     */
    default double meshChannelValue(String channel) {
        return meshChannelTarget(channel) ? 1 : 0;
    }

    /**
     * Whether the hit mesh (meshhit/AnimatedMeshHits) follows this channel. False for value channels of small parts
     * (needles, drums): the hit mesh keeps them at 0 and never waits for them to settle.
     */
    default boolean meshChannelAffectsHits(String channel) {
        return true;
    }

    /**
     * Profile part visibility, read every frame. A hidden part skips its geometry and its children. Used for alternative
     * part sets, e.g. a lamp's unlit and lit lenses (the lit set carries LabPBR emission in the atlas).
     */
    default boolean meshPartVisible(String part) {
        return true;
    }

    /** Parts drawn at full brightness instead of the block light (lit lamps, screens), read every frame. */
    default boolean meshPartEmissive(String part) {
        return false;
    }

    /**
     * The chunk mesh variant (which resting parts the chunk draws, and their poses), prepared on the client thread by
     * client/blockmesh/AflMeshChunking; empty on a server. May be called on a chunk builder thread: it only returns the
     * prepared value.
     */
    @Override
    default @NotNull ModelData getModelData() {
        return AflMeshChunkData.of(this);
    }

    /** Shared body of {@link #refreshMeshAnimationTargets()}: channel targets from the concrete block's authority. */
    static void refreshTargets(Level level, ResourceLocation meshProfile, AflBlockMeshAnimationState animation,
                               Predicate<String> target) {
        if (level == null || !level.isClientSide || animation == null || meshProfile == null) return;
        var profile = AflBlockMeshProfiles.get(meshProfile);
        animation.configure(profile);
        if (profile != null) for (var channel : profile.animations().keySet())
            animation.target(channel, target.test(channel), level.getGameTime());
    }

    /** {@link #refreshTargets} for hosts with value channels ({@link #meshChannelValue}). */
    static void refreshValueTargets(Level level, ResourceLocation meshProfile, AflBlockMeshAnimationState animation,
                                    ToDoubleFunction<String> value) {
        if (level == null || !level.isClientSide || animation == null || meshProfile == null) return;
        var profile = AflBlockMeshProfiles.get(meshProfile);
        animation.configure(profile);
        if (profile != null) for (var channel : profile.animations().keySet())
            animation.target(channel, value.applyAsDouble(channel), level.getGameTime());
    }

    /** Shared render bounds: the profile's full motion envelope for this facing, else the single block. */
    static AABB renderBounds(BlockPos pos, ResourceLocation meshProfile, Direction facing) {
        var profile = AflBlockMeshProfiles.get(meshProfile);
        return profile == null ? new AABB(pos) : profile.bounds(facing).move(pos);
    }
}
