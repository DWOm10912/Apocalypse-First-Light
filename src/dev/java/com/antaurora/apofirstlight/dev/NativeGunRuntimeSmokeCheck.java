package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.ServicePistolActions;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.network.packet.StopTriggeredSingletonAnimPacket;

/** DEV-only, runs once after resource reload; never renders, clicks or captures anything. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunRuntimeSmokeCheck {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || event.phase != TickEvent.Phase.END || mc.getOverlay() != null || mc.screen == null) return;
        checked = true;
        try {
            check(AflItems.SERVICE_PISTOL.get() instanceof ServicePistolItem, "item registry");
            var geo = GeckoLibCache.getBakedModels().get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/service_pistol.geo.json"));
            check(geo != null, "GeckoLib baked model");
            check(geo.getBone("right_arm_reference").isEmpty() && geo.getBone("left_arm_reference").isEmpty(), "reference exclusion");
            for (String anchor : new String[]{"right_hand_anchor", "left_hand_anchor", "muzzle_anchor", "ejection_anchor", "sight_anchor"})
                check(geo.getBone(anchor).isPresent(), "anchor " + anchor);
            var animations = GeckoLibCache.getBakedAnimations().get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "animations/service_pistol.animation.json"));
            check(animations != null, "GeckoLib baked animations");
            checkPresentation(mc);
            NativeGunArmChecks.checkGeometry(mc);
            NativeGunMatrixChecks.verify(mc,(ServicePistolItem)AflItems.SERVICE_PISTOL.get());
            checkReloadController((ServicePistolItem)AflItems.SERVICE_PISTOL.get());
            check(EasingType.fromString("afl_hold") != EasingType.LINEAR, "registered hold easing");
            check(EasingType.fromString("afl_hold").buildTransformer(null).apply(0.99) == 0, "hold until endpoint");
            for (boolean reload : new boolean[]{false, true}) {
                String name = ServicePistolActions.animationName(reload);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new StopTriggeredSingletonAnimPacket("afl-smoke", 123L, ServicePistolItem.CONTROLLER, name).encode(buffer);
                    check(buffer.readUtf().equals("afl-smoke"), "stop packet identity");
                    check(buffer.readVarLong() == 123L, "stop packet instance");
                    check(buffer.readUtf().equals(ServicePistolItem.CONTROLLER), "stop packet controller");
                    check(buffer.readUtf().equals(name) && !buffer.isReadable(), "non-null stop packet action");
                } finally { buffer.release(); }
            }
            ApocalypseFirstLight.LOGGER.info("[AFL NATIVE GUN SMOKE] PASS: baked model/animations, anchors, source-only arms, hold easing and fire/reload stop packet encoding");
        } catch (Throwable failure) {
            ApocalypseFirstLight.LOGGER.error("[AFL NATIVE GUN SMOKE] FAIL", failure);
        }
    }

    private static void check(boolean pass, String label) {
        if (!pass) throw new IllegalStateException(label);
    }

    private static void checkPresentation(Minecraft mc) {
        var model = mc.getModelManager().getModel(new net.minecraft.client.resources.model.ModelResourceLocation(
                ApocalypseFirstLight.MOD_ID, "service_pistol", "inventory"));
        for (var context : new net.minecraft.world.item.ItemDisplayContext[]{net.minecraft.world.item.ItemDisplayContext.GUI,
                net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND}) {
            var selected = model.applyTransform(context, new com.mojang.blaze3d.vertex.PoseStack(), false);
            check(selected.isCustomRenderer() == (context != net.minecraft.world.item.ItemDisplayContext.GUI), "model route " + context);
            if (context == net.minecraft.world.item.ItemDisplayContext.GUI)
                check(!selected.getQuads(null, null, net.minecraft.util.RandomSource.create()).isEmpty(), "non-empty GUI icon");
        }
        for (boolean slim : new boolean[]{false, true}) {
            var skinRoot = mc.getEntityModels().bakeLayer(slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER);
            for (boolean right : new boolean[]{false, true}) {
                var cube = skinRoot.getChild(right ? "right_arm" : "left_arm").getRandomCube(net.minecraft.util.RandomSource.create());
                check(cube.maxX - cube.minX == (slim ? 3 : 4), "skin arm width");
                var pose = new com.mojang.blaze3d.vertex.PoseStack();
                com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.orientAtHandTip(pose, right, slim);
                var tip = pose.last().pose().transformPosition(new org.joml.Vector3f(
                        com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.palmContactX(right, slim) / 16,
                        com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.palmY(right) / 16F, 0));
                var expected = com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.contactOffset(right).div(16);
                check(tip.distance(expected) < 0.00001, "skin palm follows anchor-local contact exactly");
                checkReadyForearm(mc, right, slim);
            }
            var playerModel = new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.player.Player>(skinRoot, slim);
            com.antaurora.apofirstlight.weapon.client.ServicePistolPlayerPose.apply(playerModel, net.minecraft.world.entity.HumanoidArm.RIGHT, false);
            check(playerModel.rightArm.xRot < -1 && playerModel.leftArm.xRot < -1, "both third-person arms raised");
        }
        for (double seconds : new double[]{-1, 0, 1.18, 1.3, 10})
            check(com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.reloadWeight(seconds) == 0, "presentation returns exactly to ready");
        for (int sample = 0; sample <= 1300; sample++) {
            float weight = com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.reloadWeight(sample / 1000D);
            check(weight >= 0 && weight <= 1, "bounded side-open curve");
        }
        var opened = new com.mojang.blaze3d.vertex.PoseStack();
        com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.applyReload(opened, 1);
        var muzzleDirection = opened.last().pose().transformDirection(new org.joml.Vector3f(0, 0, -1));
        check(muzzleDirection.x < -0.4F, "reload muzzle opens to camera left, not right");
        ApocalypseFirstLight.LOGGER.info("[AFL PRESENTATION SMOKE V042] PASS: static route, Classic+Slim geometry and asymmetric palm contacts, third-person unchanged, bounded side-open curve");
    }

    private static void checkReloadController(ServicePistolItem item) {
        var model = new com.antaurora.apofirstlight.weapon.client.ServicePistolModel();
        model.getBakedModel(model.getModelResource(item));
        var processor = model.getAnimationProcessor();
        var manager = new software.bernie.geckolib.core.animation.AnimatableManager<ServicePistolItem>(item);
        manager.tryTriggerAnimation(ServicePistolItem.CONTROLLER, "reload");
        float maxRoot = 0, maxMag = 0, maxLeft = 0;
        float minMagazineTopNdc = Float.POSITIVE_INFINITY;
        float oldArmNear = Float.NEGATIVE_INFINITY, newArmNear = Float.NEGATIVE_INFINITY;
        var skins = new net.minecraft.client.model.geom.ModelPart[]{
                Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),
                Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM)};
        try {
            for (int sample = 0; sample <= 300; sample++) {
                processor.tickAnimation(item, model, manager, sample / 10D,
                        new software.bernie.geckolib.core.animation.AnimationState<>(item, 0, 0, 0, false), true);
                maxRoot = Math.max(maxRoot, Math.abs(model.getBone("weapon_root").orElseThrow().getRotZ()));
                maxMag = Math.max(maxMag, Math.abs(model.getBone("magazine").orElseThrow().getPosY()));
                maxLeft = Math.max(maxLeft, Math.abs(model.getBone("left_hand_anchor").orElseThrow().getPosY()));
                var controller = (com.antaurora.apofirstlight.weapon.ServicePistolAnimationController)manager.getAnimationControllers().get(ServicePistolItem.CONTROLLER);
                double seconds = controller.getReloadSeconds();
                var armNear = NativeGunArmChecks.animatedNearZ(model,seconds,skins);
                oldArmNear = Math.max(oldArmNear,armNear[0]);
                newArmNear = Math.max(newArmNear,armNear[1]);
                if ((seconds >= 0.30 && seconds <= 0.50) || (seconds >= 0.61 && seconds <= 0.85))
                    minMagazineTopNdc = Math.min(minMagazineTopNdc, projectedMagazineTop(model, seconds));
            }
            check(maxRoot > 0.2 && maxMag > 20 && maxLeft > 30, "real controller evaluates full reload tracks");
            check(Math.abs(model.getBone("magazine").orElseThrow().getPosY()) < 0.01, "reload returns magazine to ready");
            check(Float.isFinite(minMagazineTopNdc), "finite magazine viewport projection");
            ApocalypseFirstLight.LOGGER.info("[AFL ARM V043 RELOAD] oldNearZ={} newNearZ={}; 301 timeline samples, both hands, Classic/Slim sleeves",oldArmNear,newArmNear);
            check(newArmNear < -0.05F, "reload full sleeves stay beyond near plane");
            // V0.4.3 freezes the user-accepted +32 degree gun presentation. Retain
            // the known V0.4.2 frustum finding explicitly; do not move the gun to pass it.
            if (minMagazineTopNdc <= -0.98F)
                ApocalypseFirstLight.LOGGER.warn("[AFL V042 FROZEN FRUSTUM] known magazine-top clipping remains: {}; not a V043 arm acceptance check", minMagazineTopNdc);
            ApocalypseFirstLight.LOGGER.info("[AFL RELOAD CONTROLLER V042] PASS: rootRotation={} magazineTravel={} leftHandTravel={} minimumMagazineTopNdc={}", maxRoot, maxMag, maxLeft, minMagazineTopNdc);
            var fireManager = new software.bernie.geckolib.core.animation.AnimatableManager<ServicePistolItem>(item);
            fireManager.tryTriggerAnimation(ServicePistolItem.CONTROLLER,"fire");
            float fireNear=Float.NEGATIVE_INFINITY, fireRecoil=0;
            for (int sample=0; sample<=40; sample++) {
                processor.tickAnimation(item,model,fireManager,sample/10D,
                        new software.bernie.geckolib.core.animation.AnimationState<>(item,0,0,0,false),true);
                fireNear=Math.max(fireNear,NativeGunArmChecks.animatedNearZ(model,-1,skins)[1]);
                fireRecoil=Math.max(fireRecoil,Math.abs(model.getBone("weapon_root").orElseThrow().getRotX()));
            }
            check(fireNear < -0.05F && fireRecoil > .04F, "fire recoil preserved and full sleeves clear near plane");
            check(Math.abs(model.getBone("weapon_root").orElseThrow().getRotX()) < .001F,"fire ends in ready");
            ApocalypseFirstLight.LOGGER.info("[AFL ARM V043 FIRE] PASS: nearZ={} recoilRadians={}; both hands and both skin widths",fireNear,fireRecoil);
        } finally {
            // The resource cache shares bones; return every part to its pre-test bind pose.
            for (var bone : processor.getRegisteredBones()) {
                var initial = bone.getInitialSnapshot();
                bone.updateRotation(initial.getRotX(), initial.getRotY(), initial.getRotZ());
                bone.updatePosition(initial.getOffsetX(), initial.getOffsetY(), initial.getOffsetZ());
                bone.updateScale(initial.getScaleX(), initial.getScaleY(), initial.getScaleZ());
            }
        }
    }

    private static float projectedMagazineTop(com.antaurora.apofirstlight.weapon.client.ServicePistolModel model, double seconds) {
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.translate(com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_X,
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_Y,
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_Z);
        pose.translate(-6 / 16F, 1 / 16F, 0);
        pose.scale(0.3F, 0.3F, 0.3F);
        pose.translate(0, 0.01, 0);
        com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.applyReload(pose,
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.reloadWeight(seconds));
        for (String name : new String[]{"root", "weapon_root", "gun", "magazine"})
            software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(pose, model.getBone(name).orElseThrow());
        float top = Float.NEGATIVE_INFINITY;
        for (var cube : model.getBone("magazine").orElseThrow().getCubes()) {
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                for (var vertex : quad.vertices()) {
                    var point = pose.last().pose().transformPosition(new org.joml.Vector3f(vertex.position()));
                    check(point.z < -0.05, "magazine does not cross camera near plane");
                    // Neutral 70-degree held-item projection. This is a numeric
                    // frustum check, not a substitute for the user's visual review.
                    top = Math.max(top, point.y / (-point.z * (float)Math.tan(Math.toRadians(35))));
                }
            }
        }
        return top;
    }

    private static void checkReadyForearm(Minecraft mc, boolean right, boolean slim) {
        var geo = GeckoLibCache.getBakedModels().get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/service_pistol.geo.json"));
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.translate(com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_X,
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_Y,
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_Z);
        pose.translate(-6 / 16F, 1 / 16F, 0);
        pose.scale(0.3F, 0.3F, 0.3F);
        pose.translate(0, 0.01, 0);
        String anchor = right ? "right_hand_anchor" : "left_hand_anchor";
        for (String name : new String[]{"root", "weapon_root", anchor})
            software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(pose, geo.getBone(name).orElseThrow());
        software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(pose, geo.getBone(anchor).orElseThrow());
        NativeGunArmChecks.checkMapping(pose, right, slim);
        var legacy = NativeGunArmChecks.copy(pose);
        NativeGunArmChecks.legacyMapping(legacy, right, slim);
        com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.orientAtHandTip(pose, right, slim);
        float centerX = com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.handCenterX(right, slim);
        float palmY = com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.palmY(right);
        var palm = pose.last().pose().transformPosition(new org.joml.Vector3f(
                com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer.palmContactX(right,slim)/16, palmY / 16, 0));
        var forearmMid = pose.last().pose().transformPosition(new org.joml.Vector3f(centerX / 16, (palmY - 2) / 32, 0));
        float tangent = (float)Math.tan(Math.toRadians(35));
        float palmNdc = palm.y / (-palm.z * tangent);
        float midNdc = forearmMid.y / (-forearmMid.z * tangent);
        check(palmNdc > -1 && palmNdc < 0, "palm contact remains in lower viewport");
        check(midNdc < -1, "forearm centerline majority exits lower viewport");
        var skinRoot = mc.getEntityModels().bakeLayer(slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER);
        var sleeve = skinRoot.getChild(right ? "right_sleeve" : "left_sleeve");
        var oldBounds = NativeGunArmChecks.vertices(sleeve, legacy);
        var newBounds = NativeGunArmChecks.vertices(sleeve, pose);
        check(newBounds.maxZ < -0.05F, "ready complete sleeve stays beyond near plane");
        ApocalypseFirstLight.LOGGER.info("[AFL ARM V043 READY] right={} slim={} oldNearZ={} newNearZ={} vertices={}", right, slim, oldBounds.maxZ, newBounds.maxZ, newBounds.vertices);
        ApocalypseFirstLight.LOGGER.info("[AFL GRIP COMPOSITION] right={} slim={} palmNdcY={} forearmMidNdcY={}", right, slim, palmNdc, midNdc);
    }
}
