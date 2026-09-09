import com.antaurora.apofirstlight.weapon.client.NativeFlashLifetime;

public final class NativeFlashLifetimeTest {
    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        for (int fps : new int[]{30, 60, 120, 300, 600, 1000}) {
            var flash = new NativeFlashLifetime();
            long start = 2_000_000_000L;
            require(flash.attachedAge(start, 1) == -1, "No tail before snapshot");
            require(flash.presentSnapshot(start, 1), "First snapshot");
            require(!flash.presentSnapshot(start + 1, 1), "Duplicate world pass");
            require(flash.attachedAge(start + 1, 1) == -1, "No double flash on first frame");
            int visible = 1;
            for (int i = 1; i <= fps; i++) {
                long nanos = start + Math.round(i * 1_000_000_000.0 / fps);
                float age = flash.attachedAge(nanos, i + 1);
                if (nanos - start < NativeFlashLifetime.DURATION_NANOS) {
                    require(age >= 0 && age < 1, "Visible throughout 50 ms at " + fps);
                    visible++;
                    require(flash.attachedAge(nanos, i + 1) == -1, "Duplicate hand pass");
                } else require(age == -1 && flash.expired(nanos), "Expired at " + fps);
                require(!flash.presentSnapshot(nanos, i + 1), "Must not replay frozen world flash");
            }
            System.out.println("PASS fps=" + fps + " visibleFrames=" + visible + " durationMs=50");
        }
        var flash = new NativeFlashLifetime();
        flash.presentSnapshot(0, 5);
        require(!flash.expired(49_999_999L), "Before boundary");
        require(flash.expired(50_000_000L), "Exact boundary");
        require(flash.attachedAge(100_000_000L, 6) == -1, "Long frame cannot extend tail");
        var next = new NativeFlashLifetime();
        require(next.presentSnapshot(100_000_000L, 6), "Independent next shot");
        require(!next.expired(100_000_001L), "Next shot gets own lifetime");
        System.out.println("PASS boundaries, stalled frame, independent shots");
    }
}
