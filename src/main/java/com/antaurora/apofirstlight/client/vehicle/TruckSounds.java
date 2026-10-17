package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.vehicle.physics.Drivetrain;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The T15's sounds on each client (docs/vehicles/first_pickup_v0_design.md 8.11; the files from
 * tools/build-pickup-t15-sounds.mjs): everything follows the synced state (engine, ignition, gear, doors, lamps / horn),
 * so every player near a truck hears the same. The infected hear the same things as far (PickupTruckEntity's noises;
 * the attenuation distances in sounds.json are those radii).
 * <ul>
 * <li>Engine: four loops played together and mixed every tick by the engine speed: the idle loop (recorded at about
 * {@link #IDLE_RPM} rpm) crossfades (equal power) into the revving loop ({@link #MID_RPM}) between {@link #MIX_FROM} and
 * MIX_FROM + MIX_SPAN rpm, each pitched by rpm over its own speed (0.5-2x); over them the pulling layer by the throttle
 * (full in gear with the clutch in, a third in neutral) and the coasting layer off the throttle in gear above
 * {@link #COAST_FROM} rpm. Starting: the key and the glow-plug relay as the preheat begins, the starter loop while it
 * cranks, the catch when it fires (the loops fade in under its tail), the stop when it stops.</li>
 * <li>Tyres: a loop per surface (asphalt, gravel, dirt, grass), each by the share of the four wheels on it, louder and a
 * little higher with speed; a skid when the wheels slide on a hard surface (locked or sideways), louder loose surfaces when
 * they slide there. Wind in the cab: only for a rider of this truck, rising with speed (not positional).</li>
 * <li>One-shots: a crash (speed lost in one tick: light / heavy), a scrape (a smaller loss while still moving), the
 * suspension bottoming out, doors (open as they start to open, shut as they close), the hood (the door sounds, lower), the
 * tailgate (as it reaches open and shut), the filler cap, a gear change, the 2H / 4H lever, the handbrake (the local
 * driver's), the horn (a loop while it sounds).</li>
 * </ul>
 * A rider in the cab hears the engine and tyres a little muffled.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TruckSounds {
    private static final double RANGE = 64;
    /** The loops' recorded engine speeds (rpm, measured in the takes). */
    static final double IDLE_RPM = 770, MID_RPM = 1830, LOAD_RPM = 1830, COAST_RPM = 1300, CRANK_RPM = 250;
    /** The idle -> revving crossfade, and where the coasting layer comes in (rpm). */
    static final double MIX_FROM = 850, MIX_SPAN = 900, COAST_FROM = 1100;
    /** Ticks after the catch before the loops fade in, and their fade-in (the catch fades out over its last second). */
    private static final int CATCH_DELAY = 6, CATCH_FADE = 16;
    /** Tyres and wind at full volume at this speed (m/s); a slide this fast (m/s) skids. */
    private static final double TYRE_FULL = 11, WIND_FULL = 24, SKID_SLIP = 3;
    /** In the cab: engine and tyres scaled by this. */
    private static final float CAB_MUFFLE = 0.72F;
    private static final int ASPHALT = 0, GRAVEL = 1, DIRT = 2, GRASS = 3;
    /** A door, the hood or the tailgate swinging shut sounds as it passes this angle (deg), a tick or two before it is shut. */
    private static final float SHUT_AT = 5.0F;
    /** Wheel contacts (forward, right; model frame): front left, front right, rear left, rear right. */
    private static final double[][] WHEELS = {{1.60, -0.712}, {1.60, 0.712}, {-1.25, -0.712}, {-1.25, 0.712}};

    private static final Map<PickupTruckEntity, Voice> VOICES = new WeakHashMap<>();
    private static ClientLevel trackedLevel;

    private TruckSounds() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            VOICES.values().forEach(Voice::stop);
            VOICES.clear();
            trackedLevel = level;
        }
        if (level == null || mc.isPaused()) return;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        for (Entity e : level.entitiesForRendering())
            if (e instanceof PickupTruckEntity truck && truck.distanceToSqr(camera) < RANGE * RANGE) VOICES.computeIfAbsent(truck, Voice::new).tick(mc);
        VOICES.entrySet().removeIf(v -> {
            PickupTruckEntity t = v.getKey();
            if (!t.isRemoved() && t.level() == level && t.distanceToSqr(camera) < (RANGE + 8) * (RANGE + 8)) return false;
            v.getValue().stop();
            return true;
        });
    }

    /** One truck's sounds and what they follow. */
    private static final class Voice {
        final PickupTruckEntity truck;
        final Loop idle, mid, load, coast, crank, wind, horn;
        final Loop[] tyres = new Loop[4];
        boolean running, open[] = new boolean[2], hood, filler, fourWheel, handbrake, first = true;
        int ignition, sinceCatch = 1000, glowAt = -1, age, crashWait, scrapeWait, skidWait, bumpWait;
        float[] doorAngle = new float[2], travel = new float[4];
        float hoodAngle, tailgateAngle;
        double speed, shiftTotal;

        Voice(PickupTruckEntity truck) {
            this.truck = truck;
            Supplier<Vec3> engine = () -> truck.point(1.7, 0, 0.8), middle = () -> truck.point(0, 0, 0.3);
            idle = new Loop(AflSounds.TRUCK_IDLE, engine, false);
            mid = new Loop(AflSounds.TRUCK_MID, engine, false);
            load = new Loop(AflSounds.TRUCK_LOAD, engine, false);
            coast = new Loop(AflSounds.TRUCK_COAST, engine, false);
            crank = new Loop(AflSounds.TRUCK_CRANK, engine, false);
            horn = new Loop(AflSounds.TRUCK_HORN, () -> truck.point(2.2, 0, 0.6), false);
            wind = new Loop(AflSounds.TRUCK_WIND, middle, true);
            tyres[ASPHALT] = new Loop(AflSounds.TRUCK_TYRES_ASPHALT, middle, false);
            tyres[GRAVEL] = new Loop(AflSounds.TRUCK_TYRES_GRAVEL, middle, false);
            tyres[DIRT] = new Loop(AflSounds.TRUCK_TYRES_DIRT, middle, false);
            tyres[GRASS] = new Loop(AflSounds.TRUCK_TYRES_GRASS, middle, false);
        }

        void tick(Minecraft mc) {
            age++;
            ClientLevel level = (ClientLevel) truck.level();
            boolean rider = mc.player != null && mc.player.getVehicle() == truck;
            int seat = rider ? truck.seatOf(mc.player) : -1;
            boolean cab = seat == PickupTruckLayout.DRIVER || seat == PickupTruckLayout.PASSENGER;
            float muffle = cab ? CAB_MUFFLE : 1;

            // ---- engine
            boolean run = truck.engineRunning();
            int ign = truck.ignitionPhase();
            double rpm = truck.tachometer(1.0F);
            if (!first) {
                if (ign == Drivetrain.PREHEAT && ignition == Drivetrain.KEY_OFF) {
                    once(AflSounds.TRUCK_KEY, truck.point(0.55, -0.35, 1.05), 1, 1);
                    glowAt = age + 4;
                }
                if (run && !running) {
                    once(AflSounds.TRUCK_CATCH, truck.point(1.7, 0, 0.8), 1, 1);
                    sinceCatch = 0;
                }
                if (!run && running) once(AflSounds.TRUCK_STOP, truck.point(1.7, 0, 0.8), 1, 1);
            }
            if (age == glowAt) once(AflSounds.TRUCK_GLOW_CLICK, truck.point(1.5, -0.4, 0.8), 1, 1);
            running = run;
            ignition = ign;
            sinceCatch++;
            float fadeIn = Mth.clamp((sinceCatch - CATCH_DELAY) / (float) CATCH_FADE, 0, 1);
            float pedal = truck.throttlePedal();
            boolean engaged = truck.gear() != 0 && truck.clutchPedal() < 0.5F && truck.shiftTotal() <= 0;
            double x = Mth.clamp((rpm - MIX_FROM) / MIX_SPAN, 0, 1);
            float base = run ? (0.8F + 0.2F * pedal) * fadeIn * muffle : 0;
            float coastW = run && engaged && pedal < 0.12F ? (float) Mth.clamp((rpm - COAST_FROM) / 700, 0, 1) * 0.9F * fadeIn * muffle : 0;
            idle.set(base * (float) Math.cos(x * Math.PI / 2), pitch(rpm / IDLE_RPM));
            mid.set(base * (float) Math.sin(x * Math.PI / 2) * (1 - 0.5F * coastW), pitch(rpm / MID_RPM));
            load.set(run ? pedal * (float) Mth.clamp((rpm - 900) / 700, 0, 1) * (engaged ? 1 : 0.35F) * fadeIn * muffle : 0, pitch(rpm / LOAD_RPM));
            coast.set(coastW, pitch(rpm / COAST_RPM));
            crank.set(ign == Drivetrain.CRANK ? muffle : 0, (float) Mth.clamp(rpm / CRANK_RPM, 0.85, 1.15));
            boolean releaseFast = !run;
            idle.release = mid.release = load.release = coast.release = releaseFast ? 0.4F : 0.25F;

            // ---- speed, slides, crashes
            double vx = (truck.getX() - truck.xo) * 20, vz = (truck.getZ() - truck.zo) * 20, v = Math.sqrt(vx * vx + vz * vz);
            Vec3 across = truck.frameDirection(0, 1, 0, 1.0F);
            double lateral = Math.abs(vx * across.x + vz * across.z), wheel = truck.speedometer(1.0F) / 3.6;
            double drop = speed - v;
            if (!first && crashWait <= 0 && drop > PickupTruckEntity.CRASH_DROP) {
                boolean heavy = drop > PickupTruckEntity.CRASH_HEAVY_DROP;
                once(heavy ? AflSounds.TRUCK_CRASH_HEAVY : AflSounds.TRUCK_CRASH_LIGHT, truck.point(0.8, 0, 0.7), 1, 0.95F + 0.1F * level.random.nextFloat());
                crashWait = 10;
            } else if (!first && scrapeWait <= 0 && drop > 1.0 && v > 2) {
                once(AflSounds.TRUCK_SCRAPE, truck.point(0, 0, 0.6), 1, 1);
                scrapeWait = 30;
            }
            speed = v;
            crashWait--;
            scrapeWait--;

            // ---- tyres
            float[] share = new float[4];
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int touching = 0;
            for (double[] w : WHEELS) {
                Vec3 at = truck.point(w[0], w[1], -0.05);
                p.set(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
                BlockState s = level.getBlockState(p);
                if (s.getCollisionShape(level, p).isEmpty()) s = level.getBlockState(p.move(0, -1, 0));
                if (s.isAir()) continue;
                share[surface(s)] += 1;
                touching++;
            }
            boolean sliding = v > 4 && (lateral > SKID_SLIP || v - wheel > SKID_SLIP);
            float roll = (float) Math.pow(Mth.clamp(v / TYRE_FULL, 0, 1), 0.8);
            float tyrePitch = (float) Mth.clamp(0.75 + 0.25 * v / TYRE_FULL, 0.7, 1.4);
            for (int i = 0; i < 4; i++) {
                float s = touching > 0 ? share[i] / 4 : 0;
                float slideBoost = sliding && i != ASPHALT ? 1.5F : 1;
                tyres[i].set(Math.min(1, roll * s * slideBoost * muffle), tyrePitch * (sliding && i != ASPHALT ? 0.9F : 1));
            }
            if (!first && sliding && share[ASPHALT] >= 2 && skidWait <= 0) {
                once(AflSounds.TRUCK_SKID, truck.point(0, 0, 0.2), (float) Mth.clamp(v / 15, 0.5, 1), 1);
                skidWait = 30;
            }
            if (sliding) skidWait = Math.max(skidWait, 10);
            skidWait--;

            // ---- wind (riders only), bottoming out
            wind.set(rider ? (float) Math.pow(Mth.clamp((v - 2) / WIND_FULL, 0, 1), 1.3) * (cab ? 1 : 1.3F) : 0,
                    (float) (0.85 + 0.25 * Math.min(v / WIND_FULL, 1.5)));
            for (int i = 0; i < 4; i++) {
                float t = truck.wheelTravel(i, 1.0F), stop = (float) VehicleSpec.T15.bump - 0.01F;
                if (!first && bumpWait <= 0 && t >= stop && travel[i] < stop && t - travel[i] > 0.025F) {
                    once(AflSounds.TRUCK_BOTTOM_OUT, truck.point(WHEELS[i][0], WHEELS[i][1], 0.4), Mth.clamp((t - travel[i]) * 12, 0.5F, 1), 1);
                    bumpWait = 8;
                }
                travel[i] = t;
            }
            bumpWait--;

            // ---- body: doors, hood, tailgate, filler
            for (int side = 0; side < 2; side++) {
                boolean right = side == 1, o = truck.doorOpen(right);
                float a = truck.doorAngle(right, 1.0F);
                Vec3 door = truck.point(0.3, right ? 0.85 : -0.85, 1.0);
                if (!first && o && !open[side]) once(AflSounds.TRUCK_DOOR_OPEN, door, 1, 1);
                if (!first && !o && shuts(doorAngle[side], a)) once(AflSounds.TRUCK_DOOR_CLOSE, door, 1, 1);
                open[side] = o;
                doorAngle[side] = a;
            }
            boolean h = truck.hoodOpen();
            float ha = truck.hoodAngle(1.0F), ta = truck.tailgateAngle(1.0F);
            if (!first && h && !hood) once(AflSounds.TRUCK_DOOR_OPEN, truck.point(2.0, 0, 1.0), 0.8F, 0.8F);
            if (!first && !h && shuts(hoodAngle, ha)) once(AflSounds.TRUCK_DOOR_CLOSE, truck.point(2.0, 0, 1.0), 1, 0.8F);
            hood = h;
            hoodAngle = ha;
            Vec3 gate = truck.point(-2.3, 0, 0.8);
            if (!first && tailgateAngle < PickupTruckEntity.TAILGATE_OPEN - 0.5F && ta >= PickupTruckEntity.TAILGATE_OPEN - 0.01F)
                once(AflSounds.TRUCK_TAILGATE, gate, 0.85F, 0.95F);
            if (!first && !truck.tailgateOpen() && shuts(tailgateAngle, ta)) once(AflSounds.TRUCK_TAILGATE, gate, 1, 1.05F);
            tailgateAngle = ta;
            boolean f = truck.fillerOpen();
            if (!first && f != filler) once(f ? AflSounds.TRUCK_FUEL_CAP_OPEN : AflSounds.TRUCK_FUEL_CAP_CLOSE, truck.point(-1.6, -0.8, 0.9), 1, 1);
            filler = f;

            // ---- controls: the gear lever, 2H / 4H, the handbrake (the local driver's), the horn
            Vec3 lever = truck.point(0.25, -0.05, 0.75);
            double st = truck.shiftTotal();
            if (!first && st > 0 && shiftTotal <= 0) once(AflSounds.TRUCK_SHIFT, lever, 1, 0.95F + 0.1F * level.random.nextFloat());
            shiftTotal = st;
            boolean four = truck.fourWheel();
            if (!first && four != fourWheel) once(AflSounds.TRUCK_TRANSFER, lever, 1, 1);
            fourWheel = four;
            boolean hb = seat == PickupTruckLayout.DRIVER && PickupTruckEntity.clientInput.get().handbrake();
            if (!first && hb != handbrake) once(hb ? AflSounds.TRUCK_HANDBRAKE_PULL : AflSounds.TRUCK_HANDBRAKE_RELEASE, lever, 1, 1);
            handbrake = hb;
            horn.set(truck.horn() ? 1 : 0, 1);
            horn.attack = 1;
            horn.release = 0.5F;
            first = false;

            Loop[] all = {idle, mid, load, coast, crank, wind, horn, tyres[0], tyres[1], tyres[2], tyres[3]};
            for (Loop l : all) l.update(mc);
        }

        private static float pitch(double ratio) { return (float) Mth.clamp(ratio, 0.5, 2.0); }

        /** A part swinging shut crossed {@link #SHUT_AT} this tick (the slam is at the start of the shut sounds). */
        private static boolean shuts(float before, float now) { return before >= SHUT_AT && now < SHUT_AT; }

        void stop() {
            for (Loop l : new Loop[]{idle, mid, load, coast, crank, wind, horn, tyres[0], tyres[1], tyres[2], tyres[3]}) l.end();
        }

        private void once(RegistryObject<SoundEvent> sound, Vec3 at, float volume, float pitch) {
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.get(), SoundSource.NEUTRAL, volume, pitch,
                    RandomSource.create(), at.x, at.y, at.z));
        }
    }

    /** Asphalt (roads, stone, anything hard), gravel (and sand), dirt (and paths, mud, snow), grass. */
    static int surface(BlockState s) {
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL) || s.is(Blocks.MOSS_BLOCK) || s.getSoundType() == SoundType.GRASS) return GRASS;
        if (s.is(Blocks.GRAVEL) || s.is(BlockTags.SAND)) return GRAVEL;
        if (s.is(BlockTags.DIRT) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND) || s.is(BlockTags.SNOW) || s.getSoundType() == SoundType.MUD) return DIRT;
        return ASPHALT;
    }

    /**
     * A loop of one truck: started when it first gets a volume, eased toward its target each tick (attack / release per
     * tick), following its point; ended once silent for a second (and started again when needed).
     */
    private static final class Loop {
        final RegistryObject<SoundEvent> sound;
        final Supplier<Vec3> at;
        final boolean relative;
        Instance playing;
        float target, pitch = 1, volume, attack = 0.25F, release = 0.25F;
        int silent;

        Loop(RegistryObject<SoundEvent> sound, Supplier<Vec3> at, boolean relative) {
            this.sound = sound;
            this.at = at;
            this.relative = relative;
        }

        void set(float target, float pitch) {
            this.target = Mth.clamp(target, 0, 1);
            this.pitch = pitch;
        }

        void update(Minecraft mc) {
            volume += Mth.clamp(target - volume, -release, attack);
            if (volume < 0.002F && target <= 0) {
                volume = 0;
                if (++silent > 20) end();
                return;
            }
            silent = 0;
            if (playing == null || playing.isStopped()) {
                playing = new Instance(this);
                mc.getSoundManager().play(playing);
            }
        }

        void end() {
            if (playing != null) playing.finish();
            playing = null;
        }
    }

    private static final class Instance extends AbstractTickableSoundInstance {
        private final Loop loop;

        Instance(Loop loop) {
            super(loop.sound.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.loop = loop;
            this.looping = true;
            this.delay = 0;
            this.relative = loop.relative;
            this.attenuation = loop.relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
            this.volume = loop.volume;
            this.pitch = loop.pitch;
            place();
        }

        private void place() {
            if (relative) {
                x = y = z = 0;
                return;
            }
            Vec3 p = loop.at.get();
            x = p.x;
            y = p.y;
            z = p.z;
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            volume = loop.volume;
            pitch = loop.pitch;
            place();
        }

        void finish() { stop(); }
    }
}
