package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.Commands;

/** DEV only; never included in the release JAR. A command is not a visual PASS. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID, value=Dist.CLIENT)
public final class NativeHandVisualGate {
    private NativeHandVisualGate() {}

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl_nativegun_debug")
                .then(Commands.literal("right").executes(c -> mode(false)))
                .then(Commands.literal("both").executes(c -> mode(true))));
    }

    private static int mode(boolean enableBoth) {
        NativePlayerArmRenderer.setHandFilter(right -> right || enableBoth);
        message("Explicit debug view: " + (enableBoth ? "both hands" : "right only")
                + ": this selects a test view, NOT visual acceptance.");
        return 1;
    }

    private static void message(String text) {
        if (Minecraft.getInstance().player!=null)
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text),false);
    }
}
