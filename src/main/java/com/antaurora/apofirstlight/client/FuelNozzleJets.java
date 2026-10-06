package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The fuel nozzles' jets on this client (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"). Every tick, for each player
 * whose nozzle runs (holding use, and its dispenser's synced flow flag on), the jet of that dispenser nozzle gets two new
 * parcels from the held spout (FuelDispenserRenderer#spout) along the player's view at the nozzle speed, with a little
 * turbulence; then every jet flies a tick (fluid/LiquidJet). Where a parcel lands it splashes a droplet or two; the
 * stream's falling end sheds droplets; when a nozzle stops, its tail falls away and the spout drips three times.
 * FuelDispenserRenderer draws the jets. The local player's own jets report where they land (AflNetwork#sendFuelSprayHits):
 * the server lays the stains there (fluid/FuelSpills, drawn by ClientFuelStains), so they lie right under the stream seen.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuelNozzleJets {
    /** Seconds a parcel flies at most. */
    private static final double MAX_AGE = 3.0;
    /** The nozzle's wobble: a slow random drift (a share of the speed) plus a little jitter per parcel. */
    private static final double WOBBLE = 0.03, JITTER = 0.003;
    private static final double SHED_AGE = 0.2, SHED_CHANCE = 0.12;
    private static final int DRIPS = 3, DRIP_EVERY = 6;

    private record Key(BlockPos dispenser, int nozzle) {}

    public static final class Jet {
        public final LiquidJet jet = new LiquidJet(LiquidJet.EARTH_GRAVITY, MAX_AGE);
        public final FuelDispenserBlock.Grade grade;
        private final Fluid fluid;
        public boolean flowing;
        @Nullable
        private UUID holder;
        @Nullable
        private Vec3 lastSpout;
        private Vec3 drift = Vec3.ZERO;
        private int drips, dripTimer;

        private Jet(FuelDispenserBlock.Grade grade) {
            this.grade = grade;
            this.fluid = FuelDispenserBlockEntity.fuel(grade);
        }

        public Fluid fluid() {
            return fluid;
        }

        @Nullable
        public UUID holder() {
            return holder;
        }

        private boolean idle() {
            return !flowing && jet.isEmpty() && drips == 0;
        }
    }

    private static final Map<Key, Jet> JETS = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private FuelNozzleJets() {
    }

    @Nullable
    public static Jet get(BlockPos dispenser, FuelDispenserBlock.Nozzle nozzle) {
        return JETS.get(new Key(dispenser, nozzle.ordinal()));
    }

    /** True while any jet of this dispenser is still to be drawn. */
    public static boolean any(BlockPos dispenser) {
        for (Key key : JETS.keySet()) if (key.dispenser().equals(dispenser)) return true;
        return false;
    }

    /** The nozzle this player is spraying (using it, and the dispenser says it runs), or null. */
    @Nullable
    public static FuelDispenserBlock.Nozzle spraying(Player player) {
        if (!player.isUsingItem()) return null;
        ItemStack stack = player.getUseItem();
        BlockPos dispenser = FuelNozzleItem.dispenserOf(stack);
        int nozzle = FuelNozzleItem.nozzleIndexOf(stack);
        if (dispenser == null || nozzle < 0 || nozzle >= FuelDispenserBlock.Nozzle.values().length) return null;
        FuelDispenserBlock.Nozzle which = FuelDispenserBlock.Nozzle.values()[nozzle];
        return player.level().getBlockEntity(dispenser) instanceof FuelDispenserBlockEntity entity && entity.flowing(which) ? which : null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            JETS.clear();
            trackedLevel = level;
        }
        if (level == null || minecraft.player == null || minecraft.isPaused()) return;
        long now = level.getGameTime();
        RandomSource random = level.random;
        // emit: every player whose nozzle runs
        Set<Key> running = new HashSet<>();
        for (Player player : level.players()) {
            FuelDispenserBlock.Nozzle nozzle = spraying(player);
            if (nozzle == null) continue;
            Key key = new Key(FuelNozzleItem.dispenserOf(player.getUseItem()), nozzle.ordinal());
            Jet jet = JETS.computeIfAbsent(key, k -> new Jet(nozzle.grade));
            running.add(key);
            Vec3 spout = FuelDispenserRenderer.spout(player, 1.0F), direction = player.getViewVector(1.0F);
            Vec3 from = jet.lastSpout == null ? spout : jet.lastSpout;
            // the wobble drifts (a damped random walk), so neighbouring parcels fly nearly the same path: a smooth stream,
            // not the zigzag independent kicks made of its falling end (user video, 2026-10-05)
            jet.drift = jet.drift.scale(0.85).add(random.triangle(0, 0.35), random.triangle(0, 0.35), random.triangle(0, 0.35));
            for (int k = 0; k < 2; k++) {   // two a tick: the later one at the spout now, the earlier half a tick on its way
                double s = FuelDispenserBlockEntity.NOZZLE_SPEED;
                Vec3 velocity = direction.scale(s).add(jet.drift.scale(WOBBLE * s))
                        .add(random.triangle(0, JITTER * s), random.triangle(0, JITTER * s), random.triangle(0, JITTER * s));
                jet.jet.emit(k == 0 ? from.lerp(spout, 0.5) : spout, velocity, k == 0 ? LiquidJet.STEP : 0.0);
            }
            jet.flowing = true;
            jet.holder = player.getUUID();
            jet.lastSpout = spout;
            jet.drips = 0;
        }
        // fly, splash, shed, drip
        TextureAtlasSprite[] sprite = new TextureAtlasSprite[1];
        JETS.forEach((key, jet) -> {
            FluidStack stack = new FluidStack(jet.fluid, 1000);
            IClientFluidTypeExtensions fluidClient = IClientFluidTypeExtensions.of(jet.fluid);
            sprite[0] = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(fluidClient.getStillTexture(stack));
            int rgb = fluidClient.getTintColor(stack) & 0xFFFFFF;
            Player holder = jet.holder == null ? null : level.getPlayerByUUID(jet.holder);
            boolean mine = holder == minecraft.player;
            java.util.List<Vec3> landedAt = new java.util.ArrayList<>();
            java.util.List<net.minecraft.core.Direction> landedOn = new java.util.ArrayList<>();
            if (jet.flowing && !running.contains(key)) {   // stopped: the tail falls away, the spout drips
                jet.flowing = false;
                jet.jet.gap();
                jet.lastSpout = null;
                jet.drips = DRIPS;
                jet.dripTimer = DRIP_EVERY / 2;
            }
            jet.jet.tick(level, holder != null ? holder : minecraft.player, (parcel, hit) -> {
                if (mine) {
                    landedAt.add(hit.getLocation());
                    landedOn.add(hit.getDirection());
                }
                if (random.nextFloat() < 0.6F) {
                    Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                    Vec3 bounce = parcel.vel.subtract(n.scale(1.6 * parcel.vel.dot(n))).scale(0.25)
                            .add(random.triangle(0, 0.6), random.triangle(0, 0.4), random.triangle(0, 0.6));
                    LiquidDroplet.spawn(level, hit.getLocation().add(n.scale(0.03)), bounce, sprite[0], rgb, 0.02F + random.nextFloat() * 0.015F);
                }
            });
            if (!landedAt.isEmpty()) com.antaurora.apofirstlight.network.AflNetwork.sendFuelSprayHits(key.dispenser(), key.nozzle(), landedAt, landedOn);
            for (LiquidJet.Parcel parcel : jet.jet.parcels()) {
                if (parcel.age > SHED_AGE && random.nextFloat() < SHED_CHANCE) {
                    LiquidDroplet.spawn(level, parcel.pos, parcel.vel.scale(0.9).add(random.triangle(0, 0.3), 0, random.triangle(0, 0.3)),
                            sprite[0], rgb, 0.015F + random.nextFloat() * 0.01F);
                }
            }
            if (jet.drips > 0 && --jet.dripTimer <= 0) {
                jet.drips--;
                jet.dripTimer = DRIP_EVERY;
                if (holder != null && FuelNozzleItem.dispenserOf(holder.getMainHandItem()) != null
                        && FuelNozzleItem.nozzleIndexOf(holder.getMainHandItem()) == key.nozzle()) {
                    LiquidDroplet.spawn(level, FuelDispenserRenderer.spout(holder, 1.0F), new Vec3(0, -0.2, 0), sprite[0], rgb, 0.018F);
                }
            }
        });
        JETS.values().removeIf(Jet::idle);
    }
}
