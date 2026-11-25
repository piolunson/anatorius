package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.mixins.accessors.PostEffectPassAccessor;
import dev.project.anatorius.mixins.accessors.PostEffectProcessorAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MotionBlurChain {
    private static final Identifier SCREENQUAD = Identifier.ofVanilla("core/screenquad");
    private static final Identifier BLUR_FSH = Identifier.of("anatorius", "post/motionblur");
    private static final Identifier BLIT_FSH = Identifier.of("anatorius", "post/chams_blit");
    private static final Identifier SWAP = Identifier.of("anatorius", "motionblur_swap");
    private static final Identifier CHAIN_NAME = Identifier.of("anatorius", "motionblur_runtime");
    private static final String BLOCK = "MotionBlurConfig";
    private static final int BLOCK_SIZE = 16;

    private static ProjectionMatrix2 projection;
    private static PostEffectProcessor cached;
    private static GpuBuffer config;
    private static boolean failed;

    private MotionBlurChain() {}

    public static PostEffectProcessor get() {
        if (cached != null || failed) return cached;
        cached = build();
        if (cached == null) failed = true;
        return cached;
    }

    public static void update(float vx, float vy, float radial, int samples) {
        if (config == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, BLOCK_SIZE).putVec2(vx, vy).putFloat(radial).putFloat(samples).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(config.slice(), data);
        }
    }

    public static void close() {
        if (cached != null) cached.close();
        cached = null;
        config = null;
        failed = false;
    }

    private static PostEffectProcessor build() {
        try {
            if (projection == null) {
                projection = new ProjectionMatrix2("anatorius_motionblur", 0.1f, 1000.0f, false);
            }
            Identifier main = DefaultFramebufferSet.MAIN;

            PostEffectPipeline.Pass blur = new PostEffectPipeline.Pass(
                    SCREENQUAD, BLUR_FSH,
                    List.of(new PostEffectPipeline.TargetSampler("In", main, false, true),
                            new PostEffectPipeline.TargetSampler("InDepth", main, true, false)),
                    SWAP,
                    Map.of(BLOCK, List.<UniformValue>of(
                            new UniformValue.Vec2fValue(new Vector2f(0f, 0f)),
                            new UniformValue.FloatValue(0f),
                            new UniformValue.FloatValue(1f))));

            PostEffectPipeline.Pass blit = new PostEffectPipeline.Pass(
                    SCREENQUAD, BLIT_FSH,
                    List.of(new PostEffectPipeline.TargetSampler("In", SWAP, false, false)),
                    main,
                    Map.of());

            PostEffectPipeline pipeline = new PostEffectPipeline(
                    Map.of(SWAP, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
                    List.of(blur, blit));

            PostEffectProcessor processor = PostEffectProcessor.parseEffect(pipeline, MinecraftClient.getInstance().getTextureManager(),
                    DefaultFramebufferSet.MAIN_ONLY, CHAIN_NAME, projection);

            PostEffectPass pass = ((PostEffectProcessorAccessor) processor).getPasses().get(0);
            Map<String, GpuBuffer> blocks = ((PostEffectPassAccessor) pass).getUniformBuffers();
            GpuBuffer writable;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer init = Std140Builder.onStack(stack, BLOCK_SIZE).putVec2(0f, 0f).putFloat(0f).putFloat(1f).get();
                writable = RenderSystem.getDevice().createBuffer(() -> "anatorius motion blur config",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, init);
            }
            GpuBuffer old = blocks.put(BLOCK, writable);
            if (old != null) old.close();
            config = writable;
            return processor;
        } catch (Throwable t) {
            Anatorius.LOGGER.warn("[Anatorius] motion blur chain build failed: {}", t.toString());
            return null;
        }
    }
}
