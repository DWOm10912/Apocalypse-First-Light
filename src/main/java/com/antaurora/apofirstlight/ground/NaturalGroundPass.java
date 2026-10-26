package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.worldgen.lock.WorldgenGateState;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGates;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * N1c (docs/worldgen/natural_ground_n1_design.md 12): a world's natural ground made of the AFL blocks. The generation
 * itself is untouched (surface rules, features, the road, town and home passes all still lay and read vanilla ground);
 * once every chunk that can write into a chunk has finished its features (the chunk's LIGHT step: its neighbours are at
 * INITIALIZE_LIGHT, ChunkStatusNaturalGroundMixin), each vanilla block of {@link NaturalGroundTable#SWAP} in it becomes
 * its AFL block. Gravel with water over it (within {@link #WET_REACH} blocks up the column) is round, other gravel
 * angular.
 * <p>
 * Same shape, same light: the vanilla and AFL blocks are full, opaque, motion-blocking cubes (mud: both 14/16), so the
 * sections are written straight (no updates, heightmaps and light as they were; locked like a feature's write into a
 * neighbour). Only worlds created with the gate
 * {@link WorldgenGates#NATURAL_GROUND} swap: an older world's new chunks stay vanilla (no seam between old and new).
 * Things built later at run time on natural ground (a bunker's burial) ask {@link #natural}.
 */
public final class NaturalGroundPass {
    private NaturalGroundPass() {}

    /** How far up the column water makes gravel a bed gravel. */
    static final int WET_REACH = 3;

    private static volatile Map<Block, BlockState> swap;
    private static volatile BlockState wetGravel, gravel;
    private static final AtomicLong CHUNKS = new AtomicLong(), BLOCKS = new AtomicLong(), NANOS = new AtomicLong();

    private static BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException("natural ground table: " + s, e);
        }
    }

    static Map<Block, BlockState> table() {
        Map<Block, BlockState> m = swap;
        if (m != null) return m;
        m = new IdentityHashMap<>();
        for (var e : NaturalGroundTable.SWAP.entrySet())
            m.put(BuiltInRegistries.BLOCK.get(new ResourceLocation(e.getKey())), parse(e.getValue()));
        gravel = m.get(net.minecraft.world.level.block.Blocks.GRAVEL);
        wetGravel = parse(NaturalGroundTable.GRAVEL_WET);
        return swap = m;
    }

    /** Is the world's natural ground AFL's (created with the gate)? */
    public static boolean on(ServerLevel level) {
        return WorldgenGateState.enabled(level, WorldgenGates.NATURAL_GROUND);
    }

    /** The natural ground block a run-time pass lays: the AFL block in a world with the gate, the vanilla one elsewhere. */
    public static BlockState natural(ServerLevel level, BlockState vanilla) {
        if (!on(level)) return vanilla;
        BlockState s = table().get(vanilla.getBlock());
        return s == null ? vanilla : s;
    }

    /** The chunk's vanilla natural ground to AFL's (class comment). */
    public static void apply(ServerLevel level, ChunkAccess chunk) {
        if (!on(level)) return;
        long t0 = System.nanoTime();
        Map<Block, BlockState> m = table();
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        long n = 0;
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection sec = sections[s];
            if (sec.hasOnlyAir() || !sec.maybeHas(b -> m.containsKey(b.getBlock()))) continue;
            for (int ly = 0; ly < 16; ly++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                BlockState to = m.get(sec.getBlockState(lx, ly, lz).getBlock());
                if (to == null) continue;
                if (to == gravel && wet(sections, s, lx, ly, lz)) to = wetGravel;
                sec.setBlockState(lx, ly, lz, to);   // locked, as a feature's write: a neighbour's light may read
                n++;
            }
        }
        if (n > 0) chunk.setUnsaved(true);
        CHUNKS.incrementAndGet();
        BLOCKS.addAndGet(n);
        NANOS.addAndGet(System.nanoTime() - t0);
    }

    /** Water within WET_REACH blocks over the gravel, through gravel and other ground (the column in this chunk). */
    private static boolean wet(LevelChunkSection[] sections, int s, int lx, int ly, int lz) {
        for (int d = 1; d <= WET_REACH; d++) {
            int y = ly + d, si = s + (y >> 4);
            if (si >= sections.length) return false;
            BlockState above = sections[si].getBlockState(lx, y & 15, lz);
            if (!above.getFluidState().isEmpty()) return above.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            if (above.isAir()) return false;
        }
        return false;
    }

    /** Chunks swapped, blocks swapped, mean time per chunk (ms), for the dev stats. */
    public static String stats() {
        long c = CHUNKS.get();
        return String.format("natural ground: %d chunks, %d blocks, %.3f ms / chunk", c, BLOCKS.get(), c == 0 ? 0 : NANOS.get() / 1e6 / c);
    }
}
