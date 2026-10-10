package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.NozzleFillView;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: a held fuel nozzle is not drawn in the hand while it is in a fuel opening (or on its way): the dispenser's
 * renderer draws it there (client/NozzleFillView, 2026-10-10).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerNozzleMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void afl$nozzleInOpening(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
                                     MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (stack.getItem() instanceof FuelNozzleItem && NozzleFillView.hidesHeldNozzle(entity)) ci.cancel();
    }
}
