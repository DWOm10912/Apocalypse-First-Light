package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Which Fluid Pipe V2 pieces (tools/build-fluid-pipe-v2.mjs) make one pipe block: shared by its client model
 * (client/FluidPipeBakedModel) and its hit mesh (meshhit/MeshHitModels, docs/rendering/mesh_hit_runtime_v1.md), so the
 * two always agree. The key holds, per direction, NONE / PIPE / PORT (2 bits each), then the band side (3 bits: 6 = a
 * band, 7 = nothing, else a wall clamp toward that side). Selection: none -> the fitting with glass on all faces; one -> an
 * end (blind flange); two opposite -> two halves and, every third block along the run (by world coordinate), a band, or a
 * clamp toward a solid face beside it (floor first, then walls, then ceiling); else the fitting, an arm per link and glass
 * on its closed faces. Steel pieces have their name, glass pieces {@code <name>_glass}.
 */
public final class FluidPipePieces {
    public static final Direction[] ORDER = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] CLAMP_PREFERENCE = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
    private static final int BAND_SHIFT = 12;
    private static final int BAND = 6;
    private static final int NOTHING = 7;
    /** A band (or clamp) every this many blocks along a run, by world coordinate along it, so spacing stays even. */
    private static final int BAND_SPACING = 3;

    private FluidPipePieces() {
    }

    public static int key(BlockGetter level, BlockPos pos, BlockState state) {
        int key = 0;
        int connected = 0;
        Direction first = null;
        for (Direction direction : ORDER) {
            FluidPipeBlock.Link link = FluidPipeBlock.link(level, pos, state, direction);
            key |= link.ordinal() << (2 * direction.ordinal());
            if (link != FluidPipeBlock.Link.NONE) {
                connected++;
                if (first == null) first = direction;
            }
        }
        int band = NOTHING;
        if (connected == 2 && state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(first.getOpposite()))
                && Math.floorMod(first.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()), BAND_SPACING) == 0) {
            band = BAND;
            for (Direction side : CLAMP_PREFERENCE) {
                if (side.getAxis() == first.getAxis()) continue;
                BlockPos neighbor = pos.relative(side);
                if (level.getBlockState(neighbor).isFaceSturdy(level, neighbor, side.getOpposite())) {
                    band = side.ordinal();
                    break;
                }
            }
        }
        return key | band << BAND_SHIFT;
    }

    /** Without level data (e.g. a lone state lookup) every connected side counts as a pipe. */
    public static int keyFromState(BlockState state) {
        int key = 0;
        for (Direction direction : ORDER) {
            if (state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction))) key |= 1 << (2 * direction.ordinal());
        }
        return key | NOTHING << BAND_SHIFT;
    }

    private static FluidPipeBlock.Link linkOf(int key, Direction direction) {
        return FluidPipeBlock.Link.values()[(key >> (2 * direction.ordinal())) & 3];
    }

    private static String suffix(FluidPipeBlock.Link link) {
        return link == FluidPipeBlock.Link.PORT ? "port" : "pipe";
    }

    /** The steel pieces' names for a key (each may have a {@code _glass} companion). */
    public static List<String> pieceNames(int key) {
        List<Direction> connected = new ArrayList<>();
        for (Direction direction : ORDER) if (linkOf(key, direction) != FluidPipeBlock.Link.NONE) connected.add(direction);
        List<String> names = new ArrayList<>();
        if (connected.isEmpty()) {
            names.add("box");
            for (Direction d : ORDER) names.add("box_glass_" + d.getName());
        } else if (connected.size() == 1) {
            Direction d = connected.get(0);
            names.add("end_" + suffix(linkOf(key, d)) + "_" + d.getName());
        } else if (connected.size() == 2 && connected.get(0).getOpposite() == connected.get(1)) {
            for (Direction d : connected) names.add("half_" + suffix(linkOf(key, d)) + "_" + d.getName());
            int band = (key >> BAND_SHIFT) & 7;
            String axis = connected.get(0).getAxis().getName();
            if (band == BAND) names.add("band_" + axis);
            else if (band != NOTHING) names.add("clamp_" + axis + "_" + Direction.values()[band].getName());
        } else {
            names.add("box");
            for (Direction d : ORDER) {
                if (connected.contains(d)) names.add("arm_" + suffix(linkOf(key, d)) + "_" + d.getName());
                else names.add("box_glass_" + d.getName());
            }
        }
        return names;
    }
}
