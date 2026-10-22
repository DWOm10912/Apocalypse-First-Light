package com.antaurora.apofirstlight.weather;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;

/**
 * Regional weather V1 dev commands (op; docs/gameplay/regional_weather_v1_plan.md 10). {@code /afl weather} shows the
 * weather where the player stands and the cells near; {@code regime <name> [hours]} puts that regime on from now (full at
 * once, 24 game hours unless given); {@code storm} / {@code shower} puts a thunderstorm / shower over the player, near
 * its peak, moving with the wind; {@code reset} drops everything set by hand. What is set by hand is kept with the world
 * (WeatherData) and sent to every player.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WeatherCommands {
    private static final int HOUR = RegionalWeather.HOUR;

    private WeatherCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        String[] names = Arrays.stream(WeatherRegime.values()).map(g -> g.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("weather")
                .requires(s -> s.hasPermission(2))
                .executes(c -> status(c.getSource()))
                .then(Commands.literal("regime").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names, b))
                        .executes(c -> regime(c.getSource(), StringArgumentType.getString(c, "name"), 24))
                        .then(Commands.argument("hours", IntegerArgumentType.integer(1, 240))
                                .executes(c -> regime(c.getSource(), StringArgumentType.getString(c, "name"),
                                        IntegerArgumentType.getInteger(c, "hours"))))))
                .then(Commands.literal("storm").executes(c -> cell(c.getSource(), RegionalWeather.Kind.STORM)))
                .then(Commands.literal("shower").executes(c -> cell(c.getSource(), RegionalWeather.Kind.SHOWER)))
                .then(Commands.literal("reset").executes(c -> reset(c.getSource())))));
    }

    private static RegionalWeather model(CommandSourceStack source) {
        RegionalWeather model = AflWeather.model(source.getServer().overworld());
        if (model == null) source.sendFailure(Component.literal("no AFL weather in this world"));
        return model;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        RegionalWeather model = model(source);
        if (model == null) return 0;
        if (source.getLevel() != overworld) {
            source.sendFailure(Component.literal("the weather is the overworld's"));
            return 0;
        }
        Vec3 p = source.getPosition();
        long t = overworld.getDayTime();
        RegionalWeather.Sample s = new RegionalWeather.Sample();
        model.sample(p.x, p.z, t, s);
        RegionalWeather.Span span = model.span(t);
        double toward = Math.toDegrees(Math.atan2(s.windX, -s.windZ));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s (%.1f h left)  rain %.1f mm/h (level %.2f)  cloud %.2f  thunder %.2f",
                s.regime.name().toLowerCase(Locale.ROOT), (span.end() - t) / (double) HOUR, s.rain, AflWeather.level(s.rain), s.cloud, s.thunder)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "wind %.1f m/s toward %.0f deg (0 = north), gusts x%.1f  air %+.1f C",
                s.windSpeed(), (toward + 360) % 360, s.gust, s.temperature)), false);
        int n = 0;
        for (RegionalWeather.Cell c : model.active(t)) {
            double d = Math.hypot(c.x(t) - p.x, c.z(t) - p.z);
            if (d > 8000) continue;
            n++;
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s %.1f km away, radius %.1f km, peak %.0f mm/h%s",
                    c.kind().name().toLowerCase(Locale.ROOT), d / 1000, c.radiusNow(t) / 1000, c.peak(),
                    c.kind() == RegionalWeather.Kind.STORM ? String.format(Locale.ROOT, ", %.1f strikes/min", c.strikesPerMinute(t)) : "")), false);
        }
        if (n == 0) source.sendSuccess(() -> Component.literal("  no rain cells within 8 km"), false);
        return 1;
    }

    private static int regime(CommandSourceStack source, String name, int hours) {
        RegionalWeather model = model(source);
        if (model == null) return 0;
        WeatherRegime g;
        try {
            g = WeatherRegime.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("unknown regime " + name));
            return 0;
        }
        ServerLevel overworld = source.getServer().overworld();
        long t = overworld.getDayTime();
        // from half a blend back, so it is fully on now
        model.force(new RegionalWeather.Force(g, t - 3 * HOUR, t + (long) hours * HOUR));
        changed(overworld, model, t);
        source.sendSuccess(() -> Component.literal("regime " + name + " for " + hours + " h"), true);
        return 1;
    }

    private static int cell(CommandSourceStack source, RegionalWeather.Kind kind) throws CommandSyntaxException {
        RegionalWeather model = model(source);
        if (model == null) return 0;
        ServerLevel overworld = source.getServer().overworld();
        Vec3 p = source.getPlayerOrException().position();
        long t = overworld.getDayTime();
        RegionalWeather.Span span = model.span(t);
        boolean storm = kind == RegionalWeather.Kind.STORM;
        int life = (storm ? 6 : 3) * HOUR;
        // near its peak now, over the player
        long born = t - (long) (0.35 * life);
        double vx = span.steerX(), vz = span.steerZ();
        model.add(new RegionalWeather.Cell(model.handId(t, model.extra(t).size()), kind, p.x - vx * (t - born), p.z - vz * (t - born),
                vx, vz, born, life, storm ? 2000 : 1000, storm ? 40 : 8, storm ? 0.8 : 0));
        changed(overworld, model, t);
        source.sendSuccess(() -> Component.literal((storm ? "thunderstorm" : "shower") + " over you"), true);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        RegionalWeather model = model(source);
        if (model == null) return 0;
        ServerLevel overworld = source.getServer().overworld();
        model.reset();
        changed(overworld, model, overworld.getDayTime());
        source.sendSuccess(() -> Component.literal("weather back to the seed's"), true);
        return 1;
    }

    private static void changed(ServerLevel overworld, RegionalWeather model, long t) {
        WeatherData.get(overworld).keep(model, t);
        AflWeather.broadcast(overworld);
    }
}
