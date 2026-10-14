package com.antaurora.apofirstlight.worldgen.lock;

/** Thrown while the overworld loads when its generation rules differ from this build's (the world is not opened). */
public final class WorldgenLockException extends RuntimeException {
    public WorldgenLockException(String message) {
        super(message);
    }
}
