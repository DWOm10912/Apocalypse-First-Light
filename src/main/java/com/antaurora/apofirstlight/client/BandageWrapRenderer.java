package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.ClientHealth;
import com.antaurora.apofirstlight.health.MedicalItem;
import com.antaurora.apofirstlight.health.Wounds;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Health V1a: the bandage stays on the body (user 2026-10-19 after testing: "绷带：需要真正留在玩家身上";
 * docs/gameplay/survival_health_v1a.md 7, bandage on the body). While a player has at least one bandaged wound (the server's word,
 * HealthPackets.Bandaged, sent to everyone tracking the player) a gauze wrap is drawn on the forearm that was held out to be
 * wrapped: the left one, the right one for a left-handed player (the bandage action is mirrored for them). Wounds have no place on the body in V1a, so it is always that forearm.
 * <p>
 * The wrap: three turns of plain off-white gauze round the sleeve, on the band {@link #BAND_FROM}..{@link #BAND_TO} px back
 * from the arm's distal end (the hand cap; the contract with the wrapping animations): a straight anchor turn at the wrist
 * end, one turn on a slant over it, a straight turn at the elbow end over that. Real geometry built here, not a texture: each
 * turn is a ring whose cross-section is the sleeve's square offset outward (rounded corners), {@link #OUT} px off the
 * sleeve (each turn a little further out than the one under it), with side walls down to 0.05 px above the bare arm, so
 * there is no coplanar face anywhere (the sleeve sits 0.25 px off the arm). Material: the bandage item's own gauze (its
 * mesh texture, a flat patch of it, with its LabPBR _n / _s), a little darker toward the edges of each turn.
 * <ul>
 * <li><b>Skeleton</b> (client/skeleton/PlayerSkeleton#draw calls {@link #drawSkeleton}): the turns are bent with the arm the
 * same way the C-min skin mesh is (SkinMesh: rings at the cuts, half way through the joint's rotation, linear between),
 * so the wrap follows the wrist and elbow without cutting into the skin or floating off it; the own first-person body too
 * (its world-pass draw).</li>
 * <li><b>Vanilla body</b> (skeleton off, or poses it does not draw: swimming, sleeping, dying, flying):
 * {@link Layer} on the rigid arm part.</li>
 * <li><b>First-person view models</b> (guns, jerry can, crowbar...: weapon/client/NativePlayerArmRenderer
 * calls {@link #drawArm}): on that arm in its own frame.</li>
 * </ul>
 * While the bandage is used (4 s), the turns appear one after another round the arm: progress =
 * clamp((use seconds - {@link #WRAP_FROM}) / {@link #WRAP_SECONDS}, 0, 1), a third of it per turn, so the wrapping runs from
 * 0.6 s to 3.1 s, each turn from the middle of the arm's front. Where the skeleton draws the action's roll, the open turn's
 * end follows the roll round the arm instead (a little behind it) and a band of cloth runs from it to the roll ({@link #strip}),
 * so the gauze comes off the roll as it goes round. Let go early and the partial wrap goes (unless a wound was already bandaged); a finished use keeps the
 * whole wrap up to 1 s until the server's answer arrives (it normally comes in the same tick).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BandageWrapRenderer {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/bandage_mesh.png");
    /** The wrapped band along the forearm, px back from the arm's distal end (the hand cap), in the arm's own frame. */
    public static final float BAND_FROM = 1.5F, BAND_TO = 5.5F;
    public static final int TURNS = 3;
    /** The wrapping during the use: from this many seconds, over this many. */
    public static final float WRAP_FROM = 0.6F, WRAP_SECONDS = 2.5F;

    /** Width of one turn (px along the arm). */
    private static final float WIDTH = 1.7F;
    /** Per turn: its middle (px back from the distal end), its slant (px along the arm per px across), its surface off the sleeve. */
    private static final float[] CENTRE = {BAND_FROM + WIDTH / 2, (BAND_FROM + BAND_TO) / 2, BAND_TO - WIDTH / 2};
    private static final float[] TILT = {0F, 0.16F, 0F};
    private static final float[] OUT = {0.14F, 0.21F, 0.28F};
    private static final float[] SHADE = {1F, 0.97F, 0.99F};
    /** The second layer's inflate (vanilla 0.25), and how far above the bare arm the turns' walls end. */
    private static final float SLEEVE = 0.25F, INNER = 0.05F;
    private static final int ARC = 3, ROWS = 4;
    /** A flat patch of the bandage mesh's gauze (textures/item/bandage_mesh.png, 256 px: plain gauze, flat _n). */
    private static final float U0 = 8 / 256F, U1 = 88 / 256F, V0 = 8 / 256F, V1 = 44 / 256F;
    /** The roll's radius as the action carries it (player_props/bandage.json: 4 px across at scale 0.5), px. */
    private static final float ROLL_R = 1.0F;
    /**
     * Where each turn starts round the arm (deg, 270: the middle of its front, -z), where the action's roll is as each turn
     * begins; while the skeleton shows the roll, the open turn's end follows it this far behind (deg) instead of the clock.
     */
    private static final int START = 270;
    private static final float TRAIL = 30F;
    /** A finished use holds the whole wrap this long (ticks) for the server's answer. */
    private static final int HOLD_TICKS = 20;

    private BandageWrapRenderer() {
    }

    // ---------------------------------------------------------------- what to draw

    /** The forearm the bandage goes on: the one held out to be wrapped, opposite the main hand. */
    public static boolean wrapsRight(Player player) {
        return player.getMainArm() == HumanoidArm.LEFT;
    }

    private static boolean usingBandage(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof MedicalItem item
                && item.treatment() == Wounds.Treatment.BANDAGE;
    }

    private static final class Track {
        int lastTicks = -1;
        long holdUntil = Long.MIN_VALUE;
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean using = usingBandage(p);
            Track t = TRACKS.get(p.getUUID());
            if (t == null) {
                if (!using) continue;
                TRACKS.put(p.getUUID(), t = new Track());
            }
            if (using) {
                t.lastTicks = p.getTicksUsingItem();
            } else {
                // the use ended at (about) its full length: finished, the server's bandaged state is on its way
                if (t.lastTicks >= MedicalItem.BANDAGE_TICKS - 3) t.holdUntil = now + HOLD_TICKS;
                t.lastTicks = -1;
            }
        }
        TRACKS.values().removeIf(t -> t.lastTicks < 0 && t.holdUntil < now);
    }

    /** Whether the player wears a bandage now: the server's word (the own player's synced wounds too). */
    public static boolean bandaged(Player player) {
        if (ClientHealth.bandaged(player.getUUID())) return true;
        if (player != Minecraft.getInstance().player) return false;
        for (Wounds.View w : ClientHealth.wounds()) if (w.bandaged()) return true;
        return false;
    }

    /** How much of the wrap is on (0 none, 1 all three turns). */
    public static float coverage(Player player, float partial) {
        if (bandaged(player)) return 1F;
        Track t = TRACKS.get(player.getUUID());
        Minecraft mc = Minecraft.getInstance();
        if (t != null && mc.level != null && mc.level.getGameTime() < t.holdUntil) return 1F;
        if (!usingBandage(player)) return 0F;
        float seconds = (player.getTicksUsingItem() + partial) / 20F;
        return Mth.clamp((seconds - WRAP_FROM) / WRAP_SECONDS, 0F, 1F);
    }

    // ---------------------------------------------------------------- the three ways it is drawn

    /** The player the skeleton drew last (its layers then leave the wrap alone); cleared by the layer and every frame. */
    private static Player skeletonDrew;

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) skeletonDrew = null;
    }

    /**
     * PlayerSkeleton#draw, after the skin: the wrap bent with the skin mesh. The pose stack is at the model origin (model px /
     * 16), as for the skin; {@code thick} is the arms' width there (SkinMesh#emit); {@code roll}: the bandage prop's middle
     * (model px) while the action holds it in view, for the cloth pulled off it ({@link #strip}), else null.
     */
    public static void drawSkeleton(AbstractClientPlayer player, SkeletonPose pose, boolean slim, float thick, PlayerModel<?> model,
                                    PoseStack stack, MultiBufferSource buffers, int light, int overlay, @Nullable Vector3f roll) {
        skeletonDrew = player;
        if (player.isInvisible()) return;
        float cover = coverage(player, Minecraft.getInstance().getFrameTime());
        if (cover <= 0F) return;
        boolean right = wrapsRight(player);
        if (!(right ? model.rightArm : model.leftArm).visible) return;
        SkeletonRig rig = pose.rig;
        SkeletonRig.SkinPart part = null;
        for (SkeletonRig.SkinPart p : rig.skin) if (p.part().equals(right ? "right_arm" : "left_arm")) part = p;
        if (part == null) return;
        SkeletonRig.Box b = part.box(slim);
        float cx = b.x() + b.w() / 2F, half = b.w() / 2F * thick;
        Arm arm = new Arm(cx - half, cx + half, b.z(), b.z() + b.d(), b.y() + b.h(), rig.overlayInflate, !right);
        // the skin's rings along the sleeve (SkinMesh#emit: the inflated layer's levels, the cuts' matrices)
        int[] bones = part.bones();
        int rings = bones.length + 1;
        float g = rig.overlayInflate, top = b.y() - g, stretch = (b.h() + 2 * g) / b.h();
        float[] levels = new float[rings];
        Matrix4f[] matrices = new Matrix4f[rings];
        for (int r = 0; r < rings; r++) {
            int level = r == 0 ? 0 : r == rings - 1 ? b.h() : part.cuts()[r - 1];
            levels[r] = top + level * stretch;
            matrices[r] = SkinMesh.ringMatrix(pose, bones, r, new Matrix4f());
        }
        Deform deform = (x, y, z, out) -> {
            int r = 0;
            while (r < rings - 2 && y > levels[r + 1]) r++;
            float t = Mth.clamp((y - levels[r]) / (levels[r + 1] - levels[r]), 0F, 1F);
            Vector3f a = matrices[r].transformPosition(x, levels[r], z, new Vector3f());
            Vector3f c = matrices[r + 1].transformPosition(x, levels[r + 1], z, new Vector3f());
            out[0] = a.x + (c.x - a.x) * t;
            out[1] = a.y + (c.y - a.y) * t;
            out[2] = a.z + (c.z - a.z) * t;
        };
        boolean winding = roll != null && cover < 1F && usingBandage(player);
        if (winding) {
            // the roll's place round the arm (the arm's rest frame, through the ring under the band's middle)
            float mid = arm.yEnd - (BAND_FROM + BAND_TO) / 2;
            int r = 0;
            while (r < rings - 2 && mid > levels[r + 1]) r++;
            Vector3f rest = new Matrix4f(matrices[r]).invert().transformPosition(roll, new Vector3f());
            float dx = rest.x - (arm.x0 + arm.x1) / 2, dz = rest.z - (arm.z0 + arm.z1) / 2;
            float deg = (float) Math.toDegrees(Math.atan2(dz, right ? -dx : dx));
            float phase = Mth.positiveModulo(deg - START - TRAIL, 360F) / 360F;
            // the turn the clock says, the place in it the roll says (the two stay within half a turn of each other)
            float turns = cover * TURNS;
            cover = Mth.clamp(Math.round(turns - phase) + phase, 0F, TURNS) / TURNS;
        }
        draw(arm, deform, cover, stack, buffers, light, overlay);
        if (winding && cover > 0F) strip(arm, deform, cover, roll, stack, buffers, light, overlay);
    }

    /**
     * The cloth pulled off the roll while it winds (user 2026-10-19: pulled out like tape): a flat band from the open turn's
     * leading end on the arm to the roll the right hand carries round (the action item/bandage_wrap's prop, its middle
     * {@code roll} in model px), as wide as a turn, both sides drawn.
     */
    private static void strip(Arm a, Deform deform, float cover, Vector3f roll, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        int turn = Math.min(TURNS - 1, (int) (cover * TURNS));
        float f = Mth.clamp(cover * TURNS - turn, 0F, 1F);
        Loop l = loop(a, OUT[turn]);
        int k = 0;
        while (k < l.n - 2 && l.t[k + 1] < f) k++;
        float u = Mth.clamp((f - l.t[k]) / Math.max(1e-6F, l.t[k + 1] - l.t[k]), 0F, 1F);
        float x = Mth.lerp(u, l.ox[k], l.ox[k + 1]), z = Mth.lerp(u, l.oz[k], l.oz[k + 1]);
        float xc = (a.x0 + a.x1) / 2, zc = (a.z0 + a.z1) / 2;
        float ex = (a.dorsalPlusX ? 1F : -1F) * Mth.SQRT_OF_TWO / 2, ez = Mth.SQRT_OF_TWO / 2;
        float y = a.yEnd - (CENTRE[turn] + TILT[turn] * ((x - xc) * ex + (z - zc) * ez));
        float[] a0 = new float[3], a1 = new float[3];
        deform.apply(x, y - WIDTH / 2, z, a0);
        deform.apply(x, y + WIDTH / 2, z, a1);
        float[] mid = {(a0[0] + a1[0]) / 2, (a0[1] + a1[1]) / 2, (a0[2] + a1[2]) / 2};
        float[] to = {mid[0] - roll.x, mid[1] - roll.y, mid[2] - roll.z};
        float len = (float) Math.sqrt(to[0] * to[0] + to[1] * to[1] + to[2] * to[2]);
        if (len < ROLL_R + 0.3F) return;
        // off the roll's surface toward the arm, the band a little narrower there (it comes off the roll in a twist)
        float s = ROLL_R / len;
        float[] b = {roll.x + to[0] * s, roll.y + to[1] * s, roll.z + to[2] * s};
        float[] b0 = new float[3], b1 = new float[3];
        for (int i = 0; i < 3; i++) {
            b0[i] = b[i] + (a0[i] - mid[i]) * 0.8F;
            b1[i] = b[i] + (a1[i] - mid[i]) * 0.8F;
        }
        Emitter e = new Emitter(stack.last(), buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, overlay);
        float[] face = Emitter.newell(a0, b0, b1, a1);
        float[] back = {-face[0], -face[1], -face[2]};
        float sh = 0.95F;
        e.quad(face, a0, b0, b1, a1, a0, b0, b1, a1, U0, V0, U1, V0, U1, V1, U0, V1, sh, sh, sh, sh);
        e.quad(back, a0, b0, b1, a1, a0, b0, b1, a1, U0, V0, U1, V0, U1, V1, U0, V1, sh * 0.9F, sh * 0.9F, sh * 0.9F, sh * 0.9F);
    }

    /**
     * weapon/client/NativePlayerArmRenderer, after an arm and its sleeve: the wrap on that arm if it is the wrapped one. The
     * pose stack is the arm part's own frame (its pose zeroed), as the vanilla cube is drawn in.
     */
    public static void drawArm(Player player, boolean right, boolean slim, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        if (right != wrapsRight(player) || player.isInvisible()) return;
        float cover = coverage(player, Minecraft.getInstance().getFrameTime());
        if (cover > 0F) drawPart(right, slim, cover, stack, buffers, light, overlay);
    }

    /** On a vanilla arm part, in its frame (PlayerModel: left arm cube from (-1, -2, -2), right from (-3 / slim -2, -2, -2), 12 long). */
    private static void drawPart(boolean right, boolean slim, float cover, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        float w = slim ? 3F : 4F, x0 = right ? (slim ? -2F : -3F) : -1F;
        Arm arm = new Arm(x0, x0 + w, -2F, 2F, 10F, SLEEVE, !right);
        draw(arm, (x, y, z, out) -> { out[0] = x; out[1] = y; out[2] = z; }, cover, stack, buffers, light, overlay);
    }

    /** The vanilla body's wrap (the skeleton draws its own; its draw marks the player so this skips it). */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (skeletonDrew == player) {
                skeletonDrew = null;
                return;
            }
            if (player.isInvisible()) return;
            float cover = coverage(player, partialTick);
            if (cover <= 0F) return;
            boolean right = wrapsRight(player);
            ModelPart arm = right ? getParentModel().rightArm : getParentModel().leftArm;
            if (!arm.visible) return;
            pose.pushPose();
            arm.translateAndRotate(pose);
            drawPart(right, "slim".equals(player.getModelName()), cover, pose, buffers, light,
                    LivingEntityRenderer.getOverlayCoords(player, 0F));
            pose.popPose();
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
            }
        }
    }

    // ---------------------------------------------------------------- geometry

    /** Rest point (the arm's frame, px) to its drawn place (model px, or the same frame). */
    private interface Deform {
        void apply(float x, float y, float z, float[] out);
    }

    /**
     * The bare arm's box across (x, z) and its distal end (y, the arm runs +y toward the hand), the sleeve's inflate, and
     * which side is the back of the forearm (where each turn starts; the outside of the arm: +x for the left arm).
     */
    private record Arm(float x0, float x1, float z0, float z1, float yEnd, float g, boolean dorsalPlusX) {}

    /** A turn's loop round the arm: outer points (x, z), their outward normals, the walls' inner ends, and the arc length 0..1. */
    private static final class Loop {
        final int n;
        final float[] ox, oz, nx, nz, ix, iz, t;

        Loop(int n) {
            this.n = n;
            ox = new float[n]; oz = new float[n]; nx = new float[n]; nz = new float[n]; ix = new float[n]; iz = new float[n]; t = new float[n];
        }
    }

    /**
     * The closed loop at {@code r} off the sleeve, from the middle of the arm's front (-z, {@link #START}) round to it again,
     * the way the bandage action's roll goes round (angle atan2(z, x - middle) rising on the left arm; mirrored in x on the
     * right one, as the action is): the sleeve's square with its corners rounded (arcs about the sleeve's corners), the inner
     * ends at the bare arm's corners + INNER.
     */
    private static Loop loop(Arm a, float r) {
        int n = 2 + 4 * (ARC + 1);
        Loop l = new Loop(n);
        int start = START;
        float zc = (a.z0 + a.z1) / 2;
        int k = 0;
        k = mid(a, l, k, start, r, zc);
        for (int c = 0; c < 4; c++) {
            int from = (start + 90 * c) % 360;
            float sx = from == 0 || from == 270 ? 1 : -1, sz = from == 0 || from == 90 ? 1 : -1;
            float cxs = sx > 0 ? a.x1 + a.g : a.x0 - a.g, czs = sz > 0 ? a.z1 + a.g : a.z0 - a.g;
            float cxi = sx > 0 ? a.x1 + INNER : a.x0 - INNER, czi = sz > 0 ? a.z1 + INNER : a.z0 - INNER;
            for (int i = 0; i <= ARC; i++) {
                double th = Math.toRadians(from + 90.0 * i / ARC);
                float cos = (float) Math.cos(th), sin = (float) Math.sin(th);
                l.ox[k] = cxs + r * cos; l.oz[k] = czs + r * sin;
                l.nx[k] = cos; l.nz[k] = sin;
                l.ix[k] = cxi; l.iz[k] = czi;
                k++;
            }
        }
        mid(a, l, k, start, r, zc);
        if (!a.dorsalPlusX) {
            float sum = a.x0 + a.x1;
            for (int i = 0; i < n; i++) {
                l.ox[i] = sum - l.ox[i];
                l.nx[i] = -l.nx[i];
                l.ix[i] = sum - l.ix[i];
            }
        }
        float total = 0;
        l.t[0] = 0;
        for (int i = 1; i < n; i++) {
            total += (float) Math.hypot(l.ox[i] - l.ox[i - 1], l.oz[i] - l.oz[i - 1]);
            l.t[i] = total;
        }
        for (int i = 1; i < n; i++) l.t[i] /= total;
        return l;
    }

    /** The middle of the side facing {@code side} deg (0 +x, 90 +z, 180 -x, 270 -z). */
    private static int mid(Arm a, Loop l, int k, int side, float r, float zc) {
        float xc = (a.x0 + a.x1) / 2;
        if (side == 0 || side == 180) {
            boolean plus = side == 0;
            l.ox[k] = plus ? a.x1 + a.g + r : a.x0 - a.g - r; l.oz[k] = zc;
            l.nx[k] = plus ? 1 : -1; l.nz[k] = 0;
            l.ix[k] = plus ? a.x1 + INNER : a.x0 - INNER; l.iz[k] = zc;
        } else {
            boolean plus = side == 90;
            l.ox[k] = xc; l.oz[k] = plus ? a.z1 + a.g + r : a.z0 - a.g - r;
            l.nx[k] = 0; l.nz[k] = plus ? 1 : -1;
            l.ix[k] = xc; l.iz[k] = plus ? a.z1 + INNER : a.z0 - INNER;
        }
        return k + 1;
    }

    /** The loop up to arc length {@code f} (0..1), the last point on the cut. */
    private static Loop cut(Loop l, float f) {
        if (f >= 1F) return l;
        int last = 1;
        while (last < l.n - 1 && l.t[last] < f) last++;
        Loop c = new Loop(last + 1);
        for (int i = 0; i < last; i++) copy(l, i, c, i, l, i, 0F);
        float u = (f - l.t[last - 1]) / Math.max(1e-6F, l.t[last] - l.t[last - 1]);
        copy(l, last - 1, c, last, l, last, Mth.clamp(u, 0F, 1F));
        return c;
    }

    private static void copy(Loop a, int i, Loop to, int k, Loop b, int j, float u) {
        to.ox[k] = Mth.lerp(u, a.ox[i], b.ox[j]); to.oz[k] = Mth.lerp(u, a.oz[i], b.oz[j]);
        to.nx[k] = Mth.lerp(u, a.nx[i], b.nx[j]); to.nz[k] = Mth.lerp(u, a.nz[i], b.nz[j]);
        to.ix[k] = Mth.lerp(u, a.ix[i], b.ix[j]); to.iz[k] = Mth.lerp(u, a.iz[i], b.iz[j]);
        to.t[k] = Mth.lerp(u, a.t[i], b.t[j]);
    }

    private static void draw(Arm a, Deform deform, float cover, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        Emitter e = new Emitter(stack.last(), buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, overlay);
        float xc = (a.x0 + a.x1) / 2, zc = (a.z0 + a.z1) / 2;
        // the slant runs across the back and the outside alike (a diagonal of the square), mirrored with the arm
        float ex = (a.dorsalPlusX ? 1F : -1F) * Mth.SQRT_OF_TWO / 2, ez = Mth.SQRT_OF_TWO / 2;
        for (int turn = 0; turn < TURNS; turn++) {
            float f = Mth.clamp(cover * TURNS - turn, 0F, 1F);
            if (f <= 0.02F) break;
            Loop l = cut(loop(a, OUT[turn]), f);
            int n = l.n;
            float[][][] restO = new float[n][ROWS + 1][], restI = new float[n][ROWS + 1][];
            float[][][] posedO = new float[n][ROWS + 1][], posedI = new float[n][ROWS + 1][];
            for (int k = 0; k < n; k++) {
                float sc = CENTRE[turn] + TILT[turn] * ((l.ox[k] - xc) * ex + (l.oz[k] - zc) * ez);
                for (int j = 0; j <= ROWS; j++) {
                    float y = a.yEnd - (sc - WIDTH / 2 + WIDTH * j / ROWS);
                    restO[k][j] = new float[]{l.ox[k], y, l.oz[k]};
                    restI[k][j] = new float[]{l.ix[k], y, l.iz[k]};
                    deform.apply(l.ox[k], y, l.oz[k], posedO[k][j] = new float[3]);
                    deform.apply(l.ix[k], y, l.iz[k], posedI[k][j] = new float[3]);
                }
            }
            float shade = SHADE[turn];
            for (int k = 0; k + 1 < n; k++) {
                float ua = Mth.lerp(l.t[k], U0, U1), ub = Mth.lerp(l.t[k + 1], U0, U1);
                float[] out = {l.nx[k] + l.nx[k + 1], 0, l.nz[k] + l.nz[k + 1]};
                for (int j = 0; j < ROWS; j++) {
                    float va = Mth.lerp(j / (float) ROWS, V0, V1), vb = Mth.lerp((j + 1) / (float) ROWS, V0, V1);
                    // a little darker toward the turn's edges, as gauze folds over them
                    float sa = shade * edge(j), sb = shade * edge(j + 1);
                    e.quad(out, restO[k][j], restO[k + 1][j], restO[k + 1][j + 1], restO[k][j + 1],
                            posedO[k][j], posedO[k + 1][j], posedO[k + 1][j + 1], posedO[k][j + 1],
                            ua, va, ub, va, ub, vb, ua, vb, sa, sa, sb, sb);
                }
                // the walls at both edges, down to the arm (the wrist side faces +y, the elbow side -y)
                float sw = shade * 0.82F;
                e.quad(new float[]{0, 1, 0}, restO[k][0], restO[k + 1][0], restI[k + 1][0], restI[k][0],
                        posedO[k][0], posedO[k + 1][0], posedI[k + 1][0], posedI[k][0],
                        ua, V0, ub, V0, ub, V0 + 0.01F, ua, V0 + 0.01F, sw, sw, sw, sw);
                e.quad(new float[]{0, -1, 0}, restO[k][ROWS], restO[k + 1][ROWS], restI[k + 1][ROWS], restI[k][ROWS],
                        posedO[k][ROWS], posedO[k + 1][ROWS], posedI[k + 1][ROWS], posedI[k][ROWS],
                        ua, V1 - 0.01F, ub, V1 - 0.01F, ub, V1, ua, V1, sw, sw, sw, sw);
            }
            if (f < 1F) {
                // an open turn (being wrapped): close both ends of the cloth
                cap(e, l, 0, -1, restO, restI, posedO, posedI, shade * 0.85F);
                cap(e, l, n - 1, 1, restO, restI, posedO, posedI, shade * 0.85F);
            }
        }
    }

    private static float edge(int row) {
        return row == 0 || row == ROWS ? 0.9F : 1F;
    }

    /** The cloth's cut end at loop point k, facing along the loop ({@code dir} -1: the start, backward; +1: the end, forward). */
    private static void cap(Emitter e, Loop l, int k, int dir, float[][][] restO, float[][][] restI, float[][][] posedO, float[][][] posedI, float shade) {
        int o = Mth.clamp(k - dir, 0, l.n - 1);   // the neighbour on the cloth's side
        float[] along = {l.ox[k] - l.ox[o], 0, l.oz[k] - l.oz[o]};   // away from the cloth
        for (int j = 0; j < ROWS; j++) {
            e.quad(along, restO[k][j], restO[k][j + 1], restI[k][j + 1], restI[k][j],
                    posedO[k][j], posedO[k][j + 1], posedI[k][j + 1], posedI[k][j],
                    U0, V0, U0 + 0.01F, V0, U0 + 0.01F, V0 + 0.01F, U0, V0 + 0.01F, shade, shade, shade, shade);
        }
    }

    /** Writes quads facing their reference direction (decided in the rest frame), flipped back under a mirrored pose. */
    private static final class Emitter {
        final Matrix4f mp;
        final Matrix3f mn;
        final VertexConsumer consumer;
        final int light, overlay;
        final boolean mirrored;
        final Vector3f p = new Vector3f(), nrm = new Vector3f();

        Emitter(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay) {
            this.mp = pose.pose();
            this.mn = pose.normal();
            this.consumer = consumer;
            this.light = light;
            this.overlay = overlay;
            this.mirrored = mp.determinant3x3() < 0F;
        }

        void quad(float[] ref, float[] ra, float[] rb, float[] rc, float[] rd, float[] pa, float[] pb, float[] pc, float[] pd,
                  float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd,
                  float sa, float sb, float sc, float sd) {
            boolean outward = dot(newell(ra, rb, rc, rd), ref) >= 0F;
            float[][] q = outward ? new float[][]{pa, pb, pc, pd} : new float[][]{pa, pd, pc, pb};
            float[][] uv = outward ? new float[][]{{ua, va}, {ub, vb}, {uc, vc}, {ud, vd}} : new float[][]{{ua, va}, {ud, vd}, {uc, vc}, {ub, vb}};
            float[] shades = outward ? new float[]{sa, sb, sc, sd} : new float[]{sa, sd, sc, sb};
            float[] n = newell(q[0], q[1], q[2], q[3]);
            float len = (float) Math.sqrt(dot(n, n));
            if (len < 1e-9F) return;
            mn.transform(n[0] / len, n[1] / len, n[2] / len, nrm);
            nrm.normalize();
            for (int i = 0; i < 4; i++) {
                int v = mirrored ? 3 - i : i;
                float[] pos = q[v];
                mp.transformPosition(pos[0] / 16F, pos[1] / 16F, pos[2] / 16F, p);
                float s = shades[v];
                consumer.vertex(p.x, p.y, p.z, s, s, s, 1F, uv[v][0], uv[v][1], overlay, light, nrm.x, nrm.y, nrm.z);
            }
        }

        /** The quad's normal, (c - a) x (d - b): the right way for a counter-clockwise a b c d. */
        private static float[] newell(float[] a, float[] b, float[] c, float[] d) {
            float x1 = c[0] - a[0], y1 = c[1] - a[1], z1 = c[2] - a[2];
            float x2 = d[0] - b[0], y2 = d[1] - b[1], z2 = d[2] - b[2];
            return new float[]{y1 * z2 - z1 * y2, z1 * x2 - x1 * z2, x1 * y2 - y1 * x2};
        }

        private static float dot(float[] a, float[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }
    }
}
