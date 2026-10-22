package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.registry.AflItems;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 3.2, 3.4, 11.2): a boulder's blocks in the world, server side.
 * A node's blocks are its root (its anchor cell) and a rock cell in each of its cells above the form's ground (the cells
 * that draw it, BoulderModel) and in each buried one the ground has bared (Earthwork#settle). Pieces of one rock share
 * cells along their cracks: a cell goes only when no rock fills it any more. Every change runs Earthwork#quietly.
 */
public final class BoulderWorld {
    private BoulderWorld() {}

    /**
     * Places a form at an origin cell (form cell (0, 0, 0): the cell over the ground, the form's ground its floor), turned;
     * the number of rocks placed. Above the ground it takes any cell (a dev command and worldgen pick the site), below only
     * open cells (the rest stays the soil the rock is buried in).
     */
    public static int place(ServerLevel level, int form, BlockPos origin, int turn) {
        var f = BoulderData.get().forms[form];
        Earthwork.quietly(() -> {
            for (int top : f.top) {
                var n = BoulderData.node(top);
                BlockPos anchor = at(origin, turn, n.ax, n.ay, n.az);
                level.setBlock(anchor, BoulderBlocks.rootState(n.index, turn), Block.UPDATE_ALL);
                resetHits(level, anchor);
                for (var c : n.cellList) {
                    BlockPos p = at(origin, turn, c.x, c.y, c.z);
                    if (p.equals(anchor)) continue;
                    BlockState s = level.getBlockState(p);
                    if (BoulderBlocks.isBoulder(s) && s.getBlock() == BoulderBlocks.root()) continue;   // another rock's root
                    if (c.y >= 0 || Earthwork.open(s)) level.setBlock(p, BoulderBlocks.cell().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        });
        return f.top.length;
    }

    /** A form cell's world position for a form placed at origin, turned. */
    static BlockPos at(BlockPos origin, int turn, int fx, int fy, int fz) {
        return origin.offset(BoulderData.turnX(turn, fx, fz), fy, BoulderData.turnZ(turn, fx, fz));
    }

    /** The boulder blocks of a root's rock: its root and the rock cells in its cells. */
    static List<BlockPos> blocks(ServerLevel level, BoulderIndex.Root r) {
        Set<BlockPos> out = new LinkedHashSet<>();
        out.add(r.pos());
        for (var c : r.node.cellList) {
            BlockPos p = r.world(c.x, c.y, c.z);
            if (BoulderBlocks.isBoulder(level.getBlockState(p))) out.add(p);
        }
        return new ArrayList<>(out);
    }

    /**
     * A struck rock splits into its next stage's pieces in place: the same form origin and turn, the pieces' roots at their
     * anchors (no strikes yet), their cells where the rock had blocks or where they reach over the ground; the rock's cells
     * no piece fills go. The pieces stay where they are (they rest on each other and on the ground they were cut from).
     */
    static void split(ServerLevel level, BoulderIndex.Root r) {
        List<BlockPos> old = blocks(level, r);
        Set<BlockPos> had = new LinkedHashSet<>(old);
        Earthwork.quietly(() -> {
            level.setBlock(r.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);   // out of the index first
            Set<BlockPos> roots = new LinkedHashSet<>();
            for (int child : r.node.children) {
                var n = BoulderData.node(child);
                BlockPos a = r.world(n.ax, n.ay, n.az);
                level.setBlock(a, BoulderBlocks.rootState(n.index, r.turn), Block.UPDATE_ALL);
                resetHits(level, a);
                roots.add(a);
            }
            for (int child : r.node.children)
                for (var c : BoulderData.node(child).cellList) {
                    BlockPos p = r.world(c.x, c.y, c.z);
                    if (roots.contains(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() == BoulderBlocks.cell()) continue;
                    if (had.contains(p) || c.y >= 0 && Earthwork.open(s))
                        level.setBlock(p, BoulderBlocks.cell().defaultBlockState(), Block.UPDATE_ALL);
                }
            for (BlockPos p : old) clearIfEmpty(level, p, roots);
        });
    }

    /**
     * A struck piece breaks: its blocks go (but the cells another piece still fills), field stones drop at the struck point
     * (clamp(round(volume x 20), 1, 6), docs 3.4), and the ground takes the rock's room (Earthwork#hollow); the stones it
     * dropped.
     */
    static int shatter(ServerLevel level, BoulderIndex.Root r, Vec3 at, Vec3 toward) {
        var n = r.node;
        Long2IntOpenHashMap bottom = new Long2IntOpenHashMap(), top = new Long2IntOpenHashMap();
        for (int j = n.vj0(); j <= n.vj1(); j++)
            for (int i = n.vi0(); i <= n.vi1(); i++) {
                int t = n.top(i, j);
                if (t == BoulderData.NONE) continue;
                int vx = r.ox * EarthGrid.PER_BLOCK + 2 + BoulderData.turnX(r.turn, i - 2, j - 2);
                int vz = r.oz * EarthGrid.PER_BLOCK + 2 + BoulderData.turnZ(r.turn, i - 2, j - 2);
                long k = EarthGrid.key(vx, vz);
                bottom.put(k, r.oy * EarthGrid.Q + n.bottom(i, j));
                top.put(k, r.oy * EarthGrid.Q + t);
            }
        List<BlockPos> old = blocks(level, r);
        Earthwork.quietly(() -> {
            level.setBlock(r.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (BlockPos p : old) clearIfEmpty(level, p, Set.of());
        });
        int stones = Math.max(1, n.stones);   // clamp(round(volume x 20), 1, 6), tools/boulders/export.mjs
        // the stones fall out of the face that was struck, a little towards the striker
        Vec3 out = toward.subtract(at).multiply(1, 0, 1);
        Vec3 drop = at.add(out.lengthSqr() > 1e-6 ? out.normalize().scale(0.3) : Vec3.ZERO).add(0, 0.1, 0);
        for (int i = 0; i < stones; i++) {
            ItemEntity e = new ItemEntity(level, drop.x, drop.y, drop.z, new ItemStack(AflItems.FIELD_STONE.get()));
            e.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.15, 0.15, (level.random.nextDouble() - 0.5) * 0.15);
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        Earthwork.hollow(level, bottom, top);
        return stones;
    }

    /** A cell of a rock that went: a rock cell while another rock fills it, else air (a root no longer there: the same). */
    private static void clearIfEmpty(ServerLevel level, BlockPos p, Set<BlockPos> keep) {
        if (keep.contains(p)) return;
        BlockState s = level.getBlockState(p);
        if (!BoulderBlocks.isBoulder(s) && !s.isAir()) return;
        if (s.getBlock() == BoulderBlocks.root()) return;   // another rock's root
        boolean filled = BoulderEarth.rock(level, p);
        if (filled && s.getBlock() != BoulderBlocks.cell()) level.setBlock(p, BoulderBlocks.cell().defaultBlockState(), Block.UPDATE_ALL);
        else if (!filled && !s.isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void resetHits(ServerLevel level, BlockPos p) {
        if (level.getBlockEntity(p) instanceof BoulderBlocks.Hits h) h.reset();
    }

    /** The development info of a root (English): form, node, stage, strikes taken of its strikes, pieces it splits into. */
    public static String describe(ServerLevel level, BoulderIndex.Root r) {
        var n = r.node;
        int hits = level.getBlockEntity(r.pos()) instanceof BoulderBlocks.Hits h ? h.hits() : 0;
        int depth = 0;
        for (int p = n.parent; p >= 0; p = BoulderData.node(p).parent) depth++;
        String form = BoulderData.get().forms[n.form].id;
        return String.format(java.util.Locale.ROOT, "%s node %d (stage %d), %.2f m3, %d / %d strikes, %s, turn %d, root %d %d %d",
                form, n.index, depth, n.volume, hits, n.strikes, n.leaf() ? "breaks into " + n.stones + " field stones"
                        : "splits into " + n.children.length + " pieces", r.turn, r.x, r.y, r.z);
    }
}
