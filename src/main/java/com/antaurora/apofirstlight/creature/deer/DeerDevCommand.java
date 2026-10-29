package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * White-tailed deer test commands (op); the world places its own deer (DeerPopulation), these put more in by hand (not
 * counted in any cell):
 * <ul>
 *   <li>{@code /afl dev deer spawn buck|doe [count]}: deer 24 blocks in front of the caller, side on;</li>
 *   <li>{@code /afl dev deer spawn herd [count]}: a doe leading, the rest does and a buck (one in four), following her;</li>
 *   <li>{@code /afl dev deer calm true|false}: the deer stop seeing, smelling and hearing anything (to watch them up
 *   close); not saved;</li>
 *   <li>{@code /afl dev deer bed on|off|clock}: bed time (D2b) forced on or off for every deer, or back to the clock; not
 *   saved;</li>
 *   <li>{@code /afl dev deer census}: the natural population's books (DeerPopulation) in the 9 cells round the caller;</li>
 *   <li>{@code /afl dev deer clear}: removes every deer and carcass in the caller's level (the cells' counts follow).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeerDevCommand {
    private static final double DISTANCE = 24.0;

    private DeerDevCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("deer")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(kind("buck", 0)).then(kind("doe", 1)).then(kind("herd", 2)))
                .then(Commands.literal("calm").then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> calm(c.getSource(), BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("bed")
                        .then(Commands.literal("on").executes(c -> bed(c.getSource(), Boolean.TRUE)))
                        .then(Commands.literal("off").executes(c -> bed(c.getSource(), Boolean.FALSE)))
                        .then(Commands.literal("clock").executes(c -> bed(c.getSource(), null))))
                .then(Commands.literal("census").executes(c -> census(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource()))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> kind(String name, int kind) {
        int fallback = kind == 2 ? 5 : 1;
        return Commands.literal(name).executes(c -> spawn(c.getSource(), kind, fallback))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .executes(c -> spawn(c.getSource(), kind, IntegerArgumentType.getInteger(c, "count"))));
    }

    private static int spawn(CommandSourceStack source, int kind, int count) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        float yaw = source.getRotation().y;
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        DeerEntity lead = null;
        int made = 0;
        for (int i = 0; i < count; i++) {
            DeerEntity e = AflEntities.WHITETAIL_DEER.get().create(level);
            if (e == null) break;
            boolean doe = kind == 1 || (kind == 2 && (i == 0 || i % 4 != 3));
            e.setDoe(doe);
            double side = (i - (count - 1) / 2.0) * 2.5, back = (i % 2) * 2.0;
            Vec3 p = at.add(forward.scale(DISTANCE + back)).add(-forward.z * side, 0, forward.x * side);
            int x = Mth.floor(p.x), z = Mth.floor(p.z);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - at.y) > 8) y = Mth.floor(at.y);
            float face = yaw + 90.0F + level.random.nextFloat() * 40.0F - 20.0F;
            e.moveTo(p.x, y, p.z, face, 0.0F);
            e.setYHeadRot(face);
            e.yBodyRot = face;
            e.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null, null);
            if (kind == 2 && lead != null) e.setLeader(lead);
            if (level.addFreshEntity(e)) {
                made++;
                if (lead == null) lead = e;
            }
        }
        int n = made;
        source.sendSuccess(() -> Component.literal("放出 " + n + " 只白尾鹿"), true);
        return made;
    }

    private static int bed(CommandSourceStack source, @org.jetbrains.annotations.Nullable Boolean on) {
        DeerEntity.devBed = on;
        String text = on == null ? "Deer bed time: by the clock (9:30–16:30, 0:30–3:00, ±40 min a herd)"
                : on ? "Deer bed time: forced on (they find beds and lie down)" : "Deer bed time: forced off (they get up)";
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    private static int census(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        String text = DeerPopulation.get(level).census(level, at.x, at.z);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int calm(CommandSourceStack source, boolean on) {
        DeerEntity.devCalm = on;
        source.sendSuccess(() -> Component.literal(on ? "白尾鹿不再察觉任何东西" : "白尾鹿恢复警觉"), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        List<Entity> gone = new ArrayList<>();
        for (Entity e : source.getLevel().getAllEntities()) if (e instanceof DeerEntity || e instanceof DeerCorpse) gone.add(e);
        gone.forEach(Entity::discard);
        int n = gone.size();
        source.sendSuccess(() -> Component.literal("清除了 " + n + " 只白尾鹿和尸体"), true);
        return n;
    }
}
