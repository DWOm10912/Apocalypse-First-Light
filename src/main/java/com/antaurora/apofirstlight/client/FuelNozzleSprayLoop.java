package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The fuel nozzle's spray loop (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"; sounds/fuel_nozzle/spray.ogg,
 * tools/build-fuel-nozzle-spray-sound-v1.mjs): one looping sound per player whose nozzle runs (holding use, and the
 * dispenser's synced flow flag for that nozzle on), at the hand, fading in over {@link #FADE_IN} ticks and out over
 * {@link #FADE_OUT} (no trigger sounds). Diesel plays a little lower. Any player in hearing range, not only the local one.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuelNozzleSprayLoop {
    private static final int FADE_IN = 2, FADE_OUT = 3;
    private static final float DIESEL_PITCH = 0.9F;
    private static final double AUDIBLE = 16.0;
    private static final Map<UUID, SprayLoop> ACTIVE = new HashMap<>();

    private FuelNozzleSprayLoop() {
    }

    /** The nozzle grade this player is spraying, or null (FuelNozzleJets#spraying). */
    @Nullable
    private static FuelDispenserBlock.Grade spraying(Player player) {
        FuelDispenserBlock.Nozzle nozzle = FuelNozzleJets.spraying(player);
        return nozzle == null ? null : nozzle.grade;
    }

    /** About the hand: ahead of the eye, a little down. */
    private static Vec3 hand(Player player) {
        return player.getEyePosition().add(player.getViewVector(1.0F).scale(0.5)).add(0, -0.4, 0);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ACTIVE.values().removeIf(SprayLoop::isStopped);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            ACTIVE.values().forEach(SprayLoop::end);
            ACTIVE.clear();
            return;
        }
        for (Player player : level.players()) {
            if (ACTIVE.containsKey(player.getUUID()) || player.distanceToSqr(minecraft.player) > AUDIBLE * AUDIBLE) continue;
            FuelDispenserBlock.Grade grade = spraying(player);
            if (grade == null) continue;
            SprayLoop sound = new SprayLoop(level, player, grade);
            minecraft.getSoundManager().play(sound);
            ACTIVE.put(player.getUUID(), sound);
        }
    }

    private static final class SprayLoop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final Player player;

        private SprayLoop(ClientLevel level, Player player, FuelDispenserBlock.Grade grade) {
            super(AflSounds.FUEL_NOZZLE_SPRAY.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 0.0F;
            this.pitch = grade == FuelDispenserBlock.Grade.DIESEL ? DIESEL_PITCH : 1.0F;
            Vec3 at = hand(player);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (Minecraft.getInstance().level != level || player.isRemoved()) {
                stop();
                return;
            }
            Vec3 at = hand(player);
            x = at.x;
            y = at.y;
            z = at.z;
            if (spraying(player) != null) volume = Math.min(1.0F, volume + 1.0F / FADE_IN);
            else if ((volume -= 1.0F / FADE_OUT) <= 0.0F) {
                volume = 0.0F;
                stop();
            }
        }

        private void end() {
            stop();
        }
    }
}
