package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.inventory.CarriedWeaponsLayer;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.skeleton.GeoAnchorSampler;
import com.antaurora.apofirstlight.skeleton.GunStance;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonPackets;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.weapon.NativeGunAnimations;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.NativeAdsProfile;
import com.antaurora.apofirstlight.weapon.client.NativeCollimatedReticleRendering;
import com.antaurora.apofirstlight.weapon.client.NativeGunAds;
import com.antaurora.apofirstlight.weapon.client.NativeGunContextRenderer;
import com.antaurora.apofirstlight.weapon.client.NativeGunRecoil;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import com.antaurora.apofirstlight.weapon.client.NativeWeaponSway;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 4, 12, 13): the guns the skeleton carries (skeleton/GunStance:
 * the BR51 as a rifle at 1.25 x real size, the P9 as a pistol at real size), placed on the shoulder (rifle), against the
 * eye (raised pistol, first-person aiming) or the chest (lowered).
 * <ul>
 * <li>Every clip of the gun plays on the skeleton, in first and third person and for the other players (the server's
 * clip triggers reach everyone tracking the player): each frame the gun's bones are evaluated before it is drawn
 * ({@link GunBones}) and both hands reach for its {@code right_hand_anchor} / {@code left_hand_anchor} by IK
 * (HumanoidPoser), so a reload or an inspect moves the hands with the gun.</li>
 * <li>Draw and put-away: the gun comes off its carry mount (rifle: the back, pistol: the thigh holster; client/inventory/
 * CarriedWeaponsLayer) along a path to the stance, and back. The clip's own whole-gun move (up from the bottom of the
 * screen) is held still ({@link #still}); its mechanical parts still play. Switching away from a gun keeps drawing it on
 * its way to the mount (a "put-away ghost") with the right hand on it; a gun drawn meanwhile waits for it.</li>
 * <li>Pistol: both hands at the ready before the chest by default (TC 3-23.35 6-31); raised to the eye while aiming, for
 * 1.5 s after a shot, while reloading or inspecting. Any gun is lowered while sprinting (not aiming), a pistol one-handed.</li>
 * <li>Rifle: the butt on the chest right of the centre, the bore along the view, in first and third person; only
 * first-person aiming takes it to the eye. The support hand under the handguard's rear (GunStance#supportUnder); a
 * reload or an inspect hands it to the clip's {@code left_hand_anchor} and back (eased).</li>
 * <li>Hands: on the gun's anchors turned by the kind's grips, elbows toward the kind's poles
 * (docs/dev/gun_handling_reference_v1.md 2, 4).</li>
 * </ul>
 * Limits (docs 13): the right hand only, the off hand empty; no gun in the shadow pass.
 */
public final class SkeletonGunStance {
    /** After a shot a pistol stays raised this long (s). */
    private static final float RAISED_AFTER_SHOT = 1.5F;
    /** A put-away ghost leaves a gun drawn after it at least this long (s). */
    private static final float MIN_DRAW = 0.25F;
    private static final Map<ResourceLocation, GeoAnchorSampler> SAMPLERS = new HashMap<>();
    private static final Map<String, String> STILL = new HashMap<>();
    private static final Map<Integer, Boolean> REMOTE_AIM = new HashMap<>();
    private static final Map<Integer, float[]> REMOTE_PROGRESS = new HashMap<>();
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static boolean sentAim;

    private SkeletonGunStance() {
    }

    /** One player's gun state, kept across frames. */
    private static final class Track {
        /** Not ticked yet: what is in the hand when a player is first seen is already drawn. */
        boolean fresh = true;
        ItemStack held = ItemStack.EMPTY;
        /**
         * Seconds (level time) the held gun came into the hand, and its draw's length; the hands start on it at
         * {@code drawBegin} (later when a put-away ghost holds them first; the draw still ends on time).
         */
        double drawStart = -1, drawBegin;
        float drawLength;
        /** The gun on its way back to its mount after the hand let go of it, or EMPTY. */
        ItemStack ghost = ItemStack.EMPTY;
        double ghostStart;
        float ghostLength;
        /** The action clip last seen, its instance (new for every trigger) and when a shot last started (s). */
        String clip = "";
        Object clipInstance;
        double lastShot = -100;
        /**
         * Eased per tick: a pistol's two-handed raise, any gun's sprint lowering, the support hand on the clip's anchor
         * (reload, inspect) instead of under the handguard.
         */
        float raise, raiseO, lower, lowerO, onAnchor, onAnchorO;
    }

    // ---------------------------------------------------------------- what is held

    /** The carried gun (GunStance) of a stack, or null. */
    static GunStance.Carried carried(ItemStack stack) {
        if (!(stack.getItem() instanceof NativeGunItem gun)) return null;
        ResourceLocation id = gun.definition().id();
        return ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) ? GunStance.carried(id.getPath()) : null;
    }

    /** The player holds a gun the skeleton carries: right-handed, nothing in the off hand, nothing in use. */
    public static boolean holds(Player p) {
        return carried(p.getMainHandItem()) != null && p.getOffhandItem().isEmpty() && !p.isUsingItem()
                && p.getMainArm() == HumanoidArm.RIGHT;
    }

    /** The skeleton draws a gun for the player this frame: one held, or one on its way back to its mount. */
    static boolean busy(Player p) {
        return !drawn(p).isEmpty();
    }

    /** The gun the hands are on: the one going back to its mount, else the one held, else EMPTY. */
    private static ItemStack drawn(Player p) {
        Track t = TRACKS.get(p.getId());
        if (t != null && !t.ghost.isEmpty()) return t.ghost;
        return holds(p) ? p.getMainHandItem() : ItemStack.EMPTY;
    }

    /** CarriedWeaponsLayer: this carried gun is on its way between the hand and its mount, so not on the mount. */
    public static boolean hidesCarried(Player p, ItemStack stack) {
        Track t = TRACKS.get(p.getId());
        return t != null && !t.ghost.isEmpty() && sameGun(t.ghost, stack);
    }

    private static boolean sameGun(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        long ia = GeoItem.getId(a), ib = GeoItem.getId(b);
        return ia == Long.MAX_VALUE || ib == Long.MAX_VALUE || ia == ib;
    }

    /** The chest's yaw right of the view (deg) with the gun held. */
    static float chestTwist(Player p, float partial) {
        GunStance.Carried c = carried(drawn(p));
        if (c == null) return 0F;
        Track t = TRACKS.get(p.getId());
        float lower = t == null ? 0F : t.lowerO + (t.lower - t.lowerO) * partial;
        float up = c.kind() == GunStance.Kind.PISTOL ? raise(t, partial) : 1F;
        return c.kind().chestTwist * up * (1F - lower);
    }

    /** The gun is up (the chest follows the view's pitch more, HumanoidPoser#chestPitch's rifle share). */
    static boolean gunUp(Player p, float partial) {
        GunStance.Carried c = carried(drawn(p));
        if (c == null) return false;
        return c.kind() == GunStance.Kind.RIFLE || raise(TRACKS.get(p.getId()), partial) > 0.5F;
    }

    private static float raise(Track t, float partial) {
        return t == null ? 0F : t.raiseO + (t.raise - t.raiseO) * partial;
    }

    // ---------------------------------------------------------------- per tick

    /** Client tick: the own aim to the server when it changes; the others' aim; every player's hand changes and easing. */
    static void tick(boolean skeleton) {
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
        if (!skeleton || mc.level == null) {
            TRACKS.clear();
            return;
        }
        double now = now(0F);
        TRACKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) tick(p, TRACKS.computeIfAbsent(p.getId(), k -> new Track()), now);
    }

    private static void tick(AbstractClientPlayer p, Track t, double now) {
        // by the main hand alone: an off-hand item or using one hides the gun's stance, it does not put the gun away
        ItemStack hand = carried(p.getMainHandItem()) != null ? p.getMainHandItem() : ItemStack.EMPTY;
        if (!t.ghost.isEmpty() && now >= t.ghostStart + t.ghostLength) t.ghost = ItemStack.EMPTY;
        if (t.fresh) {
            t.fresh = false;
            t.held = hand;
        }
        if (hand != t.held && !(hand.isEmpty() && t.held.isEmpty())) {
            boolean swapped = !t.held.isEmpty() && !(hand.getItem() == t.held.getItem() && sameGun(hand, t.held));
            if (swapped && PlayerSkeleton.supportedPose(p) && p.isAlive()) {
                // the gun let go of goes back to its mount, the right hand with it
                t.ghost = t.held;
                t.ghostStart = now;
                t.ghostLength = clipSeconds(t.held, "put_away", 0.4F);
            }
            if (!hand.isEmpty() && (t.held.isEmpty() || swapped)) {
                t.drawStart = now;
                t.drawLength = clipSeconds(hand, "draw", 0.5F);
                t.drawBegin = now;
                if (!t.ghost.isEmpty()) {
                    t.ghostLength = Math.min(t.ghostLength, Math.max(0.15F, t.drawLength - MIN_DRAW));
                    t.drawBegin = t.ghostStart + t.ghostLength;
                }
            }
            t.held = hand;
        }
        t.raiseO = t.raise;
        t.lowerO = t.lower;
        t.onAnchorO = t.onAnchor;
        boolean aiming = aim(p, 1F, p == Minecraft.getInstance().player && !Minecraft.getInstance().gameRenderer.getMainCamera().isDetached()) > 0.05F;
        boolean acting = t.clip.startsWith("reload") || t.clip.startsWith("inspect");
        boolean raised = aiming || acting || now - t.lastShot < RAISED_AFTER_SHOT;
        t.raise += ((raised ? 1F : 0F) - t.raise) * 0.4F;
        t.lower += ((p.isSprinting() && !aiming ? 1F : 0F) - t.lower) * 0.3F;
        t.onAnchor += ((acting ? 1F : 0F) - t.onAnchor) * 0.45F;
    }

    private static float clipSeconds(ItemStack stack, String clip, float fallback) {
        if (!(stack.getItem() instanceof NativeGunItem gun) || !NativeGunAnimations.hasClip(gun.animationAsset(), clip)) return fallback;
        return NativeGunAnimations.ticks(gun.animationAsset(), clip) / 20F;
    }

    private static double now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() + partial) / 20.0;
    }

    /** Leaving the world: entity ids start over. */
    static void clear() {
        REMOTE_AIM.clear();
        REMOTE_PROGRESS.clear();
        TRACKS.clear();
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

    // ---------------------------------------------------------------- per frame

    /**
     * The gun the skeleton draws this frame: the stack, its frame (gun blocks to model blocks), the bone held still; and a
     * gun held meanwhile that waits on its mount for the hands ({@code parked}, bind pose, or EMPTY).
     */
    record Hold(ItemStack stack, Matrix4f gun, String still, ItemStack parked, Matrix4f parkedAt) {}

    /**
     * Places the held (or put-away) gun for this frame and sets the arm inputs: IK targets on its anchors, how much each
     * hand is on it, shoulders, the support hand's slide. The body is already posed (HumanoidPoser#solveBody) and written to
     * {@code model}'s parts (the mounts hang on them). {@code eye}: render-origin blocks; {@code toModel}: render-origin
     * blocks to model blocks. Null when no gun is drawn.
     */
    static Hold hold(AbstractClientPlayer p, PlayerModel<?> model, SkeletonPose pose, HumanoidPoser.Input in, Vec3 eye,
                     float yaw, float pitch, float partial, boolean firstPerson, Matrix4f toModel) {
        Track t = TRACKS.get(p.getId());
        double now = now(partial);
        boolean ghost = t != null && !t.ghost.isEmpty();
        ItemStack held = holds(p) ? p.getMainHandItem() : ItemStack.EMPTY;
        ItemStack stack = ghost ? t.ghost : held;
        GunStance.Carried carried = carried(stack);
        NativeAdsProfile ads = NativeAdsProfile.forStack(stack);
        if (carried == null || ads == null) return null;
        GunStance.Kind kind = carried.kind();
        GunStance.Profile profile = numbers(ads);
        float real = carried.scale();
        GeoAnchorSampler sampler = sampler(stack, carried);
        Matrix4f rightRest = sampler == null ? null : sampler.frame("right_hand_anchor", "static_idle", 0F);
        if (rightRest == null) rightRest = new Matrix4f();

        // drawn or put away: how far along the path from / to its mount, how much each hand is on it
        GunStance.Phase phase = null;
        if (ghost) {
            phase = GunStance.putAway(kind, (float) ((now - t.ghostStart) / Math.max(0.05F, t.ghostLength)));
        } else if (t != null && t.drawStart >= 0 && now < t.drawStart + t.drawLength) {
            double begin = t.drawBegin;
            float f = (float) ((now - begin) / Math.max(0.05, t.drawStart + t.drawLength - begin));
            phase = GunStance.draw(kind, Math.max(0F, f));
        }
        Matrix4f mount = phase == null ? null : mount(model, stack);
        boolean onPath = mount != null;

        // the gun's hand anchors as its clips pose them this frame
        GunBones.Action action = GunBones.evaluate(stack, firstPerson ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, partial);
        if (action != null && t != null && !ghost) {
            if (action.instance() != t.clipInstance) {
                t.clipInstance = action.instance();
                if (action.clip().equals("shoot")) t.lastShot = now;
            }
            t.clip = action.clip();
        }
        // the clip's whole-gun move is the skeleton's path while on it, and is never wanted while its draw clip runs late
        boolean drawClip = action != null && (action.clip().contains("draw") || action.clip().equals("put_away"));
        String still = onPath || drawClip ? still(stack, carried) : null;
        Matrix4f rightAnchor = action == null ? null : GunBones.frame(stack, "right_hand_anchor", still);
        Matrix4f leftAnchor = action == null ? null : GunBones.frame(stack, "left_hand_anchor", still);
        if (rightAnchor == null) rightAnchor = rightRest;
        if (leftAnchor == null && sampler != null) leftAnchor = sampler.frame("left_hand_anchor", "static_idle", 0F);
        if (leftAnchor == null) leftAnchor = new Matrix4f();

        // where it is held: a rifle on the shoulder (to the eye aiming in first person); a pistol raised against the eye or
        // at its two-handed ready before the chest; lowered (sprinting)
        float aim = ghost ? 0F : aim(p, partial, firstPerson);
        float lower = t == null ? 0F : t.lowerO + (t.lower - t.lowerO) * partial;
        float raise = kind.readyHand != null ? Math.max(raise(t, partial), aim) : 1F;
        float up = raise * (1F - lower);
        Matrix4f raised = new Matrix4f(toModel).mul(GunStance.eye(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z), yaw, pitch));
        if (firstPerson) {
            // the view model's sway and recoil move the gun in view space, the hands follow it
            PoseStack extra = new PoseStack();
            NativeWeaponSway.apply(extra, partial);
            NativeGunRecoil.applyViewmodel(extra);
            raised.mul(extra.last().pose());
        }
        Vector3f back = raised.transformDirection(new Vector3f(0, 0, 1)).normalize(); // the view's +z, model axes
        if (kind.pocket != null) {
            Matrix4f shouldered = GunStance.onShoulder(raised, pose.world[pose.rig.chest], profile, kind, carried);
            float toEye = firstPerson ? aim : 0F;
            raised = toEye > 0 ? GunStance.blend(shouldered, raised.mul(GunStance.inView(profile, kind, real, 1F)), toEye) : shouldered;
        } else {
            raised.mul(GunStance.inView(profile, kind, real, aim));
        }
        Matrix4f chest = GunStance.chestView(pose.world[pose.rig.chest], pose.rig.pivot[pose.rig.chest]);
        Matrix4f lowered = new Matrix4f(chest).mul(GunStance.lowInChest(profile, kind, real, rightRest));
        Matrix4f base = kind.readyHand == null ? raised
                : GunStance.blend(new Matrix4f(chest).mul(GunStance.readyInChest(profile, kind, real, rightRest)), raised, raise);
        Matrix4f stance = GunStance.blend(lowered, base, 1F - lower);
        float leftOn = kind == GunStance.Kind.RIFLE ? 1F : 1F - lower;
        in.right = in.left = HumanoidPoser.ArmMode.IK;
        if ((kind.sightDistance > 0 || kind.pocket != null) && up > 0) {
            // held out at arm's length or shouldered: back toward the eye where the arms are too short (looking up or far
            // down, crouched, a clip turning the gun away)
            in.ikRightWeight = 1F;
            in.ikLeftWeight = leftOn;
            in.shoulderRight.set(kind.shoulderRight).mul(up);
            in.shoulderLeft.set(kind.shoulderLeft).mul(up * leftOn);
            boolean under = carried.support() != null && (t == null || t.onAnchor < 0.5F);
            stance = GunStance.inReach(kind, under ? carried.support() : null, PlayerSkeleton.poser(), in, pose, stance, back,
                    rightAnchor, leftAnchor);
        }

        Matrix4f gun = stance;
        float rightHand = 1F, leftHand = leftOn;
        if (phase != null) {
            if (onPath) {
                Matrix4f middle = kind == GunStance.Kind.RIFLE ? new Matrix4f(chest).mul(GunStance.sideInChest(profile, real, rightRest))
                        : new Matrix4f().translation(0, -0.2F, 0).mul(mount);
                gun = GunStance.path(mount, middle, stance, phase.along());
            }
            rightHand = phase.rightHand();
            leftHand = phase.leftHand() * leftOn;
        }

        in.ikRightWeight = rightHand;
        in.ikLeftWeight = leftHand;
        GunStance.handTarget(gun, rightAnchor, kind.gripRight, true, in.slim, in.targetRight);
        GunStance.handTarget(gun, leftAnchor, kind.gripLeft, false, in.slim, in.targetLeft);
        in.poleRight.set(kind.poleRight);
        in.poleLeft.set(kind.poleLeft);
        GunStance.Support support = carried.support();
        if (support != null) {
            // under the handguard, but on the clip's anchor while a reload or an inspect moves it
            float onAnchor = t == null ? 0F : t.onAnchorO + (t.onAnchor - t.onAnchorO) * partial;
            if (onAnchor < 1F) {
                Matrix4f under = GunStance.supportUnder(gun, support, in.slim, new Matrix4f());
                in.targetLeft.set(onAnchor <= 0F ? under : GunStance.blend(under, in.targetLeft, onAnchor));
                in.poleLeft.set(support.pole()).lerp(kind.poleLeft, onAnchor);
            }
        }
        GunStance.supportSlide(new Matrix4f().scaling(16F).mul(gun), profile, kind.supportSlide, in.slideLeft);
        in.shoulderRight.set(kind.shoulderRight).mul(up * rightHand);
        in.shoulderLeft.set(kind.shoulderLeft).mul(up * leftHand);
        // a gun taken meanwhile waits on its mount until the hands are back (its own draw starts after the put-away)
        ItemStack parked = ghost && carried(held) != null ? held : ItemStack.EMPTY;
        Matrix4f parkedAt = parked.isEmpty() ? null : mount(model, parked);
        return new Hold(stack, gun, still, parkedAt == null ? ItemStack.EMPTY : parked, parkedAt);
    }

    /** A carried gun's mount on the posed body (rifle: the back, pistol: the thigh holster), model blocks, or null. */
    private static Matrix4f mount(PlayerModel<?> model, ItemStack stack) {
        GunStance.Carried c = carried(stack);
        if (c == null) return null;
        return c.kind() == GunStance.Kind.RIFLE ? CarriedWeaponsLayer.backFrame(model, stack) : CarriedWeaponsLayer.thighFrame(model, stack);
    }

    private static GunStance.Profile numbers(NativeAdsProfile profile) {
        return new GunStance.Profile(profile.ax(), profile.ay(), profile.az(), profile.eyeRelief(),
                profile.scale(), profile.adsPitch(), profile.adsYaw(), profile.adsRoll());
    }

    private static GeoAnchorSampler sampler(ItemStack stack, GunStance.Carried carried) {
        ResourceLocation id = new ResourceLocation(ApocalypseFirstLight.MOD_ID, carried.asset());
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
     * The bone whose move the skeleton replaces during draw / put-away: the highest bone above the right hand's anchor
     * that the draw clip moves (BR51: root; P9: handling), or null.
     */
    private static String still(ItemStack stack, GunStance.Carried carried) {
        return STILL.computeIfAbsent(carried.asset(), k -> {
            GeoAnchorSampler s = sampler(stack, carried);
            if (s == null) return "";
            String found = "";
            for (String b = "right_hand_anchor"; b != null; b = s.parent(b))
                if (s.animates("draw", b)) found = b;
            return found;
        }).transform(s -> s.isEmpty() ? null : s);
    }

    /**
     * Draws the gun at its model-space frame (blocks; rigid times uniform scale) under the model's pose. The own first
     * person draws it in its first-person context (its animations, the reticle) without the old floating arms; third
     * person plays its clips too ({@link NativeGunContextRenderer#skeletonDraw}).
     */
    static void render(Hold hold, PoseStack modelPose, MultiBufferSource buffers, int light, boolean firstPerson) {
        if (!hold.parked().isEmpty()) CarriedWeaponsLayer.draw(modelPose, buffers, light, hold.parked(), hold.parkedAt());
        ItemStack stack = hold.stack();
        Matrix4f gunModel = hold.gun();
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
        NativeGunContextRenderer.skeletonDraw(!firstPerson, hold.still());
        try {
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
        } finally {
            NativeGunContextRenderer.skeletonDrawEnd();
        }
    }
}
