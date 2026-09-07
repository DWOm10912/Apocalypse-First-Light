package com.antaurora.apofirstlight.weapon.client;

import net.minecraft.client.model.HumanoidModel;

/** Vanilla enum only: safe even after the synthetic ArmPose switch map initializes. */
public final class ServicePistolPlayerPose {
    public static final HumanoidModel.ArmPose PISTOL = HumanoidModel.ArmPose.CROSSBOW_HOLD;
    private ServicePistolPlayerPose() {}
}
