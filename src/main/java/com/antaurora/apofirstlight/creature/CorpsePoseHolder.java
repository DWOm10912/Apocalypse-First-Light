package com.antaurora.apofirstlight.creature;

import net.minecraft.world.phys.Vec3;

/**
 * A body that falls as a ragdoll and is drawn from the server's snapshots (SurvivorPackets.CorpsePose): a survivor's corpse
 * (survivor/SurvivorCorpse) or a creature's (creature/infected/InfectedCorpse).
 */
public interface CorpsePoseHolder {
    /** Client: a pose from the server (RagdollSnapshot about {@code origin}); an empty last pose: it lies on its back after all. */
    void receivePose(Vec3 origin, float[] data, boolean last);
}
