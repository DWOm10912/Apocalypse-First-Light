package com.antaurora.apofirstlight.worldgen.underground;

/**
 * The underground plan a deep-world noise router cuts into its chunks (U2, docs/worldgen/underground_geology_caves_v0_design.md
 * section 20). The router gets an empty ref when its final density asks for planned caves ({@code "planned_caves": true}
 * on plan_terrain); UndergroundRuntime fills it at world load, after the version lock bound the roads, towns and home
 * the plan keeps clear of. Empty: the underground stays solid rock.
 */
public final class UndergroundRef {
    private volatile UndergroundPlan plan;

    public UndergroundPlan get() { return plan; }

    public void set(UndergroundPlan plan) { this.plan = plan; }
}
