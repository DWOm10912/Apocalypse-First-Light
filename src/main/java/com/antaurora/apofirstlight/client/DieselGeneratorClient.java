package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Diesel standby generator V1, client side (docs/machines/diesel_standby_generator_v1.md): the exhaust at the stack's top,
 * soft puffs (client/FireFx#smoke, not vanilla pixel smoke): a dark burst when the engine catches, then a grey plume that
 * thickens with the load while it runs. 2026-10-09 (user: "出来的烟有点淡，在光影下更是看不清"): the plume was 6 cm puffs
 * at 7..20 % every 4 ticks; now 9 cm at 22..47 % every 2 ticks, mid grey (it read as nothing against the sky and grass
 * under shaders), rising faster and living longer.
 */
public final class DieselGeneratorClient {
    private DieselGeneratorClient() {
    }

    public static void tick(DieselGeneratorBlockEntity generator) {
        if (!(generator.getLevel() instanceof ClientLevel level)) return;
        DieselGeneratorBlockEntity.Mode mode = generator.mode(), before = generator.clientMode;
        generator.clientMode = mode;
        if (mode != DieselGeneratorBlockEntity.Mode.RUNNING) return;
        Vec3 top = generator.stackWorld();
        long now = level.getGameTime();
        if (before != DieselGeneratorBlockEntity.Mode.RUNNING) {   // caught: one dark burst
            for (int i = 0; i < 10; i++) FireFx.smoke(level, top.x, top.y + 0.04, top.z, 0.11F + 0.025F * i, FireFx.DIESEL_SMOKE, 0.75F, 80, 0.06 + 0.012 * i);
            generator.clientPuff = now;
            return;
        }
        float load = generator.loadFe() / DieselGeneratorBlockEntity.RATED;
        if (now - generator.clientPuff < 2) return;
        generator.clientPuff = now;
        FireFx.smoke(level, top.x, top.y + 0.03, top.z, 0.09F, 0.28F, 0.22F + 0.25F * Math.min(1F, load), 70, 0.09);
    }
}
