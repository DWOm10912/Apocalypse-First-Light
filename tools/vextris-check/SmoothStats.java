import com.antaurora.vextris.mesh.MeshLoader;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How far the corner normals Vextris computes lean away from their faces, by area: for each mesh id given, per part the
 * area share of faces whose largest corner deviation is over 8, 15 and 25 degrees, and the largest faces (area over
 * 0.01 block^2) with more than 15 degrees. Large flat faces with a big lean shade like a bulge (crease too high there).
 *   java -cp vextris.jar;gson.jar SmoothStats.java <assets dir> <id> [<id> ...]
 */
public final class SmoothStats {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        for (int a = 1; a < args.length; a++) {
            String id = args[a];
            MeshModel model;
            try (Reader sr = Files.newBufferedReader(assets.resolve("meshes/" + id + ".aflmesh.json"));
                 Reader gr = Files.newBufferedReader(assets.resolve("geo/" + id + ".geo.json"))) {
                model = MeshLoader.load("apocalypse_firstlight:" + id, sr, gr);
            }
            System.out.println("== " + id);
            for (MeshPart p : model.allParts()) {
                double total = 0, over8 = 0, over15 = 0, over25 = 0;
                List<String> big = new ArrayList<>();
                for (int f = 0; f < p.faceCount(); f++) {
                    int s = p.faceStart(f), n = p.faceSize(f);
                    double[] fn = {p.faceNormal(f, 0), p.faceNormal(f, 1), p.faceNormal(f, 2)};
                    double area = 0;
                    for (int k = 1; k + 1 < n; k++) area += tri(p, s, s + k, s + k + 1);
                    double worst = 0;
                    for (int k = 0; k < n; k++) {
                        int c = s + k;
                        double d = fn[0] * p.value(c, 5) + fn[1] * p.value(c, 6) + fn[2] * p.value(c, 7);
                        worst = Math.max(worst, Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d)))));
                    }
                    total += area;
                    if (worst > 8) over8 += area;
                    if (worst > 15) over15 += area;
                    if (worst > 25) over25 += area;
                    if (worst > 15 && area > 0.01) big.add(String.format("%.3f m2 %.0f deg at %.2f %.2f %.2f", area, worst, p.value(s, 0), p.value(s, 1), p.value(s, 2)));
                }
                System.out.printf("  %-28s smooth=%-5s area=%.3f  >8: %4.1f%%  >15: %4.1f%%  >25: %4.1f%%%s%n", p.name(), p.smooth(), total,
                        100 * over8 / total, 100 * over15 / total, 100 * over25 / total, big.isEmpty() ? "" : "  big>15: " + big.size());
                big.stream().limit(4).forEach(b -> System.out.println("      " + b));
            }
        }
    }

    private static double tri(MeshPart p, int a, int b, int c) {
        double[] u = new double[3], v = new double[3];
        for (int k = 0; k < 3; k++) { u[k] = p.value(b, k) - p.value(a, k); v[k] = p.value(c, k) - p.value(a, k); }
        double x = u[1] * v[2] - u[2] * v[1], y = u[2] * v[0] - u[0] * v[2], z = u[0] * v[1] - u[1] * v[0];
        return 0.5 * Math.sqrt(x * x + y * y + z * z);
    }
}
