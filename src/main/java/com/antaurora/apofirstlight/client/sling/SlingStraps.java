package com.antaurora.apofirstlight.client.sling;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.MaintenanceGunRendering;
import com.antaurora.apofirstlight.client.WoodGunRackRenderer;
import com.antaurora.apofirstlight.physics.rope.MeshCollider;
import com.antaurora.apofirstlight.physics.rope.MeshShape;
import com.antaurora.apofirstlight.physics.rope.BoxCollider;
import com.antaurora.apofirstlight.physics.rope.RopeChain;
import com.antaurora.apofirstlight.physics.rope.RopeCollider;
import com.antaurora.apofirstlight.weapon.NativeAttachment;
import com.antaurora.apofirstlight.weapon.NativeAttachments;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeSlingMount;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.apofirstlight.weapon.client.NativeMagazineRendering;
import com.antaurora.apofirstlight.weapon.client.NativeMuzzleRendering;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Gun sling straps (docs/native_guns/attachments/sling_physics_v1.md): a fitted sling hangs from the gun's two swivels as a
 * simulated strap (physics/rope RopeChain), a steel snap hook clipped to each swivel's bar. Client only, nothing synced.
 * <ul>
 * <li>in the hand (first person, the Z page, aiming, and third person alike): the strap is off the shoulder, a free loop
 * between the swivels, live, colliding with the gun's mesh, its magazine and the arms (third person: the whole body);</li>
 * <li>slung on the back: over the left shoulder, across the chest to the right hip and round to the butt, colliding with
 * the gun and the body;</li>
 * <li>the maintenance bench: a free loop, live, lying on the mat;</li>
 * <li>the gun rack and the ground: the loop settled once at rest (cached per gun, sling and which way is down), drawn into
 * the chunk with the gun.</li>
 * </ul>
 * Simulation space is metres on world axes. Each live strap keeps its last shape in the frame it is drawn from (the gun's
 * root, a body's root), so a shadow pass draws it without stepping. Players further than {@link #FAR} keep their last shape.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class SlingStraps {
    /** Webbing 32 x 2.5 mm; node radius (contact distance from the strap's centre line). */
    static final double WIDTH = 0.032, THICK = 0.0025, RADIUS = 0.004;
    /** Hook pin (the swivel bar's centre) to the strap's exit: tools/build-equipment-meshes-v1.mjs gunSlingHook points.pin. */
    static final double HOOK = 0.0817;
    /** A free loop's strap is this much longer than its swivels' distance; one round the body this much longer than its route. */
    private static final double FREE_SLACK = 1.12, BODY_SLACK = 1.03, SEGMENT = 0.025;
    private static final double FAR = 32;
    /**
     * Share of the holder's own motion the strap is carried along, the rest it feels as inertia: of the camera's turn and
     * walk in first person (a flick of the mouse is far faster than a real body turns), of the gun's motion in the hand
     * beyond that (aiming, recoil, an inspect), of the body's with the gun on the back.
     */
    private static final double CARRY_VIEW = 0.9, CARRY_HAND = 0.75, CARRY_BODY = 0.6;
    private static final double MARGIN = 0.5 / 16;
    /** A bone drawn at under this share of its size is being hidden: no collider. */
    private static final double SHOWN = 0.25;
    /** A strap placed again this soon after its last placement skips the presettle (a strap that keeps breaking stays cheap). */
    private static final int RESETTLE_FRAMES = 60;

    private static long frame, generation = Long.MIN_VALUE;
    private static boolean inLevel;
    private static Entity current;
    private static final Map<Object, Strap> LIVE = new HashMap<>();
    private static final Map<Integer, Held> HELD = new HashMap<>();
    private static final Map<String, float[]> STILL = new ConcurrentHashMap<>();
    private static final Map<MeshModel, Map<String, MeshShape>> SHAPES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ResourceLocation, List<Piece>> MAGAZINES = new ConcurrentHashMap<>();
    /** The first-person arms drawn this frame (view space), for the strap to collide with. */
    private static final List<Arm> ARMS = new ArrayList<>();
    private static long armsFrame = -1;

    private SlingStraps() {}

    // ------------------------------------------------------------------------------------------------- contexts

    /**
     * First person (the hand, the Z page, aiming): called by the gun renderer after its traversal with the gun root's pose
     * and every drawn bone's mesh frame (view space of this pass).
     */
    public static void firstPerson(ItemStack gun, MeshModel mesh, Matrix4fc root, Map<String, Matrix4f> bones, MultiBufferSource buffers, int light) {
        Rig rig = Rig.of(gun, mesh);
        if (rig == null) return;
        double s = Math.cbrt(Math.abs(root.determinant3x3()));
        if (!(s > 0)) return;
        // the hand's view space has no fixed scale: the gun is real size in simulation
        Matrix4d simFromView = simFromView(rig.k / s);
        List<RopeCollider> arms = new ArrayList<>();
        if (armsFrame == frame) for (Arm arm : ARMS) arms.add(arm.collider(simFromView));
        ARMS.clear();
        heldLoop("fp", rig.config("loop"), rig, root, bones, simFromView, arms, CARRY_VIEW, CARRY_HAND, null, buffers, light);
    }

    /** NativePlayerArmRenderer: a first-person arm as drawn (pose: its part frame, PartPose.ZERO, model pixels / 16). */
    public static void arm(Matrix4fc pose, boolean right, boolean slim) {
        if (armsFrame != frame) { ARMS.clear(); armsFrame = frame; }
        if (ARMS.size() < 4) ARMS.add(new Arm(new Matrix4f(pose), right, slim));
    }

    private record Arm(Matrix4f pose, boolean right, boolean slim) {
        /** The arm's cube (the vanilla player's, slim 3 px wide), a little proud for the sleeve. */
        RopeCollider collider(Matrix4dc simFromView) {
            double w = slim ? 3 : 4, x0 = right ? 1 - w : -1;
            double g = 0.4;
            return BoxCollider.of(simFromView, pose, new double[] {(x0 - g) / 16, (-2 - g) / 16, (-2 - g) / 16},
                    new double[] {(x0 + w + g) / 16, (10 + g) / 16, (2 + g) / 16});
        }
    }

    /** Third person in the hand: the gun renderer records the gun; the player's CarriedWeaponsLayer runs the strap. */
    public static void thirdPerson(ItemStack gun, MeshModel mesh, Matrix4fc root, Map<String, Matrix4f> bones) {
        if (!(current instanceof AbstractClientPlayer player) || AflShaderCompat.activeShadowPass() || !inLevel) return;
        if (Rig.of(gun, mesh) == null) return;
        HELD.put(player.getId(), new Held(gun.copy(), mesh, new Matrix4f(root), Map.copyOf(bones), frame));
    }

    /** The maintenance bench: a free loop, live, lying on the mat. pose: the gun's root (after the bench transform). */
    public static void bench(Object key, ItemStack gun, PoseStack.Pose pose, MultiBufferSource buffers, int light) {
        Rig rig = Rig.of(gun, MaintenanceGunRendering.meshOf(gun));
        if (rig == null) return;
        Matrix4f root = new Matrix4f(pose.pose());
        Map<String, Matrix4f> bones = frames(root, MaintenanceGunRendering.bindFrames(gun));
        Matrix4d simFromView = simFromView(1);
        // the mat: the gun's lowest point
        Matrix4d simFromRoot = new Matrix4d(simFromView).mul(new Matrix4d(root));
        double low = Double.MAX_VALUE;
        for (var b : MaintenanceGunRendering.bounds(gun))
            for (int i = 0; i < 8; i++)
                low = Math.min(low, simFromRoot.transformPosition(new Vector3d((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY,
                        (i & 4) == 0 ? b.minZ : b.maxZ)).y);
        List<RopeCollider> extra = low < Double.MAX_VALUE ? List.of(RopeCollider.plane(0, 1, 0, low)) : List.of();
        Vector3d centre = simFromRoot.transformPosition(centre(gun));
        heldLoop(key, rig.config("bench"), rig, root, bones, simFromView, extra, 0, 0,
                (h, t, length) -> arc(h, t, length, new Vector3d(0, -1, 0), centre), buffers, light);
    }

    /**
     * The player's CarriedWeaponsLayer: the strap of the gun in the hand (recorded by {@link #thirdPerson} this frame: off
     * the shoulder, a free loop as in first person) and of the slung gun on the back (backFrame: model root to the gun, null
     * when none is drawn: round the body). Both collide with the body.
     */
    public static void carried(AbstractClientPlayer player, PlayerModel<?> model, PoseStack pose, MultiBufferSource buffers, int light,
                               ItemStack back, Matrix4f backFrame) {
        Matrix4f root = new Matrix4f(pose.last().pose());
        Held held = HELD.remove(player.getId());
        Rig heldRig = held != null && held.frame == frame ? Rig.of(held.gun, held.mesh) : null;
        Rig backRig = backFrame != null ? Rig.of(back, MaintenanceGunRendering.meshOf(back)) : null;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        // a shadow pass, the inventory's figure and far players draw the last shape
        boolean live = !AflShaderCompat.activeShadowPass() && inLevel && player.distanceToSqr(camera.getPosition()) <= FAR * FAR;
        List<RopeCollider> body = live && (heldRig != null || backRig != null) ? bodyBoxes(model, pose, simFromView(1)) : List.of();
        around(List.of("held", player.getId()), heldRig, held == null ? null : held.bones, true, live, model, root, body, buffers, light);
        around(List.of("back", player.getId()), backRig,
                backRig == null ? null : frames(new Matrix4f(root).mul(backFrame), MaintenanceGunRendering.bindFrames(back)), false, live,
                model, root, body, buffers, light);
    }

    private static void around(Object key, Rig rig, Map<String, Matrix4f> bones, boolean inHand, boolean live, PlayerModel<?> model,
                               Matrix4f root, List<RopeCollider> body, MultiBufferSource buffers, int light) {
        String context = inHand ? "held" : "back";
        Strap strap = LIVE.get(key);
        if (!live) {
            // without a rig (a shadow pass sees no recorded hand gun) a strap stepped a frame ago still stands
            if (strap != null && (rig != null ? strap.config.equals(rig.config(context)) : frame - strap.touched <= 2)) {
                if (rig != null) strap.touched = frame;
                draw(strap, root, buffers, light);
            }
            return;
        }
        if (rig == null) return;
        Matrix4d simFromView = simFromView(1);
        if (inHand) {
            // off the shoulder, as in first person: a free loop under the gun, against the gun, the arms and the body
            heldLoop(key, rig.config(context), rig, root, bones, simFromView, body, 0, CARRY_HAND, null, buffers, light);
            return;
        }
        // the route over the body, body part pixels (y down, -z the front, +x the player's left)
        double[][] route = {{3.0, -1.5, 0.8}, {2.0, 1.0, -3.0}, {-1, 7, -3.0}, {-4.0, 11, -2.6}, {-5.0, 12, 0.5}};
        PoseStack bodyPose = new PoseStack();
        bodyPose.last().pose().set(root);
        model.body.translateAndRotate(bodyPose);
        Matrix4d simFromBody = new Matrix4d(simFromView).mul(new Matrix4d(bodyPose.last().pose()));
        List<double[]> via = new ArrayList<>();
        for (double[] q : route) {
            Vector3d v = simFromBody.transformPosition(new Vector3d(q[0] / 16, q[1] / 16, q[2] / 16));
            via.add(new double[] {v.x, v.y, v.z});
        }
        bodyLoop(key, rig.config(context), rig, root, bones, simFromView, via, body, CARRY_BODY, buffers, light);
    }

    /**
     * The gun rack and the ground guns: the strap at rest on the gun as it lies (pose: the gun's root). chunk: drawn into a
     * chunk capture, whose frame is world aligned (down is -y); otherwise down comes from the camera (no strap in a shadow
     * pass, whose frame we do not know). lying: on what the gun lies on (the ground, a shelf), else hanging free below it
     * (the rack's wall brackets).
     */
    public static void still(ItemStack gun, PoseStack.Pose pose, MultiBufferSource buffers, int light, boolean chunk, boolean lying) {
        Rig rig = Rig.of(gun, MaintenanceGunRendering.meshOf(gun));
        if (rig == null) return;
        Vector3d down = new Vector3d(0, -1, 0);
        if (!chunk) {
            if (AflShaderCompat.activeShadowPass()) return;
            viewRotation().transformDirection(down);
        }
        Matrix4d modelFromPose = new Matrix4d(new Matrix4f(pose.pose())).invertAffine();
        modelFromPose.transformDirection(down).normalize();
        String key = rig.config(lying ? "lying" : "hanging") + '|' + magazines(gun) + '|' + Math.round(down.x * 20) + ','
                + Math.round(down.y * 20) + ',' + Math.round(down.z * 20);
        float[] shape = STILL.computeIfAbsent(key, k -> settle(rig, gun, down, lying));
        if (shape.length > 0) emit(shape, new Matrix4f(pose.pose()), new Matrix3f(pose.normal()), buffers, light);
    }

    // ------------------------------------------------------------------------------------------------- simulation

    /** A first shape for a new free loop: from pin h to pin t, length long (hooks included). */
    private interface Guide { double[] shape(double[] h, double[] t, double length); }

    /**
     * A free loop between the swivels (in the hand, the bench). viewCarry, carry: the shares of the camera's motion and of
     * the rest of the gun's the strap is carried along ({@link #step}); guide: its first shape, null for a V hanging down.
     */
    private static void heldLoop(Object key, String config, Rig rig, Matrix4fc root, Map<String, Matrix4f> bones, Matrix4dc simFromView,
                                 List<RopeCollider> extra, double viewCarry, double carry, Guide guide, MultiBufferSource buffers, int light) {
        Strap strap = LIVE.get(key);
        if (AflShaderCompat.activeShadowPass()) {
            if (strap != null && strap.config.equals(config)) draw(strap, root, buffers, light);
            return;
        }
        Ends ends = ends(rig, bones, simFromView);
        if (ends == null) return;
        double length = Math.max(ends.distance() * FREE_SLACK, 2 * HOOK + 0.12);
        if (strap == null || !strap.config.equals(config)) LIVE.put(key, strap = new Strap(config, length));
        RopeCollider collider = colliders(strap.colliders, false, rig, bones, simFromView, extra);
        double full = strap.chain.length();
        Guide first = guide != null ? guide : (h, t, l) -> vee(h, t, l);
        step(strap, root, simFromView, ends, collider, viewCarry, carry, () -> first.shape(ends.head, ends.tail, full));
        draw(strap, root, buffers, light);
    }

    /** A strap round the body (third person in the hand, on the back): via is its route in simulation space. */
    private static void bodyLoop(Object key, String config, Rig rig, Matrix4fc root, Map<String, Matrix4f> bones, Matrix4dc simFromView,
                                 List<double[]> via, List<RopeCollider> body, double carry, MultiBufferSource buffers, int light) {
        Ends ends = ends(rig, bones, simFromView);
        if (ends == null) return;
        double[] guide = new double[(via.size() + 2) * 3];
        System.arraycopy(ends.head, 0, guide, 0, 3);
        for (int i = 0; i < via.size(); i++) System.arraycopy(via.get(i), 0, guide, i * 3 + 3, 3);
        System.arraycopy(ends.tail, 0, guide, guide.length - 3, 3);
        double route = 0;
        for (int i = 3; i < guide.length; i += 3)
            route += Math.sqrt(sq(guide[i] - guide[i - 3]) + sq(guide[i + 1] - guide[i - 2]) + sq(guide[i + 2] - guide[i - 1]));
        Strap strap = LIVE.get(key);
        // the route's length follows the gun (raised, lowered, drawn): a new strap only when it changes by a quarter
        if (strap == null || !strap.config.equals(config) || Math.abs(strap.chain.length() - route * BODY_SLACK) > route * 0.25)
            LIVE.put(key, strap = new Strap(config, route * BODY_SLACK));
        RopeCollider collider = colliders(strap.colliders, false, rig, bones, simFromView, body);
        step(strap, root, simFromView, ends, collider, 0, carry, () -> guide);
        draw(strap, root, buffers, light);
    }

    /**
     * One frame of a live strap: placed (and presettled) when new or after a jump, else carried and stepped; then cached.
     * The carry: viewCarry of the camera's own motion (first person: its view space moves with it), then carry of the rest
     * of the root's motion; the strap's colliders are carried the same, so their sweep sees the strap where it is.
     */
    private static void step(Strap strap, Matrix4fc root, Matrix4dc simFromView, Ends ends, RopeCollider collider, double viewCarry,
                             double carry, Supplier<double[]> guide) {
        strap.touched = frame;
        Matrix4d simFromRoot = new Matrix4d(simFromView).mul(new Matrix4d(root));
        if (strap.stepped != frame) {
            RopeChain chain = strap.chain;
            long now = System.nanoTime();
            if (!chain.placed() || !chain.ends(ends.head[0], ends.head[1], ends.head[2], ends.tail[0], ends.tail[1], ends.tail[2])) {
                chain.place(guide.get());
                chain.frames(ends.headWidth, ends.tailWidth, strap.width);
                if (frame - strap.placedAt > RESETTLE_FRAMES) chain.settle(0.6, collider);
                strap.placedAt = frame;
            } else {
                if (strap.lastRoot != null) {
                    Matrix4d motion = new Matrix4d(simFromRoot).mul(new Matrix4d(strap.lastRoot).invertAffine());
                    if (viewCarry > 0 && strap.lastView != null) {
                        Matrix4d view = new Matrix4d(simFromView).mul(new Matrix4d(strap.lastView).invertAffine());
                        carry(strap, view, viewCarry);
                        motion.mul(new Matrix4d(view).invertAffine());
                    }
                    carry(strap, motion, carry);
                }
                chain.step((now - strap.nanos) / 1e9, collider);
            }
            strap.nanos = now;
            strap.stepped = frame;
            strap.lastRoot = simFromRoot;
            strap.lastView = new Matrix4d(simFromView);
            chain.frames(ends.headWidth, ends.tailWidth, strap.width);
            strap.local = cache(chain, strap.width, new Matrix4d(simFromRoot).invertAffine());
        }
    }

    private static void carry(Strap strap, Matrix4dc motion, double share) {
        if (share <= 0) return;
        strap.chain.carry(motion, share);
        for (MeshCollider c : strap.colliders.values()) c.carry(motion, share);
    }

    /** The two pins (the swivels' bars, simulation space) and the bars' directions. */
    private record Ends(double[] head, double[] tail, double[] headWidth, double[] tailWidth) {
        double distance() { return Math.sqrt(sq(head[0] - tail[0]) + sq(head[1] - tail[1]) + sq(head[2] - tail[2])); }
    }

    private static Ends ends(Rig rig, Map<String, Matrix4f> bones, Matrix4dc simFromView) {
        Matrix4f f = bones.get(rig.front.bone()), r = bones.get(rig.rear.bone());
        if (f == null || r == null) return null;
        Matrix4d sf = new Matrix4d(simFromView).mul(new Matrix4d(f)), sr = new Matrix4d(simFromView).mul(new Matrix4d(r));
        Vector3d h = sf.transformPosition(new Vector3d(rig.front.x() / 16, rig.front.y() / 16, rig.front.z() / 16));
        Vector3d t = sr.transformPosition(new Vector3d(rig.rear.x() / 16, rig.rear.y() / 16, rig.rear.z() / 16));
        Vector3d hw = sf.transformDirection(new Vector3d(1, 0, 0)).normalize(), tw = sr.transformDirection(new Vector3d(1, 0, 0)).normalize();
        if (!Double.isFinite(h.x + t.x + hw.x + tw.x)) return null;
        return new Ends(new double[] {h.x, h.y, h.z}, new double[] {t.x, t.y, t.z}, new double[] {hw.x, hw.y, hw.z}, new double[] {tw.x, tw.y, tw.z});
    }

    /**
     * The gun's drawn bones as mesh colliders, a magazine drawn in place of a bone (an extended magazine, a drum) by its own
     * mesh, and the extras. kept: the colliders by name (a live strap's, so they sweep from their last frame); hold: no
     * motion from the last frame (a static settle).
     */
    private static RopeCollider colliders(Map<String, MeshCollider> kept, boolean hold, Rig rig, Map<String, Matrix4f> bones,
                                          Matrix4dc simFromView, List<RopeCollider> extra) {
        List<RopeCollider> all = new ArrayList<>(extra);
        Map<String, MeshShape> gun = shapes(rig.mesh);
        java.util.Set<String> used = new java.util.HashSet<>();
        for (var e : bones.entrySet()) {
            String bone = e.getKey();
            Matrix4d at = new Matrix4d(simFromView).mul(new Matrix4d(e.getValue()));
            // a bone an animation hides by scaling it down (the BR-51's spare magazines: scale 0) is not there to hit
            if (!(Math.cbrt(Math.abs(at.determinant3x3())) >= rig.k * SHOWN)) continue;
            if (NativeMagazineRendering.replaces(rig.gun, bone)) {
                for (Piece piece : magazine(NativeMagazineRendering.drawn(rig.gun, bone)))
                    add(all, kept, used, "magazine|" + bone + '|' + piece.bone, piece.shape, new Matrix4d(at).mul(piece.frame), hold);
            } else {
                MeshShape shape = gun.get(bone);
                if (shape != null) add(all, kept, used, bone, shape, at, hold);
            }
        }
        // one that comes back starts from where it is, not from where it was frames ago
        kept.keySet().retainAll(used);
        return RopeCollider.of(all);
    }

    private static void add(List<RopeCollider> all, Map<String, MeshCollider> kept, java.util.Set<String> used, String key, MeshShape shape,
                            Matrix4dc at, boolean hold) {
        used.add(key);
        MeshCollider c = kept.get(key);
        if (c == null || c.shape() != shape) kept.put(key, c = new MeshCollider(shape, MARGIN));
        if (hold) c.hold(at); else c.place(at);
        all.add(c);
    }

    /** A magazine item's mesh bones as collider shapes, each with its frame under the magazine's draw pose (its bind pose). */
    private record Piece(String bone, MeshShape shape, Matrix4d frame) {}

    private static List<Piece> magazine(ItemStack magazine) {
        if (magazine.isEmpty()) return List.of();
        ResourceLocation geometry = NativeMuzzleRendering.geometry(magazine);
        return MAGAZINES.computeIfAbsent(geometry, g -> {
            MeshModel mesh = VextrisMeshes.byGeometry(g);
            var baked = GeckoLibCache.getBakedModels().get(g);
            if (mesh == null || baked == null) return List.of();
            Map<String, MeshShape> shapes = shapes(mesh);
            List<Piece> out = new ArrayList<>();
            for (GeoBone bone : baked.topLevelBones()) pieces(bone, new PoseStack(), shapes, out);
            return List.copyOf(out);
        });
    }

    /** client/MeshItemCache's walk: each bone's mesh at its pivot under its parents' bind pose. */
    private static void pieces(GeoBone bone, PoseStack pose, Map<String, MeshShape> shapes, List<Piece> out) {
        if (Boolean.TRUE.equals(bone.shouldNeverRender())) return;
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        MeshShape shape = shapes.get(bone.getName());
        if (shape != null) {
            pose.pushPose();
            RenderUtils.translateToPivotPoint(pose, bone);
            out.add(new Piece(bone.getName(), shape, new Matrix4d(new Matrix4f(pose.last().pose()))));
            pose.popPose();
        }
        for (GeoBone child : bone.getChildBones()) pieces(child, pose, shapes, out);
        pose.popPose();
    }

    /** Which magazines a gun shows in place of its bones (part of a still strap's cache key). */
    private static String magazines(ItemStack gun) {
        StringBuilder out = new StringBuilder();
        for (String bone : MaintenanceGunRendering.bindFrames(gun).keySet()) {
            if (!NativeMagazineRendering.replaces(gun, bone)) continue;
            ItemStack m = NativeMagazineRendering.drawn(gun, bone);
            out.append(bone).append('=').append(m.isEmpty() ? "-" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(m.getItem())).append(';');
        }
        return out.toString();
    }

    /** The centre of a gun's model bounds (model blocks). */
    private static Vector3d centre(ItemStack gun) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (var b : MaintenanceGunRendering.bounds(gun)) {
            lo[0] = Math.min(lo[0], b.minX); lo[1] = Math.min(lo[1], b.minY); lo[2] = Math.min(lo[2], b.minZ);
            hi[0] = Math.max(hi[0], b.maxX); hi[1] = Math.max(hi[1], b.maxY); hi[2] = Math.max(hi[2], b.maxZ);
        }
        return lo[0] > hi[0] ? new Vector3d() : new Vector3d((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2);
    }

    /** Head and arms, torso and legs as boxes (their cubes, a little proud for the skin layer and worn gear). */
    private static List<RopeCollider> bodyBoxes(PlayerModel<?> model, PoseStack pose, Matrix4dc simFromView) {
        List<RopeCollider> out = new ArrayList<>();
        box(out, model.body, pose, simFromView, new double[] {-4, 0, -2}, new double[] {4, 12, 2}, 0.8);
        box(out, model.head, pose, simFromView, new double[] {-4, -8, -4}, new double[] {4, 0, 4}, 0.4);
        box(out, model.rightArm, pose, simFromView, new double[] {-3, -2, -2}, new double[] {1, 10, 2}, 0.4);
        box(out, model.leftArm, pose, simFromView, new double[] {-1, -2, -2}, new double[] {3, 10, 2}, 0.4);
        box(out, model.rightLeg, pose, simFromView, new double[] {-2, 0, -2}, new double[] {2, 12, 2}, 0.4);
        box(out, model.leftLeg, pose, simFromView, new double[] {-2, 0, -2}, new double[] {2, 12, 2}, 0.4);
        return out;
    }

    private static void box(List<RopeCollider> out, ModelPart part, PoseStack pose, Matrix4dc simFromView, double[] lo, double[] hi, double grow) {
        if (!part.visible) return;
        PoseStack p = new PoseStack();
        p.last().pose().set(pose.last().pose());
        part.translateAndRotate(p);
        double[] l = new double[3], h = new double[3];
        for (int k = 0; k < 3; k++) { l[k] = (lo[k] - grow) / 16; h[k] = (hi[k] + grow) / 16; }
        out.add(BoxCollider.of(simFromView, p.last().pose(), l, h));
    }

    /** The strap at rest on a gun lying still, in the gun's model frame (blocks): the cache {@link #emit} draws. */
    private static float[] settle(Rig rig, ItemStack gun, Vector3d down, boolean lying) {
        Map<String, Matrix4f> bones = MaintenanceGunRendering.bindFrames(gun);
        // simulation: the model frame in metres (k per block), gravity along the model's own down
        Matrix4d simFromModel = new Matrix4d().scaling(rig.k);
        Ends ends = ends(rig, bones, simFromModel);
        if (ends == null) return new float[0];
        double length = Math.max(ends.distance() * FREE_SLACK, 2 * HOOK + 0.12);
        Strap strap = new Strap("", length);
        RopeChain chain = strap.chain;
        chain.gravityX = down.x * 9.81; chain.gravityY = down.y * 9.81; chain.gravityZ = down.z * 9.81;
        // what it lies on: the gun's lowest point along down
        double floor = -Double.MAX_VALUE;
        for (var b : MaintenanceGunRendering.bounds(gun))
            for (int i = 0; i < 8; i++)
                floor = Math.max(floor, ((i & 1) == 0 ? b.minX : b.maxX) * down.x + ((i & 2) == 0 ? b.minY : b.maxY) * down.y + ((i & 4) == 0 ? b.minZ : b.maxZ) * down.z);
        List<RopeCollider> floorPlane = lying && floor > -Double.MAX_VALUE
                ? List.of(RopeCollider.plane(-down.x, -down.y, -down.z, -floor * rig.k)) : List.of();
        RopeCollider collider = colliders(new HashMap<>(), true, rig, bones, simFromModel, floorPlane);
        double[] h = ends.head, t = ends.tail;
        if (lying) {
            // lying: laid out flat beside the gun first, bowed away from it, so it settles as a strap does, not crumpled
            chain.place(arc(h, t, chain.length(), down, centre(gun).mul(rig.k)));
        } else {
            double[] mid = {(h[0] + t[0]) / 2, (h[1] + t[1]) / 2, (h[2] + t[2]) / 2};
            double d = ends.distance(), sag = Math.sqrt(Math.max(0, sq(chain.length() / 2) - sq(d / 2)));
            chain.place(new double[] {h[0], h[1], h[2], mid[0] + down.x * sag, mid[1] + down.y * sag, mid[2] + down.z * sag, t[0], t[1], t[2]});
        }
        chain.frames(ends.headWidth, ends.tailWidth, strap.width);
        chain.settle(1.5, collider);
        chain.frames(ends.headWidth, ends.tailWidth, strap.width);
        return cache(chain, strap.width, new Matrix4d(simFromModel).invertAffine());
    }

    /**
     * A bow from pin a to pin b square to down, bulging away from centre (the gun's), about length long: a strap laid out
     * flat beside a lying gun.
     */
    private static double[] arc(double[] a, double[] b, double length, Vector3d down, Vector3d centre) {
        Vector3d h = new Vector3d(a[0], a[1], a[2]), axis = new Vector3d(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3d dn = new Vector3d(down).normalize();
        double d = axis.length();
        Vector3d away = new Vector3d(h).fma(0.5, axis).sub(centre);
        away.fma(-away.dot(dn), dn);
        if (d > 1e-6) away.fma(-away.dot(axis) / (d * d), axis);
        if (away.lengthSquared() < 1e-12) away = d > 1e-6 ? new Vector3d(dn).cross(axis) : new Vector3d(dn.y, dn.z, dn.x).cross(dn);
        away.normalize();
        // a parabola of span d and rise s is about d + 8 s^2 / 3d long
        double rise = Math.sqrt(Math.max(0, 3 * d * (length - d) / 8));
        int points = 12;
        double[] out = new double[(points + 1) * 3];
        for (int i = 0; i <= points; i++) {
            double u = (double) i / points, bow = 4 * rise * u * (1 - u);
            out[i * 3] = a[0] + axis.x * u + away.x * bow;
            out[i * 3 + 1] = a[1] + axis.y * u + away.y * bow;
            out[i * 3 + 2] = a[2] + axis.z * u + away.z * bow;
        }
        return out;
    }

    /** A V hanging from the two pins, as deep as the strap's length allows (a new free loop's first shape). */
    private static double[] vee(double[] a, double[] b, double length) {
        double d = Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
        double h = Math.sqrt(Math.max(0, sq(length / 2) - sq(d / 2)));
        return new double[] {a[0], a[1], a[2], (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 - h, (a[2] + b[2]) / 2, b[0], b[1], b[2]};
    }

    // ------------------------------------------------------------------------------------------------- drawing

    /**
     * The chain in a draw frame (rootFromSim): per node its position, then its half width and half thickness vectors,
     * 9 floats, then per segment its rest length (metres).
     */
    private static float[] cache(RopeChain chain, double[] width, Matrix4dc rootFromSim) {
        int n = chain.n;
        float[] out = new float[n * 9 + n - 1];
        double[] x = chain.positions();
        Vector3d v = new Vector3d();
        for (int i = 0; i < n; i++) {
            int o = i * 3, a = Math.max(0, i - 1) * 3, b = Math.min(n - 1, i + 1) * 3;
            double tx = x[b] - x[a], ty = x[b + 1] - x[a + 1], tz = x[b + 2] - x[a + 2];
            double wx = width[o], wy = width[o + 1], wz = width[o + 2];
            double nx = ty * wz - tz * wy, ny = tz * wx - tx * wz, nz = tx * wy - ty * wx, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-12) { nx = 0; ny = 1; nz = 0; nl = 1; }
            rootFromSim.transformPosition(v.set(x[o], x[o + 1], x[o + 2]));
            out[i * 9] = (float) v.x; out[i * 9 + 1] = (float) v.y; out[i * 9 + 2] = (float) v.z;
            rootFromSim.transformDirection(v.set(wx * WIDTH / 2, wy * WIDTH / 2, wz * WIDTH / 2));
            out[i * 9 + 3] = (float) v.x; out[i * 9 + 4] = (float) v.y; out[i * 9 + 5] = (float) v.z;
            rootFromSim.transformDirection(v.set(nx / nl * THICK / 2, ny / nl * THICK / 2, nz / nl * THICK / 2));
            out[i * 9 + 6] = (float) v.x; out[i * 9 + 7] = (float) v.y; out[i * 9 + 8] = (float) v.z;
        }
        for (int i = 0; i < n - 1; i++) out[n * 9 + i] = (float) chain.rest(i);
        return out;
    }

    private static void draw(Strap strap, Matrix4fc root, MultiBufferSource buffers, int light) {
        if (strap.local.length == 0) return;
        emit(strap.local, new Matrix4f(root), new Matrix3f(root).invert().transpose(), buffers, light);
    }

    private static void emit(float[] local, Matrix4f pose, Matrix3f normal, MultiBufferSource buffers, int light) {
        SlingStrapRenderer.draw(local, pose, normal, buffers, light);
    }

    // ------------------------------------------------------------------------------------------------- state

    /** A gun with a fitted sling and both strap ends. */
    record Rig(ItemStack gun, ItemStack sling, NativeSlingMount.StrapEnd front, NativeSlingMount.StrapEnd rear, MeshModel mesh, double k) {
        static Rig of(ItemStack gun, MeshModel mesh) {
            if (mesh == null || !(gun.getItem() instanceof NativeGunItem g)) return null;
            NativeSlingMount mount = g.definition().slingMount();
            if (mount == null || mount.front() == null || mount.rear() == null) return null;
            ItemStack sling = NativeAttachments.active(gun, NativeAttachment.Slot.SLING);
            if (sling.isEmpty()) return null;
            double k = metresPerBlock(gun);
            return k > 0 ? new Rig(gun, sling, mount.front(), mount.rear(), mesh, k) : null;
        }

        String config(String context) {
            return context + '|' + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gun.getItem()) + '|'
                    + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(sling.getItem()) + '|' + System.identityHashCode(mesh);
        }
    }

    /** The gun's real metres per model block: its real length over its model's muzzle-to-butt extent. */
    private static double metresPerBlock(ItemStack gun) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (var b : MaintenanceGunRendering.bounds(gun)) { lo = Math.min(lo, b.minZ); hi = Math.max(hi, b.maxZ); }
        return hi > lo ? WoodGunRackRenderer.length(gun, false) / (hi - lo) : 0;
    }

    private static final class Strap {
        final String config;
        final RopeChain chain;
        final double[] width;
        final Map<String, MeshCollider> colliders = new HashMap<>();
        float[] local = new float[0];
        Matrix4d lastRoot, lastView;
        long stepped = -1, touched, nanos, placedAt = Long.MIN_VALUE / 2;

        Strap(String config, double length) {
            this.config = config;
            double strap = length - 2 * HOOK;
            int segs = Math.max(12, Math.min(44, (int) Math.round(strap / SEGMENT)));
            double[] rest = new double[segs + 2], mass = new double[segs + 3];
            rest[0] = HOOK; rest[segs + 1] = HOOK;
            for (int i = 1; i <= segs; i++) rest[i] = strap / segs;
            java.util.Arrays.fill(mass, 1);
            mass[1] = 3; mass[segs + 1] = 3;   // a steel hook outweighs a strap segment
            chain = new RopeChain(rest, mass, RADIUS);
            width = new double[chain.n * 3];
        }
    }

    private record Held(ItemStack gun, MeshModel mesh, Matrix4f root, Map<String, Matrix4f> bones, long frame) {}

    /** Bone mesh frames placed under a root. */
    private static Map<String, Matrix4f> frames(Matrix4fc root, Map<String, Matrix4f> bind) {
        Map<String, Matrix4f> out = new HashMap<>();
        for (var e : bind.entrySet()) out.put(e.getKey(), new Matrix4f(root).mul(e.getValue()));
        return out;
    }

    /**
     * Each bone's mesh as a collider shape (model blocks, pivot local), without the sling hardware the hooks clip to (parts
     * named sling or swivel).
     */
    static Map<String, MeshShape> shapes(MeshModel mesh) {
        return SHAPES.computeIfAbsent(mesh, m -> {
            Map<String, MeshShape> out = new HashMap<>();
            for (String bone : m.boneNames()) {
                MeshShape.Builder b = MeshShape.builder(1 / 16.0);
                for (MeshPart part : m.parts(bone, MeshLayer.CUTOUT)) {
                    String name = part.name();
                    if (name.contains("sling") || name.contains("swivel")) continue;
                    boolean closed = part.closed();
                    for (int f = 0; f < part.faceCount(); f++) {
                        int s = part.faceStart(f), size = part.faceSize(f);
                        for (int k = 1; k + 1 < size; k++)
                            b.triangle(part.value(s, 0), part.value(s, 1), part.value(s, 2),
                                    part.value(s + k, 0), part.value(s + k, 1), part.value(s + k, 2),
                                    part.value(s + k + 1, 0), part.value(s + k + 1, 1), part.value(s + k + 1, 2), closed);
                    }
                }
                MeshShape shape = b.build();
                if (!shape.isEmpty()) out.put(bone, shape);
            }
            return Map.copyOf(out);
        });
    }

    /** View (this pass) to simulation: the camera's rotation undone, scaled to metres, moved to the camera. */
    private static Matrix4d simFromView(double metresPerUnit) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var e = camera.getPosition();
        return new Matrix4d().translation(e.x, e.y, e.z).scale(metresPerUnit).mul(new Matrix4d(viewRotation()).transpose3x3());
    }

    /** World to view rotation, as the level's pose stack (x pitch, then y yaw + 180). */
    private static Matrix4d viewRotation() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return new Matrix4d().rotationX(Math.toRadians(camera.getXRot())).rotateY(Math.toRadians(camera.getYRot() + 180));
    }

    private static double sq(double v) { return v * v; }

    // ------------------------------------------------------------------------------------------------- events

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        frame++;
        HELD.clear();
        // a resource reload: new meshes, new shapes
        long meshes = VextrisMeshes.generation();
        if (meshes != generation) { generation = meshes; clear(); }
        if ((frame & 63) == 0) LIVE.values().removeIf(s -> frame - s.touched > 600);
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) inLevel = true;
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) inLevel = false;
    }

    @SubscribeEvent
    public static void livingPre(RenderLivingEvent.Pre<?, ?> event) { current = event.getEntity(); }

    @SubscribeEvent
    public static void livingPost(RenderLivingEvent.Post<?, ?> event) { current = null; }

    /** Forgets every cached shape (resource reload: new meshes). */
    public static void clear() {
        LIVE.clear();
        STILL.clear();
        SHAPES.clear();
        MAGAZINES.clear();
    }
}
