package com.antaurora.apofirstlight.vehicle.physics;

/**
 * What the vehicle physics asks of the world (docs/vehicles/first_pickup_v0_design.md section 5): the ground under a point
 * and whether a point of the hull is inside something solid. The game's side is {@link LevelVehicleWorld}; the offline
 * check gives its own. Read only; never loads or generates a chunk.
 */
public interface VehicleWorld {
    /** The ground found under a point. */
    final class Ground {
        /** Surface height (world y) and its upward unit normal. */
        public double height, nx, ny = 1, nz;
        /** Tyre grip on this surface (asphalt 1.0 ... ice 0.15). */
        public double grip = 1;
        /** Something was found between the top and the bottom of the search. */
        public boolean found;
        /** What answered: road, edited earth, smoothed terrain, block (for the debug report). */
        public Source source = Source.NONE;

        public void set(double height, double nx, double ny, double nz, double grip, Source source) {
            this.height = height;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.grip = grip;
            this.source = source;
            found = true;
        }

        public void none() {
            found = false;
            source = Source.NONE;
        }
    }

    enum Source { NONE, ROAD, EARTH, SMOOTH, BLOCK }

    /** How to get a hull point out of something solid: a unit direction and the depth. */
    final class Push {
        public double nx, ny, nz, depth;

        public void set(double nx, double ny, double nz, double depth) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.depth = depth;
        }
    }

    /**
     * The topmost ground at (x, z) between yTop and yBottom (world y): its height, normal and grip. Something whose top
     * is above yTop but whose bottom is not (a wall) counts as ground at its top; something entirely above yTop (a bridge
     * overhead) is skipped. yHint: about where the tyre meets the ground (the hub less the radius; the road layer picks
     * the road surface nearest to it: a bridge's deck, not the road under it). False when the column is not loaded (the
     * wheel keeps its last contact).
     */
    boolean ground(double x, double z, double yHint, double yTop, double yBottom, Ground out);

    /** Whether the point is inside something solid (a block's collision box, another vehicle); `out` says how to get out. */
    boolean solid(double x, double y, double z, Push out);
}
