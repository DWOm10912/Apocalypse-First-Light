package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the C2 harness (dev only): on the first title screen it creates (mode=create) or opens (mode=open) the
 * test world named by -Dafl.c2, without any user input; {@link #quit} saves and closes the client when the probe is
 * done. Screens that would wait for a click (the experimental-settings warning of a data-pack world type) are answered
 * by the vanilla flow's own defaults: createFreshLevel does not ask, loadLevel is called with the check off.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class C2HarnessClient {
    private C2HarnessClient() {}

    private static boolean started;

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (started || !C2Harness.active() || !(event.getScreen() instanceof TitleScreen screen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        String mode = C2Harness.arg("mode", "create"), world = C2Harness.arg("world", "c2_test");
        mc.tell(() -> {
            if (mode.equals("open")) {
                C2Harness.log("opening world " + world);
                mc.createWorldOpenFlows().loadLevel(screen, world);
                return;
            }
            long seed = Long.parseLong(C2Harness.arg("seed", "0"));
            ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
                    new ResourceLocation(C2Harness.arg("preset", "minecraft:normal")));
            C2Harness.log("creating world " + world + " preset " + preset.location() + " seed " + seed);
            LevelSettings settings = new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(seed, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions());
        });
    }

    /** Saves the world (the integrated server stops and writes everything) and closes the client. */
    static void quit() {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            C2Harness.log("probe done: saving and quitting");
            if (mc.level != null) mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.literal("AFL C2 probe: saving")));
            mc.stop();
        });
    }
}
