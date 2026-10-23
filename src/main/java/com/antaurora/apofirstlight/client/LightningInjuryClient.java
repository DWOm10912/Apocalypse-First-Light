package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.lightning.LightningAfterimage;
import com.antaurora.apofirstlight.health.ClientLightningInjury;
import com.antaurora.apofirstlight.health.LightningInjuryConfig;
import com.antaurora.apofirstlight.health.LightningPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lightning injury V1 on the client (docs/gameplay/lightning_injury_v1_plan.md 3.5-3.6; health/LightningInjury decides):
 * <ul>
 * <li>the strike: the view goes white and comes back over about a second; knocked out, it goes from white to black;</li>
 * <li>fainted: the screen black (the HUD with it), one line saying so, the world's sounds far away, no moving, no hands, no
 * inventory; waking, the black lifts over {@link #WAKE_FADE} s and the view stays blurred for wake_blur s, easing off;</li>
 * <li>lying (fainted or paralysed legs): the crawl pose on the own player too (the server's is not sent to it), no jumping;
 * on weak legs no jumping either (sprinting: ClientLightningInjury#sprintBlocked);</li>
 * <li>the aftermath: now and then a headache, the view out of focus for a few seconds (health/WoundScreenEffect draws it).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningInjuryClient {
    /** The white of a strike: up at once, held, then out (s); knocked out, white then black over BLACK_IN. */
    private static final double WHITE_HOLD = 0.15, WHITE_OUT = 1.1, BLACK_IN = 0.8;
    /** Waking: the black lifting (s). */
    private static final double WAKE_FADE = 3;
    /** A headache spell: in, held, out (s). */
    private static final double ACHE_IN = 1.5, ACHE_HOLD = 3, ACHE_OUT = 2.5;
    /** Fainted, the world's sounds × this; eased over EAR_EASE s either way. */
    private static final double FAINT_HEARING = 0.12, EAR_EASE = 1.5;
    /** The cover's GUI depth: over every HUD layer (tooltips ~400), inside the GUI far plane (~20000). */
    private static final float COVER_Z = 3000;
    private static final RandomSource RANDOM = RandomSource.create();

    /** System clock (s) of the last strike's white, of waking up, of the current headache spell's start, and the next one. */
    private static double whiteAt = -1, wokeAt = -1, acheAt = -1, nextAche = -1;
    private static boolean knockedOut, wasFainted, posed;
    private static double faintEar = 1;
    private static long lastNanos;

    private LightningInjuryClient() {
    }

    private static double now() {
        return System.nanoTime() / 1e9;
    }

    public static void struck(LightningPackets.Struck packet) {
        whiteAt = now();
        knockedOut = packet.out();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) return;
        ClientLightningInjury.tick(player.isSleeping());
        boolean fainted = ClientLightningInjury.fainted();
        if (wasFainted && !fainted) wokeAt = now();
        wasFainted = fainted;
        if (ClientLightningInjury.down()) {
            if (player.getForcedPose() != Pose.SWIMMING) player.setForcedPose(Pose.SWIMMING);
            posed = true;
            if (player.isSprinting()) player.setSprinting(false);
            if (fainted && player.isUsingItem()) player.stopUsingItem();
        } else if (posed) {
            posed = false;
            if (player.getForcedPose() == Pose.SWIMMING && !com.antaurora.apofirstlight.health.ClientKnockdown.collapsed()) player.setForcedPose(null);   // still collapsed (Knockdown): crawls on
        }
        if (ClientLightningInjury.sprintBlocked() && player.isSprinting() && !player.isPassenger()) player.setSprinting(false);
        headache();
    }

    private static void headache() {
        double t = now(), strength = aftermathStrength();
        if (strength <= 0) {
            nextAche = -1;
            return;
        }
        double[] gap = LightningInjuryConfig.get().headacheGap;
        if (nextAche < 0) nextAche = t + Mth.lerp(RANDOM.nextDouble(), gap[0], gap[1]);
        if (t >= nextAche) {
            acheAt = t;
            nextAche = t + ACHE_IN + ACHE_HOLD + ACHE_OUT + Mth.lerp(RANDOM.nextDouble(), gap[0], gap[1]);
        }
    }

    private static double aftermathStrength() {
        float left = ClientLightningInjury.aftermathLeft();
        double fade = LightningInjuryConfig.get().aftermathFade;
        return left <= 0 ? 0 : fade <= 0 ? 1 : Math.min(1, left / fade);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** 0..1: the blur now (waking, or a headache spell), for health/WoundScreenEffect. */
    public static double blur() {
        double t = now(), b = 0;
        double wake = LightningInjuryConfig.get().wakeBlur;
        if (ClientLightningInjury.fainted()) return 0;
        if (wokeAt >= 0 && wake > 0) {
            double k = (t - wokeAt) / wake;
            if (k < 1) b = 1 - smooth(k);
            else wokeAt = -1;
        }
        if (acheAt >= 0) {
            double a = t - acheAt;
            double e = a < ACHE_IN ? smooth(a / ACHE_IN) : a < ACHE_IN + ACHE_HOLD ? 1 : 1 - smooth((a - ACHE_IN - ACHE_HOLD) / ACHE_OUT);
            if (a >= ACHE_IN + ACHE_HOLD + ACHE_OUT) acheAt = -1;
            else b = Math.max(b, e * 0.75 * Math.max(0.35, aftermathStrength()));
        }
        return b;
    }

    /** The world's sounds × this (client/TinnitusClient): the burst eardrum, and far away while fainted. */
    public static float hearing() {
        long nanos = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (nanos - lastNanos) / 1e9);
        lastNanos = nanos;
        double target = ClientLightningInjury.fainted() ? FAINT_HEARING : 1;
        faintEar += (target - faintEar) * (1 - Math.exp(-dt * 3 / EAR_EASE));
        return (float) (faintEar * ClientLightningInjury.eardrumHearing());
    }

    // ---- no moving or hands while fainted; no jumping lying down or on weak legs ----

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player.isPassenger()) return;
        var input = event.getInput();
        if (ClientLightningInjury.fainted()) {
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = input.shiftKeyDown = false;
        }
        if (ClientLightningInjury.down() || ClientLightningInjury.weakLegs()) input.jumping = false;
    }

    @SubscribeEvent
    public static void keys(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientLightningInjury.fainted()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Opening event) {
        if (ClientLightningInjury.fainted() && event.getNewScreen() instanceof InventoryScreen) event.setCanceled(true);
    }

    // ---- the white and the black ----

    /** 0..1 white and black over the view now. */
    private static double[] cover() {
        double t = now(), white = 0, black = 0;
        boolean striking = false;
        if (whiteAt >= 0) {
            double a = t - whiteAt;
            if (a > WHITE_HOLD + Math.max(WHITE_OUT, BLACK_IN)) {
                whiteAt = -1;
            } else {
                striking = knockedOut;
                white = a < WHITE_HOLD ? 1 : 1 - smooth((a - WHITE_HOLD) / WHITE_OUT);
                if (knockedOut) black = smooth((a - WHITE_HOLD) / BLACK_IN);   // out (or dead): the white goes black
            }
        }
        if (ClientLightningInjury.fainted()) {
            if (!striking) black = 1;
        } else if (wokeAt >= 0) {
            black = Math.max(black, 1 - smooth((t - wokeAt) / WAKE_FADE));
        }
        return new double[]{white, black};
    }

    static final IGuiOverlay COVER = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double[] c = cover();
        if (c[0] <= 0.002 && c[1] <= 0.002) return;
        // the GUI depth-tests: the chat (z 50), the hotbar's numbers and the badges' labels sit above z 0 and would show
        // through, the chat's see-through backing with the world under it; lift the cover over them (the depth is cleared
        // before screens, so the death screen and menus still draw on top)
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, COVER_Z);
        if (c[1] > 0.002) {
            graphics.fill(0, 0, width, height, (int) Math.round(Mth.clamp(c[1], 0, 1) * 255) << 24);
            if (ClientLightningInjury.fainted() && c[1] > 0.9) {
                Component line = Component.translatable("hud.apocalypse_firstlight.lightning.unconscious");
                int alpha = (int) Math.round(Mth.clamp((c[1] - 0.9) / 0.1, 0, 1) * 0x90);
                if (alpha > 4) graphics.drawCenteredString(mc.font, line, width / 2, height / 2 + 30, alpha << 24 | 0xB8B8B8);
            }
        }
        if (c[0] > 0.002) graphics.fill(0, 0, width, height, (int) Math.round(Mth.clamp(c[0], 0, 1) * 235) << 24 | 0xF4F6FF);
        graphics.pose().popPose();
    };

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLightningInjury.clear();
        whiteAt = wokeAt = acheAt = nextAche = -1;
        knockedOut = wasFainted = posed = false;
        faintEar = 1;
        lastNanos = 0;
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            // the after-image under the HUD, with the wound blur; the white and black over everything
            event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(), "lightning_afterimage", LightningAfterimage.OVERLAY);
            event.registerAboveAll("lightning_cover", COVER);
        }
    }
}
