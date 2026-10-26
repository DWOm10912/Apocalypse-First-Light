package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Render optimization switches (docs/dev/render_performance_v1.md), so a change can be compared with the old path in the
 * same session, view and shader pack:
 * <ul>
 * <li>{@code shadow_cull}: off / on / debug. On: an AFL block entity is left out of the shader pack's shadow pass while it
 * is fully occluded from the shadow light ({@link AflShadowOcclusion}); debug also outlines every AFL block entity in the
 * main pass, red = left out of the shadow pass, green = kept.</li>
 * <li>{@code static_mesh}: on / off. On: the never-moving parts of animated meshes whose profile lists them are baked into
 * the chunk ({@code client/blockmesh/AflStaticMeshModel}); off: the old per-frame path draws everything. Switching rebuilds
 * every chunk.</li>
 * <li>{@code mirror}: off / auto / lit / albedo (Mirror Reflection V1, {@link MirrorReflection}). Auto: albedo with a shader
 * pack, lit without; off: every mirror shows its plain glass.</li>
 * </ul>
 * shadow_cull and static_mesh default to on, mirror to auto. The user sets them with the client command
 * {@code /afl_render <switch> <value>} (development builds only); tools/afl_minecraft_mcp/render_benchmark.mjs writes {@code run/afl_render_dev.properties} (same keys), which a
 * development build reads once a second.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflRenderDev {
    public enum ShadowCull { OFF, ON, DEBUG }
    public enum Mirror { OFF, AUTO, LIT, ALBEDO }

    private static volatile ShadowCull shadowCull = ShadowCull.ON;
    private static volatile boolean staticMesh = true;
    private static volatile Mirror mirror = Mirror.AUTO;
    private static long fileStamp = Long.MIN_VALUE;
    private static int tick;

    private AflRenderDev() {}

    public static ShadowCull shadowCull() { return shadowCull; }
    public static boolean staticMesh() { return staticMesh; }
    public static Mirror mirror() { return mirror; }
    /** For the profiler line: {@code shadow_cull=on,static_mesh=on}. */
    public static String label() {
        return "shadow_cull=" + shadowCull.name().toLowerCase(Locale.ROOT) + ",static_mesh=" + (staticMesh ? "on" : "off");
    }

    private static Path controlFile() {
        return FMLPaths.GAMEDIR.get().resolve("afl_render_dev.properties");
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (FMLEnvironment.production || event.phase != TickEvent.Phase.END || ++tick % 20 != 0) return;
        Path file = controlFile();
        try {
            long stamp = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE;
            if (stamp == fileStamp) return;
            long previous = fileStamp;
            fileStamp = stamp;
            if (stamp == Long.MIN_VALUE) {   // removed: back to the defaults
                if (previous != Long.MIN_VALUE) { mirror = Mirror.AUTO; apply("on", "on", "control file removed"); }
                return;
            }
            var properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file)) { properties.load(reader); }
            String m = properties.getProperty("mirror");
            if (m != null) mirror = Mirror.valueOf(m.trim().toUpperCase(Locale.ROOT));
            apply(properties.getProperty("shadow_cull"), properties.getProperty("static_mesh"), "control file");
        } catch (IOException | RuntimeException e) {
            ApocalypseFirstLight.LOGGER.warn("[AFL RENDER DEV] cannot read {}: {}", file, e.getMessage());
        }
    }

    private static void apply(String cull, String mesh, String source) {
        if (cull != null) shadowCull = ShadowCull.valueOf(cull.trim().toUpperCase(Locale.ROOT));
        if (mesh != null) {
            boolean next = Boolean.parseBoolean(mesh.trim()) || mesh.trim().equalsIgnoreCase("on");
            if (next != staticMesh) {
                staticMesh = next;
                // the chunk-baked parts appear / disappear only when the chunks are rebuilt
                var mc = Minecraft.getInstance();
                if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
            }
        }
        ApocalypseFirstLight.LOGGER.info("[AFL RENDER DEV] shadow_cull={} static_mesh={} mirror={} ({})", shadowCull, staticMesh ? "on" : "off", mirror, source);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("afl_render")
                .then(Commands.literal("shadow_cull").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value");
                    try { apply(value, null, "command"); }
                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal("off / on / debug")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("shadow_cull = " + shadowCull), false);
                    return 1;
                })))
                .then(Commands.literal("static_mesh").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    apply(null, StringArgumentType.getString(c, "value"), "command");
                    c.getSource().sendSuccess(() -> Component.literal("static_mesh = " + (staticMesh ? "on" : "off")), false);
                    return 1;
                })))
                .then(Commands.literal("mirror").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    try { mirror = Mirror.valueOf(StringArgumentType.getString(c, "value").toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal("off / auto / lit / albedo")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("mirror = " + mirror), false);
                    return 1;
                })))
                // Dynamic Lights V1 (docs/rendering/dynamic_lights_v1.md): off brings back the lamps' block light to compare
                .then(Commands.literal("dynamic_lights").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    if (value.equals("on") || value.equals("off")) com.antaurora.apofirstlight.client.light.DynamicLights.enabled = value.equals("on");
                    else if (!value.equals("status")) { c.getSource().sendFailure(Component.literal("on / off / status")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("dynamic_lights " + com.antaurora.apofirstlight.client.light.DynamicLights.status()), false);
                    return 1;
                })))
                // Physical water V1 package 2 (docs/gameplay/water_v1_design.md 16.6): the AFL water surface on or off
                .then(Commands.literal("water").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    if (value.equals("on") || value.equals("off")) com.antaurora.apofirstlight.client.liquid.ClientLiquid.enabled = value.equals("on");
                    else if (!value.equals("status")) { c.getSource().sendFailure(Component.literal("on / off / status")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("water " + com.antaurora.apofirstlight.client.liquid.ClientLiquid.status()), false);
                    return 1;
                })))
                // Package 3a (docs/gameplay/water_rain_v1_design.md 4.1): ground wet from natural rain on or off
                .then(Commands.literal("wet").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    if (value.equals("on") || value.equals("off")) com.antaurora.apofirstlight.client.liquid.ClientRainWet.enabled = value.equals("on");
                    else if (!value.equals("status")) { c.getSource().sendFailure(Component.literal("on / off / status")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("wet " + com.antaurora.apofirstlight.client.liquid.ClientRainWet.status()), false);
                    return 1;
                })))
                // Package F (docs/gameplay/water_falling_v1_design.md): falling water, drops, splashes on or off
                .then(Commands.literal("falls").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    if (value.equals("on") || value.equals("off")) com.antaurora.apofirstlight.client.liquid.ClientFalls.enabled = value.equals("on");
                    else if (!value.equals("status")) { c.getSource().sendFailure(Component.literal("on / off / status")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("falls " + com.antaurora.apofirstlight.client.liquid.ClientFalls.status()), false);
                    return 1;
                })))
                // AFL picture upgrade V1a (docs/rendering/vanilla_visual_upgrade_v1.md): post all|ao|smooth|grade on|off,
                // post strength|radius|contrast|saturation <number>, post status
                .then(Commands.literal("post")
                        .then(Commands.literal("status").executes(c -> {
                            c.getSource().sendSuccess(() -> Component.literal("post " + com.antaurora.apofirstlight.client.post.AflPost.status()), false);
                            return 1;
                        }))
                        .then(Commands.argument("part", StringArgumentType.word()).then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                            String part = StringArgumentType.getString(c, "part").toLowerCase(Locale.ROOT);
                            String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                            boolean on = value.equals("on");
                            boolean flag = on || value.equals("off");
                            float number;
                            try {
                                number = flag ? 0 : Float.parseFloat(value);
                            } catch (NumberFormatException e) {
                                c.getSource().sendFailure(Component.literal("on / off or a number"));
                                return 0;
                            }
                            switch (part) {
                                case "all" -> { if (!flag) return 0; com.antaurora.apofirstlight.client.post.AflPost.enabled = on; }
                                case "ao" -> { if (!flag) return 0; com.antaurora.apofirstlight.client.post.AflPost.ao = on; }
                                case "smooth" -> { if (!flag) return 0; com.antaurora.apofirstlight.client.post.AflPost.smooth = on; }
                                case "grade" -> { if (!flag) return 0; com.antaurora.apofirstlight.client.post.AflPost.grade = on; }
                                case "strength" -> com.antaurora.apofirstlight.client.post.AflPost.aoStrength = Math.max(0, Math.min(1.5F, number));
                                case "radius" -> com.antaurora.apofirstlight.client.post.AflPost.aoRadius = Math.max(0.1F, Math.min(3, number));
                                case "contrast" -> com.antaurora.apofirstlight.client.post.AflPost.contrast = Math.max(0, Math.min(1, number));
                                case "saturation" -> com.antaurora.apofirstlight.client.post.AflPost.saturation = Math.max(0, Math.min(2, number));
                                default -> {
                                    c.getSource().sendFailure(Component.literal("all / ao / smooth / grade / strength / radius / contrast / saturation"));
                                    return 0;
                                }
                            }
                            c.getSource().sendSuccess(() -> Component.literal("post " + com.antaurora.apofirstlight.client.post.AflPost.status()), false);
                            return 1;
                        }))))
                // Rain V1 (docs/rendering/rain_v1.md): off brings back vanilla rain to compare
                .then(Commands.literal("rain").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    if (value.equals("on") || value.equals("off")) com.antaurora.apofirstlight.client.rain.RainRenderer.enabled = value.equals("on");
                    else if (!value.equals("status")) { c.getSource().sendFailure(Component.literal("on / off / status")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("rain " + com.antaurora.apofirstlight.client.rain.RainRenderer.status()), false);
                    return 1;
                })))
                // T15 windshield water (docs/vehicles/t15_windshield_rain_design.md): off dries the glass (the wipers still run)
                .then(Commands.literal("windshield").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String value = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    if (value.equals("on") || value.equals("off")) com.antaurora.apofirstlight.client.rain.WindshieldRenderer.enabled = value.equals("on");
                    else if (!value.equals("status")) { c.getSource().sendFailure(Component.literal("on / off / status")); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("windshield " + com.antaurora.apofirstlight.client.rain.WindshieldRenderer.status()), false);
                    return 1;
                })))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("shadow_cull = " + shadowCull + ", static_mesh = " + (staticMesh ? "on" : "off") + ", mirror = " + mirror), false);
                    return 1;
                })));
    }
}
