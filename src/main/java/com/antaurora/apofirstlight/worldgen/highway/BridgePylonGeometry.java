package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fully validated immutable placements, cached per crossing; no level/chunk is retained. */
public record BridgePylonGeometry(LandmarkMainSpan span, boolean enabled, String fallbackReason,
        String foundationStatus, List<Placement> placements, List<Anchor> anchors, List<Integer> baseYs) {
    public record Placement(BlockPos pos, BlockState state) {}
    /** Socket block centers; facingAlong selects the ascending/descending station half-plane. */
    public record Anchor(String kind, int pylon, int side, int facingAlong, int index, BlockPos socket) {}

    static BridgePylonGeometry disabled(LandmarkMainSpan span, String reason, String foundation) {
        return new BridgePylonGeometry(span, false, reason, foundation, List.of(), List.of(), List.of());
    }

    static BridgePylonGeometry plan(SeaBridgeGeometry bridge, HighwayProfile profile,
            SeaBridgeEngineering.FoundationSampler sampler, int minY, int maxY) {
        var span = LandmarkMainSpan.of(bridge);
        if (!span.eligible()) return disabled(span, span.reason(), "NOT_SAMPLED");
        var ys = span.pylons().stream().map(p -> profile.sampleAt(p).roadY()).toList();
        if (ys.stream().anyMatch(y -> y - 4 < minY || y + LandmarkMainSpan.TOWER_HEIGHT >= maxY))
            return disabled(span, "BUILD_HEIGHT", "NOT_SAMPLED");
        var builder = new Builder(bridge.plan());
        var concrete = HighwayPalette.REINFORCED_CONCRETE;
        // Every column in all four 3x11 footings must have a legal immutable seabed sample.
        for (int i = 0; i < 2; i++) {
            int p = span.pylons().get(i), d = ys.get(i);
            for (int side : new int[]{-1, 1}) for (int l = 12; l <= 14; l++) for (int ds = -5; ds <= 5; ds++) {
                var pos = LandmarkMainSpan.position(bridge.plan(), p + ds, side * l, d - 3);
                if (!bridge.bounds().contains(pos.getX(), pos.getZ()))
                    return disabled(span, "ENVELOPE_EXCEEDED", "NOT_COMPLETE");
                var f = sampler.sample(pos.getX(), pos.getZ(), d - 3);
                if (!f.found() || f.y() < minY || f.y() > d - 4)
                    return disabled(span, "PYLON_FOUNDATION_FAILED", "FAILED");
                builder.box(p + ds, p + ds, side * l, side * l, f.y(), d - 4, concrete);
            }
        }
        PylonZoneGeometry.addPlatforms(builder, span, profile);
        var anchors = new ArrayList<Anchor>();
        var beam = AflBlocks.STEEL_BEAM.get().defaultBlockState();
        var brace = AflBlocks.STEEL_BRACE.get().defaultBlockState();
        for (int i = 0; i < 2; i++) {
            int p = span.pylons().get(i), d = ys.get(i);
            for (int side : new int[]{-1, 1}) {
                builder.box(p - 4, p + 4, side * 12, side * 14, d - 3, d + 5, concrete);
                builder.box(p - 2, p + 2, side * 12, side * 14, d + 6, d + 61, concrete);
                // Full-height outer ribs make a recessed longitudinal face rather than a plain pole.
                for (int face : new int[]{-1, 1}) {
                    for (int l : new int[]{12, 14})
                        builder.box(p + face * 3, p + face * 3, side * l, side * l, d + 6, d + 61, concrete);
                    builder.box(p + face * 3, p + face * 3, side * 13, side * 13, d + 6, d + 61,
                            brace.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                    for (int j = 0; j < LandmarkMainSpan.ANCHOR_HEIGHTS.size(); j++) {
                        int h = LandmarkMainSpan.ANCHOR_HEIGHTS.get(j);
                        anchors.add(new Anchor("TOWER", i, side, face, j,
                                LandmarkMainSpan.position(bridge.plan(), p + face * 4, side * 13, d + h)));
                        int s = p + face * (24 + 12 * j), road = profile.sampleAt(s).roadY();
                        // One-cell ledge attaches to existing +/-12. No rail or asphalt is removed.
                        builder.box(s, s, side * 13, side * 13, road - 3, road, concrete);
                        anchors.add(new Anchor("DECK", i, side, face, j,
                                LandmarkMainSpan.position(bridge.plan(), s, side * 13, road + 1)));
                    }
                }
                builder.box(p - 3, p + 3, side * 12, side * 14, d + 62, d + 64, concrete);
            }
            // High portal crossbeam: concrete chords and end jambs clamp a vertical steel web.
            builder.box(p - 2, p + 2, -11, 11, d + 46, d + 47, concrete);
            builder.box(p - 2, p + 2, -11, 11, d + 51, d + 52, concrete);
            builder.box(p - 2, p + 2, -11, -10, d + 48, d + 50, concrete);
            builder.box(p - 2, p + 2, 10, 11, d + 48, d + 50, concrete);
            for (int face : new int[]{-1, 1}) {
                for (int l : new int[]{-9, -3, 3, 9})
                    builder.box(p + face * 2, p + face * 2, l, l, d + 48, d + 50,
                            beam.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                for (int l : new int[]{-6, 0, 6})
                    builder.box(p + face * 2, p + face * 2, l, l, d + 48, d + 50,
                            brace.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            }
        }
        for (var entry : builder.blocks.entrySet()) {
            var pos = entry.getKey();
            if (!bridge.bounds().contains(pos.getX(), pos.getZ()))
                return disabled(span, "ENVELOPE_EXCEEDED", "READY");
            if (pos.getY() < minY || pos.getY() >= maxY)
                return disabled(span, "BUILD_HEIGHT", "READY");
        }
        return new BridgePylonGeometry(span, true, "NONE", "READY:132_COLUMNS",
                builder.blocks.entrySet().stream().map(e -> new Placement(e.getKey(), e.getValue())).toList(),
                List.copyOf(anchors), ys);
    }

    public void render(HighwayBlockWriter writer) {
        for (var block : placements) if (writer.owns(block.pos())) writer.set(block.pos(), block.state());
    }

    public String description(HighwayPlan plan) {
        return span.description(plan) + " landmarkEnabled=" + enabled
                + " landmarkStatus=" + (enabled ? "ENABLED" : "DISABLED")
                + " pylonBaseY=" + baseYs + " pylonTopY=" + baseYs.stream().map(y -> y + 64).toList()
                + " pylonFoundationStatus=" + foundationStatus + " fallbackReason=" + fallbackReason
                + " futureCableAnchorCount=" + anchors.size();
    }

    static final class Builder {
        final HighwayPlan plan;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Builder(HighwayPlan plan) { this.plan = plan; }
        void box(int s0, int s1, int l0, int l1, int y0, int y1, BlockState state) {
            for (int s = Math.min(s0, s1); s <= Math.max(s0, s1); s++) for (int l = Math.min(l0, l1); l <= Math.max(l0, l1); l++)
                for (int y = y0; y <= y1; y++) blocks.put(LandmarkMainSpan.position(plan, s, l, y), state);
        }
    }
}
