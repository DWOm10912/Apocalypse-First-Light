package com.antaurora.apofirstlight.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a start scene's first survivors stand (SurvivorScenes; docs/gameplay/survivor_background_skills_v1_plan.md 8):
 * the place that owns a scene registers its spots (the forest home: Main's ForestHomeRuntime, the grass at the foot of
 * the porch steps). Asked twice: when the world is created (index 0 becomes the world spawn, so the spawn chunks are the
 * place's) and when a lineage's first survivor arrives (index = how many first survivors started before it). A scene
 * without a registered place, or a place this world does not have, gives null: the survivor starts at the world spawn
 * as before. Successors never use these (SurvivorSpawns).
 */
public final class SurvivorSceneStarts {
    /** A spot: the feet block (the block the player stands in, ground under it) and the yaw to face. */
    public record Spot(BlockPos feet, float yaw) {}

    /** A place's spots in a level; null when this level has no such place or no spot with that index. */
    @FunctionalInterface
    public interface Place {
        @Nullable Spot spot(ServerLevel overworld, int index);
    }

    private static final Map<String, Place> PLACES = new ConcurrentHashMap<>();

    private SurvivorSceneStarts() {
    }

    public static void register(String sceneId, Place place) {
        PLACES.put(sceneId, place);
    }

    /** The spot of a scene (null: none registered, the world lacks the place, or the index is past its spots). */
    @Nullable
    public static Spot spot(ServerLevel overworld, @Nullable String sceneId, int index) {
        if (sceneId == null || index < 0) return null;
        Place place = PLACES.get(SurvivorScenes.byId(sceneId).id());
        return place == null ? null : place.spot(overworld, index);
    }
}
