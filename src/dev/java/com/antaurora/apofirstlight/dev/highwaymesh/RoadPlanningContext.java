package com.antaurora.apofirstlight.dev.highwaymesh;

import java.util.Optional;
import java.util.OptionalDouble;

/** Future Terrain adapter only. No LandformPlan assumption, terrain mutation, or implicit unknown=clear. */
@FunctionalInterface
public interface RoadPlanningContext {
    Evidence sample(double worldX,double worldZ);
    enum Protection { UNKNOWN, VERIFIED_CLEAR, PROTECTED }
    record Evidence(OptionalDouble macroTargetHeight,OptionalDouble slope,Optional<Boolean> cityCandidate,
                    Optional<Boolean> mountainBelt,Optional<Boolean> riverValley,OptionalDouble suitability,
                    Protection protection,String provenance) {
        public static Evidence unknown(){return new Evidence(OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),Optional.empty(),Optional.empty(),OptionalDouble.empty(),Protection.UNKNOWN,"NO_PLANNING_PROVIDER_IN_M1B");}
    }
    RoadPlanningContext UNAVAILABLE=(x,z)->Evidence.unknown();
}