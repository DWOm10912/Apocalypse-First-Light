package com.antaurora.apofirstlight.weapon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-only sustained-fire spread for fire modes that configure fire.mode_overrides.<mode>.bloom.
 * One transient value per player; it decays lazily from the elapsed server ticks at the next shot, so there is no
 * per-tick work. The first shot of a fresh string adds nothing (base / stance / ADS accuracy only). */
public final class NativeFireBloom {
    private static final class State { ResourceLocation gun; NativeFireMode mode; double degrees; long tick; }
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private NativeFireBloom() {}

    /** Extra spread half-angle (degrees) for this accepted shot; also records the shot's own contribution. */
    public static double shot(ServerPlayer player, NativeGunDefinition definition, NativeFireMode mode) {
        var bloom = definition.fire().bloom(mode);
        if (bloom == null) { STATES.remove(player); return 0; }
        long now = player.server.getTickCount();
        var s = STATES.get(player);
        double current = 0;
        if (s != null && s.gun.equals(definition.id()) && s.mode == mode && now >= s.tick)
            current = s.degrees * Math.exp(-(now - s.tick) / bloom.recoveryTicks());
        else if (s == null) STATES.put(player, s = new State());
        s.gun = definition.id(); s.mode = mode; s.tick = now;
        s.degrees = Math.min(bloom.maxDegrees(), current + bloom.perShotDegrees());
        return current;
    }
}
