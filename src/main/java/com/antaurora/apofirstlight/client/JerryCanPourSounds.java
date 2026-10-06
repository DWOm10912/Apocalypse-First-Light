package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
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
 * The jerry can pour's sounds (2026-10-06, docs/models/fuel_containers_v1.md "声音"; tools/build-jerry-can-pour-sounds-v1.mjs),
 * for every player in hearing range pouring a can into a fill cover (item/FuelCanItem's pour tag), on the first-person
 * clips' clock (client/JerryCanFirstPerson starts them on the same tag):
 * <ul>
 *   <li>the pour starts: jerry_can_open (the cap worked loose and lifted, timed to pour_start);</li>
 *   <li>while the fuel runs (FuelCanItem.POUR_DELAY on, fuel left): jerry_can_pour looping, fading in over
 *   {@link #FADE_IN} ticks and out over {@link #FADE_OUT};</li>
 *   <li>the pour stops (another click, the can empty, the tank full...): jerry_can_close (timed to pour_end), and an opening
 *   still playing is cut off.</li>
 * </ul>
 * All at the can's spout as the pour pose holds it (FuelCanItem.spout). A player first heard mid-pour gets no opening.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class JerryCanPourSounds {
    private static final int FADE_IN = 3, FADE_OUT = 4;
    private static final double AUDIBLE = 16.0;
    /** A pour first seen later than this (ticks) was already under way: no opening. */
    private static final long LATE = 10;

    private static final class State {
        boolean pouring;
        @Nullable
        SoundInstance open;
        @Nullable
        PourLoop loop;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private JerryCanPourSounds() {
    }

    /** Fuel is running from this player's can: the cap is off and the can tipped, and there is fuel left. */
    private static boolean running(Player player) {
        ItemStack stack = player.getMainHandItem();
        return FuelCanItem.pourTicks(stack, player.level()) >= FuelCanItem.POUR_DELAY && !FuelCanItem.fluid(stack).isEmpty();
    }

    private static SoundInstance at(SoundEvent sound, Player player) {
        Vec3 spout = FuelCanItem.spout(player, 1.0F);
        return new SimpleSoundInstance(sound, SoundSource.PLAYERS, 1.0F, 1.0F, SoundInstance.createUnseededRandom(), spout.x, spout.y, spout.z);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            STATES.values().forEach(s -> { if (s.loop != null) s.loop.end(); });
            STATES.clear();
            trackedLevel = level;
        }
        if (level == null || minecraft.player == null) return;
        var sounds = minecraft.getSoundManager();
        STATES.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        for (Player player : level.players()) {
            if (player.distanceToSqr(minecraft.player) > AUDIBLE * AUDIBLE) continue;
            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
            ItemStack stack = player.getMainHandItem();
            boolean pouring = FuelCanItem.isPouring(stack);
            if (pouring && !state.pouring && FuelCanItem.pourTicks(stack, level) <= LATE) {
                state.open = at(AflSounds.JERRY_CAN_OPEN.get(), player);
                sounds.play(state.open);
            } else if (!pouring && state.pouring) {
                if (state.open != null && sounds.isActive(state.open)) sounds.stop(state.open);
                state.open = null;
                sounds.play(at(AflSounds.JERRY_CAN_CLOSE.get(), player));
            }
            state.pouring = pouring;
            if (state.loop != null && state.loop.isStopped()) state.loop = null;
            if (state.loop == null && pouring && running(player)) {
                state.loop = new PourLoop(level, player);
                sounds.play(state.loop);
            }
        }
    }

    /** The fuel running, at the spout, while it runs. */
    private static final class PourLoop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final Player player;

        private PourLoop(ClientLevel level, Player player) {
            super(AflSounds.JERRY_CAN_POUR.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 0.0F;
            place();
        }

        private void place() {
            Vec3 spout = FuelCanItem.spout(player, 1.0F);
            x = spout.x;
            y = spout.y;
            z = spout.z;
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
            place();
            if (FuelCanItem.isPouring(player.getMainHandItem()) && running(player)) volume = Math.min(1.0F, volume + 1.0F / FADE_IN);
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
