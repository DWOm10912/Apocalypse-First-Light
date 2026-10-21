package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Test commands for the T15 pickup body (permission 2, docs/vehicles/first_pickup_v0_design.md):
 * <ul><li>{@code /afl dev vehicle spawn [colour]}: a truck 5 blocks ahead on the ground, broadside to the player;</li>
 * <li>{@code /afl dev vehicle paint <colour> [second] [wear]}, {@code wear <0..1>}, {@code hood}, {@code tailgate},
 * {@code door <left|right>}, {@code filler}, {@code fuel <litres>}, {@code cargo add} (the held stack goes into the
 * bed's grid: a test until the cargo grid opens), {@code cargo clear}: the nearest truck within 16 blocks.</li>
 * <li>{@code /afl dev vehicle key <ignition|seat|glovebox|ashtray|visor_l|visor_r|elsewhere|random>}: puts the nearest
 * truck's key there (random: as a found truck, mostly elsewhere); {@code key give}: a copy of its key into the inventory
 * (docs/vehicles/t15_hotwire_v1_design.md section 9). A spawned truck has its key in the ignition (a test truck drives at
 * once).</li></ul>
 * A colour is a preset name (white, silver, deep_red, forest_green, navy, tan, light_blue, dark_grey) or RRGGBB hex.
 * forest_green alone brings its beige lower band (the two-tone factory option).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PickupTruckCommands {
    private static final double REACH = 16;
    private static final SimpleCommandExceptionType NO_TRUCK = new SimpleCommandExceptionType(Component.literal("No T15 truck within 16 blocks"));
    private static final SimpleCommandExceptionType BAD_COLOUR = new SimpleCommandExceptionType(Component.literal("Colour: a preset name or RRGGBB hex"));
    private static final SimpleCommandExceptionType BAD_SPOT = new SimpleCommandExceptionType(Component.literal("Key: ignition, seat, glovebox, ashtray, visor_l, visor_r, elsewhere or random"));
    /** Factory colours (design doc section 2.1): main, second (the lower band; the same for one colour). */
    private static final Map<String, int[]> PRESETS = new LinkedHashMap<>();
    static {
        // the factory colours, shared with the Creative spawner
        for (VehicleSpawnerItem.Paint paint : VehicleSpawnerItem.PAINTS) PRESETS.put(paint.id(), new int[]{paint.main(), paint.second()});
    }
    private static final SuggestionProvider<CommandSourceStack> COLOURS = (c, b) -> SharedSuggestionProvider.suggest(PRESETS.keySet(), b);

    private PickupTruckCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("vehicle")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(c -> spawn(c, null))
                        .then(Commands.argument("colour", StringArgumentType.word()).suggests(COLOURS)
                                .executes(c -> spawn(c, StringArgumentType.getString(c, "colour")))))
                .then(Commands.literal("paint").then(Commands.argument("colour", StringArgumentType.word()).suggests(COLOURS)
                        .executes(c -> paint(c, null, Float.NaN))
                        .then(Commands.argument("second", StringArgumentType.word()).suggests(COLOURS)
                                .executes(c -> paint(c, StringArgumentType.getString(c, "second"), Float.NaN))
                                .then(Commands.argument("wear", FloatArgumentType.floatArg(0, 1))
                                        .executes(c -> paint(c, StringArgumentType.getString(c, "second"), FloatArgumentType.getFloat(c, "wear")))))))
                .then(Commands.literal("wear").then(Commands.argument("wear", FloatArgumentType.floatArg(0, 1))
                        .executes(c -> {
                            PickupTruckEntity truck = nearest(c.getSource());
                            truck.setWear(FloatArgumentType.getFloat(c, "wear"));
                            return report(c.getSource(), truck);
                        })))
                .then(Commands.literal("hood").executes(c -> {
                    PickupTruckEntity truck = nearest(c.getSource());
                    truck.setHoodOpen(!truck.hoodOpen());
                    return report(c.getSource(), truck);
                }))
                .then(Commands.literal("tailgate").executes(c -> {
                    PickupTruckEntity truck = nearest(c.getSource());
                    truck.setTailgateOpen(!truck.tailgateOpen());
                    return report(c.getSource(), truck);
                }))
                .then(Commands.literal("filler").executes(c -> {
                    PickupTruckEntity truck = nearest(c.getSource());
                    truck.setFillerOpen(!truck.fillerOpen());
                    return report(c.getSource(), truck);
                }))
                .then(Commands.literal("fuel").then(Commands.argument("litres", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, PickupTruckEntity.TANK))
                        .executes(c -> {
                            PickupTruckEntity truck = nearest(c.getSource());
                            truck.setFuel(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "litres"));
                            return report(c.getSource(), truck);
                        })))
                .then(Commands.literal("cargo")
                        .then(Commands.literal("add").executes(c -> {
                            PickupTruckEntity truck = nearest(c.getSource());
                            ServerPlayer player = c.getSource().getPlayerOrException();
                            var held = player.getMainHandItem();
                            if (!held.isEmpty() && truck.cargo().insert(held)) truck.cargoChanged();
                            return report(c.getSource(), truck);
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            PickupTruckEntity truck = nearest(c.getSource());
                            for (var e : java.util.List.copyOf(truck.cargo().entries())) truck.cargo().remove(e);
                            truck.cargoChanged();
                            return report(c.getSource(), truck);
                        })))
                .then(Commands.literal("key")
                        .then(Commands.literal("give").executes(c -> {
                            PickupTruckEntity truck = nearest(c.getSource());
                            c.getSource().getPlayerOrException().getInventory().add(VehicleKeyItem.forVehicle(truck.getUUID(), "pickup_t15"));
                            return report(c.getSource(), truck);
                        }))
                        .then(Commands.argument("spot", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
                                        java.util.Arrays.stream(CabStowage.KeySpot.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), java.util.stream.Stream.of("random")), b))
                                .executes(c -> {
                                    PickupTruckEntity truck = nearest(c.getSource());
                                    String name = StringArgumentType.getString(c, "spot");
                                    CabStowage.KeySpot spot = "random".equalsIgnoreCase(name) ? CabStowage.KeySpot.random(truck.level().getRandom()) : CabStowage.KeySpot.byName(name);
                                    if (spot == null) throw BAD_SPOT.create();
                                    truck.setKeySpot(spot);
                                    return report(c.getSource(), truck);
                                })))
                .then(Commands.literal("door")
                        .then(Commands.literal("left").executes(c -> door(c.getSource(), false)))
                        .then(Commands.literal("right").executes(c -> door(c.getSource(), true)))))));
    }

    private static int door(CommandSourceStack source, boolean right) throws CommandSyntaxException {
        PickupTruckEntity truck = nearest(source);
        truck.setDoorOpen(right, !truck.doorOpen(right));
        return report(source, truck);
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String colour) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 at = player.position().add(look.scale(5));
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        // broadside: the truck's heading is the player's yaw turned a quarter, so its side faces the player
        PickupTruckEntity truck = PickupTruckEntity.create(level, new Vec3(at.x, ground.getY(), at.z), player.getYRot() + 90.0F);
        if (colour != null) {
            int[] c2 = colour(colour);
            truck.setPaint(c2[0], c2[1]);
        }
        level.addFreshEntity(truck);
        return report(c.getSource(), truck);
    }

    private static int paint(CommandContext<CommandSourceStack> c, String second, float wear) throws CommandSyntaxException {
        PickupTruckEntity truck = nearest(c.getSource());
        int[] main = colour(StringArgumentType.getString(c, "colour"));
        truck.setPaint(main[0], second == null ? main[1] : colour(second)[0]);
        if (!Float.isNaN(wear)) truck.setWear(wear);
        return report(c.getSource(), truck);
    }

    private static int[] colour(String text) throws CommandSyntaxException {
        int[] preset = PRESETS.get(text.toLowerCase(Locale.ROOT));
        if (preset != null) return preset;
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) throw BAD_COLOUR.create();
        try {
            int rgb = Integer.parseInt(hex, 16);
            return new int[]{rgb, rgb};
        } catch (NumberFormatException e) {
            throw BAD_COLOUR.create();
        }
    }

    private static PickupTruckEntity nearest(CommandSourceStack source) throws CommandSyntaxException {
        Vec3 at = source.getPosition();
        return source.getLevel().getEntitiesOfClass(PickupTruckEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(REACH))
                .stream().min(Comparator.comparingDouble(t -> t.distanceToSqr(at))).orElseThrow(NO_TRUCK::create);
    }

    private static int report(CommandSourceStack source, PickupTruckEntity truck) {
        String text = String.format(Locale.ROOT, "T15 #%d paint=%06X/%06X wear=%.2f hood=%s doors=%s/%s tailgate=%s filler=%s fuel=%d/%d L cargo=%d key=%s", truck.getId(),
                truck.paint(), truck.secondPaint(), truck.wear(), truck.hoodOpen() ? "open" : "shut",
                truck.doorOpen(false) ? "open" : "shut", truck.doorOpen(true) ? "open" : "shut", truck.tailgateOpen() ? "open" : "shut",
                truck.fillerOpen() ? "open" : "shut", truck.fuel(), PickupTruckEntity.TANK, truck.cargo().entries().size(),
                truck.keySpot().name().toLowerCase(Locale.ROOT));
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
