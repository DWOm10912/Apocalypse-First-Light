package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.client.weather.ClientWeather;
import net.minecraft.util.Mth;

/**
 * The mesh plants' wind as numbers (AFL vegetation, docs/gameplay/vegetation_v1_design.md 10.3, 12): the clock, the wind
 * now, and the same displacement the terrain vertex stage computes (VegetationWind's GLSL, kept line for line), so the
 * near layer (VegetationDynamics) moves its blades exactly as the chunk mesh's around them. The noise's hash has no sine
 * (a GPU's sine of large arguments differs from Java's): the same value on both sides.
 */
final class VegetationWindMath {
    private static final long START = System.nanoTime();

    private VegetationWindMath() {}

    /** Seconds on the wind's clock (wraps every hour, for float precision). */
    static float seconds() {
        long since = System.nanoTime() - START;
        return since / 1_000_000_000L % 3600 + since % 1_000_000_000L / 1e9F;
    }

    /** The wind now: {time, x, z (m/s, the way it blows), gust (0..1)}: the regional weather, or a light turning breeze. */
    static float[] now() {
        float seconds = seconds();
        double wx, wz, gust;
        if (ClientWeather.active()) {
            wx = ClientWeather.windX();
            wz = ClientWeather.windZ();
            gust = Math.min(1, ClientWeather.gust());
        } else {
            double a = 0.9 + 0.6 * Math.sin(seconds / 97.0);
            wx = Math.cos(a) * 2.5;
            wz = Math.sin(a) * 2.5;
            gust = 0.3;
        }
        return new float[]{seconds, (float) wx, (float) wz, (float) gust};
    }

    private static float fract(float x) {
        return x - Mth.floor(x);
    }

    /** GLSL afl_vegHash: a hash of a point, 0..1, without a sine. */
    static float hash(float px, float py) {
        float x = fract(px * 0.1031F), y = fract(py * 0.1031F), z = fract(px * 0.1031F);
        float d = x * (y + 33.33F) + y * (z + 33.33F) + z * (x + 33.33F);
        x += d;
        y += d;
        z += d;
        return fract((x + y) * z);
    }

    /** GLSL afl_vegNoise: smooth value noise, 0..1. */
    static float noise(float px, float py) {
        float ix = (float) Math.floor(px), iy = (float) Math.floor(py), fx = px - ix, fy = py - iy;
        float ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return Mth.lerp(uy, Mth.lerp(ux, a, b), Mth.lerp(ux, c, d));
    }

    /**
     * GLSL afl_vegWind's displacement of a point at (wx, wz) (world position wrapped as the shader's: camera-relative plus
     * the camera modulo 4096) at height h (0 foot .. 1 tip along the plant) in the wind {t, x, z, gust}, scaled by the sheet
     * column's reach (PlantShape#WIND): into out (dx dy dz).
     */
    static void displace(float wx, float wz, float h, float[] wind, float scale, float[] out) {
        out[0] = 0; out[1] = 0; out[2] = 0;
        if (h <= 0.001F) return;
        float vx = wind[1], vz = wind[2], speed = (float) Math.sqrt(vx * vx + vz * vz);
        float dx = speed > 0.05F ? vx / speed : 0.6F, dz = speed > 0.05F ? vz / speed : 0.8F;
        float t = wind[0];
        float drift = t * (0.45F + 0.06F * speed), rx = dx * drift, rz = dz * drift;
        float gusts = 0.65F * noise(wx * 0.28F - rx * 0.28F, wz * 0.28F - rz * 0.28F)
                + 0.35F * noise(wx * 0.7F - rx * 0.7F + 17, wz * 0.7F - rz * 0.7F + 17);
        float swell = noise(wx * 0.05F - rx * 0.05F + 41, wz * 0.05F - rz * 0.05F + 41);
        float flutter = (float) Math.sin(t * 5.5F + noise(wx * 1.3F, wz * 1.3F) * 6.2832F);
        float calm = Mth.clamp(speed / 10, 0, 1);
        float lean = (0.015F + 0.07F * calm) * (0.3F + 0.8F * gusts + 0.3F * wind[3] * swell);
        float k = h * h, side = k * 0.012F * flutter * (0.3F + calm);
        out[0] = (dx * k * lean - dz * side) * scale;
        out[2] = (dz * k * lean + dx * side) * scale;
        out[1] = -k * lean * 0.3F * scale;
    }
}
