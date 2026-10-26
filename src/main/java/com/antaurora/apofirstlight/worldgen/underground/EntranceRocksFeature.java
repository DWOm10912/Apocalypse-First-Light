package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.boulder.BoulderBlocks;
import com.antaurora.apofirstlight.boulder.BoulderData;
import com.antaurora.apofirstlight.boulder.BoulderRock;
import com.antaurora.apofirstlight.boulder.BoulderWorld;
import com.antaurora.apofirstlight.boulder.LooseStones;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * U4b (docs/worldgen/underground_geology_caves_v0_design.md 22.7): puts the plan's entrance rocks (EntranceRocks) in
 * their chunks, LOCAL_MODIFICATIONS, before the plants and the forest (a tree meets a boulder's cell and gives way). Per
 * rock: the ground block nearest the plan's ground (within 2 blocks: open, dry, solid under it); a boulder by
 * BoulderWorld#generate (every cell within the 3 x 3 chunks, or it is refused whole), a patch of loose stones where the
 * block can hold it. Nothing is loaded, nothing outside the 3 x 3 chunks read.
 */
public final class EntranceRocksFeature extends Feature<NoneFeatureConfiguration> {
    static final LongAdder CHUNKS = new LongAdder(), PLANNED = new LongAdder(), BOULDERS = new LongAdder(), STONES = new LongAdder(),
            NANOS = new LongAdder();
    static final Map<String, LongAdder> REFUSED = new ConcurrentHashMap<>();
    private static final int[] TRY = {0, 1, -1, 2, -2};
    private static volatile int[] forms;

    public EntranceRocksFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** {chunks, planned, boulders, stone patches, ms} and the refusals by reason, for /afl dev underground. */
    static String report() {
        var r = new TreeMap<String, Long>();
        REFUSED.forEach((k, v) -> r.put(k, v.sum()));
        return String.format(java.util.Locale.ROOT, "entrance rocks: %d chunks, %d planned, %d boulders, %d stone patches, %d ms, refused %s",
                CHUNKS.sum(), PLANNED.sum(), BOULDERS.sum(), STONES.sum(), NANOS.sum() / 1_000_000, r);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        UndergroundRef ref = ((RandomStateSeedAccess) (Object) level.getLevel().getChunkSource().randomState()).apocalypse$underground();
        UndergroundPlan plan = ref == null ? null : ref.get();
        if (plan == null || !BoulderBlocks.ready() || !LooseStones.ready()) return false;
        ChunkPos chunk = new ChunkPos(context.origin());
        UndergroundPlan.Rock[] rocks = plan.rocksIn(chunk.x, chunk.z);
        CHUNKS.increment();
        if (rocks.length == 0) return false;
        long t0 = System.nanoTime();
        int placed = 0;
        for (UndergroundPlan.Rock r : rocks) {
            PLANNED.increment();
            String why = put(level, chunk, r);
            if (why == null) {
                placed++;
                (r.boulder() ? BOULDERS : STONES).increment();
            } else REFUSED.computeIfAbsent((r.boulder() ? "boulder " : "stones ") + why, k -> new LongAdder()).increment();
        }
        NANOS.add(System.nanoTime() - t0);
        return placed > 0;
    }

    static String put(WorldGenLevel level, ChunkPos chunk, UndergroundPlan.Rock r) {
        BlockPos at = ground(level, r.x(), r.y(), r.z());
        if (at == null) return "no ground near the plan's";
        if (r.boulder()) {
            int f = form(r.form());
            return f < 0 ? "form missing" : BoulderWorld.generate(level, chunk, f, at, r.turn(), BoulderRock.of(UndergroundPlan.ROCK_LOOKS[r.look()]));
        }
        BlockState s = LooseStones.block().defaultBlockState().setValue(LooseStones.STONES, r.stones()).setValue(LooseStones.COBBLE, r.cobble())
                .setValue(LooseStones.FLINT, r.flint()).setValue(LooseStones.KIND, LooseStones.Kind.FIELD).setValue(LooseStones.TURN, r.turn())
                .setValue(LooseStones.WATERLOGGED, false).setValue(LooseStones.ROCK, BoulderRock.of(UndergroundPlan.ROCK_LOOKS[r.look()]));
        if (!s.canSurvive(level, at)) return "no hold";
        level.setBlock(at, s, Block.UPDATE_CLIENTS);
        return null;
    }

    /** The open dry block over solid ground nearest the plan's ground y (its block: floor(y + 0.5)), within 2; null none. */
    static BlockPos ground(WorldGenLevel level, int x, double y, int z) {
        int base = (int) Math.floor(y + 0.5);
        var p = new BlockPos.MutableBlockPos();
        for (int o : TRY) {
            p.set(x, base + o, z);
            if (level.isOutsideBuildHeight(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty() || !(Earthwork.open(s) || s.canBeReplaced()) || BoulderBlocks.isBoulder(s) || LooseStones.is(s)) continue;
            BlockState under = level.getBlockState(p.below());
            if (!under.getFluidState().isEmpty() || Earthwork.open(under) || under.canBeReplaced()) continue;
            return p.immutable();
        }
        return null;
    }

    /** The data form of a plan form (UndergroundPlan.ROCK_FORMS), -1 missing. */
    static int form(int planForm) {
        int[] f = forms;
        if (f == null) {
            f = new int[UndergroundPlan.ROCK_FORMS.length];
            for (int i = 0; i < f.length; i++) f[i] = BoulderData.form(UndergroundPlan.ROCK_FORMS[i]);
            forms = f;
        }
        return f[planForm];
    }
}
