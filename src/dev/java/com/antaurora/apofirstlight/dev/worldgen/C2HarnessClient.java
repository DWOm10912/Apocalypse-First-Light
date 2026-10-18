package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.terrainsmooth.client.TerrainSmoothClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
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
 * test world named by -Dafl.c2, without any user input; mode=ui (federal default, 2026-10-14) makes it the way a player
 * does, through the vanilla "Create New World" screen (CreateWorldScreen#openFresh): it logs the type and overworld the
 * screen starts on, optionally switches the type like the button does (type=minecraft:flat), and presses Create; {@link #quit} saves and closes the client when the probe is
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
            if (mode.equals("ui")) {
                createThroughScreen(mc, screen, world);
                return;
            }
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
            // the survivor mode is picked like the Create World screen would, else the host's prompt pauses the run
            com.antaurora.apofirstlight.survivor.Survivors.setCreateScreenChoice(com.antaurora.apofirstlight.survivor.SurvivorMode.NORMAL);
            mc.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(seed, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions());
        });
    }

    private static String describe(WorldCreationUiState.WorldTypeEntry entry) {
        Holder<WorldPreset> p = entry.preset();
        return entry.describePreset().getString() + " [" + (p == null ? "custom" : p.unwrapKey().map(k -> k.location().toString()).orElse("?")) + "]";
    }

    private static void createThroughScreen(Minecraft mc, TitleScreen title, String world) {
        CreateWorldScreen.openFresh(mc, title);
        if (!(mc.screen instanceof CreateWorldScreen create)) {
            C2Harness.log("UI: the create-world screen did not open (" + mc.screen + ")");
            quit();
            return;
        }
        WorldCreationUiState ui = create.getUiState();
        C2Harness.log("UI default world type: " + describe(ui.getWorldType()));
        C2Harness.log("UI default overworld: " + C2Plan.overworld(ui.getSettings().selectedDimensions()));
        StringBuilder types = new StringBuilder();
        for (var e : ui.getNormalPresetList()) types.append(types.length() > 0 ? ", " : "").append(describe(e));
        C2Harness.log("UI world types (button order): " + types);
        String type = C2Harness.arg("type", "");
        if (!type.isEmpty()) {
            ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation(type));
            var entry = ui.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(key)).findFirst().orElse(null);
            if (entry == null) {
                C2Harness.log("UI: no world type " + type + " in the list");
                quit();
                return;
            }
            ui.setWorldType(entry);
            C2Harness.log("UI switched to " + describe(ui.getWorldType()) + ": overworld " + C2Plan.overworld(ui.getSettings().selectedDimensions()));
        }
        ui.setName(world);
        ui.setSeed(C2Harness.arg("seed", ""));
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        C2Harness.log("UI pressing Create: folder " + ui.getTargetFolder() + ", seed " + ui.getSeed());
        try {
            var onCreate = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            onCreate.invoke(create);
            if (mc.screen instanceof ConfirmScreen confirm) {
                C2Harness.log("UI: a confirmation screen came up: " + confirm.getTitle().getString() + "; answering yes");
                var callback = ConfirmScreen.class.getDeclaredField("callback");
                callback.setAccessible(true);
                ((it.unimi.dsi.fastutil.booleans.BooleanConsumer) callback.get(confirm)).accept(true);
            }
        } catch (ReflectiveOperationException e) {
            C2Harness.log("UI: could not press Create: " + e);
            quit();
        }
    }

    /** Saves the world (the integrated server stops and writes everything) and closes the client. */
    static void quit() {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            C2Harness.log("probe done: saving and quitting");
            C2Harness.log("road client: " + com.antaurora.apofirstlight.client.road.RoadClient.status());
            C2Harness.log("tunnel fixtures client: " + com.antaurora.apofirstlight.client.road.TunnelFixtureClient.status());
            // the bridge's hit meshes as the crosshair meets them (outline context), around the player (2026-10-15)
            if (mc.level != null && mc.player != null && C3RoadProbe.active())
                C2Harness.log("road hits (client, crosshair): " + C3HitCheck.run(mc.level, mc.player.getBlockX(), mc.player.getBlockZ(), 24,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE));
            C2Harness.log("terrain smooth client meshes: " + TerrainSmoothClient.candidates.sum() + " natural surface tops, "
                    + TerrainSmoothClient.sloped.sum() + " sloped, " + TerrainSmoothClient.quadBuilds.sum() + " slope meshes built, "
                    + TerrainSmoothClient.plantsMoved.sum() + " plants moved, " + TerrainSmoothClient.snowSkins.sum() + " snow skins");
            if (mc.level != null) mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.literal("AFL C2 probe: saving")));
            mc.stop();
        });
    }
}
