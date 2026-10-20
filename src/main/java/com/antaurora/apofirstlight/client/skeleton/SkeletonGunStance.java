package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.inventory.CarriedWeaponsLayer;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.skeleton.GeoAnchorSampler;
import com.antaurora.apofirstlight.skeleton.GunStance;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonPackets;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.antaurora.apofirstlight.skeleton.action.PlayerProps;
import com.antaurora.apofirstlight.weapon.NativeGunAnimations;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.NativeAdsProfile;
import com.antaurora.apofirstlight.weapon.client.NativeGunAds;
import com.antaurora.apofirstlight.weapon.client.NativeGunContextRenderer;
import com.antaurora.apofirstlight.weapon.client.NativeGunRecoil;
import com.antaurora.apofirstlight.weapon.client.NativeWeaponSway;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
 * its way to the mount (a "put-away ghost") with the right hand on it; a gun drawn meanwhile waits for it. A gun with
 * its own draw / put-away player actions ({@code gun/<id>_draw}, {@code gun/<id>_holster}, {@link #action}; the P9) is
 * placed by them instead, blended with the stance by their weight (docs/dev/p9_draw_holster_actions_v1.md 2.5).</li>
 * <li>Pistol: both hands at the ready before the chest by default (TC 3-23.35 6-31); raised to the eye while aiming, for
 * 1.5 s after a shot, while reloading or inspecting. Any gun is lowered while sprinting (not aiming), a pistol one-handed.
 * A gun with stance loops ({@code gun/<id>_idle}, {@code gun/<id>_sprint}; docs/dev/p9_stance_loops_v1.md) has its ready
 * and its lowered stance from them instead: their clips on the body ({@link #stanceClips}), their gun prop for the gun,
 * their hand timelines for how much each hand is on it.</li>
 * <li>Rifle: the butt on the chest right of the centre, the bore along the view, in first and third person; only
 * first-person aiming takes it to the eye. The support hand under the handguard's rear (GunStance#supportUnder); a
 * reload or an inspect hands it to the clip's {@code left_hand_anchor} and back (eased).</li>
 * <li>Hands: on the gun's anchors turned by the kind's grips, elbows toward the kind's poles
 * (docs/dev/gun_handling_reference_v1.md 2, 4).</li>
 * <li>The own first person (user 2026-10-17, docs/dev/first_person_gun_view_v1_design.md): a held gun is its old view
 * model's (its hip and ADS hold, rigid arms, clips; PlayerSkeleton#firstPersonHands). The own body is posed as others see
 * it, its arms and the gun hidden; it draws the gun while its own hands carry it back to the mount, and, looking down
 * with the gun at its ready ({@link #atReady}, user 2026-10-19), in place of the view model (client/FirstPersonBody#gunOnBody).</li>
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
        /** When the current clip instance was first seen (level s): a reload's or an inspect's action starts there. */
        double clipStart;
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

    /**
     * The player holds a gun the skeleton carries, nothing in use. Always right-handed (mixin/PlayerGunRightArmMixin); the
     * off hand is empty but for the tick before inventory/NoOffHand clears it.
     */
    public static boolean holds(Player p) {
        return carried(p.getMainHandItem()) != null && p.getOffhandItem().isEmpty() && !p.isUsingItem();
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

    /**
     * The own first person may hand the held gun from its view model to the body's arms looking down
     * (client/FirstPersonBody#gunOnBody): a gun with a stance idle loop (the P9: its chest ready,
     * docs/dev/p9_stance_loops_v1.md), on the feet, at that ready or lowered for a sprint, and nothing else going on: not
     * aimed, not raised (a shot's {@link #RAISED_AFTER_SHOT}, a reload, an inspect), no clip of its own playing (draw,
     * first draw, shot, reload, inspect), not being drawn or put away. Other guns (the BR51: its stance follows the view)
     * keep their view model.
     */
    public static boolean atReady(Player p, float partial) {
        if (!PlayerSkeleton.supportedPose(p) || !holds(p) || loops(p.getMainHandItem()).idle() == null) return false;
        Track t = TRACKS.get(p.getId());
        // t.clip is the action clip the last body solve saw (#hold): "" when the gun's action controller is stopped
        if (t == null || t.fresh || !t.ghost.isEmpty() || !t.clip.isEmpty() || !sameGun(t.held, p.getMainHandItem())) return false;
        if (t.drawStart >= 0 && now(partial) < t.drawStart + t.drawLength) return false;
        return raise(t, partial) < 0.02F && aim(p, partial) < 0.02F;
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
        // a pistol's idle loop may stand bladed (PlayerAction#chestTwist), the raised gun square
        PlayerAction idle = loops(drawn(p)).idle();
        float idleTwist = idle == null ? 0F : idle.chestTwist;
        return (c.kind().chestTwist * up + idleTwist * (1F - up)) * (1F - lower);
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
            boolean aiming = mc.player.getMainHandItem().getItem() instanceof NativeGunItem && NativeGunAds.bodyProgress(1F) > 0.5F;
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
        boolean aiming = aim(p, 1F) > 0.05F;
        // an ADS reload keeps the aim's raise and grips: its action (shorter than the clip) eases out into the aim (2.7)
        boolean acting = (t.clip.startsWith("reload") || t.clip.startsWith("inspect"))
                && !t.clip.endsWith(com.antaurora.apofirstlight.weapon.NativeGunActions.ADS_SUFFIX);
        // a hip-fire idle loop (docs/dev/p9_stance_loops_v1.md) is already held out on the crosshair: a shot leaves it there
        PlayerAction idle = loops(hand).idle();
        boolean hipFire = idle != null && idle.hipFire;
        boolean raised = aiming || acting || !hipFire && now - t.lastShot < RAISED_AFTER_SHOT;
        t.raise += ((raised ? 1F : 0F) - t.raise) * 0.4F;
        t.lower += ((p.isSprinting() && !aiming ? 1F : 0F) - t.lower) * 0.3F;
        t.onAnchor += ((acting ? 1F : 0F) - t.onAnchor) * 0.45F;
    }

    /** A gun's draw or put-away as a player action: its id, when it started (level s) and its speed. */
    record GunAction(String id, double started, float speed) {}

    /**
     * The gun's draw or put-away as a player action (docs/dev/p9_draw_holster_actions_v1.md 2.4): {@code gun/<id path>_holster}
     * while the gun let go of goes back to its mount, from when it was let go of; {@code gun/<id path>_draw} while the held
     * gun is drawn, from when the hands are free ({@code drawBegin}). Played at the speed that fits the gun's clip into its
     * window: a put-away cut short by the draw after it, and that draw, play faster. Null: none, or the gun has no such action
     * (its draw stays on the path).
     */
    static GunAction action(Player p, double now) {
        Track t = TRACKS.get(p.getId());
        if (t == null) return null;
        if (!t.ghost.isEmpty()) {
            String id = actionId(t.ghost, "_holster");
            float clip = clipSeconds(t.ghost, "put_away", 0.4F);
            return id == null ? null : new GunAction(id, t.ghostStart, clip / Math.max(0.05F, t.ghostLength));
        }
        if (holds(p) && t.drawStart >= 0 && now < t.drawStart + t.drawLength) {
            String id = actionId(p.getMainHandItem(), "_draw");
            double window = t.drawStart + t.drawLength - t.drawBegin;
            return id == null ? null : new GunAction(id, t.drawBegin, (float) (t.drawLength / Math.max(0.05, window)));
        }
        // a reload or an inspect (docs/dev/p9_reload_inspect_v1.md 3): gun/<id path>_<clip>, from the clip's start at its
        // speed (the action is as long as the clip), while the clip runs
        if (holds(p) && (t.clip.startsWith("reload") || t.clip.startsWith("inspect"))) {
            String id = actionId(p.getMainHandItem(), "_" + t.clip);
            if (id == null) return null;
            // an action may end before its clip (an ADS reload is back on the sights early, 2.7): it then eases out into the aim
            float length = PlayerActions.get(id).length;
            float window = clipSeconds(p.getMainHandItem(), t.clip, 0F);
            if (length > 0) window = Math.min(window, length);
            return now < t.clipStart + window ? new GunAction(id, t.clipStart, 1F) : null;
        }
        return null;
    }

    private static String actionId(ItemStack stack, String suffix) {
        if (carried(stack) == null) return null;
        String id = "gun/" + ((NativeGunItem) stack.getItem()).definition().id().getPath() + suffix;
        return PlayerActions.get(id) == null ? null : id;
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

    /** The own aim in any camera (NativeGunAds#bodyProgress: the own third person raises the gun too), the others' as sent. */
    private static float aim(Player p, float partial) {
        if (p == Minecraft.getInstance().player) return NativeGunAds.bodyProgress(partial);
        float[] e = REMOTE_PROGRESS.get(p.getId());
        return e == null ? 0F : e[0] + (e[1] - e[0]) * partial;
    }

    // ---------------------------------------------------------------- stance loops

    /** A gun's stance loops (docs/dev/p9_stance_loops_v1.md): at the ready ({@code _idle}), lowered sprinting ({@code _sprint}). */
    private record Loops(PlayerAction idle, PlayerAction sprint) {
        static final Loops NONE = new Loops(null, null);
    }

    private static Loops loops(ItemStack stack) {
        if (carried(stack) == null) return Loops.NONE;
        return new Loops(loop(stack, "_idle"), loop(stack, "_sprint"));
    }

    private static PlayerAction loop(ItemStack stack, String suffix) {
        String id = actionId(stack, suffix);
        PlayerAction a = id == null ? null : PlayerActions.get(id);
        return a != null && a.loop != PlayerAction.Loop.NONE ? a : null;
    }

    /**
     * This frame's aim progress, how far the drawn gun is raised from its ready (a rifle: always up) and how far it is
     * lowered for a sprint: {aim, raise, lower}.
     */
    private static float[] state(Player p, Track t, GunStance.Kind kind, boolean ghost, float partial) {
        float aim = ghost ? 0F : aim(p, partial);
        float lower = t == null ? 0F : t.lowerO + (t.lower - t.lowerO) * partial;
        float raise = kind.readyHand != null ? Math.max(raise(t, partial), aim) : 1F;
        return new float[]{aim, raise, lower};
    }

    /**
     * Before the body solve: the drawn gun's stance loops' clips (HumanoidPoser.Input#stance), the idle by how far the gun is
     * down at its ready and not lowered, the sprint by how far it is lowered. Their props and hands come in {@link #hold}.
     */
    static void stanceClips(Player p, HumanoidPoser.Input in, SkeletonRig rig, float partial) {
        ItemStack stack = drawn(p);
        GunStance.Carried c = carried(stack);
        if (c == null) return;
        Loops loops = loops(stack);
        if (loops.idle() == null && loops.sprint() == null) return;
        Track t = TRACKS.get(p.getId());
        float[] s = state(p, t, c.kind(), t != null && !t.ghost.isEmpty(), partial);
        double now = now(partial);
        stanceClip(in, rig, loops.idle(), (1F - s[1]) * (1F - s[2]), now);
        stanceClip(in, rig, loops.sprint(), s[2], now);
    }

    private static void stanceClip(HumanoidPoser.Input in, SkeletonRig rig, PlayerAction a, float weight, double now) {
        if (a == null || a.clip == null || weight <= 0) return;
        SkeletonClips.Clip clip = SkeletonClips.byKey(a.clip);
        if (clip != null)
            in.stance.add(new HumanoidPoser.Input.StanceClip(clip, ActionPose.loopSeconds(a, now, in.walkPhase), weight, a.mask(rig, false)));
    }

    /**
     * A stance loop at this moment: the gun where it places its gun prop (model blocks), and both hands' IK weights and
     * poles from its timelines (the hands themselves go on the gun's grips: GunStance#handTarget).
     */
    private record Looped(Matrix4f gun, float right, Vector3f poleRight, float left, Vector3f poleLeft) {}

    /** {@link Looped} for a loop at {@code seconds}, or null (no loop, no gun prop placed). {@code ahead}: Moment#viewAhead. */
    private static Looped looped(SkeletonPose pose, boolean slim, ItemStack stack, PlayerAction action, float seconds, Matrix4f view,
                                 float ahead) {
        if (action == null) return null;
        int track = gunTrack(stack, action);
        if (track < 0) return null;
        ActionPose.Moment m = new ActionPose.Moment();
        m.action = action;
        m.seconds = seconds;
        m.view = view;
        m.viewAhead = ahead;
        ActionPose.props(pose, m);
        Matrix4f placed = m.props[track];
        if (placed == null) return null;
        PlayerProps.Prop prop = PlayerProps.get(action.props.get(track).id());
        HumanoidPoser.Input hands = new HumanoidPoser.Input();
        hands.slim = slim;
        hands.ikRightWeight = hands.ikLeftWeight = 0F;
        ActionPose.handsAt(hands, pose, PlayerSkeleton.poser(), m, 1F);
        return new Looped(new Matrix4f().scaling(1F / 16F).mul(placed).mul(prop.itemToLocal()), hands.ikRightWeight,
                new Vector3f(hands.poleRight), hands.ikLeftWeight, new Vector3f(hands.poleLeft));
    }

    /** The index of the action's prop that is this gun (PlayerProps.Prop#gun), or -1. */
    private static int gunTrack(ItemStack stack, PlayerAction action) {
        String path = ((NativeGunItem) stack.getItem()).definition().id().getPath();
        for (int i = 0; i < action.props.size(); i++) {
            PlayerProps.Prop p = PlayerProps.get(action.props.get(i).id());
            if (p != null && path.equals(p.gun())) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- per frame

    /**
     * The gun the skeleton draws this frame: the stack, its frame (gun blocks to model blocks), the bone held still; and a
     * gun held meanwhile that waits on its mount for the hands ({@code parked}, bind pose, or EMPTY); how thick the arms
     * are drawn ({@code arms}, GunStance#firstPersonArm: thinner in the own first person with the gun held out); the gun's
     * parts its action places as props ({@code parts}: a reload's magazines, #parts; NativeGunContextRenderer#skeletonDraw).
     */
    record Hold(ItemStack stack, Matrix4f gun, String still, ItemStack parked, Matrix4f parkedAt, float arms, Map<String, Matrix4f> parts) {}

    /**
     * Places the held (or put-away) gun for this frame and sets the arm inputs: IK targets on its anchors, how much each
     * hand is on it, shoulders, the support hand's slide. The body is already posed (HumanoidPoser#solveBody) and written to
     * {@code model}'s parts (the mounts hang on them). {@code eye}: render-origin blocks; {@code toModel}: render-origin
     * blocks to model blocks; {@code moment}: the gun's draw or put-away action ({@link #action}), or null. Null when no gun
     * is drawn.
     */
    static Hold hold(AbstractClientPlayer p, PlayerModel<?> model, SkeletonPose pose, HumanoidPoser.Input in, Vec3 eye,
                     float yaw, float pitch, float partial, boolean firstPerson, Matrix4f toModel, ActionPose.Moment moment) {
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
        rightRest = carried.rightHand(rightRest == null ? new Matrix4f() : rightRest);

        // the eye frame (view space to model blocks); in first person the view model's sway and recoil move it, the gun and
        // the hands follow. As px with the rest model's axes it is the actions' view frame (docs/dev/p9_stance_loops_v1.md)
        Matrix4f eyeFrame = new Matrix4f(toModel).mul(GunStance.eye(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z), yaw, pitch));
        if (!firstPerson && !FMLEnvironment.production && !viewChecked) checkView(eyeFrame, in);
        if (firstPerson) {
            PoseStack extra = new PoseStack();
            NativeWeaponSway.apply(extra, partial);
            NativeGunRecoil.applyViewmodel(extra);
            eyeFrame.mul(extra.last().pose());
        }
        Matrix4f view = viewFrame(eyeFrame);
        // first person with a gun held out on its view frame (a stance loop): the arms reach further, the view frame's keys
        // ahead along the look (GunStance#firstPersonReach, docs/dev/p9_stance_loops_v1.md 4.2)
        Loops loops = loops(stack);
        float ahead = firstPerson && loops.idle() != null ? GunStance.firstPersonReach(pitch) : 0F;

        // the gun's action (docs/dev/p9_draw_holster_actions_v1.md 2.5): the gun where it places its prop, the hands from
        // its timelines at full weight, before the stance sets the shoulders (as the plugin poses them)
        Acted acted = moment == null ? null : acted(model, pose, in, stack, moment, view, ahead);
        // drawn or put away along the path otherwise: how far from / to its mount, how much each hand is on it
        GunStance.Phase phase = null;
        if (acted == null && ghost) {
            phase = GunStance.putAway(kind, (float) ((now - t.ghostStart) / Math.max(0.05F, t.ghostLength)));
        } else if (acted == null && t != null && t.drawStart >= 0 && now < t.drawStart + t.drawLength) {
            double begin = t.drawBegin;
            float f = (float) ((now - begin) / Math.max(0.05, t.drawStart + t.drawLength - begin));
            phase = GunStance.draw(kind, Math.max(0F, f));
        }
        Matrix4f mount = phase == null ? null : mount(model, stack);
        boolean onPath = mount != null || acted != null;

        // the gun's hand anchors as its clips pose them this frame
        GunBones.Action action = GunBones.evaluate(stack, firstPerson ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, partial);
        if (action != null && t != null && !ghost) {
            if (action.instance() != t.clipInstance) {
                t.clipInstance = action.instance();
                t.clipStart = now;
                if (action.clip().equals("shoot")) t.lastShot = now;
            }
            t.clip = action.clip();
        }
        // the clip's whole-gun move is the skeleton's path while on it, and is never wanted while its draw clip runs late
        boolean drawClip = action != null && (action.clip().contains("draw") || action.clip().equals("put_away"));
        String still = onPath || drawClip ? still(stack, carried) : null;
        Matrix4f rightAnchor = action == null ? null : GunBones.frame(stack, "right_hand_anchor", still);
        Matrix4f leftAnchor = action == null ? null : GunBones.frame(stack, "left_hand_anchor", still);
        rightAnchor = rightAnchor == null ? rightRest : carried.rightHand(rightAnchor);
        Matrix4f leftRest = sampler == null ? null : sampler.frame("left_hand_anchor", "static_idle", 0F);
        if (leftRest == null) leftRest = new Matrix4f();
        leftAnchor = carried.leftHand(leftAnchor == null ? leftRest : leftAnchor, leftRest);

        // where it is held: a rifle on the shoulder (to the eye aiming in first person); a pistol raised against the eye or
        // at its two-handed ready before the chest (or its idle loop); lowered (sprinting; or its sprint loop)
        float[] state = state(p, t, kind, ghost, partial);
        float aim = state[0], raise = state[1], lower = state[2];
        float up = raise * (1F - lower);
        Looped idle = loops.idle() == null ? null
                : looped(pose, in.slim, stack, loops.idle(), ActionPose.loopSeconds(loops.idle(), now, in.walkPhase), view, ahead);
        Looped sprint = loops.sprint() == null ? null
                : looped(pose, in.slim, stack, loops.sprint(), ActionPose.loopSeconds(loops.sprint(), now, in.walkPhase), view, ahead);
        // the shoulders reach as much further while the gun is out on the view frame (not raised to the eye, not lowered)
        in.protraction += ahead * (1F - raise) * (1F - lower);
        // a hip-fire idle is held out as the raised gun is: both brought back toward the eye where the arms fall short
        boolean hipFire = idle != null && loops.idle().hipFire;
        float out = hipFire ? 1F - lower : up;
        Matrix4f raised = new Matrix4f(eyeFrame);
        Vector3f back = raised.transformDirection(new Vector3f(0, 0, 1)).normalize(); // the view's +z, model axes
        if (kind.pocket != null) {
            Matrix4f shouldered = GunStance.onShoulder(raised, pose.world[pose.rig.chest], profile, kind, carried);
            float toEye = firstPerson ? aim : 0F;
            raised = toEye > 0 ? GunStance.blend(shouldered, raised.mul(GunStance.inView(profile, kind, real, 1F)), toEye) : shouldered;
        } else {
            raised.mul(GunStance.inView(profile, kind, real, aim));
        }
        Matrix4f chest = GunStance.chestView(pose.world[pose.rig.chest], pose.rig.pivot[pose.rig.chest]);
        Matrix4f lowered = sprint != null ? sprint.gun() : new Matrix4f(chest).mul(GunStance.lowInChest(profile, kind, real, rightRest));
        Matrix4f ready = idle != null ? idle.gun() : kind.readyHand == null ? null
                : new Matrix4f(chest).mul(GunStance.readyInChest(profile, kind, real, rightRest));
        Matrix4f base = ready == null ? raised : GunStance.blend(ready, raised, raise);
        Matrix4f stance = GunStance.blend(lowered, base, 1F - lower);
        // how much each hand is on the gun: raised both; at the ready both or as the idle loop has it; lowered the firing
        // hand (a rifle both) or as the sprint loop has it
        float readyRight = idle == null ? 1F : idle.right(), readyLeft = idle == null ? 1F : idle.left();
        float lowRight = sprint == null ? 1F : sprint.right();
        float lowLeft = sprint == null ? (kind == GunStance.Kind.RIFLE ? 1F : 0F) : sprint.left();
        float baseRight = ready == null ? 1F : readyRight + (1F - readyRight) * raise;
        float baseLeft = ready == null ? 1F : readyLeft + (1F - readyLeft) * raise;
        float rightOn = lowRight + (baseRight - lowRight) * (1F - lower), leftOn = lowLeft + (baseLeft - lowLeft) * (1F - lower);
        float raisedShare = ready == null ? 1F : raise;
        Vector3f poleRight = pole(kind.poleRight, idle == null ? null : idle.poleRight(), sprint == null ? null : sprint.poleRight(), raisedShare, lower);
        Vector3f poleLeft = pole(kind.poleLeft, idle == null ? null : idle.poleLeft(), sprint == null ? null : sprint.poleLeft(), raisedShare, lower);
        in.right = in.left = HumanoidPoser.ArmMode.IK;
        if ((kind.sightDistance > 0 || kind.pocket != null) && out > 0) {
            // held out at arm's length or shouldered: back toward the eye where the arms are too short (looking up or far
            // down, crouched, a clip turning the gun away)
            in.ikRightWeight = rightOn;
            in.ikLeftWeight = leftOn;
            in.shoulderRight.set(kind.shoulderRight).mul(up);
            in.shoulderLeft.set(kind.shoulderLeft).mul(up * leftOn);
            boolean under = carried.support() != null && (t == null || t.onAnchor < 0.5F);
            stance = GunStance.inReach(kind, under ? carried.support() : null, PlayerSkeleton.poser(), in, pose, stance, back,
                    rightAnchor, leftAnchor);
        }

        Matrix4f gun = stance;
        float rightHand = rightOn, leftHand = leftOn;
        if (acted != null) {
            gun = GunStance.blend(stance, acted.gun, acted.weight);
        } else if (phase != null) {
            if (onPath) {
                Matrix4f middle = kind == GunStance.Kind.RIFLE ? new Matrix4f(chest).mul(GunStance.sideInChest(profile, real, rightRest))
                        : new Matrix4f().translation(0, -0.2F, 0).mul(mount);
                gun = GunStance.path(mount, middle, stance, phase.along());
            }
            rightHand = phase.rightHand() * rightOn;
            leftHand = phase.leftHand() * leftOn;
        }

        in.ikRightWeight = rightHand;
        in.ikLeftWeight = leftHand;
        GunStance.handTarget(gun, rightAnchor, kind.gripRight, true, in.slim, in.targetRight);
        GunStance.handTarget(gun, leftAnchor, kind.gripLeft, false, in.slim, in.targetLeft);
        in.poleRight.set(poleRight);
        in.poleLeft.set(poleLeft);
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
        if (acted != null) acted.mix(in);
        // a gun taken meanwhile waits on its mount until the hands are back (its own draw starts after the put-away)
        ItemStack parked = ghost && carried(held) != null ? held : ItemStack.EMPTY;
        Matrix4f parkedAt = parked.isEmpty() ? null : mount(model, parked);
        return new Hold(stack, gun, still, parkedAt == null ? ItemStack.EMPTY : parked, parkedAt, GunStance.firstPersonArm(ahead),
                acted == null ? Map.of() : acted.parts());
    }

    /**
     * A gun action at this moment: the gun where it places its gun prop (model blocks), how much it shows, and both hands'
     * IK targets, weights and poles from its timelines at full weight.
     */
    private record Acted(Matrix4f gun, float weight, Matrix4f right, float rightWeight, Vector3f poleRight,
                         Matrix4f left, float leftWeight, Vector3f poleLeft, Map<String, Matrix4f> parts) {
        /**
         * Over the stance's hands (already in {@code in}), by the action's weight: each target between the two as their
         * weights share it, the weight between them; the shoulders and the support hand's slide go with the stance.
         */
        void mix(HumanoidPoser.Input in) {
            float w = weight;
            in.ikRightWeight = mix(in.targetRight, in.ikRightWeight, right, rightWeight, w, true, in.slim);
            in.ikLeftWeight = mix(in.targetLeft, in.ikLeftWeight, left, leftWeight, w, false, in.slim);
            in.poleRight.lerp(poleRight, w);
            in.poleLeft.lerp(poleLeft, w);
            in.shoulderRight.mul(1F - w);
            in.shoulderLeft.mul(1F - w);
            in.slideLeft.mul(1F - w);
        }

        private static float mix(Matrix4f target, float stanceWeight, Matrix4f acted, float actedWeight, float w, boolean right, boolean slim) {
            float s = (1F - w) * stanceWeight, a = w * actedWeight, total = s + a;
            if (total > 1e-5F) target.set(ActionPose.blend(target, acted, a / total, right, slim));
            return total;
        }
    }

    /** An elbow's pole: the kind's raised, the idle loop's at the ready, the sprint loop's lowered (null: the kind's). */
    private static Vector3f pole(Vector3f kind, Vector3f idle, Vector3f sprint, float raise, float lower) {
        Vector3f base = idle == null ? new Vector3f(kind) : new Vector3f(idle).lerp(kind, raise);
        return new Vector3f(sprint == null ? kind : sprint).lerp(base, 1F - lower);
    }

    /** Dev: the gun props whose live mount was compared with their file's carry preview. */
    private static final java.util.Set<String> CARRY_CHECKED = new java.util.HashSet<>();

    /**
     * The gun action's gun and hands this frame, or null when it places no gun prop for this stack (then the path draws
     * it). The action's {@code carry} frame is the gun's live mount; {@code ahead}: Moment#viewAhead.
     */
    private static Acted acted(PlayerModel<?> model, SkeletonPose pose, HumanoidPoser.Input in, ItemStack stack, ActionPose.Moment m,
                               Matrix4f view, float ahead) {
        int track = gunTrack(stack, m.action);
        PlayerProps.Prop prop = track < 0 ? null : PlayerProps.get(m.action.props.get(track).id());
        if (prop == null) return null;
        Matrix4f mount = mount(model, stack);
        m.carry = mount == null ? null : carryPlacement(mount, prop);
        m.view = view;
        m.viewAhead = ahead;
        if (m.carry != null && !FMLEnvironment.production && CARRY_CHECKED.add(prop.id())) checkCarry(pose, prop, m.carry);
        ActionPose.props(pose, m);
        Matrix4f placed = m.props[track];
        if (placed == null) return null;
        Matrix4f gun = new Matrix4f().scaling(1F / 16F).mul(placed).mul(prop.itemToLocal());
        in.ikRightWeight = in.ikLeftWeight = 0F;
        ActionPose.handsAt(in, pose, PlayerSkeleton.poser(), m, 1F);
        return new Acted(gun, ActionPose.weight(m), new Matrix4f(in.targetRight), in.ikRightWeight, new Vector3f(in.poleRight),
                new Matrix4f(in.targetLeft), in.ikLeftWeight, new Vector3f(in.poleLeft), parts(stack, m, placed, prop));
    }

    /**
     * The action's gun parts (PlayerProps.Prop#gunPart: a magazine out of the gun) for this gun: bone name to its frame in
     * the gun's GeckoLib frame (blocks, about the root's origin), null while the prop is hidden; empty when it has none.
     */
    private static Map<String, Matrix4f> parts(ItemStack stack, ActionPose.Moment m, Matrix4f gunPlaced, PlayerProps.Prop gunProp) {
        String path = ((NativeGunItem) stack.getItem()).definition().id().getPath();
        Map<String, Matrix4f> out = null;
        for (int i = 0; i < m.action.props.size(); i++) {
            PlayerProps.Prop prop = PlayerProps.get(m.action.props.get(i).id());
            if (prop == null || prop.gunPart() == null || !path.equals(prop.gunPart().gun())) continue;
            if (out == null) out = new HashMap<>();
            Matrix4f placed = m.props[i];
            out.put(prop.gunPart().bone(), placed == null || m.propHidden[i] ? null
                    : new Matrix4f(gunProp.itemToLocal()).invert().mul(new Matrix4f(gunPlaced).invert()).mul(placed).mul(prop.itemToLocal()));
        }
        return out == null ? Map.of() : out;
    }

    /**
     * The actions' {@code view} frame (ActionPose.Moment#view) from the eye frame: px, the rest model's axes (view space is
     * x right, y up; the model's x left, y down).
     */
    static Matrix4f viewFrame(Matrix4f eyeFrame) {
        return new Matrix4f().scaling(16F).mul(eyeFrame).scale(-1F / 16F, -1F / 16F, 1F / 16F);
    }

    /** Dev: the view frame was compared with the head's turn (ActionPose#viewFrame, as the plugin and the checks build it). */
    private static boolean viewChecked;

    private static void checkView(Matrix4f eyeFrame, HumanoidPoser.Input in) {
        if (in.eyeTarget == null) return;
        viewChecked = true;
        Matrix4f game = viewFrame(eyeFrame), offline = ActionPose.viewFrame(in.eyeTarget, in.viewYaw, in.viewPitch);
        float worst = 0F;
        for (int c = 0; c < 4; c++) for (int r = 0; r < 3; r++) worst = Math.max(worst, Math.abs(game.get(c, r) - offline.get(c, r)));
        ApocalypseFirstLight.LOGGER.info("[AFL skeleton] view frame against the head's turn: worst {} (px / cosine)", worst);
    }

    /**
     * The {@code carry} frame from a gun's mount (gun blocks to model blocks): the gun prop's model frame (px, the gun's
     * frame turned half round x, PlayerProps.Prop#itemToLocal) to model px, the mount's size included.
     */
    static Matrix4f carryPlacement(Matrix4f mount, PlayerProps.Prop prop) {
        float k = 16F * prop.scale();
        return new Matrix4f().scaling(16F).mul(mount).scale(1F / k, -1F / k, -1F / k);
    }

    /** Dev: warns once when a gun prop's file preview of its mount is more than 0.05 px off the live one (its bounds' corners). */
    private static void checkCarry(SkeletonPose pose, PlayerProps.Prop prop, Matrix4f live) {
        Matrix4f preview = ActionPose.carryPreview(pose, prop);
        if (preview == null) return;
        float worst = 0F;
        for (int c = 0; c < 8; c++) {
            Vector3f corner = prop.local(new Vector3f((c & 1) == 0 ? prop.min().x : prop.max().x,
                    (c & 2) == 0 ? prop.min().y : prop.max().y, (c & 4) == 0 ? prop.min().z : prop.max().z));
            worst = Math.max(worst, preview.transformPosition(new Vector3f(corner)).distance(live.transformPosition(new Vector3f(corner))));
        }
        if (worst > 0.05F)
            ApocalypseFirstLight.LOGGER.warn("[AFL skeleton] gun prop {}: its carry preview is {} px off the live mount", prop.id(), worst);
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
     * Draws the gun at its model-space frame (blocks; rigid times uniform scale) under the model's pose, in its third-person
     * context with its clips playing ({@link NativeGunContextRenderer#skeletonDraw}). The own first person shows a held gun
     * through its old view model instead (PlayerSkeleton#firstPersonHands): the body draws one on its way to the mount, or
     * held at its ready looking down (client/FirstPersonBody#gunOnBody).
     */
    static void render(Hold hold, PoseStack modelPose, MultiBufferSource buffers, int light) {
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
        NativeGunContextRenderer.skeletonDraw(true, hold.still(), hold.parts());
        try {
            renderer.renderByItem(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } finally {
            NativeGunContextRenderer.skeletonDrawEnd();
        }
    }
}
