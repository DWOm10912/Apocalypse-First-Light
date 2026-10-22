package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.ClientHealth;
import com.antaurora.apofirstlight.health.Fractures;
import com.antaurora.apofirstlight.health.MedicalItem;
import com.antaurora.apofirstlight.health.SplintItem;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dressings and splints on the body (health V1a 7, the bandage on the body, user 2026-10-19: "绷带：需要真正留在玩家身上";
 * fractures and splints V1, 2026-10-20). What a player wears is the server's word (HealthPackets.Marks, sent to everyone
 * tracking the player; the own player's synced wounds too):
 * <ul>
 * <li><b>Gauze</b> on every part with a bandaged wound (BodyPart: the forearm of an arm, the shin of a leg; round the head
 * over the brow, round the chest under the armpits, round the waist: user 2026-10-21, a pad on the wound and turns round
 * the part, docs/gameplay/medical_actions_by_part_v1.md). The pad is WoundSkin's gauze patch under the turns.</li>
 * <li><b>A splint</b> on every splinted limb: two planed slats on its outer and inner side (the splint item's wood), held by
 * three narrow gauze ties round limb and slats; over a dressing on the same limb when there is one.</li>
 * </ul>
 * The gauze: three turns of plain off-white gauze round the sleeve, on the band {@link #ARM_BAND} / {@link #LEG_BAND} px back
 * from the limb's distal end (the hand cap or the sole; the arm's band is the contract with the wrapping animation): a
 * straight anchor turn at the distal end, one turn on a slant over it, a straight turn at the near end over that. Real
 * geometry built here, not a texture: each turn is a ring whose cross-section is the sleeve's square offset outward (rounded
 * corners), {@link #OUT} px off the sleeve (each turn a little further out than the one under it), with side walls down to
 * 0.05 px above the bare limb, so there is no coplanar face anywhere (the sleeve sits 0.25 px off the limb). Material: the
 * bandage item's own gauze (its mesh texture, a flat patch of it, with its LabPBR _n / _s), a little darker toward the edges
 * of each turn. The splint's slats take a flat patch of the splint mesh's wood.
 * <ul>
 * <li><b>Skeleton</b> (client/skeleton/PlayerSkeleton#draw calls {@link #drawSkeleton}): bent with the limb the same way the
 * C-min skin mesh is (SkinMesh: rings at the cuts, half way through the joint's rotation, linear between), so they follow
 * the wrist, elbow, ankle and knee without cutting into the skin or floating off it; the own first-person body too.</li>
 * <li><b>Vanilla body</b> (skeleton off, or poses it does not draw: swimming, sleeping, dying, flying):
 * {@link Layer} on the rigid arm, leg, head and body parts.</li>
 * <li><b>First-person view models</b> (guns, jerry can, crowbar...: weapon/client/NativePlayerArmRenderer
 * calls {@link #drawArm}): on that arm in its own frame.</li>
 * </ul>
 * The bandage's and the medical alcohol's actions go by the part the use started on ({@link #treatedPart}): a forearm is
 * held out and worked on by the other hand (the wound's arm, else the one opposite the main hand, {@link #workedArm}); a
 * leg sat on the ground, the head and the trunk have their own actions (SkeletonActions). While the bandage is used, the
 * turns appear one after another round its part: progress = clamp((use seconds - {@link #wrapFrom}) / {@link #wrapSeconds},
 * 0, 1), a third of it per turn, each turn from the middle of the part's front. Where the skeleton draws the action's
 * roll, the open turn's end follows the roll round the part instead (a little behind it) and a band of cloth runs from it
 * to the roll ({@link #strip}). Let go early and the partial wrap goes (unless that part was already bandaged); a finished
 * use keeps the whole wrap on its part up to 1 s until the server's answer arrives (it normally comes in the same tick).
 * <p>
 * The splint during its use (SplintItem: a forearm 8 s, a leg 6 s sat on the ground; the V2 actions item/splint_arm and
 * item/splint_leg on the limb SplintItem#target picks, docs/gameplay/splint_animation_v2_plan.md): the slats from
 * {@link #ARM_SLATS_AT} / {@link #LEG_SLATS_AT} s (where the action hides the splint it laid on), then the three ties one
 * after another by the clock, each growing round the limb from its front over {@link #ARM_TIE_TURN} / {@link #LEG_TIE_TURN}
 * s from {@link #ARM_TIE_FROM} / {@link #LEG_TIE_FROM} while the hands take the strip under and up over the top
 * (tools/medical-actions-v1a/author_splint_v2.cjs keeps the same schedule; the V2 actions carry no roll, so no cloth runs
 * off one). Let go early and the partial splint goes; a finished use keeps it up to 1 s for the server's answer, as the
 * bandage.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BandageWrapRenderer {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/bandage_mesh.png");
    public static final ResourceLocation SPLINT_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/splint_mesh.png");
    /** The wrapped band along the forearm, px back from the arm's distal end (the hand cap), in the arm's own frame. */
    public static final float BAND_FROM = 1.5F, BAND_TO = 5.5F;
    /** The same on a shin, back from the sole (above the ankle, below the knee). */
    private static final float[] ARM_BAND = {BAND_FROM, BAND_TO}, LEG_BAND = {2.0F, 6.0F};
    /**
     * The head's band up from the chin in its own frame (the brow and the scalp over the ears: the head's rows 0.6..3.4 from
     * its top, WoundSkin keeps the head's marks in rows 0..3), the chest's and the belly's up from the trunk's lower end
     * (rows 1..6 and 6.6..11.4 of the body, under the armpits and round the waist); their turns' widths (px).
     */
    private static final float[] HEAD_BAND = {4.6F, 7.4F}, CHEST_BAND = {6.0F, 11.0F}, BELLY_BAND = {0.6F, 5.4F};
    private static final float HEAD_WIDTH = 1.3F, TRUNK_WIDTH = 2.0F;
    public static final int TURNS = 3;
    /**
     * The wrapping during the use (s): from {@link #wrapFrom}, over {@link #wrapSeconds}, a third of it per turn: the forearm
     * 0.6 + 2.5, a shin 0.7 + 3.0 (sat), the head 0.8 + 3.0, the chest or belly 0.9 + 4.2 (the actions' turns,
     * tools/medical-actions-v1a/author_bandage.cjs and author_dress_v1.cjs).
     */
    public static final float WRAP_FROM = 0.6F, WRAP_SECONDS = 2.5F;

    public static float wrapFrom(BodyPart part) {
        return part.arm() ? WRAP_FROM : part.leg() ? 0.7F : part == BodyPart.HEAD ? 0.8F : 0.9F;
    }

    public static float wrapSeconds(BodyPart part) {
        return part.arm() ? WRAP_SECONDS : part.leg() || part == BodyPart.HEAD ? 3.0F : 4.2F;
    }
    /**
     * The splint's use (s; the V2 actions, tools/medical-actions-v1a/author_splint_v2.cjs): the slats from here, each tie
     * growing round the limb from these over this long (the strip under and up over the top), forearm and leg.
     */
    public static final float ARM_SLATS_AT = 1.3F, LEG_SLATS_AT = 0.9F, ARM_TIE_TURN = 0.55F, LEG_TIE_TURN = 0.65F;
    private static final float[] ARM_TIE_FROM = {2.0F, 3.4F, 4.8F}, LEG_TIE_FROM = {1.45F, 2.7F, 3.95F};

    /** Width of one turn (px along the limb). */
    private static final float WIDTH = 1.7F;
    /** Per turn: its slant (px along the limb per px across), its surface off the sleeve, its shade. */
    private static final float[] TILT = {0F, 0.16F, 0F};
    private static final float[] OUT = {0.14F, 0.21F, 0.28F};
    private static final float[] SHADE = {1F, 0.97F, 0.99F};
    /** The second layer's inflate (vanilla 0.25), and how far above the bare limb the turns' walls end. */
    private static final float SLEEVE = 0.25F, INNER = 0.05F;
    /** The hat layer's inflate (vanilla 0.5): the head's turns go round it. */
    private static final float HAT = 0.5F;
    private static final int ARC = 3, ROWS = 4;
    /** A flat patch of the bandage mesh's gauze (textures/item/bandage_mesh.png, 256 px: plain gauze, flat _n). */
    private static final float U0 = 8 / 256F, U1 = 88 / 256F, V0 = 8 / 256F, V1 = 44 / 256F;
    /** A flat patch of the splint mesh's wood (textures/item/splint_mesh.png, the plain right lower field). */
    private static final float WU0 = 160 / 256F, WU1 = 240 / 256F, WV0 = 170 / 256F, WV1 = 246 / 256F;
    /**
     * The splint on a limb: the slats from / to (px back from the distal end: over the wrist / ankle to just past the elbow /
     * knee); the three ties' middles along the limb; the slats' width across (z), thickness, gap off the sleeve (more over a
     * dressing); the ties' width and how far off the slats.
     */
    private static final float[] ARM_SLAT = {0.8F, 7.2F}, LEG_SLAT = {0.8F, 7.6F};
    private static final float[] ARM_TIES = {1.6F, 4.0F, 6.5F}, LEG_TIES = {1.6F, 4.2F, 6.9F};
    private static final float SLAT_W = 1.7F, SLAT_T = 0.5F, SLAT_GAP = 0.02F, SLAT_GAP_DRESSED = 0.34F, TIE_W = 0.9F, TIE_OUT = 0.1F;
    private static final int SLAT_ROWS = 8;
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
    private static final BodyPart[] PARTS = {BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG,
            BodyPart.HEAD, BodyPart.CHEST, BodyPart.BELLY};

    private BandageWrapRenderer() {
    }

    // ---------------------------------------------------------------- what to draw

    /**
     * The part the bandage or the alcohol in use works on: the one its use started on (held for the use, so the action and
     * the wrap do not jump when the server's answer moves the next target), else the wound that takes it now
     * (MedicalItem#target), or null.
     */
    @Nullable
    public static BodyPart treatedPart(Player player, Wounds.Treatment treatment) {
        Track t = TRACKS.get(player.getUUID());
        BodyPart held = t == null ? null : treatment == Wounds.Treatment.BANDAGE ? (t.lastTicks >= 0 ? t.part : null) : t.alcohol;
        return held != null ? held : MedicalItem.target(player, treatment);
    }

    /**
     * The forearm the bandage and the alcohol actions work on (held out, the other hand wrapping or pouring): the wound's arm
     * when the treated wound is on an arm, else the one opposite the main hand. Right: SkeletonActions mirrors the action.
     */
    public static BodyPart workedArm(Player player, Wounds.Treatment treatment) {
        BodyPart t = treatedPart(player, treatment);
        if (t != null && t.arm()) return t;
        return player.getMainArm() == HumanoidArm.LEFT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
    }

    /**
     * The way the turns on the head, chest and belly go round (SkeletonActions mirrors those actions for a left-handed
     * player): the right hand carries the roll from the front over the right side (-x) to the back, the left brings it
     * round the left side to the front; mirrored, the other way.
     */
    private static boolean wrapsPlusX(Player player) {
        return player.getMainArm() == HumanoidArm.LEFT;
    }

    private static boolean usingBandage(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof MedicalItem item
                && item.treatment() == Wounds.Treatment.BANDAGE;
    }

    private static boolean usingAlcohol(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof MedicalItem item
                && item.treatment() == Wounds.Treatment.ALCOHOL;
    }

    private static final class Track {
        int lastTicks = -1;
        long holdUntil = Long.MIN_VALUE;
        /** The part the bandage in use goes on (its wound's), held after a finished use. */
        BodyPart part;
        /** The part the alcohol in use goes on, while it is used. */
        BodyPart alcohol;
        /** The same for a splint in use: ticks used (-1: none), held until, its limb. */
        int splintTicks = -1;
        long splintHold = Long.MIN_VALUE;
        BodyPart splint;
    }

    private static boolean usingSplint(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof SplintItem;
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
            boolean using = usingBandage(p), splinting = usingSplint(p), cleaning = usingAlcohol(p);
            Track t = TRACKS.get(p.getUUID());
            if (t == null) {
                if (!using && !splinting && !cleaning) continue;
                TRACKS.put(p.getUUID(), t = new Track());
            }
            if (!cleaning) t.alcohol = null;
            else if (t.alcohol == null) t.alcohol = MedicalItem.target(p, Wounds.Treatment.ALCOHOL);
            if (splinting) {
                if (t.splintTicks < 0) t.splint = SplintItem.target(p);
                t.splintTicks = SplintItem.usedTicks(p);
            } else {
                int full = t.splint != null && t.splint.leg() ? SplintItem.LEG_USE_TICKS : SplintItem.USE_TICKS;
                if (t.splintTicks >= full - 3) t.splintHold = now + HOLD_TICKS;
                t.splintTicks = -1;
            }
            if (using) {
                if (t.lastTicks < 0) t.part = MedicalItem.target(p, Wounds.Treatment.BANDAGE);
                t.lastTicks = p.getTicksUsingItem();
            } else {
                // the use ended at (about) its full length on its part: finished, the server's bandaged state is on its way
                if (t.part != null && t.lastTicks >= MedicalItem.useTicks(Wounds.Treatment.BANDAGE, t.part) - 3) t.holdUntil = now + HOLD_TICKS;
                t.lastTicks = -1;
            }
        }
        TRACKS.values().removeIf(t -> t.lastTicks < 0 && t.holdUntil < now && t.splintTicks < 0 && t.splintHold < now && t.alcohol == null);
    }

    /** Whether the player wears a dressing on this part now: the server's word (the own player's synced wounds too). */
    public static boolean bandaged(Player player, BodyPart part) {
        if (ClientHealth.marks(player.getUUID()).bandaged(part)) return true;
        if (player != Minecraft.getInstance().player) return false;
        for (Wounds.View w : ClientHealth.wounds()) if (w.bandaged() && w.part() == part) return true;
        return false;
    }

    /** Whether this limb wears a splint now (the server's word; the own player's synced fractures too). */
    public static boolean splinted(Player player, BodyPart part) {
        if (ClientHealth.marks(player.getUUID()).splinted(part)) return true;
        if (player != Minecraft.getInstance().player) return false;
        for (Fractures.View f : ClientHealth.fractures()) if (f.splinted() && f.part() == part) return true;
        return false;
    }

    /**
     * How far the splint on this limb is: +infinity worn (the server's word, or a finished use held for its answer), the
     * seconds of the use going on on it, NaN none.
     */
    public static float splintSeconds(Player player, BodyPart limb, float partial) {
        if (splinted(player, limb)) return Float.POSITIVE_INFINITY;
        Track t = TRACKS.get(player.getUUID());
        if (t == null || t.splint != limb) return Float.NaN;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() < t.splintHold) return Float.POSITIVE_INFINITY;
        if (!usingSplint(player)) return Float.NaN;
        return (SplintItem.usedTicks(player) + partial) / 20F;
    }

    private static boolean shows(float splint, BodyPart limb) {
        return splint >= (limb.leg() ? LEG_SLATS_AT : ARM_SLATS_AT);
    }

    /** How much of the gauze on this part is on (0 none, 1 all three turns). */
    public static float coverage(Player player, BodyPart part, float partial) {
        if (bandaged(player, part)) return 1F;
        Track t = TRACKS.get(player.getUUID());
        if (t == null || t.part != part) return 0F;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() < t.holdUntil) return 1F;
        // wrapped in view on the part the action works on, turn after turn by its clock
        if (!usingBandage(player) || t.lastTicks < 0) return 0F;
        float seconds = (player.getTicksUsingItem() + partial) / 20F;
        return Mth.clamp((seconds - wrapFrom(part)) / wrapSeconds(part), 0F, 1F);
    }

    // ---------------------------------------------------------------- the three ways it is drawn

    /** The player the skeleton drew last (its layers then leave the wrap alone); cleared by the layer and every frame. */
    private static Player skeletonDrew;

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) skeletonDrew = null;
    }

    /** Whether the skeleton drew this player just now (its layers then leave the body alone; WoundSkin.Layer, before this one's). */
    public static boolean skeletonDrewThisFrame(Player player) {
        return skeletonDrew == player;
    }

    private static String skinName(BodyPart part) {
        return switch (part) {
            case RIGHT_ARM -> "right_arm";
            case LEFT_ARM -> "left_arm";
            case RIGHT_LEG -> "right_leg";
            case LEFT_LEG -> "left_leg";
            default -> "body";
        };
    }

    private static ModelPart modelPart(PlayerModel<?> model, BodyPart part) {
        return switch (part) {
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
            case HEAD -> model.head;
            default -> model.body;
        };
    }

    /**
     * PlayerSkeleton#draw, after the skin: dressings and splints bent with the skin mesh. The pose stack is at the model
     * origin (model px / 16), as for the skin; {@code thick} is the arms' width there (SkinMesh#emit); {@code roll}: the
     * bandage prop's middle (model px) while the action holds it in view, for the cloth pulled off it ({@link #strip}), else null.
     */
    public static void drawSkeleton(AbstractClientPlayer player, SkeletonPose pose, boolean slim, float thick, PlayerModel<?> model,
                                    PoseStack stack, MultiBufferSource buffers, int light, int overlay, @Nullable Vector3f roll) {
        skeletonDrew = player;
        if (player.isInvisible()) return;
        float partial = Minecraft.getInstance().getFrameTime();
        SkeletonRig rig = pose.rig;
        boolean plusX = wrapsPlusX(player);
        for (BodyPart limb : PARTS) {
            boolean limbPart = limb.arm() || limb.leg();
            float cover = coverage(player, limb, partial);
            float splint = limbPart ? splintSeconds(player, limb, partial) : Float.NaN;
            if (cover <= 0F && !shows(splint, limb) || !modelPart(model, limb).visible) continue;
            Limb arm;
            Deform deform;
            // posed (model px) to the part's rest frame through the band's middle, for the roll's place round it
            Matrix4f restOf;
            if (limb == BodyPart.HEAD) {
                // the vanilla head the skeleton draws (PlayerSkeleton#drawSolved), its frame as ModelPart#translateAndRotate
                ModelPart h = model.head;
                Matrix4f m = new Matrix4f().translation(h.x, h.y, h.z).rotate(new Quaternionf().rotationZYX(h.zRot, h.yRot, h.xRot));
                arm = limb(limb, -4F, 4F, -4F, 4F, 0F, HAT, plusX);
                deform = (x, y, z, out) -> {
                    Vector3f v = m.transformPosition(x, y, z, new Vector3f());
                    out[0] = v.x;
                    out[1] = v.y;
                    out[2] = v.z;
                };
                restOf = new Matrix4f(m).invert();
            } else {
                SkeletonRig.SkinPart part = null;
                for (SkeletonRig.SkinPart p : rig.skin) if (p.part().equals(skinName(limb))) part = p;
                if (part == null) continue;
                SkeletonRig.Box b = part.box(slim);
                float cx = b.x() + b.w() / 2F, half = b.w() / 2F * (limb.arm() ? thick : 1F);
                arm = limb(limb, cx - half, cx + half, b.z(), b.z() + b.d(), b.y() + b.h(), rig.overlayInflate, plusX);
                // the skin's rings along the sleeve or the trunk (SkinMesh#emit: the inflated layer's levels, the cuts' matrices)
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
                deform = (x, y, z, out) -> {
                    int r = 0;
                    while (r < rings - 2 && y > levels[r + 1]) r++;
                    float t = Mth.clamp((y - levels[r]) / (levels[r + 1] - levels[r]), 0F, 1F);
                    Vector3f a = matrices[r].transformPosition(x, levels[r], z, new Vector3f());
                    Vector3f c = matrices[r + 1].transformPosition(x, levels[r + 1], z, new Vector3f());
                    out[0] = a.x + (c.x - a.x) * t;
                    out[1] = a.y + (c.y - a.y) * t;
                    out[2] = a.z + (c.z - a.z) * t;
                };
                float mid = arm.yEnd - (arm.zone[0] + arm.zone[1]) / 2;
                int r = 0;
                while (r < rings - 2 && mid > levels[r + 1]) r++;
                restOf = new Matrix4f(matrices[r]).invert();
            }
            boolean winding = roll != null && cover > 0F && cover < 1F && usingBandage(player);
            if (winding) {
                // the roll's place round the part (its rest frame)
                Vector3f rest = restOf.transformPosition(roll, new Vector3f());
                float dx = rest.x - (arm.x0 + arm.x1) / 2, dz = rest.z - (arm.z0 + arm.z1) / 2;
                float deg = (float) Math.toDegrees(Math.atan2(dz, arm.dorsalPlusX ? dx : -dx));
                float phase = Mth.positiveModulo(deg - START - TRAIL, 360F) / 360F;
                // the turn the clock says, the place in it the roll says (the two stay within half a turn of each other)
                float turns = cover * TURNS;
                cover = Mth.clamp(Math.round(turns - phase) + phase, 0F, TURNS) / TURNS;
            }
            if (cover > 0F) draw(arm, deform, cover, stack, buffers, light, overlay);
            if (winding && cover > 0F) strip(arm, deform, cover, roll, stack, buffers, light, overlay);
            if (shows(splint, limb)) splint(arm, deform, cover > 0F, splint, roll, stack, buffers, light, overlay);
        }
    }

    /**
     * The cloth pulled off the roll while it winds (user 2026-10-19: pulled out like tape): a flat band from the open turn's
     * leading end on the arm to the roll the other hand carries round (the action item/bandage_wrap's prop, its middle
     * {@code roll} in model px), as wide as a turn, both sides drawn.
     */
    private static void strip(Limb a, Deform deform, float cover, Vector3f roll, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        int turn = Math.min(TURNS - 1, (int) (cover * TURNS));
        float f = Mth.clamp(cover * TURNS - turn, 0F, 1F);
        strip(a, deform, loop(a, OUT[turn]), f, centre(a, turn), TILT[turn], a.width, roll, stack, buffers, light, overlay);
    }

    /** The band off the roll to the open end at {@code f} of loop {@code l} (a turn or a splint tie: its middle c, slant, width). */
    private static void strip(Limb a, Deform deform, Loop l, float f, float c, float tilt, float width, Vector3f roll, PoseStack stack,
                              MultiBufferSource buffers, int light, int overlay) {
        int k = 0;
        while (k < l.n - 2 && l.t[k + 1] < f) k++;
        float u = Mth.clamp((f - l.t[k]) / Math.max(1e-6F, l.t[k + 1] - l.t[k]), 0F, 1F);
        float x = Mth.lerp(u, l.ox[k], l.ox[k + 1]), z = Mth.lerp(u, l.oz[k], l.oz[k + 1]);
        float xc = (a.x0 + a.x1) / 2, zc = (a.z0 + a.z1) / 2;
        float ex = (a.dorsalPlusX ? 1F : -1F) * Mth.SQRT_OF_TWO / 2, ez = Mth.SQRT_OF_TWO / 2;
        float y = a.yEnd - (c + tilt * ((x - xc) * ex + (z - zc) * ez));
        float[] a0 = new float[3], a1 = new float[3];
        deform.apply(x, y - width / 2, z, a0);
        deform.apply(x, y + width / 2, z, a1);
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
     * weapon/client/NativePlayerArmRenderer, after an arm and its sleeve: that arm's dressing and splint. The pose stack is the
     * arm part's own frame (its pose zeroed), as the vanilla cube is drawn in.
     */
    public static void drawArm(Player player, boolean right, boolean slim, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        if (player.isInvisible()) return;
        BodyPart limb = right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
        float partial = Minecraft.getInstance().getFrameTime();
        float cover = coverage(player, limb, partial), splint = splintSeconds(player, limb, partial);
        if (cover > 0F || shows(splint, limb)) drawPart(limb, slim, false, cover, splint, stack, buffers, light, overlay);
    }

    /**
     * On a vanilla part, in its frame (PlayerModel: left arm cube from (-1, -2, -2), right from (-3 / slim -2, -2, -2),
     * 12 long; legs from (-2, 0, -2), 12 long; the head's cube from (-4, -8, -4), 8 each way, under the hat; the body's
     * from (-4, 0, -2), 8 x 12 x 4). {@code plusX}: #wrapsPlusX.
     */
    private static void drawPart(BodyPart limb, boolean slim, boolean plusX, float cover, float splint, PoseStack stack,
                                 MultiBufferSource buffers, int light, int overlay) {
        Limb arm;
        if (limb.arm()) {
            boolean right = limb.right();
            float w = slim ? 3F : 4F, x0 = right ? (slim ? -2F : -3F) : -1F;
            arm = new Limb(x0, x0 + w, -2F, 2F, 10F, SLEEVE, !right, false);
        } else if (limb.leg()) {
            arm = new Limb(-2F, 2F, -2F, 2F, 12F, SLEEVE, !limb.right(), true);
        } else if (limb == BodyPart.HEAD) {
            arm = limb(limb, -4F, 4F, -4F, 4F, 0F, HAT, plusX);
        } else {
            arm = limb(limb, -4F, 4F, -2F, 2F, 12F, SLEEVE, plusX);
        }
        Deform rigid = (x, y, z, out) -> { out[0] = x; out[1] = y; out[2] = z; };
        if (cover > 0F) draw(arm, rigid, cover, stack, buffers, light, overlay);
        if (shows(splint, limb)) splint(arm, rigid, cover > 0F, splint, null, stack, buffers, light, overlay);
    }

    /** The vanilla body's dressings and splints (the skeleton draws its own; its draw marks the player so this skips it). */
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
            boolean slim = "slim".equals(player.getModelName());
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);
            boolean plusX = wrapsPlusX(player);
            for (BodyPart limb : PARTS) {
                float cover = coverage(player, limb, partialTick);
                float splint = limb.arm() || limb.leg() ? splintSeconds(player, limb, partialTick) : Float.NaN;
                if (cover <= 0F && !shows(splint, limb)) continue;
                ModelPart part = modelPart(getParentModel(), limb);
                if (!part.visible) continue;
                pose.pushPose();
                part.translateAndRotate(pose);
                drawPart(limb, slim, plusX, cover, splint, pose, buffers, light, overlay);
                pose.popPose();
            }
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                    renderer.addLayer(new WoundSkin.Layer(renderer));   // before Layer, which clears skeletonDrew
                    renderer.addLayer(new Layer(renderer));
                }
            }
        }
    }

    // ---------------------------------------------------------------- geometry

    /** Rest point (the limb's frame, px) to its drawn place (model px, or the same frame). */
    private interface Deform {
        void apply(float x, float y, float z, float[] out);
    }

    /**
     * The bare part's box across (x, z) and its distal end (y, the limb runs +y toward the hand or foot; the head's chin,
     * the trunk's lower end), the second layer's inflate, which way the turns go round and lean (+x for the left limbs;
     * #wrapsPlusX on the head and trunk), whether it is a leg, the wrapped band (px back from the distal end) and a turn's width.
     */
    private record Limb(float x0, float x1, float z0, float z1, float yEnd, float g, boolean dorsalPlusX, boolean leg,
                        float[] zone, float width) {
        Limb(float x0, float x1, float z0, float z1, float yEnd, float g, boolean dorsalPlusX, boolean leg) {
            this(x0, x1, z0, z1, yEnd, g, dorsalPlusX, leg, leg ? LEG_BAND : ARM_BAND, WIDTH);
        }

        float[] band() {
            return zone;
        }
    }

    /** A part's box: a limb's own way round and band, the head's or trunk's band and width, round as #wrapsPlusX says. */
    private static Limb limb(BodyPart part, float x0, float x1, float z0, float z1, float yEnd, float g, boolean plusX) {
        if (part.arm() || part.leg()) return new Limb(x0, x1, z0, z1, yEnd, g, !part.right(), part.leg());
        float[] zone = part == BodyPart.HEAD ? HEAD_BAND : part == BodyPart.CHEST ? CHEST_BAND : BELLY_BAND;
        return new Limb(x0, x1, z0, z1, yEnd, g, plusX, false, zone, part == BodyPart.HEAD ? HEAD_WIDTH : TRUNK_WIDTH);
    }

    /** A turn's middle, px back from the distal end: the first at the distal end of the band, the last at its near end. */
    private static float centre(Limb a, int turn) {
        float[] band = a.band();
        return switch (turn) {
            case 0 -> band[0] + a.width / 2;
            case 1 -> (band[0] + band[1]) / 2;
            default -> band[1] - a.width / 2;
        };
    }

    /** A turn's loop round the limb: outer points (x, z), their outward normals, the walls' inner ends, and the arc length 0..1. */
    private static final class Loop {
        final int n;
        final float[] ox, oz, nx, nz, ix, iz, t;

        Loop(int n) {
            this.n = n;
            ox = new float[n]; oz = new float[n]; nx = new float[n]; nz = new float[n]; ix = new float[n]; iz = new float[n]; t = new float[n];
        }
    }

    /**
     * The closed loop at {@code r} off the sleeve, from the middle of the limb's front (-z, {@link #START}) round to it again,
     * the way the bandage action's roll goes round (angle atan2(z, x - middle) rising on the left arm; mirrored in x on the
     * right one, as the action is): the sleeve's square with its corners rounded (arcs about the sleeve's corners), the inner
     * ends at the bare limb's corners + INNER.
     */
    private static Loop loop(Limb a, float r) {
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
    private static int mid(Limb a, Loop l, int k, int side, float r, float zc) {
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

    /** The gauze: up to three turns, {@code cover} of them. */
    private static void draw(Limb a, Deform deform, float cover, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        Emitter e = new Emitter(stack.last(), buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, overlay);
        for (int turn = 0; turn < TURNS; turn++) {
            float f = Mth.clamp(cover * TURNS - turn, 0F, 1F);
            if (f <= 0.02F) break;
            ring(e, a, deform, cut(loop(a, OUT[turn]), f), centre(a, turn), a.width, TILT[turn], SHADE[turn], f < 1F);
        }
    }

    /**
     * One band of cloth round the limb along a loop: its middle {@code c} px back from the distal end, {@code width} wide,
     * slanted by {@code tilt} across the back and the outside alike (a diagonal of the square, mirrored with the limb).
     */
    private static void ring(Emitter e, Limb a, Deform deform, Loop l, float c, float width, float tilt, float shade, boolean open) {
        float xc = (a.x0 + a.x1) / 2, zc = (a.z0 + a.z1) / 2;
        float ex = (a.dorsalPlusX ? 1F : -1F) * Mth.SQRT_OF_TWO / 2, ez = Mth.SQRT_OF_TWO / 2;
        int n = l.n;
        float[][][] restO = new float[n][ROWS + 1][], restI = new float[n][ROWS + 1][];
        float[][][] posedO = new float[n][ROWS + 1][], posedI = new float[n][ROWS + 1][];
        for (int k = 0; k < n; k++) {
            float sc = c + tilt * ((l.ox[k] - xc) * ex + (l.oz[k] - zc) * ez);
            for (int j = 0; j <= ROWS; j++) {
                float y = a.yEnd - (sc - width / 2 + width * j / ROWS);
                restO[k][j] = new float[]{l.ox[k], y, l.oz[k]};
                restI[k][j] = new float[]{l.ix[k], y, l.iz[k]};
                deform.apply(l.ox[k], y, l.oz[k], posedO[k][j] = new float[3]);
                deform.apply(l.ix[k], y, l.iz[k], posedI[k][j] = new float[3]);
            }
        }
        for (int k = 0; k + 1 < n; k++) {
            float ua = Mth.lerp(l.t[k], U0, U1), ub = Mth.lerp(l.t[k + 1], U0, U1);
            float[] out = {l.nx[k] + l.nx[k + 1], 0, l.nz[k] + l.nz[k + 1]};
            for (int j = 0; j < ROWS; j++) {
                float va = Mth.lerp(j / (float) ROWS, V0, V1), vb = Mth.lerp((j + 1) / (float) ROWS, V0, V1);
                // a little darker toward the band's edges, as gauze folds over them
                float sa = shade * edge(j), sb = shade * edge(j + 1);
                e.quad(out, restO[k][j], restO[k + 1][j], restO[k + 1][j + 1], restO[k][j + 1],
                        posedO[k][j], posedO[k + 1][j], posedO[k + 1][j + 1], posedO[k][j + 1],
                        ua, va, ub, va, ub, vb, ua, vb, sa, sa, sb, sb);
            }
            // the walls at both edges, down to the limb (the distal side faces +y, the near side -y)
            float sw = shade * 0.82F;
            e.quad(new float[]{0, 1, 0}, restO[k][0], restO[k + 1][0], restI[k + 1][0], restI[k][0],
                    posedO[k][0], posedO[k + 1][0], posedI[k + 1][0], posedI[k][0],
                    ua, V0, ub, V0, ub, V0 + 0.01F, ua, V0 + 0.01F, sw, sw, sw, sw);
            e.quad(new float[]{0, -1, 0}, restO[k][ROWS], restO[k + 1][ROWS], restI[k + 1][ROWS], restI[k][ROWS],
                    posedO[k][ROWS], posedO[k + 1][ROWS], posedI[k + 1][ROWS], posedI[k][ROWS],
                    ua, V1 - 0.01F, ub, V1 - 0.01F, ub, V1, ua, V1, sw, sw, sw, sw);
        }
        if (open) {
            // an open turn (being wrapped): close both ends of the cloth
            cap(e, l, 0, -1, restO, restI, posedO, posedI, shade * 0.85F);
            cap(e, l, n - 1, 1, restO, restI, posedO, posedI, shade * 0.85F);
        }
    }

    /**
     * The splint: a slat on the limb's outer and inner side (±x), {@link #SLAT_GAP} off the sleeve (further over a dressing),
     * and three gauze ties round limb and slats: the ties' loops go round a box widened by the slats, so they lie
     * {@link #TIE_OUT} over the slats' outer faces and over the sleeve (or the dressing) front and back; their walls end
     * inside the slats on the sides, 0.05 px over the bare limb front and back. No coplanar faces. {@code seconds}: into the
     * splint's use (the ties so far, {@link #splintSeconds}; +infinity all); {@code roll}: the action's roll (model px) for the
     * cloth off it to the open tie, or null.
     */
    private static void splint(Limb a, Deform deform, boolean dressed, float seconds, @Nullable Vector3f roll, PoseStack stack,
                               MultiBufferSource buffers, int light, int overlay) {
        float gap = dressed ? SLAT_GAP_DRESSED : SLAT_GAP;
        float[] slat = a.leg ? LEG_SLAT : ARM_SLAT, ties = a.leg ? LEG_TIES : ARM_TIES;
        float zc = (a.z0 + a.z1) / 2;
        Emitter wood = new Emitter(stack.last(), buffers.getBuffer(RenderType.entitySolid(SPLINT_TEXTURE)), light, overlay);
        for (int side = -1; side <= 1; side += 2) {
            float in = side > 0 ? a.x1 + a.g + gap : a.x0 - a.g - gap, out = in + side * SLAT_T;
            slat(wood, deform, Math.min(in, out), Math.max(in, out), zc - SLAT_W / 2, zc + SLAT_W / 2,
                    a.yEnd - slat[1], a.yEnd - slat[0]);
        }
        // the ties: round the limb widened by the slats on x, over the dressing front and back when there is one
        float widen = gap + SLAT_T;
        Limb wide = new Limb(a.x0 - widen, a.x1 + widen, a.z0, a.z1, a.yEnd, a.g, a.dorsalPlusX, a.leg);
        float r = TIE_OUT + (dressed ? OUT[TURNS - 1] + 0.06F : 0F);
        Emitter gauze = new Emitter(stack.last(), buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, overlay);
        Loop l = loop(wide, r);
        // the walls' inner ends: inside the slats on the sides, over the bare limb front and back
        for (int i = 0; i < l.n; i++) {
            if (Math.abs(l.nx[i]) > 0.7F) l.ix[i] = l.nx[i] > 0 ? a.x1 + a.g + gap + 0.1F : a.x0 - a.g - gap - 0.1F;
            else l.ix[i] = Mth.clamp(l.ix[i], a.x0 - INNER, a.x1 + INNER);
        }
        for (int i = 0; i < ties.length; i++) {
            float f = Mth.clamp((seconds - (a.leg ? LEG_TIE_FROM : ARM_TIE_FROM)[i]) / (a.leg ? LEG_TIE_TURN : ARM_TIE_TURN), 0F, 1F);
            if (f <= 0.02F) continue;
            ring(gauze, wide, deform, f < 1F ? cut(l, f) : l, ties[i], TIE_W, 0F, 0.98F, f < 1F);
            if (f < 1F && roll != null) strip(wide, deform, l, f, ties[i], 0F, TIE_W, roll, stack, buffers, light, overlay);
        }
    }

    /** One slat: a box across [x0, x1] x [z0, z1] from y0 to y1 (rest frame), in rows along the limb so it bends with it. */
    private static void slat(Emitter e, Deform deform, float x0, float x1, float z0, float z1, float y0, float y1) {
        float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        float[][] normals = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // the side from corner i to i + 1
        float[][][] rest = new float[SLAT_ROWS + 1][4][], posed = new float[SLAT_ROWS + 1][4][];
        for (int j = 0; j <= SLAT_ROWS; j++) {
            float y = Mth.lerp(j / (float) SLAT_ROWS, y0, y1);
            for (int i = 0; i < 4; i++) {
                rest[j][i] = new float[]{corners[i][0], y, corners[i][1]};
                deform.apply(corners[i][0], y, corners[i][1], posed[j][i] = new float[3]);
            }
        }
        float thin = WU0 + (WU1 - WU0) * 0.3F;
        for (int j = 0; j < SLAT_ROWS; j++) {
            float va = Mth.lerp(j / (float) SLAT_ROWS, WV0, WV1), vb = Mth.lerp((j + 1) / (float) SLAT_ROWS, WV0, WV1);
            for (int i = 0; i < 4; i++) {
                int i2 = (i + 1) % 4;
                // the faces across the slat's width (±z: the edges) take a strip of the patch, the broad ones (±x) all of it
                boolean broad = i % 2 == 1;
                float ub = broad ? WU1 : thin, sh = broad ? 1F : 0.9F;
                e.quad(new float[]{normals[i][0], 0, normals[i][1]}, rest[j][i], rest[j][i2], rest[j + 1][i2], rest[j + 1][i],
                        posed[j][i], posed[j][i2], posed[j + 1][i2], posed[j + 1][i], WU0, va, ub, va, ub, vb, WU0, vb, sh, sh, sh, sh);
            }
        }
        // the sawn ends, a touch darker
        float v1 = WV0 + (WV1 - WV0) * 0.1F;
        int last = SLAT_ROWS;
        e.quad(new float[]{0, -1, 0}, rest[0][0], rest[0][1], rest[0][2], rest[0][3], posed[0][0], posed[0][1], posed[0][2], posed[0][3],
                WU0, WV0, thin, WV0, thin, v1, WU0, v1, 0.8F, 0.8F, 0.8F, 0.8F);
        e.quad(new float[]{0, 1, 0}, rest[last][0], rest[last][1], rest[last][2], rest[last][3],
                posed[last][0], posed[last][1], posed[last][2], posed[last][3], WU0, WV0, thin, WV0, thin, v1, WU0, v1, 0.8F, 0.8F, 0.8F, 0.8F);
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
