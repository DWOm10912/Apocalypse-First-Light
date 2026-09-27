package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/** Identifies the local player's weapon draw inside the third-person shadow traversal. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
final class NativeGunShadowSkip {
    private static final ThreadLocal<Deque<Player>> RENDERED_PLAYERS = ThreadLocal.withInitial(ArrayDeque::new);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerPre(RenderPlayerEvent.Pre event) {
        RENDERED_PLAYERS.get().push(event.getEntity());
    }

    @SubscribeEvent
    public static void playerPost(RenderPlayerEvent.Post event) {
        Deque<Player> players = RENDERED_PLAYERS.get();
        if (!players.isEmpty() && players.peek() == event.getEntity()) players.pop();
        else players.clear();
    }

    @SubscribeEvent
    public static void renderFrame(TickEvent.RenderTickEvent event) {
        // A canceled player render may have no Post event; never carry an owner into the next frame.
        if (event.phase == TickEvent.Phase.START || event.phase == TickEvent.Phase.END)
            RENDERED_PLAYERS.get().clear();
    }

    static boolean shouldSkip(ItemStack stack, ItemDisplayContext perspective, Item item) {
        if (!(item instanceof ConfiguredNativeGunItem) || stack == null || stack.isEmpty()
                || stack.getItem() != item || !thirdPersonHand(perspective)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) return false;
        Deque<Player> players = RENDERED_PLAYERS.get();
        return !players.isEmpty() && players.peek() == minecraft.player
                && AflShaderCompat.activeShadowPass();
    }

    private static boolean thirdPersonHand(ItemDisplayContext perspective) {
        return perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private NativeGunShadowSkip() {}
}
