package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashSet;
import java.util.Set;

/** Explicit test-run screenshots only; never included in the release JAR. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunHudCapture {
    private static final Set<String> CAPTURED = new HashSet<>();
    private static String previous = "";
    private static int stable;
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("afl.dev.captureGunHud") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null) return;
        String state = "hidden";
        if (mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun) {
            state = NativeGunAmmo.read(mc.player.getMainHandItem(), gun.definition()) + "-"
                    + NativeGunAmmo.reserve(mc.player.getInventory(), gun.definition());
        }
        state += "-gui" + mc.options.guiScale().get();
        if (!state.equals(previous)) { previous = state; stable = 0; }
        if (++stable != 20 || !CAPTURED.add(state)) return;
        Screenshot.grab(mc.gameDirectory, "afl-v06-compact-hud-" + state + ".png", mc.getMainRenderTarget(),
                message -> ApocalypseFirstLight.LOGGER.info("[AFL V06 HUD CAPTURE] {}", message.getString()));
    }
}
