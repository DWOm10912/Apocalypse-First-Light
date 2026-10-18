package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;

/**
 * T15 get-out spot (user 2026-10-16: getting out climbed out through the roof): the server's dismount spot on a full
 * block floor and on road_surface layers, straight and turned, after the truck has settled; where it lands in the truck's
 * frame, and per candidate why it was refused. Also a seated rider's own box (where the client is left when it stops
 * riding) against the hull.
 */
@GameTestHolder("afl_truck_dismount_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TruckDismountGameTests {
    private static final String NAMESPACE = "afl_truck_dismount_tests";
    private static final int SX = 16, SY = 8, SZ = 16;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[" + SX + "," + SY + "," + SZ + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < SX; x++) for (int y = 0; y < SY; y++) for (int z = 0; z < SZ; z++) {
            var block = new net.minecraft.nbt.CompoundTag();
            var position = new net.minecraft.nbt.ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(net.minecraft.nbt.IntTag.valueOf(coordinate));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("truck_dismount", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 400, 0L, true, TruckDismountGameTests::run));
    }

    private record Case(String name, int layers, float yaw) {}

    /**
     * CabShelter: a zombie's blow on the driver from beside the shut left door, through the windshield, then with the
     * left door open; the driver's blow on the zombie the same ways; a bed rider stays open.
     */
    private static void shelter(GameTestHelper h, ServerLevel level, StringBuilder log, boolean[] ok) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        double top = origin.getY() + 2;
        for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) {
            for (int y = 2; y < SY; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(x, 1, z), Blocks.STONE.defaultBlockState());
        }
        Vec3 at = new Vec3(origin.getX() + SX / 2.0, top + 0.3, origin.getZ() + SZ / 2.0);
        PickupTruckEntity truck = PickupTruckEntity.create(level, at, 0F);
        level.addFreshEntity(truck);
        Player driver = h.makeMockPlayer(), bed = h.makeMockPlayer();
        net.minecraft.world.entity.monster.Zombie zombie = new net.minecraft.world.entity.monster.Zombie(level);
        h.runAfterDelay(20, () -> {
            sit(truck, driver, PickupTruckLayout.DRIVER);
            sit(truck, bed, 2);
            Vec3 left = PickupTruckLayout.world(truck.position(), truck.getYRot(), 0.10, -1.2, 0);
            Vec3 ahead = PickupTruckLayout.world(truck.position(), truck.getYRot(), 1.5, -0.3, 0);
            zombie.moveTo(left.x, truck.getY(), left.z, 0, 0);
            boolean shutSide = blocked(driver, zombie), shutHit = hitBlocked(driver, zombie), bedOpen = blocked(bed, zombie);
            zombie.moveTo(ahead.x, truck.getY(), ahead.z, 0, 0);
            boolean front = blocked(driver, zombie);
            truck.setDoorOpen(false, true);
            h.runAfterDelay(20, () -> {
                zombie.moveTo(left.x, truck.getY(), left.z, 0, 0);
                boolean openSide = blocked(driver, zombie), openHit = hitBlocked(driver, zombie);
                log.append(String.format("cab shelter: left door shut: zombie's blow %s, driver's blow %s; windshield: %s; bed rider: %s; left door open (%.0f deg): zombie's blow %s, driver's blow %s%n",
                        word(shutSide), word(shutHit), word(front), word(bedOpen), truck.doorAngle(false, 1F), word(openSide), word(openHit)));
                if (!shutSide || !shutHit || !front || bedOpen || openSide || openHit) ok[0] = false;
                truck.discard();
                moveOver(h, level, log, ok);
            });
        });
    }

    /**
     * Moving over (user 2026-10-16): a passenger looking at the free driver's seat gets the offer, a click moves it over
     * (the eye slides across), a look elsewhere gets none; then out of the driver's seat by the left door.
     */
    private static void moveOver(GameTestHelper h, ServerLevel level, StringBuilder log, boolean[] ok) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        double top = origin.getY() + 2;
        Vec3 at = new Vec3(origin.getX() + SX / 2.0, top + 0.3, origin.getZ() + SZ / 2.0);
        PickupTruckEntity truck = PickupTruckEntity.create(level, at, 25F);
        level.addFreshEntity(truck);
        Player rider = h.makeMockPlayer();
        h.runAfterDelay(20, () -> {
            sit(truck, rider, PickupTruckLayout.PASSENGER);
            truck.positionRider(rider);
            Vec3 eye0 = rider.getEyePosition();
            // look ahead: no offer; look at the driver's cushion: move over
            look(rider, PickupTruckLayout.world(truck.position(), truck.getYRot(), 3.0, 0.37, 1.3));
            var ahead = truck.actionFor(rider);
            Vec3 cushion = PickupTruckLayout.world(truck.position(), truck.getYRot(), -0.06, -0.37, 0.85);
            look(rider, cushion);
            var offer = truck.actionFor(rider);
            double[] eo = PickupTruckLayout.local(truck.position(), truck.getYRot(), rider.getEyePosition()), ed = PickupTruckLayout.localDir(truck.getYRot(), rider.getViewVector(1.0F));
            log.append(String.format("  move over look: speed %.3f, vehicle %s, eye %+.2f %+.2f %+.2f, dir %+.2f %+.2f %+.2f, seat hit %.2f%n", truck.speed(), rider.getVehicle() == truck,
                    eo[0], eo[1], eo[2], ed[0], ed[1], ed[2], PickupTruckLayout.cabSeatHit(PickupTruckLayout.DRIVER, eo, ed, 2.0)));
            truck.interact(rider, net.minecraft.world.InteractionHand.MAIN_HAND);
            int seatAfter = truck.seatOf(rider);
            double[] mid = new double[1];
            h.runAfterDelay(7, () -> {
                truck.positionRider(rider);
                mid[0] = PickupTruckLayout.local(truck.position(), truck.getYRot(), rider.getEyePosition())[1];
                h.runAfterDelay(15, () -> {
                    truck.positionRider(rider);
                    double[] end = PickupTruckLayout.local(truck.position(), truck.getYRot(), rider.getEyePosition());
                    double[] start = PickupTruckLayout.local(truck.position(), truck.getYRot(), eye0);
                    rider.stopRiding();
                    Vec3 out = rider.position();
                    double[] o = PickupTruckLayout.local(truck.position(), truck.getYRot(), out);
                    boolean good = ahead == null && offer != null && offer.action() == PickupTruckEntity.Action.MOVE_OVER
                            && seatAfter == PickupTruckLayout.DRIVER && mid[0] < start[1] - 0.1 && mid[0] > end[1] + 0.1
                            && Math.abs(end[1] - start[1] + 0.74) < 0.03 && o[1] < -1.0;
                    log.append(String.format("move over: looking ahead %s, at the driver's seat %s; seat after the click %d; eye across %+.2f -> %+.2f (half way %+.2f) -> %+.2f; out at right %+.2f%n",
                            ahead == null ? "no offer" : ahead.action(), offer == null ? "no offer" : offer.action(), seatAfter, start[1], end[1], mid[0], end[1], o[1]));
                    if (!good) ok[0] = false;
                    truck.discard();
                    lamps(h, level, log, ok);
                });
            });
        });
    }

    /**
     * The lamps (design doc 8.10): the one light key (server side of VehiclePackets.Lamps): a tap turns the lights on on low
     * beam / off, a hold flips the high beam and from off turns the lights on with it; kept in the save; the stop lamps while
     * the driver holds the back key (the brake). The turn signals (8.12): a key toggles its side, the other side's key
     * switches over; the hazard toggles on its own; both kept in the save.
     */
    private static void lamps(GameTestHelper h, ServerLevel level, StringBuilder log, boolean[] ok) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        Vec3 at = new Vec3(origin.getX() + SX / 2.0, origin.getY() + 2.3, origin.getZ() + SZ / 2.0);
        PickupTruckEntity truck = PickupTruckEntity.create(level, at, 0F);
        level.addFreshEntity(truck);
        Player driver = h.makeMockPlayer();
        h.runAfterDelay(20, () -> {
            lamp(truck, PickupTruckEntity.SWITCH_LIGHTS);
            boolean lights = truck.headlights() && !truck.highBeam();
            lamp(truck, PickupTruckEntity.SWITCH_HIGH);
            boolean high = truck.highBeam();
            lamp(truck, PickupTruckEntity.SWITCH_HIGH);
            boolean back = truck.headlights() && !truck.highBeam();
            lamp(truck, PickupTruckEntity.SWITCH_HIGH);
            lamp(truck, PickupTruckEntity.SWITCH_LIGHTS);
            boolean off = !truck.headlights() && !truck.highBeam();
            lamp(truck, PickupTruckEntity.SWITCH_LIGHTS);
            boolean lowAgain = truck.headlights() && !truck.highBeam();
            lamp(truck, PickupTruckEntity.SWITCH_LIGHTS);
            boolean fromOff = lamp(truck, PickupTruckEntity.SWITCH_HIGH) && truck.headlights() && truck.highBeam();
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            truck.saveWithoutId(tag);
            PickupTruckEntity copy = PickupTruckEntity.create(level, at, 0F);
            copy.load(tag);
            boolean saved = copy.headlights() && copy.highBeam();
            lamp(truck, PickupTruckEntity.SIGNAL_LEFT);
            boolean left = truck.turnSignal() == -1;
            lamp(truck, PickupTruckEntity.SIGNAL_RIGHT);
            boolean over = truck.turnSignal() == 1;
            lamp(truck, PickupTruckEntity.SWITCH_HAZARD);
            boolean hazard = truck.hazard() && truck.turnSignal() == 1;
            net.minecraft.nbt.CompoundTag tag2 = new net.minecraft.nbt.CompoundTag();
            truck.saveWithoutId(tag2);
            PickupTruckEntity copy2 = PickupTruckEntity.create(level, at, 0F);
            copy2.load(tag2);
            boolean signalSaved = copy2.hazard() && copy2.turnSignal() == 1;
            lamp(truck, PickupTruckEntity.SIGNAL_RIGHT);
            lamp(truck, PickupTruckEntity.SWITCH_HAZARD);
            boolean signalOff = truck.turnSignal() == 0 && !truck.hazard() && truck.highBeam();
            // the wiper stalk (design doc 8.16): off, intermittent, low, high, off again; kept in the save; the lamps untouched
            boolean wiperOff = truck.wiperMode() == com.antaurora.apofirstlight.vehicle.WiperSystem.OFF;
            StringBuilder steps = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                lamp(truck, PickupTruckEntity.SWITCH_WIPER);
                steps.append(truck.wiperMode());
            }
            lamp(truck, PickupTruckEntity.SWITCH_WIPER);
            lamp(truck, PickupTruckEntity.SWITCH_WIPER);
            net.minecraft.nbt.CompoundTag tag3 = new net.minecraft.nbt.CompoundTag();
            truck.saveWithoutId(tag3);
            PickupTruckEntity copy3 = PickupTruckEntity.create(level, at, 0F);
            copy3.load(tag3);
            boolean wipers = wiperOff && steps.toString().equals("1230") && truck.wiperMode() == com.antaurora.apofirstlight.vehicle.WiperSystem.LOW
                    && copy3.wiperMode() == com.antaurora.apofirstlight.vehicle.WiperSystem.LOW && truck.highBeam() && truck.turnSignal() == 0;
            sit(truck, driver, PickupTruckLayout.DRIVER);
            driver.zza = -1.0F;
            h.runAfterDelay(3, () -> {
                boolean braking = truck.braking();
                driver.zza = 0.0F;
                h.runAfterDelay(3, () -> {
                    boolean released = !truck.braking();
                    boolean good = lights && high && back && off && lowAgain && fromOff && saved && braking && released
                            && left && over && hazard && signalSaved && signalOff && wipers;
                    log.append(String.format("lamps: tap on low %s; hold high %s, hold back %s; tap off %s, on again low %s; hold from off %s; kept in the save %s; braking %s, released %s%n",
                            lights, high, back, off, lowAgain, fromOff, saved, braking, released));
                    log.append(String.format("signals: left %s, over to right %s, hazard with it %s, kept in the save %s, both off again %s%n",
                            left, over, hazard, signalSaved, signalOff));
                    log.append(String.format("wipers: off at first %s, steps %s (want 1230), kept in the save and lamps untouched %s%n",
                            wiperOff, steps, wipers));
                    if (!good) ok[0] = false;
                    driver.stopRiding();
                    truck.discard();
                    log.append(ok[0] ? "[AFL TRUCK DISMOUNT TEST] PASS" : "[AFL TRUCK DISMOUNT TEST] FAIL");
                    ApocalypseFirstLight.LOGGER.info(log.toString());
                    if (ok[0]) h.succeed();
                    else h.fail("dismount spots / cab shelter / move over / lamps: see the log");
                });
            });
        });
    }

    /** PickupTruckEntity#lampSwitch (package-private); true afterwards. */
    private static boolean lamp(PickupTruckEntity truck, int action) {
        try {
            var m = PickupTruckEntity.class.getDeclaredMethod("lampSwitch", int.class);
            m.setAccessible(true);
            m.invoke(truck, action);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Turns the player's look toward a point. */
    private static void look(Player p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setYHeadRot(p.getYRot());   // a living entity's view turns with its head
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }

    private static String word(boolean blocked) { return blocked ? "blocked" : "lands"; }

    private static void sit(PickupTruckEntity truck, Player player, int seat) {
        try {
            var sit = PickupTruckEntity.class.getDeclaredMethod("sit", Player.class, int.class);
            sit.setAccessible(true);
            sit.invoke(truck, player, seat);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the zombie's blow on the rider is stopped (CabShelter cancels the attack event). */
    private static boolean blocked(Player rider, net.minecraft.world.entity.monster.Zombie zombie) {
        return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.living.LivingAttackEvent(rider, rider.level().damageSources().mobAttack(zombie), 3F));
    }

    /** Whether the rider's own blow on the zombie is stopped. */
    private static boolean hitBlocked(Player rider, net.minecraft.world.entity.monster.Zombie zombie) {
        return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.AttackEntityEvent(rider, zombie));
    }

    private static final Case[] CASES = {
            new Case("stone", 0, 0F), new Case("stone turned", 0, 37F),
            new Case("road 16 layers", 16, 0F), new Case("road 16 layers turned", 16, 37F),
            new Case("road 5 layers", 5, 0F), new Case("road 32 layers", 32, 0F),
            // a road's cross slope: one more layer every block across, the door spots' boxes over two heights (user 2026-10-16)
            new Case("road cross slope", -1, 0F),
    };

    private static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        StringBuilder log = new StringBuilder("[AFL TRUCK DISMOUNT TEST]\n");
        int[] index = {0};
        boolean[] ok = {true};
        step(h, level, log, index, ok);
    }

    private static void step(GameTestHelper h, ServerLevel level, StringBuilder log, int[] index, boolean[] ok) {
        if (index[0] >= CASES.length) {
            shelter(h, level, log, ok);
            return;
        }
        Case c = CASES[index[0]];
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        // floor: stone at y 1, road_surface layers on y 2 (or nothing), air above; clear the rest
        for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(x, 1, z), Blocks.STONE.defaultBlockState());
            int layers = c.layers < 0 ? 8 + x : c.layers;
            if (layers > 0) {
                BlockState road = AflBlocks.ROAD_SURFACE.get().defaultBlockState().setValue(RoadSurfaceBlock.LAYERS, layers);
                level.setBlockAndUpdate(origin.offset(x, 2, z), road);
            }
        }
        double top = origin.getY() + 2 + (c.layers > 0 ? c.layers / 32.0 : c.layers < 0 ? (8 + SX / 2) / 32.0 : 0);
        // cross slope: off the block middle so each door spot's box stands over two blocks, its middle on the lower
        Vec3 at = new Vec3(origin.getX() + SX / 2.0 + (c.layers < 0 ? 0.4 : 0), top + 0.3, origin.getZ() + SZ / 2.0);
        PickupTruckEntity truck = PickupTruckEntity.create(level, at, c.yaw);
        level.addFreshEntity(truck);
        Player player = h.makeMockPlayer();
        player.moveTo(at.x + 4, top + 0.5, at.z, 0, 0);
        h.runAfterDelay(30, () -> {
            log.append(String.format("%s: truck y %+.3f over the floor top, pitch/roll settled%n", c.name, truck.getY() - top));
            Vec3 spot = truck.getDismountLocationForPassenger(player);
            double[] l = PickupTruckLayout.local(truck.position(), truck.getYRot(), spot);
            boolean roof = l[2] > 1.5, leftDoor = l[1] < -1.0;
            log.append(String.format("  spot: ahead %+.2f right %+.2f up %+.3f (floor %+.3f)%s%s%n", l[0], l[1], l[2], spot.y - top, roof ? "  ROOF" : "",
                    leftDoor ? "" : "  NOT THE DRIVER'S DOOR"));
            if (roof || !leftDoor) ok[0] = false;
            // the door spots, piece by piece
            for (double sd : new double[]{-1, 1}) {
                Vec3 p = PickupTruckLayout.world(truck.position(), truck.getYRot(), PickupTruckLayout.EXIT_F, sd * PickupTruckLayout.EXIT_R, 0);
                BlockPos base = BlockPos.containing(p.x, p.y + 0.5, p.z);
                for (BlockPos pos : new BlockPos[]{base, base.below(), base.above()}) {
                    double floor = level.getBlockFloorHeight(pos);
                    StringBuilder why = new StringBuilder();
                    if (DismountHelper.isBlockFloorValid(floor)) {
                        Vec3 s = new Vec3(p.x, pos.getY() + floor, p.z);
                        AABB box = player.getLocalBoundsForPose(Pose.STANDING).move(s);
                        why.append(String.format("at %+.3f: blocks %s, hull %s", s.y - top,
                                DismountHelper.canDismountTo(level, player, box) ? "free" : "IN THE WAY",
                                VehicleColliders.blocked(level, box.deflate(1.0E-4), player) ? "IN THE WAY" : "free"));
                    } else why.append("floor not valid (").append(floor).append(")");
                    log.append(String.format("    side %+.0f block y %+d: %s%n", sd, pos.getY() - origin.getY() - 2, why));
                }
            }
            // a seated rider's own box (what a client is left with when it stops riding before the teleport)
            try {
                var sit = PickupTruckEntity.class.getDeclaredMethod("sit", Player.class, int.class);
                sit.setAccessible(true);
                sit.invoke(truck, player, PickupTruckLayout.DRIVER);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            truck.positionRider(player);
            AABB seated = player.getBoundingBox();
            log.append(String.format("  rider box while seated: feet %+.3f over the floor top, in the hull: %s%n", seated.minY - top,
                    VehicleColliders.blocked(level, seated.deflate(1.0E-4), player.getVehicle() == null ? player : truck) ? "yes" : "no"));
            player.stopRiding();
            truck.discard();
            player.discard();
            index[0]++;
            step(h, level, log, index, ok);
        });
    }
}
