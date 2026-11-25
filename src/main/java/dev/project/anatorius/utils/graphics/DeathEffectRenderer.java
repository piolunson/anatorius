package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;

public final class DeathEffectRenderer implements IMinecraft {
    public enum Type { SOUL, SHOCKWAVE, BURST, PILLAR, SHATTER, VORTEX }

    public record Style(float scale, float duration, float density, Color primary, Color secondary, boolean rainbow) { }

    private static final int VB = ChunkOverlayRenderer.VERTEX_BYTES;
    private static final int MAX_PARTICLES = 3500;
    private static final int MAX_EFFECTS = 24;
    private static final float TAU = (float) (Math.PI * 2.0);

    private static final byte DOT = 0, STREAK = 1, SHARD = 2, EMBER = 3, ORBIT = 4;

    private static final class Pipelines {
        static final RenderPipeline GLOW = build("deathfx_glow", BlendFunction.ADDITIVE);
        static final RenderPipeline SOLID = build("deathfx_solid", BlendFunction.TRANSLUCENT);

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

    private static final class Particle {
        byte kind;
        double x, y, z, px, py, pz, vx, vy, vz;
        float gravity, drag = 1f;
        float age, life, dt;
        float size;
        int rgb;
        float streak = 1f;
        double floor = Double.NaN;
        float axX, axY = 1f, axZ, rot, prot, spin;
        double cx, cy, cz, startY;
        float a0, r0, w;
        float flicker;
    }

    private static final class Effect {
        Type type;
        double x, y, z, ground, chest;
        float height, age, life, dt, scale, density;
        int c1, c2;
        boolean rainbow;
        final Random rnd = new Random();
    }

    private final List<Effect> effects = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private ClientWorld lastWorld;

    private float rx, ry, rz, ux, uy, uz;
    private ByteBuffer glowBuf, solidBuf, mergedBuf;
    private GpuBuffer gpu;
    private boolean warned;

    private static final int FAN = 10;
    private static final float[] FCOS = new float[FAN + 1], FSIN = new float[FAN + 1];
    static {
        for (int i = 0; i <= FAN; i++) {
            FCOS[i] = (float) Math.cos(i * TAU / FAN);
            FSIN[i] = (float) Math.sin(i * TAU / FAN);
        }
    }

    public void spawn(Type type, Vec3d feet, float height, Style style) {
        if (mc.world == null) return;
        if (effects.size() >= MAX_EFFECTS) effects.remove(0);
        Effect e = new Effect();
        e.type = type;
        e.x = feet.x; e.y = feet.y; e.z = feet.z;
        e.height = Math.max(0.6f, height);
        e.chest = e.y + e.height * 0.55;
        e.ground = groundBelow(feet);
        if (Double.isNaN(e.ground)) e.ground = e.y;
        e.scale = Math.max(0.2f, style.scale());
        e.dt = 1f / Math.max(0.25f, style.duration());
        e.density = Math.max(0.1f, style.density());
        e.c1 = style.primary().getRGB() & 0xFFFFFF;
        e.c2 = style.secondary().getRGB() & 0xFFFFFF;
        e.rainbow = style.rainbow();
        e.life = switch (type) {
            case SOUL -> 52;
            case SHOCKWAVE -> 34;
            case BURST -> 30;
            case PILLAR -> 46;
            case SHATTER -> 10;
            case VORTEX -> 38;
        };
        effects.add(e);
        onStart(e);
    }

    public void clear() {
        effects.clear();
        particles.clear();
    }

    public boolean isEmpty() {
        return effects.isEmpty() && particles.isEmpty();
    }

    public int particleCount() {
        return particles.size();
    }

    public void close() {
        clear();
        if (gpu != null) { gpu.close(); gpu = null; }
        glowBuf = free(glowBuf);
        solidBuf = free(solidBuf);
        mergedBuf = free(mergedBuf);
    }

    private static ByteBuffer free(ByteBuffer b) {
        if (b != null) MemoryUtil.memFree(b);
        return null;
    }

    private static double groundBelow(Vec3d feet) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        int fx = (int) Math.floor(feet.x), fz = (int) Math.floor(feet.z);
        int top = (int) Math.floor(feet.y + 0.2);
        for (int y = top; y > top - 9; y--) {
            p.set(fx, y, fz);
            BlockState s = mc.world.getBlockState(p);
            VoxelShape shape = s.getCollisionShape(mc.world, p);
            if (shape.isEmpty()) continue;
            double t = y + shape.getMax(Direction.Axis.Y);
            if (t <= feet.y + 0.25) return t;
        }
        return Double.NaN;
    }

    private int pick(Effect e) {
        Random r = e.rnd;
        if (e.rainbow) return Color.HSBtoRGB(r.nextFloat(), 0.65f + r.nextFloat() * 0.3f, 1f) & 0xFFFFFF;
        return mix(e.c1, e.c2, r.nextFloat());
    }

    private Particle add(Effect e, byte kind, double x, double y, double z, float life) {
        if (particles.size() >= MAX_PARTICLES) return null;
        Particle p = new Particle();
        p.kind = kind;
        p.x = p.px = x; p.y = p.py = y; p.z = p.pz = z;
        p.life = life;
        p.dt = e.dt;
        p.rgb = pick(e);
        p.flicker = e.rnd.nextFloat() * TAU;
        particles.add(p);
        return p;
    }

    private int count(Effect e, int base) {
        return Math.max(1, Math.round(base * e.density));
    }

    private static float range(Random r, float a, float b) {
        return a + r.nextFloat() * (b - a);
    }

    private static double[] dir(Random r, double up) {
        double y = r.nextDouble() * 2 - 1;
        y = y + (1 - y) * up * r.nextDouble();
        double a = r.nextDouble() * Math.PI * 2, h = Math.sqrt(Math.max(0, 1 - y * y));
        return new double[]{Math.cos(a) * h, y, Math.sin(a) * h};
    }

    private void sparks(Effect e, double x, double y, double z, int n, float minSpeed, float maxSpeed, double up, float life) {
        Random r = e.rnd;
        for (int i = 0; i < n; i++) {
            double[] d = dir(r, up);
            float sp = range(r, minSpeed, maxSpeed) * e.scale;
            Particle p = add(e, STREAK, x, y, z, range(r, life * 0.6f, life));
            if (p == null) return;
            p.vx = d[0] * sp; p.vy = d[1] * sp; p.vz = d[2] * sp;
            p.drag = 0.87f;
            p.gravity = 0.022f;
            p.size = range(r, 0.035f, 0.06f) * e.scale;
            p.streak = range(r, 1.8f, 3.2f);
            p.floor = e.ground;
            if (r.nextFloat() < 0.25f) p.rgb = mix(p.rgb, 0xFFFFFF, 0.6f);
        }
    }

    private void onStart(Effect e) {
        Random r = e.rnd;
        switch (e.type) {
            case SOUL -> {
                for (int i = 0, n = count(e, 22); i < n; i++) {
                    double[] d = dir(r, 0.3);
                    float sp = range(r, 0.04f, 0.12f) * e.scale;
                    Particle p = add(e, DOT, e.x + d[0] * 0.25, e.y + r.nextDouble() * e.height, e.z + d[2] * 0.25, range(r, 14, 24));
                    if (p == null) break;
                    p.vx = d[0] * sp; p.vy = Math.abs(d[1]) * sp * 0.6; p.vz = d[2] * sp;
                    p.drag = 0.86f;
                    p.gravity = -0.003f;
                    p.size = range(r, 0.08f, 0.16f) * e.scale;
                }
            }
            case SHOCKWAVE -> sparks(e, e.x, e.ground + 0.1, e.z, count(e, 26), 0.25f, 0.5f, 0.75, 20);
            case BURST -> {
                sparks(e, e.x, e.chest, e.z, count(e, 75), 0.25f, 0.68f, 0.15, 32);
                for (int i = 0, n = count(e, 34); i < n; i++) {
                    double[] d = dir(r, 0.4);
                    float sp = range(r, 0.03f, 0.12f) * e.scale;
                    Particle p = add(e, EMBER, e.x, e.chest, e.z, range(r, 30, 55));
                    if (p == null) break;
                    p.vx = d[0] * sp; p.vy = d[1] * sp; p.vz = d[2] * sp;
                    p.drag = 0.93f;
                    p.gravity = -0.004f;
                    p.size = range(r, 0.05f, 0.1f) * e.scale;
                }
            }
            case PILLAR -> sparks(e, e.x, e.ground + 0.1, e.z, count(e, 18), 0.15f, 0.35f, 0.4, 18);
            case SHATTER -> {
                float s = (float) Math.sqrt(e.scale);
                for (int i = 0, n = count(e, 38); i < n; i++) {
                    double ox = (r.nextDouble() - 0.5) * 0.6, oy = r.nextDouble() * e.height, oz = (r.nextDouble() - 0.5) * 0.6;
                    Particle p = add(e, SHARD, e.x + ox, e.y + oy, e.z + oz, range(r, 50, 78));
                    if (p == null) break;
                    double hx = ox, hz = oz, hl = Math.sqrt(hx * hx + hz * hz);
                    if (hl < 1e-3) { double a = r.nextDouble() * Math.PI * 2; hx = Math.cos(a); hz = Math.sin(a); hl = 1; }
                    float out = range(r, 0.07f, 0.2f) * e.scale;
                    p.vx = hx / hl * out; p.vz = hz / hl * out;
                    p.vy = range(r, 0.12f, 0.34f) * s;
                    p.drag = 0.98f;
                    p.gravity = 0.04f;
                    p.size = range(r, 0.05f, 0.1f) * s;
                    p.floor = e.ground;
                    Vector3f ax = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f);
                    if (ax.lengthSquared() < 1e-4f) ax.set(0, 1, 0);
                    ax.normalize();
                    p.axX = ax.x; p.axY = ax.y; p.axZ = ax.z;
                    p.rot = p.prot = r.nextFloat() * TAU;
                    p.spin = range(r, 0.15f, 0.5f) * (r.nextBoolean() ? 1 : -1);
                }
                sparks(e, e.x, e.chest, e.z, count(e, 22), 0.15f, 0.4f, 0.3, 18);
            }
            case VORTEX -> {
                for (int i = 0, n = count(e, 70); i < n; i++) {
                    Particle p = add(e, ORBIT, e.x, e.y, e.z, range(r, 22, 28));
                    if (p == null) break;
                    p.cx = e.x; p.cy = e.chest; p.cz = e.z;
                    p.r0 = range(r, 1.1f, 2.3f) * e.scale;
                    p.a0 = r.nextFloat() * TAU;
                    p.w = range(r, 0.18f, 0.3f);
                    p.startY = e.ground + r.nextDouble() * 2.6 * e.scale;
                    p.size = range(r, 0.035f, 0.06f) * e.scale;
                    orbit(p, 0, p);
                    p.px = p.x; p.py = p.y; p.pz = p.z;
                }
            }
        }
    }

    public void tick() {
        if (mc.world != lastWorld) {
            clear();
            lastWorld = mc.world;
        }
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            float before = e.age;
            e.age += e.dt;
            emit(e, before, e.age);
            if (e.age >= e.life) it.remove();
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.px = p.x; p.py = p.y; p.pz = p.z;
            p.prot = p.rot;
            p.age += p.dt;
            if (p.age >= p.life) { it.remove(); continue; }
            if (p.kind == ORBIT) { orbit(p, p.age, p); continue; }
            float dt = p.dt;
            p.vy -= p.gravity * dt;
            double d = Math.pow(p.drag, dt);
            p.vx *= d; p.vy *= d; p.vz *= d;
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
            p.rot += p.spin * dt;
            if (!Double.isNaN(p.floor)) {
                double rest = p.floor + (p.kind == SHARD ? p.size : 0.02);
                if (p.y < rest) {
                    p.y = rest;
                    if (p.vy < 0) p.vy = -p.vy * (p.kind == SHARD ? 0.35 : 0.25);
                    p.vx *= 0.7; p.vz *= 0.7;
                    p.spin *= 0.6f;
                }
            }
        }
    }

    private static void orbit(Particle p, float age, Particle out) {
        float f = Math.min(1f, age / p.life);
        float e = f * f * (3f - 2f * f);
        double r = p.r0 * (1 - e);
        double a = p.a0 + p.w * age * (1 + 2.5 * f);
        out.x = p.cx + Math.cos(a) * r;
        out.z = p.cz + Math.sin(a) * r;
        out.y = p.startY + (p.cy - p.startY) * (f * f);
    }

    private void emit(Effect e, float t0, float t1) {
        Random r = e.rnd;
        switch (e.type) {
            case SOUL -> {
                if (t1 >= e.life) {
                    double[] o = soulOrb(e, e.life);
                    sparks(e, o[0], o[1], o[2], count(e, 26), 0.14f, 0.3f, 0, 18);
                    return;
                }
                double[] o = soulOrb(e, t1);
                float fade = soulFade(e, t1);
                if (fade < 0.05f) return;
                for (int k = 0; k < 3; k++) {
                    double[] w = soulWisp(e, t1, k);
                    Particle p = add(e, DOT, w[0], w[1], w[2], 14);
                    if (p == null) return;
                    p.size = 0.075f * e.scale * fade;
                    p.gravity = -0.002f;
                    p.drag = 0.9f;
                    p.rgb = e.rainbow ? Color.HSBtoRGB((t1 * 0.02f + k / 3f) % 1f, 0.7f, 1f) & 0xFFFFFF : (k == 1 ? e.c1 : e.c2);
                }
                for (int i = 0; i < Math.max(1, Math.round(1.5f * e.density)); i++) {
                    Particle p = add(e, EMBER, o[0] + (r.nextDouble() - 0.5) * 0.3 * e.scale, o[1] - 0.1, o[2] + (r.nextDouble() - 0.5) * 0.3 * e.scale, range(r, 14, 22));
                    if (p == null) return;
                    p.vx = (r.nextDouble() - 0.5) * 0.03; p.vy = -0.01; p.vz = (r.nextDouble() - 0.5) * 0.03;
                    p.drag = 0.92f;
                    p.size = range(r, 0.04f, 0.07f) * e.scale;
                }
            }
            case SHOCKWAVE -> {
                if (t1 > 14) return;
                float rad = ringRadius(e, t1, 0);
                for (int i = 0, n = count(e, 5); i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double cos = Math.cos(a), sin = Math.sin(a);
                    Particle p = add(e, DOT, e.x + cos * rad, e.ground + 0.08, e.z + sin * rad, range(r, 14, 24));
                    if (p == null) return;
                    float out = range(r, 0.05f, 0.14f) * e.scale;
                    p.vx = cos * out; p.vz = sin * out;
                    p.vy = range(r, 0.05f, 0.14f) * e.scale;
                    p.gravity = 0.012f;
                    p.drag = 0.92f;
                    p.size = range(r, 0.05f, 0.09f) * e.scale;
                    p.floor = e.ground;
                }
            }
            case BURST -> {
                if (t0 < 5 && t1 >= 5) sparks(e, e.x, e.chest, e.z, count(e, 30), 0.12f, 0.32f, 0.2, 24);
            }
            case PILLAR -> {
                if (t1 > e.life * 0.8f) return;
                double y0 = e.ground + 0.05;
                for (int k = 0; k < 2; k++) {
                    double a = t1 * 0.55 + k * Math.PI;
                    double rr = 0.75 * e.scale;
                    Particle p = add(e, DOT, e.x + Math.cos(a) * rr, y0, e.z + Math.sin(a) * rr, 32);
                    if (p == null) return;
                    p.vy = 0.24 * e.scale;
                    p.drag = 1f;
                    p.size = 0.08f * e.scale;
                    p.rgb = e.rainbow ? Color.HSBtoRGB((t1 * 0.03f + k * 0.5f) % 1f, 0.7f, 1f) & 0xFFFFFF : (k == 0 ? e.c1 : e.c2);
                }
                for (int i = 0, n = count(e, 4); i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 0.45 * e.scale;
                    Particle p = add(e, STREAK, e.x + Math.cos(a) * rr, y0 + r.nextDouble() * 1.5, e.z + Math.sin(a) * rr, range(r, 12, 20));
                    if (p == null) return;
                    p.vy = range(r, 0.4f, 0.8f) * e.scale;
                    p.drag = 0.97f;
                    p.size = range(r, 0.03f, 0.05f) * e.scale;
                    p.streak = 2.2f;
                }
            }
            case VORTEX -> {
                float implode = 26;
                if (t0 < implode && t1 >= implode) {
                    sparks(e, e.x, e.chest, e.z, count(e, 60), 0.25f, 0.6f, 0.1, 28);
                    for (int i = 0, n = count(e, 20); i < n; i++) {
                        double[] d = dir(r, 0.5);
                        Particle p = add(e, EMBER, e.x, e.chest, e.z, range(r, 26, 44));
                        if (p == null) break;
                        float sp = range(r, 0.03f, 0.1f) * e.scale;
                        p.vx = d[0] * sp; p.vy = d[1] * sp; p.vz = d[2] * sp;
                        p.drag = 0.93f;
                        p.gravity = -0.004f;
                        p.size = range(r, 0.05f, 0.09f) * e.scale;
                    }
                }
            }
            default -> { }
        }
    }

    private static double[] soulOrb(Effect e, float t) {
        float f = Math.min(1f, t / e.life);
        double rise = 3.4 * e.scale * Math.pow(f, 1.5);
        return new double[]{
                e.x + Math.sin(t * 0.21) * 0.22 * e.scale * f,
                e.chest + 0.15 + rise,
                e.z + Math.cos(t * 0.17) * 0.22 * e.scale * f};
    }

    private static double[] soulWisp(Effect e, float t, int k) {
        double[] o = soulOrb(e, t);
        float f = Math.min(1f, t / e.life);
        double a = t * 0.42 + k * TAU / 3.0;
        double r = 0.6 * e.scale * (1 - 0.35 * f);
        return new double[]{o[0] + Math.cos(a) * r, o[1] + Math.sin(t * 0.3 + k * 2.1) * 0.22 * e.scale, o[2] + Math.sin(a) * r};
    }

    private static float soulFade(Effect e, float t) {
        float in = Math.min(1f, t / 4f);
        float out = Math.min(1f, (e.life - t) / (e.life * 0.25f));
        return Math.max(0f, Math.min(in, out));
    }

    private static float ringRadius(Effect e, float t, int k) {
        float p = Math.max(0f, Math.min(1f, (t - k * 3f) / 22f));
        float ease = 1 - (1 - p) * (1 - p) * (1 - p);
        return (0.3f + 5.2f * ease) * e.scale;
    }

    public void render(float delta) {
        if (isEmpty()) return;
        try {
            build(delta);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Anatorius.LOGGER.warn("[Anatorius] DeathEffects draw failed: {}", t.toString());
            }
        }
    }

    private void build(float delta) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionfc rot = mc.gameRenderer.getCamera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(rot), up = new Vector3f(0, 1, 0).rotate(rot);
        rx = right.x; ry = right.y; rz = right.z;
        ux = up.x; uy = up.y; uz = up.z;

        if (glowBuf == null) glowBuf = MemoryUtil.memAlloc(1 << 17);
        if (solidBuf == null) solidBuf = MemoryUtil.memAlloc(1 << 15);
        glowBuf.clear();
        solidBuf.clear();

        for (Effect e : effects) drawEffect(e, cam, delta);
        for (Particle p : particles) drawParticle(p, cam, delta);

        int solidBytes = solidBuf.position(), glowBytes = glowBuf.position();
        int total = solidBytes + glowBytes;
        if (total == 0) return;
        if (mergedBuf == null || mergedBuf.capacity() < total) {
            mergedBuf = mergedBuf == null ? MemoryUtil.memAlloc(Math.max(total, 1 << 17))
                    : MemoryUtil.memRealloc(mergedBuf, Math.max(total, mergedBuf.capacity() * 2));
        }
        mergedBuf.clear();
        solidBuf.flip();
        glowBuf.flip();
        mergedBuf.put(solidBuf).put(glowBuf).flip();

        if (gpu == null || gpu.size() < total) {
            long size = Math.max(total, gpu == null ? 1 << 17 : gpu.size() * 2);
            if (gpu != null) gpu.close();
            gpu = RenderSystem.getDevice().createBuffer(() -> "anatorius death effects", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(gpu.slice(0, total), mergedBuf);

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(new Matrix4f(RenderSystem.getModelViewMatrix()), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
        Framebuffer fb = mc.getFramebuffer();
        int solidVerts = solidBytes / VB, glowVerts = glowBytes / VB;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "anatorius death effects",
                fb.getColorAttachmentView(), OptionalInt.empty(), fb.getDepthAttachmentView(), OptionalDouble.empty())) {
            if (solidVerts > 0) draw(pass, Pipelines.SOLID, transforms, 0, solidVerts);
            if (glowVerts > 0) draw(pass, Pipelines.GLOW, transforms, solidVerts, glowVerts);
        }
    }

    private void draw(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transforms, int first, int count) {
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, gpu);
        pass.draw(first, count);
    }

    private void drawEffect(Effect e, Vec3d cam, float delta) {
        float t = Math.min(e.life, e.age + delta * e.dt);
        float x = (float) (e.x - cam.x), z = (float) (e.z - cam.z);
        float g = (float) (e.ground - cam.y) + 0.03f, c = (float) (e.chest - cam.y);
        float s = e.scale;
        int c1 = e.rainbow ? Color.HSBtoRGB((t * 0.015f) % 1f, 0.7f, 1f) & 0xFFFFFF : e.c1;
        int c2 = e.rainbow ? Color.HSBtoRGB((t * 0.015f + 0.35f) % 1f, 0.7f, 1f) & 0xFFFFFF : e.c2;

        switch (e.type) {
            case SOUL -> {
                flash(x, c, z, t, 1.4f * s, 7, c1);
                double[] o = soulOrb(e, t);
                float fade = soulFade(e, t);
                float ox = (float) (o[0] - cam.x), oy = (float) (o[1] - cam.y), oz = (float) (o[2] - cam.z);
                float pulse = 1f + 0.12f * (float) Math.sin(t * 0.9);
                glow(ox, oy, oz, 0.95f * s * pulse, c1, 0.55f * fade, 0.18f);
                glow(ox, oy, oz, 0.38f * s, mix(c2, 0xFFFFFF, 0.4f), 0.9f * fade, 0.35f);
                disc(x, g, z, 1.1f * s, c1, 0.35f * (1 - Math.min(1f, t / 18f)));
            }
            case SHOCKWAVE -> {
                flash(x, c, z, t, 1.6f * s, 6, c1);
                float bp = Math.min(1f, t / 9f);
                beam(x, g, z, 4.5f * s * (0.4f + 0.6f * bp), 0.32f * s * (1 - bp), mix(c1, 0xFFFFFF, 0.3f), 0.9f * (1 - bp));
                disc(x, g, z, 1.5f * s, c1, 0.6f * (1 - Math.min(1f, t / 10f)));
                for (int k = 0; k < 3; k++) {
                    float p = (t - k * 3f) / 22f;
                    if (p < 0 || p > 1) continue;
                    float rad = ringRadius(e, t, k);
                    float inten = (float) Math.pow(1 - p, 1.4) * (1 - k * 0.22f);
                    int col = k == 1 ? c2 : mix(c1, c2, k * 0.3f);
                    ring(x, g, z, rad, (0.42f - k * 0.1f) * s * (1 - 0.4f * p), col, inten * 0.95f, 56);
                    ring(x, g, z, rad, 0.08f * s, mix(col, 0xFFFFFF, 0.6f), inten * 0.6f, 56);
                    if (k == 0) curtain(x, g, z, rad, 1.1f * s * (1 - p), col, inten * 0.55f, 56);
                }
            }
            case BURST -> {
                flash(x, c, z, t, 2.4f * s, 9, c1);
                float sp = Math.min(1f, t / 13f);
                if (sp < 1) {
                    float ease = 1 - (1 - sp) * (1 - sp);
                    shell(x, c, z, (0.3f + 3.4f * ease) * s, 0.35f * s * (1 - sp * 0.6f), c2, (1 - sp) * 0.85f);
                }
                float gp = Math.min(1f, t / 18f);
                if (gp < 1) ring(x, g, z, (0.4f + 3.6f * (1 - (1 - gp) * (1 - gp))) * s, 0.3f * s, c1, (1 - gp) * 0.6f, 48);
                disc(x, g, z, 1.6f * s, c1, 0.45f * (1 - Math.min(1f, t / 12f)));
            }
            case PILLAR -> {
                float p = t / e.life;
                float grow = Math.min(1f, t / 3f);
                float width = (0.85f * grow - 0.65f * p) * s;
                float inten = Math.min(1f, (1 - p) / 0.4f);
                if (width > 0.02f) {
                    float h = 18f * s * (0.3f + 0.7f * grow);
                    beam(x, g, z, h, width * 1.6f, c1, 0.55f * inten);
                    beam(x, g, z, h, width * 0.55f, mix(c2, 0xFFFFFF, 0.55f), 0.9f * inten);
                }
                flash(x, c, z, t, 1.5f * s, 6, c1);
                disc(x, g, z, 1.6f * s, c1, 0.6f * inten);
                float rp = Math.min(1f, t / 20f);
                if (rp < 1) ring(x, g, z, (0.5f + 2.8f * (1 - (1 - rp) * (1 - rp))) * s, 0.25f * s, c2, (1 - rp) * 0.8f, 48);
                ring(x, g, z, 1.05f * s, 0.12f * s, mix(c1, 0xFFFFFF, 0.3f), 0.7f * inten, 48);
            }
            case SHATTER -> flash(x, c, z, t, 1.7f * s, 8, c1);
            case VORTEX -> {
                float implode = 26f;
                if (t < implode) {
                    float f = t / implode;
                    glow(x, c, z, (0.2f + 0.7f * f * f) * s, c1, 0.25f + 0.5f * f, 0.25f);
                    glow(x, c, z, (0.08f + 0.25f * f) * s, mix(c2, 0xFFFFFF, 0.5f), 0.4f + 0.6f * f, 0.4f);
                    ring(x, g, z, (2.4f - 1.9f * f) * s, 0.35f * s, c2, 0.25f + 0.35f * f, 48);
                    disc(x, g, z, (2.2f - 1.2f * f) * s, c1, 0.25f * f);
                } else {
                    float tt = t - implode;
                    flash(x, c, z, tt, 2.6f * s, 9, mix(c1, 0xFFFFFF, 0.2f));
                    float sp = Math.min(1f, tt / 11f);
                    if (sp < 1) shell(x, c, z, (0.3f + 3.2f * (1 - (1 - sp) * (1 - sp))) * s, 0.3f * s, c2, (1 - sp) * 0.9f);
                    float gp = Math.min(1f, tt / 12f);
                    if (gp < 1) ring(x, g, z, (0.3f + 3.6f * (1 - (1 - gp) * (1 - gp))) * s, 0.3f * s, c1, (1 - gp) * 0.7f, 48);
                }
            }
        }
    }

    private void flash(float x, float y, float z, float t, float size, float dur, int rgb) {
        if (t >= dur) return;
        float p = t / dur;
        float grow = 1 - (1 - p) * (1 - p);
        float inten = (1 - p) * (1 - p);
        glow(x, y, z, size * (0.35f + 0.65f * grow), rgb, 0.8f * inten, 0.2f);
        glow(x, y, z, size * 0.35f * (0.5f + 0.5f * grow), 0xFFFFFF, 0.75f * inten, 0.3f);
    }

    private void drawParticle(Particle p, Vec3d cam, float delta) {
        float x = (float) (p.px + (p.x - p.px) * delta - cam.x);
        float y = (float) (p.py + (p.y - p.py) * delta - cam.y);
        float z = (float) (p.pz + (p.z - p.pz) * delta - cam.z);
        float age = p.age + delta * p.dt;
        float f = Math.max(0f, 1f - age / p.life);
        switch (p.kind) {
            case DOT -> {
                float fade = Math.min(1f, f * 2.5f) * Math.min(1f, age * 0.6f + 0.3f);
                glow(x, y, z, p.size * 2.6f * (0.5f + 0.5f * f), p.rgb, 0.75f * fade, 0.3f);
            }
            case EMBER -> {
                float tw = 0.6f + 0.4f * (float) Math.sin(age * 1.3f + p.flicker);
                glow(x, y, z, p.size * 2.4f, p.rgb, 0.8f * Math.min(1f, f * 2f) * tw, 0.35f);
            }
            case STREAK -> {
                float vx = (float) (p.x - p.px), vy = (float) (p.y - p.py), vz = (float) (p.z - p.pz);
                float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                float fade = Math.min(1f, f * 2.2f);
                float w = p.size * (0.6f + 0.4f * f);
                if (len > 0.01f) {
                    float k = p.streak;
                    streak(x, y, z, x - vx * k, y - vy * k, z - vz * k, w * 2.2f, p.rgb, 0.55f * fade);
                    streak(x, y, z, x - vx * k * 0.7f, y - vy * k * 0.7f, z - vz * k * 0.7f, w * 0.8f, mix(p.rgb, 0xFFFFFF, 0.65f), 0.9f * fade);
                } else {
                    glow(x, y, z, w * 3f, p.rgb, 0.7f * fade, 0.3f);
                }
            }
            case ORBIT -> {
                Particle tail = SCRATCH;
                orbit(p, Math.max(0f, age - 2.2f), tail);
                float tx = (float) (tail.x - cam.x), ty = (float) (tail.y - cam.y), tz = (float) (tail.z - cam.z);
                float fadeIn = Math.min(1f, age / 5f);
                streak(x, y, z, tx, ty, tz, p.size * 2.4f, p.rgb, 0.55f * fadeIn);
                streak(x, y, z, tx, ty, tz, p.size * 0.9f, mix(p.rgb, 0xFFFFFF, 0.6f), 0.8f * fadeIn);
            }
            case SHARD -> {
                float fade = Math.min(1f, f * 3.5f);
                float ang = p.prot + (p.rot - p.prot) * delta;
                cube(x, y, z, p.size, ang, p, fade);
                glow(x, y, z, p.size * 4f, p.rgb, 0.28f * fade, 0f);
            }
        }
    }

    private static final Particle SCRATCH = new Particle();

    private void glow(float x, float y, float z, float radius, int rgb, float inten, float core) {
        if (inten <= 0.003f || radius <= 0.001f) return;
        float dist = (float) Math.sqrt(x * x + y * y + z * z);
        inten *= Math.max(0f, Math.min(1f, (dist - 0.35f) / 1.6f));
        if (inten <= 0.003f) return;
        glowBuf = ensure(glowBuf, FAN * 9 * VB);
        int center = add(core > 0 ? mix(rgb, 0xFFFFFF, core) : rgb, inten);
        int mid = add(rgb, inten * 0.42f);
        float r1 = radius * 0.4f;
        for (int i = 0; i < FAN; i++) {
            float ax = (rx * FCOS[i] + ux * FSIN[i]), ay = (ry * FCOS[i] + uy * FSIN[i]), az = (rz * FCOS[i] + uz * FSIN[i]);
            float bx = (rx * FCOS[i + 1] + ux * FSIN[i + 1]), by = (ry * FCOS[i + 1] + uy * FSIN[i + 1]), bz = (rz * FCOS[i + 1] + uz * FSIN[i + 1]);
            vertex(glowBuf, x, y, z, center);
            vertex(glowBuf, x + ax * r1, y + ay * r1, z + az * r1, mid);
            vertex(glowBuf, x + bx * r1, y + by * r1, z + bz * r1, mid);

            vertex(glowBuf, x + ax * r1, y + ay * r1, z + az * r1, mid);
            vertex(glowBuf, x + ax * radius, y + ay * radius, z + az * radius, BLACK);
            vertex(glowBuf, x + bx * radius, y + by * radius, z + bz * radius, BLACK);
            vertex(glowBuf, x + ax * r1, y + ay * r1, z + az * r1, mid);
            vertex(glowBuf, x + bx * radius, y + by * radius, z + bz * radius, BLACK);
            vertex(glowBuf, x + bx * r1, y + by * r1, z + bz * r1, mid);
        }
    }

    private void streak(float hx, float hy, float hz, float tx, float ty, float tz, float w, int rgb, float inten) {
        if (inten <= 0.003f) return;
        float dx = hx - tx, dy = hy - ty, dz = hz - tz;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-4f) return;
        dx /= dl; dy /= dl; dz /= dl;
        float sx = dy * hz - dz * hy, sy = dz * hx - dx * hz, sz = dx * hy - dy * hx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-5f) { sx = rx; sy = ry; sz = rz; } else { sx /= sl; sy /= sl; sz /= sl; }
        int hot = add(rgb, inten);
        float lx = hx - sx * w, ly = hy - sy * w, lz = hz - sz * w;
        float qx = hx + sx * w, qy = hy + sy * w, qz = hz + sz * w;
        float fx = hx + dx * w * 1.4f, fy = hy + dy * w * 1.4f, fz = hz + dz * w * 1.4f;
        glowBuf = ensure(glowBuf, 12 * VB);
        vertex(glowBuf, lx, ly, lz, BLACK); vertex(glowBuf, hx, hy, hz, hot); vertex(glowBuf, tx, ty, tz, BLACK);
        vertex(glowBuf, hx, hy, hz, hot); vertex(glowBuf, qx, qy, qz, BLACK); vertex(glowBuf, tx, ty, tz, BLACK);
        vertex(glowBuf, lx, ly, lz, BLACK); vertex(glowBuf, fx, fy, fz, BLACK); vertex(glowBuf, hx, hy, hz, hot);
        vertex(glowBuf, hx, hy, hz, hot); vertex(glowBuf, fx, fy, fz, BLACK); vertex(glowBuf, qx, qy, qz, BLACK);
    }

    private void ring(float x, float y, float z, float r, float w, int rgb, float inten, int seg) {
        if (inten <= 0.003f || r <= 0) return;
        float r0 = Math.max(0f, r - w), r2 = r + w;
        int hot = add(rgb, inten);
        glowBuf = ensure(glowBuf, seg * 12 * VB);
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            ChunkOverlayRenderer.quad(glowBuf, x + c0 * r0, y, z + s0 * r0, x + c1 * r0, y, z + s1 * r0,
                    x + c1 * r, y, z + s1 * r, x + c0 * r, y, z + s0 * r, BLACK, hot);
            ChunkOverlayRenderer.quad(glowBuf, x + c0 * r, y, z + s0 * r, x + c1 * r, y, z + s1 * r,
                    x + c1 * r2, y, z + s1 * r2, x + c0 * r2, y, z + s0 * r2, hot, BLACK);
        }
    }

    private void disc(float x, float y, float z, float r, int rgb, float inten) {
        if (inten <= 0.003f) return;
        int hot = add(rgb, inten);
        int seg = 32;
        glowBuf = ensure(glowBuf, seg * 3 * VB);
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            vertex(glowBuf, x, y, z, hot);
            vertex(glowBuf, x + (float) Math.cos(a0) * r, y, z + (float) Math.sin(a0) * r, BLACK);
            vertex(glowBuf, x + (float) Math.cos(a1) * r, y, z + (float) Math.sin(a1) * r, BLACK);
        }
    }

    private void curtain(float x, float y, float z, float r, float h, int rgb, float inten, int seg) {
        if (inten <= 0.003f || h <= 0.01f) return;
        int hot = add(rgb, inten);
        glowBuf = ensure(glowBuf, seg * 6 * VB);
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            float xa = x + (float) Math.cos(a0) * r, za = z + (float) Math.sin(a0) * r;
            float xb = x + (float) Math.cos(a1) * r, zb = z + (float) Math.sin(a1) * r;
            ChunkOverlayRenderer.wall(glowBuf, xa, za, xb, zb, y, h, hot, BLACK);
        }
    }

    private void shell(float x, float y, float z, float r, float w, int rgb, float inten) {
        if (inten <= 0.003f) return;
        float dist = (float) Math.sqrt(x * x + y * y + z * z);
        inten *= Math.max(0f, Math.min(1f, (dist - 0.5f) / 2f));
        if (inten <= 0.003f) return;
        int seg = 48;
        float r0 = Math.max(0f, r - w), r2 = r + w;
        int hot = add(rgb, inten);
        glowBuf = ensure(glowBuf, seg * 12 * VB);
        for (int i = 0; i < seg; i++) {
            float c0 = (float) Math.cos(i * TAU / seg), s0 = (float) Math.sin(i * TAU / seg);
            float c1 = (float) Math.cos((i + 1) * TAU / seg), s1 = (float) Math.sin((i + 1) * TAU / seg);
            float ax = rx * c0 + ux * s0, ay = ry * c0 + uy * s0, az = rz * c0 + uz * s0;
            float bx = rx * c1 + ux * s1, by = ry * c1 + uy * s1, bz = rz * c1 + uz * s1;
            ChunkOverlayRenderer.quad(glowBuf, x + ax * r0, y + ay * r0, z + az * r0, x + bx * r0, y + by * r0, z + bz * r0,
                    x + bx * r, y + by * r, z + bz * r, x + ax * r, y + ay * r, z + az * r, BLACK, hot);
            ChunkOverlayRenderer.quad(glowBuf, x + ax * r, y + ay * r, z + az * r, x + bx * r, y + by * r, z + bz * r,
                    x + bx * r2, y + by * r2, z + bz * r2, x + ax * r2, y + ay * r2, z + az * r2, hot, BLACK);
        }
    }

    private void beam(float x, float y, float z, float h, float w, int rgb, float inten) {
        if (inten <= 0.003f || w <= 0.002f || h <= 0.01f) return;
        float sx = -z, sz = x;
        float sl = (float) Math.sqrt(sx * sx + sz * sz);
        if (sl < 1e-4f) { sx = rx; sz = rz; sl = (float) Math.sqrt(sx * sx + sz * sz); if (sl < 1e-4f) { sx = 1; sz = 0; sl = 1; } }
        sx = sx / sl * w; sz = sz / sl * w;
        int n = 10;
        glowBuf = ensure(glowBuf, n * 12 * VB);
        for (int i = 0; i < n; i++) {
            float f0 = i / (float) n, f1 = (i + 1) / (float) n;
            float y0 = y + h * f0, y1 = y + h * f1;
            int k0 = add(rgb, inten * beamFade(f0)), k1 = add(rgb, inten * beamFade(f1));
            vertex(glowBuf, x - sx, y0, z - sz, BLACK); vertex(glowBuf, x, y0, z, k0); vertex(glowBuf, x, y1, z, k1);
            vertex(glowBuf, x - sx, y0, z - sz, BLACK); vertex(glowBuf, x, y1, z, k1); vertex(glowBuf, x - sx, y1, z - sz, BLACK);
            vertex(glowBuf, x, y0, z, k0); vertex(glowBuf, x + sx, y0, z + sz, BLACK); vertex(glowBuf, x + sx, y1, z + sz, BLACK);
            vertex(glowBuf, x, y0, z, k0); vertex(glowBuf, x + sx, y1, z + sz, BLACK); vertex(glowBuf, x, y1, z, k1);
        }
    }

    private static float beamFade(float f) {
        float base = Math.min(1f, f * 12f + 0.35f);
        return base * (float) Math.pow(1 - f, 1.3);
    }

    private static final float[][] FACES = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final Vector3f LIGHT = new Vector3f(0.35f, 1f, 0.25f).normalize();

    private void cube(float x, float y, float z, float half, float angle, Particle p, float fade) {
        if (fade <= 0.01f) return;
        Quaternionf q = new Quaternionf().rotateAxis(angle, p.axX, p.axY, p.axZ);
        Vector3f n = new Vector3f(), a = new Vector3f(), b = new Vector3f(), corner = new Vector3f();
        solidBuf = ensure(solidBuf, 36 * VB);
        int r = (p.rgb >> 16) & 255, g = (p.rgb >> 8) & 255, bl = p.rgb & 255;
        int alpha = Math.round(225 * fade);
        float[][] quad = new float[4][3];
        for (float[] face : FACES) {
            n.set(face[0], face[1], face[2]).rotate(q);
            if (face[1] != 0) { a.set(1, 0, 0); b.set(0, 0, 1); }
            else if (face[0] != 0) { a.set(0, 1, 0); b.set(0, 0, 1); }
            else { a.set(1, 0, 0); b.set(0, 1, 0); }
            a.rotate(q); b.rotate(q);
            float light = 0.5f + 0.5f * Math.max(0f, n.dot(LIGHT));
            int col = (alpha << 24) | (Math.min(255, Math.round(r * light + 30 * light)) << 16)
                    | (Math.min(255, Math.round(g * light + 30 * light)) << 8) | Math.min(255, Math.round(bl * light + 30 * light));
            float[][] sgn = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int k = 0; k < 4; k++) {
                corner.set(n).mul(half).add(a.x * sgn[k][0] * half, a.y * sgn[k][0] * half, a.z * sgn[k][0] * half)
                        .add(b.x * sgn[k][1] * half, b.y * sgn[k][1] * half, b.z * sgn[k][1] * half);
                quad[k][0] = x + corner.x; quad[k][1] = y + corner.y; quad[k][2] = z + corner.z;
            }
            ChunkOverlayRenderer.quad(solidBuf, quad[0][0], quad[0][1], quad[0][2], quad[1][0], quad[1][1], quad[1][2],
                    quad[2][0], quad[2][1], quad[2][2], quad[3][0], quad[3][1], quad[3][2], col, col);
        }
    }

    private static final int BLACK = 0xFF000000;

    private static int add(int rgb, float inten) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * inten));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * inten));
        int b = Math.min(255, Math.round((rgb & 255) * inten));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void vertex(ByteBuffer b, float x, float y, float z, int argb) {
        ChunkOverlayRenderer.vertex(b, x, y, z, argb);
    }

    private static ByteBuffer ensure(ByteBuffer b, int extra) {
        if (b.remaining() >= extra) return b;
        int pos = b.position();
        ByteBuffer nb = MemoryUtil.memRealloc(b, Math.max(b.capacity() * 2, pos + extra));
        nb.limit(nb.capacity()).position(pos);
        return nb;
    }
}
