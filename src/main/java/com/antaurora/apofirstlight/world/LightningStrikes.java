package com.antaurora.apofirstlight.world;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md) on the server. What a strike does to living things is lightning injury V1
 * (health/LightningInjury, docs/gameplay/lightning_injury_v1_plan.md): vanilla's strike (5 damage and fire, again every
 * tick the bolt flashes) is replaced for them by one sorting per bolt (direct, side flash, ground current); other entities
 * (items, vehicles) keep vanilla's. Where it strikes: mixin LightningTargetServerLevelMixin (the tallest thing, a lightning
 * rod first).
 */
public final class LightningStrikes {
    /** The server keeps a bolt this long (ticks), so the client's flash plays out (mixin LightningBoltLifeMixin). */
    public static final int MIN_TICKS = 20;

    private LightningStrikes() {
    }
}
