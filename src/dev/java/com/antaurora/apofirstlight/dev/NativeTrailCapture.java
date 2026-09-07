package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.NativeBulletTrails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Collection;

/** Opt-in screenshots only; never shoots, moves the player, or changes the world. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeTrailCapture {
    private static int captures;
    private static Object last;
    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (!Boolean.getBoolean("afl.dev.captureTrail") || e.phase != TickEvent.Phase.END || captures >= 100) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        try {
            var field = NativeBulletTrails.class.getDeclaredField("ACTIVE");
            field.setAccessible(true);
            var active = (Collection<?>)field.get(null);
            if (active.isEmpty()) return;
            var trail = active.iterator().next();
            if (trail != last) {
                last = trail;
                ApocalypseFirstLight.LOGGER.info("[AFL TRAIL] perspective={} yaw={} {}",
                        mc.options.getCameraType(), mc.player.getYRot(), trail);
            }
            String name = String.format("afl-trail-%d-%03d-%s.png", System.currentTimeMillis(),
                    ++captures, mc.options.getCameraType());
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    message -> ApocalypseFirstLight.LOGGER.info("[AFL TRAIL CAPTURE] {}", message.getString()));
        } catch (ReflectiveOperationException ex) {
            captures = 100;
            ApocalypseFirstLight.LOGGER.error("Trail capture unavailable", ex);
        }
    }
}
