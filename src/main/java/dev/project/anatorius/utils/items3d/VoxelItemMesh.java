package dev.project.anatorius.utils.items3d;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Arrays;

public final class VoxelItemMesh {
    private static final int STRIDE = 3 + 4 * 5;
    public static final int MAX_SIZE = 128;
    private static final int MAX_QUADS = 24_000;

    private final float[] data;
    private final int quads;

    private VoxelItemMesh(float[] data, int quads) {
        this.data = data;
        this.quads = quads;
    }

    public int quadCount() {
        return quads;
    }

    public static VoxelItemMesh build(Sprite sprite, int steps, float thickness) {
        SpriteContents contents = sprite.getContents();
        int w = contents.getWidth(), h = contents.getHeight();
        if (w <= 0 || h <= 0 || w > MAX_SIZE || h > MAX_SIZE) return null;

        boolean[] solid = new boolean[w * h];
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!contents.isPixelTransparent(0, x, y)) { solid[y * w + x] = true; count++; }
            }
        }
        if (count == 0) return null;

        int[] dist = new int[w * h];
        int inf = w + h;
        for (int i = 0; i < dist.length; i++) dist[i] = solid[i] ? inf : 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (dist[i] == 0) continue;
                int up = y > 0 ? dist[i - w] : 0, left = x > 0 ? dist[i - 1] : 0;
                dist[i] = Math.min(dist[i], Math.min(up, left) + 1);
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (dist[i] == 0) continue;
                int down = y < h - 1 ? dist[i + w] : 0, right = x < w - 1 ? dist[i + 1] : 0;
                dist[i] = Math.min(dist[i], Math.min(down, right) + 1);
            }
        }

        float px = 1f / w;
        float[] half = new float[w * h];
        for (int i = 0; i < half.length; i++) {
            if (!solid[i]) continue;
            int bevel = Math.min(dist[i] - 1, steps);
            half[i] = (0.75f + 0.55f * bevel) * thickness * px;
        }

        float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();
        float du = (u1 - u0) / w, dv = (v1 - v0) / h;
        Builder b = new Builder();

        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                int i = y * w + x;
                if (!solid[i]) { x++; continue; }
                int end = x + 1;
                while (end < w && solid[y * w + end] && half[y * w + end] == half[i]) end++;
                float hz = half[i];
                float xa = x * px, xb = end * px, ya = 1f - (y + 1) / (float) h, yb = 1f - y / (float) h;
                float ua = u0 + x * du, ub = u0 + end * du, va = v0 + y * dv, vb = v0 + (y + 1) * dv;
                b.quad(0, 0, 1,
                        xa, ya, hz, ua, vb,  xb, ya, hz, ub, vb,  xb, yb, hz, ub, va,  xa, yb, hz, ua, va);
                b.quad(0, 0, -1,
                        xb, ya, -hz, ub, vb,  xa, ya, -hz, ua, vb,  xa, yb, -hz, ua, va,  xb, yb, -hz, ub, va);
                x = end;
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!solid[i]) continue;
                float hz = half[i];
                float uc = u0 + (x + 0.5f) * du, vc = v0 + (y + 0.5f) * dv;
                float xa = x * px, xb = (x + 1) * px, ya = 1f - (y + 1) / (float) h, yb = 1f - y / (float) h;
                side(b, x > 0 && solid[i - 1] ? half[i - 1] : -1f, hz, -1, 0, xa, ya, xa, yb, uc, vc);
                side(b, x < w - 1 && solid[i + 1] ? half[i + 1] : -1f, hz, 1, 0, xb, yb, xb, ya, uc, vc);
                side(b, y > 0 && solid[i - w] ? half[i - w] : -1f, hz, 0, 1, xa, yb, xb, yb, uc, vc);
                side(b, y < h - 1 && solid[i + w] ? half[i + w] : -1f, hz, 0, -1, xb, ya, xa, ya, uc, vc);
                if (b.count > MAX_QUADS) return null;
            }
        }
        return new VoxelItemMesh(Arrays.copyOf(b.data, b.count * STRIDE), b.count);
    }

    private static void side(Builder b, float neighbour, float hz, int nx, int ny, float ax, float ay, float bx, float by, float u, float v) {
        if (neighbour < 0f) {
            b.quad(nx, ny, 0, ax, ay, -hz, u, v,  bx, by, -hz, u, v,  bx, by, hz, u, v,  ax, ay, hz, u, v);
        } else if (neighbour < hz) {
            b.quad(nx, ny, 0, ax, ay, neighbour, u, v,  bx, by, neighbour, u, v,  bx, by, hz, u, v,  ax, ay, hz, u, v);
            b.quad(nx, ny, 0, ax, ay, -hz, u, v,  bx, by, -hz, u, v,  bx, by, -neighbour, u, v,  ax, ay, -neighbour, u, v);
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

    private static final class Builder {
        float[] data = new float[STRIDE * 256];
        int count;

        void quad(float nx, float ny, float nz,
                  float x0, float y0, float z0, float u0, float v0,
                  float x1, float y1, float z1, float u1, float v1,
                  float x2, float y2, float z2, float u2, float v2,
                  float x3, float y3, float z3, float u3, float v3) {
            if ((count + 1) * STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            int o = count * STRIDE;
            float[] d = data;
            d[o] = nx; d[o + 1] = ny; d[o + 2] = nz;
            d[o + 3] = x0; d[o + 4] = y0; d[o + 5] = z0; d[o + 6] = u0; d[o + 7] = v0;
            d[o + 8] = x1; d[o + 9] = y1; d[o + 10] = z1; d[o + 11] = u1; d[o + 12] = v1;
            d[o + 13] = x2; d[o + 14] = y2; d[o + 15] = z2; d[o + 16] = u2; d[o + 17] = v2;
            d[o + 18] = x3; d[o + 19] = y3; d[o + 20] = z3; d[o + 21] = u3; d[o + 22] = v3;
            count++;
        }
    }
}
