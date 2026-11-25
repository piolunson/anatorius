package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.memory.ObjectPool;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class MotionBlurRenderer implements IMinecraft {
    public static final MotionBlurRenderer INSTANCE = new MotionBlurRenderer();

    private static final float MAX_SHUTTER_SECONDS = 1.0f / 30.0f;
    private static final float MAX_BLUR_UV = 0.12f;
    private static final int RETRY_FRAMES = 200;

    private final ObjectPool pool = new ObjectPool(3);
    private float lastYaw, lastPitch;
    private long lastNanos;
    private boolean primed;
    private float smoothX, smoothY, smoothRadial;
    private int backoff;
    private boolean warned;

    private MotionBlurRenderer() {}

    public void render(float intensity, int samples, boolean moveBlur) {
        if (intensity <= 0.0f) return;
        if (backoff > 0) { backoff--; return; }

        Camera camera = mc.gameRenderer.getCamera();
        long now = System.nanoTime();
        float yaw = camera.getYaw(), pitch = camera.getPitch();
        if (!primed || mc.isPaused()) {
            lastYaw = yaw; lastPitch = pitch; lastNanos = now; primed = true;
            return;
        }
        float dt = Math.max((now - lastNanos) / 1.0e9f, 1.0e-4f);
        float dYaw = MathHelper.wrapDegrees(yaw - lastYaw);
        float dPitch = pitch - lastPitch;
        lastYaw = yaw; lastPitch = pitch; lastNanos = now;
        if (dt > 0.25f) { dYaw = 0; dPitch = 0; }

        float shutter = MAX_SHUTTER_SECONDS * intensity;
        float vFov = mc.options.getFov().getValue().floatValue();
        float aspect = (float) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
        float hFov = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(vFov) / 2.0) * aspect));
        float vx = (dYaw / dt) * shutter / hFov;
        float vy = (dPitch / dt) * shutter / vFov;

        float radial = 0f;
        if (moveBlur && mc.player != null) {
            Vec3d vel = mc.player.getVelocity();
            Vec3d look = mc.player.getRotationVec(1.0f);
            double forward = vel.x * look.x + vel.z * look.z;
            radial = (float) Math.max(0, forward) * 0.35f * intensity;
        }

        float a = Math.min(1f, dt / 0.012f);
        smoothX += (vx - smoothX) * a;
        smoothY += (vy - smoothY) * a;
        smoothRadial += (radial - smoothRadial) * a;
        float len = (float) Math.sqrt(smoothX * smoothX + smoothY * smoothY);
        float sx = smoothX, sy = smoothY;
        if (len > MAX_BLUR_UV) { sx *= MAX_BLUR_UV / len; sy *= MAX_BLUR_UV / len; }
        float sr = Math.min(smoothRadial, 0.08f);

        if (sx * sx + sy * sy < 1.0e-8f && sr < 1.0e-4f) return;

        try {
            PostEffectProcessor processor = MotionBlurChain.get();
            if (processor == null) { backoff = RETRY_FRAMES; return; }
            MotionBlurChain.update(-sx, sy, sr, samples);

            Framebuffer main = mc.getFramebuffer();
            FrameGraphBuilder builder = new FrameGraphBuilder();
            DefaultFramebufferSet set = new DefaultFramebufferSet();
            set.mainFramebuffer = builder.createObjectNode("main", main);
            processor.render(builder, main.textureWidth, main.textureHeight, set);
            builder.run(pool);
            pool.decrementLifespan();
        } catch (Throwable t) {
            backoff = RETRY_FRAMES;
            if (!warned) {
                warned = true;
                Anatorius.LOGGER.warn("[Anatorius] motion blur render failed (pausing it briefly): {}", t.toString());
            }
        }
    }

    public void reset() {
        pool.clear();
        MotionBlurChain.close();
        primed = false;
        smoothX = smoothY = smoothRadial = 0f;
        backoff = 0;
        warned = false;
    }
}
