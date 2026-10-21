package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Random;

/**
 * Health V1a: how an infected wound feels on the screen (docs/gameplay/survival_health_v1a.md 6; V2 from the user's
 * choice of offline previews 2026-10-20, design/health/wound_screen_v2/: "现实中感染流脓不会搞的满屏都是噪点", no flashing or
 * throbbing edges, a scratch shows nothing). The server's Wounds#visual tells the stage: a local infection 0.5,
 * suppuration 1 (fading over a recovery), eased here (2 s in, 3 s out); the client's setting (AFL settings, "wound
 * effect": off / low / normal) scales how strong everything is, not when it comes.
 * <ul>
 * <li><b>Blur spells</b> (infection and up): now and then the view goes out of focus (in 1.2 s, held, out 1.5 s, 4-6 s in
 * all, the focus drifting a little while it lasts) and back; clear in between. With a fever they come more often and go
 * further, and the view sways slowly while they last (dizzy; {@link #cameraShake}, view only).</li>
 * <li><b>Fever</b> (suppuration), all the time: colours flat and a little warm, bright parts spreading into a soft haze
 * (light sensitivity), a slight softness; and now and then a chills burst, the view shivering for about 1.4 s.</li>
 * </ul>
 * Drawn in the GUI pass under the HUD (which stays sharp): the frame is copied and halved twice into quarter and eighth
 * size copies, and the program apocalypse_firstlight:afl_wound_vision draws it back over itself. Nothing is drawn while
 * there is nothing to show (an infection between its spells). If the program or a copy fails, the screen part is dropped
 * for the session; the sway and the chills go on.
 */
public final class WoundScreenEffect {
    private static final double EASE_IN = 2.0, EASE_OUT = 3.0;
    /** Blur disc radius (fraction of the screen width) at a spell's peak: infection, fever; and a fever's steady softness. */
    private static final float SPELL_BLUR = 0.0071F, FEVER_SPELL_BLUR = 0.013F, FEVER_BLUR = 0.0033F;
    /** Fever: the bright parts' spread and its radius (fraction of the screen width); desaturation, wash, warm shift. */
    private static final float BLOOM = 0.55F, BLOOM_RADIUS = 0.028F, DESATURATE = 0.25F, WASH = 0.06F, WARM = 0.015F;
    /** A blur spell: ease in and out (s); its length (s); the gap after it (s) for an infection and with a fever. */
    private static final double SPELL_IN = 1.2, SPELL_OUT = 1.5, SPELL_MIN = 4, SPELL_MAX = 6,
            GAP_MIN = 20, GAP_MAX = 45, FEVER_GAP_MIN = 12, FEVER_GAP_MAX = 28;
    /** Dizzy sway at a fever spell's peak (deg): roll, yaw, pitch. */
    private static final double SWAY_ROLL = 0.9, SWAY_YAW = 0.6, SWAY_PITCH = 0.25;
    /** Chills: every 25-50 s, 1.2-1.6 s long; the shiver's peak angles (deg). */
    private static final double CHILLS_GAP_MIN = 25, CHILLS_GAP_MAX = 50, CHILLS_ROLL = 0.25, CHILLS_YAW = 0.25, CHILLS_PITCH = 0.2;
    private static final Random RANDOM = new Random();

    /** The eased stage (Wounds#visual: 0.5 infection, 1 suppuration). */
    private static double shown;
    private static long lastNanos;
    /** The current or next blur spell and chills burst: start and length (seconds, System.nanoTime clock); -1 none planned. */
    private static double spellStart = -1, spellLength, chillsStart = -1, chillsLength;
    private static volatile ShaderInstance program;
    private static boolean failed;
    private static TextureTarget frameCopy, half, quarter, eighth;

    private WoundScreenEffect() {
    }

    /** Infection and up, 0..1 (Wounds#visual 0 → 0.5). */
    private static double infection() {
        return Math.min(1, shown / 0.5);
    }

    /** Fever, 0..1 (Wounds#visual 0.5 → 1). */
    private static double fever() {
        double f = Math.max(0, Math.min(1, (shown - 0.5) / 0.5));
        return f * f * (3 - 2 * f);
    }

    private static double ramp(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** 0..1: the blur spell's envelope now, 0 between spells; plans the next one (sooner with a fever). */
    private static double spell(double seconds) {
        if (shown < 0.004) {
            spellStart = -1;
            return 0;
        }
        double f = fever();
        if (spellStart < 0 || seconds > spellStart + spellLength) {
            double from = spellStart < 0 ? seconds - 10 : spellStart + spellLength;   // the first: 10-35 s in (a fever: 2-18 s)
            double min = GAP_MIN + (FEVER_GAP_MIN - GAP_MIN) * f, max = GAP_MAX + (FEVER_GAP_MAX - GAP_MAX) * f;
            spellStart = Math.max(seconds + 2, from + min + RANDOM.nextDouble() * (max - min));
            spellLength = SPELL_MIN + RANDOM.nextDouble() * (SPELL_MAX - SPELL_MIN);
        }
        double t = seconds - spellStart;
        return ramp(0, SPELL_IN, t) * (1 - ramp(spellLength - SPELL_OUT, spellLength, t));
    }

    /** 0..1: the chills burst's envelope now (fever only); plans the next one. */
    private static double chills(double seconds) {
        if (fever() < 0.05) {
            chillsStart = -1;
            return 0;
        }
        if (chillsStart < 0 || seconds > chillsStart + chillsLength) {
            chillsStart = seconds + CHILLS_GAP_MIN + RANDOM.nextDouble() * (CHILLS_GAP_MAX - CHILLS_GAP_MIN);
            chillsLength = 1.2 + RANDOM.nextDouble() * 0.4;
        }
        double t = seconds - chillsStart;
        return ramp(0, 0.15, t) * (1 - ramp(chillsLength - 0.3, chillsLength, t));
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        boolean active = mc.player != null && !mc.player.isSpectator() && mc.player.isAlive();
        double target = active ? ClientHealth.visual() : 0;
        double tau = target > shown ? EASE_IN : EASE_OUT;
        shown += (target - shown) * (1 - Math.exp(-dt / tau));
        double seconds = now / 1e9, spell = spell(seconds);
        chills(seconds);   // keeps its plan running for cameraShake
        float strength = (float) FirstPersonBodyConfig.woundEffect();
        if (shown < 0.004 || strength <= 0 || failed || program == null) return;
        float f = (float) fever(), i = (float) infection();
        float drift = (float) (1 + 0.2 * Math.sin(seconds * 2.3));
        float blur = strength * (f * FEVER_BLUR + (float) spell * drift * i * (SPELL_BLUR + (FEVER_SPELL_BLUR - SPELL_BLUR) * f));
        if (blur < 1e-4F && f < 0.01F) return;
        graphics.flush();
        draw(graphics, mc, blur, strength * f);
    };

    /** The frame copied, halved twice, and drawn back over itself through afl_wound_vision. */
    private static void draw(GuiGraphics graphics, Minecraft mc, float blur, float fever) {
        try {
            RenderTarget main = mc.getMainRenderTarget();
            int w = main.width, h = main.height;
            frameCopy = target(frameCopy, w, h);
            half = target(half, Math.max(1, w / 2), Math.max(1, h / 2));
            quarter = target(quarter, Math.max(1, w / 4), Math.max(1, h / 4));
            eighth = target(eighth, Math.max(1, w / 8), Math.max(1, h / 8));
            blit(main, frameCopy, GL11.GL_NEAREST);
            blit(main, half, GL11.GL_LINEAR);
            blit(half, quarter, GL11.GL_LINEAR);
            blit(quarter, eighth, GL11.GL_LINEAR);
            main.bindWrite(false);

            ShaderInstance sh = program;
            sh.safeGetUniform("BlurRadius").set(blur);
            sh.safeGetUniform("SharpBelow").set(3F * 4F / w);   // three quarter-copy texels
            sh.safeGetUniform("Aspect").set(w / (float) h);
            sh.safeGetUniform("Bloom").set(BLOOM * fever);
            sh.safeGetUniform("BloomRadius").set(BLOOM_RADIUS);
            sh.safeGetUniform("Grade").set(DESATURATE * fever, WASH * fever, WARM * fever);
            var window = mc.getWindow();
            float scale = (float) window.getGuiScale(), pw = window.getWidth(), ph = window.getHeight();
            graphics.pose().pushPose();
            graphics.pose().scale(1 / scale, 1 / scale, 1);
            var matrix = graphics.pose().last().pose();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> sh);
            RenderSystem.setShaderTexture(0, frameCopy.getColorTextureId());
            RenderSystem.setShaderTexture(1, quarter.getColorTextureId());
            RenderSystem.setShaderTexture(2, eighth.getColorTextureId());
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(matrix, 0, ph, 0).uv(0, 0).endVertex();
            buffer.vertex(matrix, pw, ph, 0).uv(1, 0).endVertex();
            buffer.vertex(matrix, pw, 0, 0).uv(1, 1).endVertex();
            buffer.vertex(matrix, 0, 0, 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(buffer.end());
            graphics.pose().popPose();
            RenderSystem.setShaderTexture(1, 0);
            RenderSystem.setShaderTexture(2, 0);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } catch (RuntimeException ex) {
            failed = true;
            ApocalypseFirstLight.LOGGER.warn("[AFL HEALTH] Wound vision disabled: {}", ex.toString());
        }
    }

    private static TextureTarget target(TextureTarget t, int w, int h) {
        if (t != null && t.width == w && t.height == h) return t;
        if (t != null) t.destroyBuffers();
        t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    private static void blit(RenderTarget from, RenderTarget to, int filter) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, GL11.GL_COLOR_BUFFER_BIT, filter);
    }

    /**
     * The first-person view's dizzy sway in a fever's blur spells and its chills shiver (a camera director shake source,
     * client/camera/CameraSources). View only, aim untouched.
     */
    public static void cameraShake(Camera camera, float partial, com.antaurora.apofirstlight.client.camera.CameraShake out) {
        var mc = Minecraft.getInstance();
        if (shown < 0.004 || mc.player == null || camera.getEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()) return;
        double s = System.nanoTime() / 1e9, strength = FirstPersonBodyConfig.woundEffect(), f = fever() * strength;
        if (f <= 0) return;
        double sway = f * spell(s) * Math.sin((s - spellStart) * 1.3), c = f * chills(s);
        double shiver = Math.sin(s * 61) * 0.6 + Math.sin(s * 43 + 1.3) * 0.4;
        out.add(SWAY_PITCH * f * spell(s) * Math.sin((s - spellStart) * 2.1) + CHILLS_PITCH * c * Math.sin(s * 53),
                SWAY_YAW * sway + CHILLS_YAW * c * shiver,
                SWAY_ROLL * sway + CHILLS_ROLL * c * Math.sin(s * 47 + 0.4));
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            // under the HUD and the crosshair, with the temperature edges
            event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(), "wound_screen", OVERLAY);
        }

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_wound_vision"),
                        DefaultVertexFormat.POSITION_TEX), loaded -> program = loaded);
            } catch (Exception e) {
                program = null;
                ApocalypseFirstLight.LOGGER.warn("[AFL HEALTH] Wound vision program not loaded: {}", e.toString());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Session {
        private Session() {
        }

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientHealth.clear();
            shown = 0;
            spellStart = -1;
            chillsStart = -1;
        }
    }
}
