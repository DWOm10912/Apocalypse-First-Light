package com.antaurora.apofirstlight.survivor;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Start scenes (docs/gameplay/survivor_background_skills_v1_plan.md 8): where the first survivors of a world start. V1
 * has four, all post-outbreak (user 2026-10-17: bunker, forest home, wilderness, highway; story starts such as the
 * outbreak night come with the V2 story mode). One
 * scene per world, picked on the Create World page (everyone in a world starts in it). Known on the client before any
 * server exists, so a fixed list, not data. None is connected to a placement yet: every first survivor starts at the
 * world spawn, as in V0 (spawn source "initial"); the record keeps the scene for when the forest home porch (Main) and
 * C's roadside and lot anchors exist.
 *
 * @param maxPlayers how many may start there together (the forest home: a family's house, at most 3), 0 = any
 * @param recommended the background the scene suits (shown, never enforced), or null
 * @param connected whether choosing it changes where the survivor starts today
 */
public final class SurvivorScenes {
    public record Scene(String id, int maxPlayers, @Nullable String recommended, boolean connected) {
        public String name() {
            return id.substring(id.indexOf(':') + 1);
        }
    }

    public static final String WILDERNESS = "afl:wilderness";
    public static final List<Scene> ALL = List.of(
            new Scene("afl:bunker", 0, null, false),
            new Scene("afl:forest_home", 3, "afl:forest_resident", false),
            new Scene(WILDERNESS, 0, null, true),
            new Scene("afl:roadside", 0, "afl:trucker", false));

    private SurvivorScenes() {
    }

    /** The scene with this id, or the wilderness for an unknown one. */
    public static Scene byId(@Nullable String id) {
        Scene wilderness = null;
        for (Scene scene : ALL) {
            if (scene.id().equals(id)) return scene;
            if (scene.id().equals(WILDERNESS)) wilderness = scene;
        }
        return wilderness;
    }
}
