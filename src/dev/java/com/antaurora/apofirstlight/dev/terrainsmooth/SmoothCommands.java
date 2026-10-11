package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /afl dev terrain_smooth} (docs/worldgen/terrain_smooth_visual_prototype_p0.md). Development runs only, op level
 * 2, a player in the overworld. Since P1 the server owns the settings (its own collision uses them) and sends them to every
 * player's client (drawing, its own collision and selection), also on login.
 * <pre>
 *   off | a | b                     vanilla / 45-degree single-block slopes / gentle two-block slopes
 *   here [size]                     test area centred where you stand (size 32..256, default 192)
 *   area &lt;x&gt; &lt;z&gt; [size]            test area centred on x z
 *   collision on | off              slopes collide and are selected (default on) / visual only
 *   status                          mode, area, counts, cost on both sides
 *   ao on | off                     smooth lighting on sloped tops (default off: flat; this client only)
 *   refresh                         drop the shape caches and re-mesh the area
 *   probe                           write the player's last 40 ticks and the slopes under the feet (client and server) to the log
 * </pre>
 */
public final class SmoothCommands {
    static final int CONFIG = 0, STATUS = 2, AO = 3, REFRESH = 4, PROBE = 5;
    public static final int DEFAULT_SIZE = 192, MIN_SIZE = 32, MAX_SIZE = 256;

    public record Msg(int action, int mode, int x, int z, int size, boolean flag, boolean report) {
        static void encode(Msg m, FriendlyByteBuf b) {
            b.writeByte(m.action);
            b.writeByte(m.mode);
            b.writeInt(m.x);
            b.writeInt(m.z);
            b.writeShort(m.size);
            b.writeBoolean(m.flag);
            b.writeBoolean(m.report);
        }

        static Msg decode(FriendlyByteBuf b) {
            return new Msg(b.readByte(), b.readByte(), b.readInt(), b.readInt(), b.readShort(), b.readBoolean(), b.readBoolean());
        }

        static void handle(Msg m, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SmoothPrototype.handle(m)));
            c.setPacketHandled(true);
        }
    }

    private static SimpleChannel channel;
    // the server's settings (SmoothWorld.SERVER holds the active copy)
    private static int mode = SmoothRules.MODE_OFF, centreX, centreZ, size = DEFAULT_SIZE;
    private static boolean areaSet, shapes = true;

    private SmoothCommands() {}

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLCommonSetupEvent event) {
            if (FMLEnvironment.production) return;
            channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight", "dev_terrain_smooth"), () -> "2", "2"::equals, "2"::equals);
            channel.registerMessage(0, Msg.class, Msg::encode, Msg::decode, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
    public static final class Register {
        @SubscribeEvent
        public static void register(RegisterCommandsEvent event) {
            if (FMLEnvironment.production) return;
            LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("terrain_smooth")
                    .requires(s -> !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer && s.getLevel().dimension() == Level.OVERWORLD);
            c.then(Commands.literal("off").executes(x -> setMode(x, SmoothRules.MODE_OFF)));
            c.then(Commands.literal("a").executes(x -> setMode(x, SmoothRules.MODE_A)));
            c.then(Commands.literal("b").executes(x -> setMode(x, SmoothRules.MODE_B)));
            c.then(Commands.literal("here").executes(x -> area(x, x.getSource().getPlayerOrException().getBlockX(), x.getSource().getPlayerOrException().getBlockZ(), DEFAULT_SIZE))
                    .then(Commands.argument("size", size()).executes(x -> area(x, x.getSource().getPlayerOrException().getBlockX(),
                            x.getSource().getPlayerOrException().getBlockZ(), IntegerArgumentType.getInteger(x, "size")))));
            c.then(Commands.literal("area").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                    .executes(x -> area(x, IntegerArgumentType.getInteger(x, "x"), IntegerArgumentType.getInteger(x, "z"), DEFAULT_SIZE))
                    .then(Commands.argument("size", size()).executes(x -> area(x, IntegerArgumentType.getInteger(x, "x"), IntegerArgumentType.getInteger(x, "z"),
                            IntegerArgumentType.getInteger(x, "size")))))));
            c.then(Commands.literal("collision").then(Commands.literal("on").executes(x -> collision(x, true)))
                    .then(Commands.literal("off").executes(x -> collision(x, false))));
            c.then(Commands.literal("status").executes(SmoothCommands::status));
            c.then(Commands.literal("ao").then(Commands.literal("on").executes(x -> toPlayer(x, new Msg(AO, 0, 0, 0, 0, true, true))))
                    .then(Commands.literal("off").executes(x -> toPlayer(x, new Msg(AO, 0, 0, 0, 0, false, true)))));
            c.then(Commands.literal("refresh").executes(SmoothCommands::refresh));
            c.then(Commands.literal("probe").executes(x -> toPlayer(x, new Msg(PROBE, 0, 0, 0, 0, false, true))));
            event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
        }

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent event) {
            if (!FMLEnvironment.production && event.getEntity() instanceof ServerPlayer p && channel != null && SmoothWorld.SERVER.active())
                channel.send(PacketDistributor.PLAYER.with(() -> p), config(false));
        }

        @SubscribeEvent
        public static void stopped(ServerStoppedEvent event) {
            mode = SmoothRules.MODE_OFF;
            areaSet = false;
            SmoothWorld.SERVER.set(SmoothRules.MODE_OFF, 0, 0, 0, true, Level.OVERWORLD);
        }
    }

    private static IntegerArgumentType size() { return IntegerArgumentType.integer(MIN_SIZE, MAX_SIZE); }

    private static Msg config(boolean report) { return new Msg(CONFIG, mode, centreX, centreZ, size, shapes, report); }

    /** Apply the settings on the server and send them to every client. */
    private static int apply(CommandContext<CommandSourceStack> x) {
        if (channel == null) { x.getSource().sendFailure(Component.literal("Terrain smooth: development runs only")); return 0; }
        SmoothWorld.SERVER.set(mode, centreX, centreZ, size, shapes, Level.OVERWORLD);
        var server = x.getSource().getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            channel.send(PacketDistributor.PLAYER.with(() -> p), config(p == x.getSource().getEntity()));
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> x, int next) {
        if (next != SmoothRules.MODE_OFF && !areaSet) {
            var p = x.getSource().getEntity();
            centreX = p.getBlockX();
            centreZ = p.getBlockZ();
            areaSet = true;
        }
        mode = next;
        return apply(x);
    }

    private static int area(CommandContext<CommandSourceStack> x, int cx, int cz, int s) {
        centreX = cx;
        centreZ = cz;
        size = s;
        areaSet = true;
        if (mode == SmoothRules.MODE_OFF) {
            x.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Terrain smooth: area centre %d %d size %d set; a or b to show it", cx, cz, s)), false);
            return 1;
        }
        return apply(x);
    }

    private static int collision(CommandContext<CommandSourceStack> x, boolean on) {
        shapes = on;
        return apply(x);
    }

    private static int refresh(CommandContext<CommandSourceStack> x) {
        SmoothWorld.SERVER.clear();
        return toPlayer(x, new Msg(REFRESH, 0, 0, 0, 0, false, true));
    }

    private static int status(CommandContext<CommandSourceStack> x) {
        var s = SmoothWorld.SERVER;
        x.getSource().sendSuccess(() -> Component.literal("Terrain smooth (server): " + describeServer(s) + "; " + s.summary()), false);
        return toPlayer(x, new Msg(STATUS, 0, 0, 0, 0, false, true));
    }

    /** The server's own description (SmoothPrototype is client-only). */
    private static String describeServer(SmoothWorld.Config c) {
        if (!c.active()) return "OFF";
        return String.format(java.util.Locale.ROOT, "mode %s, area x %d..%d z %d..%d, collision %s", c.mode == SmoothRules.MODE_A ? "A" : "B",
                c.minX, c.minX + c.size - 1, c.minZ, c.minZ + c.size - 1, c.shapes ? "on" : "off");
    }

    private static int toPlayer(CommandContext<CommandSourceStack> x, Msg msg) {
        if (channel == null || !(x.getSource().getEntity() instanceof ServerPlayer player)) {
            x.getSource().sendFailure(Component.literal("Terrain smooth: needs a player in a development run"));
            return 0;
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), msg);
        return 1;
    }
}
