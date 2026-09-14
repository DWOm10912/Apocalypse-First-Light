package com.antaurora.apofirstlight.worldgen.spatial;

/** Candidate reservations are reconstructible; only the other three stages may enter a finite index. */
public enum ClaimStage { CANDIDATE_RESERVED, ACCEPTED_PLAN, COMMITTED, PARTIAL }
