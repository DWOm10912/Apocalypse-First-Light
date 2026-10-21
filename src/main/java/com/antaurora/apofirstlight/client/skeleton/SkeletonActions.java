package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.NozzleFillView;
import com.antaurora.apofirstlight.client.CrowbarSmashClient;
import com.antaurora.apofirstlight.client.PortableGeneratorPull;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Player actions V1 on the client (docs/dev/player_actions_v1_plan.md 2.7-2.9): which action each player plays, read from
 * state every client already has, so no packet:
 * <ul>
 * <li>an item in use with an {@code item_actions} mapping (clinical thermometer): vanilla syncs who uses what and how long;</li>
 * <li>the recoil starter held or pulled (client/PortableGeneratorPull: the set syncs its gripper and puller);</li>
 * <li>a fuel nozzle in an opening (client/NozzleFillView: the dispenser syncs which opening and when);</li>
 * <li>a crowbar smash (S2; client/CrowbarSmashClient: the server sends each smash's start and impact to the players
 * around), its hand anchors from the view ({@link PlayerAction.ViewAnchor});</li>
 * <li>a jerry can in the main hand (S2): carried, or pouring (item/FuelCanItem's pour tag, synced with the stack); the
 * pour's time runs back twice as fast after it stops, so the can is tipped back and lowered;</li>
 * <li>first of all, a landing that hurt (health/FallLanding sends it; client/FallLandingClient): body/land_hard or
 * body/fall_over, mirrored when the right hand goes down;</li>
 * <li>then a gun's draw or put-away (SkeletonGunStance#action: {@code gun/<id>_draw}, {@code gun/<id>_holster};
 * docs/dev/p9_draw_holster_actions_v1.md 2.4), played only with the gun stance (PlayerSkeleton).</li>
 * </ul>
 * A source with its own clock (item use, crowbar, jerry can) sets the action's time every frame; the others count from
 * when this client first saw it. Actions that keep their first-person view model ({@link PlayerAction.FirstPerson}) are
 * not drawn on the own first-person body ({@link #drawsFirstPerson}).
 * Each player keeps a track: when the action started (as this client first saw it, or the item's use time), its last
 * anchors, and when it stopped, so it eases out over its blend-out. While an action steps the body away from the eye
 * ({@link PlayerAction.Approach}), the own first-person camera sits on the posed eye ({@link #camera}) and the body stays
 * drawn (client/FirstPersonBody#shown).
 * <p>
 * Dev only ({@code /aflc action play <id> [speed]}, {@code /aflc action stop}): plays an action on the own player without
 * any game state, block anchors one block ahead (generator handle 0.35 high, frame 0.67, fuel opening 0.9).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkeletonActions {
    /** What a player does now, as its source sees it: the anchors in the world, the parameter, the side, its time's speed. */
    private record Live(String id, double started, float param, Map<String, Vec3> anchors, boolean mirror, float speed) {
        Live(String id, double started, float param, Map<String, Vec3> anchors, boolean mirror) {
            this(id, started, param, anchors, mirror, 1F);
        }
    }

    private static final class Track {
        String id;
        PlayerAction action;
        double start, shown, stoppedAt = -1;
        Live last;
    }

    private static final String CROWBAR = "tool/crowbar_smash", CROWBAR_HOLD = "item/crowbar", JERRY_CAN = "item/jerry_can";
    /** The crowbar hold's swing (s of its time): the melee attack swing's progress (0..1) times this. */
    private static final double CROWBAR_SWING = 0.3;
    /** The pour's time stops here (s), just past the tip's last key, so a stop tips back at once (the stream runs on). */
    private static final double POUR_MAX = 3.2;
    /**
     * The pour's lean (the action's parameter clip): none for an opening this high over the feet (blocks), full this much
     * lower; it comes in over the step's time (the action's approach in).
     */
    private static final double POUR_UPRIGHT = 1.0, POUR_LEAN_SPAN = 0.6, POUR_STEP_IN = 0.9;
    /** Poured standing (a spout too high to crouch to, {@link #pourCrouched}), the lean starts this much higher (blocks). */
    private static final double POUR_STAND_RISE = 0.35;
    /** A spout this high over the feet or higher is poured standing, a lower one crouched (blocks). */
    private static final double POUR_CROUCH_BELOW = 0.75;
    /**
     * Where the tipped can's spout is held (the action's anchor {@code spout}): this far over the fill opening, but at
     * least this high over the feet (blocks; a crouched hand reaches no lower), so a fill cover in the ground is poured
     * into from above it.
     */
    private static final double SPOUT_OVER = 0.2, SPOUT_LOWEST = 0.65;
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    /** Each player's jerry can pour time (s) and the clock it was last set at. */
    private static final Map<Integer, double[]> POURS = new HashMap<>();
    /** Each pouring player's fill opening (world), kept while the pour's time runs back after it stops. */
    private static final Map<Integer, Vec3> OPENINGS = new HashMap<>();
    /** Each player's prop points this frame (world), from its last level draw: name -> point. */
    private static final Map<Integer, Map<String, Vec3>> PROP_POINTS = new HashMap<>();
    private static final Map<Integer, Long> PROP_FRAMES = new HashMap<>();
    /** Each stepping player's body offset this frame (render-origin blocks), for its shadow (through the camera director). */
    private static final Map<Integer, Vec3> STEPS = new HashMap<>();
    private static long stepsFrame = -1;
    /** The own posed eye (world) and the frame it was solved in; whether the own action steps the body away. */
    private static Vec3 eye;
    private static long eyeFrame = -10;
    private static boolean stepping;
    /** The view's turn while stepping (deg, Minecraft's yaw and pitch) and how much of it (pitch NaN: the player's own). */
    private static float lookYaw, lookPitch = Float.NaN, lookWeight;
    private static long frame;
    /**
     * PlayerAction#firstPersonPitch: how fast the own first-person view eases down to it (per second, exponential), and the
     * clock of the last frame it did.
     */
    private static final double LOOK_DOWN_RATE = 6.0;
    private static long lookDownFrame;
    // dev play
    private static String devAction;
    private static float devSpeed = 1F;
    private static double devStart;

    private SkeletonActions() {
    }

    /** The skeleton draws the acting arms itself (the old first-person stand-in arms stay away). */
    public static boolean drawsArms() {
        return PlayerSkeleton.enabled() && FirstPersonBodyConfig.showBody();
    }

    /** A player plays (or eases out of) an action this frame. */
    static boolean active(Player p) {
        Track t = TRACKS.get(p.getId());
        return t != null && t.action != null;
    }

    /** A player plays an action the own first-person body draws (not one that keeps its view model). */
    static boolean drawsFirstPerson(Player p) {
        Track t = TRACKS.get(p.getId());
        return t != null && t.action != null && t.action.firstPerson == PlayerAction.FirstPerson.BODY;
    }

    /** After a level draw that placed the action's prop: its named points this frame (world). */
    static void propPoints(AbstractClientPlayer p, Map<String, Vec3> points) {
        PROP_POINTS.put(p.getId(), points);
        PROP_FRAMES.put(p.getId(), frame);
    }

    /**
     * A point of the prop a player's action holds, as last drawn in the level (world), or null when it was not drawn in the
     * last frames (first person, out of view, no prop): client/FuelCanPourJets starts a third-person pour at the can's spout.
     */
    public static Vec3 propPoint(Player p, String name) {
        Long at = PROP_FRAMES.get(p.getId());
        if (at == null || frame - at > 2) return null;
        Map<String, Vec3> points = PROP_POINTS.get(p.getId());
        return points == null ? null : points.get(name);
    }

    /**
     * The action's moment for this draw, or null. {@code origin}: the render origin (world), {@code toModel}: render-origin
     * blocks to model blocks; {@code world}: a draw in the level (a GUI preview gets the clips, no step and no block hands).
     */
    static ActionPose.Moment moment(AbstractClientPlayer p, float partial, Vec3 origin, Matrix4f toModel, boolean world) {
        double now = now(partial);
        Live live = live(p, partial, now);
        Track t = TRACKS.computeIfAbsent(p.getId(), k -> new Track());
        if (live != null) {
            if (!live.id().equals(t.id) || t.stoppedAt >= 0) {
                PlayerAction a = PlayerActions.get(live.id());
                if (a == null) {
                    ApocalypseFirstLight.LOGGER.warn("[AFL skeleton] no player action {}", live.id());
                    TRACKS.remove(p.getId());
                    return null;
                }
                boolean resumed = live.id().equals(t.id) && t.stoppedAt >= 0;
                t.id = live.id();
                t.action = a;
                t.start = resumed ? t.start : now;
                t.shown = resumed ? t.shown : now;
                t.stoppedAt = -1;
            }
            if (!Double.isNaN(live.started())) t.start = live.started();   // a source with its own clock
            t.last = live;
        } else if (t.action != null && t.stoppedAt < 0) {
            t.stoppedAt = now;
        }
        if (t.action == null || t.last == null) return null;
        if (t.stoppedAt >= 0 && now - t.stoppedAt > t.action.blendOut) {
            TRACKS.remove(p.getId());
            return null;
        }
        ActionPose.Moment m = new ActionPose.Moment();
        m.action = t.action;
        double speed = p == Minecraft.getInstance().player && devAction != null ? devSpeed : t.last.speed();
        m.seconds = (float) ((t.stoppedAt >= 0 ? t.stoppedAt : now) - t.start) * (float) speed;
        m.stopped = t.stoppedAt >= 0 ? (float) (now - t.stoppedAt) : -1F;
        m.age = (float) ((t.stoppedAt >= 0 ? t.stoppedAt : now) - t.shown);
        m.param = t.last.param();
        m.mirror = t.last.mirror();
        Map<String, Vec3> anchors = world ? t.last.anchors() : Map.of();
        m.anchors = name -> {
            Vec3 w = anchors.get(name);
            if (w == null) return null;
            return toModel.transformPosition(new Vector3f((float) (w.x - origin.x), (float) (w.y - origin.y), (float) (w.z - origin.z))).mul(16F);
        };
        return m;
    }

    /** After the body solve of a level draw: how far the action stepped the body (model px), for the shadow this frame. */
    static void stepped(AbstractClientPlayer p, Vector3f rootOffset, Matrix4f toModel) {
        if (stepsFrame != frame) {
            STEPS.clear();
            stepsFrame = frame;
        }
        if (rootOffset.lengthSquared() < 1e-6F) return;
        Vector3f w = new Matrix4f(toModel).invert().transformDirection(new Vector3f(rootOffset).div(16F));
        STEPS.put(p.getId(), new Vec3(w.x, 0, w.z));
    }

    /**
     * A camera director body offset source (client/camera/CameraSources): how far this frame's action stepped the body
     * (world blocks, level), or null.
     */
    public static Vec3 bodyOffset(Entity entity) {
        return stepsFrame == frame ? STEPS.get(entity.getId()) : null;
    }

    /** After the solve: the own posed eye (world), for the first-person camera while the action steps the body away. */
    static void solved(AbstractClientPlayer p, SkeletonPose pose, ActionPose.Moment m, Vec3 origin, Matrix4f toModel) {
        if (p != Minecraft.getInstance().player) return;
        stepping = m != null && ActionPose.weight(m) > 0 && m.seconds > 0 && (m.action.firstPersonEye
                || m.action.approach != null && m.anchors.apply(m.action.approach.anchor()) != null);
        if (!stepping) return;
        Matrix4f fromModel = new Matrix4f(toModel).invert();
        Vector3f e = fromModel.transformPosition(ActionPose.cameraEye(pose).div(16F));
        eye = origin.add(e.x, e.y, e.z);
        eyeFrame = frame;
        lookPitch = ActionPose.pitch(m);
        if (!Float.isNaN(lookPitch)) {
            Vector3f f = fromModel.transformDirection(ActionPose.facing(pose));
            lookYaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
            lookWeight = ActionPose.weight(m);
        }
    }

    /** Camera#setup, with {@link #camera}: the view's yaw and pitch (deg) as the posed head's (the action's), eased in; or null. */
    public static float[] cameraRotation(float yaw, float pitch) {
        if (!stepping || Float.isNaN(lookPitch) || frame - eyeFrame > 2 || !drawsArms()) return null;
        return new float[]{Mth.rotLerp(lookWeight, yaw, lookYaw), Mth.lerp(lookWeight, pitch, lookPitch)};
    }

    /** Camera#setup (mixin client.PortableGeneratorCameraMixin): the own first-person camera on the posed eye, or null. */
    public static Vec3 camera(Camera camera) {
        if (!stepping || eye == null || frame - eyeFrame > 2 || !drawsArms()) return null;
        return eye;
    }

    /** client/FirstPersonBody#shown: the camera is on the own posed eye (the body is still the first-person body). */
    public static boolean cameraOnBody(Camera camera) {
        Vec3 e = camera(camera);
        return e != null && e.distanceToSqr(camera.getPosition()) < 1e-4;
    }

    static void newFrame() {
        frame++;
    }

    /**
     * PlayerAction#firstPersonPitch (health V1a, the bandage and the medical alcohol): while the own action runs in the own
     * first person, the view eases down to at least its pitch each frame after the mouse turned it (RenderTickEvent START, as
     * client/JerryCanPourView), so the work on the held-out forearm is in view; the mouse may still look lower. The head
     * looks down with it, as the others see it. Nothing in third person.
     */
    @SubscribeEvent
    public static void lookDown(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        double dt = lookDownFrame == 0 ? 0 : Math.min(0.1, (now - lookDownFrame) / 1e9);
        lookDownFrame = now;
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || !mc.options.getCameraType().isFirstPerson() || !drawsArms()) return;
        Track t = TRACKS.get(player.getId());
        if (t == null || t.action == null || t.stoppedAt >= 0 || t.action.firstPerson != PlayerAction.FirstPerson.BODY) return;
        float floor = t.action.firstPersonPitch;
        if (Float.isNaN(floor) || player.getXRot() >= floor) return;
        float p = player.getXRot() + (floor - player.getXRot()) * (float) (1 - Math.exp(-LOOK_DOWN_RATE * dt));
        player.setXRot(p);
        player.xRotO = p;
    }

    // ---------------------------------------------------------------- sources

    private static Live live(AbstractClientPlayer p, float partial, double now) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && devAction != null) return dev(p, partial, now);
        FallLandingClient.Playing landing = FallLandingClient.playing(p, now);
        if (landing != null) return new Live(landing.id(), landing.started(), 0F, Map.of(), landing.mirror());
        SkeletonGunStance.GunAction gun = SkeletonGunStance.action(p, now);
        if (gun != null) return new Live(gun.id(), gun.started(), 0F, Map.of(), false, gun.speed());
        PortableDieselGeneratorBlockEntity set = PortableGeneratorPull.heldSet(p);
        if (set != null && set.getLevel() != null) {
            Vec3[] a = PortableGeneratorPull.anchors(set, partial);
            return new Live("machine/generator_pull", Double.NaN, PortableGeneratorPull.travel(set, partial),
                    Map.of("pull_grip", a[0], "handle_rest", a[1], "brace", a[2]), false);
        }
        Vec3[] nozzle = NozzleFillView.anchors(p, partial);
        if (nozzle != null) {
            double h = nozzle[1].y - p.getPosition(partial).y;
            return new Live("item/nozzle_fill", Double.NaN, (float) Mth.clamp((0.9 - h) / 0.6, 0, 1),
                    Map.of("nozzle_grip", nozzle[0], "opening", nozzle[1]), false);
        }
        double smash = CrowbarSmashClient.elapsed(p, partial);
        if (smash >= 0) {
            PlayerAction a = PlayerActions.get(CROWBAR);
            return new Live(CROWBAR, now - smash / 20.0, 0F, a == null ? Map.of() : viewAnchors(a, p, partial), p.getMainArm() == HumanoidArm.LEFT);
        }
        ItemStack main = p.getMainHandItem();
        if (main.is(AflItems.CROWBAR.get()))   // held; the melee swing's progress runs the hold's swing
            return new Live(CROWBAR_HOLD, now - p.getAttackAnim(partial) * CROWBAR_SWING, 0F, Map.of(), p.getMainArm() == HumanoidArm.LEFT);
        if (main.is(AflItems.JERRY_CAN.get())) {
            double[] pour = POURS.computeIfAbsent(p.getId(), k -> new double[]{0, now});
            long ticks = FuelCanItem.pourTicks(main, p.level());
            if (ticks >= 0) pour[0] = Math.min(POUR_MAX, (ticks + partial) / 20.0);
            else pour[0] = Math.max(0, pour[0] - 2 * Math.max(0, now - pour[1]));
            pour[1] = now;
            var cover = FuelCanItem.pourTarget(main);   // a block's opening or a vehicle filler's
            Vec3 at = cover == null ? null : FuelCanItem.pourOpening(p.level(), cover);
            if (at != null) OPENINGS.put(p.getId(), at);
            else if (pour[0] <= 0) OPENINGS.remove(p.getId());
            Vec3 opening = OPENINGS.get(p.getId());
            float param = 0F;
            Map<String, Vec3> anchors = Map.of();
            if (opening != null) {
                double feet = p.getPosition(partial).y;
                Vec3 spout = new Vec3(opening.x, feet + pourSpout(opening.y - feet), opening.z);
                // the torso leans further for a low spout, coming in with the step
                double low = Mth.clamp((POUR_UPRIGHT + (p.isCrouching() ? 0 : POUR_STAND_RISE) - (spout.y - feet)) / POUR_LEAN_SPAN, 0, 1);
                double in = Mth.clamp(pour[0] / POUR_STEP_IN, 0, 1);
                param = (float) (low * in * in * (3 - 2 * in));
                anchors = Map.of("opening", opening, "spout", spout);
            }
            return new Live(JERRY_CAN, now - pour[0], param, anchors, p.getMainArm() == HumanoidArm.LEFT);
        }
        POURS.remove(p.getId());
        OPENINGS.remove(p.getId());
        if (p.isUsingItem()) {
            ItemStack stack = p.getUseItem();
            float used = stack.getUseDuration() - p.getUseItemRemainingTicks() + partial;
            // the splint (fractures V1): the leg or the arm action by the limb it goes on, mirrored for a right limb (both
            // are keyed on the left one)
            if (stack.getItem() instanceof com.antaurora.apofirstlight.health.SplintItem) {
                var limb = com.antaurora.apofirstlight.health.SplintItem.target(p);
                if (limb != null) return new Live(limb.leg() ? "item/splint_leg" : "item/splint_arm", now - used / 20.0, 0F, Map.of(), limb.right());
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            String action = id != null && ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) ? PlayerActions.useAction(id.getPath()) : null;
            if (action != null) {
                HumanoidArm arm = p.getUsedItemHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
                boolean mirror = arm == HumanoidArm.LEFT;
                // the bandage and the alcohol work on the wound's forearm (the left one as keyed; the right one mirrored)
                if (stack.getItem() instanceof com.antaurora.apofirstlight.health.MedicalItem medical)
                    mirror = com.antaurora.apofirstlight.client.BandageWrapRenderer.workedArm(p, medical.treatment()).right();
                return new Live(action, now - used / 20.0, 0F, Map.of(), mirror);
            }
        }
        return null;
    }

    /**
     * How high over the feet the jerry can's spout is held over a fill opening this high over them (blocks): over it, but
     * no lower than a hand reaches (the anchor {@code spout}).
     */
    public static double pourSpout(double openingOverFeet) {
        return Math.max(openingOverFeet + SPOUT_OVER, SPOUT_LOWEST);
    }

    /** A jerry can is poured crouched into a fill opening this high over the feet (blocks), standing into a higher one. */
    public static boolean pourCrouched(double openingOverFeet) {
        return pourSpout(openingOverFeet) < POUR_CROUCH_BELOW;
    }

    /** The own first-person camera on the stepped body this frame (world), or null when the action does not step it. */
    public static Vec3 cameraEye() {
        return stepping && eye != null && frame - eyeFrame <= 2 ? eye : null;
    }

    /** The action's view anchors for a player this frame (world): the own first-person view's where an anchor has one. */
    private static Map<String, Vec3> viewAnchors(PlayerAction a, AbstractClientPlayer p, float partial) {
        Vec3 e = p.getEyePosition(partial);
        org.joml.Vector3d eye = new org.joml.Vector3d(e.x, e.y, e.z);
        boolean mirror = p.getMainArm() == HumanoidArm.LEFT;
        Minecraft mc = Minecraft.getInstance();
        boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
        Map<String, Vec3> out = new HashMap<>();
        for (PlayerAction.ViewAnchor v : a.viewAnchors) {
            org.joml.Vector3d w = ActionPose.viewAnchor(v, eye, p.getViewYRot(partial), p.getViewXRot(partial), mirror, own);
            out.put(v.name(), new Vec3(w.x, w.y, w.z));
        }
        return out;
    }

    private static Live dev(AbstractClientPlayer p, float partial, double now) {
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 feet = p.getPosition(partial), ahead = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 at = feet.add(ahead);
        double t = (now - devStart) * devSpeed;
        float yank = (float) Math.max(0, Math.sin(t * Math.PI));   // a pull a second
        // as the set places them (block/PortableDieselGeneratorBlock HANDLE_REST, PULL; client/PortableGeneratorPull BRACE)
        Vec3 handle = at.add(0, 0.346, 0);
        Map<String, Vec3> anchors = Map.of(
                "handle_rest", handle, "pull_grip", handle.add(ahead.scale(-0.46 * yank)).add(0, 0.285 * yank, 0).add(left.scale(-0.1 * yank)),
                "brace", handle.add(left.scale(0.33)).add(0, 0.327, 0).add(ahead.scale(0.03)),
                "opening", at.add(0, 0.9, 0), "nozzle_grip", at.add(0, 1.0, 0).add(ahead.scale(-0.12)));
        return new Live(devAction, devStart, devAction.startsWith("machine/") ? yank : 0.5F, anchors, false);
    }

    private static double now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() + partial) / 20.0;
    }

    static void clear() {
        TRACKS.clear();
        POURS.clear();
        OPENINGS.clear();
        PROP_POINTS.clear();
        PROP_FRAMES.clear();
        STEPS.clear();
        eye = null;
        stepping = false;
        devAction = null;
    }

    // ---------------------------------------------------------------- dev command

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aflc").then(Commands.literal("action")
                .then(Commands.literal("play").then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(c -> play(c.getArgument("id", String.class), 1F))))
                .then(Commands.literal("speed").then(Commands.argument("speed", FloatArgumentType.floatArg(0.05F, 4F))
                        .executes(c -> {
                            devSpeed = FloatArgumentType.getFloat(c, "speed");
                            say("speed " + devSpeed);
                            return 1;
                        })))
                .then(Commands.literal("stop").executes(c -> {
                    devAction = null;
                    say("stopped");
                    return 1;
                }))));
    }

    private static int play(String id, float speed) {
        if (PlayerActions.get(id) == null) {
            say("no action " + id);
            return 0;
        }
        devAction = id;
        devStart = now(0F);
        say("playing " + id + (PlayerSkeleton.enabled() ? "" : " (the skeleton is off: [skeleton] enabled in the client config)"));
        return 1;
    }

    private static void say(String s) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("[AFL action] " + s), false);
    }
}
