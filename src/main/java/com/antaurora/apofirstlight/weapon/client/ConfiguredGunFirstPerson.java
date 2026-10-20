package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Configured rigs own both FP hand passes, without the pistol's composition offsets. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ConfiguredGunFirstPerson {
    @SubscribeEvent public static void render(RenderHandEvent e) {
        var mc = Minecraft.getInstance(); var p = mc.player;
        if (p == null || !(p.getMainHandItem().getItem() instanceof ConfiguredNativeGunItem)) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || p.isSpectator() || p.isScoping()) return;
        boolean right = p.getMainArm() == HumanoidArm.RIGHT;
        var pose = NativeRenderMatrices.detachedCopy(e.getPoseStack());
        // looking down at the ready the view model slides out of view and the body's arms take the gun
        // (client/FirstPersonBody#gunDrop, docs/rendering/first_person_body_v1.md 3.5.1); still drawn for the shot snapshot
        float drop = FirstPersonBody.gunDrop();
        if (drop > 0F) pose.translate(0F, -drop, 0F);
        if(!FieldAttachmentViewState.isActive()){
            NativeWeaponSway.apply(pose,e.getPartialTick());
            NativeGunRecoil.applyViewmodel(pose);
            NativeGunAds.apply(pose, right, e.getPartialTick());
        }
        FieldAttachmentTransform.apply(pose,right,e.getPartialTick());
        NativeCollimatedReticleRendering.begin();
        try {
            mc.getItemRenderer().renderStatic(p, p.getMainHandItem(), right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !right, pose, e.getMultiBufferSource(), p.level(),
                    e.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, p.getId());
            NativeCollimatedReticleRendering.draw(e.getMultiBufferSource());
        } finally {
            NativeCollimatedReticleRendering.end();
        }
    }
}
