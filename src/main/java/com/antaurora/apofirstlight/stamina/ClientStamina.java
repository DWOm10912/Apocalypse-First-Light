package com.antaurora.apofirstlight.stamina;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/**
 * The client side of Stamina V1: the own state from the server (HUD, sprint block, weapon sway, dig speed) and the
 * breathing. Breaths are single nasal breaths, one random variant of the light or heavy set at a time, at a random
 * pitch and interval (light every 2.4-3.2 s, heavy every 1.0-1.4 s), following the breathing player; the own level comes
 * with the own state, other players' levels from their keep-alive (dropped after 50 ticks without one).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientStamina {
    private static final int REMOTE_TIMEOUT = 50;
    private static StaminaPackets.State state;
    private record Remote(byte level, long seen) {}
    private record Schedule(byte level, long next) {}
    private static final Map<Integer, Remote> REMOTE = new HashMap<>();
    private static final Map<Integer, Schedule> SCHEDULE = new HashMap<>();
    private ClientStamina() {}

    public static StaminaPackets.State state() { return state; }
    public static boolean winded() { return state != null && state.winded(); }
    /** Weapon sway amplitude × this (NativeWeaponSway). */
    public static float swayScale() { return state == null ? 1f : state.swayScale(); }

    static void accept(StaminaPackets.State packet) { state = packet; }
    static void accept(StaminaPackets.Breath packet) {
        var level = Minecraft.getInstance().level;
        if (packet.level() <= 0 || level == null) REMOTE.remove(packet.entityId());
        else REMOTE.put(packet.entityId(), new Remote(packet.level(), level.getGameTime()));
    }

    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null; REMOTE.clear(); SCHEDULE.clear();
    }

    /** Winded digging is slower; the server applies the same factor (PlayerStamina#dig). */
    @SubscribeEvent public static void dig(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof LocalPlayer && state != null && state.winded())
            event.setNewSpeed(event.getNewSpeed() * state.digScale());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { REMOTE.clear(); SCHEDULE.clear(); return; }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        Map<Integer, Byte> levels = new HashMap<>();
        if (state != null && state.breath() > 0) levels.put(mc.player.getId(), state.breath());
        REMOTE.entrySet().removeIf(e -> now - e.getValue().seen() > REMOTE_TIMEOUT || now < e.getValue().seen());
        REMOTE.forEach((id, remote) -> { if (id != mc.player.getId()) levels.put(id, remote.level()); });
        SCHEDULE.keySet().removeIf(id -> !levels.containsKey(id));
        levels.forEach((id, level) -> {
            Entity entity = mc.level.getEntity(id);
            if (entity == null || !entity.isAlive() || entity.isEyeInFluid(FluidTags.WATER)) { SCHEDULE.remove(id); return; }
            var schedule = SCHEDULE.get(id);
            // starting, or getting heavier: the first breath comes soon
            if (schedule == null || level > schedule.level()) { SCHEDULE.put(id, new Schedule(level, now + 6)); return; }
            if (now < schedule.next()) { if (level != schedule.level()) SCHEDULE.put(id, new Schedule(level, schedule.next())); return; }
            var random = mc.level.random;
            var sound = level >= 2 ? AflSounds.STAMINA_BREATH_HEAVY.get() : AflSounds.STAMINA_BREATH_LIGHT.get();
            mc.getSoundManager().play(new EntityBoundSoundInstance(sound, SoundSource.PLAYERS, 1.0f,
                    0.97f + random.nextFloat() * 0.06f, entity, random.nextLong()));
            int interval = level >= 2 ? 20 + random.nextInt(9) : 48 + random.nextInt(17);
            SCHEDULE.put(id, new Schedule(level, now + interval));
        });
    }
}
