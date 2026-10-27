package com.antaurora.apofirstlight.creature.infected;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Infected Z1a's test commands (op): {@code /afl dev infected spawn [count]} puts shamblers 8 blocks in front of the caller
 * (spread over a few blocks, on the ground there); {@code /afl dev infected clear} removes every infected and infected body
 * in the caller's level.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectedDevCommand {
    private static final double DISTANCE = 8.0;

    private InfectedDevCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("infected")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").executes(c -> spawn(c.getSource(), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                .executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource()))))));
    }

    private static int spawn(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        float yaw = source.getRotation().y;
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        int made = 0;
        for (int i = 0; i < count; i++) {
            InfectedEntity e = AflEntities.INFECTED_SHAMBLER.get().create(level);
            if (e == null) break;
            double side = (i - (count - 1) / 2.0) * 1.5, back = (i % 2) * 1.2;
            Vec3 p = at.add(forward.scale(DISTANCE + back)).add(-forward.z * side, 0, forward.x * side);
            int x = Mth.floor(p.x), z = Mth.floor(p.z);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - at.y) > 6) y = Mth.floor(at.y);
            float face = yaw + 180.0F;
            e.moveTo(p.x, y, p.z, face, 0.0F);
            e.setYHeadRot(face);
            e.yBodyRot = face;
            e.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null, null);
            if (level.addFreshEntity(e)) made++;
        }
        int n = made;
        source.sendSuccess(() -> Component.literal("放出 " + n + " 个普通感染者"), true);
        return made;
    }

    private static int clear(CommandSourceStack source) {
        java.util.List<Entity> gone = new java.util.ArrayList<>();
        for (Entity e : source.getLevel().getAllEntities()) if (e instanceof InfectedEntity || e instanceof InfectedCorpse) gone.add(e);
        gone.forEach(Entity::discard);
        int n = gone.size();
        int removed = n;
        source.sendSuccess(() -> Component.literal("清除了 " + removed + " 个感染者和尸体"), true);
        return n;
    }
}
