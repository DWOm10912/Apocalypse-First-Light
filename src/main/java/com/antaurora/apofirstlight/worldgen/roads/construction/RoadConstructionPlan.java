package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

/** Immutable actual-world preview. PREVIEW_READY still requires fresh guards and explicit approval to write. */
public record RoadConstructionPlan(String planId, String sourcePlanId, String version,
        ResourceLocation dimension, BoundsXZ bounds, Status status, List<BlockEdit> edits,
        List<Guard> guards, List<Profile> profiles, Map<String,Integer> lotGroundY, Summary summary, List<String> issues) {
    public RoadConstructionPlan {
        edits=List.copyOf(edits); guards=List.copyOf(guards); profiles=List.copyOf(profiles);
        lotGroundY=Collections.unmodifiableMap(new TreeMap<>(lotGroundY)); issues=List.copyOf(issues);
    }
    public enum Status { PREVIEW_READY, REJECTED, UNKNOWN }
    public enum EditKind { CUT, FOUNDATION, FILL, SURFACE, SHOULDER }
    public record BlockEdit(BlockPos pos, BlockState before, BlockState after, String unit, EditKind kind) {
        public BlockEdit { pos=pos.immutable(); }
    }
    public record Guard(BlockPos pos, BlockState state) {
        public Guard { pos=pos.immutable(); }
    }
    /** edge ID uses from->to station order; node:<id> contains its single flat asphalt elevation. */
    public record Profile(String edgeId, List<Integer> surfaceH16) {
        public Profile { surfaceH16=List.copyOf(surfaceH16); }
    }
    public record Summary(int roads,int junctions,int chunks,int maxCutDepth,int maxFillHeight,
            int cutBlocks,int fillBlocks,int expectedEdits,int columns) {}
}
