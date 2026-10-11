package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
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
 * {@code /afl dev terrain_smooth}: development tools for Terrain Smooth V1 (docs/worldgen/terrain_smooth_v1.md; the
 * feature itself is formal code and on by its world config). Development runs only, op level 2, a player.
 * <pre>
 *   on | off                        this server session on / off (all players follow; the config is not changed)
 *   collision on | off              slopes collide and are selected (default) / visual only
 *   status                          state, counts and cost on both sides, natural surfaces around you
 *   probe                           write your last 40 ticks and the slopes under your feet (client and server) to the log
 *   refresh                         make all natural surfaces and slopes again, re-mesh
 *   ao on | off                     smooth lighting on sloped tops (default off: flat; this client only)
 * </pre>
 */
public final class SmoothCommands {
    static final int STATUS = 0, PROBE = 1, REFRESH = 2, AO = 3;

    public record Msg(int action, boolean flag) {
        static void encode(Msg m, FriendlyByteBuf b) {
            b.writeByte(m.action);
            b.writeBoolean(m.flag);
        }

        static Msg decode(FriendlyByteBuf b) {
            return new Msg(b.readByte(), b.readBoolean());
        }

        static void handle(Msg m, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SmoothDevClient.handle(m)));
            c.setPacketHandled(true);
        }
    }

    private static SimpleChannel channel;

    private SmoothCommands() {}

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLCommonSetupEvent event) {
            if (FMLEnvironment.production) return;
            channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight", "dev_terrain_smooth"), () -> "3", "3"::equals, "3"::equals);
            channel.registerMessage(0, Msg.class, Msg::encode, Msg::decode, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
    public static final class Register {
        @SubscribeEvent
        public static void register(RegisterCommandsEvent event) {
            if (FMLEnvironment.production) return;
            LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("terrain_smooth")
                    .requires(s -> !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer);
            c.then(Commands.literal("on").executes(x -> state(x, true, TerrainSmooth.SERVER.shapes())));
            c.then(Commands.literal("off").executes(x -> state(x, false, TerrainSmooth.SERVER.shapes())));
            c.then(Commands.literal("collision").then(Commands.literal("on").executes(x -> state(x, TerrainSmooth.SERVER.enabled(), true)))
                    .then(Commands.literal("off").executes(x -> state(x, TerrainSmooth.SERVER.enabled(), false))));
            c.then(Commands.literal("status").executes(SmoothCommands::status));
            c.then(Commands.literal("probe").executes(x -> toPlayer(x, new Msg(PROBE, false))));
            c.then(Commands.literal("refresh").executes(SmoothCommands::refresh));
            c.then(Commands.literal("ao").then(Commands.literal("on").executes(x -> toPlayer(x, new Msg(AO, true))))
                    .then(Commands.literal("off").executes(x -> toPlayer(x, new Msg(AO, false)))));
            event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
        }
    }

    private static int state(CommandContext<CommandSourceStack> x, boolean enabled, boolean shapes) {
        TerrainSmooth.setServer(x.getSource().getServer(), enabled, shapes);
        x.getSource().sendSuccess(() -> Component.literal("Terrain smooth (this session): " + (enabled ? "on" : "off")
                + (enabled && !shapes ? ", visual only" : "") + " for every player; the world config is unchanged"), false);
        return 1;
    }

    private static int refresh(CommandContext<CommandSourceStack> x) {
        TerrainSmooth.SERVER.invalidate();
        return toPlayer(x, new Msg(REFRESH, false));
    }

    private static int status(CommandContext<CommandSourceStack> x) {
        x.getSource().sendSuccess(() -> Component.literal("Terrain smooth " + TerrainSmooth.SERVER.summary()), false);
        return toPlayer(x, new Msg(STATUS, false));
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
