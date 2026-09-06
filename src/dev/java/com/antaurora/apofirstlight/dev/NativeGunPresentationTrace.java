package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.antaurora.apofirstlight.weapon.client.ServicePistolRenderer;
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
        if (!(event.getRenderer() instanceof ServicePistolRenderer renderer) || !renderer.isFirstPersonPass()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;
        var stack = renderer.getCurrentItemStack();
        var item = (ServicePistolItem)stack.getItem();
        var controller = item.getAnimatableInstanceCache().getManagerForId(GeoItem.getId(stack))
                .getAnimationControllers().get(ServicePistolItem.CONTROLLER);
        if (controller == null || controller.getTriggeredAnimation() == null || System.nanoTime() < nextLog) return;
        nextLog = System.nanoTime() + 150_000_000L;
        var root = renderer.getGeoModel().getBone("weapon_root").orElseThrow();
        var mag = renderer.getGeoModel().getBone("magazine").orElseThrow();
        var left = renderer.getGeoModel().getBone("left_hand_anchor").orElseThrow();
        ApocalypseFirstLight.LOGGER.info("[AFL PRESENTATION V042] renderedId={} heldId={} controller={} animation={} rootZ={} magY={} magScale={} leftY={} skin={} reloadSeconds={} presentationWeight={}",
                GeoItem.getId(stack), GeoItem.getId(mc.player.getMainHandItem()), controller.getAnimationState(),
                controller.getCurrentAnimation() == null ? "pending" : controller.getCurrentAnimation().animation().name(),
                root.getRotZ(), mag.getPosY(), mag.getScaleY(), left.getPosY(), mc.player.getModelName(), renderer.getReloadSeconds(),
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.reloadWeight(renderer.getReloadSeconds()));
    }
}
