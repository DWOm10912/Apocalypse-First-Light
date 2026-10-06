package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import com.antaurora.apofirstlight.weapon.client.NativeRenderMatrices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

/**
 * The jerry can in first person (2026-10-05, docs/models/fuel_containers_v1.md "第一人称动画"; the user asked for a
 * GeckoLib unscrew-and-pour animation with the guns' arms): an independent view model in the hand pass, as the crowbar's
 * (client/CrowbarFirstPerson), in place of the vanilla held item and arm while a pouring can is in the main hand (the
 * off hand is not drawn: both hands are on the can).
 * <ul>
 *   <li>geo/jerry_can_first_person.geo.json: bones only; the can itself is AFL Mesh on them
 *   (meshes/jerry_can_first_person.aflmesh.json, the jerry can's own mesh and atlas, the cap and its lever on their own
 *   bone); animations/jerry_can_first_person.animation.json. All three are exported by
 *   tools/author-jerry-can-first-person.mjs from the Blockbench source src/main/blockbench/jerry_can_first_person.bbmodel.</li>
 *   <li>Clips: idle (carried by the middle handle), pour_start (raised, the cap unscrewed and taken away in the left hand, tipped
 *   to pour over the fill cover: {@link FuelCanItem#POUR_DELAY} ticks, after which the fuel runs; client/JerryCanPourView turns the
 *   view so the spout is over the opening), pour_loop, pour_end (tipped back, the cap
 *   screwed down, lowered). Chosen by whether the player is pouring (using the can).</li>
 *   <li>The hands: NativePlayerArmRenderer as the guns' (their presentation, unscaled), on right_hand / left_hand / left_under_hand,
 *   fixed-turn children of the animated anchors (the cap hand and the hand under the can are separate bones). A left-handed player sees the
 *   whole view model mirrored.</li>
 *   <li>The spout bone's place this frame is kept for the pour stream (client/FuelCanPourJets).</li>
 *   <li>The camera bone turns the view as the guns' does (weapon/client/NativeCameraBoneConsumer: view pitch, yaw, roll =
 *   minus the bone's rotation from its bind pose), so the lift, the stuck cap and the tip carry weight; read as drawn
 *   this frame and applied to the next (a frame late, unseen), mirrored for a left-handed player.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class JerryCanFirstPerson {
    static final ResourceLocation GEO = resource("geo/jerry_can_first_person.geo.json");
    private static final ResourceLocation ANIMATION = resource("animations/jerry_can_first_person.animation.json");
    private static final ResourceLocation TEXTURE = resource("textures/block/fuel_containers.png");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation POUR = RawAnimation.begin().thenPlay("pour_start").thenLoop("pour_loop");
    private static final RawAnimation STOP = RawAnimation.begin().thenPlay("pour_end").thenLoop("idle");
    private static final ViewModel VIEW = new ViewModel();
    @Nullable
    private static Renderer renderer;
    /** The spout's place in the hand pass this frame (blocks, camera space), or null when the view model was not drawn. */
    @Nullable
    private static Vector4f spout;
    private static long spoutFrame;
    /** The camera bone's rotation from its bind pose as last drawn (degrees, author space), and when. */
    private static float cameraX, cameraY, cameraZ;
    private static long cameraFrame;

    private JerryCanFirstPerson() {
    }

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }

    /** A jerry can (the container that pours) in this stack. */
    public static boolean isCan(ItemStack stack) {
        return stack.getItem() instanceof FuelCanItem can && can.pours();
    }

    /** The spout's place in the hand pass (camera space, blocks) as last drawn, if drawn within the last frames. */
    @Nullable
    static Vector4f spout() {
        return spout != null && System.nanoTime() - spoutFrame < 200_000_000L ? spout : null;
    }

    private static final class ViewModel implements GeoAnimatable {
        final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        double ticks;
        boolean right = true, pouring;

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 3, state -> {
                if (pouring) return state.setAndContinue(POUR);
                RawAnimation current = state.getController().getCurrentRawAnimation();
                return state.setAndContinue(current == POUR || current == STOP ? STOP : IDLE);
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object context) {
            return ticks;
        }
    }

    private static final class Model extends GeoModel<ViewModel> {
        @Override
        public ResourceLocation getModelResource(ViewModel view) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(ViewModel view) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(ViewModel view) {
            return ANIMATION;
        }
    }

    private static final class Renderer extends GeoObjectRenderer<ViewModel> {
        @Nullable
        private AflMeshModel mesh;

        Renderer() {
            super(new Model());
            addRenderLayer(new GeoRenderLayer<>(this) {
                @Override
                public void renderForBone(PoseStack pose, ViewModel view, GeoBone bone, RenderType type, MultiBufferSource buffers,
                                          VertexConsumer buffer, float partial, int light, int overlay) {
                    String name = bone.getName();
                    boolean right = name.equals("right_hand");
                    if (name.equals("camera")) {
                        var bind = bone.getInitialSnapshot();
                        cameraX = (float)Math.toDegrees(bone.getRotX() - bind.getRotX());
                        cameraY = (float)Math.toDegrees(bone.getRotY() - bind.getRotY());
                        cameraZ = (float)Math.toDegrees(bone.getRotZ() - bind.getRotZ());
                        cameraFrame = System.nanoTime();
                        return;
                    }
                    if (name.equals("spout")) {
                        var at = NativeRenderMatrices.detachedCopy(pose);
                        RenderUtils.translateToPivotPoint(at, bone);
                        spout = new Vector4f(0, 0, 0, 1).mul(at.last().pose());
                        spoutFrame = System.nanoTime();
                        return;
                    }
                    if (!right && !name.equals("left_hand") && !name.equals("left_under_hand")) return;
                    var locator = NativeRenderMatrices.detachedCopy(pose);
                    RenderUtils.translateToPivotPoint(locator, bone);
                    try {
                        NativePlayerArmRenderer.render(locator, right == view.right, buffers, light, overlay);
                    } finally {
                        buffers.getBuffer(type);
                    }
                }
            });
        }

        @Override
        public void preRender(PoseStack pose, ViewModel view, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                              float partial, int light, int overlay, float red, float green, float blue, float alpha) {
            super.preRender(pose, view, model, buffers, buffer, reRender, partial, light, overlay, red, green, blue, alpha);
            pose.translate(-0.5, -0.51, -0.5);   // GeoObjectRenderer centres a world object; the rig is in camera space
            mesh = AflMeshCache.snapshot().get(GEO);
        }

        /** The can's AFL Mesh on its bones (the geo has no cubes). */
        @Override
        public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                      float red, float green, float blue, float alpha) {
            super.renderCubesOfBone(pose, bone, buffer, light, overlay, red, green, blue, alpha);
            if (mesh != null) AflMeshRenderer.render(mesh, bone, pose, buffer, light, overlay, red, green, blue, alpha);
        }
    }

    /** The view turns opposite the camera bone (as the guns', weapon/client/NativeCameraBoneConsumer). */
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || event.getCamera().getEntity() != player || !mc.options.getCameraType().isFirstPerson()
                || !isCan(player.getMainHandItem()) || System.nanoTime() - cameraFrame > 100_000_000L) return;
        if (!Float.isFinite(cameraX) || !Float.isFinite(cameraY) || !Float.isFinite(cameraZ)) return;
        float mirror = player.getMainArm() == HumanoidArm.RIGHT ? 1F : -1F;
        event.setPitch(event.getPitch() - cameraX);
        event.setYaw(event.getYaw() - cameraY * mirror);
        event.setRoll(event.getRoll() - cameraZ * mirror);
    }

    @SubscribeEvent
    public static void render(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !isCan(player.getMainHandItem())) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) {   // both hands are on the can
            event.setCanceled(true);
            return;
        }
        if (player.isSpectator() || player.isScoping() || player.isSleeping()) return;
        event.setCanceled(true);
        if (renderer == null) renderer = new Renderer();
        PoseStack pose = NativeRenderMatrices.detachedCopy(event.getPoseStack());
        VIEW.right = player.getMainArm() == HumanoidArm.RIGHT;
        if (!VIEW.right) pose.scale(-1, 1, 1);
        VIEW.ticks = mc.level.getGameTime() + event.getPartialTick();
        VIEW.pouring = FuelCanItem.isPouring(player.getMainHandItem());
        pose.translate(0, -0.6 * event.getEquipProgress(), 0);   // raised as it is equipped, as vanilla's
        RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
        renderer.render(pose, VIEW, event.getMultiBufferSource(), type, event.getMultiBufferSource().getBuffer(type), event.getPackedLight());
    }
}
