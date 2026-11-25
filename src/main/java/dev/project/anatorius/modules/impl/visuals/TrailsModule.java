package dev.project.anatorius.modules.impl.visuals;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.graphics.ChunkOverlayRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@RegisterModule(name = "Trails", description = "Draws smooth trails behind you and things you throw.", category = Module.Category.VISUALS)
public class TrailsModule extends Module {
    public final NumberSetting lifetime = new NumberSetting("Lifetime", "Seconds until a point of the trail has faded out.", 3.0, 0.5, 8.0);
    public final NumberSetting lineWidth = new NumberSetting("LineWidth", "Thickness of the line, in blocks.", 0.07, 0.01, 0.4);
    public final BooleanSetting bloom = new BooleanSetting("Bloom", "Soft glow around the line.", true);
    public final ColorSetting color = new ColorSetting("Color", "Colour of the trails.", new ColorSetting.Color(new Color(130, 202, 255, 235), false, false));

    public final CategorySetting targets = new CategorySetting("Targets", "What gets a trail.");
    public final BooleanSetting self = new BooleanSetting("Self", "A trail behind you.", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Arrows from bows and crossbows.", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting pearls = new BooleanSetting("Pearls", "Ender pearls.", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting tridents = new BooleanSetting("Tridents", "Thrown tridents.", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting throwables = new BooleanSetting("Throwables", "Snowballs, eggs and experience bottles.", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting potions = new BooleanSetting("Potions", "Splash and lingering potions.", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting windCharges = new BooleanSetting("WindCharges", "Wind charges.", new CategorySetting.Visibility(targets), true);

    private static final int CAPACITY = 176;
    private static final int MAX_TRAILS = 48;
    private static final double GAP = 8.0;
    private static final double MIN_STEP = 0.04;
    private static final int SELF_ID = Integer.MIN_VALUE;
    private static final int VB = ChunkOverlayRenderer.VERTEX_BYTES;
    private static final float[] OFFS = {-1f, -0.5f, 0f, 0.5f, 1f};
    private static final float[] AMUL = {0f, 0.75f, 1f, 0.75f, 0f};

    private static final class Pipelines {
        static final RenderPipeline LINE = build("trails_line", BlendFunction.TRANSLUCENT);
        static final RenderPipeline GLOW = build("trails_glow", BlendFunction.ADDITIVE);

        private static RenderPipeline build(String name, BlendFunction blend) {
            return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/anatorius_" + name)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withBlend(blend)
                    .build());
        }
    }

    private static final class Trail {
        final double[] x = new double[CAPACITY], y = new double[CAPACITY], z = new double[CAPACITY];
        final long[] tick = new long[CAPACITY];
        final boolean[] brk = new boolean[CAPACITY];
        int head, size;
        boolean ended;
        double yOffset;

        int index(int i) { return (head - size + i + CAPACITY) % CAPACITY; }

        void add(double px, double py, double pz, long t, boolean broken) {
            x[head] = px; y[head] = py; z[head] = pz; tick[head] = t; brk[head] = broken;
            head = (head + 1) % CAPACITY;
            if (size < CAPACITY) size++;
        }
    }

    private final Map<Integer, Trail> trails = new HashMap<>();
    private ClientWorld lastWorld;
    private long ticks;

    private final double[] px = new double[CAPACITY + 2], py = new double[CAPACITY + 2], pz = new double[CAPACITY + 2], pa = new double[CAPACITY + 2];
    private final boolean[] pb = new boolean[CAPACITY + 2];
    private float[] sx = new float[1024], sy = new float[1024], sz = new float[1024], sage = new float[1024];
    private final float[] linePrev = new float[15], lineCur = new float[15], glowPrev = new float[9], glowCur = new float[9];
    private final int[] lineColPrev = new int[5], lineColCur = new int[5], glowColPrev = new int[3], glowColCur = new int[3];
    private ByteBuffer glowBuf, lineBuf, mergedBuf;
    private GpuBuffer gpu;
    private boolean warned;

    @Override
    public void onEnable() {
        trails.clear();
        lastWorld = null;
    }

    @Override
    public void onDisable() {
        trails.clear();
        lastWorld = null;
        if (gpu != null) { gpu.close(); gpu = null; }
        glowBuf = free(glowBuf);
        lineBuf = free(lineBuf);
        mergedBuf = free(mergedBuf);
    }

    private static ByteBuffer free(ByteBuffer b) {
        if (b != null) MemoryUtil.memFree(b);
        return null;
    }

    private boolean wanted(Entity e) {
        if (e instanceof PersistentProjectileEntity) return e instanceof TridentEntity ? tridents.getValue() : arrows.getValue();
        if (e instanceof EnderPearlEntity) return pearls.getValue();
        if (e instanceof PotionEntity) return potions.getValue();
        if (e instanceof SnowballEntity || e instanceof EggEntity || e instanceof ExperienceBottleEntity) return throwables.getValue();
        return e instanceof AbstractWindChargeEntity && windCharges.getValue();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;
        if (mc.world != lastWorld) {
            trails.clear();
            lastWorld = mc.world;
        }
        ticks++;
        long expireBefore = ticks - (long) Math.ceil(lifetime.getValue().doubleValue() * 20.0) - 1;

        if (self.getValue()) record(SELF_ID, mc.player);

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isRemoved() || !wanted(e)) continue;
            record(e.getId(), e);
        }

        for (Iterator<Map.Entry<Integer, Trail>> it = trails.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Trail> en = it.next();
            Trail t = en.getValue();
            if (en.getKey() == SELF_ID) {
                if (!self.getValue()) t.ended = true;
            } else {
                Entity e = mc.world.getEntityById(en.getKey());
                if (e == null || e.isRemoved() || !wanted(e)) t.ended = true;
            }
            while (t.size > 0 && t.tick[t.index(0)] < expireBefore) t.size--;
            if (t.size == 0 && t.ended) it.remove();
        }
    }

    private void record(int id, Entity e) {
        Trail t = trails.get(id);
        if (t == null) {
            if (trails.size() >= MAX_TRAILS) return;
            t = new Trail();
            trails.put(id, t);
        }
        t.ended = false;
        t.yOffset = id == SELF_ID ? e.getHeight() * 0.45 : e.getHeight() * 0.5;
        double x = e.getX(), y = e.getY() + t.yOffset, z = e.getZ();
        if (t.size > 0) {
            int l = t.index(t.size - 1);
            double d2 = sq(x - t.x[l], y - t.y[l], z - t.z[l]);
            if (d2 < MIN_STEP * MIN_STEP) return;
            t.add(x, y, z, ticks, d2 > GAP * GAP);
        } else {
            t.add(x, y, z, ticks, false);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || trails.isEmpty()) return;
        try {
            render(event.getTickDelta());
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Anatorius.LOGGER.warn("[Anatorius] Trails draw failed: {}", t.toString());
            }
        }
    }

    private void render(float delta) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        double life = Math.max(0.5, lifetime.getValue().doubleValue());
        float baseW = lineWidth.getValue().floatValue() * 0.5f;
        boolean glow = bloom.getValue();
        Color c = color.getColor();
        float alpha0 = c.getAlpha() / 255f;
        if (alpha0 <= 0f) return;

        if (glowBuf == null) glowBuf = MemoryUtil.memAlloc(1 << 16);
        if (lineBuf == null) lineBuf = MemoryUtil.memAlloc(1 << 16);
        glowBuf.clear();
        lineBuf.clear();

        for (Map.Entry<Integer, Trail> en : trails.entrySet()) {
            Trail t = en.getValue();
            int n = 0;
            for (int i = 0; i < t.size; i++) {
                int j = t.index(i);
                px[n] = t.x[j] - cam.x; py[n] = t.y[j] - cam.y; pz[n] = t.z[j] - cam.z;
                pa[n] = Math.max(0.0, (ticks + delta - t.tick[j]) / 20.0);
                pb[n] = t.brk[j];
                n++;
            }
            if (!t.ended) {
                Entity e = en.getKey() == SELF_ID ? mc.player : mc.world.getEntityById(en.getKey());
                if (e != null && !e.isRemoved()) {
                    Vec3d h = e.getLerpedPos(delta);
                    double hx = h.x - cam.x, hy = h.y + t.yOffset - cam.y, hz = h.z - cam.z;
                    double d2 = n > 0 ? sq(hx - px[n - 1], hy - py[n - 1], hz - pz[n - 1]) : Double.MAX_VALUE;
                    if (n == 0 || d2 > 1.0e-6) {
                        px[n] = hx; py[n] = hy; pz[n] = hz; pa[n] = 0; pb[n] = d2 > GAP * GAP && n > 0;
                        n++;
                    }
                }
            }
            if (n < 2) continue;

            int start = 0;
            for (int i = 1; i <= n; i++) {
                if (i == n || pb[i]) {
                    if (i - start >= 2) ribbon(start, i, life, baseW, c, alpha0, glow);
                    start = i;
                }
            }
        }

        int glowBytes = glowBuf.position(), lineBytes = lineBuf.position();
        int total = glowBytes + lineBytes;
        if (total == 0) return;
        if (mergedBuf == null || mergedBuf.capacity() < total) {
            mergedBuf = mergedBuf == null ? MemoryUtil.memAlloc(Math.max(total, 1 << 16))
                    : MemoryUtil.memRealloc(mergedBuf, Math.max(total, mergedBuf.capacity() * 2));
        }
        mergedBuf.clear();
        glowBuf.flip();
        lineBuf.flip();
        mergedBuf.put(glowBuf).put(lineBuf).flip();

        if (gpu == null || gpu.size() < total) {
            long size = Math.max(total, gpu == null ? 1 << 16 : gpu.size() * 2);
            if (gpu != null) gpu.close();
            gpu = RenderSystem.getDevice().createBuffer(() -> "anatorius trails", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(gpu.slice(0, total), mergedBuf);

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(new Matrix4f(RenderSystem.getModelViewMatrix()), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
        Framebuffer fb = mc.getFramebuffer();
        int glowVerts = glowBytes / VB, lineVerts = lineBytes / VB;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "anatorius trails",
                fb.getColorAttachmentView(), OptionalInt.empty(), fb.getDepthAttachmentView(), OptionalDouble.empty())) {
            if (glowVerts > 0) draw(pass, Pipelines.GLOW, transforms, 0, glowVerts);
            if (lineVerts > 0) draw(pass, Pipelines.LINE, transforms, glowVerts, lineVerts);
        }
    }

    private void draw(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transforms, int first, int count) {
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, gpu);
        pass.draw(first, count);
    }

    private static double sq(double a, double b, double c) { return a * a + b * b + c * c; }

    private void ribbon(int from, int to, double life, float baseW, Color c, float alpha0, boolean glow) {
        int m = 0;
        for (int i = from; i < to - 1; i++) {
            int i0 = Math.max(from, i - 1), i3 = Math.min(to - 1, i + 2);
            double len = Math.sqrt(sq(px[i + 1] - px[i], py[i + 1] - py[i], pz[i + 1] - pz[i]));
            int sub = Math.max(2, Math.min(6, (int) Math.ceil(len * 3.0)));
            int count = sub + (i == to - 2 ? 1 : 0);
            while (m + count >= sx.length) grow();
            for (int s = 0; s < count; s++) {
                double u = s / (double) sub, u2 = u * u, u3 = u2 * u;
                double b0 = -0.5 * u3 + u2 - 0.5 * u, b1 = 1.5 * u3 - 2.5 * u2 + 1.0, b2 = -1.5 * u3 + 2.0 * u2 + 0.5 * u, b3 = 0.5 * u3 - 0.5 * u2;
                sx[m] = (float) (b0 * px[i0] + b1 * px[i] + b2 * px[i + 1] + b3 * px[i3]);
                sy[m] = (float) (b0 * py[i0] + b1 * py[i] + b2 * py[i + 1] + b3 * py[i3]);
                sz[m] = (float) (b0 * pz[i0] + b1 * pz[i] + b2 * pz[i + 1] + b3 * pz[i3]);
                sage[m] = (float) (pa[i] + (pa[i + 1] - pa[i]) * u);
                m++;
            }
        }
        if (m < 2) return;

        float sideX = 0f, sideY = 1f, sideZ = 0f;
        for (int i = 0; i < m; i++) {
            int a = Math.max(0, i - 1), b = Math.min(m - 1, i + 1);
            float tx = sx[b] - sx[a], ty = sy[b] - sy[a], tz = sz[b] - sz[a];
            float nx = ty * sz[i] - tz * sy[i], ny = tz * sx[i] - tx * sz[i], nz = tx * sy[i] - ty * sx[i];
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1.0e-6f) { sideX = nx / nl; sideY = ny / nl; sideZ = nz / nl; }

            float f = (float) Math.max(0.0, 1.0 - sage[i] / life);
            float fade = f * f * (3f - 2f * f);
            float dist = (float) Math.sqrt(sx[i] * sx[i] + sy[i] * sy[i] + sz[i] * sz[i]);
            float w = Math.max(baseW * (0.3f + 0.7f * (float) Math.pow(f, 0.6)), dist * 0.0012f);
            float al = alpha0 * fade;
            float hot = 0.4f * f * f * f;
            int r = Math.round(c.getRed() + (255 - c.getRed()) * hot);
            int g = Math.round(c.getGreen() + (255 - c.getGreen()) * hot);
            int bl = Math.round(c.getBlue() + (255 - c.getBlue()) * hot);

            for (int k = 0; k < 5; k++) {
                lineCur[k * 3] = sx[i] + sideX * w * OFFS[k];
                lineCur[k * 3 + 1] = sy[i] + sideY * w * OFFS[k];
                lineCur[k * 3 + 2] = sz[i] + sideZ * w * OFFS[k];
                lineColCur[k] = (clamp(al * AMUL[k]) << 24) | (r << 16) | (g << 8) | bl;
            }
            if (glow) {
                float gw = w * 3.5f + 0.03f, ga = al * 0.4f;
                for (int k = 0; k < 3; k++) {
                    float o = k - 1, am = k == 1 ? ga : 0f;
                    glowCur[k * 3] = sx[i] + sideX * gw * o;
                    glowCur[k * 3 + 1] = sy[i] + sideY * gw * o;
                    glowCur[k * 3 + 2] = sz[i] + sideZ * gw * o;
                    glowColCur[k] = (255 << 24) | (Math.round(r * am) << 16) | (Math.round(g * am) << 8) | Math.round(bl * am);
                }
            }
            if (i > 0) {
                lineBuf = ensure(lineBuf, 4 * 6 * VB);
                for (int k = 0; k < 4; k++) quad(lineBuf, linePrev, lineColPrev, lineCur, lineColCur, k);
                if (glow) {
                    glowBuf = ensure(glowBuf, 2 * 6 * VB);
                    for (int k = 0; k < 2; k++) quad(glowBuf, glowPrev, glowColPrev, glowCur, glowColCur, k);
                }
            }
            System.arraycopy(lineCur, 0, linePrev, 0, 15);
            System.arraycopy(lineColCur, 0, lineColPrev, 0, 5);
            System.arraycopy(glowCur, 0, glowPrev, 0, 9);
            System.arraycopy(glowColCur, 0, glowColPrev, 0, 3);
        }
    }

    private static void quad(ByteBuffer b, float[] p, int[] pc, float[] q, int[] qc, int k) {
        int a = k * 3, c = (k + 1) * 3;
        ChunkOverlayRenderer.vertex(b, p[a], p[a + 1], p[a + 2], pc[k]);
        ChunkOverlayRenderer.vertex(b, p[c], p[c + 1], p[c + 2], pc[k + 1]);
        ChunkOverlayRenderer.vertex(b, q[a], q[a + 1], q[a + 2], qc[k]);
        ChunkOverlayRenderer.vertex(b, p[c], p[c + 1], p[c + 2], pc[k + 1]);
        ChunkOverlayRenderer.vertex(b, q[c], q[c + 1], q[c + 2], qc[k + 1]);
        ChunkOverlayRenderer.vertex(b, q[a], q[a + 1], q[a + 2], qc[k]);
    }

    private static int clamp(float a) {
        return Math.max(0, Math.min(255, Math.round(a * 255f)));
    }

    private static ByteBuffer ensure(ByteBuffer b, int extra) {
        if (b.remaining() >= extra) return b;
        int pos = b.position();
        ByteBuffer nb = MemoryUtil.memRealloc(b, Math.max(b.capacity() * 2, pos + extra));
        nb.limit(nb.capacity()).position(pos);
        return nb;
    }

    private void grow() {
        int n = sx.length * 2;
        sx = Arrays.copyOf(sx, n);
        sy = Arrays.copyOf(sy, n);
        sz = Arrays.copyOf(sz, n);
        sage = Arrays.copyOf(sage, n);
    }

    public int pointCount() {
        int n = 0;
        for (Trail t : trails.values()) n += t.size;
        return n;
    }

    public int trailCount() {
        return trails.size();
    }

    @Override
    public String getMetaData() {
        return String.valueOf(trails.size());
    }
}
