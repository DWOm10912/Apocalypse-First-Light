package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.NativeGunActions;
import com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.NativeAnimatedWeaponRenderer;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
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
            check(AflItems.P9_01.get() instanceof ConfiguredNativeGunItem, "item registry");
            var item = (ConfiguredNativeGunItem) AflItems.P9_01.get();
            check(item.profile.id().equals("p9_01_v2_native"), "P9 V2 profile");
            check(net.minecraftforge.client.extensions.common.IClientItemExtensions.of(item.getDefaultInstance())
                    .getCustomRenderer() instanceof NativeAnimatedWeaponRenderer<?>, "generic renderer");
            var geo = GeckoLibCache.getBakedModels().get(item.profile.resource("geo", ".geo.json"));
            check(geo != null, "GeckoLib baked model");
            check(geo.getBone("right_arm_reference").isEmpty() && geo.getBone("left_arm_reference").isEmpty(), "reference exclusion");
            for (String anchor : new String[]{"handling", "gun_body", "slide", "magazine", "follower",
                    "right_hand_anchor", "left_hand_anchor", "muzzle_anchor", "ejection_anchor",
                    "sight_anchor", "maintenance_anchor", "camera"})
                check(geo.getBone(anchor).isPresent(), "anchor " + anchor);
            var animations = GeckoLibCache.getBakedAnimations().get(item.profile.resource("animations", ".animation.json"));
            check(animations != null, "GeckoLib baked animations");
            for (String clip : new String[]{"static_idle", "empty_idle", "shoot", "reload_tactical", "reload_empty",
                    "draw", "first_draw", "put_away", "inspect", "inspect_empty"})
                check(animations.getAnimation(clip) != null, "clip " + clip);
            check(EasingType.fromString("afl_hold") != EasingType.LINEAR, "registered hold easing");
            check(EasingType.fromString("afl_hold").buildTransformer(null).apply(0.99) == 0, "hold until endpoint");
            for (boolean reload : new boolean[]{false, true}) {
                String name = NativeGunActions.animationName(reload);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new StopTriggeredSingletonAnimPacket("afl-smoke", 123L, NativeGunItem.ACTION_CONTROLLER, name).encode(buffer);
                    check(buffer.readUtf().equals("afl-smoke"), "stop packet identity");
                    check(buffer.readVarLong() == 123L, "stop packet instance");
                    check(buffer.readUtf().equals(NativeGunItem.ACTION_CONTROLLER), "stop packet controller");
                    check(buffer.readUtf().equals(name) && !buffer.isReadable(), "non-null stop packet action");
                } finally { buffer.release(); }
            }
            ApocalypseFirstLight.LOGGER.info("[AFL NATIVE GUN SMOKE] PASS: baked P9 V2 model/animations, anchors, clips and stop packet encoding; visual QA pending");
        } catch (Throwable failure) {
            ApocalypseFirstLight.LOGGER.error("[AFL NATIVE GUN SMOKE] FAIL", failure);
        }
    }

    private static void check(boolean pass, String label) {
        if (!pass) throw new IllegalStateException(label);
    }

}
