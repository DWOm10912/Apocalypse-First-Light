package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.NativeTrailGeometry;
import com.antaurora.apofirstlight.weapon.NativeTrailProfile;
import net.minecraft.world.phys.Vec3;

/** Geometry-only checks; not a substitute for an in-world visual gate. */
public final class NativeTrailChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var p = NativeTrailProfile.SUBTLE_PISTOL;
        for (double distance : new double[]{.1, .4, .41, 1, 10, 30, 64, 128}) {
            double lastHead = 0;
            for (int frame = 0; frame < 1000; frame++) {
                double age = frame / 100.0;
                var s = NativeTrailGeometry.segment(distance, age, p);
                if (s == null) continue;
                require(s.tail() >= .4 && s.head() <= distance, "muzzle/end clamp");
                require(s.head() - s.tail() <= 3.0000001, "not a full hitscan beam");
                require(s.head() >= lastHead, "forward movement");
                require(s.alpha() >= 0 && s.alpha() <= 1, "fade");
                lastHead = s.head();
            }
            require(NativeTrailGeometry.segment(distance, 6, p) == null, "safety expiry");
            require(NativeTrailGeometry.segment(distance, distance / 18 + .151, p) == null, "hit expiry");
        }
        require(NativeTrailGeometry.segment(.4, .01, p) == null, "inside muzzle hide");
        require(NativeTrailGeometry.segment(Double.NaN, 0, p) == null, "NaN rejection");
        for (Vec3 d : new Vec3[]{new Vec3(1,0,0), new Vec3(-1,0,0), new Vec3(0,0,1),
                new Vec3(0,0,-1), new Vec3(0,1,0), new Vec3(0,-1,0), new Vec3(.2,.5,.3).normalize()}) {
            for (Vec3 camera : new Vec3[]{d, d.scale(-1), Vec3.ZERO, d.add(1e-10,0,0), new Vec3(3,2,7)}) {
                Vec3 side = NativeTrailGeometry.side(d, camera);
                require(NativeTrailGeometry.finite(side) && Math.abs(side.length() - 1) < 1e-8, "stable parallel basis");
                require(Math.abs(side.dot(d)) < 1e-8, "orthogonal width");
            }
            Vec3 muzzle = new Vec3(8,2,-7), end = muzzle.add(d.scale(30));
            var s = NativeTrailGeometry.segment(30, 30.0/18, p);
            require(muzzle.add(d.scale(s.head())).distanceTo(end) < 1e-8, "all headings exact endpoint");
        }
        var type = com.antaurora.apofirstlight.network.AflNetwork.NativeShotFxS2CPacket.class;
        var encode = type.getDeclaredMethod("encode", type, net.minecraft.network.FriendlyByteBuf.class);
        var decode = type.getDeclaredMethod("decode", net.minecraft.network.FriendlyByteBuf.class);
        encode.setAccessible(true); decode.setAccessible(true);
        var packet = new com.antaurora.apofirstlight.network.AflNetwork.NativeShotFxS2CPacket(
                42, 999L, new Vec3(12345.125, -32.5, -900.75),123456L);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            encode.invoke(null, packet, buffer);
            require(packet.equals(decode.invoke(null, buffer)), "server endpoint packet roundtrip");
            require(buffer.readableBytes() == 0, "packet consumed");
        } finally { buffer.release(); }
        System.out.println("PASS: moving segment, near/end clamp, lifetime, six headings, parallel basis, packet endpoint roundtrip");
    }
}
