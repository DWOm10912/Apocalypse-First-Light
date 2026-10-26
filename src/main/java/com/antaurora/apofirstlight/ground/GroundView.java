package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.worldgen.network.road.RoadSurfaceQuery;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground's geometry for everything that is not the ground (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md
 * 6; B's physical water first): one query over Terrain Smooth's slopes, Earthwork's edited columns, carved and worn rock
 * and every other block's collision shape, so a caller never reads those four itself and the rework can change them
 * underneath.
 * <ul>
 *   <li>Units: slabs of 1/16 m; area 1/256 m2 (256 to a column); a slab of area is 1/4096 m3 = 0.244140625 L, one
 *   earthwork quantum; width 1/16 m (16 to an edge).</li>
 *   <li>A slab's value is the integral mean over the slab (its open volume over its thickness), rounded by the running
 *   total from y0, so any run of slabs is within one unit of the exact value.</li>
 *   <li>Solid: natural ground as drawn (skins and slopes integrated over their triangles, carved and worn rock by 1/8 m
 *   sub-cells), any other block by its collision boxes. Air, plants, fluids (AFL water and vanilla) are open.</li>
 *   <li>A column whose chunk is not loaded: UNKNOWN in every slab (treat it as a wall).</li>
 *   <li>Threads: the level's own. A view caches nothing between calls; make one per caller and thread.</li>
 * </ul>
 */
public interface GroundView {
    int SLABS = GroundGeometry.SLABS;
    int AREA = GroundGeometry.AREA;
    int WIDTH = GroundGeometry.WIDTH;
    int UNKNOWN = GroundGeometry.UNKNOWN;
    /** surfaceGrid: no ground down to the bottom */
    int NONE = GroundGeometry.NONE;
    /** surfaceGrid: the column is not loaded */
    int UNKNOWN_HEIGHT = GroundGeometry.UNKNOWN_HEIGHT;

    /**
     * Open (not solid) area of column (x, z) in every 1/16 m slab from block y0 (inclusive) to y1 (exclusive), in 1/256 m2,
     * out[(y - y0) * 16 + s], 0..256.
     */
    void openArea(int x, int z, int y0, int y1, short[] out);

    /**
     * Open width (1/16 m, 0..16) per 1/16 m slab of the edge column (x, z) shares with its neighbour toward dir
     * (horizontal), out[(y - y0) * 16 + s]: the edge face's open area over the slab's thickness, a point of the face open
     * when both columns are open there. Asked from either side, the same numbers.
     */
    void openEdge(int x, int z, Direction dir, int y0, int y1, byte[] out);

    /**
     * The top (blocks) of the highest solid whose bottom is at or below y on the vertical line at (px, pz): the ground a
     * point falls onto, or the top of the ground it is inside. NaN when there is none down to the bottom or the column is
     * not loaded. The surface as drawn and stood on, not the 8 x 8 collision boxes.
     */
    double surfaceBelow(double px, double y, double pz);

    /**
     * surfaceBelow for a whole chunk: the ground height in quanta (1/256 m) at the 65 x 65 vertices (0.25 m apart) of
     * chunk (chunkX, chunkZ), out[vz * 65 + vx], going down from the top of block yFrom; the east and south row are the
     * neighbour chunks' first vertices, so two chunks' grids meet. NONE where there is no ground, UNKNOWN_HEIGHT where the
     * column is not loaded.
     */
    void surfaceGrid(int chunkX, int chunkZ, int yFrom, int[] out);

    /**
     * What the ground at the block is made of; GroundMaterial.NONE where it is not natural ground, a road or loaded. A road's
     * pavement: ASPHALT, CONCRETE (curbs, sidewalks, aprons, a bridge's or tunnel's structure) or COMPACTED_GRAVEL (the
     * forest home's driveway), as the vehicle query reads it; graded turf: topsoil (road drainage D1).
     */
    GroundMaterial materialAt(int x, int y, int z);

    /** A view of the level's ground (its own side: the server's data on the server, the client's copies on a client). */
    static GroundView of(Level level) {
        return new Impl(new LevelGround(level));
    }

    final class Impl implements GroundView {
        private final LevelGround ground;
        private final GroundGeometry geometry;

        Impl(LevelGround ground) {
            this.ground = ground;
            this.geometry = new GroundGeometry(ground);
        }

        @Override
        public void openArea(int x, int z, int y0, int y1, short[] out) { geometry.openArea(x, z, y0, y1, out); }

        @Override
        public void openEdge(int x, int z, Direction dir, int y0, int y1, byte[] out) {
            int d = switch (dir) {
                case NORTH -> GroundGeometry.NORTH;
                case SOUTH -> GroundGeometry.SOUTH;
                case WEST -> GroundGeometry.WEST;
                case EAST -> GroundGeometry.EAST;
                default -> throw new IllegalArgumentException("not a horizontal direction: " + dir);
            };
            geometry.openEdge(x, z, d, y0, y1, out);
        }

        @Override
        public double surfaceBelow(double px, double y, double pz) { return geometry.surfaceBelow(px, y, pz); }

        @Override
        public void surfaceGrid(int chunkX, int chunkZ, int yFrom, int[] out) { geometry.surfaceGrid(chunkX, chunkZ, yFrom, out); }

        @Override
        public GroundMaterial materialAt(int x, int y, int z) {
            BlockState s = ground.state(x, y, z);
            if (s == null) return GroundMaterial.NONE;
            Block b = s.getBlock();
            if (b instanceof RoadStructureBlock) return GroundMaterial.CONCRETE;
            if (b instanceof GradedTurfBlock) return GroundMaterial.bulk("topsoil");
            if (b instanceof RoadSurfaceBlock) {
                // the pavement's kind at the column's centre; a deck's edge strip (no design over it) is asphalt, as for wheels
                RoadSurfaceQuery.Surface k = RoadSurfaceQuery.kind(RoadStructureBlock.boundWorks(), x + 0.5, z + 0.5, y + s.getValue(RoadSurfaceBlock.LAYERS) / 32.0);
                return k == RoadSurfaceQuery.Surface.CONCRETE ? GroundMaterial.CONCRETE
                        : k == RoadSurfaceQuery.Surface.GRAVEL ? GroundMaterial.COMPACTED_GRAVEL : GroundMaterial.ASPHALT;
            }
            return GroundMaterial.of(b);
        }
    }
}
