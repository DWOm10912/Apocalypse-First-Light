package com.antaurora.apofirstlight.client.lightning;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.light.DynamicLights;
import com.antaurora.apofirstlight.client.light.LightCone;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md) on the client. The bolt entity's client tick is AFL's
 * (mixin client.LightningBoltClientMixin): its {@link LightningFlash} (the shape from the bolt's UUID, so every player sees
 * the same flash), the sky flash per stroke (bolts within 1.5 km), thunder that arrives at the speed of sound ({@link Thunder}), the flash lighting the ground
 * (a {@link DynamicLights} light, without a shader pack only), and the bolt gone once its last stroke has faded. Drawn by {@link LightningRenderer}.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflLightning {
    /** Sound in air (m/s): thunder from the nearest part of the channel arrives this late. */
    static final double SOUND_SPEED = 343;
    /** The sky flashes (vanilla's whole-world flash) in a tick whose strokes get this bright, for bolts within SKY_RANGE (m). */
    private static final double SKY_FLASH = 0.3, SKY_RANGE = 1500;
    /**
     * The flash on the ground: a light from the main channel at this share of its height, pointing down over the whole
     * lower half (no cut-off across, below or above the aim), full light within 20 m, 6 levels at 0.7 of the reach.
     */
    private static final double LIGHT_AT = 0.3;
    private static final LightCone GLOW = new LightCone(0, 89.5, 89.5, 89.5, 280, 15, 6, 0, 20, 0, 0);
    private static final Vec3 DOWN = new Vec3(0, -1, 0), EAST = new Vec3(1, 0, 0);
    /** Bolts farther than this light nothing (blocks). */
    private static final double LIGHT_RANGE = 320;

    private static final Map<LightningBolt, LightningFlash> FLASHES = new WeakHashMap<>();
    /** Diagnostics: the last bolt's thunder and crack delays (ticks) and the client tick it first ticked on. */
    public static int lastThunderDelay = -1, lastCrackDelay = -1;

    private AflLightning() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        DynamicLights.register(AflLightning::collect);
        event.enqueueWork(LightningShaderData::register);
    }

    /** The bolt's flash (made the first time it is asked for). */
    public static LightningFlash flash(LightningBolt bolt) {
        return FLASHES.computeIfAbsent(bolt, b -> new LightningFlash(b.getUUID().getMostSignificantBits() ^ b.getUUID().getLeastSignificantBits(),
                b.getY(), AflShaderCompat.shaderPackInUse()));
    }

    /** The flashes the client knows of (render thread; removed bolts may still be in it). */
    static Map<LightningBolt, LightningFlash> flashes() {
        return FLASHES;
    }

    /** The bolt's client tick (instead of vanilla's): sounds once, sky flash, gone after the last stroke. */
    public static void clientTick(LightningBolt bolt) {
        LightningFlash f = flash(bolt);
        Minecraft mc = Minecraft.getInstance();
        if (!f.sounded) {
            f.sounded = true;
            thunder(mc, bolt, f);
        }
        double t = f.time(0);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = bolt.getX() - cam.x, dz = bolt.getZ() - cam.z;
        if (mc.level != null && f.skyPeak(t, 0.05) > SKY_FLASH && dx * dx + dz * dz < SKY_RANGE * SKY_RANGE) mc.level.setSkyFlashTime(2);
        f.age++;
        if (t > f.end()) {
            bolt.discard();
            FLASHES.remove(bolt);
        }
    }

    /** Thunder (client/lightning/Thunder) from where the main channel comes nearest the camera. */
    private static void thunder(Minecraft mc, LightningBolt bolt, LightningFlash f) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double best = Double.MAX_VALUE, nx = bolt.getX(), ny = bolt.getY(), nz = bolt.getZ();
        for (LightningChannel.Strand s : f.channel.strands) {
            if (!s.main) continue;
            for (int i = 0; i < s.count(); i++) {
                double x = bolt.getX() + s.x(i), y = bolt.getY() + s.y(i), z = bolt.getZ() + s.z(i);
                double d = cam.distanceToSqr(x, y, z);
                if (d < best) {
                    best = d;
                    nx = x;
                    ny = y;
                    nz = z;
                }
            }
        }
        lastThunderDelay = Thunder.play(mc, bolt, cam, nx, ny, nz);
        lastCrackDelay = (int) Math.round(cam.distanceTo(bolt.position()) / SOUND_SPEED * 20);
    }

    /**
     * The flash lighting the ground round the strike (render thread, the level pass), only while AFL's programs draw the
     * lights (no shader pack). A pack lights the storm from the bolt itself (Aureveil: lightningBoltPosition, lib/storm.glsl);
     * in its lamp table this light lit the ground twice, cost every pixel a shadow walk (alpha.57: 6.3 -> 8.9 ms while it
     * flashed) and would join the headlamp beams (Aureveil A, user 2026-10-18).
     */
    private static void collect(DynamicLights.Lights out, ClientLevel level, Vec3 camera, float pt) {
        if (!DynamicLights.active() || Minecraft.getInstance().options.hideLightningFlash().get()) return;
        for (Iterator<Map.Entry<LightningBolt, LightningFlash>> it = FLASHES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LightningBolt, LightningFlash> e = it.next();
            LightningBolt bolt = e.getKey();
            if (bolt.isRemoved() || bolt.level() != level) {
                it.remove();
                continue;
            }
            LightningFlash f = e.getValue();
            double t = f.time(pt);
            if (t < 0 || bolt.distanceToSqr(camera) > LIGHT_RANGE * LIGHT_RANGE) continue;
            float k = (float) f.at(t).sky;
            if (k < 0.02F) continue;
            // the main channel's point nearest LIGHT_AT of the height
            LightningChannel.Strand main = f.channel.strands.get(0);
            int at = 0;
            for (int i = 1; i < main.count(); i++)
                if (Math.abs(main.y(i) - LIGHT_AT * f.height) < Math.abs(main.y(at) - LIGHT_AT * f.height)) at = i;
            double x = bolt.getX() + main.x(at), y = bolt.getY() + main.y(at), z = bolt.getZ() + main.z(at);
            out.spot(GLOW, x, y, z, DOWN, EAST, 0.80F * k, 0.84F * k, 1.0F * k, out.occluder(x, y, z));
        }
    }

    /** A box round the whole channel, so the renderer (and Embeddium's entity culling) never drops a visible bolt. */
    public static AABB cullingBox(LightningBolt bolt) {
        double h = flash(bolt).height, side = h * 0.6;
        return new AABB(bolt.getX() - side, bolt.getY() - 4, bolt.getZ() - side, bolt.getX() + side, bolt.getY() + h, bolt.getZ() + side);
    }
}
