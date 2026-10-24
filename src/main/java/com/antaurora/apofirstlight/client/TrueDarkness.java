package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * True darkness V1 (docs/rendering/true_darkness_v1.md; user 2026-10-24: "a global real darkness; vanilla is not dark even
 * at light 0"): in the overworld light level 0 is black, with or without a shader pack's own lighting (Aureveil matches
 * it on its side). Light comes from torches, lamps and the held lights.
 * <ul>
 *   <li>Lightmap (LightTextureDarknessMixin): vanilla's grey floor is gone; the curve and the brightness setting are
 *   vanilla's.</li>
 *   <li>Fog (#fogColor): the fog took the sky's colour wherever the camera was, so a cave's far end and a great chamber
 *   faded to daylight grey. It is now darkened by the light where the eyes are (the sky light there, or the block light,
 *   whichever is brighter, on the lightmap's curve), eased over about half a second like eyes adjusting.</li>
 *   <li>Night (V2; user 2026-10-24, "even with moonlight the night is a bit bright"): the night's sky light follows the
 *   moon phase (full moon about a third of what vanilla showed at the brightest setting, new moon nearly black), is
 *   grey-blue, and the brightness setting no longer lifts it (#lerp).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrueDarkness {
    private TrueDarkness() {}

    /** how long the fog takes to follow the light at the eyes, s */
    static final double ADAPT = 0.5;

    private static double eye = 1;
    private static long last;

    /** V2: night sky light at a new moon, as a share of a full moon's (the phases between follow the lit fraction, eased) */
    static final float NEW_MOON = 0.04F;
    /** V2: how the lit fraction of the moon becomes light; above 1 the half moon is darker than half */
    static final float PHASE_CURVE = 1.5F;
    /** V2: the night's sky light colour, grey-blue (vanilla's was (0.48, 0.48, 1), lavender) */
    static final Vector3fc TINT = new Vector3f(0.62F, 0.70F, 0.90F);

    /** this lightmap update is the overworld's */
    private static boolean on;
    /** 0 by day .. 1 at night, from the sun's height as vanilla's sky darkening has it (without the weather) */
    private static float night;
    /** the sky light factor for tonight's moon phase */
    private static float moon;
    private static float skyFactor;
    private static boolean flash;
    /** the entry being worked out: its sky and block light on the curve */
    private static float sky, block;

    static boolean overworld() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension() == Level.OVERWORLD;
    }

    /** Before the lightmap's 256 entries: the moon phase and the sun's height for this frame. */
    public static void beginLightmap(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        on = level != null && level.dimension() == Level.OVERWORLD;
        if (!on) return;
        float sun = Mth.cos(level.getTimeOfDay(partialTick) * ((float) Math.PI * 2F));
        night = Mth.clamp(1.0F - (sun * 2.0F + 0.2F), 0.0F, 1.0F);
        moon = NEW_MOON + (1 - NEW_MOON) * (float) Math.pow(level.getMoonBrightness(), PHASE_CURVE);
        flash = level.getSkyFlashTime() > 0;
        skyFactor = flash ? 1.0F : level.getSkyDarken(1.0F) * 0.95F + 0.05F;
    }

    /** An entry's sky light: at night a share of it by the moon phase (lightning lights the sky whatever the moon). */
    public static float skyLight(float vanilla) {
        sky = on && !flash ? vanilla * Mth.lerp(night, 1.0F, moon) : vanilla;
        return sky;
    }

    public static float blockLight(float vanilla) {
        block = vanilla;
        return vanilla;
    }

    /**
     * Every Vector3f.lerp in the lightmap update. In the overworld:
     * <ul>
     *   <li>the two pulls toward grey (0.75, by 0.04) are left out (V1);</li>
     *   <li>the sky light's colour ((f, f, 1) toward white by 0.35) turns grey-blue at night (V2);</li>
     *   <li>the brightness setting (toward 1 − (1 − c)^4) lifts block light as before and sky light only by day, so the
     *   moon is as bright at any setting (V2).</li>
     * </ul>
     */
    public static Vector3f lerp(Vector3f self, Vector3fc to, float t) {
        if (!on) return self.lerp(to, t);
        if (t == 0.04F && to.x() == 0.75F && to.y() == 0.75F && to.z() == 0.75F) return self;
        if (t == 0.35F && to.x() == 1.0F && to.y() == 1.0F && to.z() == 1.0F)
            return self.lerp(to, t).lerp(TINT, night);
        if (to.x() == notGamma(self.x) && to.y() == notGamma(self.y) && to.z() == notGamma(self.z)) {
            float lit = block * 1.5F, moonlit = sky * skyFactor;
            float blockShare = lit + moonlit > 1e-6F ? lit / (lit + moonlit) : 1.0F;
            return self.lerp(to, t * (1.0F - night + night * blockShare));
        }
        return self.lerp(to, t);
    }

    /** LightTexture#notGamma, the brightness setting's target, exactly. */
    private static float notGamma(float c) {
        float f = 1.0F - c;
        return 1.0F - f * f * f * f;
    }

    /** The light where the eyes are, 0..1 on the lightmap's curve: the sky light (the fog colour has the time of day) or the block light. */
    static double lightAtEyes(ClientLevel level, BlockPos at) {
        int sky = Math.max(level.getBrightness(LightLayer.SKY, at), level.getBrightness(LightLayer.SKY, at.above()));
        int block = Math.max(level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.BLOCK, at.above()));
        return Math.max(LightTexture.getBrightness(level.dimensionType(), sky), LightTexture.getBrightness(level.dimensionType(), block));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !event.getCamera().isInitialized()) return;
        double target = lightAtEyes(level, event.getCamera().getBlockPosition());
        long now = System.nanoTime();
        double dt = last == 0 ? 1 : Math.min(1, (now - last) / 1e9);
        last = now;
        eye += (target - eye) * (1 - Math.exp(-dt / ADAPT));
        float k = (float) eye;
        event.setRed(event.getRed() * k);
        event.setGreen(event.getGreen() * k);
        event.setBlue(event.getBlue() * k);
    }
}
