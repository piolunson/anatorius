package dev.project.anatorius.utils.playermodels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GlbLoader {
    public static final float TARGET_HEIGHT = 1.80f;
    public static final int MAX_TRIANGLES = 160_000;
    private static final int MAX_TEXTURE = 2048;

    public record Texture(int width, int height, int[] argb) {
    }

    public record Result(PlayerModelMesh mesh, List<Texture> textures, int[] batchTexture) {
    }

    private static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

    private GlbLoader() {
    }

    private JsonObject gltf;
    private ByteBuffer bin;
    private Path dir;

    public static Result load(Path file) throws IOException {
        return load(Files.readAllBytes(file), file.toAbsolutePath().getParent());
    }

    public static Result load(byte[] data, Path dir) throws IOException {
        GlbLoader l = new GlbLoader();
        l.dir = dir;
        l.read(data);
        return l.build();
    }

    private void read(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 20 || b.getInt(0) != 0x46546C67) throw new IOException("not a .glb file");
        int length = Math.min(b.getInt(8), data.length);
        int offset = 12;
        while (offset + 8 <= length) {
            int chunkLength = b.getInt(offset), type = b.getInt(offset + 4);
            if (type == 0x4E4F534A) gltf = JsonParser.parseString(new String(data, offset + 8, chunkLength, StandardCharsets.UTF_8)).getAsJsonObject();
            else if (type == 0x004E4942) bin = ByteBuffer.wrap(data, offset + 8, chunkLength).slice().order(ByteOrder.LITTLE_ENDIAN);
            offset += 8 + chunkLength;
        }
        if (gltf == null) throw new IOException("no JSON chunk");
        for (JsonElement e : arr(gltf, "extensionsRequired")) {
            String ext = e.getAsString();
            if (ext.contains("draco") || ext.contains("meshopt")) throw new IOException("compressed mesh (" + ext + ") is not supported, export it uncompressed");
        }
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static int intOr(JsonObject o, String key, int def) {
        return o != null && o.has(key) ? o.get(key).getAsInt() : def;
    }

    private ByteBuffer bufferView(int index) throws IOException {
        JsonObject view = arr(gltf, "bufferViews").get(index).getAsJsonObject();
        int buffer = intOr(view, "buffer", 0);
        JsonObject buf = arr(gltf, "buffers").get(buffer).getAsJsonObject();
        ByteBuffer data;
        if (buf.has("uri")) data = ByteBuffer.wrap(uri(buf.get("uri").getAsString())).order(ByteOrder.LITTLE_ENDIAN);
        else if (bin != null) data = bin;
        else throw new IOException("missing binary buffer");
        int off = intOr(view, "byteOffset", 0), len = view.get("byteLength").getAsInt();
        return data.duplicate().position(off).limit(off + len).slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    private byte[] uri(String uri) throws IOException {
        if (uri.startsWith("data:")) return java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
        if (dir == null) throw new IOException("external file " + uri + " in a bundled model");
        Path p = dir.resolve(java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8)).normalize();
        if (!p.startsWith(dir)) throw new IOException("buffer outside the model folder");
        return Files.readAllBytes(p);
    }

    private static int components(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT4" -> 16;
            default -> throw new IllegalArgumentException(type);
        };
    }

    private float[] floats(int accessorIndex) throws IOException {
        JsonObject acc = arr(gltf, "accessors").get(accessorIndex).getAsJsonObject();
        int count = acc.get("count").getAsInt(), comps = components(acc.get("type").getAsString());
        int ctype = acc.get("componentType").getAsInt();
        boolean normalized = acc.has("normalized") && acc.get("normalized").getAsBoolean();
        float[] out = new float[count * comps];
        if (!acc.has("bufferView")) return out;
        int size = ctype == 5126 || ctype == 5125 ? 4 : ctype == 5122 || ctype == 5123 ? 2 : 1;
        JsonObject view = arr(gltf, "bufferViews").get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        int stride = intOr(view, "byteStride", 0);
        if (stride == 0) stride = size * comps;
        ByteBuffer b = bufferView(acc.get("bufferView").getAsInt());
        int base = intOr(acc, "byteOffset", 0);
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < comps; c++) {
                int at = base + i * stride + c * size;
                float v = switch (ctype) {
                    case 5126 -> b.getFloat(at);
                    case 5121 -> normalized ? (b.get(at) & 0xFF) / 255f : b.get(at) & 0xFF;
                    case 5120 -> normalized ? Math.max(b.get(at) / 127f, -1f) : b.get(at);
                    case 5123 -> normalized ? (b.getShort(at) & 0xFFFF) / 65535f : b.getShort(at) & 0xFFFF;
                    case 5122 -> normalized ? Math.max(b.getShort(at) / 32767f, -1f) : b.getShort(at);
                    case 5125 -> b.getInt(at) & 0xFFFFFFFFL;
                    default -> 0f;
                };
                out[i * comps + c] = v;
            }
        }
        return out;
    }

    private int[] ints(int accessorIndex) throws IOException {
        float[] f = floats(accessorIndex);
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) out[i] = (int) f[i];
        return out;
    }

    private Matrix4f[] global;
    private int[] parent;

    private void computeGlobals() {
        JsonArray nodes = arr(gltf, "nodes");
        global = new Matrix4f[nodes.size()];
        parent = new int[nodes.size()];
        java.util.Arrays.fill(parent, -1);
        for (int i = 0; i < nodes.size(); i++)
            for (JsonElement c : arr(nodes.get(i).getAsJsonObject(), "children")) parent[c.getAsInt()] = i;
        for (int i = 0; i < nodes.size(); i++) globalOf(i, nodes);
    }

    private Matrix4f globalOf(int i, JsonArray nodes) {
        if (global[i] != null) return global[i];
        Matrix4f local = localOf(nodes.get(i).getAsJsonObject());
        global[i] = parent[i] >= 0 ? new Matrix4f(globalOf(parent[i], nodes)).mul(local) : local;
        return global[i];
    }

    private static Matrix4f localOf(JsonObject node) {
        if (node.has("matrix")) {
            JsonArray m = node.getAsJsonArray("matrix");
            float[] f = new float[16];
            for (int k = 0; k < 16; k++) f[k] = m.get(k).getAsFloat();
            return new Matrix4f().set(f);
        }
        Matrix4f out = new Matrix4f();
        if (node.has("translation")) {
            JsonArray t = node.getAsJsonArray("translation");
            out.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
        }
        if (node.has("rotation")) {
            JsonArray r = node.getAsJsonArray("rotation");
            out.rotate(new Quaternionf(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), r.get(3).getAsFloat()));
        }
        if (node.has("scale")) {
            JsonArray s = node.getAsJsonArray("scale");
            out.scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat());
        }
        return out;
    }

    private String nodeName(int i) {
        JsonObject n = arr(gltf, "nodes").get(i).getAsJsonObject();
        return n.has("name") ? n.get("name").getAsString() : "";
    }

    static int categoryOfName(String raw) {
        String n = raw.toLowerCase(Locale.ROOT);
        if (n.contains("armature") || n.contains("clavicle") || n.contains("shoulder") || n.contains("肩")) return BODY;
        for (String k : new String[]{"arm", "hand", "finger", "thumb", "index", "middle", "ring", "pinky", "wrist", "elbow", "腕", "ひじ", "手", "指"})
            if (n.contains(k)) return RIGHT_ARM;
        for (String k : new String[]{"thigh", "leg", "calf", "knee", "shin", "foot", "toe", "ankle", "足", "ひざ", "脚", "つま先"})
            if (n.contains(k)) return RIGHT_LEG;
        for (String k : new String[]{"head", "neck", "hair", "eye", "jaw", "face", "ear", "tongue", "bang", "pony", "twin", "braid", "ahoge", "hat", "頭", "首", "髪", "目"})
            if (n.contains(k)) return HEAD;
        for (String k : new String[]{"spine", "chest", "pelvis", "hip", "root", "skirt", "body", "breast", "waist", "上半身", "下半身", "センター"})
            if (n.contains(k)) return BODY;
        return -1;
    }

    private static final class Bucket {
        final List<float[]> verts = new ArrayList<>();
        int material;
    }

    private Result build() throws IOException {
        computeGlobals();
        JsonArray nodes = arr(gltf, "nodes");
        int n = nodes.size();

        int[] cat = new int[n];
        java.util.Arrays.fill(cat, -2);
        java.util.Set<Integer> joints = new java.util.HashSet<>();
        for (JsonElement s : arr(gltf, "skins")) for (JsonElement j : arr(s.getAsJsonObject(), "joints")) joints.add(j.getAsInt());
        for (int i = 0; i < n; i++) category(i, cat);

        float centreX = 0;
        {
            float sum = 0;
            int c = 0;
            for (int j : joints) if (cat[j] == RIGHT_LEG || cat[j] == BODY) { sum += pos(j).x; c++; }
            if (c > 0) centreX = sum / c;
        }
        boolean hasHead = false;
        for (int j : joints) if (cat[j] == HEAD) hasHead = true;

        Map<Long, Bucket> buckets = new HashMap<>();
        int triangles = 0;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        JsonArray meshes = arr(gltf, "meshes");
        for (int ni = 0; ni < n; ni++) {
            JsonObject node = nodes.get(ni).getAsJsonObject();
            if (!node.has("mesh") || !inScene(ni)) continue;
            JsonObject mesh = meshes.get(node.get("mesh").getAsInt()).getAsJsonObject();
            Matrix4f[] jointMatrix = null;
            int[] skinJoints = null;
            if (node.has("skin")) {
                JsonObject skin = arr(gltf, "skins").get(node.get("skin").getAsInt()).getAsJsonObject();
                JsonArray js = arr(skin, "joints");
                skinJoints = new int[js.size()];
                jointMatrix = new Matrix4f[js.size()];
                float[] ibm = skin.has("inverseBindMatrices") ? floats(skin.get("inverseBindMatrices").getAsInt()) : null;
                for (int k = 0; k < js.size(); k++) {
                    skinJoints[k] = js.get(k).getAsInt();
                    Matrix4f inv = new Matrix4f();
                    if (ibm != null) inv.set(java.util.Arrays.copyOfRange(ibm, k * 16, k * 16 + 16));
                    jointMatrix[k] = new Matrix4f(global[skinJoints[k]]).mul(inv);
                }
            }
            for (JsonElement pe : arr(mesh, "primitives")) {
                JsonObject prim = pe.getAsJsonObject();
                if (intOr(prim, "mode", 4) != 4) continue;
                JsonObject attr = prim.getAsJsonObject("attributes");
                if (attr == null || !attr.has("POSITION")) continue;
                float[] p = floats(attr.get("POSITION").getAsInt());
                float[] nm = attr.has("NORMAL") ? floats(attr.get("NORMAL").getAsInt()) : null;
                float[] uv = attr.has("TEXCOORD_0") ? floats(attr.get("TEXCOORD_0").getAsInt()) : null;
                int[] jn = jointMatrix != null && attr.has("JOINTS_0") ? ints(attr.get("JOINTS_0").getAsInt()) : null;
                float[] wt = jn != null && attr.has("WEIGHTS_0") ? floats(attr.get("WEIGHTS_0").getAsInt()) : null;
                int count = p.length / 3;
                int[] idx = prim.has("indices") ? ints(prim.get("indices").getAsInt()) : null;
                int tris = (idx != null ? idx.length : count) / 3;
                triangles += tris;
                if (triangles > MAX_TRIANGLES)
                    throw new IOException("too heavy: over " + MAX_TRIANGLES + " triangles (reduce it, e.g. Blender Decimate)");
                int material = intOr(prim, "material", -1);

                float[] wp = new float[count * 3], wn = new float[count * 3];
                int[] vcat = new int[count];
                Vector3f v = new Vector3f(), acc = new Vector3f(), nv = new Vector3f(), nacc = new Vector3f();
                Matrix4f nodeM = global[ni];
                Matrix3f nodeN = new Matrix3f(nodeM).invert().transpose();
                for (int i = 0; i < count; i++) {
                    v.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
                    if (nm != null) nv.set(nm[i * 3], nm[i * 3 + 1], nm[i * 3 + 2]);
                    int best = BODY;
                    if (jn != null) {
                        acc.zero();
                        nacc.zero();
                        float bw = -1, total = 0;
                        int bestJoint = -1;
                        for (int k = 0; k < 4; k++) {
                            float w = wt != null ? wt[i * 4 + k] : (k == 0 ? 1 : 0);
                            if (w <= 0) continue;
                            int ji = jn[i * 4 + k];
                            if (ji < 0 || ji >= jointMatrix.length) continue;
                            Vector3f t = jointMatrix[ji].transformPosition(v, new Vector3f());
                            acc.fma(w, t);
                            if (nm != null) nacc.fma(w, new Matrix3f(jointMatrix[ji]).transform(new Vector3f(nv)));
                            total += w;
                            if (w > bw) { bw = w; bestJoint = skinJoints[ji]; }
                        }
                        if (total > 0) {
                            acc.div(total);
                            v.set(acc);
                            if (nm != null) nv.set(nacc);
                        } else nodeM.transformPosition(v);
                        if (bestJoint >= 0) best = sided(cat[bestJoint] < 0 ? BODY : cat[bestJoint], pos(bestJoint).x, centreX);
                    } else {
                        nodeM.transformPosition(v);
                        if (nm != null) nodeN.transform(nv);
                    }
                    if (nm != null && nv.lengthSquared() > 0) nv.normalize();
                    wp[i * 3] = v.x; wp[i * 3 + 1] = v.y; wp[i * 3 + 2] = v.z;
                    wn[i * 3] = nv.x; wn[i * 3 + 1] = nv.y; wn[i * 3 + 2] = nv.z;
                    vcat[i] = best;
                    minY = Math.min(minY, v.y);
                    maxY = Math.max(maxY, v.y);
                }
                for (int t = 0; t < tris; t++) {
                    int a = idx != null ? idx[t * 3] : t * 3, b = idx != null ? idx[t * 3 + 1] : t * 3 + 1, c = idx != null ? idx[t * 3 + 2] : t * 3 + 2;
                    int part = vcat[a] == vcat[b] || vcat[a] == vcat[c] ? vcat[a] : vcat[b] == vcat[c] ? vcat[b] : vcat[a];
                    Bucket bucket = buckets.computeIfAbsent(((long) part << 32) | (material & 0xFFFFFFFFL), k -> new Bucket());
                    bucket.material = material;
                    float[] fn = null;
                    if (nm == null) fn = faceNormal(wp, a, b, c);
                    for (int vi : new int[]{a, b, c, c}) {
                        float[] out = new float[8];
                        out[0] = wp[vi * 3]; out[1] = wp[vi * 3 + 1]; out[2] = wp[vi * 3 + 2];
                        if (fn != null) { out[3] = fn[0]; out[4] = fn[1]; out[5] = fn[2]; }
                        else { out[3] = wn[vi * 3]; out[4] = wn[vi * 3 + 1]; out[5] = wn[vi * 3 + 2]; }
                        if (uv != null) { out[6] = uv[vi * 2]; out[7] = uv[vi * 2 + 1]; }
                        bucket.verts.add(out);
                    }
                }
            }
        }
        if (buckets.isEmpty()) throw new IOException("no triangles");
        float height = maxY - minY;

        float[][] piv = new float[6][];
        float[] reach = new float[6];
        float[][] far = new float[6][];
        for (int j : joints) {
            int c = cat[j] < 0 ? BODY : sided(cat[j], pos(j).x, centreX);
            Vector3f p = pos(j);
            if (c == HEAD && (piv[HEAD] == null || p.y < piv[HEAD][1])) piv[HEAD] = new float[]{p.x, p.y, p.z};
            if (c >= RIGHT_ARM && (piv[c] == null || p.y > piv[c][1])) piv[c] = new float[]{p.x, p.y, p.z};
        }
        for (int j : joints) {
            int c = cat[j] < 0 ? BODY : sided(cat[j], pos(j).x, centreX);
            if (c < RIGHT_ARM || piv[c] == null) continue;
            Vector3f p = pos(j);
            float d = p.distance(piv[c][0], piv[c][1], piv[c][2]);
            if (d > reach[c]) { reach[c] = d; far[c] = new float[]{p.x, p.y, p.z}; }
        }
        float neckY = piv[HEAD] != null ? piv[HEAD][1]
                : piv[RIGHT_ARM] != null ? piv[RIGHT_ARM][1] + height * 0.05f : minY + height * 0.84f;
        if (piv[HEAD] == null) piv[HEAD] = new float[]{centreX, neckY, 0};
        piv[BODY] = piv[HEAD].clone();
        if (!hasHead) {
            Bucket[] moved = buckets.entrySet().stream().filter(e -> (int) (e.getKey() >> 32) == BODY).map(Map.Entry::getValue).toArray(Bucket[]::new);
            for (Bucket from : moved) {
                Bucket to = buckets.computeIfAbsent(((long) HEAD << 32) | (from.material & 0xFFFFFFFFL), k -> new Bucket());
                to.material = from.material;
                List<float[]> keep = new ArrayList<>();
                for (int q = 0; q < from.verts.size(); q += 4) {
                    float cy = (from.verts.get(q)[1] + from.verts.get(q + 1)[1] + from.verts.get(q + 2)[1]) / 3f;
                    List<float[]> dst = cy > neckY ? to.verts : keep;
                    for (int k = 0; k < 4; k++) dst.add(from.verts.get(q + k));
                }
                from.verts.clear();
                from.verts.addAll(keep);
            }
        }
        float shoulderHalf = height * 0.11f;
        if (piv[RIGHT_ARM] == null) piv[RIGHT_ARM] = new float[]{centreX - shoulderHalf, neckY - height * 0.05f, 0};
        if (piv[LEFT_ARM] == null) piv[LEFT_ARM] = new float[]{centreX + shoulderHalf, neckY - height * 0.05f, 0};
        if (piv[RIGHT_LEG] == null) piv[RIGHT_LEG] = new float[]{centreX - height * 0.05f, minY + height * 0.52f, 0};
        if (piv[LEFT_LEG] == null) piv[LEFT_LEG] = new float[]{centreX + height * 0.05f, minY + height * 0.52f, 0};

        for (int c = RIGHT_ARM; c <= LEFT_LEG; c++) {
            if (far[c] == null) continue;
            Vector3f dir = new Vector3f(far[c][0] - piv[c][0], far[c][1] - piv[c][1], far[c][2] - piv[c][2]).normalize();
            float out = (c == RIGHT_ARM || c == RIGHT_LEG ? -1 : 1) * (c <= LEFT_ARM ? 0.07f : 0.02f);
            Vector3f want = new Vector3f(out, -1, 0).normalize();
            if (dir.angle(want) < Math.toRadians(8)) continue;
            Quaternionf q = new Quaternionf().rotationTo(dir, want);
            for (Map.Entry<Long, Bucket> e : buckets.entrySet()) {
                if ((int) (e.getKey() >> 32) != c) continue;
                for (float[] vtx : e.getValue().verts) {
                    Vector3f pp = new Vector3f(vtx[0] - piv[c][0], vtx[1] - piv[c][1], vtx[2] - piv[c][2]);
                    q.transform(pp);
                    Vector3f nn = q.transform(new Vector3f(vtx[3], vtx[4], vtx[5]));
                    vtx[0] = pp.x + piv[c][0]; vtx[1] = pp.y + piv[c][1]; vtx[2] = pp.z + piv[c][2];
                    vtx[3] = nn.x; vtx[4] = nn.y; vtx[5] = nn.z;
                }
            }
        }
        minY = Float.MAX_VALUE;
        maxY = -Float.MAX_VALUE;
        for (Bucket b : buckets.values()) for (float[] vtx : b.verts) { minY = Math.min(minY, vtx[1]); maxY = Math.max(maxY, vtx[1]); }
        float scale = TARGET_HEIGHT / Math.max(1e-4f, maxY - minY);
        float cx = (piv[RIGHT_LEG][0] + piv[LEFT_LEG][0]) / 2f, cz = (piv[RIGHT_LEG][2] + piv[LEFT_LEG][2]) / 2f;
        float facing = facingSign(joints, cat, centreX);
        final float fMinY = minY;
        java.util.function.Function<float[], float[]> toMc = g -> new float[]{
                facing * (g[0] - cx) * scale, 1.5f - (g[1] - fMinY) * scale, -facing * (g[2] - cz) * scale};

        readMaterials();

        PlayerModelMesh.Part[] parts = new PlayerModelMesh.Part[6];
        List<Integer> batchTex = new ArrayList<>();
        for (int c = 0; c < 6; c++) {
            float[] pv = toMc.apply(new float[]{piv[c][0], piv[c][1], piv[c][2]});
            List<PlayerModelMesh.Batch> batches = new ArrayList<>();
            for (Map.Entry<Long, Bucket> e : buckets.entrySet()) {
                if ((int) (e.getKey() >> 32) != c || e.getValue().verts.isEmpty()) continue;
                Bucket b = e.getValue();
                int count = b.verts.size();
                float[] pos = new float[count * 3], nrm = new float[count * 3], uvs = new float[count * 2];
                int[] col = new int[count];
                int color = materialColor.getOrDefault(b.material, -1);
                for (int i = 0; i < count; i++) {
                    float[] g = b.verts.get(i);
                    float[] m = toMc.apply(g);
                    pos[i * 3] = m[0] - pv[0]; pos[i * 3 + 1] = m[1] - pv[1]; pos[i * 3 + 2] = m[2] - pv[2];
                    nrm[i * 3] = facing * g[3]; nrm[i * 3 + 1] = -g[4]; nrm[i * 3 + 2] = -facing * g[5];
                    uvs[i * 2] = g[6]; uvs[i * 2 + 1] = g[7];
                    col[i] = color;
                }
                Integer tex = materialTexture.get(b.material);
                batches.add(new PlayerModelMesh.Batch(null, materialBlend.getOrDefault(b.material, false), pos, nrm, tex != null ? uvs : null, col));
                batchTex.add(tex != null ? tex : -1);
            }
            parts[c] = new PlayerModelMesh.Part(pv[0], pv[1], pv[2], batches.toArray(new PlayerModelMesh.Batch[0]));
        }
        int[] bt = batchTex.stream().mapToInt(Integer::intValue).toArray();
        return new Result(PlayerModelMesh.of(parts), textures, bt);
    }

    public record PropResult(BackItem item, List<Texture> textures, int[] batchTexture) {
    }

    public static final int MAX_PROP_TRIANGLES = 60_000;

    public static PropResult loadProp(Path file) throws IOException {
        return loadProp(Files.readAllBytes(file), file.toAbsolutePath().getParent());
    }

    public static PropResult loadProp(byte[] data, Path dir) throws IOException {
        GlbLoader l = new GlbLoader();
        l.dir = dir;
        l.read(data);
        return l.buildProp();
    }

    private static boolean gearName(String raw) {
        String n = raw.toLowerCase(Locale.ROOT);
        return n.contains("gear") || n.contains("cog") || n.contains("歯車");
    }

    private int pieceOf(int node) {
        int child = -1;
        for (int i = node; i >= 0; child = i, i = parent[i]) {
            if (!gearName(nodeName(i))) continue;
            return 1 + (i == node || child < 0 ? i : child);
        }
        return 0;
    }

    private PropResult buildProp() throws IOException {
        computeGlobals();
        JsonArray nodes = arr(gltf, "nodes");
        JsonArray meshes = arr(gltf, "meshes");
        Map<Long, Bucket> buckets = new java.util.LinkedHashMap<>();
        int triangles = 0;
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int ni = 0; ni < nodes.size(); ni++) {
            JsonObject node = nodes.get(ni).getAsJsonObject();
            if (!node.has("mesh") || !inScene(ni)) continue;
            int piece = pieceOf(ni);
            Matrix4f nodeM = global[ni];
            Matrix3f nodeN = new Matrix3f(nodeM).invert().transpose();
            for (JsonElement pe : arr(meshes.get(node.get("mesh").getAsInt()).getAsJsonObject(), "primitives")) {
                JsonObject prim = pe.getAsJsonObject();
                if (intOr(prim, "mode", 4) != 4) continue;
                JsonObject attr = prim.getAsJsonObject("attributes");
                if (attr == null || !attr.has("POSITION")) continue;
                float[] p = floats(attr.get("POSITION").getAsInt());
                float[] nm = attr.has("NORMAL") ? floats(attr.get("NORMAL").getAsInt()) : null;
                float[] uv = attr.has("TEXCOORD_0") ? floats(attr.get("TEXCOORD_0").getAsInt()) : null;
                int count = p.length / 3;
                int[] idx = prim.has("indices") ? ints(prim.get("indices").getAsInt()) : null;
                int tris = (idx != null ? idx.length : count) / 3;
                triangles += tris;
                if (triangles > MAX_PROP_TRIANGLES)
                    throw new IOException("too heavy: over " + MAX_PROP_TRIANGLES + " triangles (reduce it, e.g. Blender Decimate)");
                float[] wp = new float[count * 3], wn = new float[count * 3];
                Vector3f v = new Vector3f(), nv = new Vector3f();
                for (int i = 0; i < count; i++) {
                    nodeM.transformPosition(v.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]));
                    wp[i * 3] = v.x; wp[i * 3 + 1] = v.y; wp[i * 3 + 2] = v.z;
                    for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], wp[i * 3 + k]); hi[k] = Math.max(hi[k], wp[i * 3 + k]); }
                    if (nm != null) {
                        nodeN.transform(nv.set(nm[i * 3], nm[i * 3 + 1], nm[i * 3 + 2]));
                        if (nv.lengthSquared() > 0) nv.normalize();
                        wn[i * 3] = nv.x; wn[i * 3 + 1] = nv.y; wn[i * 3 + 2] = nv.z;
                    }
                }
                int material = intOr(prim, "material", -1);
                Bucket bucket = buckets.computeIfAbsent(((long) piece << 32) | (material & 0xFFFFFFFFL), k -> new Bucket());
                bucket.material = material;
                for (int t = 0; t < tris; t++) {
                    int a = idx != null ? idx[t * 3] : t * 3, b = idx != null ? idx[t * 3 + 1] : t * 3 + 1, c = idx != null ? idx[t * 3 + 2] : t * 3 + 2;
                    float[] fn = nm == null ? faceNormal(wp, a, b, c) : null;
                    for (int vi : new int[]{a, b, c, c}) {
                        float[] out = new float[8];
                        out[0] = wp[vi * 3]; out[1] = wp[vi * 3 + 1]; out[2] = wp[vi * 3 + 2];
                        if (fn != null) { out[3] = fn[0]; out[4] = fn[1]; out[5] = fn[2]; }
                        else { out[3] = wn[vi * 3]; out[4] = wn[vi * 3 + 1]; out[5] = wn[vi * 3 + 2]; }
                        if (uv != null) { out[6] = uv[vi * 2]; out[7] = uv[vi * 2 + 1]; }
                        bucket.verts.add(out);
                    }
                }
            }
        }
        if (buckets.isEmpty()) throw new IOException("no triangles");

        float[] ext = {hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]};
        int thin = ext[0] < ext[1] && ext[0] < ext[2] ? 0 : ext[1] < ext[2] ? 1 : 2;
        float[] mid = {(lo[0] + hi[0]) / 2f, (lo[1] + hi[1]) / 2f, (lo[2] + hi[2]) / 2f};
        float across = Math.max(1e-4f, thin == 0 ? Math.max(ext[1], ext[2]) : thin == 1 ? Math.max(ext[0], ext[2]) : Math.max(ext[0], ext[1]));
        float scale = 1f / across;
        java.util.function.Function<float[], float[]> stand = g -> switch (thin) {
            case 0 -> new float[]{-g[2], g[1], g[0]};
            case 1 -> new float[]{g[0], -g[2], g[1]};
            default -> new float[]{g[0], g[1], g[2]};
        };
        float[] m0 = stand.apply(mid);

        readMaterials();

        Map<Integer, List<PlayerModelMesh.Batch>> pieceBatches = new java.util.LinkedHashMap<>();
        Map<Integer, float[]> pieceBounds = new HashMap<>();
        List<Integer> batchTex = new ArrayList<>();
        Map<Integer, List<Integer>> pieceTex = new HashMap<>();
        for (Map.Entry<Long, Bucket> e : buckets.entrySet()) {
            int piece = (int) (e.getKey() >> 32);
            Bucket b = e.getValue();
            int count = b.verts.size();
            float[] pos = new float[count * 3], nrm = new float[count * 3], uvs = new float[count * 2];
            int[] col = new int[count];
            int color = materialColor.getOrDefault(b.material, -1);
            float[] bounds = pieceBounds.computeIfAbsent(piece, k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE});
            for (int i = 0; i < count; i++) {
                float[] g = b.verts.get(i);
                float[] s = stand.apply(g), n = stand.apply(new float[]{g[3], g[4], g[5]});
                float x = (s[0] - m0[0]) * scale, y = -(s[1] - m0[1]) * scale, z = -(s[2] - m0[2]) * scale;
                pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z;
                nrm[i * 3] = n[0]; nrm[i * 3 + 1] = -n[1]; nrm[i * 3 + 2] = -n[2];
                uvs[i * 2] = g[6]; uvs[i * 2 + 1] = g[7];
                col[i] = color;
                bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.max(bounds[2], x); bounds[3] = Math.max(bounds[3], y);
            }
            Integer tex = materialTexture.get(b.material);
            pieceBatches.computeIfAbsent(piece, k -> new ArrayList<>())
                    .add(new PlayerModelMesh.Batch(null, materialBlend.getOrDefault(b.material, false), pos, nrm, tex != null ? uvs : null, col));
            pieceTex.computeIfAbsent(piece, k -> new ArrayList<>()).add(tex != null ? tex : -1);
        }
        List<BackItem.Piece> pieces = new ArrayList<>();
        int gear = 0;
        for (Map.Entry<Integer, List<PlayerModelMesh.Batch>> e : pieceBatches.entrySet()) {
            float[] bd = pieceBounds.get(e.getKey());
            float cx = (bd[0] + bd[2]) / 2f, cy = (bd[1] + bd[3]) / 2f;
            float radius = Math.max(0.02f, Math.max(bd[2] - bd[0], bd[3] - bd[1]) / 2f);
            float speed = 0;
            if (e.getKey() != 0) speed = (gear++ % 2 == 0 ? 1 : -1) * Math.min(1.2f, 0.06f / radius);
            pieces.add(new BackItem.Piece(cx, cy, speed, e.getValue().toArray(new PlayerModelMesh.Batch[0])));
            batchTex.addAll(pieceTex.get(e.getKey()));
        }
        return new PropResult(new BackItem(pieces.toArray(new BackItem.Piece[0])), textures,
                batchTex.stream().mapToInt(Integer::intValue).toArray());
    }

    private final List<Texture> textures = new ArrayList<>();
    private final Map<Integer, Integer> materialTexture = new HashMap<>();
    private final Map<Integer, Integer> materialColor = new HashMap<>();
    private final Map<Integer, Boolean> materialBlend = new HashMap<>();

    private void readMaterials() {
        JsonArray materials = arr(gltf, "materials");
        for (int m = 0; m < materials.size(); m++) {
            JsonObject mat = materials.get(m).getAsJsonObject();
            JsonObject pbr = mat.has("pbrMetallicRoughness") ? mat.getAsJsonObject("pbrMetallicRoughness") : null;
            JsonObject colorTexture = pbr != null && pbr.has("baseColorTexture") ? pbr.getAsJsonObject("baseColorTexture") : null;
            float[] factor = pbr != null && pbr.has("baseColorFactor") ? vec(pbr.getAsJsonArray("baseColorFactor")) : null;
            float[] emissive = mat.has("emissiveFactor") ? vec(mat.getAsJsonArray("emissiveFactor")) : null;
            boolean emissiveLit = emissive != null && (emissive[0] + emissive[1] + emissive[2]) > 0.003f;
            if (colorTexture == null && mat.has("emissiveTexture")) {
                colorTexture = mat.getAsJsonObject("emissiveTexture");
                factor = emissive;
            } else if (colorTexture == null && (factor == null || isWhite(factor)) && emissiveLit) {
                factor = emissive;
            }
            int color = factor == null ? -1 : 0xFF000000 | srgb(factor[0]) << 16 | srgb(factor[1]) << 8 | srgb(factor[2]);
            materialColor.put(m, color);
            materialBlend.put(m, mat.has("alphaMode") && mat.get("alphaMode").getAsString().equals("BLEND"));
            if (colorTexture != null) {
                try {
                    Texture t = texture(colorTexture.get("index").getAsInt());
                    if (t != null) { materialTexture.put(m, textures.size()); textures.add(t); }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean inScene(int node) {
        JsonArray scenes = arr(gltf, "scenes");
        if (scenes.isEmpty()) return true;
        JsonObject scene = scenes.get(intOr(gltf, "scene", 0)).getAsJsonObject();
        int root = node;
        while (parent[root] >= 0) root = parent[root];
        for (JsonElement r : arr(scene, "nodes")) if (r.getAsInt() == root) return true;
        return false;
    }

    private void category(int i, int[] cat) {
        if (cat[i] != -2) return;
        int byName = categoryOfName(nodeName(i));
        if (byName >= 0) { cat[i] = byName; return; }
        if (parent[i] >= 0) {
            category(parent[i], cat);
            cat[i] = cat[parent[i]];
        } else cat[i] = -1;
    }

    private static int sided(int category, float x, float centreX) {
        if (category == RIGHT_ARM || category == LEFT_ARM) return x < centreX ? RIGHT_ARM : LEFT_ARM;
        if (category == RIGHT_LEG || category == LEFT_LEG) return x < centreX ? RIGHT_LEG : LEFT_LEG;
        return category;
    }

    private Vector3f pos(int node) {
        return global[node].getTranslation(new Vector3f());
    }

    private float facingSign(java.util.Set<Integer> joints, int[] cat, float centreX) {
        float sum = 0;
        for (int j : joints) {
            String name = nodeName(j).toLowerCase(Locale.ROOT);
            if (!(name.contains("toe") || name.contains("つま先")) || parent[j] < 0) continue;
            sum += pos(j).z - pos(parent[j]).z;
        }
        return sum < -1e-4f ? -1f : 1f;
    }

    private static float[] faceNormal(float[] p, int a, int b, int c) {
        Vector3f u = new Vector3f(p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1], p[b * 3 + 2] - p[a * 3 + 2]);
        Vector3f v = new Vector3f(p[c * 3] - p[a * 3], p[c * 3 + 1] - p[a * 3 + 1], p[c * 3 + 2] - p[a * 3 + 2]);
        Vector3f n = u.cross(v);
        if (n.lengthSquared() > 0) n.normalize();
        return new float[]{n.x, n.y, n.z};
    }

    private static float[] vec(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static boolean isWhite(float[] f) {
        return f[0] > 0.99f && f[1] > 0.99f && f[2] > 0.99f;
    }

    private static int srgb(float linear) {
        return Math.round(255f * (float) Math.pow(Math.max(0, Math.min(1, linear)), 1 / 2.2));
    }

    private Texture texture(int textureIndex) throws IOException {
        JsonObject tex = arr(gltf, "textures").get(textureIndex).getAsJsonObject();
        if (!tex.has("source")) return null;
        JsonObject image = arr(gltf, "images").get(tex.get("source").getAsInt()).getAsJsonObject();
        byte[] data;
        if (image.has("bufferView")) {
            ByteBuffer b = bufferView(image.get("bufferView").getAsInt());
            data = new byte[b.remaining()];
            b.get(data);
        } else if (image.has("uri")) data = uri(image.get("uri").getAsString());
        else return null;
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
        if (img == null) return null;
        int w = img.getWidth(), h = img.getHeight();
        if (Math.max(w, h) > MAX_TEXTURE) {
            float k = (float) MAX_TEXTURE / Math.max(w, h);
            int nw = Math.max(1, Math.round(w * k)), nh = Math.max(1, Math.round(h * k));
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = scaled;
            w = nw;
            h = nh;
        }
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        return new Texture(w, h, argb);
    }
}
