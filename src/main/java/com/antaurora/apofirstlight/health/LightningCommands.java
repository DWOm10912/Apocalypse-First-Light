package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.LightningAim;
import com.antaurora.apofirstlight.world.StrikePoint;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lightning injury V1 dev commands (op; docs/gameplay/lightning_injury_v1_plan.md 3.9). {@code /afl lightning} shows the
 * own states; {@code clear} ends them all. {@code hit direct|side|ground} shows a bolt (visual only: no fire, nobody else
 * hurt) where that way of being struck comes from and strikes the own player that way, without the death roll (add
 * {@code lethal} for it); its shelter is not asked. {@code near} shows a bolt 20 blocks ahead (the after-image). {@code strike}
 * sends a real bolt to the column looked at (64 blocks): its highest block, a rod's tip, a tree's crown, as a storm's would
 * strike it there (world/LightningAim#column), sorted for everyone near it.
 * <p>
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 6): {@code aim} tells where a leader right over the own player would
 * strike (the rolling sphere, a strike distance drawn as a storm's), the strike distance and how far around it a person
 * standing alone draws it; {@code aim strike} sends that bolt; {@code aim stats} counts what 200 leaders over the ground
 * within 60 blocks would strike (the own player, a lightning rod, a tree, a truck, another living thing, the ground).
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
                    BlockPos block = BlockPos.containing(looked.getLocation());
                    LightningAim.Aim aim = LightningAim.column(p.serverLevel(), block.getX(), block.getZ(), 45);
                    LightningAim.expect(aim);
                    bolt(p.serverLevel(), aim.at(), false);
                    c.getSource().sendSuccess(() -> Component.literal(describe(p, aim)), false);
                    return 1;
                }))
                .then(Commands.literal("aim").executes(c -> aim(c.getSource(), false))
                        .then(Commands.literal("strike").executes(c -> aim(c.getSource(), true)))
                        .then(Commands.literal("stats").executes(c -> stats(c.getSource()))))));
    }

    private static int aim(CommandSourceStack source, boolean strike) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        double d = StrikePoint.distance(level.random.nextDouble(), LightningInjuryConfig.get().strike.distance);
        LightningAim.Aim aim = LightningAim.pick(level, p.getX(), p.getZ(), d);
        if (aim == null) {
            source.sendFailure(Component.literal("nothing loaded around"));
            return 0;
        }
        if (strike) {
            LightningAim.expect(aim);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return 0;
            bolt.moveTo(Vec3.atBottomCenterOf(aim.column()));
            level.addFreshEntity(bolt);
        }
        source.sendSuccess(() -> Component.literal(describe(p, aim) + String.format(Locale.ROOT, ", a person alone draws it from %.1f",
                StrikePoint.capture(1.8, d))), false);
        return 1;
    }

    private static String describe(ServerPlayer p, LightningAim.Aim aim) {
        String what = aim.kind() == LightningAim.Kind.LIVING && p.getUUID().equals(aim.target()) ? "you" : aim.kind().name().toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "strike: %s at %.1f %.1f %.1f (%.1f away), strike distance %.0f", what, aim.at().x, aim.at().y,
                aim.at().z, Math.hypot(aim.at().x - p.getX(), aim.at().z - p.getZ()), aim.distance());
    }

    /** 200 leaders over the ground within 60 blocks: what each would strike. */
    private static int stats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        double[] tri = LightningInjuryConfig.get().strike.distance;
        Map<LightningAim.Kind, Integer> kinds = new EnumMap<>(LightningAim.Kind.class);
        int you = 0, none = 0, n = 200;
        for (int i = 0; i < n; i++) {
            double r = 60 * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Mth.TWO_PI;
            LightningAim.Aim aim = LightningAim.pick(level, p.getX() + r * Math.cos(a), p.getZ() + r * Math.sin(a),
                    StrikePoint.distance(level.random.nextDouble(), tri));
            if (aim == null) none++;
            else if (aim.kind() == LightningAim.Kind.LIVING && p.getUUID().equals(aim.target())) you++;
            else kinds.merge(aim.kind(), 1, Integer::sum);
        }
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "%d leaders within 60: you %d", n, you));
        for (LightningAim.Kind k : LightningAim.Kind.values())
            b.append(", ").append(k.name().toLowerCase(Locale.ROOT)).append(' ').append(kinds.getOrDefault(k, 0));
        if (none > 0) b.append(", unloaded ").append(none);
        source.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
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
