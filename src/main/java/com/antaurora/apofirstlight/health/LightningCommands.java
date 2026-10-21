package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lightning injury V1 dev commands (op; docs/gameplay/lightning_injury_v1_plan.md 3.9). {@code /afl lightning} shows the
 * own states; {@code clear} ends them all. {@code hit direct|side|ground} shows a bolt (visual only: no fire, nobody else
 * hurt) where that way of being struck comes from and strikes the own player that way, without the death roll (add
 * {@code lethal} for it); its shelter is not asked. {@code near} shows a bolt 20 blocks ahead (the after-image). {@code strike}
 * sends a real bolt to the block looked at (64 blocks), sorted for everyone near it as a storm's would be.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LightningCommands {
    private LightningCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> hit = Commands.literal("hit");
        for (LightningInjury.Way way : LightningInjury.Way.values()) {
            String name = way.name().toLowerCase(java.util.Locale.ROOT);
            hit.then(Commands.literal(name).executes(c -> hit(c.getSource(), way, false))
                    .then(Commands.literal("lethal").executes(c -> hit(c.getSource(), way, true))));
        }
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("lightning")
                .requires(s -> s.hasPermission(2))
                .executes(c -> status(c.getSource()))
                .then(Commands.literal("clear").executes(c -> {
                    LightningInjury.clear(c.getSource().getPlayerOrException());
                    return status(c.getSource());
                }))
                .then(hit)
                .then(Commands.literal("near").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Vec3 ahead = p.getLookAngle().multiply(1, 0, 1).normalize().scale(20);
                    bolt(p.serverLevel(), p.position().add(ahead), true);
                    return 1;
                }))
                .then(Commands.literal("strike").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Vec3 eye = p.getEyePosition();
                    var looked = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(64)), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, p));
                    if (looked.getType() == HitResult.Type.MISS) {
                        c.getSource().sendFailure(Component.literal("no block within 64 blocks"));
                        return 0;
                    }
                    bolt(p.serverLevel(), looked.getLocation(), false);
                    return 1;
                }))));
    }

    private static void bolt(ServerLevel level, Vec3 at, boolean visual) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(visual);
        level.addFreshEntity(bolt);
    }

    private static int hit(CommandSourceStack source, LightningInjury.Way way, boolean lethal) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        Vec3 side = Vec3.directionFromRotation(0, p.getYRot() - 90);   // to the left
        Vec3 ahead = p.getLookAngle().multiply(1, 0, 1).normalize();
        double across = switch (way) { case DIRECT -> 0; case SIDE -> 1.5; case GROUND -> 5; };
        Vec3 at = switch (way) {
            case DIRECT -> p.position();
            case SIDE -> p.position().add(side.scale(across));
            case GROUND -> p.position().add(ahead.scale(across));
        };
        bolt(p.serverLevel(), at, true);
        LightningInjury.hit(p, way, across, at, p.isInWater(), lethal);
        return status(source);
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal(LightningInjury.describe(p)), false);
        return 1;
    }
}
