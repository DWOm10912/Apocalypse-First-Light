package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.earthwork.RockCarve;
import com.antaurora.apofirstlight.earthwork.RockTypes;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * U3a-3, rock cut where Rock R2's open cut cannot go (a cave's wall or roof, a cliff's face, a pit's side below the
 * earthwork's reach; docs/worldgen/underground_u3_rocks_ores_design.md 4, 13). Round 2 (user 2026-10-24: a strike must
 * visibly take a piece off, no cube blocks, no vanilla cracks): each strike carves a piece of the rock's
 * {@code strike_litres} (rock_types) out of the rock where it lands (RockCarve: 1/8 m sub-cells, drawn as a smooth
 * surface); a block with nothing left is gone. The stones come off by volume as R2's do (one stone a STONE_LITRES, one
 * pebble a PEBBLE_LITRES, the rest a chance), each block's own rock's stone (RockTypes#stone); an ore rock gives its raw ore
 * by volume too (U3b, OreRockBlock#drops).
 * <p>
 * Not here: a cell of an earthwork-edited column the earthwork still manages (its skin, its pick floor and the lid under
 * it: down to top0 - ROCK_DEPTH - 1), a cell under a boulder. The rock above stands where it is (no roof fall: the
 * structure stability work).
 */
public final class RockMining {
    private RockMining() {}

    /** The Aim#node of a rock cut (Aim#root the block struck). */
    public static final int NODE = -2;
    /** A block of rock, L. */
    public static final int BLOCK_LITRES = 1000;

    /** Whether cell is natural rock the pick may carve (either side; a client without earthwork data: true). */
    public static boolean minable(Level level, BlockPos cell) {
        if (!Earthwork.rock(level.getBlockState(cell).getBlock())) return false;
        if (BoulderBlocks.ready() && BoulderBlocks.isBoulder(level.getBlockState(cell.above()))) return false;
        EarthChunk e = Earthwork.get(level, cell.getX() >> 4, cell.getZ() >> 4);
        if (e == null) return true;
        int col = EarthGrid.column(cell.getX(), cell.getZ());
        return !e.edited(col) || cell.getY() < e.top0(col) - Earthwork.ROCK_DEPTH - 1;
    }

    /** One strike lands on the rock at at: the piece it carves, its effort, chips, dust and sparks (RockStrikeFx), sound and stones. */
    static void strike(ServerPlayer p, ServerLevel level, BlockPos cell, Vec3 at) {
        BlockState state = level.getBlockState(cell);
        RockTypes.Rock rock = RockTypes.of(state.getBlock());
        if (rock == null || !minable(level, cell)) return;
        RockCarve.Cut cut = RockCarve.cut(level, at, p.getLookAngle(), rock.strike(), level.getRandom().nextLong(), RockMining::minable);
        if (cut.removed() == 0) return;
        PlayerStamina.spend(p, StaminaConfig.get().costs.pickStrike, false);
        RockStrikeFx.send(level, at, p.getEyePosition(), state, cut.removed() * RockCarve.SUB_LITRES, cut.emptied() > 0);
        // the stones fall out of the face, a little towards the striker
        Vec3 out = p.getEyePosition().subtract(at);
        Vec3 drop = at.add(out.lengthSqr() > 1e-6 ? out.normalize().scale(0.25) : Vec3.ZERO);
        for (var en : cut.taken().object2IntEntrySet()) {
            double litres = en.getIntValue() * RockCarve.SUB_LITRES;
            com.antaurora.apofirstlight.block.OreRockBlock.drops(level, drop, en.getKey(), litres, BoulderStrike.STONE_LITRES, BoulderStrike.PEBBLE_LITRES);
        }
        RockStrikeFx.sound(level, p, at, state, cut.emptied() > 0);
    }
}
