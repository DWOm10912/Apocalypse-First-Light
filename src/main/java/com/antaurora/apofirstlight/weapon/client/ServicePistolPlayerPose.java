package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/** Forge's item-scoped two-handed pose runs inside setupAnim, before sleeves/held-item layers. */
public final class ServicePistolPlayerPose {
    public static final HumanoidModel.ArmPose PISTOL = HumanoidModel.ArmPose.create(
            "AFL_SERVICE_PISTOL", true, (model, entity, arm) -> {
                if (!(entity instanceof Player) || !(entity.getMainHandItem().getItem() instanceof ServicePistolItem)) return;
                apply(model, arm, entity.isCrouching());
            });

    private ServicePistolPlayerPose() {}

    public static void apply(HumanoidModel<?> model, HumanoidArm mainArm, boolean crouching) {
        boolean right = mainArm == HumanoidArm.RIGHT;
        var main = right ? model.rightArm : model.leftArm;
        var support = right ? model.leftArm : model.rightArm;
        float sign = right ? 1 : -1;
        // Compensate vanilla's subsequent crouch addition, keeping aim tied to the head.
        float pitch = -(float)Math.PI / 2 + model.head.xRot - (crouching ? 0.4F : 0);
        main.xRot = pitch;
        support.xRot = pitch + 0.08F;
        main.yRot = model.head.yRot - sign * 0.25F;
        support.yRot = model.head.yRot + sign * 0.55F;
        main.zRot = sign * 0.02F;
        support.zRot = -sign * 0.04F;
    }
}
