package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.NativeAnimatedWeaponRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.event.GeoRenderEvent;

/** DEV-only bounded text trace of the actual rendered animation instance, never screenshots. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunPresentationTrace {
    private static long nextLog;

    @SubscribeEvent
    public static void rendered(GeoRenderEvent.Item.Post event) {
        if (!Boolean.getBoolean("afl.debug.nativeGunPresentation")) return;
        if (!(event.getRenderer() instanceof NativeAnimatedWeaponRenderer<?> renderer)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.screen != null
                || !mc.options.getCameraType().isFirstPerson()) return;
        var stack = renderer.getCurrentItemStack();
        if (stack == null || stack != mc.player.getMainHandItem()
                || stack.getItem() != AflItems.P9_01.get()
                || !(stack.getItem() instanceof ConfiguredNativeGunItem item)) return;
        var controller = item.getAnimatableInstanceCache().getManagerForId(GeoItem.getId(stack))
                .getAnimationControllers().get(NativeGunItem.ACTION_CONTROLLER);
        if (controller == null || controller.getTriggeredAnimation() == null || System.nanoTime() < nextLog) return;
        nextLog = System.nanoTime() + 150_000_000L;
        var handling = renderer.getGeoModel().getBone("handling").orElseThrow();
        var mag = renderer.getGeoModel().getBone("magazine").orElseThrow();
        var left = renderer.getGeoModel().getBone("left_hand_anchor").orElseThrow();
        var slide = renderer.getGeoModel().getBone("slide").orElseThrow();
        ApocalypseFirstLight.LOGGER.info("[AFL P9 V2 PRESENTATION] renderedId={} heldId={} controller={} animation={} handlingZ={} magY={} magScale={} leftY={} slideZ={} skin={}",
                GeoItem.getId(stack), GeoItem.getId(mc.player.getMainHandItem()), controller.getAnimationState(),
                controller.getCurrentAnimation() == null ? "pending" : controller.getCurrentAnimation().animation().name(),
                handling.getRotZ(), mag.getPosY(), mag.getScaleY(), left.getPosY(), slide.getPosZ(), mc.player.getModelName());
    }
}
