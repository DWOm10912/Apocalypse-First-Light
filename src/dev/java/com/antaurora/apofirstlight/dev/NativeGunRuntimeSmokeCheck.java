package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.ServicePistolActions;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.network.packet.StopTriggeredSingletonAnimPacket;

/** DEV-only, runs once after resource reload; never renders, clicks or captures anything. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunRuntimeSmokeCheck {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || event.phase != TickEvent.Phase.END || mc.getOverlay() != null || mc.screen == null) return;
        checked = true;
        try {
            check(AflItems.SERVICE_PISTOL.get() instanceof ServicePistolItem, "item registry");
            var geo = GeckoLibCache.getBakedModels().get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/service_pistol.geo.json"));
            check(geo != null, "GeckoLib baked model");
            check(geo.getBone("right_arm_reference").isEmpty() && geo.getBone("left_arm_reference").isEmpty(), "reference exclusion");
            for (String anchor : new String[]{"right_hand_anchor", "left_hand_anchor", "muzzle_anchor", "ejection_anchor", "sight_anchor"})
                check(geo.getBone(anchor).isPresent(), "anchor " + anchor);
            var animations = GeckoLibCache.getBakedAnimations().get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "animations/service_pistol.animation.json"));
            check(animations != null, "GeckoLib baked animations");
            NativeHandContractChecks.verify(mc, (ServicePistolItem) AflItems.SERVICE_PISTOL.get());
            check(EasingType.fromString("afl_hold") != EasingType.LINEAR, "registered hold easing");
            check(EasingType.fromString("afl_hold").buildTransformer(null).apply(0.99) == 0, "hold until endpoint");
            for (boolean reload : new boolean[]{false, true}) {
                String name = ServicePistolActions.animationName(reload);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new StopTriggeredSingletonAnimPacket("afl-smoke", 123L, ServicePistolItem.CONTROLLER, name).encode(buffer);
                    check(buffer.readUtf().equals("afl-smoke"), "stop packet identity");
                    check(buffer.readVarLong() == 123L, "stop packet instance");
                    check(buffer.readUtf().equals(ServicePistolItem.CONTROLLER), "stop packet controller");
                    check(buffer.readUtf().equals(name) && !buffer.isReadable(), "non-null stop packet action");
                } finally { buffer.release(); }
            }
            ApocalypseFirstLight.LOGGER.info("[AFL NATIVE GUN SMOKE] PASS: baked model/animations, anchors, source-only arms, hold easing and fire/reload stop packet encoding");
        } catch (Throwable failure) {
            ApocalypseFirstLight.LOGGER.error("[AFL NATIVE GUN SMOKE] FAIL", failure);
        }
    }

    private static void check(boolean pass, String label) {
        if (!pass) throw new IllegalStateException(label);
    }

}
