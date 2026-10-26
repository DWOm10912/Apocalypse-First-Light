package com.antaurora.apofirstlight.time;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 4): the server's world clock. Vanilla's ServerLevel#tickTime moves the
 * day time on by 1 a tick; WorldTimeServerLevelMixin hands that step to {@link #advance}, which moves it by this tick's whole
 * units of a {@link WorldClock} at the configured day length. Only the overworld keeps a time (the other dimensions' level
 * data ignore it). Game time, entities and the rest of the tick are untouched.
 * <ul>
 *   <li>Jumps: any other set of the overworld's time (sleep, /time set or add, another mod) clears the accumulator and posts
 *   {@link AflTimeJumpEvent} with its cause.</li>
 *   <li>Saved: the accumulator and its day length in {@link AflTimeData}; a changed length carries the gathered share over.</li>
 *   <li>Clients: the day length at login and on a change (WorldTimePacket); vanilla still sends the time once a second.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WorldTimeServer {
    private WorldTimeServer() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final Pattern TIME_COMMAND = Pattern.compile("(^|\\s)/?time\\s+(set|add)\\b");

    private static final WorldClock CLOCK = new WorldClock(WorldClock.DEFAULT_MINUTES);
    private static AflTimeData data;
    /** Set while the overworld's own tick step is setting the time, so that set is not taken for a jump. */
    private static boolean stepping;
    /** The cause of a set that comes before it (sleep's event, a /time command); cleared each tick. */
    private static AflTimeJumpEvent.Cause pending;
    private static boolean jumping;
    private static long jumpFrom;

    static int minutes() { return CLOCK.minutes(); }

    static long acc() { return CLOCK.acc(); }

    private static boolean overworld(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD;
    }

    /** ServerLevel#tickTime, daylight cycle on: vanilla's next time (time + 1) becomes the time plus this tick's whole units. */
    public static long advance(ServerLevel level, long next) {
        if (!overworld(level)) return next;
        pending = null;
        stepping = true;
        long units = CLOCK.step();
        if (data != null) data.store(CLOCK);
        return next - 1 + units;
    }

    /** ServerLevel#setDayTime, before. */
    public static void beforeSet(ServerLevel level, long time) {
        if (!overworld(level)) return;
        if (stepping) {
            stepping = false;
            return;
        }
        jumpFrom = level.getDayTime();
        jumping = jumpFrom != time;
    }

    /** ServerLevel#setDayTime, after. */
    public static void afterSet(ServerLevel level, long time) {
        if (!jumping || !overworld(level)) return;
        jumping = false;
        AflTimeJumpEvent.Cause cause = pending != null ? pending : AflTimeJumpEvent.Cause.OTHER;
        pending = null;
        CLOCK.reset();
        if (data != null) data.store(CLOCK);
        MinecraftForge.EVENT_BUS.post(new AflTimeJumpEvent(level, jumpFrom, level.getDayTime(), cause));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sleepFinished(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && overworld(level)) pending = AflTimeJumpEvent.Cause.SLEEP;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void command(CommandEvent event) {
        if (event.isCanceled()) return;
        if (TIME_COMMAND.matcher(event.getParseResults().getReader().getString()).find()) pending = AflTimeJumpEvent.Cause.COMMAND;
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        data = AflTimeData.get(overworld);
        int configured = AflTimeConfig.dayMinutes();
        CLOCK.set(data.minutes > 0 ? data.minutes : configured, data.acc);
        if (CLOCK.minutes() != configured) changeMinutes(configured);
        data.store(CLOCK);
        LOG.info("[AFL world time] a day is {} real minutes ({} ticks) in this world (config day_minutes)", CLOCK.minutes(), CLOCK.ticksPerDay());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        data = null;
        stepping = jumping = false;
        pending = null;
        CLOCK.set(WorldClock.DEFAULT_MINUTES, 0);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AflNetwork.sendWorldTime(player, new WorldTimePacket(CLOCK.minutes()));
    }

    /** A new day length while the server runs: carries the gathered share over, tells every client, posts the event. */
    static void changeMinutes(int minutes) {
        int old = CLOCK.minutes();
        CLOCK.changeMinutes(minutes);
        if (CLOCK.minutes() == old) return;
        if (data != null) data.store(CLOCK);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
            for (ServerPlayer p : server.getPlayerList().getPlayers()) AflNetwork.sendWorldTime(p, new WorldTimePacket(CLOCK.minutes()));
        LOG.info("[AFL world time] day length {} -> {} real minutes", old, CLOCK.minutes());
        MinecraftForge.EVENT_BUS.post(new AflTimeRateEvent(old, CLOCK.minutes()));
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("time")
                .requires(s -> s.hasPermission(2))
                .executes(c -> show(c.getSource()))
                .then(Commands.literal("set").then(Commands.argument("clock", StringArgumentType.greedyString())
                        .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "clock")))))));
    }

    private static int show(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        ClockTime clock = AflTime.clock(overworld, 0);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World time: date %d, %s (24 h, the date turns at midnight; time %d, vanilla day %d, of day %d). A day is %d real minutes (%d ticks); a game hour is %.0f s; clock share %d / %d",
                clock.date(), clock.hhmmss(), overworld.getDayTime(), AflTime.day(overworld), AflTime.timeOfDay(overworld), CLOCK.minutes(),
                CLOCK.ticksPerDay(), AflTime.realSecondsPerGameHour(), CLOCK.acc(), CLOCK.ticksPerDay())), false);
        return 1;
    }

    private static final Pattern HHMM = Pattern.compile("\\s*(\\d{1,2}):(\\d{2})\\s*");

    /** /afl time set HH:MM: that clock time on today's clock date (it may go back), every level like vanilla's /time set. */
    private static int set(CommandSourceStack source, String text) {
        var m = HHMM.matcher(text);
        int hour = m.matches() ? Integer.parseInt(m.group(1)) : -1, minute = m.matches() ? Integer.parseInt(m.group(2)) : -1;
        if (hour < 0 || hour > 23 || minute > 59) {
            source.sendFailure(Component.literal("Expected a 24-hour time HH:MM, 00:00 to 23:59"));
            return 0;
        }
        long time = ClockTime.dayTimeAt(AflTime.date(source.getServer().overworld()), hour, minute);
        for (ServerLevel level : source.getServer().getAllLevels()) level.setDayTime(time);
        ClockTime clock = ClockTime.at(time, 0);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "World time set to date %d, %s (time %d)",
                clock.date(), clock.hhmm(), time)), true);
        return 1;
    }

    /** The config file saved while the server runs (Forge watches serverconfig). */
    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigEvents {
        private ConfigEvents() {}

        @SubscribeEvent
        public static void reloaded(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() != AflTimeConfig.SPEC) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.execute(() -> changeMinutes(AflTimeConfig.dayMinutes()));
        }
    }
}
