package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer;
import com.antaurora.apofirstlight.weapon.client.ServicePistolRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** DEV-only read-only per-bone matrix/vertex probe, not a rendered preview. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class NativeGunArmTrace {
    @SubscribeEvent
    public static void layers(GeoRenderEvent.Item.CompileRenderLayers event) {
        if (!Boolean.getBoolean("afl.debug.nativeGunPresentation")) return;
        if (event.getRenderer() instanceof ServicePistolRenderer renderer) event.addLayer(new Probe(renderer));
    }

    private static final class Probe extends GeoRenderLayer<ServicePistolItem> {
        private final ServicePistolRenderer renderer;
        private final long[] next={0,0};
        private final int[] samples={0,0};
        Probe(ServicePistolRenderer renderer) { super(renderer); this.renderer=renderer; }
        @Override
        public void renderForBone(PoseStack pose,ServicePistolItem item,GeoBone bone,RenderType type,
                                  MultiBufferSource buffers,VertexConsumer buffer,float partialTick,int light,int overlay) {
            var mc=Minecraft.getInstance();
            if (!renderer.isFirstPersonPass() || mc.player==null || mc.isPaused() || mc.player.isInvisible()
                    || !(mc.player.getMainHandItem().getItem() instanceof ServicePistolItem)) return;
            boolean right=bone.getName().equals("right_hand_anchor");
            if (!right && !bone.getName().equals("left_hand_anchor")) return;
            int side=right ? 1 : 0;
            long now=System.nanoTime();
            if (samples[side]>=120 || now<next[side]) return;
            next[side]=now+250_000_000L; samples[side]++;
            if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer skin)) return;
            var anchor=NativeGunArmChecks.copy(pose);
            RenderUtils.translateToPivotPoint(anchor,bone);
            boolean slim=mc.player.getModelName().equals("slim");
            NativeGunArmChecks.checkMapping(anchor,right,slim);
            var old=NativeGunArmChecks.copy(anchor);
            NativeGunArmChecks.legacyMapping(old,right,slim);
            ServicePistolHandLayer.orientAtHandTip(anchor,right,slim);
            var part=right ? skin.getModel().rightArm : skin.getModel().leftArm;
            var sleeve=right ? skin.getModel().rightSleeve : skin.getModel().leftSleeve;
            var baseBounds=NativeGunArmChecks.vertices(part,anchor);
            var sleeveBounds=NativeGunArmChecks.vertices(sleeve,anchor);
            var oldBounds=NativeGunArmChecks.vertices(sleeve,old);
            ApocalypseFirstLight.LOGGER.info("[AFL ARM V043B LIVE] right={} skin={} playerSkin={} sleeveEnabled={} armVisualScale={} baseVertices={} sleeveVertices={} oldNearZ={} newNearZ={} reloadSeconds={}",
                    right,mc.player.getModelName(),mc.player.getSkinTextureLocation(),
                    mc.player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE),
                    ServicePistolHandLayer.ARM_VISUAL_SCALE,
                    baseBounds.vertices,sleeveBounds.vertices,oldBounds.maxZ,sleeveBounds.maxZ,renderer.getReloadSeconds());
        }
    }
}
