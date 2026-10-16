import com.antaurora.vextris.mesh.MeshLoader;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads every AFL mesh (assets/apocalypse_firstlight/meshes/*.aflmesh.json with its geo) through the real Vextris
 * MeshLoader from the jar on the class path, and prints one line per mesh: format, parts, triangle equivalent and, for
 * format 3, the smooth parts and corners. Any rejection is printed and the exit code is 1.
 * Run by tools/vextris-check/check-meshes.mjs (no Gradle, no Minecraft start).
 */
public final class MeshCheck {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        List<Path> meshes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(assets.resolve("meshes"))) {
            files.filter(p -> p.toString().endsWith(".aflmesh.json")).sorted().forEach(meshes::add);
        }
        int failed = 0;
        for (Path mesh : meshes) {
            String rel = assets.resolve("meshes").relativize(mesh).toString().replace('\\', '/');
            String id = rel.substring(0, rel.length() - ".aflmesh.json".length());
            Path geo = assets.resolve("geo").resolve(id + ".geo.json");
            try (Reader sr = Files.newBufferedReader(mesh); Reader gr = Files.newBufferedReader(geo)) {
                MeshModel model = MeshLoader.load("apocalypse_firstlight:" + id, sr, gr);
                int smoothParts = 0, smoothCorners = 0, corners = 0;
                for (MeshPart part : model.allParts()) {
                    corners += part.cornerCount();
                    if (part.smooth()) { smoothParts++; smoothCorners += part.cornerCount(); }
                    for (int i = 0; i < part.cornerCount(); i++) {
                        double n = 0;
                        for (int k = 5; k < 8; k++) n += part.value(i, k) * part.value(i, k);
                        if (Math.abs(n - 1) > 1e-4) throw new IllegalStateException(part.name() + ": corner " + i + " normal length " + Math.sqrt(n));
                    }
                }
                System.out.println("OK " + id + " v" + model.formatVersion() + " parts=" + model.partCount() + " tris=" + model.triangleEquivalent()
                        + " corners=" + corners + (model.formatVersion() >= 3 ? " smoothParts=" + smoothParts + " smoothCorners=" + smoothCorners : ""));
            } catch (Exception e) {
                failed++;
                System.out.println("FAIL " + id + ": " + e);
            }
        }
        System.out.println(failed == 0 ? "ALL " + meshes.size() + " MESHES LOADED" : failed + " OF " + meshes.size() + " MESHES REJECTED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
