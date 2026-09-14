package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.antaurora.apofirstlight.worldgen.core.ChunkWriteBounds;
import static com.antaurora.apofirstlight.worldgen.core.CoreContractTest.rejects;

/** Exhaustive small-domain set oracle plus integer-limit regression. No world bootstrap. */
public final class BoundsContractTest {
    private static int checks;
    private static void require(boolean value) {
        checks++;
        if (!value) throw new AssertionError("WG-02 bounds check " + checks);
    }
    private static Set<String> points(Bounds3i b) {
        Set<String> result = new HashSet<>();
        for (int x = b.minX(); x < b.maxXExclusive(); x++)
            for (int y = b.minY(); y < b.maxYExclusive(); y++)
                for (int z = b.minZ(); z < b.maxZExclusive(); z++)
                    result.add(x + "," + y + "," + z);
        return result;
    }
    public static void main(String[] args) {
        var boxes = new ArrayList<Bounds3i>();
        for (int x = -1; x <= 1; x++) for (int xx = x; xx <= 1; xx++)
            for (int y = -1; y <= 1; y++) for (int yy = y; yy <= 1; yy++)
                for (int z = -1; z <= 1; z++) for (int zz = z; zz <= 1; zz++)
                    boxes.add(new Bounds3i(x, y, z, xx, yy, zz));
        for (var a : boxes) {
            var pa = points(a);
            require(a.isEmpty() == pa.isEmpty());
            require(a.width() == a.maxXExclusive() - a.minX());
            require(a.height() == a.maxYExclusive() - a.minY());
            require(a.depth() == a.maxZExclusive() - a.minZ());
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                require(a.contains(x, y, z) == pa.contains(x + "," + y + "," + z));
                require(a.xz().contains(x, z) == (x >= a.minX() && x < a.maxXExclusive() && z >= a.minZ() && z < a.maxZExclusive()));
                require(a.yRange().contains(y) == (y >= a.minY() && y < a.maxYExclusive()));
            }
            for (var b : boxes) {
                var common = new HashSet<>(pa);
                common.retainAll(points(b));
                require(a.intersects(b) == !common.isEmpty());
                require(a.intersects(b) == b.intersects(a));
                require(points(a.intersection(b)).equals(common));
                require(a.intersection(b).equals(b.intersection(a)));
                var writes = new ChunkWriteBounds(a, b);
                require(writes.effective().equals(a.intersection(b)));
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
                    require(writes.canWrite(x, y, z) == common.contains(x + "," + y + "," + z));
                var ax = a.xz();
                var bx = b.xz();
                boolean overlap = false;
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    boolean expected = ax.contains(x, z) && bx.contains(x, z);
                    overlap |= expected;
                    require(ax.intersection(bx).contains(x, z) == expected);
                }
                require(ax.intersects(bx) == overlap);
                require(ax.intersection(bx).equals(bx.intersection(ax)));
                boolean yOverlap = false;
                for (int y = -1; y <= 1; y++) yOverlap |= a.yRange().contains(y) && b.yRange().contains(y);
                require(a.yRange().intersects(b.yRange()) == yOverlap);
            }
        }
        int min = Integer.MIN_VALUE, max = Integer.MAX_VALUE;
        var full = new Bounds3i(min, min, min, max, max, max);
        require(full.width() == 4294967295L && full.height() == 4294967295L && full.depth() == 4294967295L);
        require(full.xz().width() == 4294967295L && full.xz().depth() == 4294967295L && full.yRange().height() == 4294967295L);
        require(full.contains(min, -1000, min) && !full.contains(0, max, 0));
        require(new BoundsXZ(-10, -10, -5, -5).contains(-6, -6));
        require(new BoundsXZ(0, 0, 10, 10).expand(2).equals(new BoundsXZ(-2, -2, 12, 12)));
        require(full.xz().expand(0).equals(full.xz()));
        require(new BoundsXZ(5, 5, 5, 5).expand(1).equals(new BoundsXZ(4, 4, 6, 6)));
        rejects(IllegalArgumentException.class, () -> full.xz().expand(-1));
        for (BoundsXZ edge : new BoundsXZ[]{new BoundsXZ(min, 0, 0, 1), new BoundsXZ(0, min, 1, 0),
                new BoundsXZ(0, 0, max, 1), new BoundsXZ(0, 0, 1, max)})
            rejects(ArithmeticException.class, () -> edge.expand(1));
        rejects(IllegalArgumentException.class, () -> new YRange(1, 0));
        rejects(IllegalArgumentException.class, () -> new BoundsXZ(1, 0, 0, 1));
        rejects(IllegalArgumentException.class, () -> new BoundsXZ(0, 1, 1, 0));
        rejects(IllegalArgumentException.class, () -> new Bounds3i(1, 0, 0, 0, 1, 1));
        rejects(IllegalArgumentException.class, () -> new Bounds3i(0, 1, 0, 1, 0, 1));
        rejects(IllegalArgumentException.class, () -> new Bounds3i(0, 0, 1, 1, 1, 0));
        rejects(NullPointerException.class, () -> full.intersection(null));
        rejects(NullPointerException.class, () -> full.intersects(null));
        rejects(NullPointerException.class, () -> full.xz().intersection(null));
        rejects(NullPointerException.class, () -> full.xz().intersects(null));
        rejects(NullPointerException.class, () -> full.yRange().intersects(null));
        rejects(NullPointerException.class, () -> new ChunkWriteBounds(null, full));
        rejects(NullPointerException.class, () -> new ChunkWriteBounds(full, null));
        System.out.println("PASS WG-02 bounds checks=" + checks + "; exhaustive boxes=" + boxes.size());
    }
}
