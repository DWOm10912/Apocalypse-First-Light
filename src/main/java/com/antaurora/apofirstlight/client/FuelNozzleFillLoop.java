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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
 * The fill sound of a fuel nozzle in an opening (2026-10-10, fluid/NozzleFill): one loop per player whose nozzle's fuel runs
 * (the dispenser's synced fill flag), at the opening, fading in over {@link #FADE_IN} ticks and out over {@link #FADE_OUT}.
 * Its pitch rises as the opening fills (the dispenser's synced share: an emptier can rings lower), diesel a little lower.
 * Any player in hearing range. The in, shut-off and out sounds are the server's.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuelNozzleFillLoop {
    private static final int FADE_IN = 3, FADE_OUT = 4;
    private static final float DIESEL_PITCH = 0.9F, EMPTY_PITCH = 0.9F, FULL_PITCH = 1.12F;
    private static final double AUDIBLE = 16.0;
    private static final Map<UUID, FillLoop> ACTIVE = new HashMap<>();

    private FuelNozzleFillLoop() {
    }

    /** The player's nozzle while its fuel runs into an opening: [dispenser, nozzle], or null. */
    @Nullable
    private static Object[] filling(Player player) {
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FuelNozzleItem)) return null;
        FuelDispenserBlockEntity dispenser = FuelNozzleItem.dispenser(stack, player.level());
        FuelDispenserBlock.Nozzle nozzle = FuelNozzleItem.nozzle(stack);
        return dispenser != null && nozzle != null && dispenser.inserted(nozzle) != null && dispenser.filling(nozzle) ? new Object[]{dispenser, nozzle} : null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ACTIVE.values().removeIf(FillLoop::isStopped);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            ACTIVE.values().forEach(FillLoop::end);
            ACTIVE.clear();
            return;
        }
        for (Player player : level.players()) {
            if (ACTIVE.containsKey(player.getUUID()) || player.distanceToSqr(minecraft.player) > AUDIBLE * AUDIBLE || filling(player) == null) continue;
            FillLoop sound = new FillLoop(level, player);
            minecraft.getSoundManager().play(sound);
            ACTIVE.put(player.getUUID(), sound);
        }
    }

    private static final class FillLoop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final Player player;

        private FillLoop(ClientLevel level, Player player) {
            super(AflSounds.FUEL_NOZZLE_FILL.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 0.0F;
            follow();
        }

        /** At the opening, the pitch from how full it is and the fuel. */
        private boolean follow() {
            Object[] f = filling(player);
            if (f == null) return false;
            FuelDispenserBlockEntity dispenser = (FuelDispenserBlockEntity) f[0];
            FuelDispenserBlock.Nozzle nozzle = (FuelDispenserBlock.Nozzle) f[1];
            NozzleFillView.Way way = NozzleFillView.way(dispenser, nozzle, 1.0F);
            Vec3 at = way != null ? way.opening() : player.getEyePosition();
            x = at.x;
            y = at.y;
            z = at.z;
            float grade = nozzle.grade == FuelDispenserBlock.Grade.DIESEL ? DIESEL_PITCH : 1.0F;
            pitch = grade * (EMPTY_PITCH + (FULL_PITCH - EMPTY_PITCH) * dispenser.fillShare(nozzle));
            return true;
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
            if (follow()) volume = Math.min(1.0F, volume + 1.0F / FADE_IN);
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
