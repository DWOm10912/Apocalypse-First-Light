package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FuelCanBlock;
import com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.fluid.FuelPourTarget;
import com.antaurora.apofirstlight.fluid.NozzleFill;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * The fuel nozzle in a fuel opening, on the client (2026-10-10, fluid/NozzleFill; docs/models/fuel_dispenser_v1.md "插枪加油").
 * The dispenser syncs which opening each nozzle is in and when that changed (FuelDispenserBlockEntity#inserted,
 * clientChanged). Meanwhile:
 * <ul>
 *   <li>the nozzle leaves the hand: client/FuelDispenserRenderer draws it in the world, over {@link #IN_TICKS} from the held
 *   pose into the opening (spout down, tilted away from the holder), back over {@link #OUT_TICKS} when it comes out, and
 *   the hose ends at it. The held item is hidden: first person, the hand (the arm is drawn to the grip in the world,
 *   {@link #renderArm}, as client/PortableGeneratorPull does); third person, the item layer (mixin client.ItemInHandLayerNozzleMixin),
 *   the right arm pointing at the grip ({@link #poseArm}, mixin client.HumanoidModelPullMixin);</li>
 *   <li>the local holder crouches and keeps still, and the view turns, easing, to the opening (as client/JerryCanPourView);</li>
 *   <li>the world hint says "pull out" at the opening (client/WorldInteractionHint).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NozzleFillView {
    public static final int IN_TICKS = 6, OUT_TICKS = 5;
    private static final double TURN_RATE = 5.0;
    private static long lastFrame;
    /** The local nozzle went in or out while the use button was down: its repeats do nothing until it is let go. */
    private static boolean holdUntilRelease, wasIn;

    private NozzleFillView() {
    }

    // ---- who holds what ----

    /** A player's own held nozzle (the dispenser records them as its holder), or null. */
    private record Held(FuelDispenserBlockEntity dispenser, Nozzle nozzle) {}

    @Nullable
    private static Held held(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FuelNozzleItem)) return null;
        FuelDispenserBlockEntity dispenser = FuelNozzleItem.dispenser(stack, player.level());
        Nozzle nozzle = FuelNozzleItem.nozzle(stack);
        return dispenser != null && nozzle != null && player.getUUID().equals(dispenser.holder(nozzle)) ? new Held(dispenser, nozzle) : null;
    }

    /** The opening of a block as drawn (also while the server is about to take the nozzle out of a closing fill). */
    @Nullable
    private static Vec3 drawnOpening(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FuelCanBlock) return FuelCanBlock.opening(pos, state);
        if (state.getBlock() instanceof FuelPourTarget target) return target.pourOpening(level, pos, state);
        return null;
    }

    /** A nozzle drawn in the world: the opening it goes to, its heading, and how far in it is (0 the hand .. 1 in). */
    public record Way(Vec3 opening, float heading, double k) {}

    /** This nozzle's way into or out of its opening now, or null while it is plainly in the hand. */
    @Nullable
    public static Way way(FuelDispenserBlockEntity dispenser, Nozzle nozzle, float partialTick) {
        Level level = dispenser.getLevel();
        if (level == null) return null;
        int i = nozzle.ordinal();
        double age = level.getGameTime() + partialTick - dispenser.clientChanged[i];
        BlockPos in = dispenser.inserted(nozzle);
        if (in != null) {
            Vec3 opening = drawnOpening(level, in);
            return opening == null ? null : new Way(opening, dispenser.insertHeading(nozzle), smooth(age / IN_TICKS));
        }
        BlockPos was = dispenser.clientWas[i];
        if (was == null || age >= OUT_TICKS) return null;
        Vec3 opening = drawnOpening(level, was);
        return opening == null ? null : new Way(opening, dispenser.clientWasHeading[i], 1 - smooth(age / OUT_TICKS));
    }

    private static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Whether this player's held nozzle is drawn in the world (in its opening, or on its way in or out). */
    public static boolean inWorld(Player player, float partialTick) {
        Held held = held(player);
        return held != null && way(held.dispenser(), held.nozzle(), partialTick) != null;
    }

    /** The local player's nozzle while it is in an opening (not on its way out), or null. */
    @Nullable
    private static Held localIn() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || player.isSpectator()) return null;
        Held held = held(player);
        return held != null && held.dispenser().inserted(held.nozzle()) != null ? held : null;
    }

    /** True while the local player's nozzle is in an opening (the world hints say "pull out"). */
    public static boolean active() {
        return localIn() != null;
    }

    /** The opening the local player's nozzle is in, or null. */
    @Nullable
    public static Vec3 localOpening() {
        Held held = localIn();
        if (held == null) return null;
        BlockPos in = held.dispenser().inserted(held.nozzle());
        return in == null ? null : drawnOpening(held.dispenser().getLevel(), in);
    }

    // ---- the local holder: one press, one toggle ----

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean in = localIn() != null;
        if (in != wasIn) {
            wasIn = in;
            holdUntilRelease = true;
        }
        if (!mc.options.keyUse.isDown()) holdUntilRelease = false;
    }

    /** Holding the use button repeats it every 4 ticks: with the nozzle that would take it straight back out, or spray. */
    @SubscribeEvent
    public static void click(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || !holdUntilRelease || mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof FuelNozzleItem)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ---- the local holder: crouched, still, looking at the opening ----

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer) || localIn() == null) return;
        var input = event.getInput();
        input.shiftKeyDown = true;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = false;
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        Vec3 opening = localOpening();
        if (opening == null) return;
        // a little over the opening: the nozzle body above it shows in the lower half of the view
        Vec3 d = opening.add(0, 0.12, 0).subtract(player.getEyePosition(event.renderTickTime));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        float blend = (float) (1 - Math.exp(-TURN_RATE * dt));
        float y = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * blend, p = player.getXRot() + (pitch - player.getXRot()) * blend;
        player.setYRot(y);
        player.setXRot(p);
        player.yRotO = y;
        player.xRotO = p;
        player.yHeadRot = player.yHeadRotO = y;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideHands(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && inWorld(mc.player, event.getPartialTick())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        if (localIn() != null) event.setCanceled(true);
    }

    // ---- the nozzle's frame: model points (item frame, block units) to the world ----

    /** origin: the world point of the model's (0, 0, 0); x, y, z: its axes (unit); s: its scale. */
    public record Frame(Vec3 o, Vec3 x, Vec3 y, Vec3 z, double s) {
        public Vec3 point(Vec3 m) {
            return o.add(x.scale(m.x * s)).add(y.scale(m.y * s)).add(z.scale(m.z * s));
        }
    }

    /** The nozzle in its opening, at true size. */
    public static Frame inserted(Vec3 opening, float heading) {
        Vec3[] f = NozzleFill.frame(opening, heading);
        return new Frame(f[0], f[1], f[2], f[3], 1);
    }

    /** From a to b (rigid, the scale eased too): position and scale straight, the axes eased and made square again. */
    public static Frame lerp(Frame a, Frame b, double t) {
        if (t >= 1) return b;
        if (t <= 0) return a;
        Vec3 x = a.x().lerp(b.x(), t).normalize(), yRaw = a.y().lerp(b.y(), t);
        Vec3 y = yRaw.subtract(x.scale(yRaw.dot(x))).normalize(), z = x.cross(y);
        return new Frame(a.o().lerp(b.o(), t), x, y, z, Mth.lerp(t, a.s(), b.s()));
    }

    // ---- first person: the arm on the grip (client/FuelDispenserRenderer calls this with its pose at the dispenser) ----

    /** The local holder's right arm reaching the grip, eased in with k (the nozzle's way into the opening). */
    public static void renderArm(Vec3 grip, double k, BlockPos origin, Level level, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (k <= 0 || !mc.options.getCameraType().isFirstPerson()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition(), fwd = new Vec3(camera.getLookVector()), up = new Vec3(camera.getUpVector()), right = new Vec3(camera.getLeftVector()).scale(-1);
        Vec3 under = cam.add(fwd.scale(0.30)).add(up.scale(-0.62)).add(right.scale(0.26));   // out of view, low right
        Vec3 hand = under.add(grip.subtract(under).scale(k));
        // a virtual shoulder low right under the view (client/PortableGeneratorPull#renderArm): the 0.585 m arm leaves the
        // screen before it ends
        Vec3 shoulder = cam.add(right.scale(0.30)).add(up.scale(-0.60)).add(fwd.scale(-0.05));
        Vec3 y = hand.subtract(shoulder);
        if (y.lengthSqr() < 1e-6) return;
        y = y.normalize();
        Vec3 z = hand.subtract(cam);
        z = z.subtract(y.scale(z.dot(y)));
        if (z.lengthSqr() < 1e-6) z = new Vec3(0, -1, 0).subtract(y.scale(-y.y));
        z = z.normalize();
        Vec3 x = y.cross(z), cap = hand.add(y.scale(0.035));
        pose.pushPose();
        pose.translate(cap.x - origin.getX(), cap.y - origin.getY(), cap.z - origin.getZ());
        Matrix3f basis = new Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
        pose.mulPose(new Quaternionf().setFromNormalized(basis));
        NativePlayerArmRenderer.render(pose, true, buffers, LevelRenderer.getLightColor(level, BlockPos.containing(hand)), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // ---- third person ----

    /** The item layer hides a held nozzle while it is drawn in the world (mixin client.ItemInHandLayerNozzleMixin). */
    public static boolean hidesHeldNozzle(LivingEntity entity) {
        return entity instanceof Player player && inWorld(player, Minecraft.getInstance().getFrameTime());
    }

    /** The holder's right arm toward the nozzle's grip in its opening, eased with the nozzle's way in and out. */
    public static void poseArm(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof Player player)) return;
        Held held = held(player);
        if (held == null) return;
        float partial = Mth.clamp(ageInTicks - player.tickCount, 0, 1);
        Way way = way(held.dispenser(), held.nozzle(), partial);
        if (way == null) return;
        Vec3 grip = inserted(way.opening(), way.heading()).point(NozzleFill.GRIP);
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(body), 0, Mth.cos(body)), rightSide = new Vec3(-Mth.cos(body), 0, -Mth.sin(body));
        Vec3 shoulder = player.getPosition(partial).add(0, player.isCrouching() ? 1.05 : 1.29, 0).add(rightSide.scale(0.29));
        Vec3 d = grip.subtract(shoulder);
        if (d.lengthSqr() < 1e-6) return;
        d = d.normalize();
        // model space: right = -x, down = +y, forward = -z; the arm hangs along +y, turned X then Y (ModelPart's ZYX order)
        double tx = -d.dot(rightSide), ty = -d.y, tz = -d.dot(forward);
        float s = (float) Math.sqrt(Math.max(0, 1 - ty * ty));
        float xRot = (float) -Math.acos(Mth.clamp(ty, -1, 1)), yRot = s < 1e-4F ? 0 : (float) Math.atan2(-tx / s, -tz / s);
        float k = (float) way.k();
        model.rightArm.xRot = Mth.lerp(k, model.rightArm.xRot, xRot);
        model.rightArm.yRot = Mth.lerp(k, model.rightArm.yRot, yRot);
        model.rightArm.zRot = Mth.lerp(k, model.rightArm.zRot, 0);
    }
}
