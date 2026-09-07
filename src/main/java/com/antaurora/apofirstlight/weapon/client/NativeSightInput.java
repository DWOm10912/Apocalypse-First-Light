package com.antaurora.apofirstlight.weapon.client;

import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import com.antaurora.apofirstlight.weapon.NativeGunItem;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class NativeSightInput {
    private static final KeyMapping EXCHANGE=new KeyMapping("key.apocalypse_firstlight.sight_exchange",
            net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_V,"key.categories.apocalypse_firstlight");
    private static boolean held;
    @Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e){e.register(EXCHANGE);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();boolean click=false;while(EXCHANGE.consumeClick())click=true;
        if(click&&!held&&mc.player!=null&&mc.screen==null&&mc.isWindowActive()&&mc.player.isAlive()
                &&!mc.player.isSpectator()&&mc.player.getMainHandItem().getItem() instanceof NativeGunItem)
            com.antaurora.apofirstlight.network.AflNetwork.requestSightExchange(mc.player.getInventory().selected);
        held=EXCHANGE.isDown();
    }
}
