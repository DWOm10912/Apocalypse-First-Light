package com.antaurora.apofirstlight.client.mesh;

import com.google.gson.*;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Strict V1/V2 decoder/baker; no Minecraft state, no topology work at render time. */
public final class AflMeshLoader {
    public static final int MAX_CHARACTERS = 4 * 1024 * 1024;
    public static final int MAX_PARTS = 128;
    public static final int MAX_TRIANGLES = 16384;
    private static final int MAX_VERTICES = 65536;

    public static AflMeshModel load(String resource, Reader sidecar, Reader geometry) throws IOException {
        try {
            return bake(read(sidecar), read(geometry));
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException e) {
            throw new IllegalArgumentException(resource + ": " + e.getMessage(), e);
        } catch (StackOverflowError e) {
            throw new IllegalArgumentException(resource + ": excessive JSON nesting", e);
        }
    }

    private static JsonObject read(Reader reader) throws IOException {
        var text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            require(text.length() + count <= MAX_CHARACTERS, "JSON exceeds 4 MiB character limit");
            text.append(buffer, 0, count);
        }
        return object(JsonParser.parseString(text.toString()), "root");
    }

    private static AflMeshModel bake(JsonObject root, JsonObject geometry) {
        int version = root.has("format_version") ? integer(root.get("format_version"), "format_version") : 1;
        require(version == 1 || version == 2, "unsupported format_version");
        keys(root, root.has("format_version")
                ? Set.of("format_version", "coordinate_space", "uv_origin", "winding", "texture_size", "parts")
                : Set.of("coordinate_space", "uv_origin", "winding", "texture_size", "parts"), "root");
        require(string(root.get("coordinate_space"), "coordinate_space").equals("bone_pivot_local_blocks"), "unsupported coordinate_space");
        require(string(root.get("uv_origin"), "uv_origin").equals("top_left"), "unsupported uv_origin");
        require(string(root.get("winding"), "winding").equals("ccw"), "unsupported winding");
        var texture = array(root.get("texture_size"), 2, "texture_size");
        int width = integer(texture.get(0), "texture width"), height = integer(texture.get(1), "texture height");
        require(width > 0 && height > 0 && width <= 4096 && height <= 4096, "invalid texture_size");

        var geometries = array(geometry.get("minecraft:geometry"), 1, "minecraft:geometry");
        var geo = object(geometries.get(0), "geometry");
        var description = object(geo.get("description"), "geometry.description");
        require(integer(description.get("texture_width"), "geometry.texture_width") == width
                && integer(description.get("texture_height"), "geometry.texture_height") == height, "sidecar/geometry texture_size mismatch");
        var boneNames = new HashSet<String>();
        for (var entry : array(geo.get("bones"), -1, "geometry.bones")) {
            String name = string(object(entry, "geometry bone").get("name"), "geometry bone name");
            require(boneNames.add(name), "duplicate geometry bone " + name);
        }
        var parts = array(root.get("parts"), -1, "parts");
        require(!parts.isEmpty() && parts.size() <= MAX_PARTS, "parts count must be 1.." + MAX_PARTS);
        var result = new LinkedHashMap<String, List<AflMeshPart>>();
        var names = new HashSet<String>();
        int totalTriangles = 0, totalVertices = 0;
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            String context = "part[" + partIndex + "]";
            try {
                var part = object(parts.get(partIndex), context);
                String name = string(part.get("name"), "name"), bone = string(part.get("bone"), "bone");
                context = "bone=" + bone + " part=" + name;
                String faceKey = version == 1 ? "triangles" : "faces";
                keys(part, part.has("render_layer") ? Set.of("name", "bone", "vertices", faceKey, "render_layer")
                        : Set.of("name", "bone", "vertices", faceKey), context);
                AflMeshPart.Layer layer = AflMeshPart.Layer.CUTOUT;
                if (part.has("render_layer")) {
                    String value = string(part.get("render_layer"), "render_layer");
                    require(value.equals("cutout") || value.equals("translucent"), "unknown render_layer " + value);
                    if (value.equals("translucent")) layer = AflMeshPart.Layer.TRANSLUCENT;
                }
                require(names.add(name), "duplicate part name");
                require(boneNames.contains(bone), "unknown parent bone");
                var vertices = array(part.get("vertices"), -1, "vertices");
                totalVertices += vertices.size();
                require(vertices.size() >= 3 && totalVertices <= MAX_VERTICES, "invalid/excessive vertices count");
                float[][] data = new float[vertices.size()][5];
                for (int i = 0; i < data.length; i++) {
                    var vertex = array(vertices.get(i), 5, "vertex[" + i + "] x,y,z,u,v");
                    for (int j = 0; j < 5; j++) {
                        double value = number(vertex.get(j), "vertex[" + i + "][" + j + "]");
                        require(j < 3 ? Math.abs(value) <= 256 : value >= 0 && value <= 1, "position or UV out of V1 range");
                        data[i][j] = (float)value;
                    }
                }
                var faces = array(part.get(faceKey), -1, faceKey);
                require(!faces.isEmpty() && faces.size() <= MAX_TRIANGLES, "invalid/excessive face count");
                int[] offsets = new int[faces.size() + 1];
                for (int f = 0; f < faces.size(); f++) {
                    int size = array(faces.get(f), -1, "face[" + f + "]").size();
                    require(size == 3 || version == 2 && size == 4, "face[" + f + "] requires 3/4 corners (V1: 3)");
                    totalTriangles += size - 2;
                    offsets[f + 1] = offsets[f] + size;
                }
                require(totalTriangles <= MAX_TRIANGLES, "excessive triangle-equivalent count");
                int[][] faceIds = new int[faces.size()][];
                double[][] faceNormals = new double[faces.size()][];
                for (int t = 0; t < faces.size(); t++) {
                    var indices = faces.get(t).getAsJsonArray();
                    int[] ids = new int[indices.size()];
                    for (int j = 0; j < ids.length; j++) {
                        ids[j] = integer(indices.get(j), "face[" + t + "] index");
                        require(ids[j] >= 0 && ids[j] < data.length, "face[" + t + "] invalid index");
                    }
                    float[] a = data[ids[0]], b = data[ids[1]], c = data[ids[2]];
                    double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
                    double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
                    double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    require(Double.isFinite(length) && length > 1e-10, "triangle[" + t + "] zero-area/degenerate triangle");
                    if (ids.length == 4) validateQuad(data, ids, nx / length, ny / length, nz / length, t);
                    faceIds[t] = ids;
                    faceNormals[t] = new double[]{nx / length, ny / length, nz / length};
                }
                boolean closed = closedSolid(data, faceIds);
                // fewer submitted corners for the same picture (2026-10-05): coplanar triangle pairs as quads
                pairTriangles(data, faceIds, faceNormals);
                int kept = 0;
                for (int[] ids : faceIds) if (ids != null) kept++;
                offsets = new int[kept + 1];
                int[][] outIds = new int[kept][];
                double[][] outNormals = new double[kept][];
                for (int t = 0, k = 0; t < faceIds.length; t++) {
                    if (faceIds[t] == null) continue;
                    outIds[k] = faceIds[t];
                    outNormals[k] = faceNormals[t];
                    offsets[k + 1] = offsets[k] + faceIds[t].length;
                    k++;
                }
                float[] baked = new float[offsets[kept] * AflMeshPart.STRIDE];
                double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
                double maxX = -minX, maxY = -minX, maxZ = -minX;
                int at = 0;
                for (int t = 0; t < kept; t++) {
                    int[] ids = outIds[t];
                    double nx = outNormals[t][0], ny = outNormals[t][1], nz = outNormals[t][2], length = 1.0;
                    for (int id : ids) {
                        var v = data[id];
                        for (float value : v) baked[at++] = value;
                        baked[at++] = (float)(nx / length); baked[at++] = (float)(ny / length); baked[at++] = (float)(nz / length);
                        minX = Math.min(minX, v[0]); minY = Math.min(minY, v[1]); minZ = Math.min(minZ, v[2]);
                        maxX = Math.max(maxX, v[0]); maxY = Math.max(maxY, v[1]); maxZ = Math.max(maxZ, v[2]);
                    }
                }
                result.computeIfAbsent(bone, ignored -> new ArrayList<>()).add(new AflMeshPart(name, baked, offsets,
                        new AflMeshPart.Bounds(minX, minY, minZ, maxX, maxY, maxZ), layer, closed));
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException(context + ": " + e.getMessage(), e);
            }
        }
        return new AflMeshModel(version, result);
    }

    /**
     * Merges pairs of triangles into quads where that draws exactly the same: the two share an edge (the same two vertex
     * indices, so the same positions and UVs, met in opposite directions: consistent winding) and are coplanar (face
     * normals equal to 1e-6). The quad is (a, b, c, d) with the shared edge a-c as its diagonal, which is where a QUADS
     * draw splits it ((0,1,2) and (2,3,0)), so the GPU draws the two original triangles; mirrored submission keeps the
     * same diagonal. Each merge saves the degenerate fourth corner of both triangles: 8 corners become 4. Greedy, in face
     * order; the second triangle of a pair is set to null. Returns the number of merges.
     * <p>
     * A shader pack (Oculus) gives a quad one tangent, worked out from its first three corners, so the partner triangle
     * gets the first one's: merged only when the texture runs the same way across both (UV derivatives dP/du and dP/dv
     * equal to 1e-3 of their size, same handedness). That is an affine UV mapping across the quad; the tangent is packed
     * into a byte per axis, far coarser than 1e-3. A UV seam never merges (different vertex indices).
     */
    private static int pairTriangles(float[][] data, int[][] faces, double[][] normals) {
        var byEdge = new java.util.HashMap<Long, java.util.List<Integer>>();
        for (int t = 0; t < faces.length; t++) {
            int[] f = faces[t];
            if (f.length != 3) continue;
            for (int k = 0; k < 3; k++) byEdge.computeIfAbsent(edge(f[k], f[(k + 1) % 3]), e -> new ArrayList<>()).add(t);
        }
        int merges = 0;
        boolean[] used = new boolean[faces.length];
        for (int t = 0; t < faces.length; t++) {
            int[] f = faces[t];
            if (used[t] || f == null || f.length != 3) continue;
            used[t] = true;
            for (int k = 0; k < 3; k++) {
                int x = f[k], y = f[(k + 1) % 3];
                var candidates = byEdge.get(edge(y, x));
                if (candidates == null) continue;
                int partner = -1;
                for (int u : candidates) {
                    if (used[u] || faces[u] == null) continue;
                    double[] n = normals[t], m = normals[u];
                    if (n[0] * m[0] + n[1] * m[1] + n[2] * m[2] > 1 - 1e-6 && sameUvFrame(data, f, faces[u])) { partner = u; break; }
                }
                if (partner < 0) continue;
                // t = (x, y, z): rotate so the shared edge is c -> a, i.e. a = y, b = z, c = x; d = the partner's third corner
                int z = f[(k + 2) % 3], d = -1;
                for (int corner : faces[partner]) if (corner != x && corner != y) d = corner;
                if (d < 0) continue;
                faces[t] = new int[]{y, z, x, d};
                used[partner] = true;
                faces[partner] = null;
                merges++;
                break;
            }
        }
        return merges;
    }

    /** The two triangles' UV derivatives (dP/du, dP/dv) agree to 1e-3 of their size, with the same handedness. */
    private static boolean sameUvFrame(float[][] data, int[] t, int[] u) {
        double[] a = uvFrame(data, t), b = uvFrame(data, u);
        if (a == null || b == null || a[6] != b[6]) return false;
        return near(a, b, 0) && near(a, b, 3);
    }

    private static boolean near(double[] a, double[] b, int at) {
        double dx = a[at] - b[at], dy = a[at + 1] - b[at + 1], dz = a[at + 2] - b[at + 2];
        double size = Math.max(Math.sqrt(a[at] * a[at] + a[at + 1] * a[at + 1] + a[at + 2] * a[at + 2]),
                Math.sqrt(b[at] * b[at] + b[at + 1] * b[at + 1] + b[at + 2] * b[at + 2]));
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= 1e-3 * size;
    }

    /** {dP/du, dP/dv, sign of the UV area}, or null for a triangle whose UVs are degenerate. */
    private static double[] uvFrame(float[][] data, int[] f) {
        float[] a = data[f[0]], b = data[f[1]], c = data[f[2]];
        double e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2], e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
        double du1 = b[3] - a[3], dv1 = b[4] - a[4], du2 = c[3] - a[3], dv2 = c[4] - a[4], det = du1 * dv2 - du2 * dv1;
        if (det == 0) return null;
        double[] frame = {(e1x * dv2 - e2x * dv1) / det, (e1y * dv2 - e2y * dv1) / det, (e1z * dv2 - e2z * dv1) / det,
                (e2x * du1 - e1x * du2) / det, (e2y * du1 - e1y * du2) / det, (e2z * du1 - e1z * du2) / det, Math.signum(det)};
        for (double value : frame) if (!Double.isFinite(value)) return null;
        return frame;
    }

    private static long edge(int from, int to) {
        return ((long) from << 32) | (to & 0xFFFFFFFFL);
    }

    /**
     * True for a closed, consistently wound, outward-facing solid: AflMeshPart#closed. Every separate shell must enclose
     * a positive volume on its own, so one inside-out shell next to a good one does not pass.
     */
    private static boolean closedSolid(float[][] data, int[][] faces) {
        var weld = new java.util.HashMap<Long, Integer>();
        int[] id = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            long key = (Math.round(data[i][0] * 1e5) * 73856093L) ^ (Math.round(data[i][1] * 1e5) * 19349663L) ^ (Math.round(data[i][2] * 1e5) * 83492791L);
            Integer existing = weld.get(key);
            if (existing != null && data[existing][0] == data[i][0] && data[existing][1] == data[i][1] && data[existing][2] == data[i][2]) id[i] = existing;
            else {
                if (existing == null) weld.put(key, i);
                id[i] = i;
            }
        }
        var directed = new java.util.HashSet<Long>();
        int[] shell = new int[data.length];
        for (int i = 0; i < shell.length; i++) shell[i] = i;
        for (int[] f : faces) {
            for (int k = 0; k < f.length; k++) {
                if (!directed.add(edge(id[f[k]], id[f[(k + 1) % f.length]]))) return false;   // the same edge twice the same way
                int p = root(shell, id[f[0]]), q = root(shell, id[f[k]]);
                if (p != q) shell[q] = p;
            }
        }
        for (long e : directed) if (!directed.contains(((e & 0xFFFFFFFFL) << 32) | (e >>> 32))) return false;   // an open edge
        double[] volume = new double[data.length];
        for (int[] f : faces) {
            int s = root(shell, id[f[0]]);
            for (int k = 1; k + 1 < f.length; k++) {
                float[] a = data[f[0]], b = data[f[k]], c = data[f[k + 1]];
                volume[s] += a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0]);
            }
        }
        for (int[] f : faces) if (!(volume[root(shell, id[f[0]])] > 0)) return false;
        return true;
    }

    private static int root(int[] shell, int i) {
        while (shell[i] != i) i = shell[i] = shell[shell[i]];
        return i;
    }

    private static void validateQuad(float[][] data, int[] ids, double nx, double ny, double nz, int face) {
        // Exporter owns authoring topology. This is a corruption check with a
        // decimal (1e-10) + float-ULP error budget, not a second authoring classifier.
        double error = 0, extent = 0;
        for (int id : ids) for (int axis = 0; axis < 3; axis++)
            error = Math.max(error, Math.ulp(data[id][axis]) * 0.5 + 5e-11);
        for (int a : ids) for (int b : ids) {
            double x = (double)data[a][0] - data[b][0], y = (double)data[a][1] - data[b][1], z = (double)data[a][2] - data[b][2];
            extent = Math.max(extent, Math.sqrt(x*x+y*y+z*z));
        }
        double edgeError = 2 * Math.sqrt(3) * error;
        double[] errors = new double[4], deviations = new double[4];
        for (int i = 0; i < 4; i++) {
            float[] a = data[ids[i]], b = data[ids[(i + 1) % 4]], c = data[ids[(i + 2) % 4]];
            double ux = (double)b[0] - a[0], uy = (double)b[1] - a[1], uz = (double)b[2] - a[2];
            double vx = (double)c[0] - a[0], vy = (double)c[1] - a[1], vz = (double)c[2] - a[2];
            double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            double length = Math.sqrt(x*x + y*y + z*z);
            require(Double.isFinite(length) && length > 1e-10, "face[" + face + "] degenerate quad");
            double dx = x/length-nx, dy = y/length-ny, dz = z/length-nz;
            require((x*nx+y*ny+z*nz)>0, "face[" + face + "] quad must be convex and planar");
            errors[i] = 2 * (edgeError * (Math.sqrt(ux*ux+uy*uy+uz*uz)
                    + Math.sqrt(vx*vx+vy*vy+vz*vz)) + edgeError*edgeError) / length;
            deviations[i] = Math.sqrt(dx*dx+dy*dy+dz*dz);
        }
        for (int i=0;i<4;i++) require(deviations[i] <= Math.min(0.00202, 2e-5+errors[0]+errors[i]),
                "face[" + face + "] quad must be convex and planar");
        float[] a=data[ids[0]], d=data[ids[3]];
        double distance=Math.abs(((double)d[0]-a[0])*nx+((double)d[1]-a[1])*ny+((double)d[2]-a[2])*nz);
        require(distance <= extent*(2e-5+errors[0])+edgeError,
                "face[" + face + "] quad must be convex and planar");
    }

    private static void keys(JsonObject object, Set<String> allowed, String where) {
        require(object.keySet().equals(allowed), where + " requires exactly " + allowed + "; got " + object.keySet());
    }
    private static JsonObject object(JsonElement value, String where) {
        require(value != null && value.isJsonObject(), where + " must be an object"); return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value, int length, String where) {
        require(value != null && value.isJsonArray(), where + " must be an array");
        var result = value.getAsJsonArray();
        require(length < 0 || result.size() == length, where + " must have " + length + " entries"); return result;
    }
    private static String string(JsonElement value, String where) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), where + " must be a string");
        String text = value.getAsString(); require(!text.isBlank() && text.length() <= 128, where + " has invalid length"); return text;
    }
    private static double number(JsonElement value, String where) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), where + " must be numeric");
        double result = value.getAsDouble(); require(Double.isFinite(result), where + " must be finite"); return result;
    }
    private static int integer(JsonElement value, String where) {
        double result = number(value, where); require(result == Math.rint(result) && Math.abs(result) <= Integer.MAX_VALUE, where + " must be integer"); return (int)result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private AflMeshLoader() {}
}
