package com.antaurora.apofirstlight.weapon;

import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.util.Map;

/** Read-only render timing from the existing one-shot controller, not a second action state machine. */
public final class P901AnimationController extends AnimationController<P901Item> {
    private double reloadSeconds = -1;

    public P901AnimationController(P901Item item) {
        super(item, P901Item.CONTROLLER, 0, state -> {
            var stack = state.getData(software.bernie.geckolib.constant.DataTickets.ITEMSTACK);
            if (stack == null) return software.bernie.geckolib.core.object.PlayState.STOP;
            return NativeGunAmmo.read(stack, item.definition()) == 0
                    ? state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin()
                        .thenLoop("animation.p9_01.empty_idle"))
                    : state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin()
                        .thenLoop("animation.p9_01.static_idle"));
        });
    }

    @Override
    public void process(CoreGeoModel<P901Item> model, AnimationState<P901Item> state,
                        Map<String, CoreGeoBone> bones, Map<String, BoneSnapshot> snapshots,
                        double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        reloadSeconds = getTriggeredAnimation() != null && getAnimationState() != State.STOPPED
                && getCurrentAnimation() != null
                && (getCurrentAnimation().animation().name().equals("animation.p9_01.reload")
                    || getCurrentAnimation().animation().name().equals("animation.p9_01.reload_empty"))
                ? getAnimationSpeed() * Math.max(seekTime - tickOffset, 0) / 20D : -1;
    }

    public double getReloadSeconds() { return reloadSeconds; }

    public boolean isEmptyReloadPlaying() {
        return reloadSeconds >= 0 && getCurrentAnimation() != null
                && getCurrentAnimation().animation().name().equals("animation.p9_01.reload_empty");
    }
}
