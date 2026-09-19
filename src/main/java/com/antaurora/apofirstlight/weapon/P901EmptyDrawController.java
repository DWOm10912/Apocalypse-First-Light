package com.antaurora.apofirstlight.weapon;

import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.util.Map;

/** During an empty P9 draw, retain only the authored locked-back slide pose. */
final class P901EmptyDrawController extends AnimationController<P901Item> {
    P901EmptyDrawController(P901Item item, P901AnimationController action) {
        super(item, "empty_draw_slide", 0, state -> {
            var stack = state.getData(DataTickets.ITEMSTACK);
            return stack != null && NativeGunAmmo.read(stack, item.definition()) == 0 && action.isDrawPlaying()
                    ? state.setAndContinue(RawAnimation.begin().thenPlayAndHold("empty_idle")) : PlayState.STOP;
        });
    }

    @Override public void process(CoreGeoModel<P901Item> model, AnimationState<P901Item> state,
                                  Map<String, CoreGeoBone> bones, Map<String, BoneSnapshot> snapshots,
                                  double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        // GeckoLib applies each controller's bone queues in registration order. Keep only
        // the real slide bone; empty_idle's other channels must not replace the draw pose.
        if (getAnimationState() == State.STOPPED) getBoneAnimationQueues().clear();
        else getBoneAnimationQueues().keySet().removeIf(name -> !"slide".equals(name));
    }
}
