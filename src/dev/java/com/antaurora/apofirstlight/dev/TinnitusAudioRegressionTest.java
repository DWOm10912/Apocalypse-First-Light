package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.client.ExplosionTinnitusEnvelope;
import com.antaurora.apofirstlight.explosion.ExplosionTinnitusProfile;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

/** Compile/run unchanged against both audited pre-patch and live classes; fingerprints must match. */
public final class TinnitusAudioRegressionTest {
    public static void main(String[] args) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var state = new ExplosionTinnitusEnvelope();
        var random = new Random(741937L);
        ByteBuffer sample = ByteBuffer.allocate(24);
        for (int tick = 0; tick < 100_000; tick++) {
            int result = -1;
            if (random.nextInt(4) == 0) {
                float severity = ExplosionTinnitusProfile.severity(4, random.nextDouble() * 16);
                result = state.trigger(severity).ordinal();
            }
            if (tick % 359 == 0) state.clear();
            state.tick();
            sample.clear();
            sample.putInt(tick).putInt(result).putInt(state.remainingTicks())
                    .putInt(Float.floatToRawIntBits(state.volume()))
                    .putInt(Float.floatToRawIntBits(state.severity())).putInt(state.active() ? 1 : 0);
            digest.update(sample.array());
        }
        System.out.println(HexFormat.of().formatHex(digest.digest()));
    }
}
