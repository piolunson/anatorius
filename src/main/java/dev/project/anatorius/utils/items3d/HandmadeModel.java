package dev.project.anatorius.utils.items3d;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public final class HandmadeModel {
    private static final int STRIDE = 3 + 4 * 5;
    private static final int MAX_QUADS = 4096;
    private static final float UV_INSET = 0.02f;

    private final float[] data;
    private final int quads;

    private HandmadeModel(float[] data, int quads) {
        this.data = data;
        this.quads = quads;
    }

    public int quadCount() {
        return quads;
    }

    public String extremes() {
        float bestHi = -Float.MAX_VALUE, bestLo = Float.MAX_VALUE;
        float[] hi = new float[2], lo = new float[2];
        for (int q = 0, o = 0; q < quads; q++, o += STRIDE) {
            for (int v = 0, p = o + 3; v < 4; v++, p += 5) {
                float s = data[p] + data[p + 1];
                if (s > bestHi) { bestHi = s; hi[0] = data[p]; hi[1] = data[p + 1]; }
                if (s < bestLo) { bestLo = s; lo[0] = data[p]; lo[1] = data[p + 1]; }
            }
        }
        return String.format(java.util.Locale.ROOT, "tip(%.1f,%.1f) pommel(%.1f,%.1f)", hi[0], hi[1], lo[0], lo[1]);
    }

    public static HandmadeModel load(ResourceManager manager, Identifier json) {
        Optional<Resource> resource = manager.getResource(json);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().getReader()) {
            return bake(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            return null;
        }
    }

    static HandmadeModel bake(JsonObject root) {
        JsonArray elements = root.getAsJsonArray("elements");
        if (elements == null) return null;
        float[] out = new float[STRIDE * 64];
        int count = 0;
        for (JsonElement el : elements) {
            JsonObject e = el.getAsJsonObject();
            float[] from = vec(e.getAsJsonArray("from")), to = vec(e.getAsJsonArray("to"));
            Matrix3f rotation = new Matrix3f();
            Vector3f origin = new Vector3f(8, 8, 8);
            if (e.has("rotation")) {
                JsonObject r = e.getAsJsonObject("rotation");
                if (r.has("origin")) { float[] o = vec(r.getAsJsonArray("origin")); origin.set(o[0], o[1], o[2]); }
                if (r.has("axis")) {
                    float a = (float) Math.toRadians(r.get("angle").getAsFloat());
                    switch (r.get("axis").getAsString()) {
                        case "x" -> rotation.rotationX(a);
                        case "y" -> rotation.rotationY(a);
                        default -> rotation.rotationZ(a);
                    }
                } else {
                    float x = r.has("x") ? r.get("x").getAsFloat() : 0f, y = r.has("y") ? r.get("y").getAsFloat() : 0f, z = r.has("z") ? r.get("z").getAsFloat() : 0f;
                    rotation.rotationZ((float) Math.toRadians(z)).rotateY((float) Math.toRadians(y)).rotateX((float) Math.toRadians(x));
                }
            }
            JsonObject faces = e.getAsJsonObject("faces");
            if (faces == null) continue;
            for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                JsonObject face = f.getValue().getAsJsonObject();
                float[] uv = face.has("uv") ? vec(face.getAsJsonArray("uv")) : new float[]{0, 0, 16, 16};
                int uvRotation = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
                float[][] corners = corners(f.getKey(), from, to);
                if (corners == null) continue;
                if ((count + 1) * STRIDE > out.length) out = Arrays.copyOf(out, out.length * 2);
                writeFace(out, count * STRIDE, f.getKey(), corners, uv, uvRotation, rotation, origin);
                if (++count > MAX_QUADS) return null;
            }
        }
        return count == 0 ? null : new HandmadeModel(Arrays.copyOf(out, count * STRIDE), count);
    }

    private static float[] vec(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    private static float[][] corners(String face, float[] f, float[] t) {
        float x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
        return switch (face) {
            case "north" -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case "south" -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case "west" -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case "east" -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
            case "up" -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case "down" -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            default -> null;
        };
    }

    private static Vector3f normalOf(String face) {
        return switch (face) {
            case "north" -> new Vector3f(0, 0, -1);
            case "south" -> new Vector3f(0, 0, 1);
            case "west" -> new Vector3f(-1, 0, 0);
            case "east" -> new Vector3f(1, 0, 0);
            case "up" -> new Vector3f(0, 1, 0);
            default -> new Vector3f(0, -1, 0);
        };
    }

    private static void writeFace(float[] out, int o, String face, float[][] c, float[] uv, int uvRotation, Matrix3f rotation, Vector3f origin) {
        Vector3f n = rotation.transform(normalOf(face));
        out[o] = n.x; out[o + 1] = n.y; out[o + 2] = n.z;
        float u0 = uv[0] / 16f, v0 = uv[1] / 16f, u1 = uv[2] / 16f, v1 = uv[3] / 16f;
        float su = (u1 - u0) * UV_INSET, sv = (v1 - v0) * UV_INSET;
        u0 += su; u1 -= su; v0 += sv; v1 -= sv;
        float[][] tex = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        int shift = ((uvRotation / 90) % 4 + 4) % 4;
        for (int i = 0; i < 4; i++) {
            Vector3f p = new Vector3f(c[i][0], c[i][1], c[i][2]).sub(origin);
            rotation.transform(p).add(origin);
            float[] t = tex[(i + shift) % 4];
            int p0 = o + 3 + i * 5;
            out[p0] = p.x; out[p0 + 1] = p.y; out[p0 + 2] = p.z; out[p0 + 3] = t[0]; out[p0 + 4] = t[1];
        }
    }

    public void emit(MatrixStack.Entry entry, VertexConsumer consumer, int light, int argb) {
        float[] d = data;
        for (int q = 0, o = 0; q < quads; q++, o += STRIDE) {
            float nx = d[o], ny = d[o + 1], nz = d[o + 2];
            for (int v = 0, p = o + 3; v < 4; v++, p += 5) {
                consumer.vertex(entry, d[p], d[p + 1], d[p + 2]).color(argb).texture(d[p + 3], d[p + 4])
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
            }
        }
    }
}
