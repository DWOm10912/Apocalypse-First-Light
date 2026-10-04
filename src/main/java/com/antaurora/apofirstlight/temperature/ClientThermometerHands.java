package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * How the clinical thermometer is held while measuring: tucked under the armpit. First person: the hand with the
 * thermometer drops and turns in toward the body, out of view, within about 0.4 s. Third person: that arm comes across
 * the chest to the other armpit (poseArm, from mixin client/HumanoidModelThermometerMixin after vanilla's pose; never a
 * custom ArmPose enum value, which breaks vanilla's arm-pose switches when made at run time). Not using it: vanilla.
 */
public final class ClientThermometerHands implements IClientItemExtensions {
    /** Ticks until the thermometer is tucked away in first person. */
    private static final float TUCK_TICKS = 8;
    public static final ClientThermometerHands INSTANCE = new ClientThermometerHands();
    private ClientThermometerHands() {}

    private static boolean measuring(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack && stack.is(AflItems.CLINICAL_THERMOMETER.get());
    }

    /** The measuring arm across the chest to the other armpit (inward yaw like the shield pose), elbow a little up. */
    public static void poseArm(HumanoidModel<?> model, LivingEntity entity) {
        if (!measuring(entity, entity.getUseItem())) return;
        var main = entity.getMainArm();
        var arm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? main : main.getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        var limb = right ? model.rightArm : model.leftArm;
        limb.xRot = -0.75F;
        limb.yRot = right ? -0.65F : 0.65F;
        limb.zRot = right ? 0.1F : -0.1F;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        if (!measuring(player, stack)) return false;
        float used = stack.getUseDuration() - player.getUseItemRemainingTicks() + partialTick;
        float t = Mth.clamp(used / TUCK_TICKS, 0, 1);
        t = t * t * (3 - 2 * t);
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // vanilla's resting hand (applyItemArmTransform) moved down and in, turned toward the body
        pose.translate(side * (0.56F - 0.40F * t), -0.52F - 0.70F * t, -0.72F + 0.12F * t);
        pose.mulPose(Axis.ZP.rotationDegrees(side * 40 * t));
        pose.mulPose(Axis.XP.rotationDegrees(-20 * t));
        return true;
    }
}
