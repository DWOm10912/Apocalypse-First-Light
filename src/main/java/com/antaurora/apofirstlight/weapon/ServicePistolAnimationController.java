package com.antaurora.apofirstlight.weapon;

import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.util.Map;

/** Read-only render timing from the existing one-shot controller, not a second action state machine. */
public final class ServicePistolAnimationController extends AnimationController<ServicePistolItem> {
    private double reloadSeconds = -1;

    public ServicePistolAnimationController(ServicePistolItem item) {
        super(item, ServicePistolItem.CONTROLLER, 0, state -> PlayState.STOP);
    }

    @Override
    public void process(CoreGeoModel<ServicePistolItem> model, AnimationState<ServicePistolItem> state,
                        Map<String, CoreGeoBone> bones, Map<String, BoneSnapshot> snapshots,
                        double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        reloadSeconds = getTriggeredAnimation() != null && getAnimationState() != State.STOPPED
                && getCurrentAnimation() != null
                && getCurrentAnimation().animation().name().equals("animation.service_pistol.reload")
                ? getAnimationSpeed() * Math.max(seekTime - tickOffset, 0) / 20D : -1;
    }

    public double getReloadSeconds() { return reloadSeconds; }
}
