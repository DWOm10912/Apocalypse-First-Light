package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton;
import com.antaurora.apofirstlight.client.skeleton.SeatedRiders;
import com.antaurora.apofirstlight.client.skeleton.SkeletonActions;
import com.antaurora.apofirstlight.client.skeleton.SkeletonGunStance;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import com.antaurora.apofirstlight.weapon.client.NativeGunInput;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-person body V1 (docs/rendering/first_person_body_v1.md): in first person the own player is drawn in the normal entity
 * pass (LevelRendererFirstPersonBodyMixin lets it through vanilla's "camera entity, not detached" skip), so looking down shows
 * the torso, legs and what is worn on them. While that one draw runs ({@link #begin} / {@link #end} round
 * LevelRenderer#renderEntity):
 * <ul>
 * <li>main pass: head and hat hidden, with the helmet, head items, cape, elytra, stuck arrows, shoulder parrots and the own
 * flames. The arms depend on the hand ({@link #bodyArms}): empty or an ordinary item, the body's arms hang at its sides with
 * the item in the hand, and the first-person hand slides out of view as the player looks down ({@link #handDrop}, user
 * 2026-10-15); a gun, the crowbar or the fuel can (their own first-person arms) or an item in use (eating, a drawn bow, a
 * raised shield) keeps the first-person hands, and the body's arms, their armour and the held item are hidden;</li>
 * <li>main and Oculus shadow pass alike: the whole model leant back about the feet, so the torso's front-top edge sits
 * {@link #GAP} behind the eye's vertical (vanilla puts the eye 2 px behind the chest; standing 6.0 deg, crouching 6.5 deg).
 * The shadow pass keeps head and arms (the shadow on the ground is the whole player, as before) but leans the same, else
 * the leant torso would sit in its own unleant shadow;</li>
 * <li>both passes, looking down: the body moves back by {@link #NECK} * sin(pitch) along the view's yaw. A person looking
 * down bends the neck, so the eyes come forward of the chest; the camera turning in place would see the top of the
 * shoulders right under the eye instead (user 2026-10-15: "人有点矮").</li>
 * </ul>
 * Not drawn: spectator, invisible, sleeping (vanilla already draws that), lying poses (swimming, crawling, elytra, riptide,
 * dying), and while the camera is away from the eye (gun bench view, generator pull, anything later that moves it).
 * <p>
 * With the player skeleton the arms follow PlayerSkeleton#firstPersonHands instead, and a held gun at its ready is handed
 * from its view model to the body's arms looking down ({@link #gunHandOver}, user 2026-10-19); so is an ordinary held item
 * ({@link #itemHandOver}, user 2026-10-20 "拿着弹匣也没有镜头渐变"; the same as health V1a's on the D branch).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonBody {
    /** Camera further than this from the eye (blocks): some view has moved it, draw no body. Vertical allows the crouch's eye-height easing. */
    private static final double AWAY_LEVEL = 0.3, AWAY_UP = 0.6;
    /** The torso's front-top edge behind the eye's vertical after the lean (blocks). */
    private static final float GAP = 0.03F;
    /** Model pixel in blocks (PlayerRenderer scales the model by 0.9375) and the model's top over its render origin (1.501 px * 0.9375). */
    private static final float PX = 0.9375F / 16F, TOP = 1.501F * 0.9375F;
    private static final float LEAN_STAND = lean(0F, 0F), LEAN_CROUCH = lean(3.2F, 0.5F);
    /** Neck bend: how far the body moves back looking straight down (blocks). */
    private static final float NECK = 0.13F;
    /** The first-person hand slides out between these pitches (deg down) by {@link #HAND_DROP} (camera space, blocks). */
    private static final float HAND_FROM = 25F, HAND_TO = 45F, HAND_DROP = 0.7F;
    /** The body's arms swing forward at most this far (rad, 20 deg) while the arms are shown: then first on screen at 45.6 deg down. */
    private static final float ARM_FORWARD = 0.35F;
    /**
     * A held gun handed to the body looking down ({@link #gunHandOver}): how long the view model takes to come back up for
     * something the gun does, and to go again after it (s).
     */
    private static final float GUN_BACK = 0.12F, GUN_FREE = 0.25F;
    /** Once the body holds the gun it keeps it until this many degrees above {@link #HAND_TO}: no flicker at the edge. */
    private static final float GUN_KEEP = 3F;
    /** Once the body holds an ordinary item ({@link #itemOnBody}) it keeps it until this many degrees above {@link #HAND_TO}: no flicker at the edge. */
    private static final float ITEM_KEEP = 3F;

    private enum Draw { NONE, MAIN, SHADOW }

    private static Draw drawing = Draw.NONE;
    /** This draw shows the body's arms (and the held item in them). */
    private static boolean arms;
    private static final boolean[] SAVED = new boolean[6];
    private static boolean saved;
    /** Render frames (RenderTickEvent START) and the one the gun hand-over was last worked out for, at what time (ns). */
    private static long frames, gunFrame = -1, gunNanos;
    /** How far the gun is free to go to the body (0..1, eased in time), the view model's slide this frame (0..1). */
    private static float gunFree, gunSlide;
    /** The body holds the gun this frame: its arms and the gun drawn, the view model out of view. */
    private static boolean gunBody;
    /** The frame the item hand-over was last worked out for; the first-person hand's slide (0..1); the body holds the item. */
    private static long itemFrame = -1;
    private static float itemSlide;
    private static boolean itemBody;

    private FirstPersonBody() {
    }

    /** In place of the camera's isDetached() in the entity loop: also true when the first-person body is drawn this frame. */
    public static boolean shown(Camera camera) {
        if (camera.isDetached() || !FirstPersonBodyConfig.showBody()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || camera.getEntity() != player) return false;
        if (player.isSpectator() || player.isInvisible() || player.isSleeping() || player.isDeadOrDying()) return false;
        // lying on the front out of water (the skeleton's prone clips): on the lying body's own eye
        if (PlayerSkeleton.enabled() && (com.antaurora.apofirstlight.client.skeleton.ProneClient.cameraOnBody(camera)
                || com.antaurora.apofirstlight.client.skeleton.ProneClient.ownLying(player))) return true;
        Pose pose = player.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return false;
        if (player.isVisuallySwimming() || player.isFallFlying() || player.isAutoSpinAttack()) return false;
        if (PlayerSkeleton.enabled() && SkeletonActions.cameraOnBody(camera)) return true;   // on the stepping body's own eye
        if (PlayerSkeleton.enabled() && SeatedRiders.cameraOnBody(camera)) return true;     // riding a get-in / get-out
        if (PlayerSkeleton.enabled() && com.antaurora.apofirstlight.client.skeleton.GroundSitClient.cameraOnBody(camera)) return true;   // sat on the ground
        Vec3 eye = player.getPosition(minecraft.getFrameTime()), at = camera.getPosition();
        double dx = at.x - eye.x, dz = at.z - eye.z, up = at.y - eye.y - player.getEyeHeight();
        return dx * dx + dz * dz <= AWAY_LEVEL * AWAY_LEVEL && Math.abs(up) <= AWAY_UP;
    }

    /** LevelRenderer#renderEntity HEAD: the camera entity's draw in first person, in the main or the Oculus shadow pass. */
    public static void begin(Entity entity) {
        drawing = Draw.NONE;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (entity != camera.getEntity() || !shown(camera)) return;
        drawing = AflShaderCompat.activeShadowPass() ? Draw.SHADOW : Draw.MAIN;
        arms = bodyArms(Minecraft.getInstance().player);
    }

    /**
     * Whether the body shows its own arms: not while the hand holds something with its own first-person arms (guns, the
     * crowbar, the pouring fuel can) or uses an item (eating, a drawn bow, a raised shield, a loaded crossbow held up),
     * where the arms would point into the view and the first-person hands stay.
     */
    public static boolean bodyArms(LocalPlayer player) {
        // player skeleton: its own hands (both empty, a put-away to the mount) replace the first-person hand pass; a held
        // gun at its ready goes to the body's hands looking down (#gunOnBody)
        if (PlayerSkeleton.enabled()) return PlayerSkeleton.firstPersonHands(player) || gunOnBody() || itemOnBody();
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        if (main.getItem() instanceof NativeGunItem || main.is(AflItems.CROWBAR.get()) || JerryCanFirstPerson.isCan(main)) return false;
        if (player.isUsingItem()) return false;
        return !(main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)
                || off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off));
    }

    /**
     * ItemInHandRenderer#renderHandsWithItems, around each renderArmWithItem call: how far the first-person hand goes down (camera space) while the body shows
     * its arms, from nothing at {@link #HAND_FROM} deg down to out of view at {@link #HAND_TO}. With the player skeleton: out of
     * view before the body's arms take over an action (client/skeleton/FirstPersonActionView), back up after it.
     */
    public static float handDrop(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !shown(minecraft.gameRenderer.getMainCamera())) return 0F;
        // player skeleton: out of view looking down (#itemHandOver) or as the body's arms take an action over
        // (client/skeleton/FirstPersonActionView), whichever is further
        if (PlayerSkeleton.enabled()) {
            itemHandOver();
            return HAND_DROP * Math.max(itemSlide, com.antaurora.apofirstlight.client.skeleton.FirstPersonActionView.viewModelSlide());
        }
        if (!bodyArms(player)) return 0F;
        return HAND_DROP * smooth((player.getViewXRot(partialTick) - HAND_FROM) / (HAND_TO - HAND_FROM));
    }

    /**
     * weapon/client/ConfiguredGunFirstPerson, first in the gun's view-model pose: how far the view model goes down this
     * frame (camera space, blocks; {@link #gunHandOver}).
     */
    public static float gunDrop() {
        gunHandOver();
        return HAND_DROP * gunSlide;
    }

    /** The body holds the held gun this frame ({@link #gunHandOver}): its arms and the gun are drawn, the view model is out of view. */
    public static boolean gunOnBody() {
        gunHandOver();
        return gunBody;
    }

    /**
     * Guns handed over to the body looking down (user 2026-10-19), with the player skeleton: a held gun at its ready
     * (SkeletonGunStance#atReady: one with stance loops, the P9) keeps its old view model looking ahead; looking down its
     * view model slides out as an empty hand does without the skeleton ({@link #HAND_FROM} to {@link #HAND_TO} deg,
     * {@link #HAND_DROP}), and once it is out of view the body's arms hold the gun in its stance (idle, walk, sprint), as the
     * others see it. Anything the gun does (aiming, a shot and the 1.5 s raise after it, reload, inspect, draw, put-away; the
     * fire, aim or reload key held) brings the view model back in {@link #GUN_BACK} s, the body's arms and gun hidden at
     * once; it goes again {@link #GUN_FREE} s after. Worked out once per frame, so the body's draws (main and shadow pass)
     * and the hand pass agree; the shadow pass and the other players do not use it.
     */
    private static void gunHandOver() {
        if (gunFrame == frames) return;
        gunFrame = frames;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        long now = Util.getNanos();
        float dt = gunNanos == 0L ? 0F : Mth.clamp((now - gunNanos) / 1.0E9F, 0F, 0.1F);
        gunNanos = now;
        float partial = minecraft.getFrameTime();
        boolean free = player != null && PlayerSkeleton.enabled() && shown(minecraft.gameRenderer.getMainCamera())
                && !player.isScoping() && !FieldAttachmentViewState.isActive() && SkeletonGunStance.atReady(player, partial)
                && !minecraft.options.keyAttack.isDown() && !minecraft.options.keyUse.isDown() && !NativeGunInput.reloadKey().isDown();
        gunFree = free ? Math.min(1F, gunFree + dt / GUN_FREE) : Math.max(0F, gunFree - dt / GUN_BACK);
        if (player == null) {
            gunBody = false;
            gunSlide = 0F;
            return;
        }
        float pitch = player.getViewXRot(partial);
        gunBody = free && gunFree >= 1F && (pitch >= HAND_TO || gunBody && pitch >= HAND_TO - GUN_KEEP);
        gunSlide = gunBody ? 1F : smooth((pitch - HAND_FROM) / (HAND_TO - HAND_FROM)) * smooth(gunFree);
    }

    /** With the skeleton: the body's hand holds the ordinary held item this frame, the first-person hand out of view ({@link #itemHandOver}). */
    public static boolean itemOnBody() {
        itemHandOver();
        return itemBody;
    }

    /**
     * Ordinary items handed over to the body looking down, with the player skeleton (as without it, 3.4): the first-person
     * hand with the item slides out from {@link #HAND_FROM} to {@link #HAND_TO} deg down, and from there the body's arms show,
     * the item in its hand (ItemInHandLayer), until the view comes back above {@link #HAND_TO} - {@link #ITEM_KEEP}. Not for
     * what has its own first-person arms (guns, the crowbar, the fuel can), an item in use, nor a loaded crossbow; empty
     * hands and player actions are the skeleton's own (PlayerSkeleton#firstPersonHands). The pitch is the camera's while an
     * action's look turns it (client/skeleton/FirstPersonActionView). Worked out once per frame, so the body's draw and the
     * hand pass agree.
     */
    private static void itemHandOver() {
        if (itemFrame == frames) return;
        itemFrame = frames;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (player == null || !PlayerSkeleton.enabled() || !shown(camera) || !ordinaryItem(player)
                || !PlayerSkeleton.supportedPose(player) || PlayerSkeleton.firstPersonHands(player)) {
            itemBody = false;
            itemSlide = 0F;
            return;
        }
        float pitch = com.antaurora.apofirstlight.client.skeleton.FirstPersonActionView.looking() ? camera.getXRot()
                : player.getViewXRot(minecraft.getFrameTime());
        itemBody = pitch >= HAND_TO || itemBody && pitch >= HAND_TO - ITEM_KEEP;
        itemSlide = itemBody ? 1F : smooth((pitch - HAND_FROM) / (HAND_TO - HAND_FROM));
    }

    /** Something in the hand that the body can simply hold: not empty, no first-person arms of its own, not in use. */
    private static boolean ordinaryItem(LocalPlayer player) {
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        if (main.isEmpty() && off.isEmpty()) return false;
        if (main.getItem() instanceof NativeGunItem || main.is(AflItems.CROWBAR.get()) || JerryCanFirstPerson.isCan(main)) return false;
        if (player.isUsingItem()) return false;
        return !(main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)
                || off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off));
    }

    /** The smooth step of {@code t} clamped to 0..1. */
    private static float smooth(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** LevelRenderer#renderEntity RETURN. */
    public static void end() {
        drawing = Draw.NONE;
    }

    /** The own body's main-pass draw is running: head, arms and what goes on them are left out. */
    public static boolean hidingParts() {
        return drawing == Draw.MAIN;
    }

    /** The own body's main-pass draw is running and shows its arms ({@link #bodyArms}), with what they hold. */
    public static boolean armsShown() {
        return drawing == Draw.MAIN && arms;
    }

    /** LivingEntityRenderer's layer loop: the layers left out of the own body's main-pass draw. */
    public static boolean skipsLayer(RenderLayer<?, ?> layer, Entity entity) {
        if (drawing != Draw.MAIN || entity != Minecraft.getInstance().player) return false;
        return layer instanceof ItemInHandLayer && !arms || layer instanceof CustomHeadLayer || layer instanceof CapeLayer
                || layer instanceof ElytraLayer || layer instanceof StuckInBodyLayer || layer instanceof ParrotOnShoulderLayer
                || layer instanceof SpinAttackEffectLayer || layer instanceof Deadmau5EarsLayer;
    }

    /**
     * HumanoidModel#setupAnim TAIL, the own body's main-pass draw with its arms shown: an arm swings at most
     * {@link #ARM_FORWARD} rad forward (vanilla walks up to 49 deg, sprints 57 deg; those reach into the view from 16-22 deg
     * down, beside the first-person hand). A hit swings the arm in full only once the first-person hand has slid away.
     */
    public static void limitArms(HumanoidModel<?> model, net.minecraft.world.entity.LivingEntity entity) {
        if (drawing != Draw.MAIN || !arms || entity != Minecraft.getInstance().player || PlayerSkeleton.enabled()) return;
        if (model.attackTime > 0F && entity.getViewXRot(Minecraft.getInstance().getFrameTime()) >= HAND_TO) return;
        model.rightArm.xRot = Math.max(model.rightArm.xRot, -ARM_FORWARD);
        model.leftArm.xRot = Math.max(model.leftArm.xRot, -ARM_FORWARD);
    }

    /** The own flames are not drawn round the camera (first person already shows its screen fire). */
    public static boolean hidesFlames(Entity entity) {
        return drawing == Draw.MAIN && entity == Minecraft.getInstance().player;
    }

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event) {
        if (drawing == Draw.NONE || event.getEntity() != Minecraft.getInstance().player) return;
        PlayerModel<?> model = event.getRenderer().getModel();
        float partial = event.getPartialTick();
        float yaw = Mth.rotLerp(partial, event.getEntity().yBodyRotO, event.getEntity().yBodyRot);
        PoseStack pose = event.getPoseStack();
        // the skeleton places the body against the camera itself (its root shift): no lean, no neck bend
        if (!PlayerSkeleton.enabled()) lean(event, model, partial, yaw, pose);
        if (drawing != Draw.MAIN) return;
        SAVED[0] = model.head.visible;
        SAVED[1] = model.hat.visible;
        SAVED[2] = model.rightArm.visible;
        SAVED[3] = model.leftArm.visible;
        SAVED[4] = model.rightSleeve.visible;
        SAVED[5] = model.leftSleeve.visible;
        saved = true;
        model.head.visible = model.hat.visible = false;
        if (!arms) model.rightArm.visible = model.leftArm.visible = model.rightSleeve.visible = model.leftSleeve.visible = false;
    }

    /** Without the skeleton: the neck bend and the lean that keep the torso out of the camera. */
    private static void lean(RenderPlayerEvent.Pre event, PlayerModel<?> model, float partial, float yaw, PoseStack pose) {
        // the neck bend: back along the view's yaw, (-sin, 0, cos) being the view's front
        float back = NECK * Mth.sin(Mth.clamp(event.getEntity().getViewXRot(partial), 0F, 90F) * Mth.DEG_TO_RAD);
        float view = event.getEntity().getViewYRot(partial) * Mth.DEG_TO_RAD;
        pose.translate(Mth.sin(view) * back, 0F, -Mth.cos(view) * back);
        // about the render origin (the feet): in the frame Ry(-yaw) +z is the body's front, Rx(-lean) tips the top back
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-(model.crouching ? LEAN_CROUCH : LEAN_STAND)));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        if (!saved || event.getEntity() != Minecraft.getInstance().player) return;
        restore(event.getRenderer().getModel());
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frames++;
        // a canceled draw has no Post: setModelProperties shows every part again before the next draw anyway
        saved = false;
        drawing = Draw.NONE;
    }

    /** HumanoidArmorLayer#setPartVisibility TAIL: helmet and the arm pieces stay off with the parts they cover. */
    public static void hideArmour(HumanoidModel<?> model) {
        if (drawing != Draw.MAIN) return;
        model.head.visible = model.hat.visible = false;
        if (!arms) model.rightArm.visible = model.leftArm.visible = false;
    }

    private static void restore(PlayerModel<?> model) {
        model.head.visible = SAVED[0];
        model.hat.visible = SAVED[1];
        model.rightArm.visible = SAVED[2];
        model.leftArm.visible = SAVED[3];
        model.rightSleeve.visible = SAVED[4];
        model.leftSleeve.visible = SAVED[5];
        saved = false;
    }

    /**
     * The lean (degrees) that puts the torso's front-top edge {@link #GAP} behind the eye's vertical, turning about the render
     * origin. {@code bodyY} / {@code bodyXRot}: HumanoidModel's body offset (px) and forward tilt (rad), standing 0 / 0,
     * crouching 3.2 / 0.5 (the crouch's render offset moves the origin down with the model, so it cancels out).
     */
    private static float lean(float bodyY, float bodyXRot) {
        // the torso's front-top corner (2 px in front of the pivot) after the tilt, model px (y down, z back)
        double y = bodyY + 2 * Math.sin(bodyXRot), z = -2 * Math.cos(bodyXRot);
        double forward = -z * PX, up = TOP - y * PX;
        double r = Math.hypot(forward, up), phi = Math.atan2(up, forward);
        return (float) Math.toDegrees(Math.acos(-GAP / r) - phi);
    }
}
