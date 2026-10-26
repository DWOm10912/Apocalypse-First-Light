package com.antaurora.apofirstlight.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 4): a lightning rod. A strike's rolling sphere (world/LightningAim,
 * world/StrikePoint) counts its tip strike.rod_reach higher than it stands, so it takes the strikes that would have landed
 * near it; the bolt ends on the tip and lights no fire.
 */
public interface LightningRodTip {
    /** The tip's height (blocks) above this cell's floor; NaN where this cell does not hold the tip. */
    double tip(BlockState state);
}
