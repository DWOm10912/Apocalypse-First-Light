package com.antaurora.apofirstlight.client;

import net.minecraft.client.model.HumanoidModel;

/**
 * Pouring from a jerry can, seen by others (third person, 2026-10-05, docs/models/fuel_containers_v1.md): both arms
 * forward and down over the can, the left one reaching across to bear it; they follow the head's pitch a little.
 * item/FuelCanItem gives it while the can is in use.
 */
public final class JerryCanArmPose {
    public static final HumanoidModel.ArmPose POUR = HumanoidModel.ArmPose.create("AFL_POUR_JERRY_CAN", true, (model, entity, arm) -> {
        float pitch = model.head.xRot * 0.35F;
        model.rightArm.xRot = -0.95F + pitch;
        model.rightArm.yRot = -0.22F;
        model.rightArm.zRot = 0.0F;
        model.leftArm.xRot = -1.2F + pitch;
        model.leftArm.yRot = 0.6F;
        model.leftArm.zRot = 0.0F;
    });

    private JerryCanArmPose() {
    }
}
