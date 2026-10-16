package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.skeleton.GeoAnchorSampler;
import com.antaurora.apofirstlight.skeleton.RifleStance;
import com.antaurora.apofirstlight.skeleton.SkeletonPackets;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.NativeAdsProfile;
import com.antaurora.apofirstlight.weapon.client.NativeCollimatedReticleRendering;
import com.antaurora.apofirstlight.weapon.client.NativeGunAds;
import com.antaurora.apofirstlight.weapon.client.NativeGunRecoil;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import com.antaurora.apofirstlight.weapon.client.NativeWeaponSway;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimationController;

import java.util.HashMap;
import java.util.Map;

/**
 * Player skeleton V1 P1 (docs/dev/player_skeleton_v1_design.md 4): the BR51 on the skeleton. The gun is real size
 * (rack length over the model's longest side: 0.307 of its model, first person drew it at 0.45) and placed against the
 * eye, not the screen: aiming, its sight sits on the view line {@code eye_relief} (scaled with the gun) in front of the
 * eye (NativeAdsProfile#ads with the real scale); otherwise it rests a little lower, right and forward (RifleStance.READY),
 * blended by the aim progress (first person: NativeGunAds; others: {@link SkeletonPackets}). Both hands reach for the
 * gun's {@code right_hand_anchor} / {@code left_hand_anchor} of its {@code static_idle} clip by IK
 * (skeleton/HumanoidPoser). First person adds the view model's sway and recoil to the gun, and the hands follow.
 * <p>
 * P1 limits (docs): only the BR51, right-handed, off hand empty; the hands hold the idle anchors (the shot's kick moves
 * the gun, not yet the anchors); while the gun plays reload, draw, put-away or inspect, the own first person keeps the
 * old view model; other players' guns hold the frozen idle pose (as before); no gun in the shadow pass.
 */
public final class SkeletonGunStance {
    public static final float CHEST_TWIST = RifleStance.CHEST_TWIST;
    private static final String CLIP = "static_idle";
    private static final Map<ResourceLocation, GeoAnchorSampler> SAMPLERS = new HashMap<>();
    private static final Map<Integer, Boolean> REMOTE_AIM = new HashMap<>();
    private static final Map<Integer, float[]> REMOTE_PROGRESS = new HashMap<>();
    private static boolean sentAim;

    private SkeletonGunStance() {
    }

    /** The player holds a gun the skeleton carries: the BR51, right-handed, nothing in the off hand, nothing in use. */
    public static boolean holds(Player p) {
        ItemStack main = p.getMainHandItem();
        if (!(main.getItem() instanceof NativeGunItem gun) || scale(gun) == null) return false;
        if (!p.getOffhandItem().isEmpty() || p.isUsingItem() || p.getMainArm() != HumanoidArm.RIGHT) return false;
        return p != Minecraft.getInstance().player || !actionPlaying(gun, main);
    }

    private static Float scale(NativeGunItem gun) {
        ResourceLocation id = gun.definition().id();
        return ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) ? RifleStance.realScale(id.getPath()) : null;
    }

    /** Reload, draw, put-away or inspect playing on the own gun (NativeGunAds#action's list). */
    private static boolean actionPlaying(NativeGunItem gun, ItemStack stack) {
        var manager = gun.getAnimatableInstanceCache().getManagerForId(GeoItem.getId(stack));
        var c = manager == null ? null : manager.getAnimationControllers().get("action");
        if (c == null || c.getAnimationState() == AnimationController.State.STOPPED || c.getCurrentAnimation() == null) return false;
        String clip = c.getCurrentAnimation().animation().name();
        return clip.contains("reload") || clip.equals("draw") || clip.equals("put_away") || clip.startsWith("inspect");
    }

    /** Client tick: the own aim to the server when it changes; the others' aim eased in and out. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            boolean aiming = mc.player.getMainHandItem().getItem() instanceof NativeGunItem && NativeGunAds.progress(1F) > 0.5F;
            if (aiming != sentAim) {
                sentAim = aiming;
                AflNetwork.skeletonToServer(new SkeletonPackets.Aim(aiming));
            }
        }
        for (var e : REMOTE_AIM.entrySet()) {
            float[] p = REMOTE_PROGRESS.computeIfAbsent(e.getKey(), k -> new float[2]);
            p[0] = p[1];
            p[1] = Math.max(0, Math.min(1, p[1] + (e.getValue() ? 0.25F : -0.25F)));
        }
    }

    /** Leaving the world: entity ids start over. */
    static void clear() {
        REMOTE_AIM.clear();
        REMOTE_PROGRESS.clear();
        sentAim = false;
    }

    /** SkeletonPackets.AimOf. */
    public static void remoteAim(int entity, boolean aiming) {
        REMOTE_AIM.put(entity, aiming);
    }

    private static float aim(Player p, float partial, boolean firstPerson) {
        if (p == Minecraft.getInstance().player) return firstPerson ? NativeGunAds.progress(partial) : 0F;
        float[] e = REMOTE_PROGRESS.get(p.getId());
        return e == null ? 0F : e[0] + (e[1] - e[0]) * partial;
    }

    /**
     * The gun's bare GeckoLib frame (blocks) in the render-origin frame (world axes, origin at the render origin), for
     * an eye at {@code eye} looking along yaw / pitch (deg); skeleton/RifleStance. Null when the gun has no aim calibration.
     */
    static Matrix4f place(Player p, Vec3 eye, float yaw, float pitch, float partial, boolean firstPerson) {
        ItemStack stack = p.getMainHandItem();
        NativeAdsProfile profile = NativeAdsProfile.forStack(stack);
        if (profile == null || !(stack.getItem() instanceof NativeGunItem gun)) return null;
        Matrix4f m = RifleStance.eye(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z), yaw, pitch);
        if (firstPerson) {
            // the view model's sway and recoil move the gun in view space, the hands follow it
            PoseStack extra = new PoseStack();
            NativeWeaponSway.apply(extra, partial);
            NativeGunRecoil.applyViewmodel(extra);
            m.mul(extra.last().pose());
        }
        return m.mul(RifleStance.inView(numbers(profile), scale(gun), aim(p, partial, firstPerson)));
    }

    private static RifleStance.Profile numbers(NativeAdsProfile profile) {
        return new RifleStance.Profile(profile.ax(), profile.ay(), profile.az(), profile.eyeRelief(),
                profile.scale(), profile.adsPitch(), profile.adsYaw(), profile.adsRoll());
    }

    /** The support hand's slide back along the gun (RifleStance#supportSlide), model px; zero without a calibration. */
    static void supportSlide(ItemStack stack, Matrix4f gunModel, Vector3f out) {
        NativeAdsProfile profile = NativeAdsProfile.forStack(stack);
        if (profile == null) out.zero();
        else RifleStance.supportSlide(new Matrix4f().scaling(16F).mul(gunModel), numbers(profile), out);
    }

    /** One hand's IK target on the gun (RifleStance#handTarget) at the anchors of the gun's idle clip. */
    static void handTarget(ItemStack stack, Matrix4f gunModel, boolean right, boolean slim, Matrix4f out) {
        GeoAnchorSampler sampler = sampler(stack);
        Matrix4f anchor = sampler == null ? null : sampler.frame(right ? "right_hand_anchor" : "left_hand_anchor", CLIP, 0F);
        RifleStance.handTarget(gunModel, anchor == null ? new Matrix4f() : anchor, right, slim, out);
    }

    private static GeoAnchorSampler sampler(ItemStack stack) {
        if (!(stack.getItem() instanceof NativeGunItem gun)) return null;
        ResourceLocation id = gun.definition().id();
        return SAMPLERS.computeIfAbsent(id, k -> {
            try {
                return GeoAnchorSampler.resource(k.getNamespace(), k.getPath());
            } catch (RuntimeException e) {
                ApocalypseFirstLight.LOGGER.error("[AFL skeleton] no hand anchors for {}", k, e);
                return null;
            }
        });
    }

    /**
     * Draws the gun at its model-space frame (blocks; rigid times uniform scale) under the model's pose. The own first
     * person draws it in its first-person context (its animations, the reticle) without the old floating arms.
     */
    static void render(Player p, ItemStack stack, Matrix4f gunModel, PoseStack modelPose, MultiBufferSource buffers,
                       int light, boolean firstPerson) {
        var renderer = IClientItemExtensions.of(stack).getCustomRenderer();
        PoseStack pose = new PoseStack();
        pose.last().pose().set(modelPose.last().pose());
        pose.last().normal().set(modelPose.last().normal());
        Vector3f t = gunModel.getTranslation(new Vector3f());
        float s = gunModel.getScale(new Vector3f()).x;
        pose.translate(t.x, t.y, t.z);
        pose.mulPose(gunModel.getUnnormalizedRotation(new Quaternionf()));
        pose.scale(s, s, s);
        pose.translate(-0.5F, -0.51F, -0.5F); // what ItemRenderer (-0.5) and GeoItemRenderer (+0.5, 0.51, 0.5) add
        if (!firstPerson) {
            renderer.renderByItem(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            return;
        }
        NativePlayerArmRenderer.setHandFilter(right -> false);
        NativeCollimatedReticleRendering.begin();
        try {
            renderer.renderByItem(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            NativeCollimatedReticleRendering.draw(buffers);
        } finally {
            NativeCollimatedReticleRendering.end();
            NativePlayerArmRenderer.setHandFilter(right -> true);
        }
    }
}
